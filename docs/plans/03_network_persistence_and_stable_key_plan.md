# 03. Road Nodes, Road Segments 적재와 안정 키 계획

## 목적

블루프린트의 보행 네트워크 생성 플로우 `5~6번`을 구현 가능 단위로 정리한다. 핵심은 `road_nodes`와 `road_segments` 저장 구조를 정의하고, 안정 키를 일관되게 부여하는 것이다.

## 구현 범위

- `road_nodes` 내부/저장 모델 정의
- `road_segments` 내부/저장 모델 정의
- `anchor node`만 `road_nodes`에 적재하는 규칙 구현
- 안정 키 및 유니크 제약 설계
- 배치 적재 또는 bulk load 경로 설계

## 비범위

- 공공데이터 매칭 컬럼
- 정책 상태 enum 계산
- 대중교통 속성

## 구현할 내용

### 1. `road_nodes` 모델

- 모든 OSM node를 적재하지 않는다.
- 실제 세그먼트 시작/끝으로 사용된 anchor node만 `road_nodes`에 기록한다.
- 최소 필드:
  - `vertex_id`
  - `osm_node_id`
  - `point`

### 2. `road_segments` 모델

- 최소 필드:
  - `edge_id`
  - `from_node_id`
  - `to_node_id`
  - `geom`
  - `length_meter`
  - `source_way_id`
  - `source_osm_from_node_id`
  - `source_osm_to_node_id`
  - `segment_ordinal`

### 3. 안정 키

- 안정 식별 기준:
  - `source_way_id`
  - `source_osm_from_node_id`
  - `source_osm_to_node_id`
  - `segment_ordinal`
- 권장 제약:
  - `UNIQUE (source_way_id, source_osm_from_node_id, source_osm_to_node_id)`
- `source_dataset_version`은 이번 PoC의 필수 안정 키에 넣지 않는다.

### 4. 적재 방식

- segment 생성 결과를 bulk insert 또는 batch write 가능한 구조로 정리한다.
- `road_nodes`와 `road_segments` 생성 순서를 고정한다.
- shape point는 별도 노드 테이블로 분리하지 않고 `road_segments.geom`에 유지한다.

## 구현 산출물

- `road_nodes`/`road_segments` 스키마 초안
- 적재 서비스 또는 배치 설계
- 안정 키 제약 정의
- 중복/역방향/결손 검증 규칙

## 완료 기준

- `road_nodes`에 anchor node만 적재된다는 규칙이 코드 또는 문서로 명확하다.
- `road_segments`가 안정 키와 geometry를 포함한 최소 필드를 모두 가진다.
- 동일 입력을 두 번 처리했을 때 안정 키 기준으로 동일 세그먼트를 식별할 수 있다.
- 적재 순서와 유니크 제약이 문서화되어 GraphHopper import에서 lookup 기준으로 재사용 가능하다.

## 재시도 및 리뷰 작성

- 저장 모델, 적재 구조, 안정 키 설계가 완료 기준을 만족하지 못하면 원인 수정 후 최대 2회 재시도한다.
- 2회 재시도 후에도 해결 방안이 없으면 `docs/reviews/`에 실패 원인, 중복/정합성 문제, 필요한 스키마 수정안을 `md` 파일로 남긴다.
- 성공한 경우에도 `docs/reviews/`에 모델 결정 사항, 검증 근거, 남은 스키마 리스크를 `md` 파일로 남긴다.
