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

//! an artist's top songs & discography read through the session. spotify took the web api's
//! top tracks call away in february 2026.
//!
//! the result is json shaped like the web api's `/artists/{id}/top-tracks`
//! response, so the front end reads it the same way.

use librespot::{
    core::{Session, SpotifyUri},
    protocol::metadata::{self, TopTracks},
};
use protobuf::Message;
use serde::Serialize;

use crate::playlist::{TrackPayload, track_metadata};

/// as many as the web api gave
const TOP_TRACK_COUNT: usize = 10;

#[derive(Serialize)]
pub struct ArtistOverviewPayload {
    pub uri: String,
    pub name: String,
    pub avatar_url: String,
    pub tracks: Vec<TrackPayload>,
    pub releases: Vec<ArtistReleaseItem>,
}

#[derive(Serialize)]
pub struct ArtistReleaseItem {
    pub uri: String,
    pub name: String,
    pub release_type: String, // "album", "single", "compilation"
    pub year: i32,
    pub artwork_url: String,
    pub track_count: i32,
}

pub async fn artist_top_tracks_json(session: &Session, artist_uri: &str) -> Result<String, String> {
    let uri = match SpotifyUri::from_uri(artist_uri) {
        Ok(uri @ SpotifyUri::Artist { .. }) => uri,
        _ => return Err(format!("not a spotify artist: {artist_uri}")),
    };
    let bytes = session
        .spclient()
        .get_artist_metadata(&uri)
        .await
        .map_err(|error| format!("Spotify would not send the artist: {error}"))?;
    let artist = metadata::Artist::parse_from_bytes(&bytes)
        .map_err(|error| format!("the artist could not be read: {error}"))?;

    let name = artist.name().to_string();
    let mut avatar_url = String::new();
    let portraits = match artist.portrait_group.as_ref() {
        Some(group) if !group.image.is_empty() => &group.image,
        _ => &artist.portrait,
    };
    let best_img = portraits.iter().max_by_key(|img| {
        let size_score = match img.size() {
            librespot::protocol::metadata::image::Size::XLARGE => 1280,
            librespot::protocol::metadata::image::Size::LARGE => 640,
            librespot::protocol::metadata::image::Size::DEFAULT => 300,
            librespot::protocol::metadata::image::Size::SMALL => 64,
        };
        img.width().max(size_score)
    });
    if let Some(img) = best_img {
        let file_id = img.file_id();
        if !file_id.is_empty() {
            let hex: String = file_id.iter().map(|b| format!("{b:02x}")).collect();
            avatar_url = format!("https://i.scdn.co/image/{hex}");
        }
    }

    let uris = top_track_uris(&artist.top_track, &session.country());
    let found = track_metadata(session, &uris).await?;
    let tracks: Vec<TrackPayload> = uris
        .iter()
        .filter_map(|u| found.get(u).cloned())
        .collect();

    let mut album_uris = Vec::new();

    // Albums
    for group in &artist.album_group {
        for album in &group.album {
            if let Ok(u) = SpotifyUri::try_from(album) {
                if let Ok(uri_str) = u.to_uri() {
                    album_uris.push((uri_str, "album"));
                }
            }
        }
    }

    // Singles & EPs
    for group in &artist.single_group {
        for album in &group.album {
            let rel_type = match album.type_() {
                metadata::album::Type::EP => "single",
                _ => "single",
            };
            if let Ok(u) = SpotifyUri::try_from(album) {
                if let Ok(uri_str) = u.to_uri() {
                    album_uris.push((uri_str, rel_type));
                }
            }
        }
    }

    // Compilations
    for group in &artist.compilation_group {
        for album in &group.album {
            if let Ok(u) = SpotifyUri::try_from(album) {
                if let Ok(uri_str) = u.to_uri() {
                    album_uris.push((uri_str, "compilation"));
                }
            }
        }
    }

    let mut fetch_tasks = Vec::new();
    let spclient = session.spclient();

    for (alb_uri_str, rel_type) in album_uris.into_iter().take(20) {
        if let Ok(sp_uri @ SpotifyUri::Album { .. }) = SpotifyUri::from_uri(&alb_uri_str) {
            let spclient = spclient.clone();
            fetch_tasks.push(tokio::spawn(async move {
                let alb_bytes = spclient.get_album_metadata(&sp_uri).await.ok()?;
                let full_alb = metadata::Album::parse_from_bytes(&alb_bytes).ok()?;
                let alb_name = full_alb.name().to_string();
                if alb_name.is_empty() {
                    return None;
                }
                let year = full_alb.date.as_ref().map(|d| d.year()).unwrap_or(0);
                let covers = match full_alb.cover_group.as_ref() {
                    Some(group) if !group.image.is_empty() => &group.image,
                    _ => &full_alb.cover,
                };
                let mut artwork_url = String::new();
                if let Some(img) = covers.first() {
                    let file_id = img.file_id();
                    if !file_id.is_empty() {
                        let hex: String = file_id.iter().map(|b| format!("{b:02x}")).collect();
                        artwork_url = format!("https://i.scdn.co/image/{hex}");
                    }
                }
                let mut track_count = 0;
                for disc in &full_alb.disc {
                    track_count += disc.track.len() as i32;
                }
                Some(ArtistReleaseItem {
                    uri: alb_uri_str,
                    name: alb_name,
                    release_type: rel_type.to_string(),
                    year,
                    artwork_url,
                    track_count,
                })
            }));
        }
    }

    let mut releases = Vec::new();
    for task in fetch_tasks {
        if let Ok(Some(item)) = task.await {
            releases.push(item);
        }
    }

    serde_json::to_string(&ArtistOverviewPayload {
        uri: artist_uri.to_string(),
        name,
        avatar_url,
        tracks,
        releases,
    })
    .map_err(|error| format!("failed to encode the artist overview: {error}"))
}

/// the list for the account's country, else the one for everywhere, else the
/// first there is
fn top_track_uris(lists: &[TopTracks], country: &str) -> Vec<String> {
    let list = lists
        .iter()
        .find(|list| list.country() == country)
        .or_else(|| lists.iter().find(|list| list.country().is_empty()))
        .or_else(|| lists.first());
    let Some(list) = list else {
        return Vec::new();
    };
    list.track
        .iter()
        .filter_map(|track| SpotifyUri::try_from(track).ok()?.to_uri().ok())
        .take(TOP_TRACK_COUNT)
        .collect()
}
