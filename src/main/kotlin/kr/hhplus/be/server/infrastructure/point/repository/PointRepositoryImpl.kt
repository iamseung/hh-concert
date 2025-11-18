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
        val entity = if (point.id != 0L) {
            pointJpaRepository.findByIdOrNull(point.id)?.apply {
                updateFromDomain(point)
            } ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
        } else {
            val user = userJpaRepository.findByIdOrNull(point.userId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            PointEntity.fromDomain(point, user)
        }
        val saved = pointJpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: Long): Point? {
        return pointJpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByIdOrThrow(id: Long): Point {
        return findById(id) ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
    }

    override fun findByUserId(userId: Long): Point? {
        return pointJpaRepository.findByUserId(userId)?.toDomain()
    }

    override fun findByUserIdOrThrow(userId: Long): Point {
        return findByUserId(userId) ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
    }

    override fun findByUserIdWithLock(userId: Long): Point? {
        return pointJpaRepository.findByUserIdWithLock(userId)?.toDomain()
    }
}
