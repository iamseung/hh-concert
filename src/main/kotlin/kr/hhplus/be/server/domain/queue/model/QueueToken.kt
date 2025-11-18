package kr.hhplus.be.server.domain.queue.model

import kr.hhplus.be.server.common.exception.AuthorizationException
import kr.hhplus.be.server.common.exception.ErrorCode
import java.time.LocalDateTime
import java.util.*

class QueueToken private constructor(
    var id: Long,
    val userId: Long,
    val token: String,
    var queueStatus: QueueStatus,
    var queuePosition: Int,
    var activatedAt: LocalDateTime?,
    var expiresAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    val isActive: Boolean
        get() = queueStatus == QueueStatus.ACTIVE

    val isWaiting: Boolean
        get() = queueStatus == QueueStatus.WAITING

    fun activate() {
        this.queueStatus = QueueStatus.ACTIVE
        this.queuePosition = 0
        this.activatedAt = LocalDateTime.now()
        this.expiresAt = LocalDateTime.now().plusHours(1)
        this.updatedAt = LocalDateTime.now()
    }

    fun expire() {
        this.queueStatus = QueueStatus.EXPIRED
        this.updatedAt = LocalDateTime.now()
    }

    fun validateActive() {
        if (queueStatus != QueueStatus.ACTIVE) {
            throw AuthorizationException(ErrorCode.QUEUE_TOKEN_NOT_ACTIVE)
        }

        expiresAt?.let {
            if (LocalDateTime.now().isAfter(it)) {
                expire()
                throw AuthorizationException(ErrorCode.TOKEN_EXPIRED)
            }
        }
    }

    fun updatePosition(newPosition: Int) {
        if (queueStatus == QueueStatus.WAITING) {
            this.queuePosition = newPosition
            this.updatedAt = LocalDateTime.now()
        }
    }

    companion object {
        fun create(userId: Long, position: Int): QueueToken {
            val now = LocalDateTime.now()
            return QueueToken(
                id = 0L,
                userId = userId,
                token = UUID.randomUUID().toString(),
                queueStatus = QueueStatus.WAITING,
                queuePosition = position,
                activatedAt = null,
                expiresAt = null,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            userId: Long,
            token: String,
            queueStatus: QueueStatus,
            queuePosition: Int,
            activatedAt: LocalDateTime?,
            expiresAt: LocalDateTime?,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): QueueToken {
            return QueueToken(
                id = id,
                userId = userId,
                token = token,
                queueStatus = queueStatus,
                queuePosition = queuePosition,
                activatedAt = activatedAt,
                expiresAt = expiresAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
