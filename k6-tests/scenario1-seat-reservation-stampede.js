/**
 * 시나리오 1: 좌석 예약 집중 부하 (Seat Reservation Stampede)
 *
 * 목적: 분산락 경합 상황에서의 성능 및 정합성 검증
 * - 동시 사용자 수: 10,000명
 * - 대상 좌석 수: 50석 (경합률 200:1)
 * - 테스트 시간: 5분
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭 정의
const lockAcquisitionTime = new Trend('lock_acquisition_time');
const reservationSuccessRate = new Rate('reservation_success_rate');
const reservationFailures = new Counter('reservation_failures');
const lockTimeouts = new Counter('lock_timeouts');

// 테스트 설정
export const options = {
  stages: [
    { duration: '30s', target: 1000 },   // 30초 동안 1,000명까지 증가
    { duration: '30s', target: 5000 },   // 30초 동안 5,000명까지 증가
    { duration: '1m', target: 10000 },   // 1분 동안 10,000명까지 증가
    { duration: '3m', target: 10000 },   // 3분 동안 10,000명 유지
    { duration: '30s', target: 0 },      // 30초 동안 종료
  ],
  thresholds: {
    'http_req_duration{name:reservation}': ['p(95)<500', 'p(99)<1000'], // 예약 요청 응답 시간
    'http_req_failed': ['rate<0.01'],                                    // 전체 에러율 < 1%
    'lock_acquisition_time': ['p(95)<3000', 'p(99)<5000'],              // 락 획득 시간
    'reservation_success_rate': ['rate>0.004'],                         // 성공률 > 0.4% (최소 40/10000)
  },
  // Prometheus 연동을 위한 설정 (k6 실행 시 --out prometheus 옵션 사용)
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_ID = 1;
const SCHEDULE_ID = 1;
const TOTAL_SEATS = 50; // 경합 대상 좌석 수

export default function () {
  const userId = __VU; // 각 VU마다 고유한 userId 사용 (1, 2, 3, ...)

  group('좌석 예약 집중 부하', () => {
    // 1. 대기열 토큰 발급
    const tokenResponse = issueQueueToken(userId);
    if (!tokenResponse) return;

    const token = tokenResponse.token;

    // 2. 대기열 상태 폴링 (ACTIVE 될 때까지)
    if (!waitForActiveStatus(token)) return;

    // 3. 콘서트 일정 조회
    getConcertSchedules(token);

    // 4. 좌석 목록 조회
    getAvailableSeats(token);

    // 5. 좌석 예약 시도 (랜덤 좌석 선택으로 경합 발생)
    const seatId = Math.floor(Math.random() * TOTAL_SEATS) + 1;
    const startTime = Date.now();
    attemptReservation(token, seatId);
    const elapsedTime = Date.now() - startTime;
    lockAcquisitionTime.add(elapsedTime);
  });

  sleep(1);
}

/**
 * 대기열 토큰 발급
 */
function issueQueueToken(userId) {
  const response = http.post(
    `${BASE_URL}/api/v1/queue/token`,
    JSON.stringify({ userId: userId }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'issue_token' },
    }
  );

  const success = check(response, {
    'token issued successfully': (r) => r.status === 200 || r.status === 201,
  });

  if (!success) {
    console.error(`Failed to issue token: ${response.status}`);
    return null;
  }

  return response.json();
}

/**
 * 대기열 상태 폴링 (ACTIVE 상태가 될 때까지)
 */
function waitForActiveStatus(token) {
  const maxRetries = 30; // 최대 60초 대기 (2초 * 30회)
  let retries = 0;

  while (retries < maxRetries) {
    const response = http.get(
      `${BASE_URL}/api/v1/queue/status`,
      {
        headers: { 'X-Queue-Token': token },
        tags: { name: 'queue_status' },
      }
    );

    const success = check(response, {
      'queue status retrieved': (r) => r.status === 200,
    });

    if (success) {
      const status = response.json('status');
      if (status === 'ACTIVE') {
        return true;
      }
    }

    sleep(2);
    retries++;
  }

  console.error('Queue activation timeout');
  return false;
}

/**
 * 콘서트 일정 조회
 */
function getConcertSchedules(token) {
  const response = http.get(
    `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/schedules`,
    {
      headers: { 'X-Queue-Token': token },
      tags: { name: 'get_schedules' },
    }
  );

  check(response, {
    'schedules retrieved': (r) => r.status === 200,
  });
}

/**
 * 좌석 목록 조회
 */
function getAvailableSeats(token) {
  const response = http.get(
    `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/schedules/${SCHEDULE_ID}/seats`,
    {
      headers: { 'X-Queue-Token': token },
      tags: { name: 'get_seats' },
    }
  );

  check(response, {
    'seats retrieved': (r) => r.status === 200,
  });
}

/**
 * 좌석 예약 시도
 */
function attemptReservation(token, seatId) {
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
      tags: { name: 'reservation' },
    }
  );

  const checks = check(response, {
    'reservation succeeded': (r) => r.status === 200,
    'seat already reserved (expected)': (r) => r.status === 409 || r.status === 400,
    'lock timeout': (r) => r.status === 408 || r.status === 503,
  });

  // 메트릭 수집
  if (response.status === 200) {
    reservationSuccessRate.add(1);
  } else {
    reservationSuccessRate.add(0);
    reservationFailures.add(1);

    if (response.status === 408 || response.status === 503) {
      lockTimeouts.add(1);
    }
  }
}

export function handleSummary(data) {
  return {
    'summary-scenario1.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';
  const enableColors = options.enableColors || false;

  let summary = `
${indent}시나리오 1: 좌석 예약 집중 부하 테스트 결과
${indent}==========================================
${indent}
${indent}총 요청 수: ${data.metrics.http_reqs.values.count}
${indent}성공한 예약: ${data.metrics.reservation_success_rate ? (data.metrics.reservation_success_rate.values.rate * data.metrics.http_reqs.values.count).toFixed(0) : 'N/A'}
${indent}예약 실패: ${data.metrics.reservation_failures ? data.metrics.reservation_failures.values.count : 'N/A'}
${indent}락 타임아웃: ${data.metrics.lock_timeouts ? data.metrics.lock_timeouts.values.count : 'N/A'}
${indent}
${indent}응답 시간 (예약 요청):
${indent}  - P50: ${data.metrics['http_req_duration{name:reservation}'] ? data.metrics['http_req_duration{name:reservation}'].values['p(50)'].toFixed(2) : 'N/A'} ms
${indent}  - P95: ${data.metrics['http_req_duration{name:reservation}'] ? data.metrics['http_req_duration{name:reservation}'].values['p(95)'].toFixed(2) : 'N/A'} ms
${indent}  - P99: ${data.metrics['http_req_duration{name:reservation}'] ? data.metrics['http_req_duration{name:reservation}'].values['p(99)'].toFixed(2) : 'N/A'} ms
${indent}
${indent}락 획득 시간:
${indent}  - P50: ${data.metrics.lock_acquisition_time ? data.metrics.lock_acquisition_time.values['p(50)'].toFixed(2) : 'N/A'} ms
${indent}  - P95: ${data.metrics.lock_acquisition_time ? data.metrics.lock_acquisition_time.values['p(95)'].toFixed(2) : 'N/A'} ms
${indent}  - P99: ${data.metrics.lock_acquisition_time ? data.metrics.lock_acquisition_time.values['p(99)'].toFixed(2) : 'N/A'} ms
  `;

  return summary;
}
