# 2026-04-17 10 Viewer 상호작용 및 라우팅 신뢰성 보완 리뷰

## 요약

- 결과: 부분 성공
- 이번 슬라이스에서 완료한 범위:
  - 프론트 지도 선택 UX 복구
  - 선택 마커와 경로 오버레이 분리
  - 백엔드 응답에 GraphHopper fallback 진단 필드 추가
  - compose 재기동 및 HTTP smoke 검증
- 이번 슬라이스에서 미완료로 남긴 범위:
  - `TRANSIT_MIXED` 실데이터 연동
  - 카카오맵 실제 브라우저 수동 렌더링 검증
  - imported graph 기반 safe/fast 실제 차등 경로 보정

## 구현 내용

### 1. FE 지도 상호작용 복구

- `FE/src/App.jsx`
  - 카카오맵 클릭 이벤트 시그니처를 바로잡았다.
  - `pickMode`를 `idle`, `picking-start`, `picking-end` 흐름으로 분리했다.
  - 지도 클릭 시 즉시 확정하지 않고 임시 마커를 놓은 뒤 `이 위치로 확정` 버튼으로 반영하도록 바꿨다.
  - 출발지/도착지 마커, 임시 선택 마커, 경로 polyline, 대중교통 마커를 서로 다른 컬렉션으로 관리하도록 바꿨다.
  - 드래그 직후 오작동 클릭을 줄이기 위해 click ignore 플래그를 추가했다.
  - 경로 선택과 지도 선택 상태를 분리해서 도착지 선택이 누락되던 흐름을 정리했다.
- `FE/src/styles.css`
  - 선택 모드 패널, 임시 마커 스타일, 디버그 뱃지, 경고 문구 스타일을 추가했다.
  - 선택 모드일 때 crosshair 커서를 보이게 했다.

### 2. 백엔드 fallback 가시화

- `RouteCandidateResponse`
  - `graphHopperBacked`
  - `fallbackUsed`
  - `fallbackReason`
- `TransitCandidateResponse`
  - `dataSource`
  - `walkLegGraphHopperBacked`
  - `fallbackWalkLegCount`
  - `fallbackReasonSummary`
- `TransitMixedRouteResponse`
  - `dataSource`
- `TransitWalkLegRouter`
  - GraphHopper route 실패 시 fallback 이유를 수집해 응답으로 올리도록 변경했다.
  - fallback 시 profile, 좌표, reason을 로그로 남기도록 보강했다.
- `RouteSearchFacade`, `TransitMixedRouteFacade`
  - 새 디버그 필드를 응답에 채우도록 수정했다.
  - 현재 transit 후보 데이터 소스가 stub인지 live인지 문자열로 표기하도록 추가했다.

### 3. 테스트 갱신

- route/transit 관련 단위 테스트와 controller 테스트를 새 응답 필드 구조에 맞게 수정했다.

## 검증

### 1. 백엔드 테스트

실행:

```powershell
cd C:\Users\SSAFY\ieumgil-poc-v0\poc
./gradlew.bat test
```

결과:

- 성공

### 2. 프론트 빌드

실행:

```powershell
cd C:\Users\SSAFY\ieumgil-poc-v0\FE
npm.cmd install
npm.cmd run build
```

결과:

- 성공
- 로컬 개발 의존성 설치를 위해 `FE/package-lock.json`이 생성되었다.

### 3. compose 재기동

실행:

```powershell
cd C:\Users\SSAFY\ieumgil-poc-v0
docker compose up --build -d frontend backend graphhopper
docker compose ps -a
```

결과:

- `frontend` `3000`
- `backend` `8081`
- `graphhopper` `8989`
- `postgresql` healthy 유지
- bootstrap one-off 컨테이너는 재실행되지 않고 `Exited (0)` 유지

### 4. HTTP smoke

실행:

```powershell
Invoke-WebRequest -Uri http://localhost:3000 -UseBasicParsing
Invoke-RestMethod -Method Post -Uri http://localhost:8081/routes/search -ContentType 'application/json' -Body '{"startPoint":{"lat":35.1796,"lng":129.0756},"endPoint":{"lat":35.1577,"lng":129.0590},"disabilityType":"VISUAL"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8081/routes/transit-mixed/candidates -ContentType 'application/json' -Body '{"startPoint":{"lat":35.1796,"lng":129.0756},"endPoint":{"lat":35.1577,"lng":129.0590},"disabilityType":"VISUAL"}'
```

결과:

- frontend `200`
- `/routes/search` `200`
- `/routes/transit-mixed/candidates` `200`
- `/routes/search` 응답에서 `graphHopperBacked`, `fallbackUsed`, `fallbackReason`이 확인되었다.
- `/routes/transit-mixed/candidates` 응답에서 `dataSource = STUB`, `fallbackWalkLegCount`, `fallbackReasonSummary`가 확인되었다.

## 확인된 핵심 진단

- 현재 smoke query의 보행 경로는 실제로 GraphHopper imported graph를 타지 못하고 fallback으로 내려가고 있다.
- 대표 fallback reason:
  - `Cannot find point 0: 35.1796,129.0756`
  - `Cannot find point 1: 35.1577,129.059`
- 즉 프로필 배선 문제라기보다 현재 테스트 OD가 imported graph의 snap 범위에 잘 붙지 않거나 graph coverage/접근 가능성 문제일 가능성이 높다.

## 남은 리스크

- `SAFE_WALK`와 `FAST_WALK`가 실제 imported graph 위에서 차등 geometry를 반환하는지 아직 보장되지 않는다.
- `TRANSIT_MIXED`는 여전히 stub 기반이다.
- 카카오맵은 정적 서빙과 코드 경로만 확인했으며, 실제 브라우저에서 드래그/클릭/확정 버튼 흐름을 사람이 직접 검증한 것은 아니다.

## 다음 권장 작업

1. 브라우저에서 `http://localhost:3000`에 접속해 출발지/도착지 선택, 지도 드래그, 임시 마커, 확정 버튼 흐름을 실제로 확인한다.
2. imported graph에 잘 snap되는 Busan 내부 OD fixture를 찾아 `/routes/search`에서 `graphHopperBacked = true`가 나오는 케이스를 확보한다.
3. 그 fixture로 `SAFE_WALK`와 `FAST_WALK` geometry 차이를 비교한다.
4. 이후 `TRANSIT_MIXED`의 ODsay, BIMS, 부산 지하철 실연동으로 넘어간다.
