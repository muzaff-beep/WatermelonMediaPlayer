use watermelon_plugin::DecodedFrame;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PlaybackState {
    Idle,
    Preparing,
    Playing,
    Paused,
    Ended,
    Error,
}

#[derive(Debug, Clone)]
pub struct SubtitleCue {
    pub start_us: i64,
    pub end_us: i64,
    pub text: String,
}