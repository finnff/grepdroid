# Grepdroid — Implementation Plan

## Context

Build an Android app that functions as "grep for my gallery" — scans images on-device using OCR, stores extracted text in a local SQLite database, and provides a search UI to find images by their text content. All processing stays on-device for privacy.

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.3.0 |
| AGP | 9.1.0 |
| KSP | 2.3.0-1.0.24 |
| Gradle | 9.2.1 (project-local, see below) |
| compileSdk / targetSdk | 36 (Android 16) |
| minSdk | 26 |
| Compose BOM | 2026.02.01 |
| Room | 2.8.4 (with KSP) |
| ML Kit Text Recognition | 16.0.1 |
| Coil 3 | 3.4.0 (image loading) |
| Navigation Compose | 2.9.7 |
| Activity Compose | 1.12.4 |
| Lifecycle | 2.10.0 |
| Coroutines Play Services | 1.9.0 |

## Project Structure

```
grepdroid/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/grepdroid/
│           ├── GrepdroidApp.kt              # Application class, manual DI
│           ├── MainActivity.kt              # Single activity, permission handling
│           ├── data/
│           │   ├── db/
│           │   │   ├── ScannedImage.kt      # Room entity
│           │   │   ├── ScannedImageFts.kt   # FTS4 virtual table entity
│           │   │   ├── ImageDao.kt          # DAO with FTS search
│           │   │   └── GrepdroidDatabase.kt # Room database
│           │   ├── MediaStoreScanner.kt     # Enumerates gallery images
│           │   └── OcrProcessor.kt          # ML Kit wrapper
│           ├── repository/
│           │   └── ImageRepository.kt       # Coordinates scan + search
│           └── ui/
│               ├── MainViewModel.kt         # Search state, scan lifecycle
│               ├── screen/
│               │   ├── SearchScreen.kt      # Search bar + image grid
│               │   └── ImageViewerScreen.kt # Full image + share button
│               ├── navigation/
│               │   └── NavGraph.kt          # Compose Navigation
│               └── theme/
│                   └── Theme.kt             # Material3 theme
```

## Database Schema

**Entity: `scanned_images`**
- `mediaStoreId: Long` (PK) — MediaStore `_ID`
- `contentUri: String` — `content://media/external/images/media/{id}`
- `displayName: String` — filename
- `dateModified: Long` — MediaStore `DATE_MODIFIED` (for change detection)
- `extractedText: String` — OCR result
- `scannedAt: Long` — timestamp of OCR processing

**FTS4 table: `scanned_images_fts`** — shadows `scanned_images` on `extractedText` column for fast full-text search. Room auto-generates sync triggers via `@Fts4(contentEntity = ScannedImage::class)`.

**Key queries:**
- `search(query)` — JOIN scanned_images with FTS table using MATCH
- `getAllTracked()` — returns (mediaStoreId, dateModified) pairs for diff detection
- `upsert(image)` — insert or update scanned image
- `deleteRemoved(existingIds)` — clean up deleted images

## Key Design Decisions

1. **Change detection**: Compare MediaStore `DATE_MODIFIED` against stored value. New IDs = new images, changed dates = modified images, missing IDs = deleted images.
2. **Concurrency**: Scan uses `Semaphore(4)` for parallel OCR on Snapdragon 8 Gen 5. No WorkManager — ViewModel-scoped coroutine is sufficient since scan only runs while app is visible.
3. **Search**: FTS4 with prefix matching (`query*`) for forgiving search. Debounced at 300ms.
4. **Image loading**: Coil 3 for thumbnails and full image display.
5. **Sharing**: Standard `ACTION_SEND` intent with `image/*` MIME type and content URI.
6. **Permissions**: `READ_MEDIA_IMAGES` on API 33+, `READ_EXTERNAL_STORAGE` (maxSdkVersion=32) for older.
7. **No DI framework**: Manual injection via `GrepdroidApp` lazy properties.

## Progress

### DONE - Step 1: Gradle / Project Setup
- Installed Android SDK command-line tools, platform 35+36, build-tools 35+36, platform-tools
- Created all Gradle config files (`settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`)
- Gradle 9.2.1 wrapper — **fully project-local**:
  - `gradlew` patched to set `GRADLE_USER_HOME=<project>/.gradle/` by default (overridable via env)
  - All caches, daemons, and distributions stored in `<project>/.gradle/`
  - `.gradle/` is gitignored
  - No writes to `~/.gradle`, no sudo needed
- Version catalog updated to 2026 stack (see Tech Stack above)

### DONE - Step 2: AndroidManifest + Application Class
- `AndroidManifest.xml` — permissions, app class, activity declared
- `GrepdroidApp.kt` — exists (needs review)

### DONE - Step 3: Database Layer (needs review)
- `ScannedImage.kt` — entity + FTS4 entity in same file
- `ImageDao.kt`, `GrepdroidDatabase.kt` — exist
- Known issues: FTS4 entity has `@PrimaryKey` (not valid on FTS entities), DAO uses `Pair<Long,Long>` (Room can't map this — needs a data class)

### DONE - Step 4: Data Layer (needs review)
- `MediaStoreScanner.kt`, `OcrProcessor.kt` — exist
- Known issue: `OcrProcessor` uses `visionText.text` directly on Task object (needs `.await()` or callback bridging)

### DONE - Step 5: Repository (needs review)
- `ImageRepository.kt` — exists

### DONE - Step 6: UI (needs review)
- All UI files exist: `Theme.kt`, `Typography.kt`, `MainViewModel.kt`, `SearchScreen.kt`, `ImageViewerScreen.kt`, `NavGraph.kt`, `MainActivity.kt`

### PARTIAL - Step 7: Build & Verify
- Launcher icons: `ic_launcher_background.xml` created, still need foreground + adaptive icon XMLs
- All source files need review for compilation errors before build will pass

## Remaining Work
1. Fix launcher icons (foreground drawable + adaptive icon XMLs in `mipmap-anydpi-v26/`)
2. Fix compile errors in source files (FTS entity, DAO return types, OCR coroutine bridging)
3. Get `./gradlew assembleDebug` to pass with 2026 stack
4. Add unit tests and instrumented tests (see Testing Plan below)

## Testing Plan

### Unit Tests (`app/src/test/`)
- **ImageRepository diff logic**: Test that new/modified/deleted images are correctly detected
  - Mock DAO + MediaStoreScanner, verify correct upsert/delete calls
  - Test: all new images, mix of new+existing, deleted images, modified dates

### Instrumented Tests (`app/src/androidTest/`)
- **Room DAO tests**: Use in-memory Room database
  - Test `upsert` + `getById` round-trip
  - Test `search` via FTS4 MATCH returns correct results
  - Test `deleteRemoved` cleans up stale entries
  - Test `getAllTracked` returns correct (id, dateModified) pairs
- **E2E scan flow** (if feasible to script):
  - Verify that scanning a known set of images populates the database
  - Verify search returns expected matches
  - Note: requires device/emulator with test images, harder to automate in CI

### Verification Commands
```bash
./gradlew assembleDebug          # Build check
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
```
