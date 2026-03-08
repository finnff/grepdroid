# Grepdroid

Android app that OCR-scans your photo gallery and lets you search for text found in images. Everything runs on-device.

## Features

- **OCR scanning** — uses ML Kit Text Recognition to extract text from images
- **Full-text search** — SQLite FTS4 for fast substring matching
- **Fuzzy search** — toggle fuzzy mode for approximate matches with relevance scores
- **Folder picker** — select which gallery folders to scan
- **Grid layout toggle** — cycle between 2, 3, or 4 column layouts
- **Sort control** — sort results alphabetically, newest first, or oldest first

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite FTS4), ML Kit Text Recognition, Coil 3
- No DI framework — manual injection

## Building

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Testing

```bash
./gradlew test
```
