package kr.hhplus.be.server.infrastructure.queue.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.user.entity.UserEntity
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "queue_token")
class QueueTokenEntity(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val userEntity: UserEntity,

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

    fun toDomain(): kr.hhplus.be.server.domain.queue.model.QueueToken {
        return kr.hhplus.be.server.domain.queue.model.QueueToken.reconstitute(
            id = id,
            userId = userEntity.id,
            token = token,
            queueStatus = queueStatus,
            queuePosition = queuePosition,
            activatedAt = activatedAt,
            expiresAt = expiresAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(queueToken: kr.hhplus.be.server.domain.queue.model.QueueToken) {
        this.queueStatus = queueToken.queueStatus
        this.queuePosition = queueToken.queuePosition
        this.activatedAt = queueToken.activatedAt
        this.expiresAt = queueToken.expiresAt
    }

    companion object {
        fun of(userEntity: UserEntity, position: Int): QueueTokenEntity {
            return QueueTokenEntity(
                userEntity = userEntity,
                queuePosition = position,
            )
        }

        fun fromDomain(
            queueToken: kr.hhplus.be.server.domain.queue.model.QueueToken,
            userEntity: UserEntity,
        ): QueueTokenEntity {
            return QueueTokenEntity(
                userEntity = userEntity,
                token = queueToken.token,
                queueStatus = queueToken.queueStatus,
                queuePosition = queueToken.queuePosition,
                activatedAt = queueToken.activatedAt,
                expiresAt = queueToken.expiresAt,
            )
        }
    }
}
