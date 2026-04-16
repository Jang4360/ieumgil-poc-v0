# 저장소 리셋 및 계획 재작성 실행 리뷰

## 대상 작업

- ETL 데이터셋 정리
- `docs/plans/` 전면 재작성
- `poc/AGENTS.md` 신규 작성 및 비판적 수정

## 결과

- status: `success`
- success criteria met: `yes`
- retry count: `0`

## 수행 내용

- `etl/data`에서 기존 샘플, 파생 CSV, GraphHopper artifact, reference 데이터를 모두 제거하고 `etl/data/raw/busan.osm.pbf`만 남겼다.
- `docs/plans/` 기존 문서를 전부 삭제하고, 블루프린트 기준으로 보행 네트워크 생성 `2~6번`과 GraphHopper import `10~13번`만 다루는 새 계획 세트를 작성했다.
- `poc/AGENTS.md`를 새로 만들고, 운영 가이드라인과 테스트 하네스 원칙을 반영했다.
- 초안 작성 후 내용을 다시 읽고 비판적으로 검토해 결정성, 느린 테스트 분리, 임시 출력 디렉터리, 관찰성 관련 항목을 보강했다.
- 루트 `AGENTS.md`도 현재 저장소 상태와 새 계획 문서 구조에 맞게 정리했다.

## 생성/수정 파일

- `AGENTS.md`
- `poc/AGENTS.md`
- `docs/plans/01_restart_scope_and_constraints.md`
- `docs/plans/02_way_extraction_anchor_and_segmentation_plan.md`
- `docs/plans/03_network_persistence_and_stable_key_plan.md`
- `docs/plans/04_graphhopper_import_and_artifact_plan.md`
- `docs/plans/05_execution_validation_and_review_plan.md`

## 검증 근거

- `etl/data` 재확인 결과 `raw/busan.osm.pbf`만 남아 있는 것을 확인했다.
- `docs/plans/`에는 새 범위 기준의 문서 5개만 존재하는 것을 확인했다.
- `poc/AGENTS.md`에 입력 원천, 운영 메타데이터, 테스트 계층 분리, JUnit 설정 고정, Testcontainers 사용 원칙, 랜덤 포트, 느린 테스트 분리 규칙이 포함된 것을 확인했다.
- `poc/AGENTS.md` 초안 후 2차 수정에서 아래 항목을 추가 반영했다.
  - 실행 이력 추적용 메트릭
  - 시간/난수/정렬 순서 주입
  - 테스트별 독립 임시 출력 디렉터리
  - 파서 단계 관찰성
  - `slow` 계열 검증 분리

## 참고한 외부 자료

- Google SRE release engineering: tested revision 기준 release, build identifier와 audit trail 유지 원칙 참고
- JUnit 5 user guide: 저장소 내 `junit-platform.properties`로 수명주기/실행 설정을 고정하는 방식 참고
- Spring Boot test reference: 테스트 슬라이스(`@WebMvcTest`, `@DataJpaTest`) 중심 테스트 전략 참고
- Testcontainers Java manual lifecycle control: 실제 인프라 통합 테스트와 container lifecycle 선택 기준 참고

## 이슈 및 리스크

- 계획 문서는 재작성됐지만 실제 `poc` 코드와 테스트 설정은 아직 구현되지 않았다.
- 기존 파생 데이터셋을 전부 제거했기 때문에, 이후 구현에서 작은 테스트 fixture를 코드베이스 안에 다시 설계해야 한다.
- GraphHopper import 검증은 현재 문서화만 끝난 상태이며 실제 loader 구현 난이도는 아직 확인되지 않았다.

## 다음 액션

- `02_way_extraction_anchor_and_segmentation_plan`부터 순서대로 실제 구현을 시작한다.
- `poc`에 `junit-platform.properties`, 작은 OSM fixture, import smoke test 구조를 추가한다.
- 각 구현 단계가 끝날 때마다 해당 plan-id 기준 성공/실패 리뷰를 `docs/reviews/`에 계속 누적한다.
