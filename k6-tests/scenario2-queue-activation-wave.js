/**
 * 시나리오 2: 대기열 활성화 웨이브 (Queue Activation Wave)
 *
 * 목적: 대기열 시스템의 처리량 및 Redis Sorted Set 성능 측정
 * - 초기 대기 사용자: 10,000명
 * - 활성화 목표: 30초 내 1,000명 → 10,000명 전환
 * - 폴링 간격: 2초
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// 커스텀 메트릭
const queueEntryRate = new Rate('queue_entry_success_rate');
const queueActivationTime = new Trend('queue_activation_time');
const activeTokensCount = new Gauge('active_tokens_count');
const pollingResponseTime = new Trend('polling_response_time');
const redisOperationTime = new Trend('redis_operation_time');

export const options = {
  scenarios: {
    // 시나리오: 10,000명이 동시에 대기열에 진입
    queue_entry: {
      executor: 'constant-vus',
      vus: 10000,
      duration: '30s', // 30초 동안 10,000명 유지하며 토큰 발급
      exec: 'queueEntry',
    },
    // 시나리오: 대기열 상태 폴링 (2초마다)
    queue_polling: {
      executor: 'constant-vus',
      vus: 10000,
      duration: '5m', // 5분 동안 지속적으로 폴링
      startTime: '31s', // 토큰 발급 후 시작
      exec: 'queuePolling',
    },
  },
  thresholds: {
    'http_req_duration{name:issue_token}': ['p(95)<200', 'p(99)<500'],
    'http_req_duration{name:queue_status}': ['p(95)<100', 'p(99)<200'],
    'queue_entry_success_rate': ['rate>0.99'], // 99% 이상 성공
    'queue_activation_time': ['p(95)<60000'],   // P95: 60초 이내 활성화
    'http_req_failed': ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 전역 변수로 토큰 저장 (VU별로 독립적)
let userToken = null;
let tokenIssuedAt = null;
let activatedAt = null;

/**
 * 대기열 진입 시나리오
 */
export function queueEntry() {
  const userId = `user_${__VU}`;

  group('대기열 토큰 발급', () => {
    const startTime = Date.now();

    const response = http.post(
      `${BASE_URL}/api/v1/queue/token`,
      JSON.stringify({ userId: userId }),
      {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'issue_token' },
      }
    );

    const success = check(response, {
      'token issued successfully': (r) => r.status === 200,
      'response time < 500ms': (r) => r.timings.duration < 500,
    });

    if (success) {
      queueEntryRate.add(1);
      const responseData = response.json();
      userToken = responseData.token;
      tokenIssuedAt = Date.now();

      console.log(`[VU ${__VU}] Token issued: ${userToken}`);
    } else {
      queueEntryRate.add(0);
      console.error(`[VU ${__VU}] Failed to issue token: ${response.status}`);
    }

    const elapsedTime = Date.now() - startTime;
    redisOperationTime.add(elapsedTime);
  });
}

/**
 * 대기열 폴링 시나리오
 */
export function queuePolling() {
  if (!userToken) {
    console.error(`[VU ${__VU}] No token available for polling`);
    return;
  }

  // 이미 활성화된 경우 폴링 중지
  if (activatedAt) {
    sleep(10);
    return;
  }

  group('대기열 상태 폴링', () => {
    const startTime = Date.now();

    const response = http.get(
      `${BASE_URL}/api/v1/queue/status`,
      {
        headers: { 'X-Queue-Token': userToken },
        tags: { name: 'queue_status' },
      }
    );

    const elapsedTime = Date.now() - startTime;
    pollingResponseTime.add(elapsedTime);

    const success = check(response, {
      'status retrieved successfully': (r) => r.status === 200,
      'response time < 200ms': (r) => r.timings.duration < 200,
    });

    if (success) {
      const responseData = response.json();
      const status = responseData.status;
      const position = responseData.position;

      if (status === 'ACTIVE' && !activatedAt) {
        activatedAt = Date.now();
        const activationTime = activatedAt - tokenIssuedAt;
        queueActivationTime.add(activationTime);
        activeTokensCount.add(1);

        console.log(
          `[VU ${__VU}] Activated! Waited ${(activationTime / 1000).toFixed(2)}s`
        );
      } else if (status === 'WAITING') {
        console.log(`[VU ${__VU}] Still waiting... Position: ${position || 'unknown'}`);
      }
    }
  });

  sleep(2); // 2초마다 폴링
}

export function handleSummary(data) {
  const totalRequests = data.metrics.http_reqs.values.count;
  const tokenIssueRequests = data.metrics['http_reqs{name:issue_token}']
    ? data.metrics['http_reqs{name:issue_token}'].values.count
    : 0;
  const pollingRequests = data.metrics['http_reqs{name:queue_status}']
    ? data.metrics['http_reqs{name:queue_status}'].values.count
    : 0;

  const summary = {
    scenario: 'Queue Activation Wave',
    total_requests: totalRequests,
    token_issue_requests: tokenIssueRequests,
    polling_requests: pollingRequests,
    metrics: {
      queue_entry_success_rate: data.metrics.queue_entry_success_rate
        ? data.metrics.queue_entry_success_rate.values.rate
        : null,
      queue_activation_time: data.metrics.queue_activation_time
        ? {
            p50: data.metrics.queue_activation_time.values['p(50)'],
            p95: data.metrics.queue_activation_time.values['p(95)'],
            p99: data.metrics.queue_activation_time.values['p(99)'],
            max: data.metrics.queue_activation_time.values.max,
          }
        : null,
      polling_response_time: data.metrics.polling_response_time
        ? {
            p50: data.metrics.polling_response_time.values['p(50)'],
            p95: data.metrics.polling_response_time.values['p(95)'],
            p99: data.metrics.polling_response_time.values['p(99)'],
          }
        : null,
      redis_operation_time: data.metrics.redis_operation_time
        ? {
            p50: data.metrics.redis_operation_time.values['p(50)'],
            p95: data.metrics.redis_operation_time.values['p(95)'],
            p99: data.metrics.redis_operation_time.values['p(99)'],
          }
        : null,
    },
  };

  return {
    'summary-scenario2.json': JSON.stringify(summary, null, 2),
    stdout: createTextSummary(summary),
  };
}

function createTextSummary(summary) {
  return `
시나리오 2: 대기열 활성화 웨이브 테스트 결과
============================================

총 요청 수: ${summary.total_requests}
  - 토큰 발급 요청: ${summary.token_issue_requests}
  - 폴링 요청: ${summary.polling_requests}

대기열 진입 성공률: ${summary.metrics.queue_entry_success_rate
  ? (summary.metrics.queue_entry_success_rate * 100).toFixed(2)
  : 'N/A'}%

대기열 활성화 시간:
  - P50: ${summary.metrics.queue_activation_time
    ? (summary.metrics.queue_activation_time.p50 / 1000).toFixed(2)
    : 'N/A'} 초
  - P95: ${summary.metrics.queue_activation_time
    ? (summary.metrics.queue_activation_time.p95 / 1000).toFixed(2)
    : 'N/A'} 초
  - P99: ${summary.metrics.queue_activation_time
    ? (summary.metrics.queue_activation_time.p99 / 1000).toFixed(2)
    : 'N/A'} 초
  - Max: ${summary.metrics.queue_activation_time
    ? (summary.metrics.queue_activation_time.max / 1000).toFixed(2)
    : 'N/A'} 초

폴링 응답 시간:
  - P50: ${summary.metrics.polling_response_time
    ? summary.metrics.polling_response_time.p50.toFixed(2)
    : 'N/A'} ms
  - P95: ${summary.metrics.polling_response_time
    ? summary.metrics.polling_response_time.p95.toFixed(2)
    : 'N/A'} ms
  - P99: ${summary.metrics.polling_response_time
    ? summary.metrics.polling_response_time.p99.toFixed(2)
    : 'N/A'} ms

Redis 연산 시간:
  - P50: ${summary.metrics.redis_operation_time
    ? summary.metrics.redis_operation_time.p50.toFixed(2)
    : 'N/A'} ms
  - P95: ${summary.metrics.redis_operation_time
    ? summary.metrics.redis_operation_time.p95.toFixed(2)
    : 'N/A'} ms
  - P99: ${summary.metrics.redis_operation_time
    ? summary.metrics.redis_operation_time.p99.toFixed(2)
    : 'N/A'} ms
  `;
}
