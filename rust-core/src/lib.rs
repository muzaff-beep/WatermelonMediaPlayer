// rust-core/src/lib.rs
mod audio;
mod callback;
mod decoder;
mod engine;
mod error;
mod jni_bridge;
mod playlist_parser;
mod plugin_host;
mod subtitle;
use std::sync::Once;

static INIT_LOGGER: Once = Once::new();
pub(crate) fn init_logger() {
    INIT_LOGGER.call_once(|| {
        android_logger::init_once(android_logger::Config::default().with_max_level(log::LevelFilter::Debug).with_tag("WatermelonCore"));
    });
}