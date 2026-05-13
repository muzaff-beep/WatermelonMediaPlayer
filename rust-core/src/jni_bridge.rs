use crate::engine::MediaEngine;
use jni::objects::{JClass, JObject, JString};
use jni::JNIEnv;

macro_rules! engine_mut {
    ($ptr:expr) => { unsafe { ($ptr as *mut MediaEngine).as_mut() } };
}

#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeInit(_: JNIEnv, _: JClass) -> i64 {
    crate::init_logger();
    Box::into_raw(Box::new(MediaEngine::new())) as i64
}
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeDestroy(_: JNIEnv, _: JClass, ptr: i64) {
    if ptr != 0 { unsafe { drop(Box::from_raw(ptr as *mut MediaEngine)); } }
}
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetDataSource(mut env: JNIEnv, _: JClass, ptr: i64, uri: JString) -> u8 {
    if let Ok(jstr) = env.get_string(&uri) {
        let s: String = jstr.into();
        engine_mut!(ptr).map(|e| e.set_data_source(&s).is_ok() as u8).unwrap_or(0)
    } else { 0 }
}
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativePrepare(_: JNIEnv, _: JClass, ptr: i64) { if let Some(e) = engine_mut!(ptr) { e.prepare(); } }
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativePlay(_: JNIEnv, _: JClass, ptr: i64) { if let Some(e) = engine_mut!(ptr) { e.play(); } }
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativePause(_: JNIEnv, _: JClass, ptr: i64) { if let Some(e) = engine_mut!(ptr) { e.pause(); } }
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSeekTo(_: JNIEnv, _: JClass, ptr: i64, pos: i64) { if let Some(e) = engine_mut!(ptr) { e.seek_to(pos); } }
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeGetCurrentPosition(_: JNIEnv, _: JClass, ptr: i64) -> i64 { unsafe { (ptr as *const MediaEngine).as_ref().map_or(0, |e| e.get_current_position()) } }
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeGetDuration(_: JNIEnv, _: JClass, ptr: i64) -> i64 { unsafe { (ptr as *const MediaEngine).as_ref().map_or(0, |e| e.get_duration()) } }
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSurface(_: JNIEnv, _: JClass, ptr: i64, _surface: JObject) { if let Some(e) = engine_mut!(ptr) { e.set_surface(std::ptr::null_mut()); } }
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeLoadSubtitle(mut env: JNIEnv, _: JClass, ptr: i64, path: JString) -> u8 {
    if let Ok(jstr) = env.get_string(&path) {
        let s: String = jstr.into();
        engine_mut!(ptr).map(|e| e.load_subtitle(&s).is_ok() as u8).unwrap_or(0)
    } else { 0 }
}
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSubtitleOffset(_: JNIEnv, _: JClass, ptr: i64, ms: i64) { if let Some(e) = engine_mut!(ptr) { e.set_subtitle_offset(ms); } }
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetSubtitleFont(mut env: JNIEnv, _: JClass, ptr: i64, path: JString) {
    if let Ok(jstr) = env.get_string(&path) {
        let s: String = jstr.into();
        if let Some(e) = engine_mut!(ptr) { e.set_subtitle_font(&s); }
    }
}
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeLoadPlugin(mut env: JNIEnv, _: JClass, ptr: i64, path: JString) -> u8 {
    if let Ok(jstr) = env.get_string(&path) {
        let s: String = jstr.into();
        engine_mut!(ptr).map(|e| e.load_plugin(&s).is_ok() as u8).unwrap_or(0)
    } else { 0 }
}
#[no_mangle] pub extern "system" fn Java_com_watermelon_player_rust_WatermelonCore_nativeSetEventCallback(mut env: JNIEnv, _: JClass, ptr: i64, cb: JObject) {
    let g = if cb.is_null() { None } else { env.new_global_ref(cb).ok() };
    if let Some(e) = engine_mut!(ptr) { e.set_event_callback(g); }
}