package com.grepdroid

import android.app.Application
import com.grepdroid.data.db.GrepdroidDatabase
import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.OcrProcessor
import com.grepdroid.repository.ImageRepository

class GrepdroidApp : Application() {

    val database: GrepdroidDatabase by lazy {
        GrepdroidDatabase.getInstance(this)
    }

    val mediaStoreScanner: MediaStoreScanner by lazy {
        MediaStoreScanner(this)
    }

    val ocrProcessor: OcrProcessor by lazy {
        OcrProcessor(this)
    }

    val repository: ImageRepository by lazy {
        ImageRepository(
            dao = database.imageDao(),
            mediaStoreScanner = mediaStoreScanner,
            ocrProcessor = ocrProcessor
        )
    }
}
