# 11. 보행 경로 직선 표시 원인 분석 및 Snap 복구 계획

## 목적

현재 viewer에서 `Safe Walk` 또는 `Fast Walk`를 선택했을 때, 지도에 실제 보행로를 따라가는 polyline이 아니라 `출발지 -> 도착지`를 거의 직선으로 잇는 선이 표시되는 문제가 있다.

이 문서는 다음을 분리해서 정리한다.

- 왜 지금 직선이 그려지는지
- 이 현상이 프론트 문제인지 백엔드 문제인지
- 실제 보행 네트워크 기반 geometry로 복구하려면 무엇을 점검하고 수정해야 하는지
- fallback이 unavoidable한 경우 사용자에게 어떻게 보여줘야 하는지

## 현재 진단

### 1. 현재 화면의 직선은 프론트 렌더링 버그가 아니다

프론트는 백엔드가 내려준 `geometry.coordinates`를 그대로 카카오맵 `Polyline`으로 그린다.  
즉 직선이 보인다는 것은 대체로 백엔드가 직선에 가까운 좌표열을 응답했다는 뜻이다.

### 2. 현재 백엔드는 GraphHopper route 실패 시 의도적으로 fallback 직선을 만든다

`poc/src/main/java/com/example/poc/transit/service/TransitWalkLegRouter.java` 기준으로:

- GraphHopper가 정상 route를 찾으면 `ResponsePath.getPoints()`를 좌표열로 변환한다.
- GraphHopper가 route를 찾지 못하면 `fallbackCoordinates()`를 사용한다.
- `SAFE_WALK` fallback은 `from -> to` 두 점만 내려준다.
- `FAST_WALK` fallback은 중간점을 하나 넣은 3점 꺾은선만 내려준다.

즉 지금 screenshot에서 보이는 `wheelchair_safe + Fallback` 직선은 **현재 코드상 의도된 fallback 출력**이다.

### 3. 현재 smoke 응답에서 이미 fallback 원인이 드러나고 있다

실제 응답 예시 기준:

- `graphHopperBacked = false`
- `fallbackUsed = true`
- `fallbackReason = Cannot find point ...`

이는 GraphHopper가 출발지/도착지를 imported graph에 snap하지 못했다는 의미다.

## 문제의 핵심 원인 후보

### 원인 후보 A. imported graph가 현재 선택 좌표를 커버하지 못함

- OSM import 결과가 해당 도로 주변 보행 가능한 edge를 충분히 포함하지 못했을 수 있다.
- `wheelchair_safe`, `visual_safe` 같은 stricter profile에서 통과 가능한 edge가 너무 적어서 snap 실패가 날 수 있다.

### 원인 후보 B. graph는 있지만 profile pass 조건이 과도함

- `safe` profile의 slope, stairs, curb, signal 관련 제약이 너무 강해서 usable edge가 거의 사라질 수 있다.
- 이 경우 graph 자체는 존재해도 해당 profile 관점에서는 route가 사실상 끊긴다.

### 원인 후보 C. viewer가 찍는 좌표와 graph edge 사이의 snap tolerance가 부족함

- 실제 출발지와 도착지는 건물 주소 좌표일 수 있고, 보행 edge는 도로 중심 또는 보도선에 있다.
- 따라서 단순한 origin/destination 좌표를 그대로 GraphHopper에 넣으면 `Cannot find point`가 발생할 수 있다.

### 원인 후보 D. fallback 정책이 너무 공격적으로 사용자 화면까지 내려감

- 현재는 route 실패 시 synthetic 직선을 그대로 사용자용 geometry로 응답한다.
- 진단용으로는 유용하지만, 최종 viewer에서는 “길 안내”처럼 보이는 잘못된 시각 표현이 된다.

## 결론

현재 직선 표시는 크게 두 단계가 겹친 결과다.

1. GraphHopper가 실제 보행 route를 찾지 못하고 있다.
2. 실패 시 backend가 사용자용 geometry로 직선 fallback을 내려주고 있다.

따라서 개선은 단순히 프론트 polyline 스타일을 바꾸는 것이 아니라:

- imported graph에서 실제 route가 나오도록 snap/graph/profile을 복구하고
- 그 전까지는 fallback을 “가짜 길안내”처럼 보이지 않게 표현 정책을 바꾸는

두 축으로 진행해야 한다.

## 작업 범위

- GraphHopper snap 실패 원인 진단
- imported graph coverage 점검
- user type/profile별 pass 조건 검증
- route 요청 전후 snap 보정 레이어 설계
- fallback geometry 노출 정책 변경
- viewer에서 fallback route 표시 방식 보완

## 비범위

- 대중교통 실데이터 연동
- 신규 장애 유형 추가
- mobile 별도 UX

## 1. Snap 실패 진단 계획

### 목표

- 현재 선택한 실제 좌표에서 왜 `Cannot find point`가 나는지 재현 가능한 방식으로 밝힌다.

### 작업

- known OD fixture를 5~10개 선정한다.
  - 시청, 연산역, 센텀역, 해운대구청 등 실제 보행 가능성이 높은 구간
  - `VISUAL`, `WHEELCHAIR` 각각 최소 2개 이상
- 각 OD에 대해 아래를 수집한다.
  - request lat/lng
  - profile name
  - GraphHopper 오류 메시지
  - fallback 여부
- GraphHopper nearest/snap 진단을 추가 검토한다.
  - route 전에 nearest edge lookup을 수행
  - snap 성공 거리(meter) 기록
  - snap 실패 시 주변 탐색 반경 기록

### 완료 기준

- 직선이 나오던 대표 OD들에 대해 snap 실패 이유를 표로 정리할 수 있다.

## 2. Imported Graph Coverage 점검 계획

### 목표

- 현재 graph artifact가 해당 지역 보행 edge를 실제로 가지고 있는지 확인한다.

### 작업

- 문제가 발생한 OD 주변 bounding box를 기준으로 road segment 존재 여부를 확인한다.
- import 결과에서 해당 구간 edge 수와 node 수를 집계한다.
- 가능하면 profile별 pass EV 분포를 같이 확인한다.
  - `ieum_visual_safe_pass`
  - `ieum_visual_fast_pass`
  - `ieum_wheelchair_safe_pass`
  - `ieum_wheelchair_fast_pass`
- route 실패 지점 주변에서:
  - base edge는 존재하는지
  - safe profile만 탈락하는지
  - fast도 같이 탈락하는지
를 구분한다.

### 완료 기준

- “graph가 비어 있는 문제”인지, “profile 제약 문제”인지 구분 가능하다.

## 3. Snap 보정 레이어 설계 계획

### 목표

- 건물 주소 좌표를 그대로 넣는 대신, 보행 시작 가능한 edge 근처로 origin/destination를 보정한다.

### 작업

- route 요청 전에 `raw point`와 `snapped point`를 분리한다.
- 보정 전략을 단계적으로 적용한다.
  - 1차: nearest accessible edge 탐색
  - 2차: 허용 반경 내 후보 edge 중 가장 적절한 edge 선택
  - 3차: 실패 시 사용자에게 route unavailable 처리
- 응답에는 아래를 추가 검토한다.
  - `requestedStartPoint`, `requestedEndPoint`
  - `snappedStartPoint`, `snappedEndPoint`
  - `snapDistanceMeter`
- 필요 시 출발지/도착지 앞뒤에 짧은 connector segment를 별도 메타데이터로 내려 viewer에서 구분 렌더링한다.

### 완료 기준

- building centroid 주소를 넣어도 실제 보행 edge로 route가 생성되는 케이스가 확보된다.

## 4. Profile 제약 보정 계획

### 목표

- `SAFE_WALK`와 `FAST_WALK`가 모두 전부 실패하거나 모두 fallback으로 떨어지는 상황을 줄인다.

### 작업

- `wheelchair_safe`와 `visual_safe`의 제약 조건을 재검토한다.
  - slope threshold
  - stairs 차단
  - curb gap 차단
  - unknown accessibility 속성 처리
- strict profile이 route를 완전히 끊는 경우 완화 순서를 정의한다.
  - 완전 차단
  - penalty 기반 우회
  - 최후의 실패 처리
- `safe`와 `fast`의 차이가 실제 geometry에 반영되는 fixture를 기준으로 custom model을 튜닝한다.

### 완료 기준

- known OD에서 `SAFE_WALK`와 `FAST_WALK`가 모두 `graphHopperBacked = true`로 나오는 케이스를 확보한다.

## 5. Fallback 노출 정책 변경 계획

### 목표

- GraphHopper route가 없을 때 직선을 “길 안내”처럼 보이게 그리지 않도록 바꾼다.

### 작업

- backend 정책 변경:
  - synthetic fallback geometry를 사용자용 기본 geometry로 쓰지 않도록 검토
  - fallback 시 `geometry = null` 또는 `displayGeometryType = FALLBACK_STRAIGHT_LINE` 명시
- frontend 정책 변경:
  - fallback route는 실선 길안내 대신 경고 상태로 렌더링
  - 예:
    - 점선
    - 반투명 회색 연결선
    - “실제 보행 경로 미확보” 배지
  - route card에서 `Fallback`을 더 강하게 경고
- 지도 위에도 fallback임을 명확히 표기한다.

### 완료 기준

- 사용자가 fallback 직선을 실제 길안내로 오해하지 않는다.

## 6. Viewer 렌더링 보완 계획

### 목표

- 실제 route와 fallback route를 지도에서 시각적으로 명확히 구분한다.

### 작업

- `graphHopperBacked = true` route
  - 진한 primary polyline
  - 실제 route bounds fit
- `fallbackUsed = true` route
  - dashed or muted line
  - 경고 overlay
  - detail panel 상단에 fallback reason 노출
- snapped point와 requested point가 다르면 marker/connector로 시각화하는 방안 검토

### 완료 기준

- route 신뢰 수준이 지도에서 바로 구분된다.

## 7. 검증 계획

- API 수준 검증
  - `/routes/search` known OD fixture 요청
  - `graphHopperBacked`, `fallbackUsed`, `fallbackReason` 확인
- 데이터 수준 검증
  - 문제 구간 road segment 존재 여부 확인
  - profile별 pass EV 분포 비교
- 브라우저 수준 검증
  - 동일 OD에 대해 fallback 전/후 viewer 지도 표시 비교
  - 실제 route일 때는 도로를 따라가는 geometry인지 시각 확인
  - fallback일 때는 경고 스타일인지 확인

## 8. 권장 작업 순서

1. 대표 OD fixture 선정
2. snap 실패 원인 수집
3. imported graph coverage와 EV 분포 점검
4. snap 보정 레이어 설계 및 구현
5. profile 제약 튜닝
6. fallback 노출 정책 변경
7. viewer fallback 스타일링 보완
8. 리뷰 문서 기록

## 완료 기준

- 대표 OD fixture 기준으로 최소 일부 케이스에서 `graphHopperBacked = true` 보행 geometry가 나온다.
- viewer에서 더 이상 fallback 직선을 실제 길안내처럼 보이지 않게 처리한다.
- 왜 직선이 나왔는지, 어떤 케이스가 graph coverage/profile/snap 문제였는지 리뷰 문서에 정리된다.
