package kr.hhplus.be.server.application.usecase.point

import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.infrastructure.lock.DistributeLockExecutor
import kr.hhplus.be.server.infrastructure.template.TransactionExecutor
import org.springframework.stereotype.Component

@Component
class ChargePointUseCase(
    private val userService: UserService,
    private val pointService: PointService,
    private val pointHistoryService: PointHistoryService,
    private val distributeLockExecutor: DistributeLockExecutor,
    private val transactionExecutor: TransactionExecutor,
) {

    /**
     * 포인트 충전
     *
     * 동시성 제어 전략:
     * 1. 분산락으로 여러 서버 간 동시 요청 제어
     * 2. 트랜잭션으로 데이터 일관성 보장
     *
     * 실행 순서:
     * [분산락 획득] → [트랜잭션 시작] → [비즈니스 로직] → [트랜잭션 커밋] → [분산락 해제]
     *
     * 락 설정:
     * - Key: "point:lock:{userId}" - 사용자별 격리
     * - Wait: 3초 - 락 대기 시간
     * - Lease: 5초 - 자동 해제 시간 (데드락 방지)
     *
     * 트랜잭션:
     * - TransactionExecutor 사용 (프로그래매틱 트랜잭션)
     * - Self-invocation 문제 회피
     * - 분산락 범위 > 트랜잭션 범위
     */
    fun execute(command: ChargePointCommand): ChargePointResult {
        // 1. 사용자 검증 (락 밖에서 실행 - 빠른 실패)
        val user = userService.findById(command.userId)

        // 2. 분산락으로 보호되는 포인트 충전
        return distributeLockExecutor.execute(
            lockKey = "point:lock:${user.id}",
            waitMilliSeconds = 3000,
            leaseMilliSeconds = 5000,
        ) {
            // 3. 트랜잭션 내부에서 포인트 충전 및 히스토리 기록
            transactionExecutor.execute {
                val point = pointService.chargePoint(user.id, command.amount)
                pointHistoryService.savePointHistory(user.id, command.amount, TransactionType.CHARGE)

                ChargePointResult(
                    userId = user.id,
                    balance = point.balance,
                )
            }
        }
    }
}
