# 🎤 콘서트 예약 서비스

## ERD (Entity Relationship Diagram)

```mermaid
erDiagram
    USER ||--o{ POINT_HISTORY : "has"
    USER ||--|| POINT : "has"
    USER ||--o{ RESERVATION : "makes"
    USER ||--o{ PAYMENT : "makes"

    CONCERT ||--o{ CONCERT_SCHEDULE : "has"
    CONCERT_SCHEDULE ||--o{ SEAT : "has"

    SEAT ||--o| RESERVATION : "reserved_by"
    RESERVATION ||--o| PAYMENT : "paid_by"

    USER {
        bigint id PK
        string user_name
        string email
        string password
        timestamp created_at
        timestamp updated_at
    }

    POINT {
        bigint id PK
        bigint user_id FK
        int balance "포인트 잔액"
        timestamp created_at
        timestamp updated_at
    }

    POINT_HISTORY {
        bigint id PK
        bigint user_id FK
        int amount "충전/사용 금액"
        string transaction_type "CHARGE, USE"
        timestamp created_at
        timestamp updated_at
    }

    CONCERT {
        bigint id PK
        string title
        text description "nullable"
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    CONCERT_SCHEDULE {
        bigint id PK
        bigint concert_id FK
        date concert_date "콘서트 날짜 (LocalDate)"
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    SEAT {
        bigint id PK
        bigint concert_schedule_id FK
        int seat_number "좌석 번호 (1-50)"
        string seat_status "AVAILABLE, , RESERVED"
        int price "좌석 가격 (Int)"
        timestamp created_at
        timestamp updated_at
    }

    RESERVATION {
        bigint id PK
        bigint user_id FK
        bigint seat_id FK
        string reservation_status "TEMPORARY, CONFIRMED, EXPIRED, CANCELED"
        timestamp _at "임시 배정 시간"
        timestamp temporary_expired_at "임시 배정 만료 시간 (5분)"
        timestamp created_at
        timestamp updated_at
    }

    PAYMENT {
        bigint id PK
        bigint reservation_id FK
        bigint user_id FK
        int amount "결제 금액"
        string payment_status "PENDING, CONFIRMED, CANCELLED, FAILED"
        timestamp payment_at "결제 시간"
        timestamp created_at
        timestamp updated_at
    }
```

## 엔티티 설명

### USER (사용자)
- 시스템을 이용하는 사용자 정보
- 포인트, 예약, 결제와 연관
- password 필드를 포함한 기본 인증 정보 관리

### POINT (포인트)
- 사용자별 현재 포인트 잔액
- 결제에 사용됨
- Int 타입으로 관리

### POINT_HISTORY (포인트 내역)
- 포인트 충전/사용 이력 추적
- transaction_type: CHARGE(충전), USE(사용)
- Int 타입으로 금액 관리

### CONCERT (콘서트)
- 콘서트 기본 정보
- description은 nullable

### CONCERT_SCHEDULE (콘서트 일정)
- 콘서트별 예약 가능한 날짜 정보
- 하나의 콘서트는 여러 일정을 가질 수 있음
- concert_date: LocalDate 타입 (날짜만 저장, 시간 정보 없음)
- isAvailable: concertDate >= LocalDate.now() 로 판단

### SEAT (좌석)
- 콘서트 일정별 좌석 정보
- seat_number: 1-50 범위
- 좌석 상태 (SeatStatus Enum):
  - AVAILABLE: 예약 가능
  - : 임시 배정 (5분간)
  - RESERVED: 예약 완료
- price: Int 타입으로 관리
- isAvailable: seatStatus == SeatStatus.AVAILABLE 로 판단

### RESERVATION (예약)
- 사용자의 좌석 예약 정보
- 예약 상태:
  - TEMPORARY: 임시 배정 (결제 대기)
  - CONFIRMED: 결제 완료로 확정
  - EXPIRED: 5분 내 미결제로 만료
  - CANCELED: 취소됨
- temporary_expired_at: 임시 배정 후 5분 후 자동 만료

### PAYMENT (결제)
- 예약에 대한 결제 정보
- 결제 상태:
  - PENDING: 결제 대기
  - CONFIRMED: 결제 완료
  - CANCELLED: 결제 취소
  - FAILED: 결제 실패
- 금액은 Int 타입으로 관리

## 주요 비즈니스 로직

1. **좌석 예약 프로세스**
   - 사용자가 ACTIVE 대기열 토큰 필요
   - 좌석 선택 → SEAT 상태를 TEMPORARILY_RESERVED로 변경
   - RESERVATION 생성 (TEMPORARY 상태, 5분 만료 시간 설정)
   - 5분 내 결제 미완료 시 자동 만료 → 좌석 상태 AVAILABLE로 복원

2. **결제 프로세스**
   - 사용자 포인트 잔액 확인
   - PAYMENT 생성 및 포인트 차감
   - 결제 완료 시:
     - RESERVATION 상태 → CONFIRMED
     - SEAT 상태 → RESERVED
     - WAITING_QUEUE 토큰 → EXPIRED

3. **동시성 제어**
   - 좌석 예약 시 비관적 락(Pessimistic Lock) 또는 낙관적 락(Optimistic Lock) 사용
   - 포인트 차감 시 트랜잭션 격리 수준 관리

4. **대기열 관리**
   - 특정 시간 동안 N명에게만 ACTIVE 권한 부여
   - 활성화된 최대 유저 수 N으로 유지
   - 순서대로 정확한 대기열 제공