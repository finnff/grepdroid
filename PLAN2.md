# Grepdroid Codebase Issues

## Critical Issues (will prevent compilation or cause crashes)

### 1. FTS4 Entity is not a proper FTS table — `ScannedImage.kt:18-23`
`ScannedImageFts` uses `@Entity` instead of `@Fts4(contentEntity = ScannedImage::class)`. Room will create a regular table, not an FTS4 virtual table. The MATCH queries in `ImageDao` will fail at runtime because `scanned_images_fts` won't be an FTS table.

**Also:** The FTS entity has `@PrimaryKey(autoGenerate = true)` — FTS4 entities use implicit `rowid` and Room forbids explicit `@PrimaryKey` on `@Fts4` entities.

**Fix:** Replace `@Entity` with `@Fts4(contentEntity = ScannedImage::class)`, remove `@PrimaryKey`, remove `mediaStoreId` field (FTS shadow tables only contain the indexed text columns), rename `extracted_text` to `extractedText` to match the source entity column name.

### 2. FTS column name mismatch — `ImageDao.kt:14` vs `ScannedImage.kt:12`
The DAO query uses `extracted_text` (snake_case) but the `ScannedImage` entity field is `extractedText` (camelCase). Room maps `extractedText` to a column named `extractedText` by default (no `@ColumnInfo` override). The FTS table also references `extracted_text` in the manual triggers in `GrepdroidDatabase.kt:37,50`. These will all fail.

**Fix:** Either add `@ColumnInfo(name = "extracted_text")` to `ScannedImage.extractedText`, or change all SQL references to use `extractedText`.

### 3. Manual FTS triggers conflict with Room's `@Fts4` auto-sync — `GrepdroidDatabase.kt:30-53`
If `@Fts4(contentEntity = ...)` is used (as it should be), Room auto-generates content sync triggers. The manual triggers in `onCreate` will conflict/duplicate. They should be removed entirely once the FTS annotation is fixed.

### 4. `deleteRemoved()` logic is inverted — `ImageRepository.kt:49-61`
The `toRemove` list starts as ALL tracked IDs, then only removes IDs that are unchanged. But it never removes IDs for images that are new-to-scan (modified or new). So `toRemove` will contain IDs of images that exist in MediaStore but have changed dates — those will be deleted right before being re-scanned, which is wasteful but not catastrophic. More critically, the `dao.deleteRemoved(toRemove)` call passes the list of IDs TO delete, but the DAO query is `DELETE FROM scanned_images WHERE mediaStoreId NOT IN (:ids)` — this deletes everything NOT in the list, which is the opposite of what the repository intends.

**Fix:** Either change the DAO query to `WHERE mediaStoreId IN (:ids)`, or change the repository to pass the list of IDs to KEEP.

### 5. Permission never requested — `MainActivity.kt:26-47`
`permissionLauncher` is registered but `permissionLauncher.launch(...)` is never called anywhere. The app will never request `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`, so scanning will find zero images on a fresh install.

**Fix:** Add permission check + request logic in `onCreate` or when scan is triggered.

### 6. KSP version incomplete — `libs.versions.toml:4`
KSP version is `"2.3.0"` but should be `"2.3.0-1.0.24"` (or current matching release). KSP versions must include the suffix to match the Kotlin version. This will fail Gradle plugin resolution.

### 7. SearchScreen creates a new ViewModel instance — `SearchScreen.kt:31`
`viewModel: MainViewModel = viewModel()` with no factory will try to create `MainViewModel` via no-arg constructor, which doesn't exist (it requires `ImageRepository`). This will crash at runtime. Meanwhile, `MainActivity` already creates the ViewModel with the correct factory but doesn't pass it to `SearchScreen`.

**Fix:** Pass the `viewModel` instance from `MainActivity.GrepdroidAppContent()` to `SearchScreen()`.

---

## Moderate Issues (bugs, poor behavior)

### 8. No concurrency in scan loop — `ImageRepository.kt:67-87`
The semaphore is used with `withPermit` inside a sequential `for` loop, meaning images are still processed one at a time. The semaphore only throttles but doesn't parallelize. To get actual parallelism, should use `launch { withPermit { ... } }` or `async` for each image.

### 9. `path` variable used as gate but never used — `MediaStoreScanner.kt:40`
`cursor.getString(3)` uses a hardcoded column index instead of `getColumnIndexOrThrow()`. The `path` value is extracted but never used — only `contentUri` (constructed from the ID) is used. The `continue` on null means images without a `DATA` path are silently skipped, even though they're perfectly accessible via content URI. On Android 10+ (scoped storage), `DATA` is often null/unreliable.

**Fix:** Remove the `DATA` column from the projection and the `path` line entirely.

### 10. `statusBarColor` is deprecated — `Theme.kt:50`
`window.statusBarColor` is deprecated in API 35+. Should use edge-to-edge APIs or `enableEdgeToEdge()` instead.

### 11. Color values missing alpha channel — `Theme.kt:20-22, 25-27`
`Color(0x90CAF9)` is missing the alpha byte — Compose `Color(Int)` treats this as `Color(0x0090CAF9)` which is nearly fully transparent. Should be `Color(0xFF90CAF9.toInt())` or use `Color(0xFF, 0x90, 0xCA, 0xF9)`.

### 12. NavGraph.kt is dead code
`MainActivity` implements its own manual navigation (`when (currentRoute)`) and never calls the `NavGraph` composable. The entire `NavGraph.kt` file is unused. Either use it or delete it.

### 13. `coroutines-play-services` declared in version catalog but never used in dependencies
`libs.versions.toml` defines `coroutines-play-services` but `app/build.gradle.kts` never includes it. `OcrProcessor` uses `com.google.android.gms.tasks.Tasks.await()` which requires this dependency (or `kotlinx-coroutines-play-services` for the `await()` extension). Currently it imports `Tasks.await()` which is the blocking GMS version, not the coroutine one — this blocks the IO thread but works. If the intent is to use the coroutine `await()` extension, the dependency must be added and the import changed.

### 14. Hardcoded dependency version — `app/build.gradle.kts:71`
`"androidx.compose.material:material-icons-extended:1.7.8"` should use the Compose BOM or be added to the version catalog.

---

## Minor Issues

### 15. `Icons.Default.ArrowBack` deprecated
Use `Icons.AutoMirrored.Filled.ArrowBack` instead (Material3 migration).

### 16. `coil-network-okhttp` unnecessary
The app only loads local content URIs. The OkHttp network integration for Coil is not needed and adds unnecessary dependency weight.

### 17. Unused imports in `SearchScreen.kt`
`Screen` (line 25) and `Context` parameter in `ImageCard` are imported/used but `Context` is passed to `ImageCard` without being used inside it.

### 18. `MainViewModelFactory` uses raw types — `MainActivity.kt:91-98`
Works but could use `CreationExtras` or `ViewModelProvider.Factory.companion.from()` for a cleaner pattern.

---

## Summary of Required Fixes Before Build

| # | File | Severity |
|---|------|----------|
| 1 | ScannedImage.kt — FTS4 annotation | Critical |
| 2 | ImageDao.kt / ScannedImage.kt — column name mismatch | Critical |
| 3 | GrepdroidDatabase.kt — remove manual triggers | Critical |
| 4 | ImageRepository.kt — deleteRemoved logic inverted | Critical |
| 5 | MainActivity.kt — permission never requested | Critical |
| 6 | libs.versions.toml — KSP version suffix | Critical |
| 7 | SearchScreen.kt / MainActivity.kt — ViewModel factory | Critical |
| 8 | ImageRepository.kt — no actual parallelism | Moderate |
| 9 | MediaStoreScanner.kt — hardcoded index, unused path | Moderate |
| 10 | Theme.kt — deprecated statusBarColor | Moderate |
| 11 | Theme.kt — transparent colors | Moderate |
| 12 | NavGraph.kt — dead code | Moderate |
| 13 | build.gradle.kts — missing coroutines-play-services | Moderate |

## Verification
After fixes, run:
```bash
./gradlew assembleDebug
```
