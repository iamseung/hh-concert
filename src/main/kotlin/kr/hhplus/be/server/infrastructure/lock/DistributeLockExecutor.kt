package kr.hhplus.be.server.infrastructure.lock

/**
 * 분산 락 실행기 인터페이스
 *
 * @param lockKey 락의 고유 키 (예: "reservation:lock:seat:1")
 * @param waitMilliSeconds 락 획득을 기다리는 최대 시간 (밀리초)
 * @param leaseMilliSeconds 락을 보유하는 최대 시간 (밀리초) - 이 시간이 지나면 자동으로 해제됨
 * @param block 락을 획득한 후 실행할 로직
 * @return block의 실행 결과
 */
interface DistributeLockExecutor {
    fun <T> execute(
        lockKey: String,
        waitMilliSeconds: Long,
        leaseMilliSeconds: Long,
        action: () -> T,
    ): T
}
