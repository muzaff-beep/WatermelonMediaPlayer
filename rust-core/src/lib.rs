// rust-core/src/lib.rs
// Pylon-Forged. JNI entry points. Frozen signatures per Manifesto §3.1.

#![deny(
    clippy::all,
    clippy::pedantic,
    unsafe_code,
    missing_docs,
    unused_imports,
    unused_must_use,
    deprecated
)]

mod audio;
mod callback;
mod decoder;
mod engine;
mod error;
mod jni_bridge;
mod playlist_parser;
mod plugin_host;
mod subtitle;

use engine::MediaEngine;
use std::ffi::c_void;
use std::sync::Once;

static INIT_LOGGER: Once = Once::new();

/// Initialize the Android logger once per process lifetime.
fn init_logger() {
    INIT_LOGGER.call_once(|| {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Debug)
                .with_tag("WatermelonCore"),
        );
        log::info!("Watermelon MediaEngine logger initialized");
    });
}

/// Convert a raw engine pointer back to a boxed `MediaEngine`, consuming it.
/// Safety: `ptr` must be non-null and a valid pointer from `Box::into_raw`.
unsafe fn engine_from_ptr(ptr: i64) -> Box<MediaEngine> {
    Box::from_raw(ptr as *mut MediaEngine)
}

/// Allocate a new `MediaEngine` and return an opaque pointer to Kotlin.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeInit(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
) -> i64 {
    init_logger();
    let engine = Box::new(MediaEngine::new());
    log::info!("nativeInit: engine created");
    Box::into_raw(engine) as i64
}

/// Destroy the engine and free all resources.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeDestroy(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
) {
    if engine_ptr == 0 {
        return;
    }
    unsafe {
        drop(engine_from_ptr(engine_ptr));
    }
    log::info!("nativeDestroy: engine destroyed");
}

/// Set the media data source URI.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetDataSource(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
    uri: jni::objects::JString,
) -> u8 {
    let uri: String = env.get_string(&uri).map_or_else(
        |e| {
            log::error!("nativeSetDataSource: failed to read URI string: {:?}", e);
            String::new()
        },
        |s| s.into(),
    );
    if uri.is_empty() {
        return 0;
    }
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.set_data_source(&uri).is_ok() as u8
    }
}

/// Begin asynchronous preparation (demux, decode header, start audio).
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativePrepare(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
) {
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.prepare();
    }
}

/// Start or resume playback.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativePlay(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
) {
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.play();
    }
}

/// Pause playback.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativePause(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
) {
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.pause();
    }
}

/// Seek to a position in microseconds.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSeekTo(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
    position_us: i64,
) {
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.seek_to(position_us);
    }
}

/// Get current playback position in microseconds.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeGetCurrentPosition(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
) -> i64 {
    unsafe {
        let engine = &*(engine_ptr as *const MediaEngine);
        engine.as_ref().map_or(0, |e| e.get_current_position())
    }
}

/// Get media duration in microseconds.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeGetDuration(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
) -> i64 {
    unsafe {
        let engine = &*(engine_ptr as *const MediaEngine);
        engine.as_ref().map_or(0, |e| e.get_duration())
    }
}

/// Set the Android Surface for video rendering.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSurface(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
    surface: jni::objects::JObject,
) {
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        let native_window = jni::objects::JNIEnv::get_native_interface(&_env).get_java_vm
            as *mut c_void;
        // The surface object is passed to engine, which will extract
        // ANativeWindow via JNI when rendering is initialized.
        engine.set_surface(surface.as_raw() as *mut c_void);
    }
}

/// Load a subtitle file from the given path.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeLoadSubtitle(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
    path: jni::objects::JString,
) -> u8 {
    let path: String = env.get_string(&path).map_or_else(
        |e| {
            log::error!("nativeLoadSubtitle: failed to read path: {:?}", e);
            String::new()
        },
        |s| s.into(),
    );
    if path.is_empty() {
        return 0;
    }
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.load_subtitle(&path).is_ok() as u8
    }
}

/// Set subtitle offset in milliseconds.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSubtitleOffset(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
    offset_ms: i64,
) {
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.set_subtitle_offset(offset_ms);
    }
}

/// Set the subtitle font path for GPU-rendered text.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSubtitleFont(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
    font_path: jni::objects::JString,
) {
    let path: String = env.get_string(&font_path).map_or_else(
        |e| {
            log::error!("nativeSetSubtitleFont: failed to read path: {:?}", e);
            String::new()
        },
        |s| s.into(),
    );
    if path.is_empty() {
        return;
    }
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.set_subtitle_font(&path);
    }
}

/// Load a codec plugin from the given .so path.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeLoadPlugin(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
    so_path: jni::objects::JString,
) -> u8 {
    let path: String = env.get_string(&so_path).map_or_else(
        |e| {
            log::error!("nativeLoadPlugin: failed to read path: {:?}", e);
            String::new()
        },
        |s| s.into(),
    );
    if path.is_empty() {
        return 0;
    }
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.load_plugin(&path).is_ok() as u8
    }
}

/// Set the event callback interface from Kotlin.
#[no_mangle]
pub extern "C" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetEventCallback(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    engine_ptr: i64,
    callback: jni::objects::JObject,
) {
    let callback_global = env.new_global_ref(callback).ok();
    unsafe {
        let engine = &mut *((engine_ptr as *mut MediaEngine).as_mut().unwrap());
        engine.set_event_callback(callback_global);
    }
}