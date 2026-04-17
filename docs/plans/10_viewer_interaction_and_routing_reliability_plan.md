# 10. Viewer 상호작용 및 라우팅 신뢰성 보완 계획

## 목적

현재 viewer는 정적 렌더링과 API 연결까지는 올라와 있지만, 실제 사용자 조작과 실데이터 신뢰성 측면에서 아래 문제가 남아 있다.

- 출발지, 도착지 `지도에서 선택` 버튼 클릭 후 지도에 마커가 기대대로 배치되지 않음
- 지도 선택 모드에서 지도를 움직이기 어렵거나 상호작용이 막힌 것처럼 보임
- 도착지 선택이 안정적으로 동작하지 않음
- GraphHopper 프로필 분리 배선은 들어갔지만 실제 imported graph에서 `SAFE_WALK`와 `FAST_WALK` 차이가 검증되지 않음
- `TRANSIT_MIXED`는 아직 ODsay, BIMS, 부산 지하철 운행정보 실연동이 아니라 stub 기반임
- 카카오맵은 compose와 정적 페이지 제공까지만 확인됐고 실제 브라우저 렌더링과 사용자 상호작용까지는 검증되지 않음

이 문서는 위 문제를 `UI 상호작용 복구`, `보행 라우팅 신뢰성`, `대중교통 실연동`, `브라우저 검증 체계`의 네 축으로 나눠서 정리한다.

## 범위

- FE viewer의 지도 선택 UX와 마커/오버레이 동작 복구
- GraphHopper 프로필별 실제 경로 차등 검증 및 보정
- `TRANSIT_MIXED` 외부 API 실연동 설계와 구현 계획
- 카카오맵 실제 브라우저 검증 절차와 오류 대응 정리

## 비범위

- 새로운 사용자 유형 추가
- 모바일 앱 별도 구현
- 대중교통 요금/정산 고도화
- 디자인 전면 리뉴얼

## 1. 지도 상호작용 UX 복구

### 문제 정의

- 현재 `지도에서 선택` 버튼을 누른 뒤 사용자가 지도를 클릭해도 출발지/도착지 선택이 기대대로 반영되지 않는 경우가 있다.
- 마커가 상태 전환 직후 즉시 보이지 않거나, 기존 오버레이 정리와 새 마커 반영 순서가 충돌할 가능성이 있다.
- 지도 클릭으로 포인트를 찍는 동안 사용자는 지도를 드래그해서 위치를 미세 조정할 수 있어야 하는데, 현재 UX는 클릭 모드와 탐색 모드가 명확히 분리되지 않았다.
- 도착지 선택은 출발지보다 실패율이 높아 보이며, `pickMode` 상태 전환과 비동기 주소 변환(`coord2Address`) 사이의 타이밍 문제 가능성이 있다.

### 작업 목표

- 출발지/도착지 선택이 명확히 구분되고, 지도 이동과 위치 선택이 동시에 가능한 UI로 바꾼다.
- 선택 직후 마커와 라벨 오버레이가 항상 일관되게 갱신되도록 만든다.
- 사용자가 현재 어떤 선택 모드인지, 다음 클릭이 무엇을 의미하는지 명확히 볼 수 있게 한다.

### 구현 방향

- `pickMode`를 단순 문자열 상태가 아니라 명시적 상태머신으로 바꾼다.
  - `idle`
  - `picking-start`
  - `picking-end`
- 지도 클릭만으로 즉시 확정하지 말고, 아래 두 단계 UX를 고려한다.
  - 지도 이동/확대 축소 가능
  - 중앙 십자선 또는 임시 마커 표시
  - `이 위치로 설정` 버튼으로 확정
- 또는 기존 클릭 확정 UX를 유지할 경우에도 아래 보완을 넣는다.
  - 클릭 즉시 임시 마커 렌더
  - 주소 역지오코딩 실패 시 좌표 기반 라벨로 fallback
  - 출발지/도착지별 마커 스타일 분리
- 지도 오브젝트 정리 로직을 재검토한다.
  - 기존 `clearMapArtifacts`가 선택용 마커와 경로 마커를 한 번에 지우지 않도록 역할을 분리
  - `selection markers`, `route overlays`, `transit markers`를 별도 ref 컬렉션으로 관리
- `selectedWalkRoute`, `selectedTransitCandidate`, `startPoint`, `endPoint` 렌더 순서를 재조정한다.
  - 포인트 선택용 마커는 항상 유지
  - 경로 선택 시에만 polyline 추가
  - transit marker는 transit 후보 확정 시에만 추가
- 클릭 이벤트와 드래그 이벤트가 충돌하지 않는지 확인한다.
  - 클릭 threshold 검토
  - 드래그 직후 클릭 오인식 방지

### 완료 기준

- 출발지와 도착지 모두 지도에서 안정적으로 선택 가능
- 선택 직후 마커가 즉시 보임
- 지도 드래그/확대 축소가 자연스럽게 동작함
- 경로 표시 후에도 포인트 마커가 사라지지 않음

## 2. FE 상태 구조 정리

### 문제 정의

- 현재 viewer는 `selectedWalkRoute`, `selectedTransitCandidate`, `routeSearchData`, `pickMode`가 느슨하게 연결되어 있어 화면 갱신 순서가 꼬일 가능성이 있다.
- 경로 선택과 포인트 선택이 동시에 일어나면 어떤 상태가 우선인지 코드만 보고 분명하지 않다.

### 구현 방향

- FE 상태를 아래 도메인으로 나눈다.
  - `selectionState`
  - `routeState`
  - `mapPresentationState`
- 각 상태의 전이 규칙을 명시한다.
  - 출발지/도착지 변경 시 선택된 경로 초기화
  - `TRANSIT_MIXED` 후보 선택 시 walk route 선택 해제
  - 경로 재검색 시 이전 transit 후보/오버레이 초기화
- 필요한 경우 reducer 기반 상태 전환으로 이동한다.

### 완료 기준

- 포인트 선택, 경로 선택, 지도 표시가 서로 독립적으로 갱신됨
- `도착지 선택 불가`, `마커 누락`, `경로 선택 후 상태 꼬임`이 재현되지 않음

## 3. GraphHopper 실제 프로필 차등 검증

### 문제 정의

- 현재 응답의 `appliedProfile`은 `visual_safe`, `visual_fast`, `wheelchair_safe`, `wheelchair_fast`로 내려가지만, smoke query의 geometry는 fallback처럼 보였다.
- 이는 아래 셋 중 하나일 수 있다.
  - 실제 GraphHopper route 실패 후 fallback으로 내려감
  - GraphHopper는 응답하지만 프로필별 차등이 충분하지 않음
  - import된 EV/custom model 조건이 실제 edge에 과도하게 적용되어 usable graph가 거의 비어 있음

### 작업 목표

- backend가 실제 GraphHopper route를 사용했는지 응답과 로그로 분명히 확인할 수 있게 한다.
- 프로필별로 다른 geometry가 재현되는 known OD 세트를 만든다.
- fallback이 발생하는 정확한 이유를 진단하고 제거한다.

### 구현 방향

- route 응답에 아래 디버그 필드를 추가하는 방안을 검토한다.
  - `graphHopperBacked`
  - `fallbackUsed`
  - `fallbackReason`
- `TransitWalkLegRouter`에 route 실패 로그를 보강한다.
  - profile name
  - request lat/lng
  - GraphHopper error message
- known OD fixture를 정의한다.
  - 시각장애인 safe vs fast 차이가 나야 하는 구간
  - 휠체어 safe vs fast 차이가 나야 하는 구간
- import 결과 EV 분포를 진단한다.
  - `ieum_visual_safe_pass`
  - `ieum_visual_fast_pass`
  - `ieum_wheelchair_safe_pass`
  - `ieum_wheelchair_fast_pass`
- 필요 시 아래를 수정한다.
  - slope threshold
  - stairs / curb ramp / audio signal unknown 처리
  - custom model priority와 distance influence

### 검증 계획

- 동일 OD에서 `SAFE`와 `SHORTEST` geometry diff 비교
- 로그 상 GraphHopper error 여부 확인
- fallback이 아닌 실제 GraphHopper path 사용 여부 확인

### 완료 기준

- known OD 기준으로 safe/fast 차등 경로가 재현됨
- `graphHopperBacked = true` 경로가 정상적으로 내려옴
- fallback은 예외적 상황에서만 발생하고 원인이 기록됨

## 4. `TRANSIT_MIXED` 실데이터 연동

### 문제 정의

- 현재 `TRANSIT_MIXED`는 후보 구조와 UI만 있고 실제로는 stub client가 응답을 생성한다.
- 따라서 총 이동시간, 도착 예정 시간, 버스/지하철 도착 시간, 저상버스 여부, 역 접근성 등이 실제 외부 데이터와 맞지 않는다.

### 작업 목표

- ODsay, 부산 BIMS, 부산 지하철 운행정보를 실제로 호출해 상위 3개 후보를 구성한다.
- 각 후보의 `WALK` leg는 사용자 유형별 `SAFE_WALK`로 재계산한다.
- viewer가 보여주는 대중교통 정보가 실제 API 응답 기반이 되도록 만든다.

### 구현 방향

- ODsay
  - 최상위 6~10개 후보 조회
  - `WALK/BUS/SUBWAY` leg 분해
  - 후보 조합 정규화
- 부산 BIMS
  - 버스 정류장 식별자 매핑
  - 노선 식별자 매핑
  - 버스 도착 예정 시간
  - 저상버스 운행 여부 보강
- 부산 지하철 운행정보
  - 역별 도착 예정 시간
  - 승차역/하차역/환승역 접근성
  - 필요 시 역 출입구/엘리베이터 데이터셋 연계
- 모든 `WALK` leg는 아래 규칙 고정
  - `VISUAL` -> `visual_safe`
  - `WHEELCHAIR` -> `wheelchair_safe`
- 후보 필터
  - 접근성 실패 leg 포함 시 탈락
  - transit leg 최대 2
  - 환승 최대 1
- 후보 정렬
  - 총 소요시간
  - 총 도보 거리
  - 환승 수
  - 대기시간
  - 요금

### 완료 기준

- `/routes/transit-mixed/candidates`가 stub 없이 실데이터를 사용
- 상위 3개 후보가 실제 도착 시간과 마커 정보를 포함
- viewer 상세 패널에 실제 버스/지하철 정보를 표시 가능

## 5. 카카오맵 실제 브라우저 검증

### 문제 정의

- 지금까지는 compose와 정적 페이지 제공, SDK 로드 메시지 수준까지만 검증했다.
- 실제 브라우저에서 아래가 모두 되는지 아직 확정되지 않았다.
  - 지도 렌더링
  - 출발지/도착지 선택
  - 지도 드래그/줌
  - 경로 polyline 렌더
  - transit marker 렌더

### 구현 방향

- 로컬 브라우저 수동 검증 시나리오를 체크리스트화한다.
  - `localhost:3000` 접속
  - 지도 렌더 여부
  - 출발지 선택
  - 도착지 선택
  - 길찾기 버튼 동작
  - safe/shortest/transit mixed 렌더
- 허용 도메인 관련 주의사항을 문서화한다.
  - `http://localhost:3000`
  - `http://127.0.0.1:3000`
- SDK 실패 메시지를 더 구체화한다.
  - 키 없음
  - 허용 도메인 미등록
  - 스크립트 로드 실패
  - 초기화 실패
- 가능하면 브라우저 자동화 smoke test 도입을 검토한다.
  - Playwright 또는 유사 도구
  - 최소한 지도 canvas 존재, 버튼 클릭, route card 노출까지 확인

### 완료 기준

- 실제 브라우저에서 viewer 상호작용이 끝까지 동작함
- 카카오맵 실패 원인을 화면 메시지만 보고 분류 가능
- 검증 결과가 `docs/reviews/`에 남음

## 6. 권장 작업 순서

1. FE 지도 선택 UX와 마커/오버레이 상태 복구
2. FE 상태 구조 정리
3. GraphHopper fallback 원인 추적과 프로필 차등 검증
4. `TRANSIT_MIXED` 외부 API 실연동
5. 카카오맵 실제 브라우저 검증 및 자동화 검토

## 검증 계획

- 출발지/도착지 지도 선택 수동 시나리오 검증
- 경로 재검색 반복 시 상태 꼬임 여부 확인
- safe vs shortest geometry diff 검증
- transit mixed 실데이터 후보 3개 검증
- 브라우저에서 지도/마커/polyline/상세 패널 렌더 확인
- 결과는 성공/실패 여부와 함께 `docs/reviews/`에 기록

## 완료 기준

- viewer에서 출발지/도착지 선택과 지도 탐색이 모두 정상 동작한다.
- GraphHopper 프로필별 실제 경로 차등이 검증된다.
- `TRANSIT_MIXED`가 실데이터 기반으로 동작한다.
- 카카오맵이 실제 브라우저에서 안정적으로 동작하고 검증 절차가 문서화된다.
