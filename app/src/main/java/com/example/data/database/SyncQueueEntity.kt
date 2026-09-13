package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 오프라인 상태에서 발생한 데이터 변경 사항을 보존하고,
 * 네트워크 연결 복구 시 FIFO 순서로 클라우드에 전송하기 위한 영속화 큐 엔티티
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val entityType: String, // "USER", "FINANCIAL_LOG", "CALCULATOR"
    val entityId: String,   // 사용자명, 로그 ID 등 대상 고유 식별자
    val action: String,     // "UPSERT", "DELETE"
    val payloadJson: String, // 변경 시점의 직렬화된 데이터 페이로드
    val status: String = STATUS_PENDING, // "PENDING", "PROCESSING", "FAILED", "COMPLETED"
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val lastAttemptAt: Long = 0L,
    val lastErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_COMPLETED = "COMPLETED"

        const val TYPE_USER = "USER"
        const val TYPE_FINANCIAL_LOG = "FINANCIAL_LOG"
        const val TYPE_CALCULATOR = "CALCULATOR"

        const val ACTION_UPSERT = "UPSERT"
        const val ACTION_DELETE = "DELETE"
    }
}
