use crate::decoder::{AudioConfig, DecodedAudioFrame};
use crate::error::EngineResult;
use std::sync::{Mutex, OnceLock};
#[cfg(target_os = "android")]
use oboe::{
    AudioOutputStream, AudioStream, AudioStreamAsync, AudioStreamBuilder, DataCallbackResult,
    Output, Stereo,
};

static AUDIO_ENGINE: OnceLock<Mutex<AudioEngine>> = OnceLock::new();

struct AudioEngine {
    stream: Option<AudioStreamAsync<Output, OboeCallbackHolder>>,
    buffer_queue: Vec<DecodedAudioFrame>,
    is_playing: bool,
}

#[cfg(target_os = "android")]
struct OboeCallbackHolder {
    engine: &'static Mutex<AudioEngine>,
}

#[cfg(target_os = "android")]
impl oboe::AudioOutputCallback for OboeCallbackHolder {
    type FrameType = (f32, Stereo);

    fn on_audio_ready(
        &mut self,
        _stream: &mut dyn oboe::AudioOutputStreamSafe,
        audio_data: &mut [(f32, f32)],
    ) -> DataCallbackResult {
        if let Ok(mut eng) = self.engine.lock() {
            if !eng.is_playing {
                for s in audio_data.iter_mut() {
                    *s = (0.0f32, 0.0f32);
                }
                return DataCallbackResult::Continue;
            }
            if let Some(frame) = eng.buffer_queue.first() {
                let src: &[f32] = bytemuck::cast_slice(&frame.data);
                let n = audio_data.len().min(src.len() / 2);
                for i in 0..n {
                    audio_data[i] = (src[i * 2], src[i * 2 + 1]);
                }
                for s in &mut audio_data[n..] {
                    *s = (0.0f32, 0.0f32);
                }
                eng.buffer_queue.remove(0);
            } else {
                for s in audio_data.iter_mut() {
                    *s = (0.0f32, 0.0f32);
                }
            }
        }
        DataCallbackResult::Continue
    }
}

pub fn init(config: &AudioConfig) -> EngineResult<()> {
    #[cfg(target_os = "android")]
    {
        let stream = create_oboe_stream(config)?;
        let engine = AudioEngine {
            stream: Some(stream),
            buffer_queue: Vec::with_capacity(8),
            is_playing: true,
        };
        AUDIO_ENGINE
            .set(Mutex::new(engine))
            .map_err(|_| crate::error::EngineError::Internal("already init".into()))?;
    }
    log::info!(
        "Audio engine initialized: {} Hz, {} ch",
        config.sample_rate,
        config.channels
    );
    Ok(())
}

#[cfg(target_os = "android")]
fn create_oboe_stream(
    config: &AudioConfig,
) -> EngineResult<AudioStreamAsync<Output, OboeCallbackHolder>> {
    let engine_ref: &'static Mutex<AudioEngine> =
        AUDIO_ENGINE
            .get()
            .ok_or(crate::error::EngineError::Internal("no engine".into()))?;

    let callback = OboeCallbackHolder {
        engine: engine_ref,
    };

    let stream = AudioStreamBuilder::default()
        .set_output()
        .set_f32()
        .set_stereo()
        .set_sample_rate(config.sample_rate as i32)
        .set_callback(callback)
        .open_stream()
        .map_err(|e| {
            log::error!("Oboe: {:?}", e);
            crate::error::EngineError::AudioInitFailed
        })?;

    Ok(stream)
}

pub fn enqueue_frame(frame: DecodedAudioFrame) {
    if let Some(e) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = e.lock() {
            eng.buffer_queue.push(frame);
            if eng.buffer_queue.len() > 16 {
                eng.buffer_queue.remove(0);
            }
        }
    }
}

pub fn pause() {
    if let Some(e) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = e.lock() {
            eng.is_playing = false;
            if let Some(ref mut s) = eng.stream {
                let _ = s.pause();
            }
        }
    }
}

pub fn play() {
    if let Some(e) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = e.lock() {
            eng.is_playing = true;
            if let Some(ref mut s) = eng.stream {
                let _ = s.start();
            }
        }
    }
}

pub fn flush() {
    if let Some(e) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = e.lock() {
            eng.buffer_queue.clear();
            if let Some(ref mut s) = eng.stream {
                let _ = s.stop();
                let _ = s.start();
            }
        }
    }
}

pub fn queue_depth() -> usize {
    AUDIO_ENGINE
        .get()
        .and_then(|e| e.lock().ok())
        .map_or(0, |eng| eng.buffer_queue.len())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn test_queue() {
        assert_eq!(queue_depth(), 0);
    }
}