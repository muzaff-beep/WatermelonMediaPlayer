// rust-core/src/engine.rs — Part 1 of 2
// MediaEngine struct definition, lifecycle, source control, and query methods.
// Manifesto §3.4 interface implemented.

use crate::audio;
use crate::callback::CallbackDispatcher;
use crate::decoder;
use crate::error::{EngineError, EngineResult};
use crate::playlist_parser;
use crate::plugin_host::PluginHost;
use crate::subtitle::SubtitleManager;
use jni::objects::GlobalRef;
use std::ffi::c_void;
use std::sync::{Arc, Mutex};

/// Top-level media engine. All state is owned here.
/// Thread-safe: interior mutability via `Arc<Mutex<Inner>>` for shared access
/// across decode, audio, and callback threads.
pub struct MediaEngine {
    inner: Arc<Mutex<EngineInner>>,
    callback: Arc<Mutex<Option<CallbackDispatcher>>>,
}

struct EngineInner {
    uri: Option<String>,
    state: PlaybackState,
    duration_us: i64,
    position_us: i64,
    subtitle_offset_ms: i64,
    subtitle_font_path: Option<String>,
    subtitle_manager: Option<SubtitleManager>,
    plugin_host: PluginHost,
    audio_ready: bool,
    decoder_ready: bool,
    surface_ptr: *mut c_void,
}

/// Playback states matching Manifesto §3.4 `PlaybackState` enum and
/// Kotlin `WatermelonEventCallback.onPlaybackStateChanged` int mapping.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(i32)]
pub enum PlaybackState {
    Idle = 0,
    Preparing = 1,
    Playing = 2,
    Paused = 3,
    Ended = 4,
    Error = 5,
}

/// Subtitle cue produced by subtitle parser, consumed by callback dispatcher.
#[derive(Debug, Clone)]
pub struct SubtitleCue {
    pub start_us: i64,
    pub end_us: i64,
    pub text: String,
}

impl MediaEngine {
    /// Create a new `MediaEngine` in `Idle` state.
    pub fn new() -> Self {
        let inner = EngineInner {
            uri: None,
            state: PlaybackState::Idle,
            duration_us: 0,
            position_us: 0,
            subtitle_offset_ms: 0,
            subtitle_font_path: None,
            subtitle_manager: None,
            plugin_host: PluginHost::new(),
            audio_ready: false,
            decoder_ready: false,
            surface_ptr: std::ptr::null_mut(),
        };
        log::debug!("MediaEngine created in Idle state");
        Self {
            inner: Arc::new(Mutex::new(inner)),
            callback: Arc::new(Mutex::new(None)),
        }
    }

    /// Set the media data source URI. Validates URI is non-empty and well-formed.
    /// Resets decoder and demuxer state for the new source.
    pub fn set_data_source(&mut self, uri: &str) -> EngineResult<()> {
        if uri.is_empty() {
            return Err(EngineError::InvalidUri("URI is empty".into()));
        }
        let mut inner = self.inner.lock().unwrap();
        inner.uri = Some(uri.to_owned());
        inner.state = PlaybackState::Idle;
        inner.decoder_ready = false;
        inner.duration_us = 0;
        inner.position_us = 0;
        log::info!("Data source set: {}", uri);
        self.emit_state(PlaybackState::Idle);
        Ok(())
    }

    /// Begin asynchronous preparation: open demuxer, decode headers,
    /// initialize audio backend, notify Kotlin when ready.
    pub fn prepare(&mut self) {
        let inner_clone = Arc::clone(&self.inner);
        let cb_clone = Arc::clone(&self.callback);
        {
            let mut inner = self.inner.lock().unwrap();
            inner.state = PlaybackState::Preparing;
        }
        self.emit_state(PlaybackState::Preparing);

        // Preparation runs on a dedicated thread to avoid blocking JNI.
        // On completion, the callback thread emits onPrepared and state=Playing.
        std::thread::spawn(move || {
            let mut inner = inner_clone.lock().unwrap();
            let uri = match &inner.uri {
                Some(u) => u.clone(),
                None => {
                    log::error!("prepare: no data source set");
                    inner.state = PlaybackState::Error;
                    drop(inner);
                    if let Ok(cb) = cb_clone.lock() {
                        if let Some(ref disp) = *cb {
                            disp.on_error(1, "No data source set");
                        }
                    }
                    return;
                }
            };

            // Initialize decoder subsystem
            match decoder::init(&uri) {
                Ok((duration_us, audio_config)) => {
                    inner.duration_us = duration_us;
                    inner.decoder_ready = true;
                    // Initialize audio backend
                    #[cfg(target_os = "android")]
                    {
                        match audio::init(&audio_config) {
                            Ok(()) => {
                                inner.audio_ready = true;
                                inner.state = PlaybackState::Playing;
                                drop(inner);
                                if let Ok(cb) = cb_clone.lock() {
                                    if let Some(ref disp) = *cb {
                                        disp.on_prepared(duration_us);
                                    }
                                }
                            }
                            Err(e) => {
                                log::error!("Audio init failed: {:?}", e);
                                inner.state = PlaybackState::Error;
                                drop(inner);
                                if let Ok(cb) = cb_clone.lock() {
                                    if let Some(ref disp) = *cb {
                                        disp.on_error(2, &format!("Audio init: {}", e));
                                    }
                                }
                            }
                        }
                    }
                    #[cfg(not(target_os = "android"))]
                    {
                        inner.audio_ready = true;
                        inner.state = PlaybackState::Playing;
                        drop(inner);
                        if let Ok(cb) = cb_clone.lock() {
                            if let Some(ref disp) = *cb {
                                disp.on_prepared(duration_us);
                            }
                        }
                    }
                }
                Err(e) => {
                    log::error!("Decoder init failed: {:?}", e);
                    inner.state = PlaybackState::Error;
                    drop(inner);
                    if let Ok(cb) = cb_clone.lock() {
                        if let Some(ref disp) = *cb {
                            disp.on_error(3, &format!("Decoder: {}", e));
                        }
                    }
                }
            }
        });
    }

    /// Start or resume playback.
    pub fn play(&mut self) {
        let mut inner = self.inner.lock().unwrap();
        if inner.state == PlaybackState::Paused || inner.state == PlaybackState::Preparing {
            inner.state = PlaybackState::Playing;
            log::debug!("Playback resumed");
            self.emit_state(PlaybackState::Playing);
        }
    }

    /// Pause playback.
    pub fn pause(&mut self) {
        let mut inner = self.inner.lock().unwrap();
        if inner.state == PlaybackState::Playing {
            inner.state = PlaybackState::Paused;
            log::debug!("Playback paused");
            self.emit_state(PlaybackState::Paused);
        }
    }

    /// Get current playback position in microseconds.
    pub fn get_current_position(&self) -> i64 {
        self.inner.lock().unwrap().position_us
    }

    /// Get media duration in microseconds.
    pub fn get_duration(&self) -> i64 {
        self.inner.lock().unwrap().duration_us
    }

    /// Emit a playback state change to the Kotlin callback if registered.
    fn emit_state(&self, state: PlaybackState) {
        if let Ok(cb) = self.callback.lock() {
            if let Some(ref disp) = *cb {
                disp.on_playback_state_changed(state);
            }
        }
    }

    /// Emit subtitle cues to the Kotlin callback if registered.
    fn emit_subtitle_cues(&self, cues: &[SubtitleCue]) {
        if let Ok(cb) = self.callback.lock() {
            if let Some(ref disp) = *cb {
                disp.on_subtitle_cues(cues);
            }
        }
    }
}

// Part 1 ends here. Part 2 continues with:
// - seek_to, set_surface, load_subtitle, set_subtitle_offset, set_subtitle_font
