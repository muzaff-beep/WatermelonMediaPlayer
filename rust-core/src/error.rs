#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EngineError {
    /// Generic I/O or file error.
    Io(String),
    /// The data source URI is invalid or unsupported.
    InvalidSource(String),
    /// A required codec was not found.
    CodecNotFound(String),
    /// Plugin loading failed.
    PluginLoadError(String),
    /// An unexpected internal state was detected.
    Internal(String),
}

impl std::fmt::Display for EngineError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            EngineError::Io(msg) => write!(f, "I/O error: {}", msg),
            EngineError::InvalidSource(msg) => write!(f, "Invalid source: {}", msg),
            EngineError::CodecNotFound(msg) => write!(f, "Codec not found: {}", msg),
            EngineError::PluginLoadError(msg) => write!(f, "Plugin load error: {}", msg),
            EngineError::Internal(msg) => write!(f, "Internal error: {}", msg),
        }
    }
}