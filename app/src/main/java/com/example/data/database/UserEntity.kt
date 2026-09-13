package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val passwordHash: String, // simple hashed/stored string for demo
    val nickname: String,
    val lifestyleId: String = "",
    val assetIndex: Int = 2,
    val characterId: String = "",
    val companionCustomName: String = "",
    val companionCustomPersona: String = "",
    val savingTarget: Long = 500_000_000L, // 5억원 기본값
    val savingCurrent: Long = 120_000_000L, // 1억2천만원 기본값
    val securityFund: Long = 20_000_000L, // 비상금 기본값
    val isaContribution: Long = 10_000_000L, // ISA 기여 기본값
    val hasCompletedJourney: Boolean = false
)
