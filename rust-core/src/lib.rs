pub mod error;
pub mod types;
pub mod callback;
pub mod engine;

// Future modules (comment them out until created):
// pub mod decoder;
// pub mod audio;
// pub mod subtitle;
// pub mod plugin_host;
// pub mod playlist_parser;
// pub mod jni_bridge;

pub use engine::MediaEngine;
pub use error::EngineError;
pub use types::{PlaybackState, SubtitleCue};
pub use callback::EngineCallback;