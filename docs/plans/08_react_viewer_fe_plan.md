# 08. React Viewer FE 계획

## 목적

루트에 `FE/` 폴더를 신설해 React 기반 viewer 단을 추가하고, 카카오 지도 JavaScript SDK를 사용해 부산광역시 중심 지도를 띄운 뒤 출발지/도착지 선택, 경로 옵션 비교, 경로 확정, 경로 속성 확인까지 가능한 단일 화면 UX를 만든다.

이번 문서는 구현 자체가 아니라, 프론트엔드 viewer 착수를 위한 구조, 의존성, API 계약 가정, 단계별 작업 순서를 고정하는 것이 목적이다.

## 구현 범위

- 루트 `FE/` 폴더 신설
- React 기반 단일 페이지 viewer 앱 구성
- 카카오 지도 SDK 로드 및 부산광역시 중심 초기 지도 표시
- 사이드 패널에 출발지/도착지 입력 영역과 지도 선택 버튼 배치
- 출발지/도착지 입력 영역 아래에 `시각장애인`, `휠체어` 선택 버튼 배치
- 장애 유형 선택값을 기준으로 백엔드가 GraphHopper custom model/profile을 선택하도록 API 계약 추가
- 지도 클릭으로 출발지/도착지 좌표를 지정하는 pick mode UX
- 길찾기 버튼 클릭 시 경로 옵션 목록 표시
- 총 거리 `1km` 이하일 때 `안전한길`, `빠른길` 2개 옵션 노출
- 총 거리 `1km` 초과일 때 `안전한길`, `빠른길`, `대중교통 혼합길` 3개 옵션 노출
- 옵션 선택 시 지도 위 polyline 표시
- 선택한 경로를 확정하는 CTA와 확정 후 상세 속성 패널 표시
- 첨부 이미지의 오른쪽 화면에 가까운 세로 타임라인형 상세 UI 설계
- FE와 backend를 연결하는 viewer 전용 경로 API 초안 문서화

## 비범위

- 실제 대중교통 혼합 경로 계산 엔진 구현
- 인증, 사용자 저장, 북마크, 제보 기능
- 음성 안내, GPS 추적, 재탐색
- 모바일 앱 래핑
- 운영 배포 파이프라인 확정

## 전제 및 제약

- 저장소 루트 `.env`에 카카오 JavaScript API 키가 이미 존재하며, 프론트엔드는 이 값을 직접 하드코딩하지 않는다.
- 브라우저 번들러가 읽을 수 있도록 프론트 전용 환경변수 브리지가 필요하다.
- 현재 문서화된 경로 API는 `SAFE`, `SHORTEST` 중심이며, `대중교통 혼합길`은 별도 API 계약 보강 없이는 실데이터 제공이 어렵다.
- 장애 유형 선택에 따라 GraphHopper custom model을 서버가 결정해야 하므로, 기존 경로 API는 `disabilityType` 입력과 내부 model selection 로직 보강이 필요하다.
- 현재 저장소 기준선에서는 viewer/UI가 비범위였으므로, 이번 계획은 기존 백엔드 기준선을 깨지 않는 선에서 viewer를 병렬 추가하는 방식으로 잡는다.

## 구현 방향

### 1. FE 앱 구조

- 루트에 `FE/` 폴더를 만들고 React + Vite 기반으로 시작한다.
- 이유는 카카오 SDK 스크립트 주입, `.env` 기반 설정, 빠른 로컬 실행에 유리하기 때문이다.
- 초기 범위는 단일 route 기반 SPA로 두고, 폴더 구조는 아래 수준으로 시작한다.
  - `FE/src/app`
  - `FE/src/features/map`
  - `FE/src/features/route-search`
  - `FE/src/features/route-detail`
  - `FE/src/shared`

### 2. 환경변수 처리

- 루트 `.env`의 `KAKAO_JAVASCRIPT_KEY`를 프론트에서 직접 읽지 않는다.
- 프론트 빌드 단계에서 `VITE_KAKAO_JAVASCRIPT_KEY`로 브리지하는 방식을 사용한다.
- 구현 시 선택지는 아래 둘 중 하나로 고정한다.
  - `FE/.env.local`에 `VITE_KAKAO_JAVASCRIPT_KEY`를 별도 선언
  - 또는 FE 실행 스크립트/compose에서 루트 값을 주입
- 계획 기준 기본안은 `FE/.env.local` 브리지다.
- 문서와 `.gitignore`에 “키 값 자체는 커밋하지 않는다”를 함께 반영한다.

### 3. 지도 초기 화면

- 카카오 지도 SDK가 로드되면 부산광역시청 인근 좌표를 기본 중심점으로 사용한다.
- 초기 zoom level은 부산 도심 범위를 한 번에 인지할 수 있는 수준으로 둔다.
- 지도 로딩 전에는 skeleton 또는 spinner를 표시하고, SDK 로드 실패 시 명시적 오류 문구를 보여준다.
- 지도에는 기본적으로 아래 overlay를 지원한다.
  - 출발지 마커
  - 도착지 마커
  - 후보 경로 polyline
  - 확정 경로 강조 polyline

## UX 계획

### 1. 기본 레이아웃

- 전체 화면은 `좌측 사이드 패널 + 우측 지도` 2단 구조를 기본으로 한다.
- 첨부 이미지의 오른쪽 패널처럼, 경로 확정 이후에는 사이드 패널 하단 또는 확장 섹션에 세로 타임라인형 상세 뷰를 보여준다.
- 데스크톱 우선으로 설계하되, 좁은 화면에서는 사이드 패널이 상단 카드형으로 접히는 반응형 구조를 잡는다.

### 2. 출발지/도착지 입력

- 각 섹션은 아래 요소로 구성한다.
  - 라벨: `출발지`, `도착지`
  - 현재 선택 상태 텍스트
  - `지도에서 선택` 버튼
  - 추후 확장 가능한 검색 입력 placeholder
- 사용자가 `지도에서 선택` 버튼을 누르면 현재 선택 모드가 `start` 또는 `end`로 바뀌고, 다음 지도 클릭 1회에 해당 좌표를 저장한다.
- 좌표 저장 후 카카오 reverse geocoding 또는 장소명 fallback을 붙일지 여부는 구현 단계에서 결정하되, 1차 계획은 좌표 기반 표시도 허용한다.

### 3. 장애 유형 선택

- 출발지/도착지 입력 블록 바로 아래에 `시각장애인`, `휠체어` 2개의 토글 버튼을 둔다.
- 두 버튼은 동시 선택이 아니라 단일 선택이다.
- 초기 기본값은 명시적으로 선택되지 않은 상태로 두고, 사용자가 하나를 골라야 `길찾기` 버튼이 활성화되도록 한다.
- 선택값은 FE 표시용 라벨과 별도로 API enum으로 관리한다.
  - `시각장애인` -> `VISUAL`
  - `휠체어` -> `WHEELCHAIR`
- 선택된 장애 유형은 단순 UI 상태가 아니라, backend가 어떤 GraphHopper custom model을 사용할지 결정하는 핵심 입력값으로 사용한다.

### 4. 길찾기 버튼과 옵션 분기

- `길찾기` 버튼은 장애 유형 선택 블록 바로 아래에 둔다.
- 출발지, 도착지, 장애 유형이 모두 선택되어야 `길찾기` 버튼이 활성화된다.
- 길찾기 요청 후 응답 거리 기준으로 옵션 수를 분기한다.
- 분기 규칙은 아래와 같이 고정한다.
  - `distanceMeter <= 1000` 이면 `안전한길`, `빠른길`
  - `distanceMeter > 1000` 이면 `안전한길`, `빠른길`, `대중교통 혼합길`
- 옵션은 카드 또는 segmented list 형태로 표시한다.
- 각 옵션 카드에는 최소 아래 정보를 보여준다.
  - 경로명
  - 총 거리
  - 예상 시간
  - 위험도 또는 추천 이유

### 5. 경로 선택과 확정

- 옵션 카드 클릭 시 해당 경로 polyline을 지도에 미리보기로 그린다.
- 미리보기 상태에서는 다른 옵션을 계속 바꿔볼 수 있다.
- `선택하기` 버튼을 누르면 경로가 확정되고, 이후 상세 패널을 펼친다.
- 확정 상태에서는 선택된 경로 카드가 active 상태가 되고 지도 polyline 스타일도 더 굵고 선명하게 바뀐다.

### 6. 경로 상세 패널

- 경로 확정 후에는 첨부 이미지 오른쪽과 유사한 세로 흐름 UI를 사용한다.
- 상단에는 출발지와 도착지 이름을 크게 보여준다.
- 중간에는 주요 구간/속성을 타임라인 방식으로 배치한다.
- 1차 계획에서 상세 패널은 아래 속성군을 포함한다.
  - 횡단보도 여부
  - 경사도 또는 경사 주의 구간
  - 계단 여부
  - 점자블록 여부
  - 음향신호기 여부
  - 단차 여부
  - 구간 안내 문구
- 데이터가 없는 속성은 숨기거나 `정보 없음`으로 처리하는 규칙을 정한다.

## 상태 모델 계획

- viewer 상태는 아래 단위로 나눈다.
  - `mapReady`
  - `pickMode(start | end | none)`
  - `startPoint`
  - `endPoint`
  - `disabilityType`
  - `routeCandidates`
  - `previewRouteId`
  - `selectedRouteId`
  - `routeDetailExpanded`
  - `loading/error`
- 초기 구현은 React 내장 state 또는 가벼운 store 하나로 처리한다.
- 범위상 전역 상태 관리 라이브러리 도입은 필수가 아니다.

## API 및 데이터 계약 계획

### 1. 지도

- 지도 배경은 카카오 지도 SDK를 사용한다.
- 장소명 보강이 필요하면 카카오 geocoder 서비스를 선택적으로 사용한다.

### 2. 경로 조회

- 1차 연동 대상은 문서화된 `POST /routes/search`다.
- FE는 `startPoint`, `endPoint`, `disabilityType`을 백엔드에 전달하고, 백엔드는 이 값을 기준으로 GraphHopper custom model/profile을 선택한다.
- 현재 API 문서상 옵션은 `SAFE`, `SHORTEST`만 확정되어 있으므로 FE에서는 내부 표시명을 아래처럼 매핑한다.
  - `SAFE` -> `안전한길`
  - `SHORTEST` -> `빠른길`
- `대중교통 혼합길`은 아래 두 안 중 하나가 필요하다.
  - 백엔드에 신규 옵션 계약을 추가한다.
  - 또는 FE 단계에서 mock/placeholder 카드로 먼저 표현한다.
- 이번 계획에서는 “`SAFE`, `SHORTEST`는 GraphHopper custom model 기반으로 실제 연동하고, `대중교통 혼합길`은 별도 orchestration 의존성을 문서에 명시한 상태에서 API 계약만 먼저 포함”하는 방향을 기본안으로 둔다.

### 3. GraphHopper custom model 선택 규칙

- FE는 어떤 custom model 파일을 쓸지 알 필요가 없다.
- FE는 오직 `disabilityType`과 경로 옵션만 보내고, backend가 내부 매핑 규칙으로 GraphHopper 요청을 구성한다.
- 1차 매핑 규칙은 아래처럼 고정한다.

| disabilityType | routeOption | backend 역할 |
| --- | --- | --- |
| `VISUAL` | `SAFE` | 시각장애 보행 안전 가중치 custom model 적용 |
| `VISUAL` | `SHORTEST` | 시각장애 보행 빠른길 custom model 적용 |
| `WHEELCHAIR` | `SAFE` | 휠체어 보행 안전 가중치 custom model 적용 |
| `WHEELCHAIR` | `SHORTEST` | 휠체어 보행 빠른길 custom model 적용 |
| `VISUAL` or `WHEELCHAIR` | `TRANSIT_MIXED` | GraphHopper 보행 구간 + 별도 대중교통 조합 규칙 필요 |

- 즉 `SAFE`, `SHORTEST`에서도 장애 유형에 따라 서로 다른 custom model을 호출해야 한다.
- FE는 응답 결과에 포함된 `appliedProfile` 또는 `appliedModelKey`를 디버깅/표시용 메타데이터로만 사용한다.

### 4. 경로 속성 매핑

- `segments[].hasCrosswalk`, `hasSignal`, `hasAudioSignal`, `hasBrailleBlock`, `hasStairs`, `hasCurbGap`, `guidanceMessage`를 상세 패널에 직접 매핑한다.
- `경사도`는 현재 API 명세에 직접 필드가 없으므로 아래 중 하나가 필요하다.
  - 백엔드 응답 필드 추가
  - 또는 1차 UI에서는 `경사 주의` placeholder만 표기
- 이 항목은 구현 전 백엔드 계약 확인이 필요하다.

## 단계별 작업 계획

### 1. 문서/스캐폴드 단계

- `docs/plans/`에 본 계획 확정
- `FE/` 앱 초기화
- `.gitignore`, 실행 스크립트, README 최소 보강

### 2. 지도 기반 단계

- 카카오 SDK 로더 유틸 작성
- 부산광역시 기본 지도 렌더링
- 출발지/도착지 마커와 pick mode 구현

### 3. 경로 후보 단계

- `/routes/search` 요청 payload에 `disabilityType` 포함
- backend의 GraphHopper custom model 선택 로직 연동
- 거리 기준 2개/3개 옵션 분기 구현
- 후보별 polyline 미리보기 구현

### 4. 경로 확정 단계

- `선택하기` 액션 구현
- 확정 경로 강조 스타일 적용
- 우측 예시 이미지에 가까운 타임라인형 상세 패널 구현

### 5. 정리 단계

- 빈 상태, 로딩, 에러 상태 정리
- 기본 반응형 대응
- 실행 및 검증 문서 작성

## 검증 계획

- `FE` 로컬 실행 시 카카오 지도 SDK가 정상 로드되는지 확인한다.
- 첫 화면에서 부산광역시 중심 지도가 표시되는지 확인한다.
- 출발지/도착지 각각에 대해 지도 클릭 선택이 정상 동작하는지 확인한다.
- `시각장애인`, `휠체어` 버튼이 단일 선택으로 동작하는지 확인한다.
- 길찾기 버튼 활성화 조건이 출발지/도착지/장애 유형 선택 완료 시점과 일치하는지 확인한다.
- `1km` 기준으로 옵션 수가 `2개/3개`로 바뀌는지 확인한다.
- 같은 좌표여도 장애 유형을 바꾸면 backend가 다른 GraphHopper custom model을 선택하는지 확인한다.
- 후보 경로 클릭 시 지도 polyline이 바뀌는지 확인한다.
- `선택하기` 후 상세 패널이 확정 상태로 전환되는지 확인한다.
- 성공 여부와 한계는 `docs/reviews/`에 별도 리뷰 문서로 남긴다.

## 완료 기준

- 팀원이 이 문서만 읽고 `FE/` viewer를 어떤 구조로 시작할지 바로 판단할 수 있다.
- 카카오 지도 SDK, 환경변수 브리지, 장애 유형 선택, GraphHopper custom model 연동, 경로 옵션 분기, 상세 패널 UX가 모두 작업 단위로 분해되어 있다.
- 현재 백엔드 문서와 맞는 부분과, 추가 계약이 필요한 부분이 명확히 구분되어 있다.
- 특히 `대중교통 혼합길`과 `경사도`가 현재는 FE 단독으로 완결되지 않는 의존성임이 문서에 드러난다.

## 리스크 및 후속 결정 사항

- `대중교통 혼합길`은 현 백엔드 문서만으로는 실연동이 불가능하다.
- 경사도 정보는 현 경로 API 응답만으로는 직접 표시할 수 없다.
- 카카오 지도 API 사용 시 브라우저 허용 도메인 설정이 로컬 개발 환경과 맞지 않으면 로드가 실패할 수 있다.
- 추후 viewer를 compose에 포함할지, FE를 별도 개발 서버로 둘지 운영 방식 결정을 추가로 해야 한다.

## FE-Backend 연결 API 명세 초안

### 1. 경로 탐색 API

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/routes/search` |
| 설명 | 출발지, 도착지, 장애 유형을 기준으로 backend가 GraphHopper custom model을 선택해 경로 후보를 반환한다. |

#### Request Body

| 필드명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| startPoint | object | Y | 출발지 좌표 |
| startPoint.lat | number | Y | 출발지 위도 |
| startPoint.lng | number | Y | 출발지 경도 |
| endPoint | object | Y | 도착지 좌표 |
| endPoint.lat | number | Y | 도착지 위도 |
| endPoint.lng | number | Y | 도착지 경도 |
| disabilityType | string | Y | `VISUAL`, `WHEELCHAIR` |
| requestedRouteOptions | array | N | FE가 명시적으로 요청하는 옵션 목록. 없으면 backend가 거리 기준으로 결정 |

#### Backend 처리 규칙

- 입력 검증 후 backend는 먼저 기준 보행 경로 거리 또는 primary shortest distance를 계산한다.
- 계산된 거리 기준으로 반환 대상 옵션을 결정한다.
  - `distanceMeter <= 1000` -> `SAFE`, `SHORTEST`
  - `distanceMeter > 1000` -> `SAFE`, `SHORTEST`, `TRANSIT_MIXED`
- 각 옵션에 대해 backend는 `disabilityType + routeOption` 조합으로 GraphHopper custom model/profile을 선택한다.
- FE는 model 파일명이나 GraphHopper 내부 파라미터를 알지 못하며, backend가 이를 캡슐화한다.

#### GraphHopper 내부 선택 규칙

| disabilityType | routeOption | 내부 선택 예시 |
| --- | --- | --- |
| `VISUAL` | `SAFE` | `viewer-visual-safe` custom model |
| `VISUAL` | `SHORTEST` | `viewer-visual-fast` custom model |
| `WHEELCHAIR` | `SAFE` | `viewer-wheelchair-safe` custom model |
| `WHEELCHAIR` | `SHORTEST` | `viewer-wheelchair-fast` custom model |
| `VISUAL` or `WHEELCHAIR` | `TRANSIT_MIXED` | GraphHopper 보행 leg + 별도 transit 조합 엔진 |

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
  "disabilityType": "VISUAL"
}
```

#### Response Body

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| searchId | string | 탐색 요청 식별자 |
| distancePolicy | object | 옵션 수 결정 기준 정보 |
| distancePolicy.thresholdMeter | number | 기준 거리. `1000` |
| distancePolicy.primaryDistanceMeter | number | 옵션 수 판단에 사용한 거리 |
| availableRouteOptions | array | 이번 요청에 대해 제공 가능한 옵션 목록 |
| routes | array | 실제 경로 후보 목록 |
| routes[].routeId | string | 후보 경로 ID |
| routes[].routeOption | string | `SAFE`, `SHORTEST`, `TRANSIT_MIXED` |
| routes[].title | string | FE 표시명 |
| routes[].disabilityType | string | 요청에 사용된 장애 유형 |
| routes[].appliedProfile | string | backend가 적용한 profile 또는 model key |
| routes[].distanceMeter | number | 총 거리 |
| routes[].estimatedTimeMinute | number | 예상 시간 |
| routes[].riskLevel | string | `LOW`, `MEDIUM`, `HIGH` |
| routes[].geometry | object | 지도에 즉시 그릴 수 있는 polyline 좌표 목록 |
| routes[].summary | object | 카드 요약 정보 |
| routes[].segments | array | 상세 패널용 구간 목록 |
| routes[].segments[].sequence | number | 구간 순서 |
| routes[].segments[].name | string | 구간명 또는 안내 제목 |
| routes[].segments[].distanceMeter | number | 구간 거리 |
| routes[].segments[].slopePercent | number | 경사도. 없으면 `null` |
| routes[].segments[].hasCrosswalk | boolean | 횡단보도 여부 |
| routes[].segments[].hasSignal | boolean | 신호등 여부 |
| routes[].segments[].hasAudioSignal | boolean | 음향신호기 여부 |
| routes[].segments[].hasBrailleBlock | boolean | 점자블록 여부 |
| routes[].segments[].hasStairs | boolean | 계단 여부 |
| routes[].segments[].hasCurbGap | boolean | 단차 여부 |
| routes[].segments[].guidanceMessage | string | 안내 문구 |

#### Response Example

```json
{
  "success": true,
  "data": {
    "searchId": "route-search-20260417-0001",
    "distancePolicy": {
      "thresholdMeter": 1000,
      "primaryDistanceMeter": 1264
    },
    "availableRouteOptions": [
      "SAFE",
      "SHORTEST",
      "TRANSIT_MIXED"
    ],
    "routes": [
      {
        "routeId": "route-safe-01",
        "routeOption": "SAFE",
        "title": "안전한길",
        "disabilityType": "VISUAL",
        "appliedProfile": "viewer-visual-safe",
        "distanceMeter": 1310,
        "estimatedTimeMinute": 21,
        "riskLevel": "LOW",
        "geometry": {
          "type": "LineString",
          "coordinates": [
            [129.0756416, 35.1795543],
            [129.0749910, 35.1771200]
          ]
        },
        "summary": {
          "recommendedReason": "점자블록과 신호등이 많은 경로 우선"
        },
        "segments": [
          {
            "sequence": 1,
            "name": "시청 앞 횡단보도",
            "distanceMeter": 80,
            "slopePercent": 1.2,
            "hasCrosswalk": true,
            "hasSignal": true,
            "hasAudioSignal": true,
            "hasBrailleBlock": true,
            "hasStairs": false,
            "hasCurbGap": false,
            "guidanceMessage": "전방 횡단보도를 건넌 뒤 직진하세요."
          }
        ]
      }
    ]
  },
  "message": null
}
```

### 2. 에러 규칙

| HTTP Status | code | message | 발생 조건 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | 좌표 누락, `disabilityType` 미선택, enum 오류 |
| `404` | `RESOURCE_NOT_FOUND` | 탐색 가능한 경로가 없습니다. | 후보 경로 없음 |
| `409` | `ROUTE_OPTION_NOT_SUPPORTED` | 요청한 경로 옵션을 현재 지원하지 않습니다. | `TRANSIT_MIXED` 연동 미구현 |
| `500` | `INTERNAL_ERROR` | 서버 내부 오류가 발생했습니다. | GraphHopper 처리 중 예외 |

### 3. FE 처리 규칙

- FE는 `availableRouteOptions` 순서대로 카드 UI를 렌더링한다.
- FE는 `routes[].geometry`만으로 미리보기 polyline을 즉시 그릴 수 있어야 한다.
- FE는 `선택하기` 클릭 후 추가 API 호출 없이 응답에 포함된 `segments`로 상세 패널을 구성하는 것을 기본안으로 둔다.
- 만약 응답 크기가 과도해지면 이후 단계에서 `요약 조회`와 `상세 조회`를 분리한다.
