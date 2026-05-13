// rust-core/src/callback.rs
use crate::engine::{PlaybackState, SubtitleCue};
use jni::objects::GlobalRef;
use serde::Serialize;

#[derive(Serialize)] struct PreparedPayload { event: &'static str, #[serde(rename = "durationUs")] duration_us: i64 }
#[derive(Serialize)] struct StatePayload { event: &'static str, state: i32 }
#[derive(Serialize)] struct ErrorPayload { event: &'static str, code: i32, message: String }
#[derive(Serialize)] struct CuesPayload { event: &'static str, cues: Vec<CueEntry> }
#[derive(Serialize)] struct CueEntry { #[serde(rename = "startUs")] start_us: i64, #[serde(rename = "endUs")] end_us: i64, text: String }

pub struct CallbackDispatcher { kotlin_callback: GlobalRef }

impl CallbackDispatcher {
    pub fn new(kotlin_callback: GlobalRef) -> Self { Self { kotlin_callback } }

    pub fn on_prepared(&self, duration_us: i64) {
        let json = serde_json::to_string(&PreparedPayload { event: "prepared", duration_us }).unwrap_or_default();
        self.call_kotlin("onPrepared", &json);
    }
    pub fn on_playback_state_changed(&self, state: PlaybackState) {
        let json = serde_json::to_string(&StatePayload { event: "state", state: state as i32 }).unwrap_or_default();
        self.call_kotlin("onPlaybackStateChanged", &json);
    }
    pub fn on_error(&self, code: i32, message: &str) {
        let json = serde_json::to_string(&ErrorPayload { event: "error", code, message: message.to_owned() }).unwrap_or_default();
        self.call_kotlin("onError", &json);
    }
    pub fn on_subtitle_cues(&self, cues: &[SubtitleCue]) {
        let entries: Vec<CueEntry> = cues.iter().map(|c| CueEntry { start_us: c.start_us, end_us: c.end_us, text: c.text.clone() }).collect();
        let json = serde_json::to_string(&CuesPayload { event: "cues", cues: entries }).unwrap_or_default();
        self.call_kotlin("onSubtitleCues", &json);
    }

    fn call_kotlin(&self, method_name: &str, json: &str) {
        let obj = self.kotlin_callback.as_obj();
        let jvm = unsafe { jni::JavaVM::from_raw(obj.as_raw() as *mut _) };
        match jvm {
            Ok(vm) => {
                if let Ok(mut env) = vm.attach_current_thread() {
                    if let Ok(jstring) = env.new_string(json) {
                        let _ = env.call_method(obj, method_name, "(Ljava/lang/String;)V", &[(&jstring).into()]);
                    }
                }
            }
            Err(e) => log::error!("callback: JavaVM failed: {:?}", e),
        }
    }
}

unsafe impl Send for CallbackDispatcher {}