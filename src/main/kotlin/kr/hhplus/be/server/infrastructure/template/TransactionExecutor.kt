package kr.hhplus.be.server.infrastructure.template

import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 프로그래매틱 트랜잭션 헬퍼
 *
 * TransactionTemplate을 Kotlin 친화적으로 래핑
 * - Self-invocation 문제 해결
 * - 분산락과 함께 사용 시 트랜잭션 범위 명확화
 *
 * 사용 예시:
 * ```kotlin
 * val result = transactionExecutor.execute {
 *     // 트랜잭션 내부 로직
 * }
 * ```
 *
 * 출처: https://mangkyu.tistory.com/394
 */
@Service
class TransactionExecutor(
    transactionManager: PlatformTransactionManager,
) {

    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val readOnlyTransactionTemplate = TransactionTemplate(transactionManager).apply {
        isReadOnly = true
    }

    /**
     * 읽기/쓰기 트랜잭션 실행
     *
     * @param action 트랜잭션 내부에서 실행할 로직
     * @return action의 실행 결과
     */
    fun <T> execute(action: () -> T): T {
        return transactionTemplate.execute {
            action()
        } ?: throw IllegalStateException("Transaction returned null unexpectedly")
    }

    /**
     * 읽기 전용 트랜잭션 실행
     *
     * @param action 트랜잭션 내부에서 실행할 로직
     * @return action의 실행 결과
     */
    fun <T> executeReadOnly(action: () -> T): T {
        return readOnlyTransactionTemplate.execute {
            action()
        } ?: throw IllegalStateException("Transaction returned null unexpectedly")
    }
}
