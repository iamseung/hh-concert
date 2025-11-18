package kr.hhplus.be.server.application

import kr.hhplus.be.server.api.dto.response.PointResponse
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class PointFacade(
    private val userService: UserService,
    private val pointService: PointService,
    private val pointHistoryService: PointHistoryService,
) {

    @Transactional(readOnly = true)
    fun getPoints(userId: Long): PointResponse {
        userService.findById(userId)
        val point = pointService.getPointByUserId(userId)

        return PointResponse.from(point)
    }

    fun chargePoint(userId: Long, amount: Int): PointResponse {
        val user = userService.findById(userId)
        val point = pointService.chargePoint(userId, amount)

        pointHistoryService.savePointHistory(user, amount, TransactionType.CHARGE)
        return PointResponse.from(point)
    }
}
