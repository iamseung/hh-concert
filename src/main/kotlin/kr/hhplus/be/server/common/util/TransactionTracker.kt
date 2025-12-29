package kr.hhplus.be.server.common.util

import org.springframework.transaction.support.TransactionSynchronizationManager

object TransactionTracker {

    /**
     * 현재 트랜잭션 활성화 여부
     */
    fun isTransactionActive(): Boolean {
        return TransactionSynchronizationManager.isActualTransactionActive()
    }

    /**
     * 현재 트랜잭션 이름
     */
    fun getCurrentTransactionName(): String? {
        return TransactionSynchronizationManager.getCurrentTransactionName()
    }

    /**
     * 현재 트랜잭션이 readOnly인지
     */
    fun isReadOnly(): Boolean {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
    }

    /**
     * 현재 바인딩된 리소스들 (Connection, EntityManager 등)
     */
    fun getBoundResourceCount(): Int {
        return TransactionSynchronizationManager.getResourceMap().size
    }

    /**
     * 트랜잭션 정보를 문자열로 반환
     */
    fun getTransactionInfo(): TransactionInfo {
        return TransactionInfo(
            isActive = isTransactionActive(),
            name = getCurrentTransactionName(),
            isReadOnly = isReadOnly(),
            resourceCount = getBoundResourceCount(),
            threadId = Thread.currentThread().id,
        )
    }

    data class TransactionInfo(
        val isActive: Boolean,
        val name: String?,
        val isReadOnly: Boolean,
        val resourceCount: Int,
        val threadId: Long,
    )
}
