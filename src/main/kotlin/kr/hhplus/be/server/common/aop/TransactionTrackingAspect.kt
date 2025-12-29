package kr.hhplus.be.server.common.aop

import kr.hhplus.be.server.common.util.TransactionTracker
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Aspect
@Component
class TransactionTrackingAspect {

    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        // 테스트에서 접근할 수 있도록 static으로 관리
        val transactionLog = ConcurrentHashMap<String, MutableList<TransactionSnapshot>>()
        private val callSequence = AtomicInteger(0)

        fun clear() {
            transactionLog.clear()
            callSequence.set(0)
        }

        fun getLog(testName: String): List<TransactionSnapshot> {
            return transactionLog[testName]?.toList() ?: emptyList()
        }
    }

    data class TransactionSnapshot(
        val sequence: Int,
        val methodName: String,
        val transactionName: String?,
        val isActive: Boolean,
        val isReadOnly: Boolean,
        val timestamp: Long = System.nanoTime(),
    )

    // Repository 메서드 호출 추적
    @Around("execution(* kr.hhplus.be.server.domain..repository.*Repository.*(..))")
    fun trackRepositoryTransaction(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = "${joinPoint.signature.declaringType.simpleName}.${joinPoint.signature.name}"
        val txInfo = TransactionTracker.getTransactionInfo()

        val snapshot = TransactionSnapshot(
            sequence = callSequence.incrementAndGet(),
            methodName = methodName,
            transactionName = txInfo.name,
            isActive = txInfo.isActive,
            isReadOnly = txInfo.isReadOnly,
        )

        // 현재 테스트 이름으로 로그 저장 (ThreadLocal로 관리하면 더 좋음)
        val testName = TestContext.currentTestName ?: "unknown"
        transactionLog.computeIfAbsent(testName) { mutableListOf() }.add(snapshot)

        logger.debug(
            "TX[{}] {} - active={}, readOnly={}, txName={}",
            snapshot.sequence,
            methodName,
            txInfo.isActive,
            txInfo.isReadOnly,
            txInfo.name,
        )

        return joinPoint.proceed()
    }
}

// 테스트 컨텍스트 관리
object TestContext {
    private val testNameHolder = ThreadLocal<String>()

    var currentTestName: String?
        get() = testNameHolder.get()
        set(value) = if (value != null) testNameHolder.set(value) else testNameHolder.remove()
}
