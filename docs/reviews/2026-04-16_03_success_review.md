# 03 Road Nodes, Road Segments 적재와 안정 키 실행 리뷰

## 대상 계획

- plan: `03_network_persistence_and_stable_key_plan`

## 결과

- status: `success`
- success criteria met: `yes`
- retry count: `0`

## 수행 내용

- `road_nodes`, `road_segments`를 ERD 기준으로 PostGIS 스키마로 정의하는 SQL(`poc/src/main/resources/sql/network-schema.sql`)을 추가했다.
- `road_segments`에 `UNIQUE (source_way_id, source_osm_from_node_id, source_osm_to_node_id)` 제약과 `from_node_id`, `to_node_id` FK를 반영했다.
- `poc.network-persistence.*` 설정, 조건부 DataSource/JdbcTemplate 구성, batch insert 기반 적재 서비스(`RoadNetworkPersistenceService`)를 추가했다.
- 네트워크 생성 배치가 스냅샷 파일을 쓴 뒤 persistence가 활성화돼 있으면 같은 결과를 DB에도 적재하도록 연결했다.
- `docker-compose.yml`의 DB 이미지를 `postgis/postgis:16-3.4-alpine`으로 변경하고, `postgresql/init/00-enable-postgis.sql`로 compose DB 초기화 시점에 PostGIS extension과 DB 권한을 준비하도록 바꿨다.
- backend는 compose가 준비한 PostGIS DB에 스키마/데이터만 적재하도록 정리했다.

## 수정 파일

- `poc/build.gradle`
- `poc/src/main/resources/application.yaml`
- `poc/src/main/resources/sql/network-schema.sql`
- `postgresql/init/00-enable-postgis.sql`
- `poc/src/main/java/com/example/poc/network/batch/NetworkBuildService.java`
- `poc/src/main/java/com/example/poc/network/persistence/**`
- `poc/src/test/java/com/example/poc/network/persistence/**`
- `docker-compose.yml`

## 검증 근거

- `./gradlew test` 실행 성공
- `./gradlew bootRun --args='--spring.main.web-application-type=none --poc.network-build.enabled=true --poc.network-build.output-directory=build/network-snapshots/latest --poc.network-persistence.enabled=false'` 실행 성공
- `ruby` YAML 파서로 `docker-compose.yml`을 읽어 `postgresql -> postgis/postgis:16-3.4-alpine`, `backend -> poc/Dockerfile`, `graphhopper -> graphhopper/Dockerfile` 구성을 확인했다.
- `network-schema.sql`에 PK/FK, stable key unique constraint, lookup index가 모두 정의된 것을 확인했다.
- `postgresql/init/00-enable-postgis.sql`이 compose 초기화 시점에 PostGIS extension을 준비하도록 정의된 것을 확인했다.
- `RoadNetworkPersistenceServiceTest`는 Docker가 있는 환경에서는 PostGIS Testcontainers로 schema 생성과 insert를 검증하도록 추가했다.

## 성공 기준 판정

- `road_nodes`에 anchor node만 적재하는 구조가 코드와 스키마에서 분명하다: `충족`
- `road_segments`가 geometry와 안정 키 최소 필드를 모두 가진다: `충족`
- 동일 입력을 두 번 처리해도 stable key 기준으로 같은 segment를 식별할 수 있는 제약과 lookup 구조가 준비됐다: `충족`
- 적재 순서와 유니크 제약이 GraphHopper import 재사용 기준으로 정리됐다: `충족`

## 이슈 및 리스크

- 현재 작업 환경에는 `docker`와 `psql`이 없어 실제 `docker compose up` 또는 로컬 PostGIS 적재를 직접 실행해 보지는 못했다.
- compose 초기화 스크립트는 빈 Postgres volume에서만 자동 실행되므로, 기존 `postgres-data` volume이 남아 있으면 `docker compose down -v` 후 다시 올려야 한다.
- `RoadNetworkPersistenceServiceTest`는 Docker가 없으면 조용히 종료되므로, CI 또는 Docker 가능한 개발 환경에서 한 번은 실제 PostGIS insert를 확인해야 한다.
- `road_segments`의 정책 상태 컬럼은 현재 `UNKNOWN` 기본값으로만 적재되며, 실제 ETL 해석값 반영은 후속 단계에서 필요하다.
- 아직 `segment_features` 적재는 구현하지 않았다. 이번 단계 범위는 `road_nodes`, `road_segments`에 한정했다.

## 다음 액션

- Docker 가능한 환경에서 `docker compose up --build --abort-on-container-exit --exit-code-from backend backend`로 실제 PostGIS 적재를 검증한다.
- `04_graphhopper_import_and_artifact_plan`에서 DB에 적재된 `road_segments`를 bulk load하는 GraphHopper import 경로를 연결한다.
- 필요하면 `segment_features` 스키마/적재를 분리 구현해 공공데이터 feature trace를 보강한다.
