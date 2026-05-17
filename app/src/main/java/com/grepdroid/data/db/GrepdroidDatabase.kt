package com.grepdroid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ScannedImage::class,
        ScannedImageFts::class,
        ScannedVideo::class,
        ScannedVideoFts::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GrepdroidDatabase : RoomDatabase() {

    abstract fun imageDao(): ImageDao
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile
        private var INSTANCE: GrepdroidDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scanned_videos` (
                        `mediaStoreId` INTEGER NOT NULL,
                        `contentUri` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `dateModified` INTEGER NOT NULL,
                        `extractedText` TEXT NOT NULL,
                        `frameTimestampsJson` TEXT NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        `scannedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`mediaStoreId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `scanned_videos_fts`
                    USING FTS4(`extractedText` TEXT NOT NULL, content=`scanned_videos`)
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): GrepdroidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GrepdroidDatabase::class.java,
                    "grepdroid_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
