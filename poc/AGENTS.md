# AGENTS.md

## 목적

이 문서는 `poc/` 백엔드 작업의 운영 기준과 테스트 하네스 기준을 고정한다. 현재 백엔드의 1차 목표는 `docs/2026-04-16_ACCESSIBLE_ROUTING_POC_RESTART_BLUEPRINT.md`를 따라 보행 네트워크 생성 플로우 `2~6번`과 GraphHopper import 플로우 `10~13번`을 재현 가능한 방식으로 구현하는 것이다.

## 현재 범위

- 입력 원천은 `../etl/data/raw/busan.osm.pbf` 하나만 사용한다.
- 공공데이터 매칭과 ETL은 이번 단계에서 구현하지 않는다.
- 목표는 `OSM way -> anchor node -> road_segments/road_nodes -> GraphHopper import -> graph artifact` 흐름을 안정적으로 만드는 것이다.
- 운영 서버는 import 실행기보다 artifact 로더에 가깝게 설계한다.

## 작업 단위 원칙

- `OSM way`를 그대로 서비스 edge로 쓰지 않는다.
- 모든 OSM node를 적재하지 않는다. 실제 연결에 쓰인 anchor node만 `road_nodes`에 남긴다.
- segment 안정 키는 `source_way_id + source_osm_from_node_id + source_osm_to_node_id`를 기준으로 삼고 `segment_ordinal`은 보조 검증용으로 유지한다.
- import 단계에서 edge마다 저장소를 다시 조회하지 않는다. `road_segments`는 import 시작 시 bulk load 후 메모리 lookup으로 사용한다.
- artifact는 재생성 가능한 산출물로 취급한다. 데이터나 config가 바뀌면 재import 후 새 artifact를 만든다.

## 운영 가이드라인

- 입력, 중간 산출물, 최종 artifact의 경계를 분리한다.
- 운영 메타데이터에는 최소한 PBF 경로, 빌드 시각, 코드 revision, build identifier를 남긴다.
- release 또는 배포는 테스트를 통과한 revision 기준으로만 만든다.
- release 시 사용하는 테스트 타깃과 CI에서 상시 돌리는 테스트 타깃을 가능하면 같게 유지한다.
- import 실행기는 재실행 가능해야 하고, 동일 입력과 동일 설정이면 같은 세그먼트 식별 기준을 산출해야 한다.
- 운영 환경에서는 import보다 load를 우선하고, import는 별도 작업으로 분리한다.
- import와 artifact 생성 결과는 실행 이력으로 추적 가능해야 한다. 최소한 way 수, anchor 수, segment 수, 중복 키 수, artifact 출력 경로를 남긴다.

## 검증 기준

- 검증은 단순 `./gradlew build` 또는 테스트 태스크 성공만으로 끝내지 않는다.
- 실제 구현 확인이 필요한 변경은 로컬에서 `docker compose`를 실행해 `backend`, `postgresql`, `graphhopper`를 함께 올리고 동작을 확인한다.
- 최소한 애플리케이션 기동, DB 연결, GraphHopper 연계 또는 artifact 로드 경로 중 변경 영향이 있는 부분은 compose 환경에서 직접 확인한다.
- 리뷰 문서에는 실행한 compose 명령, 확인한 컨테이너, 통과/실패 범위를 요약해 남긴다.

## 백엔드 구조 가이드

- 파서, segment 생성기, 저장 모델, GraphHopper import adapter를 분리한다.
- 순수 계산 로직은 Spring Bean에 과도하게 묶지 말고 일반 Java 객체로 두어 단위 테스트를 쉽게 만든다.
- HTTP API, 배치/import, artifact 로더는 각각 다른 진입점으로 분리한다.
- 설정값은 경로, 플래그, 출력 디렉터리, import 옵션을 포함해 외부화한다.
- 테스트용 fixture 경로와 운영용 대용량 PBF 경로를 혼용하지 않는다.
- 시간, 난수, 파일 경로, 정렬 순서 같은 외부 요인은 주입 가능하게 만들어 결정적 실행을 보장한다.

## 하네스 엔지니어링 베스트 프랙티스

### 1. 테스트 계층을 분리한다

- 순수 파서/anchor/segment 로직은 빠른 단위 테스트로 검증한다.
- Spring 계층 테스트는 `@WebMvcTest`, `@DataJpaTest` 같은 슬라이스 테스트를 우선 사용한다.
- `@SpringBootTest`는 실제로 여러 계층을 함께 확인해야 할 때만 제한적으로 사용한다.

### 2. 테스트 설정은 저장소에 고정한다

- JUnit 실행 기본값은 `src/test/resources/junit-platform.properties`에 둔다.
- IDE와 CI가 다른 JVM 옵션으로 실행되어 테스트 수명주기나 병렬 설정이 달라지지 않게 한다.
- 병렬 실행은 기본 활성화가 아니라, 공유 자원 잠금과 실행 시간이 검증된 뒤에만 켠다.

### 3. 입력 fixture는 작고 결정적으로 유지한다

- 대부분의 테스트는 작은 OSM fixture 또는 직접 구성한 domain fixture를 사용한다.
- `busan.osm.pbf` 전체 파일은 smoke test나 장시간 import 검증에만 사용한다.
- 테스트가 로컬 파일 시스템의 우연한 상태에 의존하지 않게 입력 경로와 출력 경로를 명시한다.
- 출력은 테스트마다 독립된 임시 디렉터리를 사용하고, 테스트 종료 시 정리 규칙을 명확히 한다.

### 4. 정책/태그 조합은 parameterized test로 커버한다

- 태그 조합, anchor 분기, segment 경계, 안정 키 생성 규칙은 parameterized test로 묶는다.
- repeated test는 flake를 숨기기 위한 용도가 아니라 재현이 어려운 경계 상황 확인에만 제한적으로 쓴다.

### 5. 실제 인프라 검증은 Testcontainers를 사용한다

- PostgreSQL 등 실제 의존성이 필요한 통합 테스트는 Testcontainers를 우선 사용한다.
- Spring Boot 연동 시 `@ServiceConnection`을 우선 검토한다.
- 컨테이너 시작 비용이 큰 경우에만 수동 lifecycle 또는 singleton container 패턴을 검토하고, 그 외에는 테스트 격리를 우선한다.

### 6. 서버 테스트는 랜덤 포트를 사용한다

- 실행 중인 서버를 띄우는 테스트는 random port를 사용한다.
- 포트 충돌에 의존한 flaky test를 만들지 않는다.

### 7. 실패 증거를 남긴다

- 세그먼트 개수, 중복 안정 키, 누락 lookup, import 시간, artifact 경로를 로그와 검증 결과로 남긴다.
- import 실패 시 입력 PBF, config, build identifier를 바로 추적할 수 있어야 한다.
- 대규모 import 검증은 결과 요약 파일을 남겨 회귀 비교 가능하게 한다.
- 파서 단계에서는 필터링된 way 수, anchor 승격 사유, segment 분해 수를 남겨 원인 추적 가능하게 한다.

### 8. 하네스는 실제 배포 경로를 닮아야 한다

- CI에서 도는 검증 중 일부는 실제 release와 같은 방식으로 artifact를 만든 뒤 로드까지 확인해야 한다.
- release branch 또는 release candidate는 실제 배포 대상과 동일한 테스트를 다시 통과해야 한다.

### 9. 느린 검증과 빠른 검증을 분리한다

- 빠른 피드백용 테스트는 작은 fixture, 단위 테스트, 슬라이스 테스트 중심으로 유지한다.
- `busan.osm.pbf` 전체를 쓰는 import 검증은 smoke 또는 `slow` 계열로 분리한다.
- 느린 검증은 merge 또는 release 직전에 다시 실행하되, 개발 중 기본 테스트 루프를 막지 않게 한다.

## 테스트 작성 원칙

- 테스트 이름은 규칙, 입력, 기대 결과가 드러나게 적는다.
- 한 테스트가 여러 실패 원인을 동시에 숨기지 않게 한다.
- 안정 키와 geometry 같은 핵심 필드는 snapshot 또는 명시 assertion으로 검증한다.
- import 관련 테스트는 속도만 보지 말고 lookup 정합성과 artifact 메타데이터도 확인한다.
- flaky test가 생기면 반복 횟수를 늘리는 대신 공유 자원, 시간 의존성, 순서 의존성을 제거한다.
- Map, Set, stream 순서가 결과에 영향을 주는 부분은 정렬 기준을 명시하고 테스트도 같은 기준으로 검증한다.
- 네트워크 호출은 기본 단위/슬라이스 테스트에서 금지하고, 필요한 경우 통합 테스트에서만 명시적으로 허용한다.

## 피해야 할 것

- `busan.osm.pbf` 전체 파일을 모든 테스트 기본 입력으로 사용하는 것
- import 중 edge마다 DB query 하는 것
- sample 데이터셋이 다시 생긴다는 가정으로 코드를 짜는 것
- 로컬 머신 상태에 따라 달라지는 절대 경로, 고정 포트, 현재 시각 의존 테스트
- artifact에 어떤 코드/설정으로 만들어졌는지 추적 정보가 없는 상태로 배포하는 것

## 문서와 리뷰

- 구현 범위가 바뀌면 먼저 `docs/plans/`를 수정한다.
- 작업 결과는 성공 여부와 관계없이 `docs/reviews/`에 남긴다.
- 성공 기준 미달 시 최대 2회 재시도하고, 이후에도 해결 방안이 없으면 실패 리뷰를 남긴다.
