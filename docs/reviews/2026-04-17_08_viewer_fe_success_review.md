# 2026-04-17 08 viewer FE 성공 리뷰

## 작업 범위

`docs/plans/08_react_viewer_fe_plan.md`의 첫 구현 슬라이스로 아래를 반영했다.

- `FE/` 최소 smoke test 앱을 실제 viewer 형태로 교체
- 카카오맵 SDK 로더 추가
- 좌측 패널에 출발지/도착지, `시각장애인`/`휠체어`, `길찾기` UI 배치
- 지도 클릭 기반 출발지/도착지 선택 모드 구현
- `POST /routes/search` 백엔드 API 추가
- `SAFE`, `SHORTEST`, `TRANSIT_MIXED` 카드 렌더링 구현
- `TRANSIT_MIXED` 선택 시 `POST /routes/transit-mixed/candidates` 호출 및 하위 후보 리스트 렌더링 구현
- 선택된 경로/후보를 지도 polyline과 상세 패널로 표시하도록 구현
- frontend Docker build 시 카카오 JavaScript 키를 build arg로 주입하도록 구성

## 구현 파일

### FE

- `FE/src/App.jsx`
- `FE/src/styles.css`
- `FE/Dockerfile`
- `FE/nginx.conf`

### Backend

- `poc/src/main/java/com/example/poc/route/...`
  - `RouteSearchController`
  - `RouteSearchFacade`
  - request/response model records
- 기존 transit mixed facade 재사용

## 현재 동작 범위

### 1. routes/search

- 출발지/도착지/장애유형 입력을 받는다
- `SAFE`, `SHORTEST` 기본 후보를 반환한다
- 기준 거리 `1km` 초과 시 `TRANSIT_MIXED` 카드도 함께 반환한다
- `TRANSIT_MIXED` 카드에는 최고 후보의 요약 정보만 싣고, 실제 조합 리스트는 별도 endpoint에서 조회한다

### 2. FE viewer

- 지도를 띄우고 클릭으로 출발지/목적지를 지정할 수 있다
- 경로 카드 클릭 시 보행 경로 또는 대중교통 후보가 지도와 상세 패널에 표시된다
- 대중교통 후보는 버스/지하철/도보 leg 정보를 타임라인형 상세 패널에 보여준다

## 검증 결과

### 1. backend 테스트

실행 명령:

```powershell
cd poc
./gradlew.bat test
```

결과:

- 성공

추가 검증 대상:

- `RouteSearchFacadeTest`
- `RouteSearchControllerTest`
- 기존 transit mixed 테스트

### 2. frontend build

실행 명령:

```powershell
docker compose build frontend
```

결과:

- 성공
- JSX 경고 없이 Vite production build 완료

### 3. docker compose 통합 검증

실행 명령:

```powershell
docker compose down
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

### 4. HTTP 확인

확인 결과:

- `http://127.0.0.1:3000` -> `200`
- `http://127.0.0.1:8081/routes/search` -> `200`
- `http://127.0.0.1:8081/routes/transit-mixed/candidates` -> `200`
- `http://127.0.0.1:8989/internal/health` -> `200`

확인한 내용:

- frontend 정적 앱 제공
- backend viewer 검색 API 응답
- transit mixed API 응답
- graphhopper artifact load 상태

## 제한 사항

- `SAFE`, `SHORTEST`는 현재 동일한 GraphHopper 기반 geometry를 공유하고, 프로필 차등과 세그먼트 속성은 아직 가상값이 섞여 있다
- `TRANSIT_MIXED`는 여전히 stub 기반 외부 연동이다
- 카카오맵 실제 렌더링은 브라우저에서 key/도메인 설정 영향을 받는다. compose 검증에서는 정적 페이지 제공과 API 연결까지만 확인했다
- compose에서 `frontend`만 재기동해도 `depends_on` 체인 때문에 one-off 배치 컨테이너가 다시 수행될 수 있다

## 다음 단계

1. ODsay/BIMS/부산 지하철 실데이터 연동
2. `SAFE`/`SHORTEST`의 실제 장애유형별 custom model 분기
3. 카카오맵 위 transit marker 스타일 개선
4. walk segment 속성을 실제 GraphHopper/custom EV 또는 DB 정보와 연결
