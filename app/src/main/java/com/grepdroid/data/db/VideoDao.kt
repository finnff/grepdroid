package com.grepdroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.sqlite.db.SupportSQLiteQuery

data class TrackedVideo(
    val mediaStoreId: Long,
    val dateModified: Long
)

data class UntranscribedVideo(
    val mediaStoreId: Long,
    val contentUri: String
)

@Dao
interface VideoDao {

    @Query("SELECT scanned_videos.* FROM scanned_videos JOIN scanned_videos_fts ON scanned_videos.rowid = scanned_videos_fts.rowid WHERE scanned_videos_fts MATCH :query || '*' LIMIT 50")
    fun search(query: String): Flow<List<ScannedVideo>>

    @Query("SELECT mediaStoreId, dateModified FROM scanned_videos")
    fun getAllTracked(): Flow<List<TrackedVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: ScannedVideo)

    @Query("DELETE FROM scanned_videos WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM scanned_videos")
    suspend fun getAll(): List<ScannedVideo>

    @Query("UPDATE scanned_videos SET thumbnailPath = :path WHERE mediaStoreId = :mediaStoreId")
    suspend fun updateThumbnailPath(mediaStoreId: Long, path: String)

    @Query("SELECT * FROM scanned_videos WHERE mediaStoreId = :mediaStoreId")
    suspend fun getById(mediaStoreId: Long): ScannedVideo?

    @Query("SELECT mediaStoreId, contentUri FROM scanned_videos WHERE transcribedAt = 0 ORDER BY dateModified DESC")
    suspend fun getUntranscribed(): List<UntranscribedVideo>

    @Query("UPDATE scanned_videos SET transcript = :transcript, transcriptSegmentsJson = :segmentsJson, transcribedAt = :transcribedAt WHERE mediaStoreId = :mediaStoreId")
    suspend fun updateTranscript(mediaStoreId: Long, transcript: String, segmentsJson: String, transcribedAt: Long)

    @RawQuery
    suspend fun searchFuzzyCandidates(query: SupportSQLiteQuery): List<ScannedVideo>

    @Query("SELECT COUNT(*) FROM scanned_videos")
    fun getTotalCount(): Flow<Int>
}
