# 01. 재시작 범위와 고정 제약

## 목적

`docs/2026-04-16_ACCESSIBLE_ROUTING_POC_RESTART_BLUEPRINT.md`를 기준으로 PoC를 다시 시작하되, 이번 계획 세트는 보행 네트워크 생성 플로우의 `2~6번`과 GraphHopper import 플로우 `10~13번`만 다룬다.

## 현재 기준 상태

- 보행 네트워크 생성 플로우 `1번(OSM PBF 확보)`은 완료된 것으로 본다.
- 현재 남겨둔 원천 데이터셋은 `etl/data/raw/busan.osm.pbf` 하나뿐이다.
- 기존의 샘플, 파생 CSV, GraphHopper artifact, reference 데이터는 모두 제거된 상태를 기준으로 한다.

## 구현 범위

- OSM에서 보행 가능 `way`를 추출하는 로직
- `anchor node` 식별 로직
- `road_segments`, `road_nodes` 생성 로직
- 안정 키 부여 및 저장 구조 설계
- GraphHopper import loader, custom EV 채우기, graph artifact 생성/로딩 절차
- 위 과정을 재현 가능한 백엔드/배치 구조로 정리하는 문서화

## 비범위

- 공공데이터 raw staging 적재
- 공공데이터 매칭 ETL
- `road_segments` 정책 속성 계산 ETL
- ODsay/BIMS/Subway 연동
- viewer/UI 구현

## 구현할 내용

- `raw/busan.osm.pbf`만으로 시작 가능한 실행 경로를 문서와 코드 구조 양쪽에서 고정한다.
- `OSM way`를 그대로 edge로 쓰지 않고, `anchor node` 기반 세그먼트 구조를 기준선으로 확정한다.
- `source_way_id + source_osm_from_node_id + source_osm_to_node_id + segment_ordinal` 안정 키 방향을 코드와 문서에 일관되게 반영한다.
- GraphHopper import는 최종 `road_segments`를 읽어 custom EV를 채우는 구조로 설계한다.
- 운영 서버는 import보다 load 중심으로 동작하도록 경계를 나눈다.

## 완료 기준

- 팀원이 이 문서만 읽고 이번 단계에서 무엇을 구현하고 무엇을 구현하지 않는지 즉시 판단할 수 있다.
- 모든 후속 계획 문서가 `raw/busan.osm.pbf` 단일 원천과 보행 네트워크 `2~6`, GraphHopper import `10~13` 범위를 기준으로 일치한다.
- 공공데이터 ETL과 대중교통 오케스트레이션이 현재 범위 밖임이 문서 전반에서 명확하다.

## 재시도 및 리뷰 작성

- 구현 또는 문서 정렬이 완료 기준을 충족하지 못하면 원인을 수정한 뒤 최대 2회 재시도한다.
- 2회 재시도 후에도 해결 방안이 없으면 `docs/reviews/`에 실패 결과, 시도 내용, 미해결 원인을 `md` 파일로 남긴다.
- 성공한 경우에도 `docs/reviews/`에 성공 결과, 검증 근거, 남은 리스크를 `md` 파일로 남긴다.
