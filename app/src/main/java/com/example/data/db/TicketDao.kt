package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MemberTicket
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {
    @Query("SELECT * FROM member_tickets ORDER BY createdAt DESC")
    fun getAllTickets(): Flow<List<MemberTicket>>

    @Query("SELECT * FROM member_tickets WHERE status = :status ORDER BY createdAt DESC")
    fun getTicketsByStatus(status: String): Flow<List<MemberTicket>>

    @Query("SELECT * FROM member_tickets WHERE ticketCode = :code LIMIT 1")
    suspend fun getTicketByCode(code: String): MemberTicket?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: MemberTicket): Long

    @Update
    suspend fun updateTicket(ticket: MemberTicket)

    @Delete
    suspend fun deleteTicket(ticket: MemberTicket)

    @Query("SELECT COUNT(*) FROM member_tickets")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM member_tickets WHERE status = 'OPEN'")
    suspend fun getOpenCount(): Int
}
