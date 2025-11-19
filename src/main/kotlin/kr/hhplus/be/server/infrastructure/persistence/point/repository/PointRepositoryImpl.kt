package kr.hhplus.be.server.infrastructure.persistence.point.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.domain.point.repository.PointRepository
import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PointRepositoryImpl(
    private val pointJpaRepository: PointJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : PointRepository {

    override fun save(pointModel: PointModel): PointModel {
        val entity = if (pointModel.id != 0L) {
            pointJpaRepository.findByIdOrNull(pointModel.id)?.apply {
                updateFromDomain(pointModel)
            } ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
        } else {
            val user = userJpaRepository.findByIdOrNull(pointModel.userId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            Point.fromDomain(pointModel, user)
        }
        val saved = pointJpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findById(id: Long): PointModel? {
        return pointJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): PointModel {
        return findById(id) ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
    }

    override fun findByUserId(userId: Long): PointModel? {
        return pointJpaRepository.findByUserId(userId)?.toModel()
    }

    override fun findByUserIdOrThrow(userId: Long): PointModel {
        return findByUserId(userId) ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
    }

    override fun findByUserIdWithLock(userId: Long): PointModel? {
        return pointJpaRepository.findByUserIdWithLock(userId)?.toModel()
    }
}
