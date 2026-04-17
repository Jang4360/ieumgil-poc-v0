# 2026-04-17 보행 경로 직선 fallback 진단 및 해결 리포트

## 요약

- 상태: 1차 해결
- 증상:
  - viewer에서 보행 경로가 실제 보행로를 따라가지 않고 출발지와 도착지를 직선 또는 거의 직선으로 연결했다.
  - route card에 `Fallback`과 `Cannot find point ...`가 표시되었다.
- 결론:
  - 프론트 렌더링 문제가 아니라 백엔드 fallback geometry 노출 문제였다.
  - 직접 원인은 GraphHopper imported graph에서 각 profile의 pass edge 수가 사실상 `0`이어서 snap이 실패한 것이다.
  - 근본 원인은 현재 `road_segments` 접근성 속성이 전부 `UNKNOWN`인데, import 시 pass 규칙이 `UNKNOWN`을 전부 탈락시키도록 설계되어 있었기 때문이다.

## 문제 재현

### 대표 재현 좌표 1

- 사용자 유형: `WHEELCHAIR`
- 출발지: `35.185160159071756, 129.0992445756258`
- 도착지: `35.18288721728427, 129.122104579223`

수정 전 `/routes/search` 응답:

- `graphHopperBacked = false`
- `fallbackUsed = true`
- `fallbackReason = Cannot find point 0 ... | Cannot find point 1 ...`

### 대표 재현 좌표 2

- 사용자 유형: `VISUAL`
- 출발지: `35.1796, 129.0756`
- 도착지: `35.1577, 129.0590`

수정 전 `/routes/search` 응답:

- `graphHopperBacked = false`
- `fallbackUsed = true`
- geometry가 거의 직선

## 원인 확인 순서

### 1. 런타임 로그 확인

`backend` 로그에서 동일 패턴이 반복 확인되었다.

- `Falling back to synthetic walk route`
- `profile=visual_safe` 또는 `profile=wheelchair_safe`
- `reason=Cannot find point ...`

이 단계에서 직선은 프론트 문제가 아니라 `TransitWalkLegRouter`의 fallback 응답이라는 점을 확인했다.

관련 파일:

- [TransitWalkLegRouter.java](C:/Users/SSAFY/ieumgil-poc-v0/poc/src/main/java/com/example/poc/transit/service/TransitWalkLegRouter.java)

### 2. artifact는 실제로 존재하는지 확인

확인 결과:

- GraphHopper artifact load 성공
- `graph-cache` 존재
- imported edge 수 존재
- profile 4개 등록 확인

즉 “artifact가 비어 있다”는 가설은 배제했다.

### 3. road segment 데이터 분포 확인

PostgreSQL에서 `road_segments`를 직접 확인했다.

결과:

- 전체 `148753`건
- `walk_access`, `braille_block_state`, `audio_signal_state`, `curb_ramp_state`, `width_state`, `surface_state`, `stairs_state`, `crossing_state`가 전부 `UNKNOWN`

즉 현재 네트워크 빌더는 아직 접근성 실데이터 보강 없이 OSM-only 상태였다.

관련 파일:

- [RoadNetworkBuilder.java](C:/Users/SSAFY/ieumgil-poc-v0/poc/src/main/java/com/example/poc/network/service/RoadNetworkBuilder.java)

### 4. pass 규칙 계산 결과 확인

DB 분포를 현재 import 규칙에 대입해 계산했다.

결과:

- `visual_safe_pass = 0`
- `visual_fast_pass = 0`
- `wheelchair_safe_pass = 0`
- `wheelchair_fast_pass = 0`

이 단계에서 root cause가 확정되었다.

- imported graph는 존재한다.
- 그러나 profile pass 조건이 모두 false라서 route와 snap에 사용할 edge가 없다.
- 따라서 GraphHopper는 `Cannot find point`를 내고 fallback으로 내려간다.

관련 파일:

- [IeumOsmReader.java](C:/Users/SSAFY/ieumgil-poc-v0/poc/src/main/java/com/example/poc/graphhopper/service/IeumOsmReader.java)

## 수정 내용

### 변경 목표

현재 저장소는 접근성 실데이터 연동 전 단계이므로, `UNKNOWN`을 전부 차단하면 실사용 가능한 graph가 0이 된다.  
따라서 OSM-only 단계에서는 다음 원칙으로 완화했다.

- 명시적 위험값만 차단
- `UNKNOWN`은 일단 통과
- `stairs = YES` 같은 명시적 금지값은 계속 차단
- slope는 값이 없으면 허용

### 실제 수정

[IeumOsmReader.java](C:/Users/SSAFY/ieumgil-poc-v0/poc/src/main/java/com/example/poc/graphhopper/service/IeumOsmReader.java)에서:

- `isVisualSafePass`
- `isVisualFastPass`
- `isWheelchairSafePass`
- `isWheelchairFastPass`

규칙을 다음 방향으로 완화했다.

- `UNKNOWN` 필드 때문에 자동 탈락하지 않게 수정
- `surface`, `width`, `braille`, `crossing`은 명시적 bad/no만 차단
- `avgSlopePercent == null`이면 통과
- `walk_access == NO`만 차단

## 재시도 기록

### 재시도 1

작업:

1. pass 규칙 완화
2. `./gradlew.bat test`
3. `docker compose --profile bootstrap up --build network-builder graphhopper-importer`
4. `docker compose up --build -d backend graphhopper frontend`
5. 동일 좌표로 `/routes/search` 재호출

결과:

- 성공

import 로그 변화:

- 이전: 사실상 모든 edge 탈락
- 이후: `PrepareRoutingSubnetworks`가 네 profile 모두에서 큰 connected component를 찾음

대표 로그:

- `wheelchair_safe - Found 424 subnetworks`
- `-> 2 components(s) remain`
- `visual_safe - Found 424 subnetworks`

즉 profile 관점에서 usable graph가 다시 생겼다.

## 해결 확인

### 대표 재현 좌표 1 재검증

- 사용자 유형: `WHEELCHAIR`
- 출발지: `35.185160159071756, 129.0992445756258`
- 도착지: `35.18288721728427, 129.122104579223`

수정 후 결과:

- `graphHopperBacked = true`
- `fallbackUsed = false`
- `fallbackReason = null`
- geometry가 다점 polyline으로 반환됨

### 대표 재현 좌표 2 재검증

- 사용자 유형: `VISUAL`
- 출발지: `35.1796, 129.0756`
- 도착지: `35.1577, 129.0590`

수정 후 결과:

- `graphHopperBacked = true`
- `fallbackUsed = false`
- geometry가 실제 도로를 따라가는 다점 polyline으로 반환됨

## 현재 판단

“왜 직선이 나오는가”에 대한 이번 이슈는 현재 기준으로 해결되었다.

- 직선의 직접 원인은 fallback geometry였다.
- fallback의 직접 원인은 snap 실패였다.
- snap 실패의 직접 원인은 모든 profile pass가 0개였기 때문이다.
- 그 원인은 네트워크 속성이 전부 `UNKNOWN`인데 strict pass가 `UNKNOWN`을 전부 차단한 설계였다.

## 남은 리스크

### 1. `SAFE`와 `SHORTEST`가 여전히 같은 geometry일 수 있음

현재는 unknown을 통과시키는 완화 정책을 적용했기 때문에:

- `visual_safe`와 `visual_fast`
- `wheelchair_safe`와 `wheelchair_fast`

가 동일 경로를 반환할 가능성이 높다.

즉 “직선 fallback 제거”는 해결했지만, “profile별 차등 경로 품질”은 아직 별도 과제다.

### 2. 접근성 속성은 여전히 전부 `UNKNOWN`

현재 graph-backed route는 복구됐지만, 이는 실데이터 접근성 제약이 반영된 최종 품질이 아니다.  
지금은 OSM-only 운영 가능 상태를 회복한 단계다.

### 3. viewer 브라우저 시각 검증은 별도

API 응답은 graph-backed geometry로 바뀌었지만, 실제 브라우저에서 사용자가 최종 시각 결과를 보는 검증은 별도로 수행해야 한다.

## 권장 후속 작업

1. `SAFE`와 `SHORTEST` 차등 경로가 실제로 나오는 fixture를 따로 만든다.
2. 실데이터 연동 전까지는 fallback이 다시 발생해도 viewer에서 경고 스타일로 표시하도록 유지한다.
3. 이후 공공데이터 보강이 들어오면 strict safe profile을 다시 강화한다.
