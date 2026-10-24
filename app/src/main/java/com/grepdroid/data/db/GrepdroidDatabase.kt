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
    version = 3,
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

        /**
         * Adds the speech transcript columns and re-creates the video FTS table so it
         * indexes the transcript too. The old content-sync triggers only copy
         * extractedText, so they're dropped; Room re-creates them on open.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scanned_videos` ADD COLUMN `transcript` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `scanned_videos` ADD COLUMN `transcriptSegmentsJson` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `scanned_videos` ADD COLUMN `transcribedAt` INTEGER NOT NULL DEFAULT 0")
                for (suffix in listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")) {
                    db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_scanned_videos_fts_$suffix")
                }
                db.execSQL("DROP TABLE IF EXISTS `scanned_videos_fts`")
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `scanned_videos_fts`
                    USING FTS4(`extractedText` TEXT NOT NULL, `transcript` TEXT NOT NULL, content=`scanned_videos`)
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO `scanned_videos_fts`(`scanned_videos_fts`) VALUES('rebuild')")
            }
        }

        fun getInstance(context: Context): GrepdroidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GrepdroidDatabase::class.java,
                    "grepdroid_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
