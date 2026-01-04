# k6 부하 테스트 가이드

콘서트 예약 시스템의 부하 테스트를 위한 k6 스크립트 모음입니다.

## 목차
- [k6 설치](#k6-설치)
- [사전 준비](#사전-준비)
- [테스트 시나리오](#테스트-시나리오)
- [실행 방법](#실행-방법)
- [결과 분석](#결과-분석)
- [Prometheus/Grafana 연동](#prometheusgrafana-연동)

---

## k6 설치

### macOS
```bash
brew install k6
```

### Linux
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### Docker
```bash
docker pull grafana/k6:latest
```

설치 확인:
```bash
k6 version
```

---

## 사전 준비

### 1. 시스템 실행

```bash
# 1. Docker Compose로 인프라 실행
docker-compose up -d mysql redis prometheus grafana

# Kafka도 함께 실행 (시나리오 4용)
docker-compose -f docker-compose.kafka.yml up -d

# 2. Spring Boot 애플리케이션 실행
./gradlew bootRun

# 3. 시스템 상태 확인
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

### 2. 테스트 데이터 준비

부하 테스트 전에 다음 데이터가 필요합니다:
- 콘서트 정보 (ID: 1)
- 콘서트 일정 (ID: 1)
- 좌석 (최소 100석 이상)

`data.sql` 파일이 자동으로 로드되는지 확인하세요.

### 3. 환경 변수 설정 (옵션)

```bash
export BASE_URL=http://localhost:8080
```

---

## 테스트 시나리오

### 시나리오 1: 좌석 예약 집중 부하 (Seat Reservation Stampede)
**목적**: 분산락 경합 상황에서의 성능 및 정합성 검증

- **동시 사용자**: 10,000명
- **대상 좌석**: 50석 (경합률 200:1)
- **테스트 시간**: 5분
- **예상 병목**: Redisson 분산락, MySQL `SELECT FOR UPDATE`

**주요 측정 지표**:
- 분산락 획득 시간 (P50, P95, P99)
- 예약 성공률
- 락 타임아웃 발생 빈도

---

### 시나리오 2: 대기열 활성화 웨이브 (Queue Activation Wave)
**목적**: 대기열 시스템의 처리량 및 Redis 성능 측정

- **초기 대기**: 10,000명
- **폴링 간격**: 2초
- **테스트 시간**: 5분
- **예상 병목**: Redis Sorted Set 연산, 네트워크 대역폭

**주요 측정 지표**:
- 대기열 진입 처리량 (req/sec)
- 활성화 소요 시간 (P95, P99)
- 폴링 응답 시간

---

### 시나리오 3: 동시 결제 처리 (Concurrent Payment Processing)
**목적**: 결제 트랜잭션 동시성 제어 및 포인트 차감 정합성 검증

- **동시 결제**: 1,000건/초
- **테스트 시간**: 5분
- **예상 병목**: MySQL 커넥션 풀, POINT 테이블 행 잠금

**주요 측정 지표**:
- 결제 처리 응답 시간 (P50, P95, P99)
- DB 데드락 발생 빈도
- 커넥션 풀 고갈 여부

---

### 시나리오 5: 지속적 부하 테스트 (Sustained Load Test)
**목적**: 장시간 운영 시 시스템 안정성 및 리소스 누수 확인

- **목표 부하**: 1,000 req/sec
- **테스트 시간**: 10분
- **시나리오**: 완전한 사용자 여정 (대기열 → 예약 → 결제)

**주요 측정 지표**:
- End-to-End 사용자 여정 시간
- 에러율 (목표: < 0.1%)
- 메모리/CPU 사용률

---

## 실행 방법

### 기본 실행

```bash
# 시나리오 1 실행
k6 run k6-tests/scenario1-seat-reservation-stampede.js

# 시나리오 2 실행
k6 run k6-tests/scenario2-queue-activation-wave.js

# 시나리오 3 실행
k6 run k6-tests/scenario3-concurrent-payment.js

# 시나리오 5 실행
k6 run k6-tests/scenario5-sustained-load.js
```

### 옵션과 함께 실행

```bash
# 커스텀 BASE_URL 설정
k6 run -e BASE_URL=http://production-server:8080 scenario1-seat-reservation-stampede.js

# VU 수 조정
k6 run --vus 5000 --duration 3m scenario1-seat-reservation-stampede.js

# 결과를 JSON 파일로 저장
k6 run --out json=results.json scenario1-seat-reservation-stampede.js

# 실시간 웹 대시보드로 확인
k6 run --out web scenario1-seat-reservation-stampede.js
```

### Docker로 실행

```bash
docker run --rm -i --network=host \
  -v $(pwd)/k6-tests:/scripts \
  grafana/k6:latest run /scripts/scenario1-seat-reservation-stampede.js
```

---

## 결과 분석

### 1. 콘솔 출력

테스트 실행 중 실시간 메트릭이 콘솔에 출력됩니다:

```
scenarios: (100.00%) 1 scenario, 10000 max VUs, 5m30s max duration
✓ token issued successfully
✓ reservation succeeded

checks.........................: 95.23% ✓ 95230   ✗ 4770
data_received..................: 234 MB 780 kB/s
data_sent......................: 78 MB  260 kB/s
http_req_duration..............: avg=234ms min=12ms med=189ms max=4.5s p(90)=387ms p(95)=456ms
http_reqs......................: 520314 1734/s
lock_acquisition_time..........: avg=142ms min=5ms med=98ms max=3.2s p(90)=287ms p(95)=456ms
reservation_success_rate.......: 0.48%  ✓ 50     ✗ 9950
```

### 2. JSON 결과 파일

각 시나리오는 `summary-scenarioN.json` 파일을 생성합니다:

```bash
cat summary-scenario1.json | jq '.metrics.reservation_success_rate'
```

### 3. 성공 기준 판단

각 시나리오의 `thresholds` 섹션을 확인하세요:

```javascript
thresholds: {
  'http_req_duration{name:reservation}': ['p(95)<500', 'p(99)<1000'],
  'reservation_success_rate': ['rate>0.004'],
}
```

테스트 종료 시 모든 threshold가 통과하면 ✓, 실패하면 ✗로 표시됩니다.

---

## Prometheus/Grafana 연동

### 1. Prometheus로 메트릭 전송

k6는 실시간으로 Prometheus에 메트릭을 전송할 수 있습니다:

```bash
# k6 Prometheus Remote Write Extension 사용
k6 run --out experimental-prometheus-rw scenario1-seat-reservation-stampede.js
```

또는 StatsD를 통한 전송:

```bash
k6 run --out statsd scenario1-seat-reservation-stampede.js
```

### 2. Grafana 대시보드 설정

1. Grafana 접속: http://localhost:3000 (admin/admin)

2. Data Source 추가:
   - Configuration > Data Sources > Add data source
   - Prometheus 선택
   - URL: `http://prometheus:9090`
   - Save & Test

3. k6 대시보드 Import:
   - Dashboard > Import
   - Dashboard ID: **2587** (k6 Load Testing Results)
   - 또는 **19665** (k6 Prometheus)

4. Spring Boot 대시보드 Import (시스템 모니터링):
   - Dashboard ID: **11378** (JVM Micrometer)
   - 또는 **4701** (Spring Boot Statistics)

### 3. 실시간 모니터링

테스트 실행 중 다음을 동시에 모니터링:

**k6 메트릭** (부하):
- HTTP 요청 수 (req/sec)
- 응답 시간 (P95, P99)
- 에러율

**Spring Boot 메트릭** (시스템):
- JVM 메모리 사용량
- GC 시간
- HikariCP 커넥션 풀 사용률
- HTTP 요청 처리 시간

**인프라 메트릭**:
- CPU/메모리 사용률
- Redis 처리량
- MySQL 쿼리 시간

---

## 테스트 순서 권장사항

부하 테스트는 다음 순서로 진행하는 것을 권장합니다:

1. **시나리오 5** (지속적 부하): 시스템 전반적인 안정성 확인
2. **시나리오 1** (좌석 예약 집중): 분산락 성능 측정
3. **시나리오 2** (대기열 활성화): Redis 성능 측정
4. **시나리오 3** (동시 결제): 트랜잭션 정합성 검증

각 테스트 사이에는 **최소 5분 이상** 대기하여 시스템이 안정화되도록 합니다.

---

## 문제 해결

### 테스트 실패 시 체크리스트

1. **시스템이 정상 작동하는가?**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **테스트 데이터가 준비되었는가?**
   ```bash
   # MySQL 접속
   docker exec -it <mysql-container> mysql -u application -p hhplus
   SELECT COUNT(*) FROM seat WHERE schedule_id = 1;
   ```

3. **Redis가 정상 작동하는가?**
   ```bash
   docker exec -it <redis-container> redis-cli ping
   ```

4. **커넥션 풀이 부족한가?**
   - `application.yml`에서 `hikari.maximum-pool-size` 증가 (20 → 50)

5. **메모리가 부족한가?**
   - JVM 힙 메모리 증가: `JAVA_OPTS=-Xmx2g ./gradlew bootRun`

---

## 추가 리소스

- [k6 공식 문서](https://k6.io/docs/)
- [k6 예제 모음](https://github.com/grafana/k6-learn)
- [Prometheus 쿼리 가이드](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Grafana 대시보드 갤러리](https://grafana.com/grafana/dashboards/)

---

## 라이센스

이 테스트 스크립트는 프로젝트의 라이센스를 따릅니다.
