# 2026-04-16 04 GraphHopper Import And Artifact Review

## 범위

- `docs/plans/04_graphhopper_import_and_artifact_plan.md`
- 입력 기준:
  - `etl/data/raw/busan.osm.pbf`
  - PostGIS `road_segments`
  - GraphHopper custom EV/load-only runtime

## 구현 결과

- `backend`와 분리된 GraphHopper import 계층을 `poc/src/main/java/com/example/poc/graphhopper/`에 추가했다.
- import 시작 시 `road_segments`를 한 번에 읽어 `RoadSegmentImportLookup` 메모리 맵을 만든다.
- import 중에는 DB를 다시 조회하지 않는다.
- GraphHopper import는 `IeumOsmReader`에서 DB lookup 결과를 artificial way tag로 주입하고, `IeumRoadSegmentTagParser`에서 custom EV로 기록한다.
- artifact는 `runtime/graphhopper/latest/graph-cache`에 생성되고 메타데이터는 `runtime/graphhopper/latest/artifact-metadata.json`에 남긴다.
- load-only 컨테이너는 같은 artifact를 읽어 `http://localhost:8989/internal/health`로 readiness를 노출한다.

## 검증 명령

```bash
cd poc
./gradlew test
```

```bash
cd ..
/usr/local/bin/docker compose up --build --abort-on-container-exit --exit-code-from graphhopper-importer graphhopper-importer
```

```bash
/usr/local/bin/docker compose up -d --no-deps graphhopper
curl -fsS http://localhost:8989/internal/health
```

## 검증 결과

- `./gradlew test` 통과
- `graphhopper-importer` 성공
- `graphhopper` load-only 성공
- host health check 성공:
  - `{"graphDirectory":"/graphhopper/artifacts/latest/graph-cache","loadedAtUtc":"2026-04-16T13:50:47.934859714Z","ready":true}`

## artifact 메타데이터

- `loadedRoadSegmentCount=148285`
- `loadedWayCount=67299`
- `duplicateStableKeyCount=0`
- `importedWayCount=67299`
- `matchedSegmentCount=147834`
- `unmatchedSegmentCount=2354`
- `importedEdgeCount=150188`
- `graphNodeCount=110648`
- `graphEdgeCount=150188`
- `profileName=foot`

## 운영 판단

- `road_segments` bulk load와 custom EV 주입 위치는 코드로 고정됐다.
- import 중 per-edge DB query는 없다.
- artifact 생성과 load-only 서버 분리는 compose 기준으로 재현 가능하다.

## 남은 리스크

- strict mode(`poc.graphhopper-import.fail-on-unmatched-segment=true`)로 실행하면 현재 `unmatchedSegmentCount=2354` 때문에 실패한다.
- 현재 compose 기본값은 artifact 생성과 load-only 검증을 우선하기 위해 `POC_GRAPHHOPPER_IMPORT_FAIL_ON_UNMATCHED_SEGMENT=false`로 둔다.
- mismatch edge는 graph 안에는 적재되지만 custom EV 기준으로는 `ieum_db_match=false`다.
- 원인은 GraphHopper 분할 결과와 `road_segments` anchor 분할의 일부 차이로 보이며, 후속으로 mismatch 샘플 추출과 split rule 정합화가 필요하다.
