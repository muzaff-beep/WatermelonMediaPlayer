// rust-core/src/subtitle.rs
// SRT and ASS subtitle parser. Cue management with offset support.
// Manifesto §1.2: RTL-ready, HarfBuzz shaping (stub for platform-side integration).

use crate::engine::SubtitleCue;
use crate::error::{EngineError, EngineResult};
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;

// ---------------------------------------------------------------------------
// SubtitleManager
// ---------------------------------------------------------------------------

/// Manages loaded subtitle data. Supports SRT and ASS/SSA formats.
pub struct SubtitleManager {
    cues: Vec<SubtitleCue>,
    offset_ms: i64,
    font_path: Option<String>,
}

impl SubtitleManager {
    /// Create an empty subtitle manager.
    pub fn new() -> Self {
        Self {
            cues: Vec::new(),
            offset_ms: 0,
            font_path: None,
        }
    }

    /// Load a subtitle file. Detects format by extension: `.srt` or `.ass`/`.ssa`.
    pub fn load(&mut self, path: &str, font_path: Option<&str>) -> EngineResult<()> {
        let path = Path::new(path);
        let extension = path
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("")
            .to_lowercase();

        let file = File::open(path).map_err(|e| {
            EngineError::SubtitleError(format!("Failed to open subtitle file: {}", e))
        })?;
        let reader = BufReader::new(file);
        let lines: Vec<String> = reader.lines().filter_map(|l| l.ok()).collect();

        self.cues = match extension.as_str() {
            "srt" => parse_srt_lines(&lines)?,
            "ass" | "ssa" => parse_ass_lines(&lines)?,
            _ => {
                return Err(EngineError::SubtitleError(format!(
                    "Unsupported subtitle format: .{}",
                    extension
                )))
            }
        };

        if let Some(fp) = font_path {
            self.font_path = Some(fp.to_owned());
        }

        log::info!(
            "Subtitle loaded: {} cues from {}",
            self.cues.len(),
            path.display()
        );
        Ok(())
    }

    /// Set the subtitle offset in milliseconds.
    /// Positive = delay subtitles (show later), negative = advance (show earlier).
    pub fn set_offset(&mut self, offset_ms: i64) {
        self.offset_ms = offset_ms;
    }

    /// Set the font path for rendering.
    pub fn set_font_path(&mut self, font_path: &str) {
        self.font_path = Some(font_path.to_owned());
    }

    /// Get all subtitle cues active at the given position (in microseconds).
    /// The offset is applied to the position before matching.
    pub fn cues_at(&self, position_us: i64) -> Vec<SubtitleCue> {
        let adjusted_us = position_us + (self.offset_ms * 1000);
        self.cues
            .iter()
            .filter(|cue| adjusted_us >= cue.start_us && adjusted_us < cue.end_us)
            .cloned()
            .collect()
    }

    /// Get all cues (for full-subtitle display modes).
    pub fn all_cues(&self) -> &[SubtitleCue] {
        &self.cues
    }

    /// Number of loaded cues.
    pub fn cue_count(&self) -> usize {
        self.cues.len()
    }

    /// Flush subtitle state (after seek). Cues remain; no state to reset.
    pub fn flush(&mut self) {
        // Cue data is stateless; nothing to flush beyond retaining parsed cues.
    }
}

// ---------------------------------------------------------------------------
// SRT parser
// ---------------------------------------------------------------------------

/// Parse SRT lines into `SubtitleCue` vector.
/// Format:
///   1
///   00:00:01,000 --> 00:00:04,000
///   Line 1 of text
///   Line 2 of text
///   (blank line)
fn parse_srt_lines(lines: &[String]) -> EngineResult<Vec<SubtitleCue>> {
    let mut cues: Vec<SubtitleCue> = Vec::new();
    let mut i = 0;

    while i < lines.len() {
        // Skip blank lines
        if lines[i].trim().is_empty() {
            i += 1;
            continue;
        }

        // Index line (numeric) — skip
        let index_line = lines[i].trim();
        if index_line.parse::<u32>().is_ok() {
            i += 1;
        } else {
            i += 1;
            continue;
        }

        if i >= lines.len() {
            break;
        }

        // Timestamp line: "00:00:01,000 --> 00:00:04,000"
        let ts_line = lines[i].clone();
        i += 1;

        let timestamps = parse_srt_timestamp(&ts_line)?;
        let (start_us, end_us) = timestamps;

        // Text lines (until blank line or end)
        let mut text_lines: Vec<String> = Vec::new();
        while i < lines.len() && !lines[i].trim().is_empty() {
            text_lines.push(lines[i].clone());
            i += 1;
        }

        let text = text_lines.join("\n").trim().to_owned();
        if !text.is_empty() {
            cues.push(SubtitleCue {
                start_us,
                end_us,
                text,
            });
        }
    }

    Ok(cues)
}

/// Parse an SRT timestamp line: "00:00:01,000 --> 00:00:04,000"
fn parse_srt_timestamp(line: &str) -> EngineResult<(i64, i64)> {
    let parts: Vec<&str> = line.split("-->").collect();
    if parts.len() != 2 {
        return Err(EngineError::SubtitleError(format!(
            "Invalid SRT timestamp line: {}",
            line
        )));
    }
    let start = srt_time_to_us(parts[0].trim())?;
    let end = srt_time_to_us(parts[1].trim())?;
    Ok((start, end))
}

/// Convert SRT time "00:00:01,000" or "00:00:01.000" to microseconds.
fn srt_time_to_us(time_str: &str) -> EngineResult<i64> {
    // Split hours:mins:secs and milliseconds
    let time_str = time_str.replace(',', ".");
    let parts: Vec<&str> = time_str.split(':').collect();
    if parts.len() != 3 {
        return Err(EngineError::SubtitleError(format!(
            "Invalid SRT time: {}",
            time_str
        )));
    }
    let hours: i64 = parts[0]
        .parse()
        .map_err(|_| EngineError::SubtitleError("Invalid hours".into()))?;
    let minutes: i64 = parts[1]
        .parse()
        .map_err(|_| EngineError::SubtitleError("Invalid minutes".into()))?;
    let secs_parts: Vec<&str> = parts[2].split('.').collect();
    let seconds: i64 = secs_parts[0]
        .parse()
        .map_err(|_| EngineError::SubtitleError("Invalid seconds".into()))?;
    let millis: i64 = if secs_parts.len() > 1 {
        let ms_str = secs_parts[1];
        // Pad or truncate to 3 digits
        let padded = format!("{:0<3}", ms_str);
        padded[..3]
            .parse()
            .map_err(|_| EngineError::SubtitleError("Invalid milliseconds".into()))?
    } else {
        0
    };

    let total_us = (hours * 3_600_000_000) + (minutes * 60_000_000) + (seconds * 1_000_000) + (millis * 1_000);
    Ok(total_us)
}

// ---------------------------------------------------------------------------
// ASS/SSA parser
// ---------------------------------------------------------------------------

/// Parse ASS/SSA lines into `SubtitleCue` vector.
/// Focuses on `[Events]` section, extracting `Format:` and `Dialogue:` lines.
fn parse_ass_lines(lines: &[String]) -> EngineResult<Vec<SubtitleCue>> {
    let mut cues: Vec<SubtitleCue> = Vec::new();
    let mut in_events = false;
    let mut format_map: Vec<String> = Vec::new();

    for line in lines {
        let trimmed = line.trim();

        // Detect section
        if trimmed.eq_ignore_ascii_case("[Events]") {
            in_events = true;
            continue;
        }
        if !in_events {
            continue;
        }
        // Exit on next section
        if trimmed.starts_with('[') && trimmed.ends_with(']') {
            break;
        }

        // Format line
        if trimmed.to_lowercase().starts_with("format:") {
            let fmt_str = &trimmed[7..].trim();
            format_map = fmt_str
                .split(',')
                .map(|s| s.trim().to_lowercase().to_owned())
                .collect();
            continue;
        }

        // Dialogue line
        if trimmed.to_lowercase().starts_with("dialogue:") {
            let dial_str = &trimmed[9..].trim();
            let fields: Vec<&str> = dial_str.splitn(format_map.len(), ',').collect();

            if fields.len() < format_map.len() {
                continue;
            }

            // Locate Start, End, Text columns
            let mut start_str = "";
            let mut end_str = "";
            let mut text_str = "";

            for (i, col) in format_map.iter().enumerate() {
                match col.as_str() {
                    "start" => start_str = fields.get(i).copied().unwrap_or(""),
                    "end" => end_str = fields.get(i).copied().unwrap_or(""),
                    "text" => text_str = fields.get(i).copied().unwrap_or(""),
                    _ => {}
                }
            }

            if start_str.is_empty() || end_str.is_empty() || text_str.is_empty() {
                continue;
            }

            let start_us = ass_time_to_us(start_str)?;
            let end_us = ass_time_to_us(end_str)?;

            // Strip ASS override tags {\\...}
            let clean_text = strip_ass_tags(text_str);

            if !clean_text.is_empty() {
                cues.push(SubtitleCue {
                    start_us,
                    end_us,
                    text: clean_text,
                });
            }
        }
    }

    Ok(cues)
}

/// Convert ASS time "H:MM:SS.cc" or "H:MM:SS.centiseconds" to microseconds.
fn ass_time_to_us(time_str: &str) -> EngineResult<i64> {
    let parts: Vec<&str> = time_str.split(':').collect();
    if parts.len() != 3 {
        return Err(EngineError::SubtitleError(format!(
            "Invalid ASS time: {}",
            time_str
        )));
    }
    let hours: i64 = parts[0]
        .parse()
        .map_err(|_| EngineError::SubtitleError("Invalid hours".into()))?;
    let minutes: i64 = parts[1]
        .parse()
        .map_err(|_| EngineError::SubtitleError("Invalid minutes".into()))?;
    let secs_parts: Vec<&str> = parts[2].split('.').collect();
    let seconds: i64 = secs_parts[0]
        .parse()
        .map_err(|_| EngineError::SubtitleError("Invalid seconds".into()))?;
    let centiseconds: i64 = if secs_parts.len() > 1 {
        let cs_str = secs_parts[1];
        let padded = format!("{:0<2}", cs_str);
        padded[..2]
            .parse()
            .map_err(|_| EngineError::SubtitleError("Invalid centiseconds".into()))?
    } else {
        0
    };

    // ASS uses centiseconds (1/100s), convert to microseconds (1cs = 10,000us)
    let total_us = (hours * 3_600_000_000) + (minutes * 60_000_000) + (seconds * 1_000_000) + (centiseconds * 10_000);
    Ok(total_us)
}

/// Strip ASS override tags: remove anything within {\\...} pairs.
fn strip_ass_tags(text: &str) -> String {
    let mut result = String::with_capacity(text.len());
    let mut in_tag = false;
    for ch in text.chars() {
        if ch == '{' {
            in_tag = true;
            continue;
        }
        if in_tag && ch == '}' {
            in_tag = false;
            continue;
        }
        if !in_tag {
            result.push(ch);
        }
    }
    // Replace \N with newline
    result.replace("\\N", "\n").replace("\\n", "\n")
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    // --- SRT tests ---

    #[test]
    fn test_srt_time_to_us_basic() {
        assert_eq!(srt_time_to_us("00:00:01,000").unwrap(), 1_000_000);
    }

    #[test]
    fn test_srt_time_to_us_with_millis() {
        assert_eq!(srt_time_to_us("00:00:01,500").unwrap(), 1_500_000);
    }

    #[test]
    fn test_srt_time_to_us_hours() {
        assert_eq!(srt_time_to_us("01:00:00,000").unwrap(), 3_600_000_000);
    }

    #[test]
    fn test_srt_time_to_us_dot_separator() {
        assert_eq!(srt_time_to_us("00:00:02.250").unwrap(), 2_250_000);
    }

    #[test]
    fn test_parse_srt_single_cue() {
        let lines = vec![
            "1".to_owned(),
            "00:00:01,000 --> 00:00:04,000".to_owned(),
            "Hello world".to_owned(),
            "".to_owned(),
        ];
        let cues = parse_srt_lines(&lines).unwrap();
        assert_eq!(cues.len(), 1);
        assert_eq!(cues[0].start_us, 1_000_000);
        assert_eq!(cues[0].end_us, 4_000_000);
        assert_eq!(cues[0].text, "Hello world");
    }

    #[test]
    fn test_parse_srt_multiline_text() {
        let lines = vec![
            "1".to_owned(),
            "00:00:01,000 --> 00:00:04,000".to_owned(),
            "Line one".to_owned(),
            "Line two".to_owned(),
            "".to_owned(),
        ];
        let cues = parse_srt_lines(&lines).unwrap();
        assert_eq!(cues[0].text, "Line one\nLine two");
    }

    #[test]
    fn test_parse_srt_multiple_cues() {
        let lines = vec![
            "1".to_owned(),
            "00:00:01,000 --> 00:00:02,000".to_owned(),
            "First".to_owned(),
            "".to_owned(),
            "2".to_owned(),
            "00:00:03,000 --> 00:00:04,000".to_owned(),
            "Second".to_owned(),
            "".to_owned(),
        ];
        let cues = parse_srt_lines(&lines).unwrap();
        assert_eq!(cues.len(), 2);
        assert_eq!(cues[0].text, "First");
        assert_eq!(cues[1].text, "Second");
    }

    // --- ASS tests ---

    #[test]
    fn test_ass_time_to_us_basic() {
        assert_eq!(ass_time_to_us("0:00:01.00").unwrap(), 1_000_000);
    }

    #[test]
    fn test_ass_time_to_us_centiseconds() {
        assert_eq!(ass_time_to_us("0:00:01.50").unwrap(), 1_500_000);
    }

    #[test]
    fn test_parse_ass_dialogue() {
        let lines = vec![
            "[Events]".to_owned(),
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text".to_owned(),
            "Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Hello ASS".to_owned(),
        ];
        let cues = parse_ass_lines(&lines).unwrap();
        assert_eq!(cues.len(), 1);
        assert_eq!(cues[0].start_us, 1_000_000);
        assert_eq!(cues[0].end_us, 4_000_000);
        assert_eq!(cues[0].text, "Hello ASS");
    }

    #[test]
    fn test_strip_ass_tags_removes_curly_braces() {
        let input = "{\\i1}Italic{\\i0} text";
        let result = strip_ass_tags(input);
        assert_eq!(result, "Italic text");
    }

    #[test]
    fn test_strip_ass_tags_newline() {
        let input = "Line one\\NLine two";
        let result = strip_ass_tags(input);
        assert_eq!(result, "Line one\nLine two");
    }

    // --- SubtitleManager tests ---

    #[test]
    fn test_manager_cues_at_exact() {
        let mut manager = SubtitleManager::new();
        manager.cues = vec![SubtitleCue {
            start_us: 1_000_000,
            end_us: 2_000_000,
            text: "Test".into(),
        }];
        let active = manager.cues_at(1_500_000);
        assert_eq!(active.len(), 1);
    }

    #[test]
    fn test_manager_cues_at_boundary() {
        let mut manager = SubtitleManager::new();
        manager.cues = vec![SubtitleCue {
            start_us: 1_000_000,
            end_us: 2_000_000,
            text: "Test".into(),
        }];
        assert_eq!(manager.cues_at(2_000_000).len(), 0);
        assert_eq!(manager.cues_at(999_999).len(), 0);
        assert_eq!(manager.cues_at(1_000_000).len(), 1);
    }

    #[test]
    fn test_manager_offset_delays_cue() {
        let mut manager = SubtitleManager::new();
        manager.set_offset(500); // +500ms = +500,000us
        manager.cues = vec![SubtitleCue {
            start_us: 1_000_000,
            end_us: 2_000_000,
            text: "Test".into(),
        }];
        // At 500,000us position, cue is now active (500,000 + 500,000 = 1,000,000)
        assert_eq!(manager.cues_at(500_000).len(), 1);
        // At 499,999us position, cue not yet active
        assert_eq!(manager.cues_at(499_999).len(), 0);
    }

    #[test]
    fn test_manager_negative_offset_advances_cue() {
        let mut manager = SubtitleManager::new();
        manager.set_offset(-500);
        manager.cues = vec![SubtitleCue {
            start_us: 1_000_000,
            end_us: 2_000_000,
            text: "Test".into(),
        }];
        // At 1,500,000us position, adjusted = 1,000,000 (active)
        assert_eq!(manager.cues_at(1_500_000).len(), 1);
    }

    #[test]
    fn test_manager_empty_no_cues() {
        let manager = SubtitleManager::new();
        assert_eq!(manager.cues_at(0).len(), 0);
        assert_eq!(manager.cue_count(), 0);
    }

    #[test]
    fn test_manager_flush_retains_cues() {
        let mut manager = SubtitleManager::new();
        manager.cues = vec![SubtitleCue {
            start_us: 0,
            end_us: 1_000_000,
            text: "Persistent".into(),
        }];
        manager.flush();
        assert_eq!(manager.cue_count(), 1);
    }
}