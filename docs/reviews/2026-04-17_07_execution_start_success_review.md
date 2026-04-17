# 2026-04-17 07 대중교통 혼합 경로 실행 시작 성공 리뷰

## 작업 범위

`docs/plans/07_accessible_transit_mixed_routing_plan.md`의 첫 실행 슬라이스로 아래를 구현했다.

- `POST /routes/transit-mixed/candidates` endpoint 추가
- 대중교통 혼합 후보 요청/응답 모델 추가
- 장애유형 `VISUAL`, `WHEELCHAIR` 입력 처리 추가
- 대중교통 후보 seed, 버스 도착 보강, 지하철 운행시간 보강용 서비스 인터페이스 추가
- ODsay/BIMS/부산 지하철 운행정보 자리를 대체하는 stub client 추가
- 모든 `WALK` leg를 `TransitWalkLegRouter`로 재계산하는 구조 추가
- GraphHopper 로드 서비스에 현재 hopper 노출 메서드 추가
- `application.yaml`에 `.env` 기반 대중교통 혼합 설정 브리지 추가
- transit facade/controller 테스트 추가

## 이번 단계에서 의도적으로 남긴 것

- ODsay 실응답 파싱 및 정규화
- 부산 BIMS 실데이터 매핑
- 부산 지하철 운행정보 실데이터 매핑
- 장애유형별 실제 GraphHopper custom model 차등 적용
- docker compose 기반 servlet API 검증

즉 이번 단계는 “API와 오케스트레이션 뼈대를 코드로 올리고, FE가 붙을 수 있는 응답 계약을 먼저 고정하는 단계”로 본다.

## 검증 결과

### 1. 테스트

실행 명령:

```powershell
cd poc
./gradlew.bat test
```

결과:

- 전체 테스트 통과
- 추가한 테스트
  - `com.example.poc.transit.service.TransitMixedRouteFacadeTest`
  - `com.example.poc.transit.web.TransitMixedRouteControllerTest`

검증한 내용:

- 상위 `3개` 후보 반환
- `WALK` leg에 장애유형별 안전경로 policy 메타데이터 부여
- controller request validation 동작
- endpoint 응답 envelope 구조 유지

### 2. 애플리케이션 기동 확인

실행 명령:

```powershell
cd poc
./gradlew.bat bootRun
```

확인 범위:

- Spring Boot servlet 앱이 로컬 `8080` 포트에서 정상 기동되는 로그를 확인했다.

제한:

- PowerShell 래퍼로 자동 HTTP probe를 붙인 검증은 안정적으로 캡처되지 않아, HTTP 계약 검증은 MockMvc 테스트로 대체했다.

### 3. docker compose 검증

이번 변경은 compose 검증을 수행하지 않았다.

이유:

- 현재 `docker-compose.yml`의 `backend` 서비스는 `SPRING_MAIN_WEB_APPLICATION_TYPE: none`으로 설정된 배치 실행기다.
- 따라서 새로 추가한 servlet API endpoint를 현재 compose 구성만으로 직접 검증하기 어렵다.
- 이후 viewer 연동 단계에서 web backend 실행 구성을 compose에 분리하거나 확장해야 한다.

## 확인된 리스크

- 현재 `/routes/transit-mixed/candidates`는 실데이터 연동이 아니라 stub client 기반이다.
- GraphHopper walk leg 재계산은 hopper가 준비되지 않으면 fallback 직선 경로 추정으로 내려간다.
- 사용자 유형은 `VISUAL`, `WHEELCHAIR`로 정렬했다.
- 버스/지하철 식별자 매핑과 실시간 도착정보 신뢰도는 후속 구현에서 검증해야 한다.

## 다음 단계 제안

1. ODsay 응답 파서와 내부 `TransitCandidateSeed` 정규화 구현
2. 부산 BIMS 정류장/도착 API 실제 연동 구현
3. 부산 지하철 운행정보 실제 연동 구현
4. GraphHopper profile/custom model을 장애유형별 안전경로로 분리
5. 08 viewer가 새 endpoint를 소비하도록 FE 연동 시작
