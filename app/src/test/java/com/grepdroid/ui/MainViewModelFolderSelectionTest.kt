package com.grepdroid.ui

import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.UserPreferences
import com.grepdroid.data.OcrProcessor
import com.grepdroid.data.db.ImageDao
import com.grepdroid.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for folder picker selection logic in MainViewModel.
 *
 * Mock setup: 4 folders (buckets) with IDs 1, 2, 3, 4.
 * "Camera" (100 images), "Screenshots" (50), "Downloads" (25), "WhatsApp" (10).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelFolderSelectionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockDao: ImageDao = mock()
    private val mockScanner: MediaStoreScanner = mock()
    private val mockOcr: OcrProcessor = mock()

    private val fakeBuckets = listOf(
        MediaStoreScanner.ImageBucket(1L, "Camera", 100),
        MediaStoreScanner.ImageBucket(2L, "Screenshots", 50),
        MediaStoreScanner.ImageBucket(3L, "Downloads", 25),
        MediaStoreScanner.ImageBucket(4L, "WhatsApp", 10)
    )
    private val allBucketIds = fakeBuckets.map { it.bucketId }.toSet()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(mockDao.getTotalCount()).thenReturn(flowOf(0))
        whenever(mockDao.getAllTracked()).thenReturn(flowOf(emptyList()))
        whenever(mockScanner.getAvailableBuckets()).thenReturn(fakeBuckets)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Creates a fake UserPreferences backed by in-memory vars. */
    private fun createFakePrefs(initialBucketIds: Set<Long> = emptySet()): UserPreferences {
        val prefs = mock<UserPreferences>()
        var storedBucketIds = initialBucketIds
        whenever(prefs.selectedBucketIds).thenAnswer { storedBucketIds }
        doAnswer { storedBucketIds = it.getArgument(0); null }
            .whenever(prefs).selectedBucketIds = any()
        whenever(prefs.useFuzzySearch).thenReturn(false)
        whenever(prefs.gridColumns).thenReturn(3)
        whenever(prefs.sortOrder).thenReturn("ALPHABETICAL")
        return prefs
    }

    /** Creates a ViewModel with all dispatchers pointing to the test dispatcher. */
    private fun createViewModel(prefs: UserPreferences): MainViewModel {
        val repo = ImageRepository(mockDao, mockScanner, mockOcr, prefs, testDispatcher)
        return MainViewModel(repo, prefs, ioDispatcher = testDispatcher)
    }

    // ---------------------------------------------------------------
    // loadBuckets - opening the folder picker
    // ---------------------------------------------------------------

    @Test
    fun `loadBuckets - fresh app (no stored selection) shows all folders checked`() = runTest {
        val prefs = createFakePrefs(emptySet()) // empty = "all" in storage
        val vm = createViewModel(prefs)

        vm.loadBuckets()

        val state = vm.uiState.value
        assertTrue("Folder picker should be visible", state.showFolderPicker)
        assertEquals("All 4 buckets should be available", 4, state.availableBuckets.size)
        assertEquals("All bucket IDs should be selected", allBucketIds, state.selectedBucketIds)
    }

    @Test
    fun `loadBuckets - with subset stored shows only those checked`() = runTest {
        val prefs = createFakePrefs(setOf(1L, 3L))
        val vm = createViewModel(prefs)

        vm.loadBuckets()

        val state = vm.uiState.value
        assertEquals("Only stored buckets should be selected", setOf(1L, 3L), state.selectedBucketIds)
    }

    @Test
    fun `loadBuckets - stored bucket ID no longer in available buckets`() = runTest {
        // User previously selected bucket 5 which no longer exists on device
        val prefs = createFakePrefs(setOf(1L, 5L))
        val vm = createViewModel(prefs)

        vm.loadBuckets()

        val state = vm.uiState.value
        // Stored IDs shown as-is; bucket 5 won't appear in the list but stays in selection
        assertEquals(setOf(1L, 5L), state.selectedBucketIds)
    }

    @Test
    fun `loadBuckets - previously had 3 folders stored, now 4 available, only stored 3 shown checked`() = runTest {
        // User selected folders 1,2,3 last time. Now folder 4 appeared (new app, SD card, etc.)
        val prefs = createFakePrefs(setOf(1L, 2L, 3L))
        val vm = createViewModel(prefs)

        vm.loadBuckets()

        val state = vm.uiState.value
        assertEquals("Only previously stored 3 should be checked", setOf(1L, 2L, 3L), state.selectedBucketIds)
        assertEquals("All 4 should be available in list", 4, state.availableBuckets.size)
    }

    // ---------------------------------------------------------------
    // selectAllBuckets - "All folders" toggle
    // ---------------------------------------------------------------

    @Test
    fun `selectAll - when none selected, selects all`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()

        // Deselect everything first
        vm.selectAllBuckets() // all -> empty
        assertEquals("Precondition - nothing selected", emptySet<Long>(), vm.uiState.value.selectedBucketIds)

        vm.selectAllBuckets()

        assertEquals("All should be selected", allBucketIds, vm.uiState.value.selectedBucketIds)
    }

    @Test
    fun `selectAll - when all selected, deselects all`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()
        assertEquals("Precondition - all selected", allBucketIds, vm.uiState.value.selectedBucketIds)

        vm.selectAllBuckets()

        assertEquals("All should be deselected", emptySet<Long>(), vm.uiState.value.selectedBucketIds)
    }

    @Test
    fun `selectAll - when some selected, selects all`() = runTest {
        val prefs = createFakePrefs(setOf(1L, 2L))
        val vm = createViewModel(prefs)
        vm.loadBuckets()
        assertEquals("Precondition - 2 selected", setOf(1L, 2L), vm.uiState.value.selectedBucketIds)

        vm.selectAllBuckets()

        assertEquals("All should be selected", allBucketIds, vm.uiState.value.selectedBucketIds)
    }

    @Test
    fun `selectAll toggle round-trip - all on then all off then all on`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()

        // Start: all selected
        assertEquals(allBucketIds, vm.uiState.value.selectedBucketIds)

        // Toggle off
        vm.selectAllBuckets()
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedBucketIds)

        // Toggle on
        vm.selectAllBuckets()
        assertEquals(allBucketIds, vm.uiState.value.selectedBucketIds)
    }

    // ---------------------------------------------------------------
    // toggleBucket - individual folder toggle
    // ---------------------------------------------------------------

    @Test
    fun `toggleBucket - adds bucket when not selected`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()
        // Deselect all first
        vm.selectAllBuckets()

        vm.toggleBucket(2L)

        assertEquals(setOf(2L), vm.uiState.value.selectedBucketIds)
    }

    @Test
    fun `toggleBucket - removes bucket when already selected`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()
        // All are selected initially

        vm.toggleBucket(3L)

        assertEquals(setOf(1L, 2L, 4L), vm.uiState.value.selectedBucketIds)
    }

    @Test
    fun `toggleBucket - removing last bucket results in empty set`() = runTest {
        val prefs = createFakePrefs(setOf(1L))
        val vm = createViewModel(prefs)
        vm.loadBuckets()

        vm.toggleBucket(1L)

        assertEquals("Should be empty after removing last", emptySet<Long>(), vm.uiState.value.selectedBucketIds)
    }

    // ---------------------------------------------------------------
    // confirmBucketSelection - saving and starting scan
    // ---------------------------------------------------------------

    @Test
    fun `confirm - all selected stores empty set and scans everything`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()
        // All are selected

        whenever(mockScanner.scanImages(isNull())).thenReturn(flowOf(emptyList()))

        vm.confirmBucketSelection()

        // Should store empty set (meaning "all / no filter")
        verify(prefs).selectedBucketIds = emptySet()
        assertFalse("Picker should be dismissed", vm.uiState.value.showFolderPicker)
    }

    @Test
    fun `confirm - subset selected stores that subset`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()

        // Deselect buckets 3 and 4
        vm.toggleBucket(3L)
        vm.toggleBucket(4L)

        whenever(mockScanner.scanImages(setOf(1L, 2L))).thenReturn(flowOf(emptyList()))

        vm.confirmBucketSelection()

        verify(prefs).selectedBucketIds = setOf(1L, 2L)
    }

    @Test
    fun `confirm - nothing selected does not start scan`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()

        // Deselect all
        vm.selectAllBuckets()
        assertEquals("Precondition - nothing selected", emptySet<Long>(), vm.uiState.value.selectedBucketIds)

        vm.confirmBucketSelection()

        // Scanner should NOT be called with null (which would scan everything)
        verify(mockScanner, never()).scanImages(isNull())
        // Scanner should NOT be called at all — no scan when nothing selected
        verify(mockScanner, never()).scanImages(anyOrNull())
        assertFalse("Picker should be dismissed", vm.uiState.value.showFolderPicker)
    }

    @Test
    fun `confirm - triggers scan after saving`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()

        whenever(mockScanner.scanImages(isNull())).thenReturn(flowOf(emptyList()))

        vm.confirmBucketSelection()

        // Scan should have been triggered
        verify(mockScanner, atLeast(1)).scanImages(anyOrNull())
    }

    // ---------------------------------------------------------------
    // dismissFolderPicker - cancelling without saving
    // ---------------------------------------------------------------

    @Test
    fun `dismiss - restores previously saved selection`() = runTest {
        val prefs = createFakePrefs(setOf(1L, 2L))
        val vm = createViewModel(prefs)
        vm.loadBuckets()

        // User makes changes but doesn't confirm
        vm.toggleBucket(3L)
        assertEquals(setOf(1L, 2L, 3L), vm.uiState.value.selectedBucketIds)

        vm.dismissFolderPicker()

        assertEquals("Should restore stored selection", setOf(1L, 2L), vm.uiState.value.selectedBucketIds)
        assertFalse("Picker should be hidden", vm.uiState.value.showFolderPicker)
    }

    @Test
    fun `dismiss - with empty stored (meaning all) restores all checked`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)
        vm.loadBuckets()

        // User deselects some but then dismisses
        vm.toggleBucket(1L)

        vm.dismissFolderPicker()

        assertEquals("Should restore all-selected", allBucketIds, vm.uiState.value.selectedBucketIds)
    }

    // ---------------------------------------------------------------
    // Round-trip scenarios
    // ---------------------------------------------------------------

    @Test
    fun `confirm then reopen - round trip preserves subset selection`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)

        // Open, select only 1 and 2
        vm.loadBuckets()
        vm.selectAllBuckets() // deselect all
        vm.toggleBucket(1L)
        vm.toggleBucket(2L)

        whenever(mockScanner.scanImages(setOf(1L, 2L))).thenReturn(flowOf(emptyList()))
        vm.confirmBucketSelection()

        // Reopen
        vm.loadBuckets()

        assertEquals("Should show previously confirmed selection", setOf(1L, 2L), vm.uiState.value.selectedBucketIds)
    }

    @Test
    fun `confirm all then reopen - should show all checked`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val vm = createViewModel(prefs)

        vm.loadBuckets()
        // All already selected, confirm
        whenever(mockScanner.scanImages(isNull())).thenReturn(flowOf(emptyList()))
        vm.confirmBucketSelection()

        // Reopen
        vm.loadBuckets()

        assertEquals("All should be checked on reopen", allBucketIds, vm.uiState.value.selectedBucketIds)
    }

    // ---------------------------------------------------------------
    // Repository - scan with bucket filter
    // ---------------------------------------------------------------

    @Test
    fun `repository startScan - empty bucket IDs means scan all (null to scanner)`() = runTest {
        val prefs = createFakePrefs(emptySet())
        val repo = ImageRepository(mockDao, mockScanner, mockOcr, prefs, testDispatcher)

        whenever(mockScanner.scanImages(isNull())).thenReturn(flowOf(emptyList()))

        repo.startScan()

        verify(mockScanner).scanImages(isNull())
    }

    @Test
    fun `repository startScan - specific bucket IDs passed to scanner`() = runTest {
        val prefs = createFakePrefs(setOf(1L, 2L))
        val repo = ImageRepository(mockDao, mockScanner, mockOcr, prefs, testDispatcher)

        whenever(mockScanner.scanImages(setOf(1L, 2L))).thenReturn(flowOf(emptyList()))

        repo.startScan()

        verify(mockScanner).scanImages(setOf(1L, 2L))
    }
}
