# 2026-04-17 06 Partial Review

## 작업 범위

- `06_graphhopper_segment_alignment_and_strict_mode_plan.md` 실행
- DB segmentation을 GraphHopper split semantics에 더 가깝게 조정
- GraphHopper import mismatch diagnostics 추가
- strict mode compose 기본값 복구

## 적용한 내용

### 1. repeated node / closed loop 분할 정합화

- `OsmPbfEligibleWayReader.computeUsageCount()`가 이제 same-way duplicate node를 dedup하지 않는다.
- `RoadNetworkBuilder`는 단순 anchor 사이 분할 대신 GraphHopper `splitLoopSegments`와 유사한 방식으로 loop-like segment를 두 조각으로 나눈다.
- 그 결과 self-edge 성격의 loop segment를 직접 남기지 않고, repeated node / closed loop에서 더 세밀한 segment를 생성한다.

### 2. stable key 처리 보정

- 실제 부산 PBF 재실행 결과, GraphHopper semantics를 따르자 동일 `source_way_id + source_osm_from_node_id + source_osm_to_node_id`가 두 번 나오는 사례가 확인됐다.
- 예시:
  - `468917877:4633709275:4633709272`
  - `471354082:4655127916:4655127926`
  - `504120045:4943226617:4943226614`
- 따라서 코드와 DB 제약은 `source_way_id + source_osm_from_node_id + source_osm_to_node_id + segment_ordinal` 기준으로 유일성을 보장하도록 수정했다.
- `3-col` 조합은 import lookup 후보 키로 유지하고, 동일 후보가 여러 개면 geometry exact match로 disambiguation 하도록 변경했다.

### 3. GraphHopper import diagnostics 추가

- import 시 unmatched edge를 `mismatch-analysis.jsonl`, `mismatch-summary.json`로 남기도록 구현했다.
- reason category는 최소 아래를 포함한다.
  - `NO_WAY_NODE_SEQUENCE`
  - `NO_DB_CANDIDATE_FOR_WAY`
  - `AMBIGUOUS_ENDPOINT_CANDIDATE`
  - `ENDPOINT_MATCH_GEOMETRY_DIFFERENT`
  - `WAY_NODE_REPEAT_NOT_ANCHOR`
  - `GRAPHHOPPER_SPLIT_EXTRA`
  - `DB_SPLIT_EXTRA`

### 4. synthetic barrier edge 처리

- GraphHopper의 `gh:barrier_edge`는 DB `road_segments`와 1:1 대응하지 않는 synthetic edge다.
- import 단계에서 이 edge는 `unmatched`로 세지지 않고 `syntheticBarrierEdgeCount`로 별도 집계되도록 변경했다.
- compose 기본값은 다시 `POC_GRAPHHOPPER_IMPORT_FAIL_ON_UNMATCHED_SEGMENT=true`로 복구했다.

## 로컬 검증 결과

### 1. 단위 테스트

- `./gradlew test`: 통과

### 2. PBF 기준 network build 재실행

- 명령:
  - `./gradlew bootRun --args='--spring.main.web-application-type=none --poc.network-build.enabled=true --poc.network-build.source-pbf=../etl/data/raw/busan.osm.pbf --poc.network-build.output-directory=build/network-snapshots/alignment-check --poc.network-persistence.enabled=false'`
- 결과:
  - `eligibleWays=67299`
  - `anchors=108793`
  - `roadNodes=109213`
  - `roadSegments=148753`
  - `duplicateStableKeyCount=0`

### 3. 생성 snapshot 추가 확인

- `self_edges=0`
- `3-col duplicate lookup key 수=6`
- `max 3-col duplicate count=2`

## 남은 리스크

- 이 셸에는 `docker`, `psql`이 없어서 compose 기반 PostGIS 적재와 strict import를 end-to-end로 다시 실행하지 못했다.
- 따라서 아래는 코드상 복구했지만 실제 컨테이너 재검증은 아직 비어 있다.
  - `backend` 적재
  - `graphhopper-importer` strict mode 성공
  - `mismatch-summary.json`가 실제로 0 unmatched를 기록하는지

## 판정

- 코드 구현: `부분 완료`
- 로컬 단위/배치 검증: `완료`
- compose strict import 재검증: `미완료`

## 다음 확인 명령

- `docker compose down -v`
- `docker compose up --build --abort-on-container-exit --exit-code-from graphhopper-importer graphhopper-importer`
- 검증 대상:
  - `runtime/graphhopper/latest/artifact-metadata.json`
  - `runtime/graphhopper/latest/mismatch-summary.json`
  - `runtime/graphhopper/latest/mismatch-analysis.jsonl`
