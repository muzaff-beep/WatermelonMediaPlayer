// rust-core/src/audio.rs
use crate::decoder::{AudioConfig, DecodedAudioFrame};
use crate::error::EngineResult;
use std::sync::{Mutex, OnceLock};

#[cfg(target_os = "android")]
use oboe::{AudioOutputStreamSafe, AudioStreamBuilder, DataCallbackResult, PerformanceMode, SharingMode, Direction, Stereo};

static AUDIO_ENGINE: OnceLock<Mutex<AudioEngine>> = OnceLock::new();

struct AudioEngine {
    #[allow(dead_code)]
    config: AudioConfig,
    stream: Option<AudioStreamHandle>,
    buffer_queue: Vec<DecodedAudioFrame>,
    is_playing: bool,
}

#[cfg(target_os = "android")]
struct AudioStreamHandle {
    stream: Box<dyn AudioOutputStreamSafe<FrameType = (f32, Stereo)>>,
}
#[cfg(target_os = "android")]
unsafe impl Send for AudioStreamHandle {}
#[cfg(not(target_os = "android"))]
struct AudioStreamHandle;

pub fn init(config: &AudioConfig) -> EngineResult<()> {
    let mut engine = AudioEngine { config: config.clone(), stream: None, buffer_queue: Vec::with_capacity(8), is_playing: false };
    #[cfg(target_os = "android")]
    {
        let stream = create_oboe_stream(config)?;
        engine.stream = Some(AudioStreamHandle { stream });
        engine.is_playing = true;
        AUDIO_ENGINE.set(Mutex::new(engine)).map_err(|_| crate::error::EngineError::Internal("already init".into()))?;
    }
    #[cfg(not(target_os = "android"))]
    { AUDIO_ENGINE.set(Mutex::new(engine)).map_err(|_| crate::error::EngineError::Internal("already init".into()))?; }
    Ok(())
}

pub fn enqueue_frame(frame: DecodedAudioFrame) {
    if let Some(e) = AUDIO_ENGINE.get() { if let Ok(mut eng) = e.lock() { eng.buffer_queue.push(frame); if eng.buffer_queue.len() > 16 { eng.buffer_queue.remove(0); } } }
}

pub fn pause() {
    if let Some(e) = AUDIO_ENGINE.get() { if let Ok(mut eng) = e.lock() { eng.is_playing = false; #[cfg(target_os = "android")] if let Some(ref h) = eng.stream { let _ = h.stream.pause(); } } }
}
pub fn play() {
    if let Some(e) = AUDIO_ENGINE.get() { if let Ok(mut eng) = e.lock() { eng.is_playing = true; #[cfg(target_os = "android")] if let Some(ref h) = eng.stream { let _ = h.stream.start(); } } }
}
pub fn flush() {
    if let Some(e) = AUDIO_ENGINE.get() { if let Ok(mut eng) = e.lock() { eng.buffer_queue.clear(); #[cfg(target_os = "android")] if let Some(ref h) = eng.stream { let _ = h.stream.stop(); let _ = h.stream.start(); } } }
}

#[cfg(target_os = "android")]
fn create_oboe_stream(config: &AudioConfig) -> EngineResult<Box<dyn AudioOutputStreamSafe<FrameType = (f32, Stereo)>>> {
    let sample_rate = config.sample_rate as i32;
    struct Cb { engine: &'static Mutex<AudioEngine> }
    impl oboe::AudioOutputCallback for Cb {
        type FrameType = (f32, Stereo);
        fn on_audio_ready(&mut self, _stream: &mut dyn AudioOutputStreamSafe<FrameType = Self::FrameType>, audio_data: &mut [Self::FrameType]) -> DataCallbackResult {
            if let Ok(mut eng) = self.engine.lock() {
                if !eng.is_playing { for s in audio_data.iter_mut() { *s = (0.0f32, Stereo(0.0f32)); } return DataCallbackResult::Continue; }
                if let Some(frame) = eng.buffer_queue.first() {
                    let src: &[f32] = bytemuck::cast_slice(&frame.data);
                    let n = audio_data.len().min(src.len() / 2);
                    for i in 0..n { audio_data[i] = (src[i*2], Stereo(src[i*2+1])); }
                    for s in &mut audio_data[n..] { *s = (0.0f32, Stereo(0.0f32)); }
                    eng.buffer_queue.remove(0);
                } else { for s in audio_data.iter_mut() { *s = (0.0f32, Stereo(0.0f32)); } }
            }
            DataCallbackResult::Continue
        }
    }
    let engine_ref: &'static Mutex<AudioEngine> = AUDIO_ENGINE.get().ok_or(crate::error::EngineError::Internal("no engine".into()))?;
    let stream = AudioStreamBuilder::default()
        .direction(Direction::Output)
        .performance_mode(PerformanceMode::LowLatency)
        .sharing_mode(SharingMode::Exclusive)
        .format::<(f32, Stereo)>()
        .channel_count::<Stereo>()
        .sample_rate(sample_rate)
        .callback(Cb { engine: engine_ref })
        .open_stream()
        .map_err(|e| { log::error!("Oboe: {:?}", e); crate::error::EngineError::AudioInitFailed })?;
    Ok(stream)
}

pub fn queue_depth() -> usize { AUDIO_ENGINE.get().and_then(|e| e.lock().ok()).map_or(0, |eng| eng.buffer_queue.len()) }

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn test_queue() { assert_eq!(queue_depth(), 0); }
}