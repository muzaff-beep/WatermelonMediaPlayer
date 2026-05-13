use crate::callback::EngineCallback;
use crate::error::EngineError;
use crate::types::PlaybackState;
use std::ffi::c_void;

pub struct MediaEngine {
    data_source: Option<String>,
    surface: *mut c_void,
    callback: Option<Box<dyn EngineCallback>>,
    state: PlaybackState,
    // future fields: decoder, audio output, subs, etc.
}

impl MediaEngine {
    pub fn new() -> Self {
        MediaEngine {
            data_source: None,
            surface: std::ptr::null_mut(),
            callback: None,
            state: PlaybackState::Idle,
        }
    }

    pub fn set_data_source(&mut self, uri: &str) -> Result<(), EngineError> {
        if uri.is_empty() {
            return Err(EngineError::InvalidSource("empty URI".into()));
        }
        self.data_source = Some(uri.to_string());
        self.state = PlaybackState::Idle;
        Ok(())
    }

    pub fn prepare(&mut self) {
        if self.data_source.is_none() {
            if let Some(cb) = &self.callback {
                cb.on_error(1, "No data source set");
                cb.on_playback_state_changed(PlaybackState::Error);
            }
            self.state = PlaybackState::Error;
            return;
        }
        // In the future: load media, detect streams, etc.
        self.state = PlaybackState::Preparing;
        // For now, fire prepared immediately with a dummy duration.
        if let Some(cb) = &self.callback {
            cb.on_prepared(42_000_000); // 42s mock
            cb.on_playback_state_changed(PlaybackState::Preparing);
        }
    }

    pub fn play(&mut self) {
        if self.state == PlaybackState::Preparing || self.state == PlaybackState::Paused {
            self.state = PlaybackState::Playing;
            if let Some(cb) = &self.callback {
                cb.on_playback_state_changed(PlaybackState::Playing);
            }
        }
    }

    pub fn pause(&mut self) {
        if self.state == PlaybackState::Playing {
            self.state = PlaybackState::Paused;
            if let Some(cb) = &self.callback {
                cb.on_playback_state_changed(PlaybackState::Paused);
            }
        }
    }

    pub fn seek_to(&mut self, _position_us: i64) {
        // Stub: do nothing
    }

    pub fn get_current_position(&self) -> i64 {
        // Stub
        0
    }

    pub fn get_duration(&self) -> i64 {
        // Stub
        42_000_000
    }

    pub fn set_surface(&mut self, surface: *mut c_void) {
        self.surface = surface;
    }

    pub fn load_subtitle(&mut self, _path: &str) -> Result<(), EngineError> {
        // Stub
        Ok(())
    }

    pub fn set_subtitle_offset(&mut self, _offset_ms: i64) {
        // Stub
    }

    pub fn load_plugin(&mut self, _so_path: &str) -> Result<(), EngineError> {
        // Stub: later we'll use libloading
        Err(EngineError::PluginLoadError("plugin loading not yet implemented".into()))
    }

    pub fn set_event_callback(&mut self, callback: Box<dyn EngineCallback>) {
        self.callback = Some(callback);
    }
}