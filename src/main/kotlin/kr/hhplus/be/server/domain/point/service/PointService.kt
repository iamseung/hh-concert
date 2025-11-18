package kr.hhplus.be.server.domain.point.service

import kr.hhplus.be.server.domain.point.model.Point
import kr.hhplus.be.server.domain.point.repository.PointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional
@Service
class PointService(
    private val pointRepository: PointRepository,
) {

    @Transactional(readOnly = true)
    fun getPointByUserId(userId: Long): Point {
        return pointRepository.findByUserIdOrThrow(userId)
    }

    fun chargePoint(userId: Long, amount: Int): Point {
        val point = getPointByUserId(userId)
        point.chargePoint(amount)
        return pointRepository.save(point)
    }

    fun usePoint(userId: Long, amount: Int): Point {
        val point = getPointByUserId(userId)
        point.usePoint(amount)
        return pointRepository.save(point)
    }
}
