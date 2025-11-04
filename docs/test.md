openapi: 3.0.1
info:
title: Concert Reservation API
version: 1.0.0
description: 콘서트 예약 서비스 API 명세서
servers:

- url: http://localhost:8080/api/v1
  description: Local server
  tags:
- name: Auth
  description: 로그인/회원가입
- name: Queue
  description: 대기 관리
- name: Reservation
  description: 좌석 예약/취소
- name: Wallet
  description: 포인트 잔액/충전
- name: Payment
  description: 결제 확정

paths:
/auth/login:
post:
tags: [Auth]
summary: 로그인
requestBody:
required: true
content:
application/json:
schema:
type: object
properties:
loginId: { type: string, example: user@example.com }
password: { type: string, example: P@ssw0rd! }
responses:
'200':
description: 로그인 성공
content:
application/json:
schema:
$ref: '#/components/schemas/TokenResponse'
'401':
description: 인증 실패

/queue/tokens:
post:
tags: [Queue]
summary: 대기열 토큰 발급
responses:
'201':
description: 성공
content:
application/json:
schema:
$ref: '#/components/schemas/QueueTokenResponse'
'429':
description: 대기열 만석

/reservations:
post:
tags: [Reservation]
summary: 좌석 예약(임시 배정)
requestBody:
required: true
content:
application/json:
schema:
$ref: '#/components/schemas/ReservationRequest'
responses:
'201':
description: 예약 생성
content:
application/json:
schema:
$ref: '#/components/schemas/ReservationResponse'
'409':
description: 좌석 충돌

/wallet/balance:
get:
tags: [Wallet]
summary: 잔액 조회
responses:
'200':
description: 성공
content:
application/json:
schema:
$ref: '#/components/schemas/WalletBalance'

/payments:
post:
tags: [Payment]
summary: 결제 확정
parameters:

- in: header
  name: Idempotency-Key
  required: true
  schema: { type: string }
  requestBody:
  required: true
  content:
  application/json:
  schema:
  $ref: '#/components/schemas/PaymentRequest'
  responses:
  '200':
  description: 결제 성공
  content:
  application/json:
  schema:
  $ref: '#/components/schemas/PaymentResponse'
  '409':
  description: 상태 충돌(잔액 부족/만료 등)

components:
securitySchemes:
bearerAuth:
type: http
scheme: bearer
bearerFormat: JWT

schemas:
TokenResponse:
type: object
properties:
accessToken: { type: string, example: eyJhbGciOi... }
refreshToken: { type: string, example: eyJhbGciOi... }

    QueueTokenResponse:
      type: object
      properties:
        token: { type: string, example: qtk_abc123 }
        position: { type: integer, example: 12 }
        etaSeconds: { type: integer, example: 300 }

    ReservationRequest:
      type: object
      properties:
        showId: { type: integer, example: 202 }
        seatIds:
          type: array
          items: { type: integer, example: 12 }

    ReservationResponse:
      type: object
      properties:
        reservationId: { type: integer, example: 5001 }
        status: { type: string, enum: [HELD, CONFIRMED] }
        expiresAt: { type: string, format: date-time }

    WalletBalance:
      type: object
      properties:
        balance: { type: integer, example: 10000 }
        asOf: { type: string, format: date-time }

    PaymentRequest:
      type: object
      properties:
        reservationId: { type: integer, example: 5001 }
        amount: { type: integer, example: 300000 }

    PaymentResponse:
      type: object
      properties:
        paymentId: { type: integer, example: 9001 }
        status: { type: string, enum: [SUCCEEDED, FAILED] }
        paidAt: { type: string, format: date-time }