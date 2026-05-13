use crate::types::{PlaybackState, SubtitleCue};

pub trait EngineCallback: Send {
    fn on_prepared(&self, duration_us: i64);
    fn on_playback_state_changed(&self, state: PlaybackState);
    fn on_error(&self, code: i32, message: &str);
    fn on_subtitle_cues(&self, cues: Vec<SubtitleCue>);
}