# 06. GraphHopper Segment 정합화와 Strict Mode 복구 계획

## 목적

`04_graphhopper_import_and_artifact_plan.md` 구현 이후 남아 있는 `unmatchedSegmentCount`를 제거해, GraphHopper import를 strict mode로 다시 고정할 수 있게 한다.

핵심은 아래 2가지를 동시에 해결하는 것이다.

- GraphHopper의 실제 edge 분할 결과와 `road_segments` anchor 분할 결과의 차이를 재현 가능하게 분석한다.
- 분석 결과를 바탕으로 DB segmentation을 GraphHopper segmentation semantics에 맞춰 정합화하고 `poc.graphhopper-import.fail-on-unmatched-segment=true`를 다시 기본값으로 올린다.

## 현재 관측값

2026-04-16 compose 검증 기준 현재 값은 아래와 같다.

- `road_segments`: `148285`
- GraphHopper imported edges: `150188`
- `matchedSegmentCount`: `147834`
- `unmatchedSegmentCount`: `2354`
- GraphHopper edge 수와 DB segment 수 차이: `1903`
- GraphHopper imported edge 대비 unmatched 비율: 약 `1.57%`
- DB segment 대비 matched 커버리지: 약 `99.70%`

현재 compose 기본값은 artifact 생성과 load-only 검증을 위해 `POC_GRAPHHOPPER_IMPORT_FAIL_ON_UNMATCHED_SEGMENT=false`로 둔다.

## 구현 방향 확정

이번 계획에서는 아래 선택지를 기본 방향으로 고정한다.

- 현재 구조 유지
- DB segmentation을 GraphHopper segmentation에 맞춘다

이 방향을 선택하는 이유는 아래와 같다.

- 최종 라우팅 graph의 edge owner는 GraphHopper다.
- 현재 PoC는 `road_segments -> GraphHopper import -> custom EV bake-in` 구조다.
- 따라서 DB `road_segments` 경계가 GraphHopper edge 경계와 맞아야 stable key lookup이 strict하게 성립한다.
- GraphHopper 내부 split rule을 포크해 우리 쪽 방식으로 바꾸는 건 가능하더라도, 업스트림 추적 비용과 라우팅 정합성 책임이 너무 크다.

즉 이번 계획의 목표는 “GraphHopper를 우리 쪽에 맞추는 것”이 아니라 “DB segmentation을 GraphHopper와 같은 semantics로 맞추는 것”이다.

## 구현 범위

- mismatch edge 샘플 추출
- split rule 차이 분석
- DB anchor/segment rule을 GraphHopper 쪽으로 정합화
- import lookup diagnostics 보강
- strict mode 복구 조건 정의
- 검증/리뷰 문서화

## 비범위

- 공공데이터 ETL 반영
- 사용자 정책별 weighting 정교화
- transit orchestration
- viewer/UI

## 현재 분석

### 1. split rule 차이가 실제로 존재한다

- GraphHopper import 결과 edge 수가 `road_segments`보다 `1903`개 많다.
- 즉 현재 mismatch는 단순 lookup 구현 버그만이 아니라, 분할 단위 자체가 완전히 같지 않다는 뜻이다.

### 2. 현재 DB 쪽 anchor rule은 GraphHopper의 내부 분할 규칙을 완전히 복제하지 않는다

현재 DB 쪽 anchor는 아래 기준으로만 올라간다.

- 시작 node
- 끝 node
- 둘 이상의 eligible `way`에 등장한 node
- `crossing`
- `highway=crossing`
- `elevator`

하지만 현재 usage count는 [OsmPbfEligibleWayReader.java](/Users/jangjooyoon/Desktop/JooYoon/ssafy/ieumgil-poc-v0/poc/src/main/java/com/example/poc/network/io/OsmPbfEligibleWayReader.java:129)에서 `way` 내부 중복 node를 dedup한 뒤 계산한다.

즉 아래 케이스는 현재 anchor로 승격되지 않을 수 있다.

- 하나의 `way` 안에서 같은 node가 두 번 이상 나오는 self-intersection
- loop 또는 repeated node 형태의 `way`

이 경우 GraphHopper 쪽은 split을 더 만들고, DB 쪽은 하나의 segment로 남길 가능성이 있다.

### 3. 현재 mismatch의 주원인은 이미 확인됐다

원인 분석 결과 현재 mismatch의 주원인은 아래 2가지다.

- `same-way repeated node`
- `closed loop(start == end)`

근거는 아래와 같다.

- GraphHopper 11의 `WaySegmentParser`는 공식 소스 주석과 `splitLoopSegments` 로직에서 같은 `way` 안의 duplicate node와 loop를 분할 대상으로 처리한다.
- 반면 현재 DB 쪽 usage count는 [OsmPbfEligibleWayReader.java](/Users/jangjooyoon/Desktop/JooYoon/ssafy/ieumgil-poc-v0/poc/src/main/java/com/example/poc/network/io/OsmPbfEligibleWayReader.java:138)에서 `LinkedHashSet`으로 same-way duplicate node를 제거한다.
- 실제 데이터 분석 결과:
  - walkable eligible `way`: `67299`
  - same-way repeated node를 가진 `way`: `1260`
  - closed loop `way`: `628`
  - 두 수의 합: `1888`
  - GraphHopper extra edge(`150188 - 148285`): `1903`

두 수가 거의 같은 규모라서, 현재 strict mode를 깨는 주요 원인은 GraphHopper의 loop/self-intersection 분할을 DB 쪽이 반영하지 못한 것으로 본다.

### 4. 현재 lookup은 geometry/endpoint fallback까지는 있지만 mismatch 원인 분류 정보가 없다

현재 lookup은 [WayNodeSequence.java](/Users/jangjooyoon/Desktop/JooYoon/ssafy/ieumgil-poc-v0/poc/src/main/java/com/example/poc/graphhopper/model/WayNodeSequence.java:32)에서

- 전체 point sequence 일치
- 실패 시 endpoint fallback

까지 지원한다.

그런데 현재는 mismatch를 아래처럼 분류해 남기지 않는다.

- 같은 `way` 안에 동일 endpoint 후보가 여러 개인 경우
- start/end는 맞지만 내부 geometry가 다른 경우
- DB 쪽에 더 긴 segment만 있고 GraphHopper가 중간에서 더 잘게 split한 경우
- DB 쪽에 split가 더 있고 GraphHopper가 하나로 유지한 경우
- self-intersection node 미승격 때문에 생긴 경우

즉 strict mode를 복구하려면 먼저 mismatch를 설명 가능한 데이터로 바꿔야 한다.

## 구현할 내용

### 1. mismatch 분석 산출물을 추가한다

GraphHopper import를 `analysis mode`로 한 번 더 실행할 수 있게 하고, unmatched edge마다 아래를 파일로 남긴다.

- `way_id`
- GraphHopper edge의 시작/끝 좌표
- point count
- 전체 point sequence hash
- endpoint 기준 후보 stable key 수
- 같은 `way` 내 candidate DB segment 수
- candidate가 있으면 `source_osm_from_node_id`, `source_osm_to_node_id`, `segment_ordinal`
- mismatch reason category

권장 산출물:

- `runtime/graphhopper/latest/mismatch-analysis.jsonl`
- `runtime/graphhopper/latest/mismatch-summary.json`

### 2. mismatch reason을 분류한다

최소 아래 카테고리로 분류한다.

- `WAY_NODE_REPEAT_NOT_ANCHOR`
- `GRAPHHOPPER_SPLIT_EXTRA`
- `DB_SPLIT_EXTRA`
- `ENDPOINT_MATCH_GEOMETRY_DIFFERENT`
- `NO_DB_CANDIDATE_FOR_WAY`
- `AMBIGUOUS_ENDPOINT_CANDIDATE`

목적은 “왜 strict mode가 깨지는지”를 숫자로 설명 가능하게 만드는 것이다.

### 3. DB anchor rule과 segment 분해를 GraphHopper 방식으로 맞춘다

이 단계는 이번 계획의 핵심 구현 항목이다.

우선순위는 아래 순서로 둔다.

1. `computeUsageCount`에서 same-way repeated node를 dedup하지 않거나, 별도 repeated-node pass를 추가해 anchor 후보로 승격한다.
2. `closed loop(start == end)`를 DB builder에서도 GraphHopper와 같은 방식으로 분해한다.
3. self-intersection / repeated node를 anchor로 승격한 뒤 segment 경계를 다시 계산한다.
4. 필요하면 현재 GraphHopper `splitNodeFilter`와 동일한 태그 기준을 DB builder에도 반영한다.
5. `source_osm_from_node_id == source_osm_to_node_id` 형태의 loop-like segment가 더 이상 남지 않는지 검증한다.

주의:

- 이 단계에서도 블루프린트 원칙은 유지한다.
- `road_nodes`에 모든 OSM node를 넣지 않는다.
- `road_segments` 안정 키는 계속 `source_way_id + source_osm_from_node_id + source_osm_to_node_id` 중심으로 유지한다.
- 해결 방향은 lookup 완화가 아니라 DB segment 경계 정합화다.

### 4. import lookup을 진단 가능 구조로 바꾼다

현재는 lookup 실패 시 단순 카운트만 늘어난다.

이를 아래처럼 바꾼다.

- failure마다 reason code를 기록한다.
- endpoint 후보가 1개일 때는 그 후보를 사용했는지 여부를 명시한다.
- candidate가 여러 개면 어떤 규칙으로 tie-break 했는지 기록한다.
- strict mode 실패 시 첫 100개 샘플을 로그 또는 파일로 남긴다.

### 5. strict mode 복구 기준을 명시한다

strict mode 복구는 아래 순서로 진행한다.

1. mismatch reason 분포를 추출한다.
2. 가장 큰 카테고리부터 DB split rule을 수정한다.
3. compose 검증에서 `unmatchedSegmentCount=0`을 만든다.
4. 그 뒤 `POC_GRAPHHOPPER_IMPORT_FAIL_ON_UNMATCHED_SEGMENT=true`를 compose 기본값으로 되돌린다.

## 구현 산출물

- mismatch 분석 산출물 writer
- mismatch reason category 정의
- anchor 승격 규칙 보강
- loop/self-intersection segment 분해 수정
- strict mode 복구 절차 문서
- 결과 리뷰 문서

## 완료 기준

- unmatched edge마다 reason category가 남는다.
- mismatch summary에서 가장 큰 원인 카테고리가 정량적으로 보인다.
- self-intersection/repeated node 케이스를 재현하는 테스트가 추가된다.
- `same-way repeated node`와 `closed loop`가 DB segmentation에서 GraphHopper와 같은 방식으로 분해된다.
- compose 검증에서 `unmatchedSegmentCount=0` 또는 strict mode 복구 불가 원인이 명시된다.
- strict mode를 다시 기본값으로 올릴 수 있으면 compose와 문서에 반영된다.

## 권장 실행 순서

1. mismatch-analysis 산출물 추가
2. self-intersection / repeated-node fixture 추가
3. DB anchor rule 및 loop 분해 수정
4. import lookup diagnostics 보강
5. compose strict mode 재실행
6. 결과를 `docs/reviews/`에 기록

## 재시도 및 리뷰 작성

- mismatch 원인 분석과 strict mode 복구가 완료 기준을 만족하지 못하면 원인 수정 후 최대 2회 재시도한다.
- 2회 재시도 후에도 해결 방안이 없으면 `docs/reviews/`에 실패 원인, mismatch 상위 카테고리, 남은 설계 차이를 `md`로 남긴다.
- 성공한 경우에도 `docs/reviews/`에 mismatch 감소 추이, strict mode 복구 여부, 남은 예외 케이스를 기록한다.
