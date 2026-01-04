/**
 * 시나리오 5: 지속적 부하 테스트 (Sustained Load Test)
 *
 * 목적: 장시간 운영 시 시스템 안정성 및 리소스 누수 확인
 * - 목표 부하: 1,000 req/sec
 * - 테스트 시간: 10분
 * - 트래픽 패턴: 일정 부하 유지
 * - 시나리오: 대기열 → 예약 → 결제의 완전한 사용자 여정
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// 커스텀 메트릭
const overallSuccessRate = new Rate('overall_success_rate');
const endToEndDuration = new Trend('end_to_end_duration');
const errorRate = new Rate('error_rate');
const memoryUsage = new Gauge('estimated_memory_usage');
const activeConnections = new Gauge('active_connections');

// 에러 타입별 카운터
const tokenErrors = new Counter('token_errors');
const queueErrors = new Counter('queue_errors');
const reservationErrors = new Counter('reservation_errors');
const paymentErrors = new Counter('payment_errors');

export const options = {
  scenarios: {
    sustained_load: {
      executor: 'constant-arrival-rate',
      rate: 1000,           // 초당 1,000 요청
      timeUnit: '1s',
      duration: '10m',      // 10분 동안 유지
      preAllocatedVUs: 300,
      maxVUs: 1000,
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],
    'error_rate': ['rate<0.001'],                      // 에러율 < 0.1%
    'overall_success_rate': ['rate>0.999'],            // 전체 성공률 > 99.9%
    'end_to_end_duration': ['p(95)<5000', 'p(99)<10000'], // E2E 시간
    'http_req_failed': ['rate<0.001'],
  },
  // 외부 메트릭 출력 설정
  ext: {
    loadimpact: {
      projectID: 3543743,
      name: 'Sustained Load Test - Concert Reservation System',
    },
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_ID = 1;
const SCHEDULE_ID = 1;

export default function () {
  const userId = `sustained_user_${__VU}_${__ITER}`;
  const startTime = Date.now();
  let overallSuccess = true;

  // 완전한 사용자 여정 시뮬레이션
  group('완전한 사용자 여정', () => {
    // 1. 대기열 토큰 발급
    const token = issueQueueTokenFlow(userId);
    if (!token) {
      overallSuccess = false;
      errorRate.add(1);
      tokenErrors.add(1);
      return;
    }

    // 2. 대기열 대기 (간소화된 폴링)
    if (!waitForQueueActivation(token)) {
      overallSuccess = false;
      errorRate.add(1);
      queueErrors.add(1);
      return;
    }

    // 3. 콘서트 정보 탐색
    browseConcertInfo(token);

    // 4. 포인트 충전
    const chargeSuccess = chargeUserPoints(userId, token);
    if (!chargeSuccess) {
      overallSuccess = false;
      errorRate.add(1);
      return;
    }

    // 5. 좌석 예약
    const reservationId = reserveSeat(token);
    if (!reservationId) {
      overallSuccess = false;
      errorRate.add(1);
      reservationErrors.add(1);
      return;
    }

    // 6. 결제 처리
    const paymentSuccess = processPayment(userId, token, reservationId);
    if (!paymentSuccess) {
      overallSuccess = false;
      errorRate.add(1);
      paymentErrors.add(1);
      return;
    }

    // 7. 예약 확인
    verifyReservation(token, reservationId);
  });

  const endTime = Date.now();
  const totalDuration = endTime - startTime;

  // E2E 메트릭 수집
  endToEndDuration.add(totalDuration);
  overallSuccessRate.add(overallSuccess ? 1 : 0);
  errorRate.add(overallSuccess ? 0 : 1);

  // 메모리 추정 (VU 수 기반)
  memoryUsage.add(__VU * 0.5); // VU당 약 0.5MB로 가정
  activeConnections.add(__VU);

  // Think time (사용자 행동 시뮬레이션)
  sleep(Math.random() * 2 + 1); // 1~3초 대기
}

/**
 * 1. 대기열 토큰 발급
 */
function issueQueueTokenFlow(userId) {
  const response = http.post(
    `${BASE_URL}/api/v1/queue/token`,
    JSON.stringify({ userId: userId }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'issue_token', flow: 'user_journey' },
    }
  );

  const success = check(response, {
    'token issued': (r) => r.status === 200,
  });

  return success ? response.json('token') : null;
}

/**
 * 2. 대기열 활성화 대기
 */
function waitForQueueActivation(token) {
  const maxRetries = 10;
  let retries = 0;

  while (retries < maxRetries) {
    const response = http.get(
      `${BASE_URL}/api/v1/queue/status`,
      {
        headers: { 'X-Queue-Token': token },
        tags: { name: 'queue_status', flow: 'user_journey' },
      }
    );

    if (response.status === 200) {
      const status = response.json('status');
      if (status === 'ACTIVE') {
        return true;
      }
    }

    sleep(1);
    retries++;
  }

  return false;
}

/**
 * 3. 콘서트 정보 탐색 (일정 및 좌석)
 */
function browseConcertInfo(token) {
  // 일정 조회
  http.get(
    `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/schedules`,
    {
      headers: { 'X-Queue-Token': token },
      tags: { name: 'get_schedules', flow: 'user_journey' },
    }
  );

  sleep(0.5);

  // 좌석 조회
  http.get(
    `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/schedules/${SCHEDULE_ID}/seats`,
    {
      headers: { 'X-Queue-Token': token },
      tags: { name: 'get_seats', flow: 'user_journey' },
    }
  );

  sleep(0.5);
}

/**
 * 4. 포인트 충전
 */
function chargeUserPoints(userId, token) {
  const response = http.post(
    `${BASE_URL}/api/v1/points/charge`,
    JSON.stringify({
      userId: userId,
      amount: 50000,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Queue-Token': token,
      },
      tags: { name: 'charge_points', flow: 'user_journey' },
    }
  );

  return check(response, {
    'points charged': (r) => r.status === 200,
  });
}

/**
 * 5. 좌석 예약
 */
function reserveSeat(token) {
  const seatId = Math.floor(Math.random() * 100) + 1;

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
      tags: { name: 'create_reservation', flow: 'user_journey' },
    }
  );

  if (response.status === 200) {
    return response.json('reservationId');
  }

  return null;
}

/**
 * 6. 결제 처리
 */
function processPayment(userId, token, reservationId) {
  const response = http.post(
    `${BASE_URL}/api/payments`,
    JSON.stringify({
      reservationId: reservationId,
      userId: userId,
      amount: 10000,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Queue-Token': token,
      },
      tags: { name: 'payment', flow: 'user_journey' },
    }
  );

  return check(response, {
    'payment succeeded': (r) => r.status === 200,
  });
}

/**
 * 7. 예약 확인
 */
function verifyReservation(token, reservationId) {
  http.get(
    `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/reservations`,
    {
      headers: { 'X-Queue-Token': token },
      tags: { name: 'get_reservations', flow: 'user_journey' },
    }
  );
}

export function handleSummary(data) {
  const totalRequests = data.metrics.http_reqs.values.count;
  const totalIterations = data.metrics.iterations.values.count;

  const summary = {
    scenario: 'Sustained Load Test',
    duration: '10 minutes',
    target_rate: '1,000 req/sec',
    total_requests: totalRequests,
    total_user_journeys: totalIterations,
    metrics: {
      overall_success_rate: data.metrics.overall_success_rate
        ? data.metrics.overall_success_rate.values.rate
        : null,
      error_rate: data.metrics.error_rate ? data.metrics.error_rate.values.rate : null,
      end_to_end_duration: data.metrics.end_to_end_duration
        ? {
            avg: data.metrics.end_to_end_duration.values.avg,
            p50: data.metrics.end_to_end_duration.values['p(50)'],
            p95: data.metrics.end_to_end_duration.values['p(95)'],
            p99: data.metrics.end_to_end_duration.values['p(99)'],
            max: data.metrics.end_to_end_duration.values.max,
          }
        : null,
      http_req_duration: {
        avg: data.metrics.http_req_duration.values.avg,
        p50: data.metrics.http_req_duration.values['p(50)'],
        p95: data.metrics.http_req_duration.values['p(95)'],
        p99: data.metrics.http_req_duration.values['p(99)'],
      },
      errors: {
        token_errors: data.metrics.token_errors
          ? data.metrics.token_errors.values.count
          : 0,
        queue_errors: data.metrics.queue_errors
          ? data.metrics.queue_errors.values.count
          : 0,
        reservation_errors: data.metrics.reservation_errors
          ? data.metrics.reservation_errors.values.count
          : 0,
        payment_errors: data.metrics.payment_errors
          ? data.metrics.payment_errors.values.count
          : 0,
      },
    },
  };

  return {
    'summary-scenario5.json': JSON.stringify(summary, null, 2),
    stdout: createTextSummary(summary),
  };
}

function createTextSummary(summary) {
  return `
시나리오 5: 지속적 부하 테스트 결과
====================================

테스트 기간: ${summary.duration}
목표 부하: ${summary.target_rate}

총 요청 수: ${summary.total_requests.toLocaleString()}
완료된 사용자 여정: ${summary.total_user_journeys.toLocaleString()}

전체 성공률: ${summary.metrics.overall_success_rate
  ? (summary.metrics.overall_success_rate * 100).toFixed(3)
  : 'N/A'}%
에러율: ${summary.metrics.error_rate
  ? (summary.metrics.error_rate * 100).toFixed(3)
  : 'N/A'}%

End-to-End 사용자 여정 시간:
  - 평균: ${summary.metrics.end_to_end_duration
    ? (summary.metrics.end_to_end_duration.avg / 1000).toFixed(2)
    : 'N/A'} 초
  - P50: ${summary.metrics.end_to_end_duration
    ? (summary.metrics.end_to_end_duration.p50 / 1000).toFixed(2)
    : 'N/A'} 초
  - P95: ${summary.metrics.end_to_end_duration
    ? (summary.metrics.end_to_end_duration.p95 / 1000).toFixed(2)
    : 'N/A'} 초
  - P99: ${summary.metrics.end_to_end_duration
    ? (summary.metrics.end_to_end_duration.p99 / 1000).toFixed(2)
    : 'N/A'} 초

API 응답 시간:
  - 평균: ${summary.metrics.http_req_duration.avg.toFixed(2)} ms
  - P50: ${summary.metrics.http_req_duration.p50.toFixed(2)} ms
  - P95: ${summary.metrics.http_req_duration.p95.toFixed(2)} ms
  - P99: ${summary.metrics.http_req_duration.p99.toFixed(2)} ms

에러 분포:
  - 토큰 발급 실패: ${summary.metrics.errors.token_errors}
  - 대기열 오류: ${summary.metrics.errors.queue_errors}
  - 예약 실패: ${summary.metrics.errors.reservation_errors}
  - 결제 실패: ${summary.metrics.errors.payment_errors}

시스템 안정성: ${summary.metrics.error_rate && summary.metrics.error_rate < 0.001
  ? '✓ 안정적 (에러율 < 0.1%)'
  : '✗ 불안정 (에러율 초과)'}
  `;
}
