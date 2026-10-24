package com.grepdroid

import android.app.Application
import com.grepdroid.data.db.GrepdroidDatabase
import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.OcrProcessor
import com.grepdroid.data.SpeechEngine
import com.grepdroid.data.SpeechModel
import com.grepdroid.data.UserPreferences
import com.grepdroid.data.VideoProcessor
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

    val videoProcessor: VideoProcessor by lazy {
        VideoProcessor(this, ocrProcessor)
    }

    val speechModel: SpeechModel by lazy {
        SpeechModel(this)
    }

    val userPreferences: UserPreferences by lazy {
        UserPreferences(this)
    }

    val repository: ImageRepository by lazy {
        ImageRepository(
            dao = database.imageDao(),
            mediaStoreScanner = mediaStoreScanner,
            ocrProcessor = ocrProcessor,
            userPreferences = userPreferences,
            videoDao = database.videoDao(),
            videoProcessor = videoProcessor,
            speechEngine = SpeechEngine(this, speechModel)
        )
    }
}
