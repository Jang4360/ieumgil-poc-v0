# 2026-04-16 AGENTS 검증 기준 수정 리뷰

## 대상

- `AGENTS.md`
- `poc/AGENTS.md`

## 수행 내용

### 1. 루트 AGENTS 공통 검증 원칙 추가

- 검증이 단순 `gradle build` 성공만으로 끝나지 않도록 `검증 원칙` 섹션을 추가했다.
- 구현 확인이 필요한 작업은 로컬 `docker compose` 실행을 통해 `backend`, `postgresql`, `graphhopper`가 함께 기동되고 연결되는지 확인하도록 명시했다.
- 통합 실행 검증 결과를 리뷰 문서에 남기도록 규칙을 추가했다.

### 2. `poc/AGENTS.md` 백엔드 검증 기준 강화

- 단순 `./gradlew build` 또는 테스트 태스크 성공만으로 완료 처리하지 않도록 명시했다.
- 로컬 `docker compose`로 `backend`, `postgresql`, `graphhopper`를 함께 올려 실제 동작을 확인하도록 규칙을 추가했다.
- 변경 영향 범위에 따라 애플리케이션 기동, DB 연결, GraphHopper 연계, artifact 로드 경로를 compose 환경에서 직접 확인하도록 정리했다.
- 리뷰 문서에 compose 명령, 확인한 컨테이너, 통과/실패 범위를 기록하도록 명시했다.

## 확인 결과

- 루트 규칙과 `poc` 세부 규칙 모두 검증 기준이 동일한 방향으로 정렬됐다.
- 앞으로는 빌드 성공만으로 검증 완료를 주장하지 않고, 로컬 compose 기반 통합 실행 확인까지 요구하는 문서 상태가 됐다.

## 남은 리스크

- 이번 작업은 AGENTS 문서만 수정했다.
- 실제 `docker compose` 파일이나 실행 스크립트는 이번 변경에서 생성하거나 검증하지 않았다.
