package kr.hhplus.be.server.infrastructure.persistence.queue.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "queue_token")
class QueueToken(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, unique = true)
    val token: String = UUID.randomUUID().toString(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var queueStatus: QueueStatus = QueueStatus.WAITING,

    @Column(nullable = false)
    var queuePosition: Int = 0,

    @Column
    var activatedAt: LocalDateTime? = null,

    @Column
    var expiresAt: LocalDateTime? = null,
) : BaseEntity() {

    fun toModel(): QueueTokenModel {
        return QueueTokenModel.reconstitute(
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

    fun updateFromDomain(queueTokenModel: QueueTokenModel) {
        this.queueStatus = queueTokenModel.queueStatus
        this.queuePosition = queueTokenModel.queuePosition
        this.activatedAt = queueTokenModel.activatedAt
        this.expiresAt = queueTokenModel.expiresAt
    }

    companion object {
        fun fromDomain(queueTokenModel: QueueTokenModel): QueueToken {
            return QueueToken(
                userId = queueTokenModel.userId,
                token = queueTokenModel.token,
                queueStatus = queueTokenModel.queueStatus,
                queuePosition = queueTokenModel.queuePosition,
                activatedAt = queueTokenModel.activatedAt,
                expiresAt = queueTokenModel.expiresAt,
            )
        }
    }
}
