package kr.hhplus.be.server.application.usecase.point

import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetPointUseCase(
    private val userService: UserService,
    private val pointService: PointService,
) {

    @Transactional(readOnly = true)
    fun execute(command: GetPointCommand): GetPointResult {
        // 1. 사용자 검증
        val user = userService.findById(command.userId)

        // 2. 포인트 조회
        val point = pointService.getPointByUserId(user.id)

        // 3. 결과 반환
        return GetPointResult(
            userId = user.id,
            balance = point.balance
        )
    }
}
