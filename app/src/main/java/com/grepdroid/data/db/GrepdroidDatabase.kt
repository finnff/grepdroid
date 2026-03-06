package com.grepdroid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScannedImage::class, ScannedImageFts::class],
    version = 1,
    exportSchema = false
)
abstract class GrepdroidDatabase : RoomDatabase() {

    abstract fun imageDao(): ImageDao

    companion object {
        @Volatile
        private var INSTANCE: GrepdroidDatabase? = null

        fun getInstance(context: Context): GrepdroidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GrepdroidDatabase::class.java,
                    "grepdroid_database"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Create FTS sync triggers
                            db.execSQL("""
                                CREATE TRIGGER IF NOT EXISTS scanned_images_ai AFTER INSERT ON scanned_images
                                BEGIN
                                    INSERT OR REPLACE INTO scanned_images_fts (mediaStoreId, extracted_text)
                                    VALUES (NEW.mediaStoreId, NEW.extracted_text);
                                END
                            """)
                            db.execSQL("""
                                CREATE TRIGGER IF NOT EXISTS scanned_images_ad AFTER DELETE ON scanned_images
                                BEGIN
                                    DELETE FROM scanned_images_fts WHERE mediaStoreId = OLD.mediaStoreId;
                                END
                            """)
                            db.execSQL("""
                                CREATE TRIGGER IF NOT EXISTS scanned_images_au AFTER UPDATE ON scanned_images
                                BEGIN
                                    DELETE FROM scanned_images_fts WHERE mediaStoreId = OLD.mediaStoreId;
                                    INSERT INTO scanned_images_fts (mediaStoreId, extracted_text)
                                    VALUES (NEW.mediaStoreId, NEW.extracted_text);
                                END
                            """)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
