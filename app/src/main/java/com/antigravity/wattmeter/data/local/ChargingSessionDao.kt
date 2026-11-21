package com.antigravity.wattmeter.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antigravity.wattmeter.data.model.ChargingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChargingSession): Long

    @Update
    suspend fun update(session: ChargingSession)

    @Delete
    suspend fun delete(session: ChargingSession)

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ChargingSession>>

    @Query("SELECT * FROM charging_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ChargingSession?

    @Query("DELETE FROM charging_sessions")
    suspend fun clearAll()
}
