/// Every plugin must export a function with the exact signature:
/// #[no_mangle] pub extern "C" fn watermelon_plugin_create() -> *mut dyn WatermelonPlugin
/// The returned pointer is cast to Box<dyn WatermelonPlugin> by the host.

pub trait WatermelonPlugin: Send + Sync {
    /// Unique name, e.g. "av1-software"
    fn codec_name(&self) -> &str;

    /// MIME types this plugin handles, e.g. ["video/av01", "video/AV1"]
    fn supported_mime_types(&self) -> Vec<String>;

    /// Decode one frame from raw data, returns None if more data needed
    fn decode_frame(&mut self, data: &[u8]) -> Option<DecodedFrame>;

    /// Flush decoder state (e.g., after seek)
    fn flush(&mut self);
}

pub struct DecodedFrame {
    pub width: u32,
    pub height: u32,
    pub pixel_format: PixelFormat,
    pub data: Vec<u8>,          // raw pixel data
    pub pts_us: i64,
}

pub enum PixelFormat {
    YUV420P,
    NV12,
    RGBA8,
}