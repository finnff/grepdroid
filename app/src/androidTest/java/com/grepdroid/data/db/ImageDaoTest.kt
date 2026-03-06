package com.grepdroid.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageDaoTest {

    private lateinit var database: GrepdroidDatabase
    private lateinit var dao: ImageDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            GrepdroidDatabase::class.java
        ).build()
        dao = database.imageDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testUpsertAndGetById() = runTest {
        val image = ScannedImage(
            mediaStoreId = 1L,
            contentUri = "content://media/external/images/media/1",
            displayName = "test.jpg",
            dateModified = 1000L,
            extractedText = "Hello World",
            scannedAt = System.currentTimeMillis()
        )

        dao.upsert(image)

        val result = dao.getById(1L)

        assertNotNull(result)
        assertEquals(1L, result!!.mediaStoreId)
        assertEquals("test.jpg", result.displayName)
        assertEquals("Hello World", result.extractedText)
    }

    @Test
    fun testSearchFts() = runTest {
        val image1 = ScannedImage(
            mediaStoreId = 1L,
            contentUri = "content://media/external/images/media/1",
            displayName = "test1.jpg",
            dateModified = 1000L,
            extractedText = "Hello World this is a test",
            scannedAt = System.currentTimeMillis()
        )

        val image2 = ScannedImage(
            mediaStoreId = 2L,
            contentUri = "content://media/external/images/media/2",
            displayName = "test2.jpg",
            dateModified = 2000L,
            extractedText = "Another image with different text",
            scannedAt = System.currentTimeMillis()
        )

        dao.upsert(image1)
        dao.upsert(image2)

        val results = dao.search("Hello").first()

        assertEquals(1, results.size)
        assertEquals(1L, results[0].mediaStoreId)
    }

    @Test
    fun testSearchFtsPrefix() = runTest {
        val image = ScannedImage(
            mediaStoreId = 1L,
            contentUri = "content://media/external/images/media/1",
            displayName = "test.jpg",
            dateModified = 1000L,
            extractedText = "Hello World",
            scannedAt = System.currentTimeMillis()
        )

        dao.upsert(image)

        // Test prefix matching
        val results = dao.search("Hell").first()

        assertEquals(1, results.size)
        assertEquals("Hello World", results[0].extractedText)
    }

    @Test
    fun testGetAllTracked() = runTest {
        val image1 = ScannedImage(
            mediaStoreId = 1L,
            contentUri = "content://media/external/images/media/1",
            displayName = "test1.jpg",
            dateModified = 1000L,
            extractedText = "Test",
            scannedAt = System.currentTimeMillis()
        )

        val image2 = ScannedImage(
            mediaStoreId = 2L,
            contentUri = "content://media/external/images/media/2",
            displayName = "test2.jpg",
            dateModified = 2000L,
            extractedText = "Test2",
            scannedAt = System.currentTimeMillis()
        )

        dao.upsert(image1)
        dao.upsert(image2)

        val tracked = dao.getAllTracked().first()

        assertEquals(2, tracked.size)
        assertTrue(tracked.any { it.mediaStoreId == 1L && it.dateModified == 1000L })
        assertTrue(tracked.any { it.mediaStoreId == 2L && it.dateModified == 2000L })
    }

    @Test
    fun testDeleteRemoved() = runTest {
        val image1 = ScannedImage(
            mediaStoreId = 1L,
            contentUri = "content://media/external/images/media/1",
            displayName = "test1.jpg",
            dateModified = 1000L,
            extractedText = "Test",
            scannedAt = System.currentTimeMillis()
        )

        val image2 = ScannedImage(
            mediaStoreId = 2L,
            contentUri = "content://media/external/images/media/2",
            displayName = "test2.jpg",
            dateModified = 2000L,
            extractedText = "Test2",
            scannedAt = System.currentTimeMillis()
        )

        dao.upsert(image1)
        dao.upsert(image2)

        dao.deleteRemoved(listOf(1L))

        val all = dao.getAllTracked().first()
        assertEquals(1, all.size)
        assertEquals(1L, all[0].mediaStoreId)
    }

    @Test
    fun testUpdateTriggersFts() = runTest {
        val image = ScannedImage(
            mediaStoreId = 1L,
            contentUri = "content://media/external/images/media/1",
            displayName = "test.jpg",
            dateModified = 1000L,
            extractedText = "Original text",
            scannedAt = System.currentTimeMillis()
        )

        dao.upsert(image)

        // Search for original text
        var results = dao.search("Original").first()
        assertEquals(1, results.size)

        // Update the text
        val updatedImage = image.copy(extractedText = "Updated text")
        dao.upsert(updatedImage)

        // Original search should return nothing
        results = dao.search("Original").first()
        assertEquals(0, results.size)

        // New text should be found
        results = dao.search("Updated").first()
        assertEquals(1, results.size)
    }
}