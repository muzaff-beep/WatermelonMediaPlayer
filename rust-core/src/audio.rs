// rust-core/src/audio.rs
// Android AAudio output via Oboe 0.6. Manifesto §1.2 audio path.

use crate::decoder::{AudioConfig, AudioSampleFormat, DecodedAudioFrame};
use crate::error::EngineResult;
use std::sync::{Mutex, OnceLock};

#[cfg(target_os = "android")]
use oboe::{
    AudioStreamBuilder, AudioOutputCallback, DataCallbackResult,
    PerformanceMode, SharingMode, Direction,
};

static AUDIO_ENGINE: OnceLock<Mutex<AudioEngine>> = OnceLock::new();

struct AudioEngine {
    config: AudioConfig,
    stream: Option<AudioStreamHandle>,
    buffer_queue: Vec<DecodedAudioFrame>,
    is_playing: bool,
}

#[cfg(target_os = "android")]
struct AudioStreamHandle {
    stream: Box<dyn oboe::AudioOutputStreamSafe>,
}

#[cfg(target_os = "android")]
unsafe impl Send for AudioStreamHandle {}

#[cfg(not(target_os = "android"))]
struct AudioStreamHandle;

pub fn init(config: &AudioConfig) -> EngineResult<()> {
    let mut engine = AudioEngine {
        config: config.clone(),
        stream: None,
        buffer_queue: Vec::with_capacity(8),
        is_playing: false,
    };

    #[cfg(target_os = "android")]
    {
        let stream = create_oboe_stream(config)?;
        engine.stream = Some(AudioStreamHandle { stream });
        engine.is_playing = true;

        AUDIO_ENGINE
            .set(Mutex::new(engine))
            .map_err(|_| crate::error::EngineError::Internal("Audio engine already initialized".into()))?;
    }

    #[cfg(not(target_os = "android"))]
    {
        AUDIO_ENGINE
            .set(Mutex::new(engine))
            .map_err(|_| crate::error::EngineError::Internal("Audio engine already initialized".into()))?;
    }

    log::info!("Audio engine initialized: {} Hz, {} ch", config.sample_rate, config.channels);
    Ok(())
}

pub fn enqueue_frame(frame: DecodedAudioFrame) {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = engine.lock() {
            eng.buffer_queue.push(frame);
            if eng.buffer_queue.len() > 16 {
                eng.buffer_queue.remove(0);
            }
        }
    }
}

pub fn pause() {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = engine.lock() {
            eng.is_playing = false;
            #[cfg(target_os = "android")]
            if let Some(ref handle) = eng.stream {
                let _ = handle.stream.pause();
            }
        }
    }
}

pub fn play() {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = engine.lock() {
            eng.is_playing = true;
            #[cfg(target_os = "android")]
            if let Some(ref handle) = eng.stream {
                let _ = handle.stream.start();
            }
        }
    }
}

pub fn flush() {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = engine.lock() {
            eng.buffer_queue.clear();
            #[cfg(target_os = "android")]
            if let Some(ref handle) = eng.stream {
                let _ = handle.stream.stop();
                let _ = handle.stream.start();
            }
        }
    }
}

pub fn queue_depth() -> usize {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(eng) = engine.lock() {
            return eng.buffer_queue.len();
        }
    }
    0
}

#[cfg(target_os = "android")]
fn create_oboe_stream(config: &AudioConfig) -> EngineResult<Box<dyn oboe::AudioOutputStreamSafe>> {
    let channel_count = config.channels as i32;
    let sample_rate = config.sample_rate as i32;

    struct OboeCallback {
        engine: &'static Mutex<AudioEngine>,
    }

    impl AudioOutputCallback for OboeCallback {
        type FrameType = f32;

        fn on_audio_ready(
            &mut self,
            _stream: &mut dyn oboe::AudioOutputStreamSafe,
            audio_data: &mut [f32],
        ) -> DataCallbackResult {
            if let Ok(mut eng) = self.engine.lock() {
                if !eng.is_playing {
                    for s in audio_data.iter_mut() { *s = 0.0; }
                    return DataCallbackResult::Continue;
                }
                if let Some(frame) = eng.buffer_queue.first() {
                    let src: &[f32] = bytemuck::cast_slice(&frame.data);
                    let n = audio_data.len().min(src.len());
                    audio_data[..n].copy_from_slice(&src[..n]);
                    for s in &mut audio_data[n..] { *s = 0.0; }
                    eng.buffer_queue.remove(0);
                } else {
                    for s in audio_data.iter_mut() { *s = 0.0; }
                }
            }
            DataCallbackResult::Continue
        }
    }

    let engine_ref: &'static Mutex<AudioEngine> =
        AUDIO_ENGINE.get().ok_or_else(|| {
            crate::error::EngineError::Internal("Audio engine not initialized".into())
        })?;

    let callback = OboeCallback { engine: engine_ref };

    let stream = AudioStreamBuilder::default()
        .direction(Direction::Output)
        .performance_mode(PerformanceMode::LowLatency)
        .sharing_mode(SharingMode::Exclusive)
        .format::<f32>()
        .channel_count(channel_count)
        .sample_rate(sample_rate)
        .callback(callback)
        .open_stream()
        .map_err(|e| {
            log::error!("Oboe open failed: {:?}", e);
            crate::error::EngineError::AudioInitFailed
        })?;

    log::info!("Oboe stream opened: {} Hz, {} ch", sample_rate, channel_count);
    Ok(stream)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_queue_empty() { assert_eq!(queue_depth(), 0); }

    #[test]
    fn test_enqueue_no_panic() {
        enqueue_frame(DecodedAudioFrame {
            data: vec![0; 256], samples: 64, sample_rate: 48000, channels: 2,
        });
    }

    #[test]
    fn test_pause_play_flush() { pause(); play(); flush(); }
}