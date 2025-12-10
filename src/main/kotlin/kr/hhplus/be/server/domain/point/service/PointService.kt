package kr.hhplus.be.server.domain.point.service

import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.domain.point.repository.PointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PointService(
    private val pointRepository: PointRepository,
) {

    @Transactional(readOnly = true)
    fun getPointByUserId(userId: Long): PointModel {
        return pointRepository.findByUserIdOrThrow(userId)
    }

    @Transactional
    fun chargePoint(userId: Long, amount: Int): PointModel {
        val point = pointRepository.findByUserIdWithLock(userId)
        point.chargePoint(amount)

        return pointRepository.update(point)
    }

    @Transactional
    fun usePoint(userId: Long, amount: Int): PointModel {
        val point = pointRepository.findByUserIdWithLock(userId)
        point.usePoint(amount)

        return pointRepository.update(point)
    }
}
