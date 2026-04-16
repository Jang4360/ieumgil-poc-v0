# 2026-04-16 문서 정합성 수정 리뷰

## 대상

- `docs/plans/02_way_extraction_anchor_and_segmentation_plan.md`
- `docs/ARD/erd.md`

## 수행 내용

### 1. 02 계획 문서 보강

- 문서 첫 부분에 `docker compose` 기반 통합 실행 전제를 추가했다.
- 실제 구현 검증은 `backend`, `postgresql`, `graphhopper`를 각각 컨테이너화해 함께 실행하는 기준으로 명시했다.
- 로컬 단일 프로세스 실행은 보조 수단이고, 완료 검증 기준은 compose 기반 통합 실행 성공으로 정리했다.

### 2. ERD의 보행 네트워크 스키마 정렬

- `road_nodes`를 모든 OSM node 저장 구조가 아니라 `anchor node` 저장 구조로 설명을 수정했다.
- `road_segments`에 안정 키 관련 컬럼을 추가했다.
  - `source_way_id`
  - `source_osm_from_node_id`
  - `source_osm_to_node_id`
  - `segment_ordinal`
- `road_segments`의 기존 boolean 중심 컬럼을 블루프린트 기준 상태 enum 컬럼 구조로 교체했다.
  - `braille_block_state`
  - `audio_signal_state`
  - `curb_ramp_state`
  - `width_state`
  - `surface_state`
  - `stairs_state`
  - `elevator_state`
  - `crossing_state`
- 프로필별 경사 상태 컬럼은 `road_segments`에 두지 않고, `avg_slope_percent`를 기준으로 GraphHopper import 또는 EV 채움 단계에서 파생 계산하는 것으로 정리했다.
- ETL 최종 반영 컬럼은 최소값만 유지했다.
  - `walk_access`
- `UNIQUE (source_way_id, source_osm_from_node_id, source_osm_to_node_id)` 제약 기준을 문서에 명시했다.

### 3. `segment_features` 테이블 추가

- `road_segments : segment_features = 1 : N` 관계를 ERD 다이어그램과 관계 명세에 반영했다.
- `segment_features` 테이블을 아래 컬럼으로 정의했다.
  - `feature_id`
  - `edge_id`
  - `feature_type`
  - `geom`
  - `value`
- 개별 feature 표시 위치/구간과 세부 값 추적 용도라는 설명을 추가했다.

## 근거

- 기준 문서 `docs/2026-04-16_ACCESSIBLE_ROUTING_POC_RESTART_BLUEPRINT.md`의 다음 내용을 반영했다.
  - `OSM way`를 anchor 기준 segment로 분해하는 구조
  - `road_segments` 안정 키 기준
  - ETL이 최종 속성을 `road_segments`에 직접 반영하는 구조
  - GraphHopper import에서 상태값 중심 EV를 읽는 구조

## 확인 결과

- 계획 문서와 ERD 문서 모두 현재 블루프린트 기준 용어와 컬럼 구성이 맞도록 정리됐다.
- `segment_features` 추가에 따라 도메인 목록, ERD 다이어그램, 관계 명세가 함께 갱신됐다.
- `road_segments`는 라우팅에 직접 필요한 사실값과 상태값만 저장하고, 프로필별 경사 판정은 영속 컬럼에서 제외하는 방향으로 단순화했다.

## 남은 리스크

- 이번 작업은 문서 수정만 수행했다. 실제 `docker compose` 파일, 컨테이너 이미지, 실행 스크립트는 아직 생성하거나 검증하지 않았다.
- `walk_access`의 실제 허용값과 `segment_features.value`의 직렬화 규칙은 구현 시점에 한 번 더 고정해야 한다.
