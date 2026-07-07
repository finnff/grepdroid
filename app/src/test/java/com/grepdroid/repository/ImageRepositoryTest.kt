package com.grepdroid.repository

import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.OcrProcessor
import com.grepdroid.data.ProcessedVideo
import com.grepdroid.data.UserPreferences
import com.grepdroid.data.VideoProcessor
import com.grepdroid.data.db.ImageDao
import com.grepdroid.data.db.ScannedImage
import com.grepdroid.data.db.TrackedImage
import com.grepdroid.data.db.TrackedVideo
import com.grepdroid.data.db.VideoDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ImageRepositoryTest {

    private val mockDao: ImageDao = mock()
    private val mockMediaStoreScanner: MediaStoreScanner = mock()
    private val mockOcrProcessor: OcrProcessor = mock()
    private val mockVideoDao: VideoDao = mock()
    private val mockVideoProcessor: VideoProcessor = mock()

    @Test
    fun `detects new images - should scan when no tracked images exist`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val allImages = listOf(
                MediaStoreScanner.ImageItem(1L, "new1.jpg", 1000L, "content://1"),
                MediaStoreScanner.ImageItem(2L, "new2.jpg", 2000L, "content://2")
            )

            whenever(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
            whenever(mockDao.getAllTracked()).thenReturn(flowOf(emptyList()))
            whenever(mockOcrProcessor.extractTextFromUri(any())).thenReturn("extracted text")

            val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, dispatcher = testDispatcher)
            repo.startScan()
            advanceUntilIdle()

            verify(mockOcrProcessor, atLeast(1)).extractTextFromUri(any())
            verify(mockDao, atLeast(1)).upsert(any())
        }
    }

    @Test
    fun `detects modified images - should scan when date changes`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val allImages = listOf(
                MediaStoreScanner.ImageItem(1L, "modified.jpg", 9999L, "content://1")
            )
            val trackedImages = listOf(TrackedImage(1L, 1000L))

            whenever(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
            whenever(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))
            whenever(mockOcrProcessor.extractTextFromUri(any())).thenReturn("extracted text")

            val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, dispatcher = testDispatcher)
            repo.startScan()
            advanceUntilIdle()

            verify(mockOcrProcessor, atLeast(1)).extractTextFromUri(any())
            verify(mockDao, atLeast(1)).upsert(any())
        }
    }

    @Test
    fun `detects deleted images - should remove when image not in gallery`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val allImages = listOf(
                MediaStoreScanner.ImageItem(2L, "kept.jpg", 2000L, "content://2")
            )
            val trackedImages = listOf(
                TrackedImage(1L, 1000L),
                TrackedImage(2L, 2000L)
            )

            whenever(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
            whenever(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))

            val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, dispatcher = testDispatcher)
            repo.startScan()
            advanceUntilIdle()

            verify(mockDao).deleteByIds(listOf(1L))
        }
    }

    @Test
    fun `does not rescan unchanged images - same date means no rescan`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val allImages = listOf(
                MediaStoreScanner.ImageItem(1L, "unchanged.jpg", 1000L, "content://1")
            )
            val trackedImages = listOf(TrackedImage(1L, 1000L))

            whenever(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
            whenever(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))

            val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, dispatcher = testDispatcher)
            repo.startScan()
            advanceUntilIdle()

            verify(mockOcrProcessor, never()).extractTextFromUri(any())
            verify(mockDao, never()).upsert(any())
        }
    }

    @Test
    fun `upserts ScannedImage with correct fields`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val allImages = listOf(
                MediaStoreScanner.ImageItem(1L, "test.jpg", 1000L, "content://media/external/images/media/1")
            )

            whenever(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
            whenever(mockDao.getAllTracked()).thenReturn(flowOf(emptyList()))
            whenever(mockOcrProcessor.extractTextFromUri(any())).thenReturn("Hello World")

            val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, dispatcher = testDispatcher)
            repo.startScan()
            advanceUntilIdle()

            verify(mockDao).upsert(any<ScannedImage>())
        }
    }

    @Test
    fun `search sanitizes query and delegates to dao`() {
        val results = flowOf(listOf(ScannedImage(1L, "", "", 0, "", 0)))
        whenever(mockDao.search("test*")).thenReturn(results)

        val repository = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor)
        repository.search("test")

        verify(mockDao).search("test*")
    }

    @Test
    fun `search with FTS-hostile query still delegates sanitized value to dao`() {
        val results = flowOf(listOf(ScannedImage(1L, "", "", 0, "", 0)))
        whenever(mockDao.search("ab c*")).thenReturn(results)

        val repository = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor)
        val flow = repository.search("a*b (c")

        assertNotNull(flow)
        verify(mockDao).search("ab c*")
    }

    @Test
    fun `search with blank query returns empty flow without touching dao`() {
        val repository = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor)
        val flow = repository.search("")

        assertNotNull(flow)
        verify(mockDao, never()).search(any())
    }

    @Test
    fun `searchVideos with blank query returns empty flow without touching videoDao`() {
        val repository = ImageRepository(
            mockDao, mockMediaStoreScanner, mockOcrProcessor, videoDao = mockVideoDao
        )
        val flow = repository.searchVideos("")

        assertNotNull(flow)
        verify(mockVideoDao, never()).search(any())
    }

    @Test
    fun `removes video row and thumbnail when tracked video disappears from gallery`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val currentVideos = listOf(
                MediaStoreScanner.VideoItem(2L, "kept.mp4", 2000L, "content://2", 1000L)
            )
            val trackedVideos = listOf(
                TrackedVideo(1L, 1000L),
                TrackedVideo(2L, 2000L)
            )
            val mockPrefs = mock<UserPreferences>()
            whenever(mockPrefs.indexVideos).thenReturn(true)

            whenever(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(emptyList()))
            whenever(mockDao.getAllTracked()).thenReturn(flowOf(emptyList()))
            whenever(mockMediaStoreScanner.scanVideos()).thenReturn(flowOf(currentVideos))
            whenever(mockVideoDao.getAllTracked()).thenReturn(flowOf(trackedVideos))
            whenever(mockVideoProcessor.process(any(), any())).thenReturn(
                ProcessedVideo(durationMs = 1000L, frames = emptyList(), thumbnailPath = "x")
            )

            val repo = ImageRepository(
                mockDao,
                mockMediaStoreScanner,
                mockOcrProcessor,
                mockPrefs,
                dispatcher = testDispatcher,
                videoDao = mockVideoDao,
                videoProcessor = mockVideoProcessor
            )
            repo.startScan()
            advanceUntilIdle()

            verify(mockVideoDao).deleteByIds(listOf(1L))
            verify(mockVideoProcessor).deleteThumbnail(1L)
        }
    }
}
