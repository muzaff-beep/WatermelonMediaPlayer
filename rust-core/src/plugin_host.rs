// rust-core/src/plugin_host.rs
// Dynamic codec plugin loader and dispatch. Manifesto §3.2 trait, §3.5 binary protocol.

use crate::error::{EngineError, EngineResult};
use std::collections::HashMap;
use std::ffi::{c_void, CStr, CString};
use std::path::Path;

// ---------------------------------------------------------------------------
// Frozen CodecPlugin trait — Manifesto §3.2
// ---------------------------------------------------------------------------

/// Pixel format for decoded frames. Must match Manifesto §3.2 `PixelFormat` enum.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum PluginPixelFormat {
    YUV420P = 0,
    NV12 = 1,
    RGBA8 = 2,
}

/// Decoded frame returned by a plugin's `decode_frame`.
#[derive(Debug, Clone)]
pub struct PluginDecodedFrame {
    pub width: u32,
    pub height: u32,
    pub pixel_format: PluginPixelFormat,
    pub data: Vec<u8>,
    pub pts_us: i64,
}

/// The trait every external codec plugin must implement.
/// Plugin authors implement this trait; the host loads it dynamically.
pub trait CodecPlugin: Send + Sync {
    /// Unique codec name, e.g. "av1-software".
    fn codec_name(&self) -> &str;

    /// MIME types this plugin handles, e.g. ["video/av01", "video/AV1"].
    fn supported_mime_types(&self) -> Vec<String>;

    /// Decode one frame from raw encoded data. Returns None if more data needed.
    fn decode_frame(&mut self, data: &[u8]) -> Option<PluginDecodedFrame>;

    /// Flush decoder state after seek.
    fn flush(&mut self);
}

/// Every plugin must export this function with `#[no_mangle]`.
/// Signature: returns a raw pointer to a heap-allocated `dyn CodecPlugin`.
pub type PluginCreateFn = extern "C" fn() -> *mut dyn CodecPlugin;

// ---------------------------------------------------------------------------
// Binary message protocol — Manifesto §3.5
// ---------------------------------------------------------------------------

/// Message types for inter-process plugin communication.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
enum PluginMessageType {
    Init = 0x01,
    DecodeFrame = 0x02,
    Flush = 0x03,
    DecodedFrame = 0x04,
}

/// Serialize a `PluginDecodedFrame` to binary per Manifesto §3.5 layout:
/// [4 bytes width LE] [4 bytes height LE] [1 byte pixel_format]
/// [8 bytes pts_us LE] [4 bytes data_len LE] [data bytes]
fn serialize_decoded_frame(frame: &PluginDecodedFrame) -> Vec<u8> {
    let mut buf = Vec::with_capacity(21 + frame.data.len());
    buf.extend_from_slice(&frame.width.to_le_bytes());
    buf.extend_from_slice(&frame.height.to_le_bytes());
    buf.push(frame.pixel_format as u8);
    buf.extend_from_slice(&frame.pts_us.to_le_bytes());
    buf.extend_from_slice(&(frame.data.len() as u32).to_le_bytes());
    buf.extend_from_slice(&frame.data);
    buf
}

/// Deserialize a `PluginDecodedFrame` from binary.
fn deserialize_decoded_frame(data: &[u8]) -> Option<PluginDecodedFrame> {
    if data.len() < 21 {
        return None;
    }
    let width = u32::from_le_bytes([data[0], data[1], data[2], data[3]]);
    let height = u32::from_le_bytes([data[4], data[5], data[6], data[7]]);
    let pixel_format = match data[8] {
        0 => PluginPixelFormat::YUV420P,
        1 => PluginPixelFormat::NV12,
        2 => PluginPixelFormat::RGBA8,
        _ => return None,
    };
    let pts_us = i64::from_le_bytes([
        data[9], data[10], data[11], data[12], data[13], data[14], data[15], data[16],
    ]);
    let data_len = u32::from_le_bytes([data[17], data[18], data[19], data[20]]) as usize;
    if data.len() < 21 + data_len {
        return None;
    }
    let frame_data = data[21..21 + data_len].to_vec();
    Some(PluginDecodedFrame {
        width,
        height,
        pixel_format,
        data: frame_data,
        pts_us,
    })
}

/// Build a binary message per Manifesto §3.5:
/// [1 byte type] [2 bytes big-endian payload length] [payload bytes]
fn build_message(msg_type: PluginMessageType, payload: &[u8]) -> Vec<u8> {
    let len = payload.len() as u16;
    let mut msg = Vec::with_capacity(3 + payload.len());
    msg.push(msg_type as u8);
    msg.extend_from_slice(&len.to_be_bytes());
    msg.extend_from_slice(payload);
    msg
}

/// Parse a binary message header. Returns (msg_type, payload_length) if valid.
fn parse_message_header(data: &[u8]) -> Option<(PluginMessageType, usize)> {
    if data.len() < 3 {
        return None;
    }
    let msg_type = match data[0] {
        0x01 => PluginMessageType::Init,
        0x02 => PluginMessageType::DecodeFrame,
        0x03 => PluginMessageType::Flush,
        0x04 => PluginMessageType::DecodedFrame,
        _ => return None,
    };
    let payload_len = u16::from_be_bytes([data[1], data[2]]) as usize;
    Some((msg_type, payload_len))
}

// ---------------------------------------------------------------------------
// PluginHost — manages all loaded plugins
// ---------------------------------------------------------------------------

/// Maps MIME type → plugin index for fast dispatch.
pub struct PluginHost {
    plugins: Vec<Box<dyn CodecPlugin>>,
    mime_map: HashMap<String, usize>,
    loaded_libs: Vec<*mut c_void>,
}

impl PluginHost {
    /// Create an empty plugin host.
    pub fn new() -> Self {
        Self {
            plugins: Vec::new(),
            mime_map: HashMap::new(),
            loaded_libs: Vec::new(),
        }
    }

    /// Load a plugin from a shared library path.
    /// The library must export `watermelon_plugin_create`.
    pub fn load(&mut self, so_path: &str) -> EngineResult<()> {
        let path = CString::new(so_path).map_err(|e| {
            EngineError::PluginLoadError(format!("Invalid path: {}", e))
        })?;

        // Verify file exists
        if !Path::new(so_path).exists() {
            return Err(EngineError::PluginLoadError(format!(
                "Plugin file not found: {}",
                so_path
            )));
        }

        #[cfg(target_os = "android")]
        {
            use std::os::unix::ffi::OsStrExt;
            let lib = unsafe { libc::dlopen(path.as_ptr(), libc::RTLD_NOW) };
            if lib.is_null() {
                let err = unsafe { CStr::from_ptr(libc::dlerror()) };
                return Err(EngineError::PluginLoadError(format!(
                    "dlopen failed: {:?}",
                    err
                )));
            }

            let create_fn_name = CString::new("watermelon_plugin_create").unwrap();
            let create_fn: PluginCreateFn = unsafe {
                let ptr = libc::dlsym(lib, create_fn_name.as_ptr());
                if ptr.is_null() {
                    libc::dlclose(lib);
                    return Err(EngineError::PluginLoadError(
                        "watermelon_plugin_create symbol not found".into(),
                    ));
                }
                std::mem::transmute(ptr)
            };

            let plugin_ptr: *mut dyn CodecPlugin = create_fn();
            if plugin_ptr.is_null() {
                libc::dlclose(lib);
                return Err(EngineError::PluginLoadError(
                    "Plugin factory returned null".into(),
                ));
            }

            let plugin: Box<dyn CodecPlugin> = unsafe { Box::from_raw(plugin_ptr) };
            let codec_name = plugin.codec_name().to_owned();
            let mime_types = plugin.supported_mime_types();

            let idx = self.plugins.len();
            for mime in &mime_types {
                self.mime_map.insert(mime.clone(), idx);
            }
            self.plugins.push(plugin);
            self.loaded_libs.push(lib);

            log::info!(
                "Plugin loaded: {} (handles {} MIME types)",
                codec_name,
                mime_types.len()
            );
        }

        #[cfg(not(target_os = "android"))]
        {
            return Err(EngineError::PluginLoadError(
                "Dynamic plugin loading only supported on Android".into(),
            ));
        }

        Ok(())
    }

    /// Find a plugin that handles the given MIME type.
    pub fn find_for_mime(&self, mime: &str) -> Option<&dyn CodecPlugin> {
        let idx = self.mime_map.get(mime)?;
        Some(self.plugins[*idx].as_ref())
    }

    /// Get mutable access to a plugin by MIME type.
    pub fn find_for_mime_mut(&mut self, mime: &str) -> Option<&mut dyn CodecPlugin> {
        let idx = self.mime_map.get(mime).copied()?;
        Some(self.plugins[idx].as_mut())
    }

    /// Number of loaded plugins.
    pub fn plugin_count(&self) -> usize {
        self.plugins.len()
    }

    /// List all loaded plugin codec names.
    pub fn list_plugins(&self) -> Vec<String> {
        self.plugins.iter().map(|p| p.codec_name().to_owned()).collect()
    }
}

// ---------------------------------------------------------------------------
// Drop — unload all dynamic libraries
// ---------------------------------------------------------------------------

impl Drop for PluginHost {
    fn drop(&mut self) {
        // Drop plugins first (they may reference library memory)
        self.plugins.clear();
        // Unload libraries in reverse load order
        for lib in self.loaded_libs.drain(..).rev() {
            #[cfg(target_os = "android")]
            unsafe {
                libc::dlclose(lib);
            }
        }
        log::debug!("PluginHost: all plugins unloaded");
    }
}

// Safety: PluginHost holds raw library handles. It is Send because
// libc::dlopen/dlclose are thread-safe on Android.
unsafe impl Send for PluginHost {}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    // Mock plugin for testing
    struct MockPlugin {
        name: String,
        mimes: Vec<String>,
    }

    impl CodecPlugin for MockPlugin {
        fn codec_name(&self) -> &str {
            &self.name
        }
        fn supported_mime_types(&self) -> Vec<String> {
            self.mimes.clone()
        }
        fn decode_frame(&mut self, _data: &[u8]) -> Option<PluginDecodedFrame> {
            Some(PluginDecodedFrame {
                width: 64,
                height: 64,
                pixel_format: PluginPixelFormat::RGBA8,
                data: vec![0u8; 64 * 64 * 4],
                pts_us: 0,
            })
        }
        fn flush(&mut self) {}
    }

    #[test]
    fn test_plugin_host_new_is_empty() {
        let host = PluginHost::new();
        assert_eq!(host.plugin_count(), 0);
        assert!(host.list_plugins().is_empty());
    }

    #[test]
    fn test_serialize_deserialize_frame_roundtrip() {
        let frame = PluginDecodedFrame {
            width: 1920,
            height: 1080,
            pixel_format: PluginPixelFormat::NV12,
            data: vec![0xABu8; 100],
            pts_us: 4_200_000,
        };
        let serialized = serialize_decoded_frame(&frame);
        let deserialized = deserialize_decoded_frame(&serialized).unwrap();
        assert_eq!(deserialized.width, 1920);
        assert_eq!(deserialized.height, 1080);
        assert_eq!(deserialized.pixel_format, PluginPixelFormat::NV12);
        assert_eq!(deserialized.pts_us, 4_200_000);
        assert_eq!(deserialized.data.len(), 100);
        assert_eq!(deserialized.data[0], 0xAB);
    }

    #[test]
    fn test_deserialize_frame_truncated_returns_none() {
        let data = vec![0u8; 10];
        assert!(deserialize_decoded_frame(&data).is_none());
    }

    #[test]
    fn test_build_and_parse_message() {
        let payload = b"hello";
        let msg = build_message(PluginMessageType::Init, payload);
        let (msg_type, len) = parse_message_header(&msg).unwrap();
        assert_eq!(msg_type, PluginMessageType::Init);
        assert_eq!(len, 5);
        assert_eq!(&msg[3..8], b"hello");
    }

    #[test]
    fn test_parse_message_header_too_short() {
        assert!(parse_message_header(&[0x01]).is_none());
        assert!(parse_message_header(&[0x01, 0x00]).is_none());
    }

    #[test]
    fn test_parse_message_header_unknown_type() {
        assert!(parse_message_header(&[0xFF, 0x00, 0x05]).is_none());
    }

    #[test]
    fn test_message_roundtrip_all_types() {
        let types = [
            PluginMessageType::Init,
            PluginMessageType::DecodeFrame,
            PluginMessageType::Flush,
            PluginMessageType::DecodedFrame,
        ];
        for t in &types {
            let msg = build_message(*t, b"test");
            let (parsed, _) = parse_message_header(&msg).unwrap();
            assert_eq!(parsed, *t);
        }
    }

    #[test]
    fn test_pixel_format_discriminants() {
        assert_eq!(PluginPixelFormat::YUV420P as u8, 0);
        assert_eq!(PluginPixelFormat::NV12 as u8, 1);
        assert_eq!(PluginPixelFormat::RGBA8 as u8, 2);
    }
}