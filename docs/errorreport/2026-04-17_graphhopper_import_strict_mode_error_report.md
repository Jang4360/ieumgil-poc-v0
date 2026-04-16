# 2026-04-17 GraphHopper Import Strict Mode Error Report

### 1. 문제 상황

- 언제
  - `2026-04-16`부터 `04_graphhopper_import_and_artifact_plan` 구현 및 compose 검증 중
- 어디서
  - `graphhopper-importer` 컨테이너 실행 시
  - `GraphHopperImportService` strict mode 검증 구간
- 어떤 기능에서
  - `road_segments` bulk load 후 GraphHopper import를 수행하고 custom EV를 strict하게 매칭하는 기능에서
- 어떤 증상이 발생했는가
  - `POC_GRAPHHOPPER_IMPORT_FAIL_ON_UNMATCHED_SEGMENT=true`로 실행하면 import 마지막에 실패
  - 에러 메시지:
    - `IllegalStateException: Unmatched graphhopper segments detected: 2354`
  - 관련 수치:
    - DB `road_segments=148285`
    - GraphHopper imported edges=`150188`
    - `matchedSegmentCount=147834`
    - `unmatchedSegmentCount=2354`

### 2. 초기 가설

- GraphHopper가 non-walkable OSM까지 같이 import해서 mismatch가 났을 가능성
- custom EV lookup 로직이 geometry를 잘못 매칭했을 가능성
- DB `road_segments`와 GraphHopper edge 분할 기준이 서로 다를 가능성
- self-intersection, loop, repeated node 같은 특수 way를 DB 쪽에서 덜 쪼갰을 가능성

### 3. 검증 방법

- GraphHopper import 대상 `way` 수와 DB `loadedWayCount` 비교
- artifact metadata와 `graph-cache/properties.txt` 확인
- GraphHopper 공식 `WaySegmentParser` 소스 확인
- DB snapshot(`road-segments.jsonl`)에서 loop-like segment와 다분할 way 확인
- walkable OSM way 중 same-way repeated node, closed loop 개수 직접 집계
- strict mode 실패 수치와 repeated/loop way 수의 규모 비교

### 4. 실제 원인

- 원인은 non-walkable OSM 유입이 아니었다.
  - DB loaded way count=`67299`
  - GraphHopper imported way count=`67299`
  - 즉 import 대상 `way` 집합은 이미 일치했다.
- 실제 원인은 DB segmentation과 GraphHopper segmentation semantics 차이였다.
- 특히 주원인은 아래 2가지로 확인됐다.
  - `same-way repeated node`
  - `closed loop(start == end)`
- GraphHopper 11의 `WaySegmentParser`는 같은 `way` 안에서 같은 node가 다시 등장하는 경우와 loop를 split 대상으로 처리한다.
- 반면 현재 DB 쪽은 `OsmPbfEligibleWayReader.computeUsageCount()`에서 `LinkedHashSet`으로 same-way duplicate node를 제거해 usage count를 계산한다.
- 그 결과 self-intersection/loop node가 anchor로 충분히 승격되지 않았고, DB는 덜 쪼개고 GraphHopper는 더 잘게 쪼개는 차이가 발생했다.
- 확인된 보조 근거:
  - walkable way 중 same-way repeated node가 있는 way=`1260`
  - walkable way 중 closed loop way=`628`
  - 두 수의 합=`1888`
  - GraphHopper extra edge=`150188 - 148285 = 1903`
  - 규모가 거의 일치해 mismatch의 주원인으로 판단했다.

### 5. 해결 방법

- 해결 방향은 GraphHopper를 우리 방식으로 바꾸는 것이 아니라, DB segmentation을 GraphHopper 방식에 맞추는 것으로 확정했다.
- 구체적으로는 아래 순서로 수정한다.
  - `computeUsageCount`에서 same-way repeated node를 제거하지 않거나 별도 repeated-node pass를 추가해 anchor 후보로 승격
  - closed loop를 DB builder에서도 GraphHopper와 같은 방식으로 분해
  - self-intersection/loop node를 기준으로 segment 경계를 재계산
  - mismatch 분석 산출물을 추가해 strict mode 실패 시 reason category를 남김
  - `unmatchedSegmentCount=0` 확인 후 `POC_GRAPHHOPPER_IMPORT_FAIL_ON_UNMATCHED_SEGMENT=true`를 기본값으로 복구
- 현재 임시 운영 조치:
  - compose 기본값은 artifact 생성과 load-only 검증을 우선하기 위해 `POC_GRAPHHOPPER_IMPORT_FAIL_ON_UNMATCHED_SEGMENT=false`로 유지

### 6. 배운 점

- GraphHopper import 정합성 문제는 단순 lookup 버그보다 “누가 edge 경계를 최종 결정하느냐”를 먼저 봐야 한다.
- 현재 구조처럼 custom EV를 GraphHopper edge에 bake-in 하는 경우, DB segment 경계가 GraphHopper edge 경계와 맞아야 strict mode가 성립한다.
- OSM way 처리에서 same-way repeated node와 closed loop는 예외 케이스가 아니라 실제 edge 분할 규칙의 핵심이다.
- usage count 계산에서 same-way duplicate를 dedup하는 로직은 네트워크 생성 단계에서는 단순해 보이지만, GraphHopper import 단계와 결합되면 정합성 문제를 만들 수 있다.

### 7. 재발 방지

- GraphHopper import 전용 정합성 체크리스트에 아래 항목을 고정한다.
  - repeated node 처리 여부
  - closed loop 처리 여부
  - `source_osm_from_node_id == source_osm_to_node_id` segment 존재 여부
  - GraphHopper edge 수와 DB segment 수 차이
- strict mode 실패 시 mismatch summary와 샘플 jsonl을 자동으로 남기게 한다.
- self-intersection / loop / repeated node fixture를 테스트에 추가한다.
- 향후 segment builder 수정 시 GraphHopper 공식 split semantics와의 차이를 먼저 검토하고 변경한다.
