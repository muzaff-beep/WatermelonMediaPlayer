// rust-core/src/plugin_host.rs
use crate::error::{EngineError, EngineResult};
use std::collections::HashMap;
use std::ffi::{c_void, CStr, CString};
use std::path::Path;

#[derive(Debug, Clone, Copy, PartialEq, Eq)] #[repr(u8)] pub enum PluginPixelFormat { YUV420P = 0, NV12 = 1, RGBA8 = 2 }
#[derive(Debug, Clone)] pub struct PluginDecodedFrame { pub width: u32, pub height: u32, pub pixel_format: PluginPixelFormat, pub data: Vec<u8>, pub pts_us: i64 }

pub trait CodecPlugin: Send + Sync {
    fn codec_name(&self) -> &str;
    fn supported_mime_types(&self) -> Vec<String>;
    fn decode_frame(&mut self, data: &[u8]) -> Option<PluginDecodedFrame>;
    fn flush(&mut self);
}

pub type PluginCreateFn = extern "C" fn() -> *mut dyn CodecPlugin;

pub struct PluginHost {
    plugins: Vec<Box<dyn CodecPlugin>>,
    mime_map: HashMap<String, usize>,
    loaded_libs: Vec<*mut c_void>,
}

impl PluginHost {
    pub fn new() -> Self { Self { plugins: Vec::new(), mime_map: HashMap::new(), loaded_libs: Vec::new() } }

    pub fn load(&mut self, so_path: &str) -> EngineResult<()> {
        let path = CString::new(so_path).map_err(|e| EngineError::PluginLoadError(format!("path: {}", e)))?;
        if !Path::new(so_path).exists() { return Err(EngineError::PluginLoadError("not found".into())); }
        #[cfg(target_os = "android")]
        {
            let lib = unsafe { libc::dlopen(path.as_ptr(), libc::RTLD_NOW) };
            if lib.is_null() { return Err(EngineError::PluginLoadError("dlopen failed".into())); }
            let sym = CString::new("watermelon_plugin_create").unwrap();
            let create_fn: PluginCreateFn = unsafe {
                let ptr = libc::dlsym(lib, sym.as_ptr());
                if ptr.is_null() { libc::dlclose(lib); return Err(EngineError::PluginLoadError("symbol not found".into())); }
                std::mem::transmute(ptr)
            };
            let plugin_ptr = create_fn();
            if plugin_ptr.is_null() { unsafe { libc::dlclose(lib); } return Err(EngineError::PluginLoadError("null".into())); }
            let plugin: Box<dyn CodecPlugin> = unsafe { Box::from_raw(plugin_ptr) };
            let idx = self.plugins.len();
            for mime in &plugin.supported_mime_types() { self.mime_map.insert(mime.clone(), idx); }
            self.plugins.push(plugin);
            self.loaded_libs.push(lib);
        }
        Ok(())
    }

    pub fn plugin_count(&self) -> usize { self.plugins.len() }
}

impl Drop for PluginHost {
    fn drop(&mut self) {
        self.plugins.clear();
        for lib in self.loaded_libs.drain(..).rev() {
            #[cfg(target_os = "android")] unsafe { libc::dlclose(lib); }
        }
    }
}
unsafe impl Send for PluginHost {}