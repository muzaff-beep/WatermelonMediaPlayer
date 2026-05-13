use watermelon_core::{EngineCallback, MediaEngine, PlaybackState, SubtitleCue};
use std::sync::Mutex;

struct TestCallback {
    state_changes: Mutex<Vec<PlaybackState>>,
    prepared_duration: Mutex<Option<i64>>,
}

impl TestCallback {
    fn new() -> Self {
        TestCallback {
            state_changes: Mutex::new(Vec::new()),
            prepared_duration: Mutex::new(None),
        }
    }
}

impl EngineCallback for TestCallback {
    fn on_prepared(&self, duration_us: i64) {
        *self.prepared_duration.lock().unwrap() = Some(duration_us);
    }

    fn on_playback_state_changed(&self, state: PlaybackState) {
        self.state_changes.lock().unwrap().push(state);
    }

    fn on_error(&self, _code: i32, _message: &str) {
        // Ignore
    }

    fn on_subtitle_cues(&self, _cues: Vec<SubtitleCue>) {}
}

#[test]
fn test_engine_lifecycle() {
    let mut engine = MediaEngine::new();
    let callback = TestCallback::new();

    // Set empty URI should fail
    assert!(engine.set_data_source("").is_err());

    // Set valid URI
    engine.set_data_source("file:///test.mkv").unwrap();
    engine.set_event_callback(Box::new(callback));

    engine.prepare();
    // After prepare, state should be Preparing, and callback should have been called with a duration
    let states = callback.state_changes.lock().unwrap();
    assert!(states.contains(&PlaybackState::Preparing));
    let duration = *callback.prepared_duration.lock().unwrap();
    assert_eq!(duration, Some(42_000_000));

    // Now play
    engine.play();
    let states = callback.state_changes.lock().unwrap();
    assert!(states.contains(&PlaybackState::Playing));

    // Pause
    engine.pause();
    let states = callback.state_changes.lock().unwrap();
    assert!(states.contains(&PlaybackState::Paused));
}