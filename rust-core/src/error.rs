// rust-core/src/error.rs
use thiserror::Error;

#[derive(Error, Debug)]
pub enum EngineError {
    #[error("Media source not found: {0}")]
    SourceNotFound(String),

    #[error("Invalid data source URI: {0}")]
    InvalidUri(String),

    #[error("Decoder initialization failed")]
    DecoderInitFailed,

    #[error("Audio output initialization failed")]
    AudioInitFailed,

    #[error("Subtitle parse error: {0}")]
    SubtitleError(String),

    #[error("Plugin load error: {0}")]
    PluginLoadError(String),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Internal error: {0}")]
    Internal(String),
}

pub type EngineResult<T> = Result<T, EngineError>;