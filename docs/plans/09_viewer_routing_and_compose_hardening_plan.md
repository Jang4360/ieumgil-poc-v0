# 09. Viewer Routing And Compose Hardening Plan

## 목적

`08_react_viewer_fe_plan.md` 1차 구현 이후 남아 있는 네 가지 제한사항을, `2026-04-16_ACCESSIBLE_ROUTING_POC_RESTART_BLUEPRINT.md`의 최종 정책 구조에 맞춰 다시 정리한다.

- `SAFE`, `SHORTEST`가 아직 동일한 GraphHopper 기반 geometry를 공유하는 문제
- `TRANSIT_MIXED`가 아직 stub 기반 외부 연동인 문제
- 카카오맵 실제 렌더링 검증이 compose smoke test에 포함되지 않은 문제
- compose에서 `frontend`만 재기동해도 one-off 배치 컨테이너가 다시 수행될 수 있는 문제

이 문서의 핵심은 viewer 하드닝 자체보다, viewer가 사용하는 라우팅 옵션을 `VISUAL/WHEELCHAIR x SAFE_WALK/FAST_WALK/ACCESSIBLE_TRANSIT`의 6개 정책 구조로 고정하고 그에 맞는 backend, GraphHopper, compose 운영 모델을 확정하는 데 있다.

## 블루프린트 기준 고정 규칙

### 1. 사용자 유형과 옵션

- 사용자 유형은 `VISUAL`, `WHEELCHAIR` 두 가지로 고정한다.
- viewer 옵션은 `SAFE`, `SHORTEST`, `TRANSIT_MIXED`를 유지하되, backend 내부 정책명은 아래처럼 매핑한다.
  - `SAFE` -> `SAFE_WALK`
  - `SHORTEST` -> `FAST_WALK`
  - `TRANSIT_MIXED` -> `ACCESSIBLE_TRANSIT`

### 2. GraphHopper 프로필 구조

- GraphHopper는 도보 전용 4개 프로필만 가진다.
  - `visual_safe`
  - `visual_fast`
  - `wheelchair_safe`
  - `wheelchair_fast`
- `ACCESSIBLE_TRANSIT`는 GraphHopper 프로필이 아니라 backend 오케스트레이션 모드로 처리한다.
- `ACCESSIBLE_TRANSIT`의 모든 `WALK` leg는 사용자 유형별 `SAFE_WALK` 정책으로 재계산한다.

### 3. viewer, backend, GraphHopper 매핑

| viewer 입력 | backend 정책 | GraphHopper 사용 방식 |
| --- | --- | --- |
| `VISUAL + SAFE` | `VISUAL + SAFE_WALK` | `visual_safe` |
| `VISUAL + SHORTEST` | `VISUAL + FAST_WALK` | `visual_fast` |
| `VISUAL + TRANSIT_MIXED` | `VISUAL + ACCESSIBLE_TRANSIT` | transit orchestration + `visual_safe` walk legs |
| `WHEELCHAIR + SAFE` | `WHEELCHAIR + SAFE_WALK` | `wheelchair_safe` |
| `WHEELCHAIR + SHORTEST` | `WHEELCHAIR + FAST_WALK` | `wheelchair_fast` |
| `WHEELCHAIR + TRANSIT_MIXED` | `WHEELCHAIR + ACCESSIBLE_TRANSIT` | transit orchestration + `wheelchair_safe` walk legs |

## 유형별 custom model 반영 계획

## 1. `VISUAL + SAFE_WALK` -> `visual_safe`

### 정책 규칙

- `braille_block_state == YES`
- `crossing_state != NO`인 경우 `audio_signal_state == YES`
- `slope_state_visual_safe == PASS`
- `stairs_state != YES`
- `UNKNOWN` 포함 edge 제외

### 반영 방식

- GraphHopper `visual_safe` custom model은 위 항목을 hard filter에 가깝게 반영한다.
- import 단계에서 edge에 아래 EV가 채워져 있어야 한다.
  - `braille_block_state`
  - `crossing_state`
  - `audio_signal_state`
  - `slope_state_visual_safe`
  - `stairs_state`
- viewer 상세 패널의 속성은 동일 edge EV 또는 `road_segments` 보강값에서 일관되게 내려준다.

## 2. `VISUAL + FAST_WALK` -> `visual_fast`

### 정책 규칙

- `braille_block_state == YES`
- `crossing_state != NO`인 경우 `audio_signal_state == YES`
- `slope_state_visual_fast == PASS`
- `UNKNOWN` 포함 edge 제외
- 계단은 보수적으로 처리하되, `SAFE_WALK`보다 시간 우선 정렬을 적용한다.

### 반영 방식

- `visual_fast`는 시각장애인 최소 접근성 제약은 유지하면서 시간 우선 weighting을 사용한다.
- `visual_safe`와 geometry가 달라질 수 있도록 slope threshold, penalty, priority를 분리한다.
- `SAFE`와 `SHORTEST`가 동일 geometry를 내지 않도록 integration test를 추가한다.

## 3. `WHEELCHAIR + SAFE_WALK` -> `wheelchair_safe`

### 정책 규칙

- `stairs_state == NO`
- `crossing_state != NO`인 경우 `curb_ramp_state == YES`
- `surface_state not in {GRAVEL, UNPAVED}`
- `width_state in {ADEQUATE_150, ADEQUATE_120}`
- `slope_state_wheelchair_safe == PASS`
- `UNKNOWN` 포함 edge 제외

### 반영 방식

- `wheelchair_safe`는 계단, 경사, 단차, 폭, 표면을 모두 hard constraint로 가깝게 다룬다.
- import 단계 EV가 아래까지 채워져 있어야 한다.
  - `curb_ramp_state`
  - `surface_state`
  - `width_state`
  - `slope_state_wheelchair_safe`
- viewer 세그먼트 속성은 `경사`, `폭`, `표면`, `단차램프`, `계단 여부`를 우선 노출한다.

## 4. `WHEELCHAIR + FAST_WALK` -> `wheelchair_fast`

### 정책 규칙

- `stairs_state == NO`
- `crossing_state != NO`인 경우 `curb_ramp_state == YES`
- `surface_state not in {GRAVEL, UNPAVED}`
- `width_state in {ADEQUATE_150, ADEQUATE_120}`
- `slope_state_wheelchair_fast == PASS`
- `UNKNOWN` 포함 edge 제외

### 반영 방식

- `wheelchair_fast`는 휠체어 필수 접근성은 유지하고, 경사 허용 범위를 `safe`보다 완화한다.
- `wheelchair_safe`와 `wheelchair_fast`가 같은 경로를 고정적으로 반환하지 않도록 ranking과 penalty를 분리한다.
- backend 응답에는 `appliedProfile`, `policyName`, `rejectedConstraintSummary`를 추가해 디버깅 가능하게 한다.

## 5. `ACCESSIBLE_TRANSIT` 정책

### `VISUAL + ACCESSIBLE_TRANSIT`

- 모든 `WALK` leg는 `VISUAL + SAFE_WALK` 기준 그대로 적용한다.
- transit leg는 정류장/출입구/환승 접근성과 도착 시각 기준으로 평가한다.

### `WHEELCHAIR + ACCESSIBLE_TRANSIT`

- 모든 `WALK` leg는 `WHEELCHAIR + SAFE_WALK` 기준 그대로 적용한다.
- 버스는 저상버스 접근 가능 조건을 검증한다.
- 지하철은 승차역, 하차역, 환승역의 엘리베이터 접근 가능 여부를 검증한다.

### 허용 조합

- `BUS`
- `SUBWAY`
- `BUS + BUS`
- `BUS + SUBWAY`
- `SUBWAY + BUS`
- `SUBWAY + SUBWAY`

제한:

- transit leg 최대 `2`
- 환승 최대 `1`

## 구현 범위

- viewer 옵션과 backend 정책명의 정합성 고정
- GraphHopper 4개 보행 프로필 실구현
- 세그먼트 속성 응답의 custom EV 정합성 확보
- `TRANSIT_MIXED`의 실데이터 연동 전환
- 카카오맵 실렌더링 검증 절차 보강
- compose bootstrap/runtime 분리

## 비범위

- 새로운 사용자 유형 추가
- viewer 시각 디자인 전면 개편
- 공공데이터 ETL 전체 재설계
- 모바일 앱 별도 클라이언트 구현

## 세부 작업 계획

## 1. route contract를 6개 정책 구조로 정렬

### 작업

- frontend enum을 `SAFE`, `SHORTEST`, `TRANSIT_MIXED`로 유지한다.
- backend 내부 enum은 `SAFE_WALK`, `FAST_WALK`, `ACCESSIBLE_TRANSIT`로 고정한다.
- `disabilityType`은 `VISUAL`, `WHEELCHAIR`만 허용한다.
- `routes/search`와 `routes/transit-mixed/candidates` 응답에 아래 필드를 추가하거나 정리한다.
  - `userType`
  - `routeOption`
  - `policyName`
  - `appliedProfile`
  - `resultType`

### 완료 기준

- FE와 BE가 더 이상 `SAFE/SHORTEST/TRANSIT_MIXED`를 임의 문자열로 해석하지 않는다.
- 모든 응답이 어떤 정책과 프로필을 사용했는지 명시한다.

## 2. GraphHopper 4개 보행 프로필 분리

### 작업

- `visual_safe`, `visual_fast`, `wheelchair_safe`, `wheelchair_fast` 4개 프로필을 운영 설정으로 고정한다.
- custom model 파일 또는 생성 로직을 프로필별로 분리한다.
- import 시점에 slope 관련 상태값을 프로필별로 계산해 EV에 채운다.
  - `slope_state_visual_safe`
  - `slope_state_visual_fast`
  - `slope_state_wheelchair_safe`
  - `slope_state_wheelchair_fast`
- 아래 블루프린트 기준 임계값 반영 여부를 확인한다.
  - `visual_safe`: `avg_slope_percent < 5`
  - `visual_fast`: `avg_slope_percent < 8`
  - `wheelchair_safe`: `avg_slope_percent < 3`
  - `wheelchair_fast`: `avg_slope_percent < 5`

### 완료 기준

- 동일 OD 쿼리에서 `SAFE`와 `SHORTEST`가 정책별로 실제 다른 profile을 사용한다.
- geometry diff 검증에서 유형별 safe/fast 차이가 재현된다.

## 3. 세그먼트 속성과 viewer 상세 패널 정합화

### 작업

- route 응답 세그먼트에 가상값 대신 실제 근거 필드를 포함한다.
  - `surfaceState`
  - `widthState`
  - `stairsState`
  - `brailleBlockState`
  - `audioSignalState`
  - `curbRampState`
  - `slopeCategory`
  - `dataSource`
- viewer 상세 패널은 사용자 유형에 따라 우선 노출 속성을 다르게 구성한다.
  - `VISUAL`: 점자블록, 음향신호, 횡단보도, 경사, 계단
  - `WHEELCHAIR`: 경사, 폭, 표면, 턱/램프, 계단
- 경로 선택 확정 후 속성 패널과 지도 overlay가 같은 세그먼트 데이터를 사용하도록 통합한다.

### 완료 기준

- viewer 상세 패널에 남는 주요 속성이 더 이상 mock 값에 의존하지 않는다.
- 응답의 `dataSource`만으로 속성 근거를 추적할 수 있다.

## 4. `TRANSIT_MIXED` 실연동 전환

### 작업

- ODsay에서 상위 `6~10`개 후보를 조회한다.
- 후보를 `WALK`, `BUS`, `SUBWAY` leg로 정규화한다.
- 모든 `WALK` leg를 사용자 유형별 `SAFE_WALK` 프로필로 재계산한다.
- `BUS` leg는 부산 BIMS로 보강한다.
  - 정류장 매핑
  - 노선 매핑
  - 저상버스 여부
  - 버스 도착 예정 시각
- `SUBWAY` leg는 부산 지하철 운행정보로 보강한다.
  - 역 출입구 및 엘리베이터 접근성
  - 열차 도착 예정 시각
  - 환승역 접근성
- 접근성 실패 leg가 하나라도 있으면 후보를 탈락시킨다.
- 최종 상위 `3`개 후보만 반환한다.

### 정렬 기준

- `SAFE_WALK`: 정책 통과, 위험도 낮음, 거리 짧음, 시간 짧음
- `FAST_WALK`: 정책 통과, 시간 짧음, 거리 짧음, 위험도 낮음
- `ACCESSIBLE_TRANSIT`: 접근성 통과, 총 소요시간 짧음, 총 도보거리 짧음, 환승 적음, 대기시간 짧음, 요금 낮음

### 완료 기준

- `TRANSIT_MIXED`가 stub 없이 실데이터 기반 상위 3개 후보를 반환한다.
- 각 후보에 아래 정보가 포함된다.
  - 총 이동시간
  - 목적지 도착 예정시간
  - 버스 도착 예정시간
  - 지하철 도착 예정시간
  - 사용한 대중교통 조합
  - 마커용 정류장/출입구 정보

## 5. 카카오맵 실렌더링 검증 보강

### 작업

- 카카오 개발자 콘솔 허용 도메인에 아래를 등록한다.
  - `http://localhost:3000`
  - 필요 시 `http://127.0.0.1:3000`
- frontend에서 SDK 로드 실패를 명시적으로 노출한다.
- viewer 초기 진입 시 아래를 검사하는 smoke checklist를 문서화한다.
  - 키 누락 여부
  - 허용 도메인 미등록 여부
  - SDK 로드 성공 여부
  - 부산 기본 중심 좌표 렌더링 여부
  - 출발지/도착지 마커 표시 여부
  - 도보/대중교통 overlay 표시 여부
- compose 검증과 별도로 브라우저 기반 수동 검증 결과를 `docs/reviews/`에 남긴다.

### 완료 기준

- compose 성공과 별개로 실제 브라우저에서 지도가 렌더링되는지 확인할 수 있다.
- 키 또는 도메인 문제일 때 사용자와 개발자가 구분 가능한 오류 메시지를 본다.

## 6. compose bootstrap/runtime 분리

### 문제 정의

- 현재 `frontend`, `backend` 재기동 시 `depends_on` 체인 때문에 `network-builder`, `graphhopper-importer` 같은 one-off 서비스가 다시 수행될 수 있다.
- 개발 루프와 초기 artifact 생성 루프가 분리되어 있지 않다.

### 권장안

- `bootstrap` profile
  - `network-builder`
  - `graphhopper-importer`
- `runtime` profile
  - `postgresql`
  - `graphhopper`
  - `backend`
  - `frontend`
- runtime 서비스는 one-off 서비스에 직접 `depends_on`하지 않는다.
- runtime은 artifact 존재 여부만 검사하고, 없으면 명시적으로 실패한다.

### 실행 모델

초기 bootstrap:

```powershell
docker compose --profile bootstrap up --build
```

일상 개발 및 실행:

```powershell
docker compose --profile runtime up -d
```

### 추가 보완

- GraphHopper artifact ready marker 또는 파일 존재 검사를 추가한다.
- README와 운영 문서에 bootstrap/runtime 명령을 분리 기재한다.
- `frontend` 단독 재기동 시에는 bootstrap 서비스가 다시 실행되지 않도록 compose 구조를 고정한다.

### 완료 기준

- `docker compose up -d frontend` 또는 `docker compose up -d backend` 실행 시 one-off 서비스가 다시 실행되지 않는다.
- artifact가 없는 상태에서는 runtime이 원인을 설명하고 즉시 실패한다.

## 권장 작업 순서

1. route contract와 6개 정책 구조 정렬
2. GraphHopper 4개 보행 프로필 분리
3. 세그먼트 속성 응답 정합화
4. `TRANSIT_MIXED` 실연동 전환
5. 카카오맵 브라우저 검증 절차 추가
6. compose bootstrap/runtime 분리

## 검증 계획

- 동일 OD에 대해 `VISUAL SAFE` vs `VISUAL SHORTEST` geometry diff 검증
- 동일 OD에 대해 `WHEELCHAIR SAFE` vs `WHEELCHAIR SHORTEST` geometry diff 검증
- `ACCESSIBLE_TRANSIT`의 모든 `WALK` leg가 유형별 `SAFE_WALK` 프로필을 쓰는지 검증
- `TRANSIT_MIXED` 상위 3개 후보의 버스/지하철 도착정보 실데이터 검증
- 브라우저에서 카카오맵 실제 렌더링 확인
- compose runtime만 재기동할 때 bootstrap 서비스가 재실행되지 않는지 확인
- 결과는 성공/실패 여부와 함께 `docs/reviews/`에 기록

## 완료 기준

- viewer 라우팅이 블루프린트의 `VISUAL/WHEELCHAIR x SAFE_WALK/FAST_WALK/ACCESSIBLE_TRANSIT` 6개 정책 구조와 일치한다.
- `SAFE`, `SHORTEST`는 더 이상 동일한 GraphHopper geometry를 공유하지 않는다.
- `TRANSIT_MIXED`는 실데이터 기반 오케스트레이션으로 동작한다.
- 카카오맵은 실제 브라우저에서 렌더링 검증 가능 상태가 된다.
- compose는 bootstrap과 runtime이 분리되어 개발 루프에서 one-off 배치가 재실행되지 않는다.
