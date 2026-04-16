# AGENTS.md

## 목적

이 저장소의 공통 작업 기준을 정리한다. 상세한 백엔드 구현 및 테스트 하네스 규칙은 `poc/AGENTS.md`를 따른다.

## 현재 저장소 기준선

- 기준 설계 문서는 `docs/2026-04-16_ACCESSIBLE_ROUTING_POC_RESTART_BLUEPRINT.md`다.
- 현재 계획 범위는 보행 네트워크 생성 플로우 `2~6번`과 GraphHopper import 플로우 `10~13번`이다.
- 보행 네트워크 생성 플로우 `1번(OSM PBF 확보)`은 완료된 상태로 본다.
- 현재 남겨둔 데이터셋은 `etl/data/raw/busan.osm.pbf` 하나뿐이다.
- 기존 샘플, 파생 CSV, GraphHopper artifact, reference 데이터는 모두 제거된 상태를 기준으로 작업한다.

## 범위

- 구현 대상:
  - 보행 가능 `way` 추출
  - `anchor node` 식별
  - `road_nodes`, `road_segments` 생성과 안정 키 설계
  - GraphHopper import loader, custom EV 채움, graph artifact 생성/로드
- 비범위:
  - 공공데이터 매칭과 ETL
  - 대중교통 오케스트레이션
  - viewer/UI

## 문서 규칙

- 계획 문서는 `docs/plans/`를 기준으로 유지한다.
- 작업 결과는 성공 여부와 관계없이 `docs/reviews/`에 `md` 파일로 남긴다.
- 완료 기준 미달 시 최대 2회 재시도하고, 이후에도 해결 방안이 없으면 실패 리뷰를 남긴다.

## 구현 원칙

- `OSM way`를 그대로 edge로 쓰지 않는다.
- `road_nodes`에는 anchor node만 남긴다.
- `road_segments` 안정 키는 `source_way_id + source_osm_from_node_id + source_osm_to_node_id`를 기준으로 하고 `segment_ordinal`은 보조 검증용으로 유지한다.
- import 단계에서 per-edge 저장소 조회를 하지 않는다.
- 운영은 가능하면 import보다 artifact load 중심으로 설계한다.

## 검증 원칙

- 검증은 단순 `gradle build` 성공만으로 완료 처리하지 않는다.
- 구현 확인이 필요한 작업은 로컬에서 `docker compose`를 실행해 최소한 `backend`, `postgresql`, `graphhopper`가 함께 기동되고 연결되는지 확인한다.
- 통합 실행 검증 결과는 작업 리뷰에 실행 방식과 확인 범위를 함께 남긴다.

## 참조 문서

- 저장소 공통 범위: `docs/plans/01_restart_scope_and_constraints.md`
- 보행 네트워크 생성: `docs/plans/02_way_extraction_anchor_and_segmentation_plan.md`
- 적재/안정 키: `docs/plans/03_network_persistence_and_stable_key_plan.md`
- GraphHopper import: `docs/plans/04_graphhopper_import_and_artifact_plan.md`
- 검증/리뷰: `docs/plans/05_execution_validation_and_review_plan.md`
- 백엔드 세부 가이드: `poc/AGENTS.md`
