package kr.hhplus.be.server.application.usecase.point

import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ChargePointUseCase(
    private val userService: UserService,
    private val pointService: PointService,
    private val pointHistoryService: PointHistoryService,
) {

    @Transactional
    @Retryable(
        retryFor = [
            PessimisticLockingFailureException::class,
            CannotAcquireLockException::class,
        ],
        maxAttempts = 2,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun execute(command: ChargePointCommand): ChargePointResult {
        // 1. 사용자 검증
        val user = userService.findById(command.userId)

        // 2. 포인트 충전 (Service 내부에서 @Transactional)
        val point = pointService.chargePoint(user.id, command.amount)

        // 3. 히스토리 기록
        pointHistoryService.savePointHistory(user.id, command.amount, TransactionType.CHARGE)

        // 4. 결과 반환
        return ChargePointResult(
            userId = user.id,
            balance = point.balance,
        )
    }
}
