// rust-core/src/callback.rs
// Marshals engine events from Rust to Kotlin via JNI GlobalRef.
// Manifesto §3.6 JSON formats implemented.

use crate::engine::{PlaybackState, SubtitleCue};
use jni::objects::GlobalRef;
use jni::JNIEnv;
use serde::Serialize;

/// Serializable payload for onPrepared callback.
#[derive(Serialize)]
struct PreparedPayload {
    event: &'static str,
    #[serde(rename = "durationUs")]
    duration_us: i64,
}

/// Serializable payload for onPlaybackStateChanged callback.
#[derive(Serialize)]
struct StatePayload {
    event: &'static str,
    state: i32,
}

/// Serializable payload for onError callback.
#[derive(Serialize)]
struct ErrorPayload {
    event: &'static str,
    code: i32,
    message: String,
}

/// Serializable payload for onSubtitleCues callback.
#[derive(Serialize)]
struct CuesPayload {
    event: &'static str,
    cues: Vec<CueEntry>,
}

#[derive(Serialize)]
struct CueEntry {
    #[serde(rename = "startUs")]
    start_us: i64,
    #[serde(rename = "endUs")]
    end_us: i64,
    text: String,
}

/// Dispatches engine callbacks to the Kotlin `WatermelonEventCallback` interface.
/// Holds a `GlobalRef` to the Kotlin callback object. All methods invoke
/// the corresponding method on that object via JNI, passing a JSON string argument.
pub struct CallbackDispatcher {
    kotlin_callback: GlobalRef,
}

impl CallbackDispatcher {
    /// Create a new dispatcher wrapping the given Kotlin callback `GlobalRef`.
    pub fn new(kotlin_callback: GlobalRef) -> Self {
        Self { kotlin_callback }
    }

    /// Notify Kotlin that preparation is complete.
    pub fn on_prepared(&self, duration_us: i64) {
        let payload = PreparedPayload {
            event: "prepared",
            duration_us,
        };
        let json = serde_json::to_string(&payload).unwrap_or_else(|e| {
            log::error!("on_prepared: JSON serialization failed: {}", e);
            r#"{"event":"prepared","durationUs":0}"#.to_owned()
        });
        self.call_kotlin("onPrepared", &json);
    }

    /// Notify Kotlin of a playback state change.
    pub fn on_playback_state_changed(&self, state: PlaybackState) {
        let payload = StatePayload {
            event: "state",
            state: state as i32,
        };
        let json = serde_json::to_string(&payload).unwrap_or_else(|e| {
            log::error!("on_playback_state_changed: JSON failed: {}", e);
            r#"{"event":"state","state":0}"#.to_owned()
        });
        self.call_kotlin("onPlaybackStateChanged", &json);
    }

    /// Notify Kotlin of an error.
    pub fn on_error(&self, code: i32, message: &str) {
        let payload = ErrorPayload {
            event: "error",
            code,
            message: message.to_owned(),
        };
        let json = serde_json::to_string(&payload).unwrap_or_else(|e| {
            log::error!("on_error: JSON failed: {}", e);
            format!(r#"{{"event":"error","code":{},"message":"serialization failed"}}"#, code)
        });
        self.call_kotlin("onError", &json);
    }

    /// Notify Kotlin of subtitle cues at current position.
    pub fn on_subtitle_cues(&self, cues: &[SubtitleCue]) {
        let entries: Vec<CueEntry> = cues
            .iter()
            .map(|c| CueEntry {
                start_us: c.start_us,
                end_us: c.end_us,
                text: c.text.clone(),
            })
            .collect();
        let payload = CuesPayload {
            event: "cues",
            cues: entries,
        };
        let json = serde_json::to_string(&payload).unwrap_or_else(|e| {
            log::error!("on_subtitle_cues: JSON failed: {}", e);
            r#"{"event":"cues","cues":[]}"#.to_owned()
        });
        self.call_kotlin("onSubtitleCues", &json);
    }

    /// Low-level JNI invocation. Attaches to the JVM thread, retrieves
    /// the Kotlin object, and calls the named method with a single String parameter.
    fn call_kotlin(&self, method_name: &str, json: &str) {
        // Obtain JNIEnv via the JavaVM stored in the GlobalRef's context.
        // The GlobalRef itself is a jobject pointing to the callback instance.
        let jvm = {
            let guard = self.kotlin_callback.as_obj();
            match unsafe { jni::JavaVM::from_raw(guard.get_raw() as _) } {
                Ok(vm) => vm,
                Err(e) => {
                    log::error!("call_kotlin: failed to get JavaVM: {:?}", e);
                    return;
                }
            }
        };
        let mut env = match jvm.attach_current_thread() {
            Ok(e) => e,
            Err(e) => {
                log::error!("call_kotlin: attach_current_thread failed: {:?}", e);
                return;
            }
        };
        let obj = self.kotlin_callback.as_obj();
        let jstring = match env.new_string(json) {
            Ok(s) => s,
            Err(e) => {
                log::error!("call_kotlin: new_string failed: {:?}", e);
                return;
            }
        };
        let class = match env.get_object_class(obj) {
            Ok(c) => c,
            Err(e) => {
                log::error!("call_kotlin: get_object_class failed: {:?}", e);
                return;
            }
        };
        let sig = "(Ljava/lang/String;)V";
        if let Err(e) = env.call_method(obj, method_name, sig, &[(&jstring).into()]) {
            log::error!("call_kotlin: call_method '{}' failed: {:?}", method_name, e);
        }
        // Jstring, class, and env are dropped on return; no explicit cleanup needed
        // beyond what JNI local frame management handles.
    }
}

/// `CallbackDispatcher` is `Send` because `GlobalRef` is `Send`.
/// It is not `Sync` — the caller must wrap in `Arc<Mutex<...>>`.
unsafe impl Send for CallbackDispatcher {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_prepared_payload_json() {
        let payload = PreparedPayload {
            event: "prepared",
            duration_us: 12_345_678,
        };
        let json = serde_json::to_string(&payload).unwrap();
        assert!(json.contains("\"event\":\"prepared\""));
        assert!(json.contains("\"durationUs\":12345678"));
    }

    #[test]
    fn test_state_payload_json() {
        let payload = StatePayload {
            event: "state",
            state: 2,
        };
        let json = serde_json::to_string(&payload).unwrap();
        assert!(json.contains("\"state\":2"));
    }

    #[test]
    fn test_error_payload_json() {
        let payload = ErrorPayload {
            event: "error",
            code: 404,
            message: "file not found".into(),
        };
        let json = serde_json::to_string(&payload).unwrap();
        assert!(json.contains("\"code\":404"));
        assert!(json.contains("file not found"));
    }

    #[test]
    fn test_cues_payload_json() {
        let cues = vec![
            SubtitleCue {
                start_us: 0,
                end_us: 2_500_000,
                text: "Hello".into(),
            },
        ];
        let entries: Vec<CueEntry> = cues
            .iter()
            .map(|c| CueEntry {
                start_us: c.start_us,
                end_us: c.end_us,
                text: c.text.clone(),
            })
            .collect();
        let payload = CuesPayload {
            event: "cues",
            cues: entries,
        };
        let json = serde_json::to_string(&payload).unwrap();
        assert!(json.contains("\"startUs\":0"));
        assert!(json.contains("\"text\":\"Hello\""));
    }
}