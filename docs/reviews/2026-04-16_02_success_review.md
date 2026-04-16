# 02 보행 가능 Way 추출, Anchor 식별, Segment 생성 실행 리뷰

## 대상 계획

- plan: `02_way_extraction_anchor_and_segmentation_plan`

## 결과

- status: `success`
- success criteria met: `yes`
- retry count: `0`

## 수행 내용

- `poc`에 `busan.osm.pbf` 단일 입력을 기준으로 동작하는 네트워크 생성 배치 진입점과 설정을 추가했다.
- `osmosis-pbf` 기반 2-pass reader를 구현해 보행 가능 `way`만 추출하고, 참조 node 좌표와 node tag를 결합하도록 구성했다.
- 순수 로직으로 `anchor node` 식별, `road_segments` 분해, `road_nodes` 생성, 안정 키 중복 검증을 구현했다.
- 이후 `docs/ARD/erd.md`를 기준으로 `road_nodes.vertexId`, `road_segments.edgeId`, `from_node_id`, `to_node_id`를 숫자 PK/FK 구조로 재정렬했고, geometry 및 기본 상태 컬럼(`walk_access`, `*_state`)을 ERD 기본값 `UNKNOWN` 기준으로 반영했다.
- 결과를 `build/network-snapshots/latest` 아래 `build-metadata.json`, `road-nodes.jsonl`, `road-segments.jsonl`로 저장하는 스냅샷 writer를 추가했다.
- 후속 GraphHopper import에서 재사용할 수 있도록 `road-segments.jsonl` bulk load reader와 in-memory lookup index를 추가했다.
- 루트 `docker-compose.yml`, `poc/Dockerfile`, `graphhopper/Dockerfile`, `.dockerignore`를 추가해 `backend + postgresql + graphhopper` compose 실행 경로를 고정했다.
- `graphhopper`는 `02` 범위상 실제 import가 아니라 compose 검증용 placeholder 컨테이너로 두고, import 구현은 후속 plan으로 넘겼다.

## 수정 파일

- `poc/build.gradle`
- `poc/Dockerfile`
- `poc/src/main/java/com/example/poc/PocApplication.java`
- `poc/src/main/resources/application.yaml`
- `poc/src/main/java/com/example/poc/network/**`
- `poc/src/test/java/com/example/poc/network/**`
- `poc/src/test/resources/junit-platform.properties`
- `graphhopper/Dockerfile`
- `docker-compose.yml`
- `.dockerignore`

## 검증 근거

- `./gradlew test` 실행 성공
- `./gradlew bootRun --args='--spring.main.web-application-type=none --poc.network-build.enabled=true'` 실행 성공
- `docker` CLI가 현재 환경에 없어 `docker compose` 실실행은 미수행
- 대신 `ruby` YAML 파서로 `docker-compose.yml` 문법과 service key(`backend`, `graphhopper`, `postgresql`)를 검증
- compose와 같은 출력 경로를 흉내 내기 위해 `./gradlew bootRun --args='--spring.main.web-application-type=none --poc.network-build.enabled=true --poc.network-build.output-directory=../runtime/backend-output/latest --poc.network-build.build-identifier=compose-path-local --poc.network-build.code-revision=local-no-docker'` 실행 성공
- 실제 실행 결과:
  - `eligibleWayCount = 67299`
  - `referencedNodeCount = 462869`
  - `anchorNodeCount = 108769`
  - `roadNodeCount = 108769`
  - `roadSegmentCount = 148285`
  - `duplicateStableKeyCount = 0`
- 스냅샷 출력 확인:
  - `poc/build/network-snapshots/latest/build-metadata.json`
  - `poc/build/network-snapshots/latest/road-nodes.jsonl` line count = `108769`
  - `poc/build/network-snapshots/latest/road-segments.jsonl` line count = `148285`
  - `road_nodes` 샘플은 `vertexId`, `osmNodeId`, `point` 형태로 출력됨
  - `road_segments` 샘플은 `edgeId`, `fromNodeId`, `toNodeId`, `geom`, `lengthMeter`, 안정 키 컬럼, `walkAccess`, `brailleBlockState`, `audioSignalState`, `curbRampState`, `widthState`, `surfaceState`, `stairsState`, `elevatorState`, `crossingState`를 포함함
  - `runtime/backend-output/latest/build-metadata.json`
  - `runtime/backend-output/latest/road-nodes.jsonl` line count = `108769`
  - `runtime/backend-output/latest/road-segments.jsonl` line count = `148285`

## 성공 기준 판정

- `busan.osm.pbf`를 읽어 보행 가능 `way`를 추출할 수 있다: `충족`
- 동일 입력에서 anchor 규칙과 segment 분해가 결정적으로 동작한다: `충족`
- 생성된 segment가 `source_way_id`, `source_osm_from_node_id`, `source_osm_to_node_id`, `segment_ordinal`, `LINESTRING`를 모두 가진다: `충족`
- 후속 import가 per-edge 저장소 조회 없이 bulk snapshot load를 사용할 수 있는 구조가 준비됐다: `충족`
- compose 기준 실행 정의(`docker-compose.yml`, backend/graphhopper Dockerfile)는 준비됐지만, 실제 `docker compose up` 성공 여부는 현재 환경에서 미검증이다: `부분 충족`

## 이슈 및 리스크

- 현재 보행 가능 `way` 필터는 최소 규칙 위주라 부산 전역 정책 세밀화 전에는 과다/과소 포함 가능성이 있다.
- `codeRevision` 메타데이터는 아직 설정값 기반이라 CI 또는 release 단계에서 git revision 주입이 추가로 필요하다.
- 현재 출력은 파일 스냅샷 기준이며, 실제 DB 적재와 GraphHopper custom EV 채움은 다음 단계에서 이어서 붙여야 한다.
- `graphhopper` 컨테이너는 현재 placeholder라 실제 import/load 검증은 `04_graphhopper_import_and_artifact_plan` 단계에서 대체 구현이 필요하다.
- 현재 작업 환경에는 `docker` CLI가 없어 compose 실실행 검증은 별도 환경에서 한 번 더 확인해야 한다.

## 다음 액션

- `03_network_persistence_and_stable_key_plan` 기준으로 DB 적재 스키마와 batch write 경로를 추가한다.
- `04_graphhopper_import_and_artifact_plan` 기준으로 snapshot bulk loader를 GraphHopper import 확장 포인트에 연결한다.
- 보행 가능 `way` 필터 규칙과 anchor 승격 규칙을 fixture 기반으로 더 촘촘히 보강한다.
