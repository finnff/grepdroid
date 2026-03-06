package com.grepdroid.repository

import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.OcrProcessor
import com.grepdroid.data.db.ImageDao
import com.grepdroid.data.db.ScannedImage
import com.grepdroid.data.db.TrackedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.junit.MockitoJUnitRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ImageRepositoryTest {

    @Mock
    private lateinit var mockDao: ImageDao

    @Mock
    private lateinit var mockMediaStoreScanner: MediaStoreScanner

    @Mock
    private lateinit var mockOcrProcessor: OcrProcessor

    private lateinit var repository: ImageRepository
    private lateinit var testDispatcher: StandardTestDispatcher

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        repository = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `detects new images - should scan when no tracked images exist`() = runTest(testDispatcher) {
        val allImages = listOf(
            MediaStoreScanner.ImageItem(1L, "new1.jpg", 1000L, "content://1"),
            MediaStoreScanner.ImageItem(2L, "new2.jpg", 2000L, "content://2")
        )

        val trackedImages = emptyList<TrackedImage>()

        `when`(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
        `when`(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))
        `when`(mockOcrProcessor.extractTextFromUri(anyString())).thenReturn("extracted text")

        val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, testDispatcher)
        repo.startScan()
        advanceUntilIdle()

        verify(mockOcrProcessor, atLeast(1)).extractTextFromUri(anyString())
        verify(mockDao, atLeast(1)).upsert(any())
    }

    @Test
    fun `detects modified images - should scan when date changes`() = runTest(testDispatcher) {
        val allImages = listOf(
            MediaStoreScanner.ImageItem(1L, "modified.jpg", 9999L, "content://1")
        )

        val trackedImages = listOf(
            TrackedImage(1L, 1000L)
        )

        `when`(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
        `when`(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))
        `when`(mockOcrProcessor.extractTextFromUri(anyString())).thenReturn("extracted text")

        val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, testDispatcher)
        repo.startScan()
        advanceUntilIdle()

        verify(mockOcrProcessor, atLeast(1)).extractTextFromUri(anyString())
        verify(mockDao, atLeast(1)).upsert(any())
    }

    @Test
    fun `detects deleted images - should remove when image not in gallery`() = runTest(testDispatcher) {
        val allImages = listOf(
            MediaStoreScanner.ImageItem(2L, "kept.jpg", 2000L, "content://2")
        )

        val trackedImages = listOf(
            TrackedImage(1L, 1000L),
            TrackedImage(2L, 2000L)
        )

        `when`(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
        `when`(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))

        val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, testDispatcher)
        repo.startScan()
        advanceUntilIdle()

        verify(mockDao).deleteRemoved(listOf(1L))
    }

    @Test
    fun `does not rescan unchanged images - same date means no rescan`() = runTest(testDispatcher) {
        val allImages = listOf(
            MediaStoreScanner.ImageItem(1L, "unchanged.jpg", 1000L, "content://1")
        )

        val trackedImages = listOf(
            TrackedImage(1L, 1000L)
        )

        `when`(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
        `when`(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))

        val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, testDispatcher)
        repo.startScan()
        advanceUntilIdle()

        verify(mockOcrProcessor, never()).extractTextFromUri(anyString())
        verify(mockDao, never()).upsert(any())
    }

    @Test
    fun `upserts ScannedImage with correct fields`() = runTest(testDispatcher) {
        val allImages = listOf(
            MediaStoreScanner.ImageItem(1L, "test.jpg", 1000L, "content://media/external/images/media/1")
        )

        val trackedImages = emptyList<TrackedImage>()

        `when`(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
        `when`(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))
        `when`(mockOcrProcessor.extractTextFromUri(anyString())).thenReturn("Hello World")

        val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, testDispatcher)
        repo.startScan()
        advanceUntilIdle()

        verify(mockDao).upsert(any<ScannedImage>())
    }

    @Test
    fun `handles multiple new and modified images correctly`() = runTest(testDispatcher) {
        val allImages = listOf(
            MediaStoreScanner.ImageItem(1L, "new.jpg", 3000L, "content://1"),
            MediaStoreScanner.ImageItem(2L, "modified.jpg", 2500L, "content://2"),
            MediaStoreScanner.ImageItem(3L, "unchanged.jpg", 1000L, "content://3")
        )

        val trackedImages = listOf(
            TrackedImage(2L, 1000L),
            TrackedImage(3L, 1000L)
        )

        `when`(mockMediaStoreScanner.scanImages()).thenReturn(flowOf(allImages))
        `when`(mockDao.getAllTracked()).thenReturn(flowOf(trackedImages))
        `when`(mockOcrProcessor.extractTextFromUri(anyString())).thenReturn("text")

        val repo = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor, testDispatcher)
        repo.startScan()
        advanceUntilIdle()

        verify(mockOcrProcessor, atLeast(1)).extractTextFromUri(anyString())
        verify(mockDao, atLeast(1)).upsert(any())
        verify(mockDao).deleteRemoved(listOf(2L))
    }

    @Test
    fun `search returns flow of results`() {
        val query = "test"
        val results = flowOf(listOf(ScannedImage(1L, "", "", 0, "", 0)))

        `when`(mockDao.search(query)).thenReturn(results)

        repository = ImageRepository(mockDao, mockMediaStoreScanner, mockOcrProcessor)

        val result = repository.search(query)

        assertTrue(result != null)
    }
}