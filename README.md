# Grepdroid

Android app that OCR-scans your photo gallery **and your videos** and lets you search for text in either. Everything runs on-device — no network, no uploads.

[Download the latest APK](https://github.com/finnff/grepdroid/releases/latest)

## Features

### Search
- **OCR scanning** — ML Kit Text Recognition extracts text from images
- **Video indexing** — extracts 1 frame per second from each video, runs the same OCR pipeline, and stores per-frame timestamps so search results can jump straight to the matching moment
- **Full-text search** — SQLite FTS4 for fast substring matching across images + videos in one query
- **Fuzzy search** — toggle for approximate matches with relevance scores
- **Media filter** — top-bar toggle cycles *Both / Images only / Videos only*, with a snackbar confirming the active filter

### Viewing
- **Image viewer** — tap the image to open fullscreen; "Open in gallery" launches your preferred app
- **Built-in video player** (Media3 / ExoPlayer) — starts at the first frame matching your search; the seek bar highlights every stretch where the text appears, and tapping a match chip jumps there
  - Tap to show/hide controls, double-tap left/right to skip ±10s, drag the seek bar to scrub
  - Playback speed (1x / 1.5x / 2x / 0.5x), mute, fullscreen (landscape clips rotate sideways; turning the phone sideways goes fullscreen)
  - Pauses for calls/other audio and when headphones are unplugged; releases the decoder while the app is in the background
- **Selectable extracted text** — long-press to select and copy part of the OCR text; the "Hide repetitions" switch collapses lines that repeat back-to-back (e.g. a caption OCR'd on every video frame)
- **Open in another app** — still one tap away (top bar, or fullscreen top-right) for VLC etc. (passes along the current playback position)
- **App chooser with remembered defaults** — pick which gallery / player to use once and the app remembers it; clear it any time from the chooser dialog

### Controls
- **Folder picker** — pick image folders and video folders independently; video indexing is opt-in (off by default, since it's slower than images)
- **Grid layout toggle** — 2 / 3 / 4 columns
- **Sort control** — best match, alphabetical, newest first, oldest first

## Tech Stack

- Kotlin 2.3, Jetpack Compose, Material 3
- Room (SQLite FTS4) with non-destructive migrations
- ML Kit Text Recognition (on-device)
- `MediaMetadataRetriever` for video frame extraction
- Media3 ExoPlayer + `media3-ui-compose` for in-app playback
- Coil 3 for image loading
- No DI framework — manual injection via `GrepdroidApp` lazy properties

## Permissions

- `READ_MEDIA_IMAGES` — required for image scanning
- `READ_MEDIA_VIDEO` — required only if you turn on video indexing; you can decline this and still use image search

## Building

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Testing

```bash
./gradlew test
```
