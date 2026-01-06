# 운영 문서 (Operation Documents)

콘서트 예약 시스템의 운영, 장애 대응, 성능 분석 관련 문서 모음입니다.

---

## 📚 문서 목록

### 1. [병목 지점 분석](./bottleneck-analysis.md)
**대상**: 개발팀, DevOps팀
**읽는 시간**: 10분

부하 테스트 결과 기반 시스템 병목 지점 분석
- 대기열 활성화 타임아웃 (CRITICAL)
- DB 커넥션 풀 부족
- Redis 분산락 대기
- Kafka Consumer 지연
- 성능 한계 예측 및 개선 방안

**주요 내용**
- 부하 테스트 결과 요약
- 병목 지점 상세 분석
- 권장 개선 사항 (단기/중기/장기)
- 모니터링 지표 및 임계값

---

### 2. [장애 대응 메뉴얼](./incident-response-manual.md) ⭐
**대상**: 전체 개발팀, DevOps팀, On-Call Engineer
**읽는 시간**: 15분

실무에서 사용 가능한 장애 대응 절차
- 장애 레벨 정의 (P0~P3)
- 7가지 주요 장애 시나리오별 대응
- 모니터링 및 알람 설정
- 롤백/복구 절차
- Post-Mortem 템플릿

**주요 시나리오**
1. 대기열 시스템 장애 (P1)
2. 데이터베이스 장애 (P0)
3. Redis 장애 (P0)
4. Kafka 장애 (P2)
5. 결제 시스템 장애 (P1)
6. 외부 API 장애 (P2)
7. 애플리케이션 메모리 부족 (P1)

**활용 방법**
- 장애 발생 시 해당 시나리오 섹션 참고
- 체크리스트 따라 순차 대응
- 복구 후 Post-Mortem 작성

---

### 3. [시스템 개요 발표 자료](./system-overview-presentation.md) ⭐
**대상**: 경영진, PM, 마케팅, CS, 전체 구성원
**읽는 시간**: 20분

비기술자도 이해 가능한 범용 시스템 소개 자료
- 시스템 아키텍처 (다이어그램 포함)
- 주요 기능 및 동작 방식
- 성능 및 처리 용량
- 보안
- 취약점 및 개선 계획
- Q&A (10개)

**주요 질문**
- 동시 몇 명까지 처리 가능한가?
- 대기 시간은 얼마나 되는가?
- 중복 결제는 어떻게 방지하는가?
- 장애 시 복구 시간은?
- 개인정보 보호는?

---

## 🎯 빠른 참조

### 현재 시스템 용량

| 지표 | 현재 | 목표 (1주 이내) | 목표 (3개월) |
|------|------|----------------|-------------|
| 동시 활성 사용자 | 100명 | 1,000명 | 10,000명 |
| 초당 처리량 | 2,081 req/sec | 20,000 req/sec | 50,000 req/sec |
| API 응답 시간 (P95) | 6.82ms | < 50ms | < 100ms |
| DB 커넥션 | 20개 | 50개 | 100개 |

### 긴급 상황 대응

| 상황 | 문서 | 페이지 |
|------|------|--------|
| 🔴 전체 서비스 다운 | 장애 대응 메뉴얼 | 섹션 3.2 (DB 장애) |
| 🔴 대기열 작동 안 함 | 장애 대응 메뉴얼 | 섹션 3.1 (대기열 장애) |
| 🟡 응답 속도 느림 | 병목 지점 분석 | 섹션 2 |
| 🟡 결제 실패 증가 | 장애 대응 메뉴얼 | 섹션 3.5 (결제 장애) |

### 주요 모니터링 URL

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Actuator**: http://localhost:8080/actuator
- **Swagger**: http://localhost:8080/swagger-ui.html

### 주요 명령어

```bash
# 애플리케이션 상태 확인
kubectl get pods -l app=hhplus-app

# 로그 확인
kubectl logs -f deployment/hhplus-app

# Redis 상태 확인
redis-cli ping
redis-cli INFO server

# MySQL 상태 확인
mysql -u root -p -e "SHOW PROCESSLIST;"

# Kafka Consumer Lag 확인
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group hhplus-reservation-consumer --describe
```

---

## 🔄 문서 업데이트 이력

| 날짜 | 내용 | 작성자 |
|------|------|--------|
| 2026-01-06 | 초안 작성 (3개 문서) | Claude Code |
| - | - | - |

**다음 업데이트 예정**
- 대기열 스케줄러 수정 후 병목 분석 업데이트
- 부하 테스트 재실행 후 성능 지표 업데이트
- 장애 사례 발생 시 Post-Mortem 추가

---

## 📞 문의

**기술 문의**
- 개발팀: -
- DevOps팀: -

**서비스 문의**
- PM: -
- CS: -

---

**작성**: 2026-01-06
**버전**: 1.0
