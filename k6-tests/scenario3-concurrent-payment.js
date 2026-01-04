/**
 * 시나리오 3: 동시 결제 처리 (Concurrent Payment Processing)
 *
 * 목적: 결제 트랜잭션의 동시성 제어 및 포인트 차감 정합성 검증
 * - 동시 결제 요청: 1,000건/초
 * - 테스트 시간: 5분
 * - 사용자당 포인트: 충분한 잔액
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭
const paymentSuccessRate = new Rate('payment_success_rate');
const paymentResponseTime = new Trend('payment_response_time');
const pointDeductionTime = new Trend('point_deduction_time');
const dbDeadlocks = new Counter('db_deadlocks');
const connectionPoolExhaustion = new Counter('connection_pool_exhaustion');
const transactionFailures = new Counter('transaction_failures');

export const options = {
  scenarios: {
    // 사전 준비: 예약 생성 (좌석 임시 예약 상태)
    setup_reservations: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 10, // VU당 10개 예약 = 1,000개 예약
      maxDuration: '2m',
      exec: 'setupReservation',
    },
    // 메인 테스트: 동시 결제
    concurrent_payment: {
      executor: 'constant-arrival-rate',
      rate: 1000,          // 초당 1,000건
      timeUnit: '1s',
      duration: '5m',      // 5분 동안 유지
      preAllocatedVUs: 500, // 사전 할당 VU
      maxVUs: 2000,        // 최대 VU
      startTime: '2m',     // 예약 생성 후 시작
      exec: 'processPayment',
    },
  },
  thresholds: {
    'http_req_duration{name:payment}': ['p(95)<500', 'p(99)<1000'],
    'payment_success_rate': ['rate>0.99'], // 99% 이상 성공
    'payment_response_time': ['p(95)<500', 'p(99)<1000'],
    'http_req_failed{name:payment}': ['rate<0.01'],
    'db_deadlocks': ['count<10'], // 데드락 10건 이하
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_ID = 1;
const SCHEDULE_ID = 1;
const PAYMENT_AMOUNT = 10000; // 결제 금액

// 예약 ID를 저장할 배열 (VU별 독립적)
let reservationIds = [];

/**
 * 사전 준비: 예약 생성 (좌석 임시 예약)
 */
export function setupReservation() {
  const userId = `payment_user_${__VU}_${__ITER}`;

  group('예약 생성 (사전 준비)', () => {
    // 1. 토큰 발급
    const tokenResponse = issueToken(userId);
    if (!tokenResponse) return;

    const token = tokenResponse.token;

    // 2. 대기열 활성화 대기 (간소화)
    sleep(1);

    // 3. 포인트 충전 (충분한 잔액 확보)
    chargePoints(userId, token, PAYMENT_AMOUNT * 2);

    // 4. 좌석 예약
    const seatId = (__VU * 10) + __ITER; // VU별로 다른 좌석 선택
    const reservationResponse = createReservation(token, seatId);

    if (reservationResponse) {
      reservationIds.push({
        reservationId: reservationResponse.reservationId,
        userId: userId,
        token: token,
      });
      console.log(`[Setup] Created reservation: ${reservationResponse.reservationId}`);
    }
  });

  sleep(1);
}

/**
 * 메인 테스트: 동시 결제 처리
 */
export function processPayment() {
  // 예약 ID 목록에서 랜덤 선택
  if (reservationIds.length === 0) {
    console.error('No reservations available for payment');
    return;
  }

  const randomIndex = Math.floor(Math.random() * reservationIds.length);
  const reservation = reservationIds[randomIndex];

  group('동시 결제 처리', () => {
    const startTime = Date.now();

    const response = http.post(
      `${BASE_URL}/api/payments`,
      JSON.stringify({
        reservationId: reservation.reservationId,
        userId: reservation.userId,
        amount: PAYMENT_AMOUNT,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Queue-Token': reservation.token,
        },
        tags: { name: 'payment' },
      }
    );

    const elapsedTime = Date.now() - startTime;
    paymentResponseTime.add(elapsedTime);

    const checks = check(response, {
      'payment succeeded': (r) => r.status === 200,
      'insufficient points': (r) => r.status === 400,
      'already paid': (r) => r.status === 409,
      'deadlock detected': (r) => {
        if (r.status === 500 && r.body.includes('deadlock')) {
          dbDeadlocks.add(1);
          return true;
        }
        return false;
      },
      'connection pool exhausted': (r) => {
        if (r.status === 503 || (r.status === 500 && r.body.includes('connection'))) {
          connectionPoolExhaustion.add(1);
          return true;
        }
        return false;
      },
    });

    if (response.status === 200) {
      paymentSuccessRate.add(1);
      console.log(`[VU ${__VU}] Payment succeeded for reservation ${reservation.reservationId}`);

      // 결제 성공 시 목록에서 제거 (동일 예약 재결제 방지)
      reservationIds.splice(randomIndex, 1);
    } else {
      paymentSuccessRate.add(0);

      if (response.status >= 500) {
        transactionFailures.add(1);
        console.error(
          `[VU ${__VU}] Payment failed: ${response.status} - ${response.body}`
        );
      }
    }

    // 포인트 차감 시간 측정 (응답 헤더에서 가져오기, 없으면 전체 시간 사용)
    pointDeductionTime.add(elapsedTime);
  });

  sleep(0.1); // 100ms 대기
}

/**
 * 토큰 발급
 */
function issueToken(userId) {
  const response = http.post(
    `${BASE_URL}/api/v1/queue/token`,
    JSON.stringify({ userId: userId }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'issue_token' },
    }
  );

  if (response.status === 200) {
    return response.json();
  }
  return null;
}

/**
 * 포인트 충전
 */
function chargePoints(userId, token, amount) {
  const response = http.post(
    `${BASE_URL}/api/v1/points/charge`,
    JSON.stringify({
      userId: userId,
      amount: amount,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Queue-Token': token,
      },
      tags: { name: 'charge_points' },
    }
  );

  check(response, {
    'points charged': (r) => r.status === 200,
  });
}

/**
 * 좌석 예약
 */
function createReservation(token, seatId) {
  const response = http.post(
    `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/reservations`,
    JSON.stringify({
      scheduleId: SCHEDULE_ID,
      seatId: seatId,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Queue-Token': token,
      },
      tags: { name: 'create_reservation' },
    }
  );

  if (response.status === 200) {
    return response.json();
  }
  return null;
}

export function handleSummary(data) {
  const totalPayments = data.metrics['http_reqs{name:payment}']
    ? data.metrics['http_reqs{name:payment}'].values.count
    : 0;

  const summary = {
    scenario: 'Concurrent Payment Processing',
    total_payment_requests: totalPayments,
    metrics: {
      payment_success_rate: data.metrics.payment_success_rate
        ? data.metrics.payment_success_rate.values.rate
        : null,
      payment_response_time: data.metrics.payment_response_time
        ? {
            p50: data.metrics.payment_response_time.values['p(50)'],
            p95: data.metrics.payment_response_time.values['p(95)'],
            p99: data.metrics.payment_response_time.values['p(99)'],
            max: data.metrics.payment_response_time.values.max,
          }
        : null,
      point_deduction_time: data.metrics.point_deduction_time
        ? {
            p50: data.metrics.point_deduction_time.values['p(50)'],
            p95: data.metrics.point_deduction_time.values['p(95)'],
            p99: data.metrics.point_deduction_time.values['p(99)'],
          }
        : null,
      db_deadlocks: data.metrics.db_deadlocks
        ? data.metrics.db_deadlocks.values.count
        : 0,
      connection_pool_exhaustion: data.metrics.connection_pool_exhaustion
        ? data.metrics.connection_pool_exhaustion.values.count
        : 0,
      transaction_failures: data.metrics.transaction_failures
        ? data.metrics.transaction_failures.values.count
        : 0,
    },
  };

  return {
    'summary-scenario3.json': JSON.stringify(summary, null, 2),
    stdout: createTextSummary(summary),
  };
}

function createTextSummary(summary) {
  return `
시나리오 3: 동시 결제 처리 테스트 결과
======================================

총 결제 요청 수: ${summary.total_payment_requests}

결제 성공률: ${summary.metrics.payment_success_rate
  ? (summary.metrics.payment_success_rate * 100).toFixed(2)
  : 'N/A'}%

결제 응답 시간:
  - P50: ${summary.metrics.payment_response_time
    ? summary.metrics.payment_response_time.p50.toFixed(2)
    : 'N/A'} ms
  - P95: ${summary.metrics.payment_response_time
    ? summary.metrics.payment_response_time.p95.toFixed(2)
    : 'N/A'} ms
  - P99: ${summary.metrics.payment_response_time
    ? summary.metrics.payment_response_time.p99.toFixed(2)
    : 'N/A'} ms
  - Max: ${summary.metrics.payment_response_time
    ? summary.metrics.payment_response_time.max.toFixed(2)
    : 'N/A'} ms

포인트 차감 시간:
  - P50: ${summary.metrics.point_deduction_time
    ? summary.metrics.point_deduction_time.p50.toFixed(2)
    : 'N/A'} ms
  - P95: ${summary.metrics.point_deduction_time
    ? summary.metrics.point_deduction_time.p95.toFixed(2)
    : 'N/A'} ms
  - P99: ${summary.metrics.point_deduction_time
    ? summary.metrics.point_deduction_time.p99.toFixed(2)
    : 'N/A'} ms

DB 데드락 발생: ${summary.metrics.db_deadlocks} 건
커넥션 풀 고갈: ${summary.metrics.connection_pool_exhaustion} 건
트랜잭션 실패: ${summary.metrics.transaction_failures} 건
  `;
}
