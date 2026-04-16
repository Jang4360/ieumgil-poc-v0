# 2026-04-17 06 Success Review

## 범위

- `06_graphhopper_segment_alignment_and_strict_mode_plan.md` 실행
- DB segmentation을 GraphHopper split semantics에 정합화
- GraphHopper strict import 복구

## 원인

### 1. 1차 원인

- 기존 mismatch `2354`건의 주원인은 `same-way repeated node`와 `closed loop`를 DB builder가 충분히 분해하지 못한 점이었다.
- DB는 덜 쪼개고 GraphHopper는 더 쪼개면서 strict mode가 깨졌다.

### 2. 2차 원인

- 1차 수정 후 mismatch는 `12`건까지 줄었지만, 모두 `ENDPOINT_MATCH_GEOMETRY_DIFFERENT`로 남았다.
- 이 12건은 모두 “같은 `way` 안에서 같은 방향 `source_osm_from_node_id -> source_osm_to_node_id`가 두 번 이상 등장하는 케이스”였다.
- 대표 way:
  - `468917877`
  - `471354082`
  - `504120045`
  - `843532888`
  - `1101439079`
- 예를 들어 한 `way` 안에서 아래처럼 같은 방향 key가 두 번 나온다.
  - 직선 2-point segment
  - 우회 4-point segment
- 기존 lookup은 exact match가 나와도 `lookup key`만 남기고 “몇 번째 등장인지”를 잃어버려, duplicate key 후보 중 올바른 DB segment를 고르지 못했다.

## 적용한 해결

### 1. DB segmentation 보정

- `OsmPbfEligibleWayReader.computeUsageCount()`에서 same-way duplicate node dedup을 제거했다.
- `RoadNetworkBuilder`에 loop-aware split을 추가해 repeated node / closed loop를 GraphHopper `splitLoopSegments`와 유사하게 분해했다.
- 결과적으로:
  - `self edge`는 제거됐다.
  - `road_segments` 수가 `148285 -> 148753`으로 증가했다.

### 2. stable key/lookup 보정

- GraphHopper semantics를 따르자 실제 데이터에서 동일 `source_way_id + source_osm_from_node_id + source_osm_to_node_id`가 중복될 수 있음이 확인됐다.
- 따라서 유일성 기준은 코드/DB에서 `source_way_id + source_osm_from_node_id + source_osm_to_node_id + segment_ordinal`로 보정했다.
- `3-col` 조합은 lookup 후보 키로 유지했다.

### 3. exact occurrence 기반 disambiguation

- `WayNodeSequence.resolve()`가 exact match occurrence를 단순 key가 아니라 `startIndex/endIndex/pointCount`까지 포함해 반환하도록 바꿨다.
- `IeumOsmReader`는 duplicate `lookup key`가 있을 때 geometry string만 보지 않고, exact occurrence가 몇 번째 등장인지 기준으로 동일 key 후보를 순서대로 소모해 매칭하도록 수정했다.
- 이 변경으로 남아 있던 `12`건이 모두 해소됐다.

### 4. synthetic barrier edge 분리

- GraphHopper의 `gh:barrier_edge`는 DB `road_segments`와 1:1 대응하지 않는 synthetic edge라서 `unmatched`로 세지지 않도록 별도 집계로 분리했다.

## 실행 결과

### 1. 단위 테스트

- `./gradlew test`: 통과

### 2. backend compose 실행

- 명령:
  - `docker compose down -v`
  - `docker compose up --build --abort-on-container-exit --exit-code-from backend backend`
- 결과:
  - `eligibleWays=67299`
  - `anchors=108793`
  - `roadNodes=109213`
  - `roadSegments=148753`

### 3. DB 적재 확인

- `road_nodes = 109213`
- `road_segments = 148753`

### 4. graphhopper-importer strict mode 실행

- 명령:
  - `docker compose up --build --abort-on-container-exit --exit-code-from graphhopper-importer graphhopper-importer`
- 결과:
  - `matchedSegments=148753`
  - `unmatchedSegments=0`
  - `graphEdges=150188`
  - 종료 코드 `0`

## 산출물 확인

- `runtime/graphhopper/latest/artifact-metadata.json`
  - `loadedRoadSegmentCount=148753`
  - `matchedSegmentCount=148753`
  - `unmatchedSegmentCount=0`
- `runtime/graphhopper/latest/mismatch-summary.json`
  - `{ }`

## 판정

- `06_graphhopper_segment_alignment_and_strict_mode_plan.md`: `완료`
- GraphHopper strict mode 복구: `완료`
- compose 기본 strict mode(`POC_GRAPHHOPPER_IMPORT_FAIL_ON_UNMATCHED_SEGMENT=true`) 유지 가능: `가능`
