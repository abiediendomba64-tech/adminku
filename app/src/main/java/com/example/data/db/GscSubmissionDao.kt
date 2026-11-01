package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.GscSubmissionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface GscSubmissionDao {
    @Query("SELECT * FROM gsc_submission_logs ORDER BY submittedAt DESC")
    fun getAllLogs(): Flow<List<GscSubmissionLog>>

    @Query("SELECT * FROM gsc_submission_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): GscSubmissionLog?

    @Query("SELECT * FROM gsc_submission_logs WHERE domain = :domain ORDER BY submittedAt DESC")
    fun getLogsByDomain(domain: String): Flow<List<GscSubmissionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: GscSubmissionLog): Long

    @Update
    suspend fun updateLog(log: GscSubmissionLog)

    @Delete
    suspend fun deleteLog(log: GscSubmissionLog)

    @Query("DELETE FROM gsc_submission_logs")
    suspend fun clearAllLogs()
}
