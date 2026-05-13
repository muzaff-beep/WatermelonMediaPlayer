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

        if lower.starts_with("title=") && !lower.starts_with("title") == false {
            let rest = &trimmed[6..];
            if let Some(first_char) = rest.chars().next() {
                if !first_char.is_ascii_digit() {
                    title = Some(rest.to_owned());
                    continue;
                }
            }
        }

        if lower.starts_with("numberofentries=") {
            // NumberOfEntries is informational; we don't need to store it.
            continue;
        }

        // FileN=...
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

        // TitleN=...
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

        // LengthN=...
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