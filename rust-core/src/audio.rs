// rust-core/src/audio.rs
// Android AAudio output via Oboe wrapper. Low-latency audio path per Manifesto §1.2.

use crate::decoder::{AudioConfig, AudioSampleFormat, DecodedAudioFrame};
use crate::error::{EngineError, EngineResult};
use std::sync::{Arc, Mutex};

// ---------------------------------------------------------------------------
// Oboe binding — conditional on Android target.
// ---------------------------------------------------------------------------
#[cfg(target_os = "android")]
use oboe::{
    AudioOutputCallback, AudioStream, AudioStreamBuilder, DataCallbackResult,
    PerformanceMode, SharingMode, StreamDirection,
};

// ---------------------------------------------------------------------------
// Audio engine state
// ---------------------------------------------------------------------------
use std::sync::OnceLock;

static AUDIO_ENGINE: OnceLock<Mutex<AudioEngine>> = OnceLock::new();

struct AudioEngine {
    config: AudioConfig,
    stream: Option<AudioStreamHandle>,
    buffer_queue: Vec<DecodedAudioFrame>,
    is_playing: bool,
}

#[cfg(target_os = "android")]
struct AudioStreamHandle {
    stream: AudioStream,
    stream_ptr: *mut AudioStream,
}

// Safety: AudioStream is Send (Oboe C++ class is internally synchronized).
#[cfg(target_os = "android")]
unsafe impl Send for AudioStreamHandle {}

#[cfg(not(target_os = "android"))]
struct AudioStreamHandle;

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/// Initialize the audio output backend with the given audio configuration.
pub fn init(config: &AudioConfig) -> EngineResult<()> {
    let engine = AudioEngine {
        config: config.clone(),
        stream: None,
        buffer_queue: Vec::with_capacity(8),
        is_playing: false,
    };

    #[cfg(target_os = "android")]
    {
        let stream = create_oboe_stream(config)?;
        let stream_ptr = Box::into_raw(Box::new(stream));
        let handle = AudioStreamHandle {
            stream: unsafe { std::ptr::read(stream_ptr) },
            stream_ptr,
        };

        let mut engine = engine;
        let raw_ptr: *mut AudioStream = handle.stream_ptr;
        engine.stream = Some(handle);

        // Start the stream
        unsafe {
            (*raw_ptr).start().map_err(|e| {
                EngineError::AudioInitFailed
            })?;
        }
        engine.is_playing = true;

        AUDIO_ENGINE
            .set(Mutex::new(engine))
            .map_err(|_| EngineError::Internal("Audio engine already initialized".into()))?;
    }

    #[cfg(not(target_os = "android"))]
    {
        AUDIO_ENGINE
            .set(Mutex::new(engine))
            .map_err(|_| EngineError::Internal("Audio engine already initialized".into()))?;
    }

    log::info!(
        "Audio engine initialized: {} Hz, {} ch",
        config.sample_rate,
        config.channels
    );
    Ok(())
}

/// Push a decoded audio frame into the playback queue.
/// The audio callback thread will consume frames from this queue.
pub fn enqueue_frame(frame: DecodedAudioFrame) {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = engine.lock() {
            eng.buffer_queue.push(frame);
            // Prevent unbounded queue growth
            if eng.buffer_queue.len() > 16 {
                eng.buffer_queue.remove(0);
            }
        }
    }
}

/// Pause audio output.
pub fn pause() {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = engine.lock() {
            eng.is_playing = false;
            #[cfg(target_os = "android")]
            if let Some(ref handle) = eng.stream {
                let raw_ptr: *mut AudioStream = handle.stream_ptr;
                unsafe {
                    let _ = (*raw_ptr).pause();
                }
            }
            log::debug!("Audio paused");
        }
    }
}

/// Resume audio output.
pub fn play() {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = engine.lock() {
            eng.is_playing = true;
            #[cfg(target_os = "android")]
            if let Some(ref handle) = eng.stream {
                let raw_ptr: *mut AudioStream = handle.stream_ptr;
                unsafe {
                    let _ = (*raw_ptr).start();
                }
            }
            log::debug!("Audio resumed");
        }
    }
}

/// Flush audio queue and reset stream (after seek).
pub fn flush() {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(mut eng) = engine.lock() {
            eng.buffer_queue.clear();
            #[cfg(target_os = "android")]
            if let Some(ref handle) = eng.stream {
                let raw_ptr: *mut AudioStream = handle.stream_ptr;
                unsafe {
                    let _ = (*raw_ptr).stop();
                    let _ = (*raw_ptr).start();
                }
            }
            log::debug!("Audio flushed");
        }
    }
}

/// Get the number of queued audio frames (for buffer health monitoring).
pub fn queue_depth() -> usize {
    if let Some(engine) = AUDIO_ENGINE.get() {
        if let Ok(eng) = engine.lock() {
            return eng.buffer_queue.len();
        }
    }
    0
}

// ---------------------------------------------------------------------------
// Oboe stream creation
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
fn create_oboe_stream(config: &AudioConfig) -> EngineResult<AudioStream> {
    let channel_count = config.channels as i32;
    let sample_rate = config.sample_rate as i32;

    // Map our sample format to Oboe format
    let format = match config.format {
        AudioSampleFormat::S16 => oboe::AudioFormat::I16,
        AudioSampleFormat::F32 => oboe::AudioFormat::F32,
    };

    // Oboe callback that reads from our buffer queue
    struct OboeCallback {
        engine: &'static Mutex<AudioEngine>,
    }

    impl AudioOutputCallback for OboeCallback {
        fn on_audio_ready(
            &mut self,
            stream: &mut AudioStream,
            audio_data: &mut [f32],
        ) -> DataCallbackResult {
            if let Ok(mut eng) = self.engine.lock() {
                if !eng.is_playing {
                    // Fill with silence
                    for sample in audio_data.iter_mut() {
                        *sample = 0.0f32;
                    }
                    return DataCallbackResult::Continue;
                }

                if let Some(frame) = eng.buffer_queue.first() {
                    // Convert frame data to f32 samples
                    let samples_out = audio_data.len();
                    let src = &frame.data;
                    match eng.config.format {
                        AudioSampleFormat::S16 => {
                            let s16_data: &[i16] =
                                unsafe { std::slice::from_raw_parts(src.as_ptr() as *const i16, src.len() / 2) };
                            let count = std::cmp::min(samples_out, s16_data.len());
                            for i in 0..count {
                                audio_data[i] = s16_data[i] as f32 / 32768.0f32;
                            }
                            for i in count..samples_out {
                                audio_data[i] = 0.0f32;
                            }
                        }
                        AudioSampleFormat::F32 => {
                            let f32_data: &[f32] =
                                unsafe { std::slice::from_raw_parts(src.as_ptr() as *const f32, src.len() / 4) };
                            let count = std::cmp::min(samples_out, f32_data.len());
                            audio_data[..count].copy_from_slice(&f32_data[..count]);
                            for i in count..samples_out {
                                audio_data[i] = 0.0f32;
                            }
                        }
                    }
                    // Remove consumed frame
                    eng.buffer_queue.remove(0);
                } else {
                    // Underflow — fill silence
                    for sample in audio_data.iter_mut() {
                        *sample = 0.0f32;
                    }
                }
            }
            DataCallbackResult::Continue
        }

        fn on_error_before_close(&mut self, _stream: &mut AudioStream, error: oboe::Error) {
            log::error!("Oboe error before close: {:?}", error);
        }

        fn on_error_after_close(&mut self, _stream: &mut AudioStream, error: oboe::Error) {
            log::error!("Oboe error after close: {:?}", error);
        }
    }

    // Build the stream
    let engine_ref: &'static Mutex<AudioEngine> =
        AUDIO_ENGINE.get().ok_or_else(|| {
            EngineError::Internal("Audio engine not initialized for callback".into())
        })?;

    let callback = OboeCallback {
        engine: engine_ref,
    };

    let mut stream = AudioStreamBuilder::default()
        .direction(StreamDirection::Output)
        .performance_mode(PerformanceMode::LowLatency)
        .sharing_mode(SharingMode::Exclusive)
        .format(format)
        .channel_count(channel_count)
        .sample_rate(sample_rate)
        .callback(callback)
        .open()
        .map_err(|e| {
            log::error!("Oboe stream open failed: {:?}", e);
            EngineError::AudioInitFailed
        })?;

    // Set buffer size to minimize latency
    let _ = stream.set_buffer_size_in_frames(256);

    log::info!(
        "Oboe stream opened: {} Hz, {} ch, latency={:?}",
        sample_rate,
        channel_count,
        stream.get_frames_per_burst()
    );

    Ok(stream)
}

// ---------------------------------------------------------------------------
// Drop cleanup
// ---------------------------------------------------------------------------

impl Drop for AudioEngine {
    fn drop(&mut self) {
        #[cfg(target_os = "android")]
        if let Some(handle) = self.stream.take() {
            let raw_ptr: *mut AudioStream = handle.stream_ptr;
            unsafe {
                let _ = (*raw_ptr).stop();
                let _ = (*raw_ptr).close();
                let _ = Box::from_raw(raw_ptr);
            }
            log::debug!("Audio stream closed and freed");
        }
        self.buffer_queue.clear();
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_queue_depth_empty() {
        // Without initialization, queue depth is 0
        assert_eq!(queue_depth(), 0);
    }

    #[test]
    fn test_audio_config_format_equality() {
        assert_eq!(AudioSampleFormat::S16, AudioSampleFormat::S16);
        assert_ne!(AudioSampleFormat::S16, AudioSampleFormat::F32);
    }

    #[test]
    fn test_enqueue_frame_no_init_does_not_panic() {
        let frame = DecodedAudioFrame {
            data: vec![0u8; 256],
            samples: 128,
            sample_rate: 48000,
            channels: 2,
        };
        enqueue_frame(frame);
    }

    #[test]
    fn test_pause_play_no_init_does_not_panic() {
        pause();
        play();
    }

    #[test]
    fn test_flush_no_init_does_not_panic() {
        flush();
    }
}