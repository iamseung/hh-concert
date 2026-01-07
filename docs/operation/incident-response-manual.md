# 장애 대응 메뉴얼 (Incident Response Manual)

**최종 업데이트**: 2026-01-06
**버전**: 1.0
**담당**: DevOps팀, 개발팀

---

## 📋 목차

1. [장애 레벨 정의](#1-장애-레벨-정의)
2. [장애 대응 프로세스](#2-장애-대응-프로세스)
3. [주요 장애 시나리오별 대응](#3-주요-장애-시나리오별-대응)
4. [모니터링 및 알람](#4-모니터링-및-알람)
5. [롤백/복구 절차](#5-롤백복구-절차)
6. [긴급 연락망](#6-긴급-연락망)
7. [사후 분석](#7-사후-분석)

---

## 1. 장애 레벨 정의

### Severity 분류

| 레벨 | 정의 | 대응 시간 | 예시 |
|------|------|----------|------|
| **P0 (Critical)** | 서비스 완전 중단 | **즉시** (5분 이내) | - 애플리케이션 다운<br>- DB 전체 장애<br>- Redis 클러스터 장애 |
| **P1 (High)** | 핵심 기능 장애 | **15분 이내** | - 결제 불가<br>- 예약 불가<br>- 로그인 불가 |
| **P2 (Medium)** | 일부 기능 장애 | **1시간 이내** | - 대기열 지연<br>- 알림 미발송<br>- 성능 저하 (50%) |
| **P3 (Low)** | 경미한 이슈 | **4시간 이내** | - UI 버그<br>- 로그 누락<br>- 성능 저하 (20%) |

---

## 2. 장애 대응 프로세스

### 2.1 초기 대응 (First Response)

```
1. 장애 감지 (1분 이내)
   └─ 모니터링 알람 또는 고객 신고

2. 상황 파악 (3분 이내)
   ├─ 영향 범위 확인 (전체/일부)
   ├─ 재현 가능 여부
   └─ 원인 추정

3. 긴급 공지 (5분 이내)
   ├─ 사용자 공지 (장애 인지)
   └─ 내부 공유 (Slack)

4. 임시 조치 (10분 이내)
   ├─ 서비스 격리 (영향 최소화)
   └─ 트래픽 제한
```

### 2.2 장애 대응 체크리스트

**즉시 확인 사항**
- [ ] 애플리케이션 상태 (Health Check)
- [ ] 데이터베이스 연결 상태
- [ ] Redis 연결 상태
- [ ] Kafka 상태
- [ ] 최근 배포 이력 (30분 이내)
- [ ] 에러 로그 (최근 10분)
- [ ] Grafana 대시보드

**대응 우선순위**
1. 사용자 영향 최소화 (트래픽 제한, 장애 격리)
2. 원인 파악 및 임시 조치
3. 근본 원인 해결
4. 복구 확인
5. 사후 분석

---

## 3. 주요 장애 시나리오별 대응

### 3.1 대기열 시스템 장애 (P1)

#### 현상
- 사용자가 WAITING 상태에서 무한 대기
- "Queue activation timeout" 에러
- 예약/결제 진행 불가

#### 원인 분석
```bash
# 1. 스케줄러 실행 여부 확인
kubectl logs -f deployment/hhplus-app | grep "QueueScheduler"

# 2. Redis 연결 확인
redis-cli ping
redis-cli ZCOUNT queue:waiting -inf +inf  # 대기 중인 사용자 수
redis-cli ZCOUNT queue:active -inf +inf   # 활성 사용자 수

# 3. 애플리케이션 로그 확인
tail -f /var/log/hhplus/application.log | grep "activateWaitingTokens"
```

#### 대응 절차

**즉시 조치**
```bash
# 1. 스케줄러 수동 실행 (임시)
curl -X POST http://localhost:8080/admin/queue/activate \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 2. 대기열 상태 강제 초기화 (최후 수단)
redis-cli DEL queue:waiting queue:active
```

**근본 원인 해결**
1. QueueScheduler.activateWaitingTokens() 로직 디버깅
2. Redis Lua 스크립트 검증 (`activate_waiting_users.lua`)
3. 스케줄러 설정 확인 (`@EnableScheduling`)
4. 애플리케이션 재시작 (필요 시)

**복구 확인**
- [ ] 스케줄러 로그 출력 확인
- [ ] WAITING → ACTIVE 전환 확인
- [ ] 예약 플로우 E2E 테스트

#### 예방 조치
- 스케줄러 실행 모니터링 (Heartbeat)
- 대기 시간 알람 (10분 초과 시)
- 자동 복구 스크립트 준비

---

### 3.2 데이터베이스 장애 (P0)

#### 현상
- "Connection timeout" 에러
- "Too many connections" 에러
- 모든 API 응답 지연/실패

#### 원인 분석
```bash
# 1. MySQL 상태 확인
mysql -u root -p -e "SHOW PROCESSLIST;"
mysql -u root -p -e "SHOW STATUS LIKE 'Threads_connected';"

# 2. 커넥션 풀 상태
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active

# 3. Slow Query 확인
mysql -u root -p -e "SELECT * FROM information_schema.PROCESSLIST WHERE TIME > 5;"
```

#### 대응 절차

**즉시 조치**
```bash
# 1. 커넥션 풀 확장 (application.yml)
# maximum-pool-size: 20 → 50

# 2. 장시간 실행 쿼리 종료
mysql -u root -p -e "KILL <PROCESS_ID>;"

# 3. 읽기 전용 모드 전환 (쓰기 차단)
mysql -u root -p -e "SET GLOBAL read_only = ON;"
```

**롤백 시나리오**
```bash
# 1. 애플리케이션 중단
kubectl scale deployment/hhplus-app --replicas=0

# 2. DB 백업에서 복구
mysql -u root -p hhplus < /backup/hhplus_20260106.sql

# 3. 애플리케이션 재시작
kubectl scale deployment/hhplus-app --replicas=3
```

#### 예방 조치
- 커넥션 풀 사용률 모니터링 (70% 이상 알람)
- Slow Query 로그 분석 (주 1회)
- 인덱스 최적화
- 정기 백업 (일 1회, 시간별 증분)

---

### 3.3 Redis 장애 (P0)

#### 현상
- "Connection refused" 에러
- 대기열 시스템 전체 중단
- 캐시 Miss로 인한 DB 부하 급증
- 분산락 획득 실패

#### 원인 분석
```bash
# 1. Redis 상태 확인
redis-cli ping
redis-cli INFO server
redis-cli INFO memory

# 2. 메모리 사용률 확인
redis-cli INFO stats | grep used_memory

# 3. Slow Log 확인
redis-cli SLOWLOG GET 10
```

#### 대응 절차

**즉시 조치 (Redis 다운)**
```bash
# 1. Redis 재시작
docker restart redis
# 또는
systemctl restart redis

# 2. 애플리케이션 재시작 (커넥션 재연결)
kubectl rollout restart deployment/hhplus-app
```

**메모리 부족 시**
```bash
# 1. 메모리 확보
redis-cli FLUSHDB  # ⚠️ 주의: 모든 데이터 삭제

# 2. 특정 키 패턴만 삭제
redis-cli --scan --pattern "cache:*" | xargs redis-cli DEL

# 3. TTL 정책 조정
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

**Degraded Mode (Redis 없이 운영)**
```yaml
# application.yml - 캐시 비활성화
spring:
  cache:
    type: none
```

**주의**: 대기열, 분산락 기능 사용 불가 → 서비스 제한 필요

#### 예방 조치
- Redis 메모리 사용률 모니터링 (80% 이상 알람)
- Redis Persistence 설정 (AOF 또는 RDB)
- Redis Sentinel/Cluster 구성 (HA)

---

### 3.4 Kafka 장애 (P2)

#### 현상
- "Broker not available" 에러
- Consumer Lag 급증
- 알림 미발송
- DLQ 메시지 누적

#### 원인 분석
```bash
# 1. Kafka 브로커 상태
kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# 2. Consumer Lag 확인
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group hhplus-reservation-consumer --describe

# 3. 토픽 상태 확인
kafka-topics.sh --bootstrap-server localhost:9092 --list
```

#### 대응 절차

**즉시 조치**
```bash
# 1. Kafka 재시작
docker-compose restart kafka1 kafka2 kafka3

# 2. Consumer 재시작
kubectl rollout restart deployment/hhplus-app

# 3. DLQ 메시지 재처리 (수동)
# ReservationKafkaConsumer에서 DLQ 토픽 구독 후 재처리
```

**Consumer Lag 해소**
```bash
# 1. Consumer 인스턴스 증가 (수평 확장)
kubectl scale deployment/hhplus-app --replicas=5

# 2. 배치 크기 증가
# application.yml
# max.poll.records: 100 → 500
```

**영향 범위**
- ✅ 예약/결제 플로우: 정상 (Kafka 비동기)
- ⚠️ 알림 발송: 지연
- ⚠️ 데이터 플랫폼 전송: 지연

#### 예방 조치
- Consumer Lag 모니터링 (1,000건 이상 알람)
- DLQ 메시지 수 모니터링
- Kafka 클러스터 헬스 체크

---

### 3.5 결제 시스템 장애 (P1)

#### 현상
- 결제 처리 실패
- "포인트 부족" 에러 (실제 잔액 충분)
- 중복 결제 발생

#### 원인 분석
```bash
# 1. 결제 에러 로그
kubectl logs -f deployment/hhplus-app | grep "ProcessPaymentUseCase"

# 2. 분산락 상태 확인
redis-cli KEYS "reservation:payment:lock:*"

# 3. DB 트랜잭션 확인
mysql -u root -p -e "SELECT * FROM payment WHERE status='PENDING' ORDER BY created_at DESC LIMIT 10;"
```

#### 대응 절차

**즉시 조치**
```bash
# 1. 분산락 해제 (장시간 점유 시)
redis-cli DEL reservation:payment:lock:{reservationId}

# 2. PENDING 상태 결제 수동 처리
# - 포인트 차감 확인
# - 좌석 상태 확인
# - 필요 시 보상 트랜잭션 실행
```

**중복 결제 방지**
```sql
-- 1. 중복 결제 확인
SELECT reservation_id, COUNT(*) as cnt
FROM payment
GROUP BY reservation_id
HAVING cnt > 1;

-- 2. 중복 결제 취소 (수동)
UPDATE payment SET status='CANCELLED' WHERE id IN (...);

-- 3. 포인트 환불
UPDATE point SET balance = balance + {amount} WHERE user_id = {userId};
INSERT INTO point_history (user_id, amount, type) VALUES ({userId}, {amount}, 'REFUND');
```

#### 예방 조치
- 결제 멱등성 키 검증 강화
- 분산락 lease 시간 모니터링
- 결제 상태 알람 (PENDING 5분 초과 시)

---

### 3.6 외부 API 장애 (P2)

#### 현상
- 데이터 플랫폼 전송 실패
- Discord 알림 미발송
- Resilience4j 서킷브레이커 OPEN

#### 원인 분석
```bash
# 1. 서킷브레이커 상태
curl http://localhost:8080/actuator/circuitbreakers

# 2. 외부 API 응답 확인
curl -X POST https://external-api.com/reservations \
  -H "Content-Type: application/json" \
  -d '{"reservationId": "test"}'

# 3. Kafka DLQ 메시지 확인
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic reservation.confirmed.dlq --from-beginning
```

#### 대응 절차

**즉시 조치**
```bash
# 1. 서킷브레이커 수동 CLOSE (임시)
# ⚠️ 외부 API 복구 확인 후에만 실행

# 2. DLQ 메시지 수동 재처리
# - 외부 API 복구 후
# - Kafka Consumer에서 DLQ 토픽 구독
# - 배치 단위로 재전송
```

**영향 범위**
- ✅ 예약/결제 플로우: 정상 (비동기)
- ⚠️ 데이터 플랫폼 동기화: 지연
- ⚠️ 사용자 알림: 미발송

**Degraded Mode**
```yaml
# application.yml - 외부 API 비활성화
resilience4j:
  circuitbreaker:
    instances:
      dataPlatform:
        register-health-indicator: false
```

#### 예방 조치
- 외부 API 헬스 체크 (5분마다)
- 서킷브레이커 상태 모니터링
- DLQ 재처리 자동화 스크립트

---

### 3.7 애플리케이션 메모리 부족 (P1)

#### 현상
- OutOfMemoryError
- 애플리케이션 재시작 반복
- 응답 지연 심화

#### 원인 분석
```bash
# 1. 힙 메모리 사용률
kubectl top pod -l app=hhplus-app

# 2. 힙 덤프 생성
kubectl exec -it hhplus-app-xxx -- jmap -dump:format=b,file=/tmp/heap.hprof 1

# 3. GC 로그 확인
kubectl logs -f hhplus-app-xxx | grep "GC"
```

#### 대응 절차

**즉시 조치**
```bash
# 1. 메모리 제한 증가 (임시)
kubectl set resources deployment/hhplus-app \
  --limits=memory=4Gi --requests=memory=2Gi

# 2. 인스턴스 증가 (부하 분산)
kubectl scale deployment/hhplus-app --replicas=5

# 3. 애플리케이션 재시작
kubectl rollout restart deployment/hhplus-app
```

**근본 원인 분석**
- 힙 덤프 분석 (Eclipse MAT)
- 메모리 누수 탐지
- 대용량 객체 확인

#### 예방 조치
- 힙 메모리 사용률 모니터링 (80% 이상 알람)
- GC 시간 모니터링
- 정기 힙 덤프 분석 (월 1회)

---

## 4. 모니터링 및 알람

### 4.1 Grafana 대시보드

**URL**: http://localhost:3000

**주요 패널**
1. **시스템 개요**
   - HTTP 요청 처리량 (req/sec)
   - 응답 시간 (P50, P95, P99)
   - 에러율 (%)

2. **대기열 현황**
   - 대기 중인 사용자 수
   - 활성 사용자 수
   - 활성화 속도

3. **인프라 상태**
   - DB 커넥션 사용률
   - Redis 메모리 사용률
   - Kafka Consumer Lag

### 4.2 알람 설정

**Slack 알람**
- P0, P1: `#incident-critical`
- P2, P3: `#incident-warn`

**알람 임계값**
```yaml
# prometheus-alerts.yml
groups:
  - name: hhplus-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
        for: 1m
        labels:
          severity: P1
        annotations:
          summary: "높은 에러율 감지: {{ $value }}%"

      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        labels:
          severity: P1

      - alert: KafkaConsumerLagHigh
        expr: kafka_consumer_lag > 10000
        for: 5m
        labels:
          severity: P2
```

---

## 5. 롤백/복구 절차

### 5.1 애플리케이션 롤백

```bash
# 1. 이전 버전 확인
kubectl rollout history deployment/hhplus-app

# 2. 롤백 실행
kubectl rollout undo deployment/hhplus-app

# 3. 특정 버전으로 롤백
kubectl rollout undo deployment/hhplus-app --to-revision=5

# 4. 롤백 상태 확인
kubectl rollout status deployment/hhplus-app
```

### 5.2 데이터베이스 복구

```bash
# 1. 백업 파일 확인
ls -lh /backup/mysql/

# 2. DB 복구 (전체)
mysql -u root -p hhplus < /backup/hhplus_20260106_0300.sql

# 3. 특정 테이블만 복구
mysql -u root -p hhplus < /backup/payment_table_20260106.sql

# 4. Point-in-Time Recovery (바이너리 로그 사용)
mysqlbinlog --start-datetime="2026-01-06 03:00:00" \
            --stop-datetime="2026-01-06 03:05:00" \
            /var/log/mysql/mysql-bin.000001 | mysql -u root -p hhplus
```

### 5.3 Redis 복구

```bash
# 1. RDB 백업에서 복구
cp /backup/redis/dump.rdb /var/lib/redis/
redis-cli SHUTDOWN
systemctl start redis

# 2. AOF 백업에서 복구
cp /backup/redis/appendonly.aof /var/lib/redis/
redis-cli CONFIG SET appendonly yes
redis-cli BGREWRITEAOF
```

### 5.4 Feature Flag를 이용한 긴급 차단

```yaml
# application.yml
features:
  queue-system: false          # 대기열 비활성화
  payment-system: false        # 결제 비활성화
  notification-system: false   # 알림 비활성화
```

**적용 방법**
```bash
# 1. ConfigMap 업데이트
kubectl edit configmap hhplus-config

# 2. 애플리케이션 재시작 없이 적용 (Spring Cloud Config 사용 시)
curl -X POST http://localhost:8080/actuator/refresh
```

---

## 6. 긴급 연락망

### 6.1 담당자 연락처

| 역할 | 이름 | 연락처 | 백업 |
|------|------|--------|------|
| **On-Call Engineer** | - | - | - |
| **DevOps Lead** | - | - | - |
| **Backend Lead** | - | - | - |
| **DBA** | - | - | - |
| **PM/PO** | - | - | - |

### 6.2 에스컬레이션 절차

```
1차 대응 (0~15분)
└─ On-Call Engineer

2차 에스컬레이션 (15~30분)
└─ DevOps Lead + Backend Lead

3차 에스컬레이션 (30분~)
└─ CTO + PM/PO
```

### 6.3 외부 벤더 연락처

| 서비스 | 연락처 | 지원 시간 |
|--------|--------|----------|
| AWS Support | - | 24/7 |
| DataPlatform | - | 09:00~18:00 |

---

## 7. 사후 분석 (Post-Mortem)

### 7.1 Post-Mortem 템플릿

```markdown
# [P1] 대기열 시스템 장애 - 2026-01-06

## 개요
- **발생 일시**: 2026-01-06 14:30 ~ 15:45 (1시간 15분)
- **장애 레벨**: P1 (High)
- **영향 범위**: 전체 사용자 (약 10,000명)
- **담당자**: DevOps팀, Backend팀

## Timeline
- 14:30 - 장애 감지 (Grafana 알람)
- 14:35 - 원인 파악 시작
- 14:45 - 임시 조치 (스케줄러 수동 실행)
- 15:00 - 근본 원인 수정 (코드 배포)
- 15:15 - 복구 확인
- 15:45 - 장애 종료 선언

## 근본 원인 (Root Cause)
대기열 활성화 스케줄러가 Redis Lua 스크립트 실행 시 ...

## 영향 (Impact)
- 예약 불가: 100% (1시간 15분)
- 결제 불가: 100%
- 고객 문의: 약 500건

## 해결 방법 (Resolution)
1. 스케줄러 로직 수정
2. Redis 연산 검증 추가
3. 애플리케이션 재배포

## 재발 방지 (Prevention)
- [ ] 스케줄러 Heartbeat 모니터링 추가
- [ ] E2E 테스트에 대기열 시나리오 추가
- [ ] 대기 시간 알람 설정 (5분 초과 시)

## 교훈 (Lessons Learned)
...
```

### 7.2 정기 리뷰

**주기**: 분기별 (3개월)

**리뷰 항목**
- 장애 발생 빈도 및 패턴
- 평균 복구 시간 (MTTR)
- 알람 정확도
- 매뉴얼 개선 사항

---

**문서 관리**
- 장애 발생 시마다 매뉴얼 업데이트
- 새로운 시나리오 추가
- 연락처 정기 갱신 (월 1회)

**다음 업데이트**: 대기열 스케줄러 수정 후
