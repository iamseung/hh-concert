package kr.hhplus.be.server.infrastructure.point.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.point.model.Point
import kr.hhplus.be.server.domain.point.repository.PointRepository
import kr.hhplus.be.server.infrastructure.point.entity.PointEntity
import kr.hhplus.be.server.infrastructure.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PointRepositoryImpl(
    private val pointJpaRepository: PointJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : PointRepository {

    override fun save(point: Point): Point {
        val entity = toEntity(point)
        val saved = pointJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Point? {
        return pointJpaRepository.findByIdOrNull(id)?.let { toDomain(it) }
    }

    override fun findByIdOrThrow(id: Long): Point {
        return findById(id) ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
    }

    override fun findByUserId(userId: Long): Point? {
        return pointJpaRepository.findByUserId(userId)?.let { toDomain(it) }
    }

    override fun findByUserIdOrThrow(userId: Long): Point {
        return findByUserId(userId) ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
    }

    override fun findByUserIdWithLock(userId: Long): Point? {
        return pointJpaRepository.findByUserIdWithLock(userId)?.let { toDomain(it) }
    }

    private fun toDomain(point: PointEntity): Point {
        return Point.reconstitute(
            id = point.id,
            userId = point.userEntity.id,
            balance = point.balance,
            createdAt = point.createdAt,
            updatedAt = point.updatedAt,
        )
    }

    private fun toEntity(point: Point): PointEntity {
        val user = userJpaRepository.findByIdOrNull(point.userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val entity = PointEntity(
            userEntity = user,
            balance = point.balance,
        )

        return entity
    }
}