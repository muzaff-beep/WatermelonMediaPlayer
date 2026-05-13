// rust-core/src/jni_bridge.rs
// JNI function implementations. Each function bridges a Kotlin call
// through to the MediaEngine. Manifesto §3.1 frozen signatures.

use crate::engine::MediaEngine;
use crate::error::EngineError;
use jni::objects::{GlobalRef, JClass, JObject, JString};
use jni::JNIEnv;
use std::ffi::c_void;

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

/// Create a new engine and return opaque pointer.
#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) -> i64 {
    crate::init_logger();
    let engine = Box::new(MediaEngine::new());
    log::info!("nativeInit: engine created");
    Box::into_raw(engine) as i64
}

/// Destroy engine and free all resources.
#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
) {
    if engine_ptr == 0 {
        return;
    }
    unsafe {
        let _ = Box::from_raw(engine_ptr as *mut MediaEngine);
    }
    log::info!("nativeDestroy: engine destroyed");
}

// ---------------------------------------------------------------------------
// Source and control
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetDataSource(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
    uri: JString,
) -> u8 {
    let uri: String = match env.get_string(&uri) {
        Ok(s) => s.into(),
        Err(e) => {
            log::error!("nativeSetDataSource: failed to read URI: {:?}", e);
            return 0;
        }
    };
    if uri.is_empty() {
        return 0;
    }
    unsafe {
        let engine = engine_ptr_as_mut(engine_ptr);
        match engine {
            Some(e) => e.set_data_source(&uri).is_ok() as u8,
            None => 0,
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativePrepare(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
) {
    unsafe {
        if let Some(engine) = engine_ptr_as_mut(engine_ptr) {
            engine.prepare();
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativePlay(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
) {
    unsafe {
        if let Some(engine) = engine_ptr_as_mut(engine_ptr) {
            engine.play();
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativePause(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
) {
    unsafe {
        if let Some(engine) = engine_ptr_as_mut(engine_ptr) {
            engine.pause();
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSeekTo(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
    position_us: i64,
) {
    unsafe {
        if let Some(engine) = engine_ptr_as_mut(engine_ptr) {
            engine.seek_to(position_us);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeGetCurrentPosition(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
) -> i64 {
    unsafe {
        let engine = engine_ptr_as_ref(engine_ptr);
        engine.map_or(0, |e| e.get_current_position())
    }
}

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeGetDuration(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
) -> i64 {
    unsafe {
        let engine = engine_ptr_as_ref(engine_ptr);
        engine.map_or(0, |e| e.get_duration())
    }
}

// ---------------------------------------------------------------------------
// Surface
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSurface(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
    surface: JObject,
) {
    unsafe {
        if let Some(engine) = engine_ptr_as_mut(engine_ptr) {
            let native_window = if surface.is_null() {
                std::ptr::null_mut()
            } else {
                // Extract ANativeWindow from Android Surface JObject.
                // The ndk crate provides this conversion.
                #[cfg(target_os = "android")]
                {
                    ndk_sys::ANativeWindow_fromSurface(
                        _env.get_native_interface() as *mut _,
                        surface.into_raw(),
                    )
                }
                #[cfg(not(target_os = "android"))]
                {
                    std::ptr::null_mut()
                }
            };
            engine.set_surface(native_window);
        }
    }
}

// ---------------------------------------------------------------------------
// Subtitles
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeLoadSubtitle(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
    path: JString,
) -> u8 {
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(e) => {
            log::error!("nativeLoadSubtitle: failed to read path: {:?}", e);
            return 0;
        }
    };
    if path.is_empty() {
        return 0;
    }
    unsafe {
        let engine = engine_ptr_as_mut(engine_ptr);
        match engine {
            Some(e) => e.load_subtitle(&path).is_ok() as u8,
            None => 0,
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSubtitleOffset(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
    offset_ms: i64,
) {
    unsafe {
        if let Some(engine) = engine_ptr_as_mut(engine_ptr) {
            engine.set_subtitle_offset(offset_ms);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSubtitleFont(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
    font_path: JString,
) {
    let path: String = match env.get_string(&font_path) {
        Ok(s) => s.into(),
        Err(e) => {
            log::error!("nativeSetSubtitleFont: failed to read path: {:?}", e);
            return;
        }
    };
    if path.is_empty() {
        return;
    }
    unsafe {
        if let Some(engine) = engine_ptr_as_mut(engine_ptr) {
            engine.set_subtitle_font(&path);
        }
    }
}

// ---------------------------------------------------------------------------
// Plugin management
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeLoadPlugin(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
    so_path: JString,
) -> u8 {
    let path: String = match env.get_string(&so_path) {
        Ok(s) => s.into(),
        Err(e) => {
            log::error!("nativeLoadPlugin: failed to read path: {:?}", e);
            return 0;
        }
    };
    if path.is_empty() {
        return 0;
    }
    unsafe {
        let engine = engine_ptr_as_mut(engine_ptr);
        match engine {
            Some(e) => e.load_plugin(&path).is_ok() as u8,
            None => 0,
        }
    }
}

// ---------------------------------------------------------------------------
// Callbacks
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetEventCallback(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: i64,
    callback: JObject,
) {
    let callback_global = if callback.is_null() {
        None
    } else {
        match env.new_global_ref(callback) {
            Ok(g) => Some(g),
            Err(e) => {
                log::error!("nativeSetEventCallback: new_global_ref failed: {:?}", e);
                return;
            }
        }
    };
    unsafe {
        if let Some(engine) = engine_ptr_as_mut(engine_ptr) {
            engine.set_event_callback(callback_global);
        }
    }
}

// ---------------------------------------------------------------------------
// Helper: pointer casts
// ---------------------------------------------------------------------------

/// Cast raw engine pointer to mutable reference. Returns None if null.
unsafe fn engine_ptr_as_mut(ptr: i64) -> Option<&'static mut MediaEngine> {
    if ptr == 0 {
        None
    } else {
        (ptr as *mut MediaEngine).as_mut()
    }
}

/// Cast raw engine pointer to immutable reference. Returns None if null.
unsafe fn engine_ptr_as_ref(ptr: i64) -> Option<&'static MediaEngine> {
    if ptr == 0 {
        None
    } else {
        (ptr as *const MediaEngine).as_ref()
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_engine_ptr_as_mut_null_returns_none() {
        unsafe {
            assert!(engine_ptr_as_mut(0).is_none());
        }
    }

    #[test]
    fn test_engine_ptr_as_ref_null_returns_none() {
        unsafe {
            assert!(engine_ptr_as_ref(0).is_none());
        }
    }

    #[test]
    fn test_engine_ptr_roundtrip() {
        let engine = Box::new(MediaEngine::new());
        let ptr = Box::into_raw(engine) as i64;
        assert_ne!(ptr, 0);
        unsafe {
            let engine_ref = engine_ptr_as_mut(ptr);
            assert!(engine_ref.is_some());
            // Reclaim to avoid leak
            let _ = Box::from_raw(ptr as *mut MediaEngine);
        }
    }
}