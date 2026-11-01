package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DomainRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {
    @Query("SELECT * FROM domain_requests ORDER BY createdAt DESC")
    fun getAllDomains(): Flow<List<DomainRequest>>

    @Query("SELECT * FROM domain_requests WHERE status = :status ORDER BY createdAt DESC")
    fun getDomainsByStatus(status: String): Flow<List<DomainRequest>>

    @Query("SELECT * FROM domain_requests WHERE domainName = :name LIMIT 1")
    suspend fun getDomainByName(name: String): DomainRequest?

    @Query("SELECT * FROM domain_requests WHERE status = 'INDEXED_GSC' OR gscIndexingStatus = 'INDEXED' ORDER BY createdAt DESC")
    fun getIndexedDomains(): Flow<List<DomainRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomain(domain: DomainRequest): Long

    @Update
    suspend fun updateDomain(domain: DomainRequest)

    @Delete
    suspend fun deleteDomain(domain: DomainRequest)

    @Query("SELECT COUNT(*) FROM domain_requests")
    suspend fun getCount(): Int
}
