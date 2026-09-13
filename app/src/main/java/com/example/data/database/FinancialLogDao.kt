package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialLogDao {
    @Query("SELECT * FROM financial_logs WHERE username = :username ORDER BY timestamp DESC")
    fun observeLogsForUser(username: String): Flow<List<FinancialLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FinancialLogEntity): Long

    @Update
    suspend fun updateLog(log: FinancialLogEntity)

    @Query("DELETE FROM financial_logs WHERE id = :logId")
    suspend fun deleteLogById(logId: Long)

    @Query("SELECT * FROM financial_logs WHERE id = :logId LIMIT 1")
    suspend fun getLogById(logId: Long): FinancialLogEntity?

    @Query("SELECT SUM(amount) FROM financial_logs WHERE username = :username AND isSolved = 1")
    fun observeTotalSolvedSaving(username: String): Flow<Long?>
}
