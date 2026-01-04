# 부하 테스트 보고서

이 디렉토리에는 콘서트 예약 시스템의 부하 테스트 결과 보고서가 포함되어 있습니다.

## 📄 문서 목록

### 1. [Executive Summary](./executive-summary.md) ⭐
**빠른 파악용 - 5분 독서**

경영진 및 의사결정자를 위한 핵심 요약본
- 테스트 결과 핵심 요약
- 주요 이슈 및 우선순위
- Action Items

### 2. [Load Test Report](./load-test-report.md)
**상세 분석용 - 15분 독서**

개발팀 및 QA팀을 위한 상세 보고서
- 테스트 환경 및 설정
- 상세 메트릭 및 분석
- 발견된 이슈 및 원인 분석
- 권장 사항 및 후속 계획

---

## 🎯 주요 결과 요약

### ✅ 성공
- API 응답 속도: P95 **14.23ms** (목표 대비 97% 개선)
- HTTP 실패율: **0%**
- 토큰 발급/조회: **100%** 성공

### ❌ 차단 이슈
- **대기열 활성화 타임아웃** (즉시 수정 필요)

---

## 📁 관련 파일

- 테스트 스크립트: `../k6-tests/`
- 실행 가이드: `../k6-tests/README.md`
- 부하 테스트 계획: `../docs/PR_docs/section10.md`

---

## 🔗 모니터링 도구

- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090
- Actuator: http://localhost:8080/actuator/prometheus

---

**최종 업데이트**: 2026-01-04
**다음 테스트 예정**: 대기열 이슈 해결 후
