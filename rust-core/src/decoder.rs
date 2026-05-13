use crate::error::{EngineError, EngineResult};
use std::fs::File;
use std::sync::Mutex;
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::formats::{FormatOptions, FormatReader, SeekMode, SeekTo};
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

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

static DECODER_STATE: Mutex<DecoderState> = Mutex::new(DecoderState::Uninitialized);

enum DecoderState {
    Uninitialized,
    Initialized {
        format: Box<dyn FormatReader>,
        track_id: u32,
        decoder: Box<dyn symphonia::core::codecs::Decoder>,
        duration_us: i64,
    },
}

unsafe impl Send for DecoderState {}

pub fn init(uri: &str) -> EngineResult<(i64, AudioConfig)> {
    let mut guard = DECODER_STATE.lock().unwrap();
    *guard = DecoderState::Uninitialized;

    let file = File::open(uri)
        .map_err(|e| EngineError::SourceNotFound(format!("Failed to open: {}", e)))?;
    let mss = MediaSourceStream::new(Box::new(file), Default::default());
    let probed = symphonia::default::get_probe()
        .format(&Hint::new(), mss, &FormatOptions::default(), &MetadataOptions::default())
        .map_err(|e| EngineError::SourceNotFound(format!("Probe failed: {}", e)))?;
    let mut format = probed.format;
    let decoder_opts = DecoderOptions::default();

    // In Symphonia 0.5.5, a supported audio track is one that does not have a NULL codec.
    let track = format.tracks().iter().find(|t| t.codec_params.codec != CODEC_TYPE_NULL)
        .ok_or(EngineError::DecoderInitFailed)?;
    let track_id = track.id;
    let params = &track.codec_params;

    let decoder = symphonia::default::get_codecs()
        .make(params, &decoder_opts)
        .map_err(|e| {
            log::error!("Decoder creation failed: {}", e);
            EngineError::DecoderInitFailed
        })?;

    let audio_config = AudioConfig {
        sample_rate: params.sample_rate.unwrap_or(48000),
        channels: params.channels.map(|c| c.count() as u16).unwrap_or(2),
        format: AudioSampleFormat::F32,
    };

    *guard = DecoderState::Initialized { format, track_id, decoder, duration_us: 0 };
    Ok((0, audio_config))
}

pub fn decode_audio_frame() -> Option<DecodedAudioFrame> {
    let mut guard = DECODER_STATE.lock().unwrap();
    if let DecoderState::Initialized { ref mut format, track_id, ref mut decoder, .. } = *guard {
        loop {
            let packet = match format.next_packet() {
                Ok(p) => p,
                Err(_) => return None,
            };
            if packet.track_id() != track_id { continue; }
            match decoder.decode(&packet) {
                Ok(buf) => {
                    let spec = *buf.spec();
                    let frames = buf.frames();
                    let mut sb = SampleBuffer::<f32>::new(frames as u64, spec);
                    sb.copy_interleaved_ref(buf);
                    let data: Vec<u8> = bytemuck::cast_slice(sb.samples()).to_vec();
                    return Some(DecodedAudioFrame {
                        data,
                        samples: frames as u32,
                        sample_rate: spec.rate,
                        channels: spec.channels.count() as u16,
                    });
                }
                Err(symphonia::core::errors::Error::DecodeError(_)) => continue,
                Err(_) => return None,
            }
        }
    }
    None
}

pub fn decode_video_frame() -> Option<DecodedVideoFrame> {
    None
}

pub fn flush() {
    let mut guard = DECODER_STATE.lock().unwrap();
    if let DecoderState::Initialized { ref mut format, ref mut decoder, .. } = *guard {
        decoder.reset();
        let _ = format.seek(SeekMode::Accurate, SeekTo::Time { time: 0, track_id: None });
    }
}

pub fn get_duration() -> i64 { 0 }
pub fn set_render_target(_surface: *mut std::ffi::c_void) {}

#[derive(Debug, Clone)] pub struct DecodedVideoFrame { pub data: Vec<u8>, pub width: u32, pub height: u32, pub pts_us: i64 }
#[derive(Debug, Clone)] pub struct DecodedAudioFrame { pub data: Vec<u8>, pub samples: u32, pub sample_rate: u32, pub channels: u16 }

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn test_flush() { flush(); }
    #[test] fn test_duration() { assert_eq!(get_duration(), 0); }
}