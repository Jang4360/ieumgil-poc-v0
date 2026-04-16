# 2026-04-17 05 Success Review

## 범위

- `05_execution_validation_and_review_plan.md` 실행
- 파서/세그먼트 반복 실행 검증
- 저장 구조/안정 키 검증
- import loader / artifact load 검증
- 리뷰 기록 규칙 반영

## 검증 입력

- OSM 입력:
  - `etl/data/raw/busan.osm.pbf`
- network build 출력:
  - `poc/build/network-snapshots/validation-a`
  - `poc/build/network-snapshots/validation-b`
- compose 출력:
  - `runtime/backend-output/latest`
  - `runtime/graphhopper/latest`

## 실행한 검증

### 1. 파서/세그먼트 반복 실행 검증

- 명령:
  - `./gradlew bootRun --args='--spring.main.web-application-type=none --poc.network-build.enabled=true --poc.network-build.source-pbf=../etl/data/raw/busan.osm.pbf --poc.network-build.output-directory=build/network-snapshots/validation-a --poc.network-persistence.enabled=false'`
  - `./gradlew bootRun --args='--spring.main.web-application-type=none --poc.network-build.enabled=true --poc.network-build.source-pbf=../etl/data/raw/busan.osm.pbf --poc.network-build.output-directory=build/network-snapshots/validation-b --poc.network-persistence.enabled=false'`
- 결과:
  - 두 실행 모두 `eligibleWays=67299`
  - `anchors=108793`
  - `roadNodes=109213`
  - `roadSegments=148753`
- 파일 해시:
  - `road-nodes.jsonl`
    - `validation-a`: `15ea748633681e8307588dcf9e880aa2787bdf446909f4f5564d0d042737641a`
    - `validation-b`: `15ea748633681e8307588dcf9e880aa2787bdf446909f4f5564d0d042737641a`
  - `road-segments.jsonl`
    - `validation-a`: `52b1a6ba18a21eb0286ee8ccc070a054c0c244f41038140de0ffe7a1e52883a5`
    - `validation-b`: `52b1a6ba18a21eb0286ee8ccc070a054c0c244f41038140de0ffe7a1e52883a5`
- 판정:
  - 동일 입력 반복 실행 시 동일 snapshot이 나온다: `충족`

### 2. road_nodes anchor-only 구조 검증

- 검증 방식:
  - `validation-a/road-segments.jsonl`의 `sourceOsmFromNodeId`, `sourceOsmToNodeId` endpoint 집합과
  - `validation-a/road-nodes.jsonl`의 `osmNodeId` 집합 비교
- 결과:
  - `road_node_count = 109213`
  - `unique_endpoint_count = 109213`
  - `node_not_used_as_endpoint = 0`
  - `endpoint_without_road_node = 0`
- 판정:
  - `road_nodes`에는 endpoint anchor만 들어간다: `충족`

### 3. 저장 구조 / 안정 키 검증

- 검증 방식:
  - `way/from/to/segment_ordinal` 4-col key 중복 수 집계
  - reverse direction 쌍 존재 여부 확인
- 결과:
  - `duplicate_4col_keys = 0`
  - `directional_pairs_with_reverse = 625`
- 해석:
  - `4-col` 유일성은 유지된다.
  - reverse direction 쌍은 실제 loop / repeated-node 분해 결과로 존재할 수 있으므로 “검출 대상”이지 “오류”로 보지 않는다.
- 판정:
  - 안정 키 중복 없음: `충족`

### 4. import loader 검증

- 코드 기준 확인:
  - [RoadSegmentImportLoader.java](/Users/jangjooyoon/Desktop/JooYoon/ssafy/ieumgil-poc-v0/poc/src/main/java/com/example/poc/graphhopper/persistence/RoadSegmentImportLoader.java:29)에서 `road_segments`를 한 번에 `jdbcTemplate.query(...)`로 bulk load한다.
  - [IeumOsmReader.java](/Users/jangjooyoon/Desktop/JooYoon/ssafy/ieumgil-poc-v0/poc/src/main/java/com/example/poc/graphhopper/service/IeumOsmReader.java:107) 이후 import 중 조회는 메모리 `lookup`만 사용하고 DB 접근이 없다.
- 판정:
  - import 중 per-edge query 없음: `충족`

### 5. artifact 생성 / load 검증

- backend compose:
  - `docker compose down -v`
  - `docker compose up --build --abort-on-container-exit --exit-code-from backend backend`
- graphhopper-importer strict mode:
  - `docker compose up --build --abort-on-container-exit --exit-code-from graphhopper-importer graphhopper-importer`
- 결과:
  - `loadedRoadSegmentCount = 148753`
  - `matchedSegmentCount = 148753`
  - `unmatchedSegmentCount = 0`
  - `graphEdges = 150188`
- graphhopper load-only:
  - `docker compose up -d graphhopper`
  - `curl http://localhost:8989/internal/health`
- health 응답:
  - `{"graphDirectory":"/graphhopper/artifacts/latest/graph-cache","loadedAtUtc":"2026-04-16T16:10:49.427326088Z","ready":true}`
- artifact 경로 확인:
  - `runtime/backend-output/latest/build-metadata.json`
  - `runtime/backend-output/latest/road-nodes.jsonl`
  - `runtime/backend-output/latest/road-segments.jsonl`
  - `runtime/graphhopper/latest/artifact-metadata.json`
  - `runtime/graphhopper/latest/graph-cache/*`
  - `runtime/graphhopper/latest/load-ready.txt`
  - `runtime/graphhopper/latest/mismatch-analysis.jsonl`
  - `runtime/graphhopper/latest/mismatch-summary.json`
- 판정:
  - artifact 생성 및 load-only 경로가 문서와 일치한다: `충족`

## 남은 테스트 공백

- `busan.osm.pbf` 전체를 쓰는 검증은 성공했지만, loop/repeated-node 특수 케이스에 대한 fixture 기반 통합 테스트는 더 보강할 수 있다.
- import loader의 “no per-edge query”는 현재 코드 구조 검증 기준이며 SQL interceptor 기반 런타임 계측은 아직 없다.

## 판정

- `05_execution_validation_and_review_plan.md`: `완료`
- 파서/세그먼트 생성 반복 실행 검증: `완료`
- 저장 구조/안정 키 검증: `완료`
- import loader / artifact load 검증: `완료`
- 리뷰 문서 기록 규칙 적용: `완료`
