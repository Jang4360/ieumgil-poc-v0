# 07. 대중교통 혼합 경로 오케스트레이션 계획

## 목적

`08_react_viewer_fe_plan.md`보다 앞선 단계에서, viewer의 3번 선택지인 `대중교통 혼합길`을 실제로 뒷받침할 수 있도록 백엔드 대중교통 혼합 경로 오케스트레이션 구조를 먼저 고정한다.

이번 계획의 핵심은 아래 4가지다.

- ODsay로 대중교통 후보 조합을 가져온다.
- 각 후보의 모든 `WALK` leg를 GraphHopper의 장애유형별 `안전한경로(1번 선택지)` 알고리즘으로 다시 계산한다.
- 버스 구간은 부산 BIMS, 지하철 구간은 부산 지하철 운행정보로 도착/운행 시간을 보강한다.
- 최종적으로 `버스-버스`, `버스-지하철`, `지하철-버스`, `지하철-지하철`을 포함한 조합 중 최단 상위 `3개` 후보를 viewer가 바로 쓸 수 있는 API로 반환한다.

## 현재 기준과 승격 이유

- 기존 `docs/plans` 기준선에서는 대중교통 오케스트레이션이 현재 범위 밖이었다.
- 하지만 viewer에서 `1km` 초과 시 3번 선택지로 `대중교통 혼합길`을 노출하려면, FE보다 먼저 백엔드의 후보 생성/검증/API 계약이 필요하다.
- 따라서 이번 계획은 블루프린트에 있던 `ACCESSIBLE_TRANSIT` 상위 설계를 실제 구현 가능한 백엔드 작업 계획으로 내린다.

## 구현 범위

- ODsay 기반 대중교통 후보 조회
- ODsay 후보의 `WALK`, `BUS`, `SUBWAY` leg 분해 및 내부 표준 모델 정규화
- 장애유형별 `SAFE` 도보 알고리즘으로 모든 `WALK` leg 재계산
- 버스 구간의 저상 여부 및 도착 시간 보강
- 지하철 구간의 역/출입구/운행 시간 보강
- 허용 조합 필터링과 최종 상위 `3개` 후보 정렬
- 08 viewer가 사용할 대중교통 혼합 후보 API 설계
- 지도 마커와 상세 패널에 필요한 응답 구조 설계

## 비범위

- viewer/UI 구현
- 실제 환승 요금 계산 정교화
- 저상버스 예약 또는 외부 링크 자동 이동
- ODsay/BIMS/지하철 원천 데이터 자체 저장소 구축
- 2회 이상 환승을 포함한 복잡한 대중교통 조합

## 외부 연동 및 환경변수 기준

- `.env`에 아래 연동 정보가 이미 존재하는 것을 전제로 한다.
  - `ODSAY_API_BASE_URL`
  - `ODSAY_API_KEY`
  - `ODSAY_SERVER_IP`
  - `BUSAN_BIMS_API_BASE_URL`
  - `BUSAN_BIMS_SERVICE_KEY_ENCODING`
  - `BUSAN_BIMS_SERVICE_KEY_DECODING`
  - `BUSAN_SUBWAY_OPERATION_API_BASE_URL`
  - `BUSAN_SUBWAY_OPERATION_API_PATH`
  - `BUSAN_SUBWAY_OPERATION_SERVICE_KEY_ENCODING`
  - `BUSAN_SUBWAY_OPERATION_SERVICE_KEY_DECODING`
- ODsay는 경로 조합 구조와 예상 이동시간의 1차 후보를 제공한다.
- 부산 BIMS는 버스 정류장/노선/도착 정보와 저상버스 여부를 보강한다.
- 부산 지하철 운행정보는 역별 출발/도착 시각과 운행시간 보강에 사용한다.

## 정책 기준

### 1. 사용자 유형

- 현재 viewer 계획 기준 사용자 유형은 `VISUAL`, `WHEELCHAIR` 두 가지로 둔다.
- 대중교통 혼합 후보의 모든 도보 구간은 해당 장애유형의 `1번 선택지 = 안전한경로` 규칙을 그대로 사용한다.
- 즉 `TRANSIT_MIXED`는 별도 도보 프로필을 갖지 않고, 아래 원칙을 따른다.
  - `VISUAL + TRANSIT_MIXED` -> `VISUAL + SAFE` 도보 로직 재사용
  - `WHEELCHAIR + TRANSIT_MIXED` -> `WHEELCHAIR + SAFE` 도보 로직 재사용

### 2. 허용 조합

허용:

- `BUS`
- `SUBWAY`
- `BUS + BUS`
- `BUS + SUBWAY`
- `SUBWAY + BUS`
- `SUBWAY + SUBWAY`

제한:

- transit leg 최대 `2개`
- 환승 최대 `1회`
- 최종 반환 후보 최대 `3개`

### 3. 탈락 기준

- `WALK` leg 중 하나라도 장애유형별 안전한경로 검증에 실패하면 후보 탈락
- 버스 leg에서 저상 여부를 요구하는 정책이 있는데 해당 정보가 없거나 부적합하면 후보 탈락
- 지하철 leg에서 필요한 출입구/역 접근 또는 운행시간 보강이 불가능하면 후보 탈락 또는 `PARTIAL` 격하
- 출발/도착 예상 시간이 계산되지 않는 후보는 정식 추천 후보에서 제외
- 보행-only `빠른길`보다 현저히 느린 후보는 추천 목록에서 제외할 수 있도록 정책 훅을 둔다

## 구현 방향

### 1. 내부 처리 흐름

1. FE 또는 통합 라우팅 API가 `대중교통 혼합길` 탐색을 요청한다.
2. 백엔드는 ODsay에서 상위 `6~10개` 대중교통 후보를 조회한다.
3. ODsay 응답을 `WALK`, `BUS`, `SUBWAY` leg 단위의 내부 표준 모델로 변환한다.
4. 각 `WALK` leg를 GraphHopper로 재계산한다.
5. 재계산 시 `disabilityType`에 대응하는 `SAFE` custom model/profile을 사용한다.
6. `BUS` leg는 부산 BIMS로 정류장, 노선, 저상 여부, 버스 도착 시간을 보강한다.
7. `SUBWAY` leg는 부산 지하철 운행정보로 탑승역, 하차역, 출발 예정 시각, 도착 예정 시각을 보강한다.
8. 전체 후보를 접근성/시간/환승 기준으로 재정렬한다.
9. 상위 `3개` 후보를 viewer가 바로 렌더링할 수 있는 형태로 반환한다.

### 2. 도보 leg 재계산 원칙

- 대중교통 후보의 도보 구간은 ODsay 원본 geometry를 그대로 신뢰하지 않는다.
- 출발지 -> 탑승 정류장/역 입구, 환승 지점 간 이동, 하차 정류장/역 출구 -> 목적지까지 모두 재계산한다.
- 재계산에 사용하는 알고리즘은 08 viewer의 1번 선택지와 동일하다.
- 응답에는 ODsay 기준 원본 보행시간이 아니라, 재계산된 GraphHopper 보행시간을 넣는다.

### 3. 버스 도착 정보 보강

- 버스 탑승 정류장마다 부산 BIMS 도착 정보를 호출한다.
- 가능하면 특정 노선 기준으로 필터링해 해당 leg에 맞는 버스 도착 예정 시각을 계산한다.
- 응답에는 최소 아래 정보를 포함한다.
  - 버스 번호
  - 탑승 정류장명
  - 하차 정류장명
  - 다음 저상버스 도착 예정 시각 또는 남은 분
  - 버스 탑승 예정 시각
  - 버스 하차 예정 시각

### 4. 지하철 운행 정보 보강

- 지하철 leg는 부산 지하철 운행정보를 사용해 탑승역/하차역 기준의 운행시간을 보강한다.
- 응답에는 최소 아래 정보를 포함한다.
  - 호선명
  - 탑승역
  - 하차역
  - 탑승 예정 시각
  - 하차 예정 시각
  - 목적지 도착 예정 시각 계산에 반영된 기준 시각

### 5. 지도 표시용 마커

- `대중교통 혼합길` 후보 응답은 지도 polyline 외에도 marker 목록을 함께 내려준다.
- 08 viewer는 이를 사용해 카카오맵 스타일에 가까운 탑승/하차 지점을 표시한다.
- 최소 marker 유형은 아래를 포함한다.
  - `START`
  - `END`
  - `BUS_STOP_BOARD`
  - `BUS_STOP_ALIGHT`
  - `SUBWAY_ENTRANCE`
  - `SUBWAY_EXIT`
  - `TRANSFER_POINT`

## 백엔드 구성 계획

### 1. 모듈 책임

- `TransitCandidateProvider`
  - ODsay 호출과 후보 원본 수집
- `TransitCandidateNormalizer`
  - ODsay 응답을 내부 leg 모델로 변환
- `TransitWalkLegRouter`
  - 각 `WALK` leg를 GraphHopper 안전경로로 재계산
- `BusArrivalEnrichmentService`
  - 부산 BIMS로 버스 도착 정보/저상 여부 보강
- `SubwayScheduleEnrichmentService`
  - 부산 지하철 운행정보로 지하철 운행시간 보강
- `TransitCandidateRanker`
  - 접근성/총 소요시간/환승/대기시간 기준 정렬
- `TransitMixedRouteFacade`
  - 위 단계를 조합해 최종 API 응답 생성

### 2. 내부 표준 모델

- `TransitMixedRouteRequest`
- `TransitCandidate`
- `TransitLeg`
- `WalkLegDetail`
- `BusLegDetail`
- `SubwayLegDetail`
- `TransitMarker`
- `TransitTimingSummary`

## API 계획

### 1. 대중교통 혼합 후보 조회 API

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/routes/transit-mixed/candidates` |
| 설명 | 출발지, 도착지, 장애유형을 기준으로 대중교통 혼합 경로 상위 3개 후보를 계산해 반환한다. |

#### Request Body

| 필드명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| startPoint | object | Y | 출발지 좌표 |
| startPoint.lat | number | Y | 위도 |
| startPoint.lng | number | Y | 경도 |
| endPoint | object | Y | 도착지 좌표 |
| endPoint.lat | number | Y | 위도 |
| endPoint.lng | number | Y | 경도 |
| disabilityType | string | Y | `VISUAL`, `WHEELCHAIR` |
| departureAt | string | N | 탐색 기준 시각. 없으면 서버 현재 시각 사용 |
| maxCandidates | int | N | 최종 반환 개수. 기본값 `3`, 최대 `3` |

#### 처리 규칙

- ODsay에서 상위 후보를 가져온 뒤 내부적으로 `6~10개`를 평가한다.
- 각 후보의 `WALK` leg는 반드시 GraphHopper 안전경로로 재계산한다.
- 각 후보는 `BUS`, `SUBWAY`, `BUS+BUS`, `BUS+SUBWAY`, `SUBWAY+BUS`, `SUBWAY+SUBWAY` 조합만 허용한다.
- 정렬 기준은 아래 순서로 둔다.
  - 접근성 검증 완료 여부
  - 총 이동시간 짧은 순
  - 도착 예정 시각 빠른 순
  - 총 도보 거리 짧은 순
  - 환승 횟수 적은 순
  - 대기시간 짧은 순

#### Request Example

```json
{
  "startPoint": {
    "lat": 35.1795543,
    "lng": 129.0756416
  },
  "endPoint": {
    "lat": 35.1579012,
    "lng": 129.0592114
  },
  "disabilityType": "VISUAL",
  "departureAt": "2026-04-17T18:10:00+09:00",
  "maxCandidates": 3
}
```

#### Response Body

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| searchId | string | 요청 식별자 |
| baseDepartureAt | string | 계산 기준 출발 시각 |
| candidates | array | 최종 후보 목록 |
| candidates[].candidateId | string | 후보 ID |
| candidates[].rank | number | 정렬 순위 |
| candidates[].combinationType | string | `BUS`, `SUBWAY`, `BUS_BUS`, `BUS_SUBWAY`, `SUBWAY_BUS`, `SUBWAY_SUBWAY` |
| candidates[].verificationStatus | string | `VERIFIED`, `PARTIAL`, `REJECTED` |
| candidates[].totalDurationMinute | number | 총 이동시간 |
| candidates[].arrivalEstimatedAt | string | 목적지 도착 예정 시각 |
| candidates[].totalWalkDistanceMeter | number | 총 도보 거리 |
| candidates[].transferCount | number | 환승 횟수 |
| candidates[].summary | object | 요약 정보 |
| candidates[].summary.primaryTransitLabel | string | 대표 교통수단 표시명 |
| candidates[].summary.busArrivalDisplay | string | 버스 도착 정보 표시용 문자열 |
| candidates[].summary.subwayArrivalDisplay | string | 지하철 도착/출발 정보 표시용 문자열 |
| candidates[].geometry | object | 전체 지도 표시용 선형 좌표 |
| candidates[].markers | array | 지도 마커 목록 |
| candidates[].legs | array | 세부 구간 목록 |

#### Legs 상세 필드

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| legs[].type | string | `WALK`, `BUS`, `SUBWAY` |
| legs[].sequence | number | 구간 순서 |
| legs[].title | string | UI 표시명 |
| legs[].distanceMeter | number | 구간 거리 |
| legs[].durationMinute | number | 구간 소요 시간 |
| legs[].departureAt | string | 구간 출발 예정 시각 |
| legs[].arrivalAt | string | 구간 도착 예정 시각 |
| legs[].geometry | object | 구간 polyline |
| legs[].walkPolicy | string | 도보 leg일 때 적용한 안전경로 정책 키 |
| legs[].busRouteId | string | 버스 노선 ID |
| legs[].busRouteNo | string | 버스 번호 |
| legs[].boardStopName | string | 탑승 정류장명 |
| legs[].alightStopName | string | 하차 정류장명 |
| legs[].busArrivalMinute | number | 버스 도착 예정까지 남은 분 |
| legs[].isLowFloorExpected | boolean | 저상버스 예상 여부 |
| legs[].subwayLineName | string | 지하철 호선명 |
| legs[].boardStationName | string | 탑승역 |
| legs[].alightStationName | string | 하차역 |
| legs[].subwayDepartureAt | string | 지하철 출발 예정 시각 |
| legs[].subwayArrivalAt | string | 지하철 하차 예정 시각 |

#### Markers 상세 필드

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| markers[].markerType | string | `START`, `END`, `BUS_STOP_BOARD`, `BUS_STOP_ALIGHT`, `SUBWAY_ENTRANCE`, `SUBWAY_EXIT`, `TRANSFER_POINT` |
| markers[].label | string | 마커 라벨 |
| markers[].lat | number | 위도 |
| markers[].lng | number | 경도 |
| markers[].sequence | number | 지도/상세 패널 표시 순서 |

#### Response Example

```json
{
  "success": true,
  "data": {
    "searchId": "transit-mixed-20260417-0001",
    "baseDepartureAt": "2026-04-17T18:10:00+09:00",
    "candidates": [
      {
        "candidateId": "candidate-1",
        "rank": 1,
        "combinationType": "BUS_SUBWAY",
        "verificationStatus": "VERIFIED",
        "totalDurationMinute": 28,
        "arrivalEstimatedAt": "2026-04-17T18:38:00+09:00",
        "totalWalkDistanceMeter": 430,
        "transferCount": 1,
        "summary": {
          "primaryTransitLabel": "버스 86 + 부산 1호선",
          "busArrivalDisplay": "버스 5분 후 도착",
          "subwayArrivalDisplay": "지하철 18:26 출발"
        },
        "geometry": {
          "type": "MultiLineString",
          "coordinates": []
        },
        "markers": [
          {
            "markerType": "BUS_STOP_BOARD",
            "label": "시청역 정류장",
            "lat": 35.1799,
            "lng": 129.0769,
            "sequence": 1
          },
          {
            "markerType": "SUBWAY_ENTRANCE",
            "label": "서면역 2번 출구",
            "lat": 35.1571,
            "lng": 129.0594,
            "sequence": 2
          }
        ],
        "legs": [
          {
            "type": "WALK",
            "sequence": 1,
            "title": "출발지에서 버스 정류장까지",
            "distanceMeter": 160,
            "durationMinute": 4,
            "departureAt": "2026-04-17T18:10:00+09:00",
            "arrivalAt": "2026-04-17T18:14:00+09:00",
            "walkPolicy": "visual-safe"
          },
          {
            "type": "BUS",
            "sequence": 2,
            "title": "버스 86 탑승",
            "durationMinute": 9,
            "departureAt": "2026-04-17T18:15:00+09:00",
            "arrivalAt": "2026-04-17T18:24:00+09:00",
            "busRouteId": "5200000086",
            "busRouteNo": "86",
            "boardStopName": "부산시청",
            "alightStopName": "서면역",
            "busArrivalMinute": 5,
            "isLowFloorExpected": true
          },
          {
            "type": "SUBWAY",
            "sequence": 3,
            "title": "부산 1호선 탑승",
            "durationMinute": 8,
            "departureAt": "2026-04-17T18:26:00+09:00",
            "arrivalAt": "2026-04-17T18:34:00+09:00",
            "subwayLineName": "부산 1호선",
            "boardStationName": "서면역",
            "alightStationName": "남포역",
            "subwayDepartureAt": "2026-04-17T18:26:00+09:00",
            "subwayArrivalAt": "2026-04-17T18:34:00+09:00"
          }
        ]
      }
    ]
  },
  "message": null
}
```

### 2. 통합 라우팅 API와의 연결 규칙

- 08 viewer의 상위 경로 탐색 화면은 기존 `SAFE`, `SHORTEST`와 별도로 3번 카드 `TRANSIT_MIXED`를 노출한다.
- `distanceMeter > 1000`인 경우에만 viewer가 `TRANSIT_MIXED` 카드를 노출한다.
- 08 viewer는 3번 카드를 눌렀을 때 `POST /routes/transit-mixed/candidates`를 호출해 상위 `3개` 후보를 받아 렌더링한다.
- 이후 후보 하나를 선택하면 응답에 포함된 `markers`, `legs`, `arrivalEstimatedAt`, `busArrivalDisplay`, `subwayArrivalDisplay`를 그대로 사용해 지도를 그리고 상세 패널을 구성한다.

### 3. 에러 규칙

| HTTP Status | code | message | 발생 조건 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | 좌표 누락, 장애유형 오류 |
| `404` | `RESOURCE_NOT_FOUND` | 탐색 가능한 대중교통 후보가 없습니다. | 후보 전부 탈락 |
| `409` | `TRANSIT_DATA_PARTIAL` | 대중교통 보강 데이터가 불완전합니다. | 도착 정보 일부 미확보 |
| `502` | `EXTERNAL_API_ERROR` | 외부 API 호출에 실패했습니다. | ODsay/BIMS/지하철 운행정보 실패 |
| `500` | `INTERNAL_ERROR` | 서버 내부 오류가 발생했습니다. | 내부 오케스트레이션 오류 |

## 단계별 작업 계획

### 1. 외부 연동 정리 단계

- ODsay 후보 조회 파라미터와 응답 구조 확인
- 부산 BIMS 정류장/도착 API와 ODsay 정류장 식별자 매핑 규칙 정리
- 부산 지하철 운행정보 API의 역명/시간표 매핑 규칙 정리

### 2. 표준 모델 단계

- ODsay 후보를 내부 `TransitCandidate`, `TransitLeg` 모델로 변환
- `WALK`, `BUS`, `SUBWAY` 공통 timing 필드 확정
- 지도 표시용 marker 모델 확정

### 3. 도보 재계산 단계

- GraphHopper 안전경로 재사용 서비스 작성
- 모든 `WALK` leg를 장애유형별 안전경로로 재검증
- 실패 후보 탈락 또는 `PARTIAL` 규칙 정리

### 4. 버스/지하철 보강 단계

- 버스 도착 시간/저상 여부 보강
- 지하철 출발/도착 예정 시각 보강
- 전체 도착 예정 시각 계산

### 5. 후보 정렬 및 API 단계

- 상위 `3개` 후보 정렬 로직 구현
- `/routes/transit-mixed/candidates` 응답 스키마 구현
- 08 viewer가 사용할 최소 표시 필드 검증

## 검증 계획

- 동일 출발지/도착지에 대해 ODsay 후보가 내부 표준 모델로 안정적으로 변환되는지 확인한다.
- `WALK` leg가 모두 GraphHopper 안전경로로 재계산되는지 확인한다.
- `BUS + SUBWAY`, `SUBWAY + BUS`, `BUS + BUS`, `SUBWAY + SUBWAY` 조합이 각각 최소 1건 이상 처리 가능한지 샘플 검증한다.
- 상위 `3개` 후보가 총 이동시간 기준으로 정렬되는지 확인한다.
- 응답에 총 이동시간, 목적지 도착 예정 시각, 버스 도착 정보, 지하철 출발/도착 시각이 모두 포함되는지 확인한다.
- 응답의 `markers`만으로 viewer가 버스 정류장과 지하철 출입구를 지도에 표시할 수 있는지 확인한다.
- 성공/실패 결과는 `docs/reviews/`에 별도 리뷰 문서로 남긴다.

## 완료 기준

- 팀원이 이 문서만 읽고 대중교통 혼합 경로 오케스트레이션 작업을 바로 시작할 수 있다.
- ODsay, 부산 BIMS, 부산 지하철 운행정보의 역할 분리가 명확하다.
- `대중교통 혼합길`의 모든 `WALK` leg가 장애유형별 안전한경로를 사용한다는 원칙이 고정되어 있다.
- 상위 `3개` 후보 반환, 마커 정보 반환, 총 이동시간/도착 예정 시각 반환 요구사항이 API 수준에서 명확하다.
- 08 viewer가 이 API를 그대로 소비할 수 있는 입력/출력 계약이 문서에 포함되어 있다.

## 리스크 및 후속 결정 사항

- ODsay 정류장/역 식별자와 부산 BIMS/부산 지하철 데이터의 매칭이 완전히 안정적이지 않을 수 있다.
- 부산 지하철 운행정보가 실시간이 아니라 시각표 기반이면, 응답에는 `예정 시각`으로만 표기해야 한다.
- 현재 viewer 계획과 블루프린트의 장애유형 enum은 `VISUAL`, `WHEELCHAIR`로 맞춘다.
- `대중교통 혼합길`을 통합 `POST /routes/search`에 합칠지, 별도 endpoint로 유지할지는 구현 단계에서 최종 결정해야 한다.
