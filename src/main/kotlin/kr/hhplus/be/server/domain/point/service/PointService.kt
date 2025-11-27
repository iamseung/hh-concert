package kr.hhplus.be.server.domain.point.service

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.domain.point.repository.PointRepository
import org.springframework.stereotype.Service

@Service
class PointService(
    private val pointRepository: PointRepository,
) {

    fun getPointByUserId(userId: Long): PointModel {
        return pointRepository.findByUserIdOrThrow(userId)
    }

    fun chargePoint(userId: Long, amount: Int): PointModel {
        val point = pointRepository.findByUserIdWithLock(userId)
            ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
        point.chargePoint(amount)
        return pointRepository.save(point)
    }

    fun usePoint(userId: Long, amount: Int): PointModel {
        val point = pointRepository.findByUserIdWithLock(userId)
            ?: throw BusinessException(ErrorCode.POINT_NOT_FOUND)
        point.usePoint(amount)
        return pointRepository.save(point)
    }
}
