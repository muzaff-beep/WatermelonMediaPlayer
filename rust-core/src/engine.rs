use crate::decoder;
use crate::error::{EngineError, EngineResult};
use crate::plugin_host::PluginHost;
use crate::subtitle::SubtitleManager;
use jni::objects::GlobalRef;
use std::sync::{Arc, Mutex};

pub struct MediaEngine {
    inner: Arc<Mutex<EngineInner>>,
    callback: Arc<Mutex<Option<crate::callback::CallbackDispatcher>>>,
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
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(i32)]
pub enum PlaybackState {
    Idle = 0, Preparing = 1, Playing = 2, Paused = 3, Ended = 4, Error = 5,
}

#[derive(Debug, Clone)]
pub struct SubtitleCue {
    pub start_us: i64, pub end_us: i64, pub text: String,
}

impl MediaEngine {
    pub fn new() -> Self {
        Self {
            inner: Arc::new(Mutex::new(EngineInner {
                uri: None, state: PlaybackState::Idle, duration_us: 0, position_us: 0,
                subtitle_offset_ms: 0, subtitle_font_path: None, subtitle_manager: None,
                plugin_host: PluginHost::new(), audio_ready: false, decoder_ready: false,
            })),
            callback: Arc::new(Mutex::new(None)),
        }
    }
    pub fn set_data_source(&mut self, uri: &str) -> EngineResult<()> {
        let mut inner = self.inner.lock().unwrap();
        inner.uri = Some(uri.to_owned());
        Ok(())
    }
    pub fn prepare(&mut self) {
        let inner = self.inner.clone();
        let cb = self.callback.clone();
        std::thread::spawn(move || {
            let mut eng = inner.lock().unwrap();
            eng.state = PlaybackState::Preparing;
            if let Some(ref uri) = eng.uri.clone() {
                if let Ok((dur, _cfg)) = decoder::init(uri) {
                    eng.duration_us = dur;
                    eng.decoder_ready = true;
                    eng.state = PlaybackState::Playing;
                    drop(eng);
                    if let Ok(cb) = cb.lock() {
                        if let Some(ref disp) = *cb {
                            disp.on_prepared(dur);
                        }
                    }
                }
            }
        });
    }
    pub fn play(&mut self) { self.inner.lock().unwrap().state = PlaybackState::Playing; }
    pub fn pause(&mut self) { self.inner.lock().unwrap().state = PlaybackState::Paused; }
    pub fn seek_to(&mut self, pos: i64) {
        let mut inner = self.inner.lock().unwrap();
        inner.position_us = pos;
        decoder::flush();
    }
    pub fn get_current_position(&self) -> i64 { self.inner.lock().unwrap().position_us }
    pub fn get_duration(&self) -> i64 { self.inner.lock().unwrap().duration_us }
    pub fn set_surface(&mut self, _surface: *mut std::ffi::c_void) {}
    pub fn load_subtitle(&mut self, path: &str) -> EngineResult<()> {
        let mut inner = self.inner.lock().unwrap();
        let mut m = SubtitleManager::new();
        m.load(path, inner.subtitle_font_path.as_deref())?;
        m.set_offset(inner.subtitle_offset_ms);
        inner.subtitle_manager = Some(m);
        Ok(())
    }
    pub fn set_subtitle_offset(&mut self, ms: i64) {
        let mut inner = self.inner.lock().unwrap();
        inner.subtitle_offset_ms = ms;
        if let Some(ref mut s) = inner.subtitle_manager { s.set_offset(ms); }
    }
    pub fn set_subtitle_font(&mut self, path: &str) {
        self.inner.lock().unwrap().subtitle_font_path = Some(path.to_owned());
    }
    pub fn load_plugin(&mut self, path: &str) -> EngineResult<()> {
        self.inner.lock().unwrap().plugin_host.load(path)
    }
    pub fn set_event_callback(&mut self, cb: Option<GlobalRef>) {
        *self.callback.lock().unwrap() = cb.map(crate::callback::CallbackDispatcher::new);
    }
}