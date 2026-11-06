/*
 * Swiftify, a spotify player for desktop
 * Copyright (C) 2026  shred0s
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

//! Album metadata & track listing through session

use librespot::{
    core::{Session, SpotifyUri},
    protocol::metadata,
};
use protobuf::Message;
use serde::Serialize;

use crate::playlist::{TrackPayload, track_metadata};

#[derive(Serialize)]
pub struct AlbumDetailPayload {
    pub uri: String,
    pub name: String,
    pub artist_name: String,
    pub artist_uri: String,
    pub year: i32,
    pub artwork_url: String,
    pub tracks: Vec<TrackPayload>,
}

pub async fn album_json(session: &Session, album_uri: &str) -> Result<String, String> {
    let uri = match SpotifyUri::from_uri(album_uri) {
        Ok(uri @ SpotifyUri::Album { .. }) => uri,
        _ => return Err(format!("not a spotify album: {album_uri}")),
    };

    let bytes = session
        .spclient()
        .get_album_metadata(&uri)
        .await
        .map_err(|error| format!("Spotify would not send album: {error}"))?;

    let album = metadata::Album::parse_from_bytes(&bytes)
        .map_err(|error| format!("album could not be parsed: {error}"))?;

    let name = album.name().to_string();
    let year = album.date.as_ref().map(|d| d.year()).unwrap_or(0);

    let (artist_name, artist_uri) = if let Some(artist) = album.artist.first() {
        let n = artist.name().to_string();
        let u = SpotifyUri::try_from(artist).ok().and_then(|u| u.to_uri().ok()).unwrap_or_default();
        (n, u)
    } else {
        ("Unknown Artist".to_string(), String::new())
    };

    let covers = match album.cover_group.as_ref() {
        Some(group) if !group.image.is_empty() => &group.image,
        _ => &album.cover,
    };
    let mut artwork_url = String::new();
    if let Some(img) = covers.first() {
        let file_id = img.file_id();
        if !file_id.is_empty() {
            let hex: String = file_id.iter().map(|b| format!("{b:02x}")).collect();
            artwork_url = format!("https://i.scdn.co/image/{hex}");
        }
    }

    let mut track_uris = Vec::new();
    for disc in &album.disc {
        for track in &disc.track {
            if let Ok(track_uri) = SpotifyUri::try_from(track) {
                if let Ok(u) = track_uri.to_uri() {
                    track_uris.push(u);
                }
            }
        }
    }

    let found = track_metadata(session, &track_uris).await?;
    let tracks: Vec<TrackPayload> = track_uris
        .iter()
        .filter_map(|u| found.get(u).cloned())
        .collect();

    serde_json::to_string(&AlbumDetailPayload {
        uri: album_uri.to_string(),
        name,
        artist_name,
        artist_uri,
        year,
        artwork_url,
        tracks,
    })
    .map_err(|e| format!("failed to serialize album: {e}"))
}
