// rust-core/src/playlist_parser.rs
// M3U and PLS playlist file parsers. Manifesto §1.2: parsing is fast in Rust.

use crate::error::{EngineError, EngineResult};
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;

/// A single entry in a playlist.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PlaylistEntry {
    pub title: Option<String>,
    pub uri: String,
    pub duration_secs: Option<u64>,
}

/// A parsed playlist containing zero or more entries.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Playlist {
    pub entries: Vec<PlaylistEntry>,
    pub title: Option<String>,
}

/// Supported playlist formats.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PlaylistFormat {
    M3U,
    PLS,
}

/// Parse a playlist file at the given path.
/// Detects format by extension: `.m3u` or `.m3u8` → M3U, `.pls` → PLS.
pub fn parse_file(path: &str) -> EngineResult<Playlist> {
    let path = Path::new(path);
    let extension = path
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_lowercase();

    match extension.as_str() {
        "m3u" | "m3u8" => parse_m3u(path),
        "pls" => parse_pls(path),
        _ => Err(EngineError::Internal(format!(
            "Unsupported playlist format: .{}",
            extension
        ))),
    }
}

/// Parse a playlist from an in-memory string. Format must be specified.
pub fn parse_string(content: &str, format: PlaylistFormat) -> EngineResult<Playlist> {
    match format {
        PlaylistFormat::M3U => parse_m3u_string(content),
        PlaylistFormat::PLS => parse_pls_string(content),
    }
}

// ---------------------------------------------------------------------------
// M3U parser
// ---------------------------------------------------------------------------

fn parse_m3u(path: &Path) -> EngineResult<Playlist> {
    let file = File::open(path)
        .map_err(|e| EngineError::Internal(format!("Failed to open M3U: {}", e)))?;
    let reader = BufReader::new(file);
    let content: Vec<String> = reader.lines().filter_map(|l| l.ok()).collect();
    parse_m3u_lines(&content)
}

fn parse_m3u_string(content: &str) -> EngineResult<Playlist> {
    let lines: Vec<String> = content.lines().map(|l| l.to_owned()).collect();
    parse_m3u_lines(&lines)
}

fn parse_m3u_lines(lines: &[String]) -> EngineResult<Playlist> {
    let mut entries: Vec<PlaylistEntry> = Vec::new();
    let mut title: Option<String> = None;
    let mut current_extinf: Option<(Option<String>, Option<u64>)> = None;

    for line in lines {
        let trimmed = line.trim();

        if trimmed.is_empty() {
            continue;
        }
        if trimmed.starts_with('#') && !trimmed.starts_with("#EXTINF") && !trimmed.starts_with("#PLAYLIST") {
            continue;
        }

        if trimmed.starts_with("#PLAYLIST:") {
            title = Some(trimmed[10..].trim().to_owned());
            continue;
        }

        if trimmed.starts_with("#EXTINF:") {
            let body = &trimmed[8..];
            let (duration_str, track_title) = if let Some(comma) = body.find(',') {
                (body[..comma].trim(), Some(body[comma + 1..].trim().to_owned()))
            } else {
                (body.trim(), None)
            };
            let duration = duration_str.parse::<f64>().ok().map(|d| d as u64);
            current_extinf = Some((track_title, duration));
            continue;
        }

        if !trimmed.starts_with('#') {
            let (track_title, duration_secs) = current_extinf.take().unwrap_or((None, None));
            entries.push(PlaylistEntry {
                title: track_title,
                uri: trimmed.to_owned(),
                duration_secs,
            });
        }
    }

    Ok(Playlist { entries, title })
}

// ---------------------------------------------------------------------------
// PLS parser
// ---------------------------------------------------------------------------

fn parse_pls(path: &Path) -> EngineResult<Playlist> {
    let file = File::open(path)
        .map_err(|e| EngineError::Internal(format!("Failed to open PLS: {}", e)))?;
    let reader = BufReader::new(file);
    let content: Vec<String> = reader.lines().filter_map(|l| l.ok()).collect();
    parse_pls_lines(&content)
}

fn parse_pls_string(content: &str) -> EngineResult<Playlist> {
    let lines: Vec<String> = content.lines().map(|l| l.to_owned()).collect();
    parse_pls_lines(&lines)
}

fn parse_pls_lines(lines: &[String]) -> EngineResult<Playlist> {
    let mut entries: Vec<PlaylistEntry> = Vec::new();
    let mut title: Option<String> = None;
    let mut in_header = true;

    for line in lines {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }
        if trimmed.eq_ignore_ascii_case("[playlist]") {
            in_header = false;
            continue;
        }
        let lower = trimmed.to_lowercase();

        if in_header {
            continue;
        }

        if lower.starts_with("title=") {
            let rest = &trimmed[6..];
            if let Some(first_char) = rest.chars().next() {
                if !first_char.is_ascii_digit() {
                    title = Some(rest.to_owned());
                    continue;
                }
            }
        }

        if lower.starts_with("numberofentries=") {
            continue;
        }

        if lower.starts_with("file") {
            let rest = &trimmed[4..];
            if let Some(eq) = rest.find('=') {
                let index: usize = rest[..eq].parse().unwrap_or(0);
                let uri = rest[eq + 1..].trim().to_owned();
                let idx = index.saturating_sub(1);
                while entries.len() <= idx {
                    entries.push(PlaylistEntry {
                        title: None,
                        uri: String::new(),
                        duration_secs: None,
                    });
                }
                entries[idx].uri = uri;
            }
        }

        if lower.starts_with("title") && lower.contains('=') {
            let rest = &trimmed[5..];
            if let Some(eq) = rest.find('=') {
                let index_str = &rest[..eq];
                if let Ok(index) = index_str.parse::<usize>() {
                    let track_title = rest[eq + 1..].trim().to_owned();
                    let idx = index.saturating_sub(1);
                    while entries.len() <= idx {
                        entries.push(PlaylistEntry {
                            title: None,
                            uri: String::new(),
                            duration_secs: None,
                        });
                    }
                    entries[idx].title = Some(track_title);
                }
            }
        }

        if lower.starts_with("length") && lower.contains('=') {
            let rest = &trimmed[6..];
            if let Some(eq) = rest.find('=') {
                let index_str = &rest[..eq];
                if let Ok(index) = index_str.parse::<usize>() {
                    let length_str = rest[eq + 1..].trim();
                    let duration = length_str.parse::<i64>().ok().map(|d| if d < 0 { 0 } else { d as u64 });
                    let idx = index.saturating_sub(1);
                    while entries.len() <= idx {
                        entries.push(PlaylistEntry {
                            title: None,
                            uri: String::new(),
                            duration_secs: None,
                        });
                    }
                    entries[idx].duration_secs = duration;
                }
            }
        }
    }

    entries.retain(|e| !e.uri.is_empty());
    Ok(Playlist { entries, title })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_m3u_basic() {
        let content = "#EXTM3U\n#EXTINF:123,Sample Track\n/path/to/file.mp3\n";
        let playlist = parse_string(content, PlaylistFormat::M3U).unwrap();
        assert_eq!(playlist.entries.len(), 1);
        assert_eq!(playlist.entries[0].title.as_deref(), Some("Sample Track"));
        assert_eq!(playlist.entries[0].uri, "/path/to/file.mp3");
        assert_eq!(playlist.entries[0].duration_secs, Some(123));
    }

    #[test]
    fn test_parse_m3u_multiple_entries() {
        let content = "#EXTM3U\n#EXTINF:-1,First\none.mp3\n#EXTINF:240,Second\ntwo.mp3\n";
        let playlist = parse_string(content, PlaylistFormat::M3U).unwrap();
        assert_eq!(playlist.entries.len(), 2);
        assert_eq!(playlist.entries[0].title.as_deref(), Some("First"));
        assert_eq!(playlist.entries[1].duration_secs, Some(240));
    }

    #[test]
    fn test_parse_m3u_playlist_title() {
        let content = "#EXTM3U\n#PLAYLIST:My Mix\n#EXTINF:10,Track\ntrack.mp3\n";
        let playlist = parse_string(content, PlaylistFormat::M3U).unwrap();
        assert_eq!(playlist.title.as_deref(), Some("My Mix"));
    }

    #[test]
    fn test_parse_m3u_no_extinf() {
        let content = "#EXTM3U\n/path/one.mp3\n/path/two.mp3\n";
        let playlist = parse_string(content, PlaylistFormat::M3U).unwrap();
        assert_eq!(playlist.entries.len(), 2);
        assert!(playlist.entries[0].title.is_none());
        assert!(playlist.entries[0].duration_secs.is_none());
    }

    #[test]
    fn test_parse_pls_basic() {
        let content = "[playlist]\nNumberOfEntries=1\nFile1=/music/song.mp3\nTitle1=My Song\nLength1=180\n";
        let playlist = parse_string(content, PlaylistFormat::PLS).unwrap();
        assert_eq!(playlist.entries.len(), 1);
        assert_eq!(playlist.entries[0].uri, "/music/song.mp3");
        assert_eq!(playlist.entries[0].title.as_deref(), Some("My Song"));
        assert_eq!(playlist.entries[0].duration_secs, Some(180));
    }

    #[test]
    fn test_parse_pls_multiple_entries() {
        let content = "[playlist]\nNumberOfEntries=2\nFile1=a.mp3\nTitle1=A\nLength1=10\nFile2=b.mp3\nTitle2=B\nLength2=20\n";
        let playlist = parse_string(content, PlaylistFormat::PLS).unwrap();
        assert_eq!(playlist.entries.len(), 2);
        assert_eq!(playlist.entries[1].uri, "b.mp3");
        assert_eq!(playlist.entries[1].title.as_deref(), Some("B"));
    }

    #[test]
    fn test_parse_pls_negative_length_clamped() {
        let content = "[playlist]\nNumberOfEntries=1\nFile1=x.mp3\nLength1=-1\n";
        let playlist = parse_string(content, PlaylistFormat::PLS).unwrap();
        assert_eq!(playlist.entries[0].duration_secs, Some(0));
    }

    #[test]
    fn test_parse_pls_missing_entries_skipped() {
        let content = "[playlist]\nNumberOfEntries=3\nFile1=a.mp3\nFile3=c.mp3\n";
        let playlist = parse_string(content, PlaylistFormat::PLS).unwrap();
        assert_eq!(playlist.entries.len(), 2);
        assert_eq!(playlist.entries[0].uri, "a.mp3");
        assert_eq!(playlist.entries[1].uri, "c.mp3");
    }

    #[test]
    fn test_unsupported_format() {
        let result = parse_file("test.wpl");
        assert!(result.is_err());
    }
}