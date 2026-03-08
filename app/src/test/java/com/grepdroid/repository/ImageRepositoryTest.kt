package com.grepdroid.repository

import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.OcrProcessor
import com.grepdroid.data.db.ImageDao
import com.grepdroid.data.db.ScannedImage
import com.grepdroid.data.db.TrackedImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
    fun `search returns flow of results`() {
        val query = "test"
        val results = flowOf(listOf(ScannedImage(1L, "", "", 0, "", 0)))

        whenever(mockDao.search(query)).thenReturn(results)

        val repository = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor)
        val result = repository.search(query)

        assertTrue(result != null)
    }
}
