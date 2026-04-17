# 2026-04-17 compose frontend backend graphhopper 성공 리뷰

## 작업 범위

- `docker-compose.yml`을 수정해 장기 실행 서비스와 일회성 배치 서비스를 분리했다.
- `backend`를 배치 실행기가 아니라 servlet API 컨테이너로 전환했다.
- 기존 배치 역할은 `network-builder`, `graphhopper-importer` one-off 서비스로 분리했다.
- `frontend` 컨테이너를 새로 추가했다.
- 사용자 유형을 `VISUAL`, `WHEELCHAIR`로 정렬했다.
- 07/08 계획 문서와 07 실행 리뷰 문서의 장애유형 표기를 정정했다.

## 변경 내용

### 1. compose 구조

- `postgresql`: DB
- `network-builder`: 보행 네트워크 생성 및 DB 적재
- `graphhopper-importer`: graph artifact 생성
- `graphhopper`: artifact load 후 장기 실행
- `backend`: transit mixed API 제공 및 동일 artifact load
- `frontend`: nginx로 정적 React 앱 제공, `/api/`를 backend로 프록시

### 2. 포트

- frontend: `3000`
- backend: `8081`
- graphhopper: `8989`

백엔드 호스트 포트를 `8081`로 둔 이유는 로컬 환경에서 `8080`이 이미 사용 중이어서 compose가 처음 실패했기 때문이다.

### 3. 사용자 유형 정렬

- `HEARING` 제거
- `WHEELCHAIR` 사용
- 계획 문서:
  - `docs/plans/07_accessible_transit_mixed_routing_plan.md`
  - `docs/plans/08_react_viewer_fe_plan.md`
- 리뷰 문서:
  - `docs/reviews/2026-04-17_07_execution_start_success_review.md`
- backend transit 코드와 테스트도 같은 기준으로 정정

## 검증 결과

### 1. 백엔드 테스트

실행 명령:

```powershell
cd poc
./gradlew.bat test
```

결과:

- 성공

### 2. docker compose 기동

실행 명령:

```powershell
docker compose up --build -d
docker compose ps -a
```

최종 상태:

- `postgresql`: `Up (healthy)`
- `network-builder`: `Exited (0)`
- `graphhopper-importer`: `Exited (0)`
- `graphhopper`: `Up (healthy)`
- `backend`: `Up`
- `frontend`: `Up`

### 3. HTTP 확인

실행 확인:

- `http://127.0.0.1:3000` -> `200`
- `http://127.0.0.1:8081/routes/transit-mixed/candidates` -> `200`
- `http://127.0.0.1:8989/internal/health` -> `200`

확인한 내용:

- frontend가 정적 React 앱을 정상 제공한다.
- backend가 `WHEELCHAIR` 입력을 포함한 transit mixed API 요청에 응답한다.
- graphhopper가 artifact를 로드한 뒤 health endpoint를 반환한다.

## 남은 리스크

- frontend는 현재 compose smoke test용 최소 React 앱이며 실제 viewer 구현은 아니다.
- transit mixed API는 아직 ODsay/BIMS/부산 지하철 실연동이 아니라 stub 기반이다.
- backend는 현재 graphhopper artifact를 자체 로드하므로, graphhopper 서비스와 메모리를 이중 사용한다.
- `docker compose up --build -d`는 부산 PBF 처리 때문에 초기 기동 시간이 길 수 있다.

## 다음 단계

1. frontend를 실제 viewer UI로 확장
2. transit mixed API의 외부 실연동 구현
3. 장애유형별 GraphHopper custom model 실제 분기 구현
4. 필요 시 backend가 graphhopper를 직접 로드하지 않고 별도 라우팅 호출로 바꾸는 구조 검토
