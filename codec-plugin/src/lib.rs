// codec-plugin/src/lib.rs
// Frozen CodecPlugin trait. Plugin authors implement this.
// Manifesto §3.2 — single source of truth for plugin ABI.

/// Pixel format for decoded frames. Explicit `#[repr(u8)]` for FFI safety.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum PixelFormat {
    YUV420P = 0,
    NV12 = 1,
    RGBA8 = 2,
}

/// A decoded video frame produced by a plugin.
#[derive(Debug, Clone)]
pub struct DecodedFrame {
    pub width: u32,
    pub height: u32,
    pub pixel_format: PixelFormat,
    pub data: Vec<u8>,
    pub pts_us: i64,
}

/// Every codec plugin must implement this trait and export:
/// `#[no_mangle] pub extern "C" fn watermelon_plugin_create() -> *mut dyn CodecPlugin`
pub trait CodecPlugin: Send + Sync {
    /// Unique codec name, e.g. "av1-software".
    fn codec_name(&self) -> &str;

    /// MIME types this plugin handles, e.g. ["video/av01", "video/AV1"].
    fn supported_mime_types(&self) -> Vec<String>;

    /// Decode one frame from raw encoded data. Returns None if more data is needed.
    fn decode_frame(&mut self, data: &[u8]) -> Option<DecodedFrame>;

    /// Flush decoder state after a seek.
    fn flush(&mut self);
}