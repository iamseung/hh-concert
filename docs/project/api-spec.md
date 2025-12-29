# API 명세서

## 개요
- Base URL: `/api/v1`
- 모든 응답은 JSON 형식
- 날짜/시간은 UTC 기준
- 활성화되고 삭제되지 않은 데이터만 반환 (is_active=true, is_deleted=false)

## 1. Concert API

### 1.1 예약 가능한 날짜 목록 조회

**Endpoint:** `GET /concerts/{concertId}/schedules`

**Description:** 특정 콘서트의 예약 가능한 날짜 목록을 조회합니다.

**Path Parameters:**
- `concertId` (Long, required): 콘서트 ID

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "concertId": 1,
    "concertDate": "2025-12-15"
  },
  {
    "id": 2,
    "concertId": 1,
    "concertDate": "2025-12-16"
  }
]
```

**Response Schema:**
```kotlin
data class ConcertScheduleResponse(
    val id: Long,                    // 일정 ID
    val concertId: Long,             // 콘서트 ID
    val concertDate: String          // 날짜 (yyyy-MM-dd)
)
```

**Error Responses:**
- `404 Not Found`: 콘서트를 찾을 수 없음
```json
{
  "timestamp": "2025-11-14T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "요청한 엔티티를 찾을 수 없습니다",
  "path": "/api/v1/concerts/999/schedules"
}
```

**비즈니스 로직:**
- 해당 콘서트의 모든 일정 조회
- `isAvailable` (concertDate >= 오늘) 인 일정만 필터링
- LocalDate를 ISO_LOCAL_DATE 포맷(yyyy-MM-dd)으로 변환하여 반환

---

### 1.2 예약 가능한 좌석 조회

**Endpoint:** `GET /concerts/{concertId}/schedules/{scheduleId}/seats`

**Description:** 특정 날짜의 예약 가능한 좌석 정보를 조회합니다. 좌석 번호는 1-50번까지 관리됩니다.

**Path Parameters:**
- `concertId` (Long, required): 콘서트 ID
- `scheduleId` (Long, required): 콘서트 일정 ID

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "scheduleId": 1,
    "seatNumber": 15,
    "seatStatus": "AVAILABLE",
    "price": 150000
  },
  {
    "id": 2,
    "scheduleId": 1,
    "seatNumber": 16,
    "seatStatus": "AVAILABLE",
    "price": 150000
  }
]
```

**Response Schema:**
```kotlin
data class SeatResponse(
    val id: Long,                    // 좌석 ID
    val scheduleId: Long,            // 일정 ID
    val seatNumber: Int,             // 좌석 번호 (1-50)
    val seatStatus: SeatStatus,      // 좌석 상태 (AVAILABLE, , RESERVED)
    val price: Int                   // 가격 (원)
)

enum class SeatStatus {
    AVAILABLE,           // 예약 가능
    ,  // 임시 배정 (5분간)
    RESERVED             // 예약 완료
}
```

**Error Responses:**
- `404 Not Found`: 콘서트 또는 일정을 찾을 수 없음

**비즈니스 로직:**
- 콘서트 존재 확인
- 일정 존재 확인
- 해당 일정의 모든 좌석 조회
- `isAvailable` (seatStatus == AVAILABLE) 인 좌석만 필터링

---

## 2. Reservation API

### 2.1 좌석 예약 요청 (임시 배정)

**Endpoint:** `POST /api/v1/concerts/{concertId}/reservations`

**Description:** 날짜와 좌석 정보를 입력받아 좌석을 5분간 임시 예약합니다.

**Path Parameters:**
- `concertId` (Long, required): 콘서트 ID

**Headers:**
- `X-Queue-Token` (String, required): 대기열 토큰 (UUID 형식)

**Request Body:**
```json
{
  "userId": 1,
  "scheduleId": 1,
  "seatId": 15
}
```

**Request Schema:**
```kotlin
data class CreateReservationRequest(
    val userId: Long,      // 사용자 ID
    val scheduleId: Long,  // 일정 ID
    val seatId: Long       // 좌석 ID
)
```

**Response (201 Created):**
```json
{
  "id": 1,
  "userId": 1,
  "seatId": 15,
  "seatNumber": 15,
  "price": 150000,
  "reservationStatus": "TEMPORARY",
  "temporaryReservedAt": "2025-11-16T10:00:00Z",
  "temporaryExpiredAt": "2025-11-16T10:05:00Z"
}
```

**Response Schema:**
```kotlin
data class ReservationResponse(
    val id: Long,                      // 예약 ID
    val userId: Long,                  // 사용자 ID
    val seatId: Long,                  // 좌석 ID
    val seatNumber: Int,               // 좌석 번호
    val price: Int,                    // 가격
    val reservationStatus: ReservationStatus,  // 예약 상태
    val temporaryReservedAt: String?,  // 임시 배정 시각 (ISO-8601)
    val temporaryExpiredAt: String?    // 임시 배정 만료 시각 (ISO-8601)
)

enum class ReservationStatus {
    TEMPORARY,    // 임시 배정 (5분간)
    CONFIRMED,    // 확정 (결제 완료)
    CANCELED      // 취소
}
```

**Error Responses:**
- `400 Bad Request`: 이미 예약된 좌석, 만료된 일정 등
- `401 Unauthorized`: 유효하지 않은 토큰
- `403 Forbidden`: 대기열 토큰이 ACTIVE 상태가 아님
- `404 Not Found`: 사용자, 일정 또는 좌석을 찾을 수 없음

**비즈니스 로직:**
1. 사용자 존재 확인
2. 좌석 존재 및 예약 가능 여부 확인 (seatStatus == AVAILABLE)
3. 콘서트 일정이 유효한지 확인 (concertDate >= 오늘)
4. 좌석을 TEMPORARY_RESERVED 상태로 변경
5. 예약 생성 (상태: TEMPORARY, 5분 후 만료)

**중요:**
- 좌석은 5분간 임시 배정됩니다
- 5분 내 결제 미완료 시 자동으로 AVAILABLE 상태로 복원됩니다
- 대기열 토큰이 ACTIVE 상태여야 예약 가능합니다

---

### 2.2 예약 조회

**Endpoint:** `GET /api/v1/concerts/{concertId}/reservations`

**Description:** 사용자의 예약 목록을 조회합니다.

**Path Parameters:**
- `concertId` (Long, required): 콘서트 ID

**Headers:**
- `User-Id` (Long, required): 사용자 ID

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "userId": 1,
    "seatId": 15,
    "seatNumber": 15,
    "price": 150000,
    "reservationStatus": "CONFIRMED",
    "temporaryReservedAt": "2025-11-16T10:00:00Z",
    "temporaryExpiredAt": "2025-11-16T10:05:00Z"
  }
]
```

**Error Responses:**
- `404 Not Found`: 사용자를 찾을 수 없음

---

## 3. Point API

### 3.1 포인트 조회

**Endpoint:** `GET /api/v1/points?userId={userId}`

**Description:** 사용자 식별자를 통해 해당 사용자의 포인트를 조회합니다.

**Query Parameters:**
- `userId` (Long, required): 사용자 ID

**Response (200 OK):**
```json
{
  "userId": 1,
  "balance": 50000
}
```

**Response Schema:**
```kotlin
data class PointResponse(
    val userId: Long,    // 사용자 ID
    val balance: Int     // 포인트 잔액 (원)
)
```

**Error Responses:**
- `404 Not Found`: 사용자 또는 포인트 정보를 찾을 수 없음

---

### 3.2 포인트 충전

**Endpoint:** `POST /api/v1/points/charge`

**Description:** 사용자 식별자 및 충전할 금액을 받아 포인트를 충전합니다.

**Request Body:**
```json
{
  "userId": 1,
  "amount": 10000
}
```

**Request Schema:**
```kotlin
data class ChargePointRequest(
    val userId: Long,   // 사용자 ID
    val amount: Int     // 충전 금액 (원, 0보다 커야 함)
)
```

**Response (200 OK):**
```json
{
  "userId": 1,
  "balance": 60000
}
```

**Error Responses:**
- `400 Bad Request`: 충전 금액이 0 이하
```json
{
  "timestamp": "2025-11-16T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "충전 금액이 올바르지 않습니다",
  "path": "/api/v1/points/charge"
}
```
- `404 Not Found`: 사용자를 찾을 수 없음

**비즈니스 로직:**
1. 사용자 존재 확인
2. 충전 금액 검증 (amount > 0)
3. 포인트 잔액에 충전 금액 추가
4. 포인트 히스토리 생성 (거래 타입: CHARGE)

---

## 4. Payment API

### 4.1 결제 처리

**Endpoint:** `POST /api/payments`

**Description:** 임시 예약된 좌석에 대해 결제를 처리합니다. 포인트를 차감하고 예약을 확정합니다.

**Headers:**
- `User-Id` (Long, required): 사용자 ID

**Request Body:**
```json
{
  "reservationId": 1
}
```

**Request Schema:**
```kotlin
data class ProcessPaymentRequest(
    val reservationId: Long   // 예약 ID
)
```

**Response (200 OK):**
```json
{
  "paymentId": 1,
  "reservationId": 1,
  "userId": 1,
  "amount": 150000,
  "paymentStatus": "PENDING"
}
```

**Response Schema:**
```kotlin
data class PaymentResponse(
    val paymentId: Long,              // 결제 ID
    val reservationId: Long,          // 예약 ID
    val userId: Long,                 // 사용자 ID
    val amount: Int,                  // 결제 금액 (원)
    val paymentStatus: PaymentStatus  // 결제 상태
)

enum class PaymentStatus {
    PENDING,     // 대기 중
    COMPLETED,   // 완료
    CANCELED     // 취소
}
```

**Error Responses:**
- `400 Bad Request`: 잘못된 요청
  - 예약이 본인 것이 아님
  - 예약 상태가 TEMPORARY가 아님 (이미 확정됨 등)
  - 포인트 부족
```json
{
  "timestamp": "2025-11-16T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "포인트가 부족합니다",
  "path": "/api/payments"
}
```
- `404 Not Found`: 예약 또는 사용자를 찾을 수 없음

**비즈니스 로직:**
1. 사용자 존재 확인
2. 예약 존재 확인
3. 예약 소유권 검증 (reservation.userId == userId)
4. 예약 상태 검증 (reservationStatus == TEMPORARY)
5. 포인트 차감 (잔액 부족 시 실패)
6. 포인트 히스토리 생성 (거래 타입: USE)
7. 결제 생성 (상태: PENDING)
8. 좌석 상태 변경 (TEMPORARY_RESERVED → RESERVED)
9. 예약 상태 변경 (TEMPORARY → CONFIRMED)

**중요:**
- 임시 예약(TEMPORARY) 상태인 예약만 결제 가능
- 결제 완료 시 좌석이 최종 확정되며 다른 사용자는 예약 불가
- 포인트 부족 시 트랜잭션 롤백되어 예약 상태 유지

---

## 5. Exception Handling

### 전역 예외 처리

모든 예외는 GlobalExceptionHandler에서 처리되며 일관된 형식으로 반환됩니다.

**ErrorResponse 공통 형식:**
```json
{
  "timestamp": "2025-11-14T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "요청한 엔티티를 찾을 수 없습니다",
  "path": "/api/v1/concerts/999/schedules"
}
```

### 주요 에러 코드

| HTTP Status | Error Code | Message |
|-------------|------------|---------|
| 400 | INVALID_INPUT_VALUE | 잘못된 입력값입니다 |
| 400 | SEAT_ALREADY_RESERVED | 이미 예약된 좌석입니다 |
| 400 | INSUFFICIENT_POINTS | 포인트가 부족합니다 |
| 401 | UNAUTHORIZED | 인증에 실패했습니다 |
| 403 | ACCESS_DENIED | 접근 권한이 없습니다 |
| 404 | ENTITY_NOT_FOUND | 요청한 엔티티를 찾을 수 없습니다 |
| 404 | CONCERT_NOT_FOUND | 콘서트를 찾을 수 없습니다 |
| 404 | SCHEDULE_NOT_FOUND | 콘서트 일정을 찾을 수 없습니다 |
| 404 | SEAT_NOT_FOUND | 좌석을 찾을 수 없습니다 |
| 500 | INTERNAL_SERVER_ERROR | 서버 내부 오류가 발생했습니다 |

---

## 6. 데이터 타입 및 제약사항

### Concert
- `id`: Long (auto_increment)
- `title`: String (required)
- `description`: String (nullable, TEXT)
- BaseEntity 상속 (is_active, is_deleted, created_at, updated_at)

### ConcertSchedule
- `id`: Long (auto_increment)
- `concert_id`: Long (required, FK)
- `concert_date`: LocalDate (required)
- BaseEntity 상속
- **계산 필드:**
  - `isAvailable`: Boolean = !concertDate.isBefore(LocalDate.now())

### Seat
- `id`: Long (auto_increment)
- `concert_schedule_id`: Long (required, FK)
- `seat_number`: Int (1-50)
- `seat_status`: SeatStatus (AVAILABLE, , RESERVED)
- `price`: Int (원 단위)
- BaseEntity 상속
- **계산 필드:**
  - `isAvailable`: Boolean = seatStatus == SeatStatus.AVAILABLE

---

## 7. 비즈니스 규칙

### 일정 조회
1. 콘서트 ID로 콘서트 존재 확인 (없으면 404)
2. 해당 콘서트의 모든 일정 조회
3. 현재 날짜 이후의 일정만 필터링 (isAvailable = true)
4. LocalDate를 yyyy-MM-dd 포맷 String으로 변환하여 반환

### 좌석 조회
1. 콘서트 ID로 콘서트 존재 확인 (없으면 404)
2. 일정 ID로 일정 존재 확인 (없으면 404)
3. 해당 일정의 모든 좌석 조회
4. 예약 가능한 좌석만 필터링 (seatStatus == AVAILABLE)
