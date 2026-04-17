# 2026-04-17 09 라우팅 프로필 및 Compose 부트스트랩 보완 리뷰

## 요약

- 결과: 부분 성공
- 이번 실행 범위:
  - `SAFE`, `SHORTEST`, `TRANSIT_MIXED` 라우팅 계약 정렬
  - `policyName`, `appliedProfile`, `resultType`, `userType` 응답 메타데이터 추가
  - GraphHopper 보행 프로필을 `visual_safe`, `visual_fast`, `wheelchair_safe`, `wheelchair_fast`로 분리
  - import 단계에서 프로필별 pass encoded value 추가
  - compose에서 `network-builder`, `graphhopper-importer`를 bootstrap 단계로 분리

## 코드 변경

- viewer route search와 transit mixed search 응답에 정책 메타데이터를 추가했다.
- 도보 라우팅이 `SAFE_WALK`, `FAST_WALK`를 별도로 요청하고 각기 다른 `appliedProfile`을 노출하도록 수정했다.
- GraphHopper에 아래 프로필별 pass gate encoded value를 추가했다.
  - `ieum_visual_safe_pass`
  - `ieum_visual_fast_pass`
  - `ieum_wheelchair_safe_pass`
  - `ieum_wheelchair_fast_pass`
- GraphHopper factory가 4개 보행 프로필을 등록하고 artifact metadata에 전체 프로필 목록을 기록하도록 수정했다.
- runtime 서비스가 one-off 서비스에 직접 `depends_on`하지 않도록 compose를 재구성했다.

## 검증 수행

## 1. Gradle 테스트

실행:

```powershell
cd C:\Users\SSAFY\ieumgil-poc-v0\poc
./gradlew.bat test
```

결과:

- 통과

## 2. Compose bootstrap 검증

실행:

```powershell
cd C:\Users\SSAFY\ieumgil-poc-v0
docker compose down
docker compose --profile bootstrap up --build network-builder graphhopper-importer
```

확인:

- `network-builder` 종료 코드 `0`
- `graphhopper-importer` 종료 코드 `0`
- `postgresql` healthy
- GraphHopper import 로그에 아래 4개 subnetwork job이 나타남
  - `visual_safe`
  - `visual_fast`
  - `wheelchair_safe`
  - `wheelchair_fast`

## 3. Compose runtime 검증

실행:

```powershell
docker compose up --build -d backend graphhopper frontend
docker compose ps -a
```

확인:

- `backend`가 `8081`에서 기동
- `graphhopper`가 `8989`에서 기동
- `frontend`가 `3000`에서 기동
- `graphhopper-importer`, `network-builder`는 `Exited (0)` 상태 유지

## 4. HTTP smoke 검증

실행:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8081/routes/search -Method POST -ContentType 'application/json' -Body '{"startPoint":{"lat":35.1796,"lng":129.0756},"endPoint":{"lat":35.1577,"lng":129.059},"disabilityType":"VISUAL"}'
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8081/routes/transit-mixed/candidates -Method POST -ContentType 'application/json' -Body '{"startPoint":{"lat":35.1796,"lng":129.0756},"endPoint":{"lat":35.1577,"lng":129.059},"disabilityType":"VISUAL"}'
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8989/internal/health
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000
```

결과:

- 모두 `200`
- `/routes/search` 응답에 아래 조합이 내려옴
  - `SAFE_WALK` + `wheelchair_safe`
  - `FAST_WALK` + `wheelchair_fast`
  - `ACCESSIBLE_TRANSIT` + `wheelchair_safe`
- `/routes/transit-mixed/candidates` 응답에 아래 메타데이터가 내려옴
  - `routeOption = TRANSIT_MIXED`
  - `policyName = ACCESSIBLE_TRANSIT`
  - `walkProfile = visual_safe`

## 5. one-off 재실행 방지 검증

실행:

```powershell
docker compose up -d frontend
```

확인:

- `backend`, `frontend`만 running으로 표시
- `network-builder`, `graphhopper-importer`는 다시 시작되지 않음

## 남은 리스크

- GraphHopper 프로필 분리 배선은 들어갔지만, smoke query의 도보 geometry는 아직 fallback처럼 보였다. 즉 artifact load와 profile 등록은 성공했지만, 실제 imported graph에서 프로필별 경로 차이가 제대로 반영되는지는 추가 검증이 필요하다.
- `TRANSIT_MIXED`는 아직 ODsay, BIMS, 부산 지하철 운행정보 실연동이 아니라 stub 기반이다.
- 카카오맵은 이번 슬라이스에서 compose와 정적 페이지 제공까지만 봤고, 실제 브라우저 렌더링까지는 별도 확인이 필요했다.

## 다음 권장 작업

- 실제 OD 쌍을 기준으로 GraphHopper가 프로필별 다른 경로를 반환하는지 검증하고, 현재 smoke query가 fallback 형태로 보이는 원인을 먼저 추적한다.
