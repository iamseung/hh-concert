package kr.hhplus.be.server.infrastructure.event

import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 예약 확정 이벤트 리스너
 *
 * 역할:
 * 1. 결제 완료 후 실시간으로 예약 정보를 데이터 플랫폼에 전송
 * 2. 비동기 처리 (@Async) - 메인 플로우(결제)에 영향 없음
 * 3. 트랜잭션 커밋 후 실행 - 데이터 일관성 보장
 *
 * 책임 분리:
 * - EventListener: 이벤트 수신 및 오케스트레이션만 담당
 * - DataPlatformClient: 도메인 특화 API 호출 (페이로드 변환, 멱등성 키 생성)
 * - ExternalApiSender: HTTP 통신 인프라 (WebClient, Resilience4j 적용)
 *
 * 동시성 처리:
 * - @Async 스레드풀에서 비동기 실행
 * - WebClient 논블로킹 I/O로 리소스 효율적
 * - Resilience4j 재시도 + 서킷브레이커로 장애 격리
 */
@Component
class ReservationEventListener(
    private val dataPlatformClient: DataPlatformClient,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 예약 확정 이벤트 처리
     *
     * 실행 흐름:
     * 1. 트랜잭션 커밋 후 비동기 실행
     * 2. DataPlatformClient를 통한 데이터 플랫폼 전송
     * 3. 성공/실패 로깅
     *
     * 세부 처리는 DataPlatformClient와 ExternalApiSender에 위임:
     * - 재시도 (최대 3회, 지수 백오프: 100ms → 200ms → 400ms)
     * - 서킷브레이커 (실패율 50% 시 30초간 차단)
     * - 타임아웃 (3초)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReservation(event: ReservationConfirmedEvent) {
        logger.info(
            "예약 확정 이벤트 수신 - reservationId={}, concertId={}, userId={}",
            event.reservationId,
            event.concertId,
            event.userId,
        )

        dataPlatformClient.sendReservation(event)
            .doOnSuccess { response ->
                logger.info(
                    "데이터 플랫폼 전송 성공 - reservationId={}, response={}",
                    event.reservationId,
                    response,
                )
            }
            .doOnError { error ->
                logger.error(
                    "데이터 플랫폼 전송 실패 - reservationId={}, error={}",
                    event.reservationId,
                    error.message,
                    error,
                )
            }
            .subscribe()
    }
}
