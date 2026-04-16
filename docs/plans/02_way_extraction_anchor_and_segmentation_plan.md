# 02. 보행 가능 Way 추출, Anchor 식별, Segment 생성 계획

## 목적

블루프린트의 보행 네트워크 생성 플로우 `2~4번`을 구현 가능 단위로 정리한다. 핵심은 `busan.osm.pbf`에서 보행 가능 `way`를 추출하고, `anchor node` 기준으로 분해해 `road_segments`를 만드는 것이다.

## 구현 범위

- 보행 가능 `way` 추출 파서
- `eligible_osm_way` 내부 모델 정의
- `anchor node` 식별 규칙 구현
- `way -> road_segments` 분해 로직 구현
- 중간 shape point를 포함한 `LINESTRING` 생성

## 비범위

- DB 적재
- 공공데이터 속성 병합
- GraphHopper import

## 구현할 내용

### 1. 보행 가능 `way` 추출

- `busan.osm.pbf`에서 보행 가능 여부 판단에 필요한 태그를 읽는다.
- 최소 필터 기준은 블루프린트에 적힌 `highway`, `foot`, `sidewalk`, `access`, `crossing`, `steps`, `elevator`를 포함한다.
- 산출물은 `way_id`, 정렬된 node refs, tags, geometry를 담는 내부 모델로 고정한다.

### 2. `anchor node` 식별

- 최소 anchor 기준:
  - `way` 시작 node
  - `way` 끝 node
  - 둘 이상의 보행 가능 `way`에 공통으로 등장하는 node
- 추가 anchor 기준:
  - `crossing`
  - `steps`
  - `elevator`
  - 기타 속성 변화 지점

### 3. segment 생성

- 각 `way`의 ordered node list를 anchor 기준으로 분해한다.
- segment는 최소 아래 필드를 생성해야 한다.
  - `source_way_id`
  - `source_osm_from_node_id`
  - `source_osm_to_node_id`
  - `segment_ordinal`
  - 중간 shape point 포함 `LINESTRING`
- `road_segment`는 `way` 전체가 아니라 anchor 사이 구간이라는 원칙을 코드 수준에서 보장한다.

## 구현 산출물

- PBF 파서 모듈 또는 서비스
- `eligible_osm_way` 내부 모델
- `anchor node` 식별기
- `road_segments` 생성기
- 샘플 입력/출력 검증용 테스트 fixture

## 완료 기준

- `busan.osm.pbf`를 읽어 보행 가능 `way`를 추출할 수 있다.
- 동일 `way`에 대해 anchor 규칙이 결정적이고 반복 실행 시 동일한 결과를 만든다.
- 생성된 segment가 `way` 전체가 아니라 anchor 기반 구간임을 테스트 또는 검증 로그로 확인할 수 있다.
- segment가 `source_way_id`, `source_osm_from_node_id`, `source_osm_to_node_id`, `segment_ordinal`, `LINESTRING`를 모두 가진다.

## 재시도 및 리뷰 작성

- way 추출, anchor 식별, segment 생성이 완료 기준을 만족하지 못하면 원인 수정 후 최대 2회 재시도한다.
- 2회 재시도 후에도 해결 방안이 없으면 `docs/reviews/`에 실패 원인, 막힌 태그 규칙, 대체 접근안을 `md` 파일로 남긴다.
- 성공한 경우에도 `docs/reviews/`에 입력 PBF, 검증 방식, 남은 리스크를 `md` 파일로 남긴다.
