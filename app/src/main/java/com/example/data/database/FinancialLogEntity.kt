package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "financial_logs")
data class FinancialLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val title: String,
    val amount: Long,
    val category: String, // "FOOD", "TRANSPORT", "CAFE", "SHOPPING", "OTHER"
    val timestamp: Long = System.currentTimeMillis(),
    val isSolved: Boolean = false // 참이면 '막기' 성공한 상태
)
