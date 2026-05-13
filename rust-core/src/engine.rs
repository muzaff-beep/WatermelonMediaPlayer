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

// rust-core/src/engine.rs — Part 2 of 2
// Continuation: seek, surface, subtitle management, plugin loading, callback registration.
// Append to Part 1. Final file assembles to ~580 lines.

impl MediaEngine {
    // -------------------------------------------------------------------------
    // Seek
    // -------------------------------------------------------------------------

    /// Seek to a position in microseconds. Flushes decoder and subtitle state.
    pub fn seek_to(&mut self, position_us: i64) {
        let mut inner = self.inner.lock().unwrap();
        if position_us < 0 {
            log::warn!("seek_to: negative position, clamping to 0");
            inner.position_us = 0;
        } else if position_us > inner.duration_us && inner.duration_us > 0 {
            log::warn!(
                "seek_to: position {} exceeds duration {}, clamping",
                position_us,
                inner.duration_us
            );
            inner.position_us = inner.duration_us;
        } else {
            inner.position_us = position_us;
        }
        decoder::flush();
        if let Some(ref mut sub) = inner.subtitle_manager {
            sub.flush();
        }
        log::debug!("Seeked to {} us", inner.position_us);
    }

    // -------------------------------------------------------------------------
    // Surface
    // -------------------------------------------------------------------------

    /// Set the Android Surface for video rendering.
    /// The pointer is an `ANativeWindow *` obtained from the Kotlin Surface object.
    pub fn set_surface(&mut self, surface: *mut c_void) {
        let mut inner = self.inner.lock().unwrap();
        inner.surface_ptr = surface;
        if !surface.is_null() {
            log::debug!("Surface set for video rendering");
            decoder::set_render_target(surface);
        } else {
            log::debug!("Surface cleared");
        }
    }

    // -------------------------------------------------------------------------
    // Subtitle management
    // -------------------------------------------------------------------------

    /// Load a subtitle file. Supports SRT and ASS formats per Manifesto §1.2.
    pub fn load_subtitle(&mut self, path: &str) -> EngineResult<()> {
        let mut inner = self.inner.lock().unwrap();
        let font_path = inner.subtitle_font_path.clone();
        let offset_ms = inner.subtitle_offset_ms;
        let mut manager = SubtitleManager::new();
        manager.load(path, font_path.as_deref())?;
        manager.set_offset(offset_ms);
        inner.subtitle_manager = Some(manager);
        log::info!("Subtitle loaded: {}", path);
        Ok(())
    }

    /// Set subtitle offset in milliseconds. Positive = delay, negative = advance.
    pub fn set_subtitle_offset(&mut self, offset_ms: i64) {
        let mut inner = self.inner.lock().unwrap();
        inner.subtitle_offset_ms = offset_ms;
        if let Some(ref mut sub) = inner.subtitle_manager {
            sub.set_offset(offset_ms);
        }
        log::debug!("Subtitle offset set to {} ms", offset_ms);
    }

    /// Set the font path for GPU-rendered subtitles.
    /// Path is relative to APK assets, resolved by Kotlin layer.
    pub fn set_subtitle_font(&mut self, font_path: &str) {
        let mut inner = self.inner.lock().unwrap();
        inner.subtitle_font_path = Some(font_path.to_owned());
        if let Some(ref mut sub) = inner.subtitle_manager {
            sub.set_font_path(font_path);
        }
        log::debug!("Subtitle font path set: {}", font_path);
    }

    // -------------------------------------------------------------------------
    // Plugin management
    // -------------------------------------------------------------------------

    /// Load a codec plugin from the given shared library path.
    /// The plugin must export `watermelon_plugin_create` per Manifesto §3.2.
    pub fn load_plugin(&mut self, so_path: &str) -> EngineResult<()> {
        let mut inner = self.inner.lock().unwrap();
        inner.plugin_host.load(so_path)?;
        log::info!("Plugin loaded: {}", so_path);
        Ok(())
    }

    // -------------------------------------------------------------------------
    // Callback registration
    // -------------------------------------------------------------------------

    /// Register the Kotlin callback interface.
    /// The `GlobalRef` is stored and used to invoke `WatermelonEventCallback` methods.
    pub fn set_event_callback(&mut self, callback: Option<GlobalRef>) {
        let dispatcher = callback.map(CallbackDispatcher::new);
        let mut cb = self.callback.lock().unwrap();
        *cb = dispatcher;
        log::debug!("Event callback registered");
    }

    // -------------------------------------------------------------------------
    // Internal: subtitle cue emission (called by decode thread)
    // -------------------------------------------------------------------------

    /// Query subtitle cues active at the current playback position.
    /// Called periodically by the decode loop. Cues are emitted to Kotlin.
    pub(crate) fn poll_subtitle_cues(&self) {
        let inner = self.inner.lock().unwrap();
        if let Some(ref sub) = inner.subtitle_manager {
            let cues = sub.cues_at(inner.position_us);
            if !cues.is_empty() {
                drop(inner);
                self.emit_subtitle_cues(&cues);
            }
        }
    }

    /// Update internal position from decode thread progress.
    pub(crate) fn update_position(&self, position_us: i64) {
        let mut inner = self.inner.lock().unwrap();
        inner.position_us = position_us;
        if position_us >= inner.duration_us && inner.duration_us > 0 {
            if inner.state == PlaybackState::Playing {
                inner.state = PlaybackState::Ended;
                drop(inner);
                self.emit_state(PlaybackState::Ended);
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Drop safety for EngineInner
// -----------------------------------------------------------------------------

impl Drop for EngineInner {
    fn drop(&mut self) {
        // Release Android surface reference if held
        if !self.surface_ptr.is_null() {
            #[cfg(target_os = "android")]
            unsafe {
                let window: *mut ndk_sys::ANativeWindow = self.surface_ptr as *mut _;
                ndk_sys::ANativeWindow_release(window);
            }
            self.surface_ptr = std::ptr::null_mut();
        }
        // Flush decoder state
        decoder::flush();
        // Plugin host unloads all dynamic libraries on drop
        log::debug!("EngineInner dropped, all resources released");
    }
}

// -----------------------------------------------------------------------------
// Exported C ABI helpers used by jni_bridge.rs
// -----------------------------------------------------------------------------

/// Expose `Arc<Mutex<Inner>>` clone for decode/audio worker threads.
pub(crate) fn clone_inner(engine: &MediaEngine) -> Arc<Mutex<EngineInner>> {
    Arc::clone(&engine.inner)
}

/// Expose callback `Arc` clone for worker threads.
pub(crate) fn clone_callback(engine: &MediaEngine) -> Arc<Mutex<Option<CallbackDispatcher>>> {
    Arc::clone(&engine.callback)
}

// -----------------------------------------------------------------------------
// Tests — inline, no separate test file
// -----------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_engine_new_is_idle() {
        let engine = MediaEngine::new();
        assert_eq!(engine.get_duration(), 0);
        assert_eq!(engine.get_current_position(), 0);
    }

    #[test]
    fn test_set_data_source_rejects_empty() {
        let mut engine = MediaEngine::new();
        assert!(engine.set_data_source("").is_err());
    }

    #[test]
    fn test_set_data_source_accepts_valid_uri() {
        let mut engine = MediaEngine::new();
        assert!(engine.set_data_source("file:///sdcard/test.mp4").is_ok());
    }

    #[test]
    fn test_play_pause_state_machine() {
        let mut engine = MediaEngine::new();
        engine.set_data_source("file:///sdcard/test.mp4").unwrap();
        engine.play();
        engine.pause();
        assert_eq!(engine.get_duration(), 0);
    }

    #[test]
    fn test_seek_clamps_negative() {
        let mut engine = MediaEngine::new();
        engine.seek_to(-500);
        assert_eq!(engine.get_current_position(), 0);
    }

    #[test]
    fn test_seek_within_range() {
        let mut engine = MediaEngine::new();
        engine.set_data_source("file:///sdcard/test.mp4").unwrap();
        engine.seek_to(1_500_000);
        assert_eq!(engine.get_current_position(), 1_500_000);
    }

    #[test]
    fn test_subtitle_offset_stored() {
        let mut engine = MediaEngine::new();
        engine.set_subtitle_offset(250);
        engine.set_subtitle_offset(-100);
    }

    #[test]
    fn test_set_subtitle_font_path() {
        let mut engine = MediaEngine::new();
        engine.set_subtitle_font("fonts/vazir.ttf");
    }

    #[test]
    fn test_playback_state_enum_values() {
        assert_eq!(PlaybackState::Idle as i32, 0);
        assert_eq!(PlaybackState::Preparing as i32, 1);
        assert_eq!(PlaybackState::Playing as i32, 2);
        assert_eq!(PlaybackState::Paused as i32, 3);
        assert_eq!(PlaybackState::Ended as i32, 4);
        assert_eq!(PlaybackState::Error as i32, 5);
    }
}
