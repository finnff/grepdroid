package com.grepdroid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
