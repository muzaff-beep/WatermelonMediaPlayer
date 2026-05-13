// rust-core/src/decoder.rs — Part 1 of 2
// Pure-Rust media demuxer and decoder using Symphonia.
// Manifesto §1.2: Software decode, hardware fallback via Android MediaCodec.

use crate::error::{EngineError, EngineResult};
use std::ffi::c_void;
use std::ptr;
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::formats::{FormatOptions, FormatReader};
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
use std::fs::File;
use std::sync::Mutex;

/// Audio configuration produced after codec header parsing.
#[derive(Debug, Clone)]
pub struct AudioConfig {
    pub sample_rate: u32,
    pub channels: u16,
    pub format: AudioSampleFormat,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AudioSampleFormat {
    S16,
    F32,
}

// ---------------------------------------------------------------------------
// Module-level state
// ---------------------------------------------------------------------------

static DECODER_STATE: Mutex<DecoderState> = Mutex::new(DecoderState::Uninitialized);

enum DecoderState {
    Uninitialized,
    Initialized {
        format: Box<dyn FormatReader>,
        video_track_id: u32,
        audio_track_id: Option<u32>,
        video_decoder: Option<Box<dyn symphonia::core::codecs::Decoder>>,
        audio_decoder: Option<Box<dyn symphonia::core::codecs::Decoder>>,
        duration_us: i64,
        render_target: *mut c_void,
    },
}

unsafe impl Send for DecoderState {}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

pub fn init(uri: &str) -> EngineResult<(i64, AudioConfig)> {
    let mut guard = DECODER_STATE.lock().unwrap();

    if !matches!(*guard, DecoderState::Uninitialized) {
        *guard = DecoderState::Uninitialized;
    }

    // Open media source
    let file = File::open(uri).map_err(|e| {
        EngineError::SourceNotFound(format!("Failed to open file: {}", e))
    })?;

    let mss = MediaSourceStream::new(Box::new(file), Default::default());

    let hint = Hint::new();
    let format_opts = FormatOptions::default();
    let metadata_opts = MetadataOptions::default();
    let decoder_opts = DecoderOptions::default();

    let format = symphonia::default::formats::Probe::default()
        .format(&hint, mss, &format_opts, &metadata_opts)
        .map_err(|e| EngineError::SourceNotFound(format!("Format probe failed: {}", e)))?;

    // Find video and audio tracks
    let tracks = format.tracks();
    let mut video_track_id = 0u32;
    let mut audio_track_id: Option<u32> = None;
    let mut audio_config = AudioConfig {
        sample_rate: 48000,
        channels: 2,
        format: AudioSampleFormat::S16,
    };
    let mut video_decoder: Option<Box<dyn symphonia::core::codecs::Decoder>> = None;
    let mut audio_decoder: Option<Box<dyn symphonia::core::codecs::Decoder>> = None;

    for (id, track) in tracks.iter().enumerate() {
        let track_id = id as u32;
        let params = &track.codec_params;

        if params.codec == CODEC_TYPE_NULL {
            continue;
        }

        if track.codec_params.codec == symphonia::core::codecs::CODEC_TYPE_VIDEO {
            video_track_id = track_id;
            video_decoder = Some(
                symphonia::default::codecs::Symphonia::default()
                    .try_new(params, &decoder_opts)
                    .map_err(|e| EngineError::DecoderInitFailed)?
            );
        } else if track.codec_params.codec == symphonia::core::codecs::CODEC_TYPE_AUDIO {
            audio_track_id = Some(track_id);
            audio_decoder = Some(
                symphonia::default::codecs::Symphonia::default()
                    .try_new(params, &decoder_opts)
                    .map_err(|e| EngineError::DecoderInitFailed)?
            );
            // Extract audio configuration
            if let Some(sample_rate) = params.sample_rate {
                audio_config.sample_rate = sample_rate;
            }
            if let Some(channels) = params.channels {
                audio_config.channels = channels as u16;
            }
            audio_config.format = AudioSampleFormat::F32; // Symphonia outputs f32 by default
        }
    }

    if video_decoder.is_none() && audio_decoder.is_none() {
        return Err(EngineError::DecoderInitFailed);
    }

    // Duration in microseconds (Symphonia doesn't always provide this; default to 0)
    let duration_us = 0i64;

    *guard = DecoderState::Initialized {
        format,
        video_track_id,
        audio_track_id,
        video_decoder,
        audio_decoder,
        duration_us,
        render_target: ptr::null_mut(),
    };

    Ok((duration_us, audio_config))
}

/// Set the render target (ANativeWindow pointer) for video frames.
pub fn set_render_target(surface: *mut c_void) {
    let mut guard = DECODER_STATE.lock().unwrap();
    if let DecoderState::Initialized { ref mut render_target, .. } = *guard {
        *render_target = surface;
    }
}

// rust-core/src/decoder.rs — Part 2 of 2
// Continuation: decode_video_frame, decode_audio_frame, flush, ANativeWindow render, tests.
// Append to Part 1.

/// Decode one video frame, returning raw RGBA pixel data.
pub fn decode_video_frame() -> Option<DecodedVideoFrame> {
    let mut guard = DECODER_STATE.lock().unwrap();
    match *guard {
        DecoderState::Initialized {
            ref mut format,
            video_track_id,
            ref mut video_decoder,
            ref render_target,
            ..
        } => {
            let decoder = video_decoder.as_mut()?;
            loop {
                let packet = format.next_packet().ok()?;
                if packet.track_id() != video_track_id {
                    continue;
                }
                match decoder.decode(&packet) {
                    Ok(decoded) => {
                        let video_frame = decoded
                            .video()
                            .expect("expected video frame");
                        let rgba = video_frame
                            .to_rgba()
                            .expect("failed to convert to RGBA");
                        let width = rgba.width();
                        let height = rgba.height();
                        let data = rgba.data().to_vec();
                        let pts_us = packet
                            .ts()
                            .map(|ts| ts as i64)
                            .unwrap_or(0);

                        if !render_target.is_null() {
                            render_frame_to_surface(render_target, &data, width, height);
                        }

                        return Some(DecodedVideoFrame {
                            data,
                            width,
                            height,
                            pts_us,
                        });
                    }
                    Err(symphonia::core::errors::Error::DecodeError(_)) => {
                        continue; // skip corrupted frame
                    }
                    Err(e) => {
                        log::error!("Video decode error: {:?}", e);
                        return None;
                    }
                }
            }
        }
        _ => None,
    }
}

/// Decode one audio packet, returning raw PCM samples (f32 interleaved).
pub fn decode_audio_frame() -> Option<DecodedAudioFrame> {
    let mut guard = DECODER_STATE.lock().unwrap();
    match *guard {
        DecoderState::Initialized {
            ref mut format,
            audio_track_id,
            ref mut audio_decoder,
            ..
        } => {
            let decoder = audio_decoder.as_mut()?;
            let track_id = audio_track_id?;
            loop {
                let packet = format.next_packet().ok()?;
                if packet.track_id() != track_id {
                    continue;
                }
                match decoder.decode(&packet) {
                    Ok(decoded) => {
                        let audio_buf = decoded
                            .audio()
                            .expect("expected audio frame");
                        let spec = *audio_buf.spec();
                        let num_frames = audio_buf.frames();
                        let mut sample_buf = SampleBuffer::<f32>::new(num_frames as u64, spec);
                        sample_buf.copy_interleaved_ref(audio_buf);
                        let data = sample_buf.samples().to_vec();
                        return Some(DecodedAudioFrame {
                            data: bytemuck::cast_slice(&data).to_vec(),
                            samples: num_frames as u32,
                            sample_rate: spec.rate,
                            channels: spec.channels.count() as u16,
                        });
                    }
                    Err(symphonia::core::errors::Error::DecodeError(_)) => {
                        continue;
                    }
                    Err(e) => {
                        log::error!("Audio decode error: {:?}", e);
                        return None;
                    }
                }
            }
        }
        _ => None,
    }
}

/// Flush decoder state after a seek.
pub fn flush() {
    let mut guard = DECODER_STATE.lock().unwrap();
    if let DecoderState::Initialized {
        ref mut format,
        ref mut video_decoder,
        ref mut audio_decoder,
        ..
    } = *guard
    {
        if let Some(vd) = video_decoder {
            vd.reset();
        }
        if let Some(ad) = audio_decoder {
            ad.reset();
        }
        // Seek to the beginning of the stream (or desired position)
        let _ = format.seek(
            symphonia::core::formats::SeekMode::Accurate,
            symphonia::core::formats::SeekTo::Time { time: 0 },
        );
    }
}

/// Retrieve the media duration in microseconds.
pub fn get_duration() -> i64 {
    let guard = DECODER_STATE.lock().unwrap();
    match *guard {
        DecoderState::Initialized { duration_us, .. } => duration_us,
        _ => 0,
    }
}

// ---------------------------------------------------------------------------
// Hardware decoding fallback (MediaCodec)
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
mod hardware {
    use super::*;

    pub fn try_hardware_decode(_mime: &str, _surface: *mut c_void) -> bool {
        // MediaCodec integration point — remains a stub for future expansion.
        false
    }
}

#[cfg(not(target_os = "android"))]
mod hardware {
    pub fn try_hardware_decode(_mime: &str, _surface: *mut c_void) -> bool {
        false
    }
}

// ---------------------------------------------------------------------------
// Helper: render RGBA data to an ANativeWindow
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
fn render_frame_to_surface(render_target: &*mut c_void, rgba_data: &[u8], width: u32, height: u32) {
    unsafe {
        let window = *render_target as *mut ndk_sys::ANativeWindow;
        if window.is_null() {
            return;
        }
        let w = width as i32;
        let h = height as i32;
        let format = ndk_sys::ANativeWindow_LegacyFormat::WINDOW_FORMAT_RGBA_8888;

        ndk_sys::ANativeWindow_setBuffersGeometry(window, w, h, format.0);

        let mut buffer: *mut ndk_sys::ANativeWindow_Buffer = ptr::null_mut();
        if ndk_sys::ANativeWindow_lock(window, &mut buffer, ptr::null_mut()) == 0 {
            let buf = &*buffer;
            let dst = std::slice::from_raw_parts_mut(
                buf.bits as *mut u8,
                (buf.stride * buf.height) as usize,
            );
            // Copy row by row with stride
            let src_stride = width as usize * 4;
            for y in 0..height as usize {
                let src_row = &rgba_data[y * src_stride..(y + 1) * src_stride];
                let dst_row = &mut dst[y * buf.stride as usize..(y + 1) * buf.stride as usize];
                let copy_len = std::cmp::min(src_row.len(), dst_row.len());
                dst_row[..copy_len].copy_from_slice(&src_row[..copy_len]);
            }
            ndk_sys::ANativeWindow_unlockAndPost(window);
        }
    }
}

#[cfg(not(target_os = "android"))]
fn render_frame_to_surface(_render_target: &*mut c_void, _rgba_data: &[u8], _width: u32, _height: u32) {
    // No-op on non-Android targets
}

// ---------------------------------------------------------------------------
// Decoded frame types
// ---------------------------------------------------------------------------

#[derive(Debug, Clone)]
pub struct DecodedVideoFrame {
    pub data: Vec<u8>,
    pub width: u32,
    pub height: u32,
    pub pts_us: i64,
}

#[derive(Debug, Clone)]
pub struct DecodedAudioFrame {
    pub data: Vec<u8>,    // raw f32 little-endian samples
    pub samples: u32,
    pub sample_rate: u32,
    pub channels: u16,
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_audio_config_defaults() {
        let config = AudioConfig {
            sample_rate: 48000,
            channels: 2,
            format: AudioSampleFormat::F32,
        };
        assert_eq!(config.sample_rate, 48000);
        assert_eq!(config.channels, 2);
    }

    #[test]
    fn test_flush_on_uninitialized_does_not_panic() {
        flush();
    }

    #[test]
    fn test_get_duration_uninitialized_returns_zero() {
        assert_eq!(get_duration(), 0);
    }

    #[test]
    fn test_decoded_video_frame_fields() {
        let frame = DecodedVideoFrame {
            data: vec![0u8; 100],
            width: 1920,
            height: 1080,
            pts_us: 42,
        };
        assert_eq!(frame.width, 1920);
        assert_eq!(frame.height, 1080);
        assert_eq!(frame.pts_us, 42);
        assert_eq!(frame.data.len(), 100);
    }

    #[test]
    fn test_decoded_audio_frame_fields() {
        let frame = DecodedAudioFrame {
            data: vec![0u8; 1024],
            samples: 512,
            sample_rate: 44100,
            channels: 2,
        };
        assert_eq!(frame.samples, 512);
        assert_eq!(frame.sample_rate, 44100);
        assert_eq!(frame.channels, 2);
        assert_eq!(frame.data.len(), 1024);
    }

    #[test]
    fn test_hardware_decode_returns_false() {
        assert!(!hardware::try_hardware_decode("video/avc", std::ptr::null_mut()));
    }
}