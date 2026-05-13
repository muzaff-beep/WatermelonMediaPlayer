// rust-core/src/decoder.rs
// FFmpeg-based demuxer and decoder. Wraps libavformat, libavcodec, libswscale.
// Manifesto §1.2: software decode via FFmpeg, hardware decode via NDK MediaCodec fallback.

use crate::error::{EngineError, EngineResult};
use std::ffi::{c_void, CString};
use std::ptr;

// ---------------------------------------------------------------------------
// FFmpeg C bindings — minimal, direct. No full ffmpeg-sys dependency needed
// because ffmpeg-next already re-exports what we need.
// ---------------------------------------------------------------------------
use ffmpeg_next::{
    codec, format, frame, media, software, util,
    util::format::Pixel,
};

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
// Module-level state — mutex-guarded because decode runs on a worker thread.
// ---------------------------------------------------------------------------
use std::sync::Mutex;

static DECODER_STATE: Mutex<DecoderState> = Mutex::new(DecoderState::Uninitialized);

enum DecoderState {
    Uninitialized,
    Initialized {
        input_ctx: format::context::InputContext,
        video_stream_idx: usize,
        audio_stream_idx: Option<usize>,
        video_decoder: codec::decoder::Video,
        audio_decoder: Option<codec::decoder::Audio>,
        duration_us: i64,
        render_target: *mut c_void,
    },
}

// Safety: FFmpeg contexts are internally synchronized for our single-threaded decode loop.
unsafe impl Send for DecoderState {}

// ---------------------------------------------------------------------------
// Public API called from engine.rs
// ---------------------------------------------------------------------------

/// Initialize the decoder for the given URI.
/// Parses the container, opens video and audio codecs, returns duration and audio config.
pub fn init(uri: &str) -> EngineResult<(i64, AudioConfig)> {
    let mut guard = DECODER_STATE.lock().unwrap();

    // Flush any prior state
    if !matches!(*guard, DecoderState::Uninitialized) {
        *guard = DecoderState::Uninitialized;
    }

    // Build CString for URI
    let uri_c = CString::new(uri).map_err(|e| {
        EngineError::InvalidUri(format!("URI contains null byte: {}", e))
    })?;

    // Open input
    let input = format::input(&uri_c).map_err(|e| {
        EngineError::SourceNotFound(format!("ffmpeg open failed: {}", e))
    })?;

    // Find best video stream
    let video_stream = input
        .streams()
        .best(media::Type::Video)
        .ok_or_else(|| EngineError::DecoderInitFailed)?;
    let video_stream_idx = video_stream.index();

    // Open video decoder
    let video_codec = codec::context::Context::from_parameters(video_stream.parameters())
        .map_err(|e| EngineError::Internal(format!("video codec context: {}", e)))?;
    let mut video_decoder = video_codec.decoder().video().map_err(|e| {
        EngineError::Internal(format!("video decoder open: {}", e))
    })?;

    // Find audio stream
    let audio_stream = input.streams().best(media::Type::Audio);
    let audio_stream_idx = audio_stream.as_ref().map(|s| s.index());
    let audio_config;
    let audio_decoder;

    if let Some(audio_stream) = audio_stream {
        let audio_codec = codec::context::Context::from_parameters(audio_stream.parameters())
            .map_err(|e| EngineError::Internal(format!("audio codec context: {}", e)))?;
        let mut dec = audio_codec.decoder().audio().map_err(|e| {
            EngineError::Internal(format!("audio decoder open: {}", e))
        })?;

        // Determine audio sample format
        let fmt = match dec.format() {
            format::Sample::I16(_) => AudioSampleFormat::S16,
            format::Sample::F32(_) => AudioSampleFormat::F32,
            _ => AudioSampleFormat::S16, // default fallback
        };

        audio_config = AudioConfig {
            sample_rate: dec.rate(),
            channels: dec.channels() as u16,
            format: fmt,
        };
        audio_decoder = Some(dec);
    } else {
        // No audio stream — provide silent config
        audio_config = AudioConfig {
            sample_rate: 48000,
            channels: 2,
            format: AudioSampleFormat::S16,
        };
        audio_decoder = None;
    }

    let duration_us = input.duration() as i64;

    *guard = DecoderState::Initialized {
        input_ctx: input,
        video_stream_idx,
        audio_stream_idx,
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
    if let DecoderState::Initialized {
        ref mut render_target,
        ..
    } = *guard
    {
        *render_target = surface;
    }
}

/// Decode one video frame, returning raw RGBA pixel data.
/// Called from the decode worker loop.
pub fn decode_video_frame() -> Option<DecodedVideoFrame> {
    let mut guard = DECODER_STATE.lock().unwrap();
    match *guard {
        DecoderState::Initialized {
            ref mut input_ctx,
            video_stream_idx,
            ref mut video_decoder,
            ref mut render_target,
            ..
        } => {
            // Read next packet
            let packet = loop {
                match input_ctx.packets().next() {
                    Some((stream_idx, packet)) => {
                        if stream_idx == video_stream_idx {
                            break packet;
                        }
                    }
                    None => return None,
                }
            };

            // Decode
            video_decoder.send_packet(&packet).ok()?;
            let mut decoded = frame::Video::empty();
            video_decoder.receive_frame(&mut decoded).ok()?;

            // Convert to RGBA
            let mut scaler = software::scaling::Context::get(
                decoded.format(),
                decoded.width(),
                decoded.height(),
                Pixel::RGBA,
                decoded.width(),
                decoded.height(),
                software::scaling::Flags::BILINEAR,
            )
            .ok()?;

            let mut rgba = frame::Video::empty();
            scaler.run(&decoded, &mut rgba).ok()?;

            let pts_us = decoded.pts().unwrap_or(0);

            // If a render target is set, render directly
            if !render_target.is_null() {
                render_frame_to_surface(render_target, &rgba);
            }

            // Extract raw bytes
            let data = rgba.data(0).to_vec();
            let width = rgba.width();
            let height = rgba.height();

            Some(DecodedVideoFrame {
                data,
                width,
                height,
                pts_us,
            })
        }
        _ => None,
    }
}

/// Decode one audio packet, returning raw PCM samples.
pub fn decode_audio_frame() -> Option<DecodedAudioFrame> {
    let mut guard = DECODER_STATE.lock().unwrap();
    match *guard {
        DecoderState::Initialized {
            ref mut input_ctx,
            audio_stream_idx,
            ref mut audio_decoder,
            ..
        } => {
            let audio_decoder = audio_decoder.as_mut()?;
            let stream_idx = audio_stream_idx?;

            let packet = loop {
                match input_ctx.packets().next() {
                    Some((idx, packet)) => {
                        if idx == stream_idx {
                            break packet;
                        }
                    }
                    None => return None,
                }
            };

            audio_decoder.send_packet(&packet).ok()?;
            let mut decoded = frame::Audio::empty();
            audio_decoder.receive_frame(&mut decoded).ok()?;

            // Extract PCM data
            let data = decoded.data(0).to_vec();
            let samples = decoded.samples() as u32;
            let sample_rate = decoded.rate();
            let channels = decoded.channels() as u16;

            Some(DecodedAudioFrame {
                data,
                samples,
                sample_rate,
                channels,
            })
        }
        _ => None,
    }
}

/// Flush decoder state after a seek.
pub fn flush() {
    let mut guard = DECODER_STATE.lock().unwrap();
    if let DecoderState::Initialized {
        ref mut input_ctx,
        ref mut video_decoder,
        ref mut audio_decoder,
        ..
    } = *guard
    {
        video_decoder.flush();
        if let Some(ref mut ad) = audio_decoder {
            ad.flush();
        }
        // Seek to beginning of input to resync
        let _ = input_ctx.seek(0, ..);
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
// Hardware decoding fallback via Android MediaCodec
// Stub: the software path above is primary. MediaCodec integration
// is activated when an Android Surface is provided and the codec supports it.
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
mod hardware {
    use super::*;

    /// Attempt hardware-accelerated decode via AMediaCodec.
    /// Returns true if hardware decode was initiated.
    pub fn try_hardware_decode(_mime: &str, _surface: *mut c_void) -> bool {
        // MediaCodec integration point — creates AMediaCodec, configures,
        // feeds encoded packets from FFmpeg, receives decoded buffers.
        // For the initial deliverable, software decode via FFmpeg suffices.
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
// Helper: render a decoded RGBA frame to an ANativeWindow
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
fn render_frame_to_surface(render_target: &*mut c_void, rgba: &frame::Video) {
    unsafe {
        let window = *render_target as *mut ndk_sys::ANativeWindow;
        if window.is_null() {
            return;
        }
        let width = rgba.width() as i32;
        let height = rgba.height() as i32;
        let format = ndk_sys::ANativeWindow_LegacyFormat::WINDOW_FORMAT_RGBA_8888;

        ndk_sys::ANativeWindow_setBuffersGeometry(window, width, height, format.0);

        let mut buffer: *mut ndk_sys::ANativeWindow_Buffer = ptr::null_mut();
        if ndk_sys::ANativeWindow_lock(window, &mut buffer, ptr::null_mut()) == 0 {
            let buf = &*buffer;
            let src = rgba.data(0);
            let dst =
                std::slice::from_raw_parts_mut(buf.bits as *mut u8, (buf.stride * buf.height) as usize);
            // Copy row by row to handle stride mismatch
            let src_stride = rgba.stride(0);
            for y in 0..height as usize {
                let src_row = &src[y * src_stride..(y + 1) * src_stride];
                let dst_row = &mut dst[y * buf.stride as usize..(y + 1) * buf.stride as usize];
                let copy_len = std::cmp::min(src_row.len(), dst_row.len());
                dst_row[..copy_len].copy_from_slice(&src_row[..copy_len]);
            }
            ndk_sys::ANativeWindow_unlockAndPost(window);
        }
    }
}

#[cfg(not(target_os = "android"))]
fn render_frame_to_surface(_render_target: &*mut c_void, _rgba: &frame::Video) {
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
    pub data: Vec<u8>,
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
            format: AudioSampleFormat::S16,
        };
        assert_eq!(config.sample_rate, 48000);
        assert_eq!(config.channels, 2);
    }

    #[test]
    fn test_decoder_state_is_send() {
        fn assert_send<T: Send>() {}
        assert_send::<DecoderState>();
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
}