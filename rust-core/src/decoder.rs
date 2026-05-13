// rust-core/src/decoder.rs
use crate::error::{EngineError, EngineResult};
use std::fs::File;
use std::sync::Mutex;
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::formats::{FormatOptions, FormatReader};
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
        video_track_id: u32,
        audio_track_id: Option<u32>,
        video_decoder: Option<Box<dyn symphonia::core::codecs::Decoder>>,
        audio_decoder: Option<Box<dyn symphonia::core::codecs::Decoder>>,
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

    let mut video_track_id = 0u32;
    let mut audio_track_id = None;
    let mut audio_config = AudioConfig { sample_rate: 48000, channels: 2, format: AudioSampleFormat::F32 };
    let mut video_decoder = None;
    let mut audio_decoder = None;

    for (id, track) in format.tracks().iter().enumerate() {
        let tid = id as u32;
        let params = &track.codec_params;
        if params.codec == CODEC_TYPE_NULL { continue; }
        if is_video(&params.codec) {
            video_track_id = tid;
            video_decoder = Some(symphonia::default::get_codecs().make(params, &decoder_opts)
                .map_err(|_| EngineError::DecoderInitFailed)?);
        } else if is_audio(&params.codec) {
            audio_track_id = Some(tid);
            audio_decoder = Some(symphonia::default::get_codecs().make(params, &decoder_opts)
                .map_err(|_| EngineError::DecoderInitFailed)?);
            if let Some(sr) = params.sample_rate { audio_config.sample_rate = sr; }
            if let Some(ch) = params.channels { audio_config.channels = ch.count() as u16; }
        }
    }

    if video_decoder.is_none() && audio_decoder.is_none() {
        return Err(EngineError::DecoderInitFailed);
    }

    *guard = DecoderState::Initialized {
        format, video_track_id, audio_track_id, video_decoder, audio_decoder, duration_us: 0,
    };
    Ok((0, audio_config))
}

fn is_video(c: &symphonia::core::codecs::CodecType) -> bool {
    matches!(c, &symphonia::core::codecs::CODEC_TYPE_H264 | &symphonia::core::codecs::CODEC_TYPE_HEVC)
}
fn is_audio(c: &symphonia::core::codecs::CodecType) -> bool {
    matches!(c, &symphonia::core::codecs::CODEC_TYPE_AAC | &symphonia::core::codecs::CODEC_TYPE_MP3 |
        &symphonia::core::codecs::CODEC_TYPE_FLAC | &symphonia::core::codecs::CODEC_TYPE_OPUS |
        &symphonia::core::codecs::CODEC_TYPE_VORBIS)
}

pub fn decode_video_frame() -> Option<DecodedVideoFrame> {
    let mut guard = DECODER_STATE.lock().unwrap();
    if let DecoderState::Initialized { ref mut format, video_track_id, ref mut video_decoder, .. } = *guard {
        let dec = video_decoder.as_mut()?;
        loop {
            let pkt = format.next_packet().ok()?;
            if pkt.track_id() != video_track_id { continue; }
            match dec.decode(&pkt) {
                Ok(_) => return Some(DecodedVideoFrame { data: vec![], width: 0, height: 0, pts_us: pkt.ts() as i64 }),
                Err(symphonia::core::errors::Error::DecodeError(_)) => continue,
                Err(_) => return None,
            }
        }
    }
    None
}

pub fn decode_audio_frame() -> Option<DecodedAudioFrame> {
    let mut guard = DECODER_STATE.lock().unwrap();
    if let DecoderState::Initialized { ref mut format, audio_track_id, ref mut audio_decoder, .. } = *guard {
        let dec = audio_decoder.as_mut()?;
        let tid = audio_track_id?;
        loop {
            let pkt = format.next_packet().ok()?;
            if pkt.track_id() != tid { continue; }
            match dec.decode(&pkt) {
                Ok(buf) => {
                    let spec = *buf.spec();
                    let frames = buf.frames();
                    let mut sb = SampleBuffer::<f32>::new(frames as u64, spec);
                    sb.copy_interleaved_ref(&buf);
                    let data: Vec<u8> = bytemuck::cast_slice(sb.samples()).to_vec();
                    return Some(DecodedAudioFrame { data, samples: frames as u32, sample_rate: spec.rate, channels: spec.channels.count() as u16 });
                }
                Err(symphonia::core::errors::Error::DecodeError(_)) => continue,
                Err(_) => return None,
            }
        }
    }
    None
}

pub fn flush() {
    let mut guard = DECODER_STATE.lock().unwrap();
    if let DecoderState::Initialized { ref mut format, ref mut video_decoder, ref mut audio_decoder, .. } = *guard {
        if let Some(vd) = video_decoder { vd.reset(); }
        if let Some(ad) = audio_decoder { ad.reset(); }
        let _ = format.seek(symphonia::core::formats::SeekMode::Accurate,
            symphonia::core::formats::SeekTo::Time { time: 0, track_id: 0 });
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