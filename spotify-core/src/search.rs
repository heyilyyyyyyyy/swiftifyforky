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

//! Search & Library implementation using spclient context-resolve and rootlist

use serde::Serialize;
use protobuf::Message;
use librespot::core::{Session, SpotifyUri};
use crate::playlist::{TrackPayload, track_metadata};

#[derive(Serialize)]
pub struct SearchResultsPayload {
    pub tracks: Vec<TrackPayload>,
}

#[derive(Serialize)]
pub struct SearchArtistsPayload {
    pub artists: Vec<SearchArtistItem>,
}

#[derive(Clone, Serialize)]
pub struct SearchArtistItem {
    pub uri: String,
    pub name: String,
    pub artwork_url: String,
}

pub async fn search_tracks_json(session: &Session, query: &str) -> Result<String, String> {
    let trimmed = query.trim();
    if trimmed.is_empty() {
        return Ok(r#"{"tracks":[]}"#.to_string());
    }
    let encoded_query = trimmed.replace(' ', "+");
    let uri = format!("spotify:search:{encoded_query}");

    let context = session
        .spclient()
        .get_context(&uri)
        .await
        .map_err(|error| format!("Spotify search context failed: {error}"))?;

    let mut uris = Vec::new();
    for page in context.pages {
        for track in page.tracks {
            if let Some(track_uri) = track.uri {
                if !track_uri.is_empty() {
                    uris.push(track_uri);
                }
            }
        }
    }

    let found = track_metadata(session, &uris).await?;
    let tracks: Vec<TrackPayload> = uris
        .iter()
        .filter_map(|u| found.get(u).cloned())
        .collect();

    serde_json::to_string(&SearchResultsPayload { tracks })
        .map_err(|error| format!("failed to encode search results: {error}"))
}

pub async fn search_artists_json(session: &Session, query: &str) -> Result<String, String> {
    let trimmed = query.trim();
    if trimmed.is_empty() {
        return Ok(r#"{"artists":[]}"#.to_string());
    }

    let encoded_query = trimmed.replace(' ', "+");
    let uri = format!("spotify:search:{encoded_query}");

    let mut uris = Vec::new();
    if let Ok(context) = session.spclient().get_context(&uri).await {
        for page in context.pages {
            for track in page.tracks {
                if let Some(track_uri) = track.uri {
                    if !track_uri.is_empty() {
                        uris.push(track_uri);
                    }
                }
            }
        }
    }

    let found = track_metadata(session, &uris).await.unwrap_or_default();
    let mut artists_map = std::collections::HashMap::new();
    let mut ordered_artist_uris = Vec::new();

    for u in &uris {
        if let Some(track) = found.get(u) {
            for art in &track.artists {
                if !artists_map.contains_key(&art.uri) {
                    artists_map.insert(art.uri.clone(), art.name.clone());
                    ordered_artist_uris.push(art.uri.clone());
                }
            }
        }
    }

    // Sort to prioritize exact/close name matches with query
    let lower_q = trimmed.to_lowercase();
    ordered_artist_uris.sort_by(|a, b| {
        let name_a = artists_map.get(a).unwrap().to_lowercase();
        let name_b = artists_map.get(b).unwrap().to_lowercase();
        let exact_a = name_a == lower_q;
        let exact_b = name_b == lower_q;
        let starts_a = name_a.starts_with(&lower_q);
        let starts_b = name_b.starts_with(&lower_q);

        match (exact_a, exact_b) {
            (true, false) => std::cmp::Ordering::Less,
            (false, true) => std::cmp::Ordering::Greater,
            _ => match (starts_a, starts_b) {
                (true, false) => std::cmp::Ordering::Less,
                (false, true) => std::cmp::Ordering::Greater,
                _ => std::cmp::Ordering::Equal,
            },
        }
    });

    let mut artists = Vec::new();
    // Resolve avatar for top 10 unique artists
    for art_uri in ordered_artist_uris.into_iter().take(10) {
        let name = artists_map.get(&art_uri).cloned().unwrap_or_default();
        let mut artwork_url = String::new();

        if let Ok(sp_uri @ SpotifyUri::Artist { .. }) = SpotifyUri::from_uri(&art_uri) {
            if let Ok(bytes) = session.spclient().get_artist_metadata(&sp_uri).await {
                if let Ok(artist) = librespot::protocol::metadata::Artist::parse_from_bytes(&bytes) {
                    let portraits = match artist.portrait_group.as_ref() {
                        Some(group) if !group.image.is_empty() => &group.image,
                        _ => &artist.portrait,
                    };
                    if let Some(img) = portraits.first() {
                        let file_id = img.file_id();
                        if !file_id.is_empty() {
                            let hex: String = file_id.iter().map(|b| format!("{b:02x}")).collect();
                            artwork_url = format!("https://i.scdn.co/image/{hex}");
                        }
                    }
                }
            }
        }

        artists.push(SearchArtistItem {
            uri: art_uri,
            name,
            artwork_url,
        });
    }

    serde_json::to_string(&SearchArtistsPayload { artists })
        .map_err(|error| format!("failed to encode search artists: {error}"))
}

#[derive(Clone, Serialize)]
pub struct PlaylistMetadataItem {
    pub uri: String,
    pub name: String,
    pub owner: String,
    pub artwork_url: String,
}

#[derive(Serialize)]
pub struct UserPlaylistsPayload {
    pub playlists: Vec<PlaylistMetadataItem>,
}

pub async fn user_playlists_json(session: &Session) -> Result<String, String> {
    let bytes = session
        .spclient()
        .get_rootlist(0, Some(100))
        .await
        .map_err(|e| format!("failed to get rootlist: {e}"))?;

    let list = librespot::protocol::playlist4_external::SelectedListContent::parse_from_bytes(&bytes)
        .map_err(|e| format!("failed to parse rootlist: {e}"))?;

    let mut items_to_resolve = Vec::new();
    if let Some(contents) = list.contents.as_ref() {
        for item in &contents.items {
            let uri = item.uri().to_owned();
            if uri.starts_with("spotify:playlist:") {
                items_to_resolve.push(uri);
            }
        }
    }

    let mut playlists = Vec::new();
    for uri in items_to_resolve {
        let mut final_name = String::new();
        let mut owner = "Spotify".to_string();
        let mut artwork_url = String::new();

        if let Ok(SpotifyUri::Playlist { id, .. }) = SpotifyUri::from_uri(&uri) {
            if let Ok(pl_bytes) = session.spclient().get_playlist(&id).await {
                if let Ok(pl_content) = librespot::protocol::playlist4_external::SelectedListContent::parse_from_bytes(&pl_bytes) {
                    if let Some(attrs) = pl_content.attributes.as_ref() {
                        if let Some(n) = attrs.name.as_ref() {
                            final_name = n.clone();
                        }
                        // Check picture bytes
                        if let Some(pic_bytes) = attrs.picture.as_ref() {
                            if !pic_bytes.is_empty() {
                                let hex: String = pic_bytes.iter().map(|b| format!("{b:02x}")).collect();
                                artwork_url = format!("https://i.scdn.co/image/{hex}");
                            }
                        }
                        // Check picture_size urls
                        if artwork_url.is_empty() {
                            if let Some(ps) = attrs.picture_size.first() {
                                if let Some(u) = ps.url.as_ref() {
                                    artwork_url = u.clone();
                                }
                            }
                        }
                    }
                    if let Some(o) = pl_content.owner_username.as_ref() {
                        owner = o.clone();
                    }
                }
            }
        }

        if final_name.is_empty() {
            final_name = "Playlist".to_string();
        }

        playlists.push(PlaylistMetadataItem {
            uri,
            name: final_name,
            owner,
            artwork_url,
        });
    }

    serde_json::to_string(&UserPlaylistsPayload { playlists })
        .map_err(|e| format!("failed to encode playlists: {e}"))
}

pub async fn collection_tracks_json(session: &Session) -> Result<String, String> {
    let username = session.username();
    let uri = format!("spotify:user:{username}:collection");

    let context = session
        .spclient()
        .get_context(&uri)
        .await
        .map_err(|error| format!("Spotify collection context failed: {error}"))?;

    let mut uris = Vec::new();
    for page in context.pages {
        for track in page.tracks {
            if let Some(track_uri) = track.uri {
                if !track_uri.is_empty() {
                    uris.push(track_uri);
                }
            }
        }
    }

    let found = track_metadata(session, &uris).await?;
    let tracks: Vec<TrackPayload> = uris
        .iter()
        .filter_map(|u| found.get(u).cloned())
        .collect();

    serde_json::to_string(&SearchResultsPayload { tracks })
        .map_err(|error| format!("failed to encode collection tracks: {error}"))
}
