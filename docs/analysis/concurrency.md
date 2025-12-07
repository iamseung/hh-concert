# 과제
- **“콘서트 예약 서비스”**
    - **예상 동시성 이슈**
        - 같은 좌석에 대해 동시에 예약 요청 → **중복 예약 발생**
        - 잔액 차감 중 충돌 발생 → **음수 잔액**
        - 예약 후 결제 지연 → **임시 배정 해제 로직 부정확**
    - **구현 및 보고서 요구사항**
        - 다음 항목 중 2개 이상 구현 및 테스트
            - 좌석 임시 배정 시 락 제어
            - 잔액 차감 동시성 제어
            - 배정 타임아웃 해제 스케줄러 + 테스트
        - **조건부 UPDATE / SELECT FOR UPDATE / 낙관적 락** 중 하나 이상 사용
        - **멀티스레드 테스트** 작성
        - md에 문제 상황, 해결 전략, 테스트 결과를 정리

1. Finalize
- 지금까지 구현한 API를 기반으로 **서비스 전체가 실제 운영 환경에서도 안정적으로 동작할 수 있도록 마무리 작업을 수행**합니다.
- 모든 기능이 **정상 동작**하고, **예외 상황에 유연하게 대응**할 수 있어야 하며, **테스트를 통해 안정성**을 검증해야 합니다.
- 각 기능에 대해 **테스트 케이스 1개 이상** 작성 필수
# API 기준 동시성 문제가 발생할 수 있는 기능 선별
1. 포인트 충전 (ChargePointUseCase)
2. 좌석 예약
3. 결제 처리

## 1. 포인트 충전

1. 트랜잭션 격리 수준의 적용
```kotlin
@Transactional(isolation = Isolation.REPEATABLE_READ)
fun execute(command: ProcessPaymentCommand) {
    val user = userService.findById(command.userId)          // ← REPEATABLE_READ
    val reservation = reservationService.findById(...)       // ← REPEATABLE_READ
    val seat = seatService.findById(reservation.seatId)      // ← REPEATABLE_READ
    val token = queueTokenService.getQueueTokenByToken(...)  // ← REPEATABLE_READ

    // 모든 SELECT 쿼리에 REPEATABLE_READ가 적용됨
}
```

2. 하지만 명시적 Lock이 있으면?
```kotlin
// PointService.kt
fun usePoint(userId: Long, amount: Int): PointModel {
    // 이 SELECT는 FOR UPDATE 락을 사용
    val point = pointRepository.findByUserIdWithLock(userId)  // ← 비관적 락 우선!
    point.usePoint(amount)
    return pointRepository.update(point)
}
```

## 2. 좌석 예약
좌석 예약도 동일하게 좌석에 대해 X-Lock 을 지정하는 것으로 해결