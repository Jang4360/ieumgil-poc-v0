# 04. GraphHopper Import, Custom EV, Artifact 운영 계획

## 목적

블루프린트의 GraphHopper import 플로우 `10~13번` 전체를 구현 가능 단위로 정리한다. 핵심은 `road_segments`를 한 번에 읽어 import 중 custom EV를 채우고, 결과 graph artifact를 운영 가능한 단위로 관리하는 것이다.

## 구현 범위

- GraphHopper import job 실행 구조
- `road_segments` bulk load 기반 lookup loader
- custom EV 기록 규칙
- graph artifact 생성과 버전 관리
- 서버 load-only 운영 구조

## 비범위

- 공공데이터 ETL 반영
- 대중교통 후보 생성
- viewer/UI 시각화

## 구현할 내용

### 1. import 입력

- 입력은 아래 3가지로 고정한다.
  - `etl/data/raw/busan.osm.pbf`
  - GraphHopper config 및 custom EV 정의
  - 최종 `road_segments`

### 2. `road_segments` loader

- import 시작 시 `road_segments`를 한 번에 읽어 메모리 lookup map을 만든다.
- import 도중 edge마다 DB query 하지 않는다.
- lookup key는 아래 조합을 사용한다.
  - `source_way_id + source_osm_from_node_id + source_osm_to_node_id`
- `segment_ordinal`은 보조 검증 키로 사용한다.

### 3. custom EV 채우기

- OSM parser가 `way`를 읽고 edge를 만들 때 같은 segment를 lookup으로 찾는다.
- 찾은 segment의 값을 custom EV에 기록한다.
- 이번 단계에서는 public-data ETL을 제외하므로, custom EV 구조와 채움 포인트를 먼저 완성하고 실제 정책 컬럼 유입은 후속 단계로 둔다.

### 4. graph artifact 생성

- import 결과는 새 artifact로 저장한다.
- custom EV 값은 graph 안에 baked-in 되므로 데이터가 바뀌면 재import가 필요하다는 점을 운영 규칙으로 명시한다.
- artifact 메타데이터에는 최소한 아래가 있어야 한다.
  - 사용한 PBF 경로
  - 빌드 시각
  - 코드/설정 revision 또는 build identifier

### 5. 서버 운영

- 운영 서버는 import를 직접 수행하지 않고, 완성된 artifact를 로드하는 구조를 우선한다.
- 배포 단위는 다음 3가지를 함께 관리한다.
  - PBF 스냅샷
  - `road_segments` 스냅샷
  - graph artifact 버전

## 구현 산출물

- GraphHopper import 실행 설계
- `road_segments` bulk loader
- custom EV 매핑 규칙
- artifact 버전 정책과 로딩 절차
- 운영/재생성(runbook) 문서

## 완료 기준

- import가 `road_segments.edge_id`가 아니라 안정 키 lookup으로 동작하도록 설계 또는 코드가 정리된다.
- loader가 import 중 per-edge DB query를 하지 않는다는 점이 문서와 코드에서 확인된다.
- custom EV를 채우는 위치와 책임 경계가 명확하다.
- artifact 재생성 조건과 운영 서버의 load-only 원칙이 문서화된다.

## 재시도 및 리뷰 작성

- import loader, custom EV, artifact 운영 구조가 완료 기준을 만족하지 못하면 원인 수정 후 최대 2회 재시도한다.
- 2회 재시도 후에도 해결 방안이 없으면 `docs/reviews/`에 실패 원인, 병목 구간, 필요한 GraphHopper 확장 포인트를 `md` 파일로 남긴다.
- 성공한 경우에도 `docs/reviews/`에 검증 방식, artifact 메타데이터 정책, 남은 운영 리스크를 `md` 파일로 남긴다.
