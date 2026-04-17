import { useEffect, useMemo, useRef, useState } from "react";

const BUSAN_CENTER = { lat: 35.1796, lng: 129.0756, label: "부산광역시청" };
const KAKAO_KEY = import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY;
const KAKAO_ALLOWED_ORIGINS = ["http://localhost:3000", "http://127.0.0.1:3000"];

function normalizeLocalKakaoHost() {
  if (typeof window === "undefined") return;
  if (window.location.hostname !== "127.0.0.1") return;
  const nextUrl = new URL(window.location.href);
  nextUrl.hostname = "localhost";
  window.location.replace(nextUrl.toString());
}

function formatCoordinate(point) {
  if (!point) return "선택되지 않음";
  if (point.label) return point.label;
  return `${point.lat.toFixed(5)}, ${point.lng.toFixed(5)}`;
}

function formatDistance(distanceMeter) {
  if (distanceMeter == null) return "-";
  return distanceMeter >= 1000
    ? `${(distanceMeter / 1000).toFixed(2)}km`
    : `${Math.round(distanceMeter)}m`;
}

function formatArrival(minutes) {
  if (minutes == null) return "-";
  const date = new Date(Date.now() + minutes * 60_000);
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function formatIsoTime(isoString) {
  if (!isoString) return "-";
  return isoString.slice(11, 16);
}

function toKoreanType(type) {
  return type === "VISUAL" ? "시각장애인" : "휠체어";
}

function routeOptionLabel(routeOption) {
  if (routeOption === "SAFE") return "안전한 길";
  if (routeOption === "SHORTEST") return "빠른 길";
  return "대중교통 혼합길";
}

function riskLevelLabel(riskLevel) {
  if (!riskLevel) return "-";
  if (riskLevel === "LOW") return "낮음";
  if (riskLevel === "MEDIUM") return "보통";
  if (riskLevel === "HIGH") return "높음";
  return riskLevel;
}

function loadKakaoMapSdk() {
  if (window.kakao?.maps) {
    return Promise.resolve(window.kakao);
  }
  if (!KAKAO_KEY) {
    return Promise.reject(new Error("카카오 JavaScript 키가 비어 있다."));
  }

  return new Promise((resolve, reject) => {
    const finishLoad = () => {
      if (!window.kakao?.maps?.load) {
        reject(
          new Error(
            `카카오 SDK가 로드되었지만 초기화되지 않았다. 현재 origin(${window.location.origin})과 허용 도메인(${KAKAO_ALLOWED_ORIGINS.join(", ")})을 확인해 달라.`,
          ),
        );
        return;
      }
      window.kakao.maps.load(() => resolve(window.kakao));
    };

    const existing = document.querySelector("script[data-kakao-map-sdk='true']");
    if (existing) {
      if (window.kakao?.maps?.load) {
        finishLoad();
        return;
      }
      existing.addEventListener("load", finishLoad, { once: true });
      existing.addEventListener("error", () => reject(new Error("카카오 SDK 스크립트를 불러오지 못했다.")), {
        once: true,
      });
      return;
    }

    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_KEY}&autoload=false&libraries=services`;
    script.async = true;
    script.dataset.kakaoMapSdk = "true";
    script.onload = finishLoad;
    script.onerror = () =>
      reject(
        new Error(
          `카카오 SDK 로드에 실패했다. ${window.location.origin} 이 허용 도메인에 등록되어 있는지 확인해 달라.`,
        ),
      );
    document.head.appendChild(script);
  });
}

function createSelectionOverlay(label, modifier = "") {
  return `<div class="map-pill ${modifier}">${label}</div>`;
}

function createMarkerBadge(label) {
  return `<div class="map-badge">${label}</div>`;
}

function buildRouteMetricsLabel(route) {
  return `${formatDistance(route.distanceMeter)} · ${route.estimatedTimeMinute}분 · 도착 ${formatArrival(route.estimatedTimeMinute)}`;
}

function buildPoint(lat, lng, label) {
  return { lat, lng, label };
}

export default function App() {
  const mapElementRef = useRef(null);
  const mapRef = useRef(null);
  const geocoderRef = useRef(null);
  const kakaoRef = useRef(null);
  const pickModeRef = useRef("idle");
  const ignoreNextClickRef = useRef(false);
  const selectionMarkersRef = useRef([]);
  const selectionOverlaysRef = useRef([]);
  const pendingMarkersRef = useRef([]);
  const routeMarkersRef = useRef([]);
  const routeOverlaysRef = useRef([]);
  const routePolylineRef = useRef(null);

  const [mapState, setMapState] = useState({
    ready: false,
    error: "",
  });
  const [selectionState, setSelectionState] = useState({
    startPoint: BUSAN_CENTER,
    endPoint: null,
    disabilityType: "VISUAL",
  });
  const [pickState, setPickState] = useState({
    mode: "idle",
    pendingPoint: null,
    resolving: false,
    error: "",
  });
  const [routeState, setRouteState] = useState({
    searching: false,
    transitLoading: false,
    apiError: "",
    routeSearchData: null,
    selectedRouteOption: null,
    selectedWalkRoute: null,
    transitCandidates: [],
    selectedTransitCandidate: null,
  });

  const activeRoute = useMemo(
    () => routeState.selectedTransitCandidate ?? routeState.selectedWalkRoute,
    [routeState.selectedTransitCandidate, routeState.selectedWalkRoute],
  );

  useEffect(() => {
    pickModeRef.current = pickState.mode;
  }, [pickState.mode]);

  useEffect(() => {
    let alive = true;
    let handleResize = null;

    if (typeof window !== "undefined" && window.location.hostname === "127.0.0.1") {
      normalizeLocalKakaoHost();
      return () => {
        alive = false;
      };
    }

    loadKakaoMapSdk()
      .then((kakao) => {
        if (!alive || !mapElementRef.current) return;
        kakaoRef.current = kakao;
        geocoderRef.current = kakao.maps.services ? new kakao.maps.services.Geocoder() : null;

        const map = new kakao.maps.Map(mapElementRef.current, {
          center: new kakao.maps.LatLng(BUSAN_CENTER.lat, BUSAN_CENTER.lng),
          level: 6,
        });
        mapRef.current = map;

        kakao.maps.event.addListener(map, "dragstart", () => {
          ignoreNextClickRef.current = true;
        });
        kakao.maps.event.addListener(map, "dragend", () => {
          window.setTimeout(() => {
            ignoreNextClickRef.current = false;
          }, 0);
        });
        kakao.maps.event.addListener(map, "click", (mouseEvent) => {
          if (ignoreNextClickRef.current) return;
          const currentMode = pickModeRef.current;
          if (currentMode === "idle") return;
          const latLng = mouseEvent.latLng;
          const nextPoint = buildPoint(latLng.getLat(), latLng.getLng(), `${latLng.getLat().toFixed(5)}, ${latLng.getLng().toFixed(5)}`);
          setPickState((previous) => ({
            ...previous,
            pendingPoint: nextPoint,
            resolving: true,
            error: "",
          }));
          resolveLabel(latLng.getLat(), latLng.getLng())
            .then((label) => {
              setPickState((previous) => {
                if (previous.mode === "idle" || !previous.pendingPoint) return previous;
                return {
                  ...previous,
                  pendingPoint: buildPoint(latLng.getLat(), latLng.getLng(), label),
                  resolving: false,
                };
              });
            })
            .catch(() => {
              setPickState((previous) => {
                if (previous.mode === "idle" || !previous.pendingPoint) return previous;
                return {
                  ...previous,
                  resolving: false,
                  error: "좌표를 주소로 변환하지 못해 좌표값으로 표시한다.",
                };
              });
            });
        });

        handleResize = () => {
          if (!mapRef.current) return;
          mapRef.current.relayout();
        };
        window.addEventListener("resize", handleResize);
        setMapState({ ready: true, error: "" });
      })
      .catch((error) => {
        if (alive) {
          setMapState({ ready: false, error: error.message });
        }
      });

    return () => {
      alive = false;
      if (handleResize) {
        window.removeEventListener("resize", handleResize);
      }
    };
  }, []);

  useEffect(() => {
    syncSelectionArtifacts();
  }, [selectionState.startPoint, selectionState.endPoint, pickState.pendingPoint, pickState.mode]);

  useEffect(() => {
    syncRouteArtifacts();
  }, [activeRoute, selectionState.startPoint, selectionState.endPoint]);

  function syncSelectionArtifacts() {
    if (!mapRef.current || !kakaoRef.current) return;
    clearCollection(selectionMarkersRef.current);
    clearCollection(selectionOverlaysRef.current);
    clearCollection(pendingMarkersRef.current);
    selectionMarkersRef.current = [];
    selectionOverlaysRef.current = [];
    pendingMarkersRef.current = [];

    const map = mapRef.current;
    const kakao = kakaoRef.current;

    [
      { point: selectionState.startPoint, label: "출발지", modifier: "start" },
      { point: selectionState.endPoint, label: "도착지", modifier: "end" },
    ].forEach(({ point, label, modifier }) => {
      if (!point) return;
      const position = new kakao.maps.LatLng(point.lat, point.lng);
      const marker = new kakao.maps.Marker({ map, position });
      const overlay = new kakao.maps.CustomOverlay({
        map,
        position,
        yAnchor: 1.7,
        content: createSelectionOverlay(label, modifier),
      });
      selectionMarkersRef.current.push(marker);
      selectionOverlaysRef.current.push(overlay);
    });

    if (pickState.pendingPoint && pickState.mode !== "idle") {
      const position = new kakao.maps.LatLng(pickState.pendingPoint.lat, pickState.pendingPoint.lng);
      const marker = new kakao.maps.Marker({ map, position });
      const overlay = new kakao.maps.CustomOverlay({
        map,
        position,
        yAnchor: 1.7,
        content: createSelectionOverlay("확정 전", "preview"),
      });
      pendingMarkersRef.current.push(marker, overlay);
      if (typeof map.panTo === "function") {
        map.panTo(position);
      }
    }
  }

  function syncRouteArtifacts() {
    if (!mapRef.current || !kakaoRef.current) return;
    clearCollection(routeMarkersRef.current);
    clearCollection(routeOverlaysRef.current);
    routeMarkersRef.current = [];
    routeOverlaysRef.current = [];
    if (routePolylineRef.current) {
      routePolylineRef.current.setMap(null);
      routePolylineRef.current = null;
    }

    if (!activeRoute?.geometry?.coordinates?.length) return;

    const map = mapRef.current;
    const kakao = kakaoRef.current;
    const path = activeRoute.geometry.coordinates.map(
      (coordinate) => new kakao.maps.LatLng(coordinate[1], coordinate[0]),
    );

    routePolylineRef.current = new kakao.maps.Polyline({
      map,
      path,
      strokeWeight: 6,
      strokeColor: routeState.selectedTransitCandidate ? "#2563eb" : "#16a34a",
      strokeOpacity: 0.9,
      strokeStyle: "solid",
    });

    if (routeState.selectedTransitCandidate?.markers?.length) {
      routeState.selectedTransitCandidate.markers.forEach((markerData) => {
        const position = new kakao.maps.LatLng(markerData.lat, markerData.lng);
        const marker = new kakao.maps.Marker({ map, position });
        const overlay = new kakao.maps.CustomOverlay({
          map,
          position,
          yAnchor: 1.9,
          content: createMarkerBadge(markerData.label),
        });
        routeMarkersRef.current.push(marker);
        routeOverlaysRef.current.push(overlay);
      });
    }

    const bounds = new kakao.maps.LatLngBounds();
    path.forEach((point) => bounds.extend(point));
    if (selectionState.startPoint) {
      bounds.extend(new kakao.maps.LatLng(selectionState.startPoint.lat, selectionState.startPoint.lng));
    }
    if (selectionState.endPoint) {
      bounds.extend(new kakao.maps.LatLng(selectionState.endPoint.lat, selectionState.endPoint.lng));
    }
    map.setBounds(bounds);
  }

  async function resolveLabel(lat, lng) {
    const geocoder = geocoderRef.current;
    const kakao = kakaoRef.current;
    if (!geocoder || !kakao?.maps?.services) {
      return `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
    }
    return new Promise((resolve) => {
      geocoder.coord2Address(lng, lat, (result, status) => {
        if (status === kakao.maps.services.Status.OK && result[0]) {
          const road = result[0].road_address?.address_name;
          const address = result[0].address?.address_name;
          resolve(road || address || `${lat.toFixed(5)}, ${lng.toFixed(5)}`);
          return;
        }
        resolve(`${lat.toFixed(5)}, ${lng.toFixed(5)}`);
      });
    });
  }

  function clearCollection(collection) {
    collection.forEach((entry) => entry.setMap?.(null));
  }

  function resetRouteState() {
    setRouteState((previous) => ({
      ...previous,
      apiError: "",
      routeSearchData: null,
      selectedRouteOption: null,
      selectedWalkRoute: null,
      transitCandidates: [],
      selectedTransitCandidate: null,
    }));
  }

  function beginPicking(mode) {
    setPickState({
      mode,
      pendingPoint: null,
      resolving: false,
      error: "",
    });
  }

  function cancelPicking() {
    setPickState({
      mode: "idle",
      pendingPoint: null,
      resolving: false,
      error: "",
    });
  }

  function confirmPendingPoint() {
    if (!pickState.pendingPoint) return;
    const field = pickState.mode === "picking-start" ? "startPoint" : "endPoint";
    setSelectionState((previous) => ({
      ...previous,
      [field]: pickState.pendingPoint,
    }));
    resetRouteState();
    cancelPicking();
  }

  function updateDisabilityType(nextType) {
    setSelectionState((previous) => ({
      ...previous,
      disabilityType: nextType,
    }));
    resetRouteState();
  }

  async function runRouteSearch() {
    if (!selectionState.startPoint || !selectionState.endPoint || !selectionState.disabilityType) return;
    setRouteState((previous) => ({
      ...previous,
      searching: true,
      apiError: "",
      selectedRouteOption: null,
      selectedWalkRoute: null,
      transitCandidates: [],
      selectedTransitCandidate: null,
    }));

    try {
      const response = await fetch("/api/routes/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          startPoint: { lat: selectionState.startPoint.lat, lng: selectionState.startPoint.lng },
          endPoint: { lat: selectionState.endPoint.lat, lng: selectionState.endPoint.lng },
          disabilityType: selectionState.disabilityType,
        }),
      });
      if (!response.ok) {
        throw new Error(`routes/search returned ${response.status}`);
      }
      const body = await response.json();
      setRouteState((previous) => ({
        ...previous,
        searching: false,
        routeSearchData: body.data,
      }));
    } catch (error) {
      setRouteState((previous) => ({
        ...previous,
        searching: false,
        apiError: error.message,
      }));
    }
  }

  async function handleRouteOptionClick(route) {
    setRouteState((previous) => ({
      ...previous,
      selectedRouteOption: route.routeOption,
      selectedWalkRoute: null,
      selectedTransitCandidate: null,
    }));

    if (route.routeOption === "TRANSIT_MIXED") {
      await loadTransitCandidates();
      return;
    }

    setRouteState((previous) => ({
      ...previous,
      selectedRouteOption: route.routeOption,
      selectedWalkRoute: route,
      selectedTransitCandidate: null,
    }));
  }

  async function loadTransitCandidates() {
    if (!selectionState.startPoint || !selectionState.endPoint) return;
    setRouteState((previous) => ({
      ...previous,
      transitLoading: true,
      apiError: "",
      selectedWalkRoute: null,
      selectedTransitCandidate: null,
      transitCandidates: [],
    }));

    try {
      const response = await fetch("/api/routes/transit-mixed/candidates", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          startPoint: { lat: selectionState.startPoint.lat, lng: selectionState.startPoint.lng },
          endPoint: { lat: selectionState.endPoint.lat, lng: selectionState.endPoint.lng },
          disabilityType: selectionState.disabilityType,
        }),
      });
      if (!response.ok) {
        throw new Error(`transit-mixed returned ${response.status}`);
      }
      const body = await response.json();
      const candidates = body.data.candidates || [];
      setRouteState((previous) => ({
        ...previous,
        transitLoading: false,
        transitCandidates: candidates,
        selectedTransitCandidate: candidates[0] ?? null,
      }));
    } catch (error) {
      setRouteState((previous) => ({
        ...previous,
        transitLoading: false,
        apiError: error.message,
      }));
    }
  }

  function renderRouteCards() {
    if (!routeState.routeSearchData) return null;

    return (
      <div className="route-card-list">
        {routeState.routeSearchData.routes.map((route) => (
          <button
            key={route.routeId}
            className={routeState.selectedRouteOption === route.routeOption ? "route-card active" : "route-card"}
            onClick={() => handleRouteOptionClick(route)}
          >
            <div className="route-card-top">
              <strong>{route.title}</strong>
              <span>{routeOptionLabel(route.routeOption)}</span>
            </div>
            <div className="route-metrics">
              <span>{buildRouteMetricsLabel(route)}</span>
            </div>
            <p>{route.summary?.recommendedReason || "경로 요약 없음"}</p>
            <div className="route-debug">
              <small>프로필 {route.appliedProfile}</small>
              <small>{route.graphHopperBacked ? "GraphHopper" : "Fallback"}</small>
              <small>위험도 {riskLevelLabel(route.riskLevel)}</small>
            </div>
            {route.fallbackReason ? <small className="route-warning">{route.fallbackReason}</small> : null}
            {route.summary?.busArrivalDisplay ? <small>{route.summary.busArrivalDisplay}</small> : null}
            {route.summary?.subwayArrivalDisplay ? <small>{route.summary.subwayArrivalDisplay}</small> : null}
          </button>
        ))}
      </div>
    );
  }

  function renderTransitCandidates() {
    if (routeState.selectedRouteOption !== "TRANSIT_MIXED") return null;

    return (
      <div className="transit-block">
        <div className="section-heading">
          <h3>대중교통 혼합 후보</h3>
          <span>{routeState.transitLoading ? "조회 중.." : `${routeState.transitCandidates.length}개 후보`}</span>
        </div>
        <div className="route-card-list compact">
          {routeState.transitCandidates.map((candidate) => (
            <button
              key={candidate.candidateId}
              className={routeState.selectedTransitCandidate?.candidateId === candidate.candidateId ? "route-card active" : "route-card"}
              onClick={() =>
                setRouteState((previous) => ({
                  ...previous,
                  selectedTransitCandidate: candidate,
                }))
              }
            >
              <div className="route-card-top">
                <strong>{candidate.summary.primaryTransitLabel}</strong>
                <span>{candidate.combinationType}</span>
              </div>
              <div className="route-metrics">
                <span>{candidate.totalDurationMinute}분</span>
                <span>도착 {formatIsoTime(candidate.arrivalEstimatedAt)}</span>
                <span>도보 {formatDistance(candidate.totalWalkDistanceMeter)}</span>
              </div>
              <div className="route-debug">
                <small>{candidate.dataSource}</small>
                <small>{candidate.walkLegGraphHopperBacked ? "도보 검증 완료" : `${candidate.fallbackWalkLegCount}개 fallback`}</small>
              </div>
              {candidate.summary.busArrivalDisplay ? <small>{candidate.summary.busArrivalDisplay}</small> : null}
              {candidate.summary.subwayArrivalDisplay ? <small>{candidate.summary.subwayArrivalDisplay}</small> : null}
              {candidate.fallbackReasonSummary ? <small className="route-warning">{candidate.fallbackReasonSummary}</small> : null}
            </button>
          ))}
        </div>
      </div>
    );
  }

  function renderDetailPanel() {
    if (!activeRoute) {
      return (
        <div className="detail-empty">
          <h3>경로 상세</h3>
          <p>옵션을 선택하면 지도와 상세 정보가 여기에 표시된다.</p>
        </div>
      );
    }

    if (routeState.selectedTransitCandidate) {
      return (
        <div className="detail-panel">
          <div className="timeline-header">
            <span>출발지</span>
            <strong>{formatCoordinate(selectionState.startPoint)}</strong>
          </div>
          <div className="timeline">
            {routeState.selectedTransitCandidate.legs.map((leg) => (
              <div key={`${leg.type}-${leg.sequence}`} className="timeline-item">
                <div className="timeline-dot" />
                <div className="timeline-content">
                  <div className="timeline-meta">
                    <strong>{leg.title}</strong>
                    <span>{leg.type}</span>
                  </div>
                  <p>
                    {formatIsoTime(leg.departureAt)} - {formatIsoTime(leg.arrivalAt)} / {leg.durationMinute}분
                  </p>
                  {leg.busRouteNo ? <p>버스 {leg.busRouteNo} / {leg.boardStopName} → {leg.alightStopName}</p> : null}
                  {leg.subwayLineName ? <p>{leg.subwayLineName} / {leg.boardStationName} → {leg.alightStationName}</p> : null}
                  {leg.walkPolicy ? <p>도보 정책: {leg.walkPolicy}</p> : null}
                </div>
              </div>
            ))}
          </div>
          <div className="timeline-footer">
            <span>목적지</span>
            <strong>{formatCoordinate(selectionState.endPoint)}</strong>
          </div>
        </div>
      );
    }

    return (
      <div className="detail-panel">
        <div className="timeline-header">
          <span>출발지</span>
          <strong>{formatCoordinate(selectionState.startPoint)}</strong>
        </div>
        <div className="timeline">
          {activeRoute.segments?.map((segment) => (
            <div key={segment.sequence} className="timeline-item">
              <div className="timeline-dot" />
              <div className="timeline-content">
                <div className="timeline-meta">
                  <strong>{segment.name}</strong>
                  <span>{formatDistance(segment.distanceMeter)}</span>
                </div>
                <p>{segment.guidanceMessage}</p>
                <div className="attr-row">
                  <span>횡단보도 {segment.hasCrosswalk ? "있음" : "없음"}</span>
                  <span>경사 {segment.slopePercent ?? "-"}%</span>
                  <span>음향신호 {segment.hasAudioSignal ? "있음" : "없음"}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
        <div className="timeline-footer">
          <span>목적지</span>
          <strong>{formatCoordinate(selectionState.endPoint)}</strong>
        </div>
      </div>
    );
  }

  return (
    <main className="viewer-shell">
      <section className="side-panel">
        <div className="panel-head">
          <p className="eyebrow">Ieumgil Viewer</p>
          <h1>부산 보행 경로 Viewer</h1>
          <p className="description">
            카카오맵 위에서 출발지와 도착지를 선택하고, 보행 경로와 대중교통 혼합 후보를 비교한다.
          </p>
        </div>

        <div className="input-card">
          <div className="field-block">
            <span className="field-label">출발지</span>
            <strong>{formatCoordinate(selectionState.startPoint)}</strong>
            <button
              className={pickState.mode === "picking-start" ? "ghost active" : "ghost"}
              onClick={() => beginPicking("picking-start")}
            >
              지도에서 선택
            </button>
          </div>

          <div className="field-block">
            <span className="field-label">도착지</span>
            <strong>{formatCoordinate(selectionState.endPoint)}</strong>
            <button
              className={pickState.mode === "picking-end" ? "ghost active" : "ghost"}
              onClick={() => beginPicking("picking-end")}
            >
              지도에서 선택
            </button>
          </div>

          <div className="type-row">
            {["VISUAL", "WHEELCHAIR"].map((type) => (
              <button
                key={type}
                className={selectionState.disabilityType === type ? "chip chip-active" : "chip"}
                onClick={() => updateDisabilityType(type)}
              >
                {toKoreanType(type)}
              </button>
            ))}
          </div>

          <button
            className="primary"
            disabled={!selectionState.startPoint || !selectionState.endPoint || routeState.searching}
            onClick={runRouteSearch}
          >
            {routeState.searching ? "길찾는 중.." : "길찾기"}
          </button>

          {routeState.apiError ? <p className="error">{routeState.apiError}</p> : null}
        </div>

        {routeState.routeSearchData ? (
          <div className="meta-strip">
            <span>기준 거리 {formatDistance(routeState.routeSearchData.distancePolicy.primaryDistanceMeter)}</span>
            <span>옵션 {routeState.routeSearchData.availableRouteOptions.length}개</span>
          </div>
        ) : null}

        {renderRouteCards()}
        {renderTransitCandidates()}
        {renderDetailPanel()}
      </section>

      <section className="map-panel">
        <div className="map-toolbar">
          <div>
            <strong>현재 선택</strong>
            <p>
              {routeState.selectedTransitCandidate
                ? "대중교통 혼합 후보"
                : routeState.selectedWalkRoute?.title || "아직 선택된 경로 없음"}
            </p>
          </div>
          <div className="toolbar-badges">
            <span>{toKoreanType(selectionState.disabilityType)}</span>
            <span>
              {pickState.mode === "idle"
                ? "지도 탐색 모드"
                : pickState.mode === "picking-start"
                  ? "출발지 선택 모드"
                  : "도착지 선택 모드"}
            </span>
          </div>
        </div>

        {pickState.mode !== "idle" ? (
          <div className="pick-panel">
            <div>
              <strong>{pickState.mode === "picking-start" ? "출발지" : "도착지"} 선택 중</strong>
              <p>
                지도를 자유롭게 움직인 뒤 원하는 위치를 클릭하면 임시 마커가 놓인다. 그 다음 확정 버튼을 눌러 반영한다.
              </p>
              {pickState.pendingPoint ? <p className="pick-summary">{formatCoordinate(pickState.pendingPoint)}</p> : null}
              {pickState.resolving ? <p className="pick-status">주소를 확인하는 중..</p> : null}
              {pickState.error ? <p className="pick-status warning">{pickState.error}</p> : null}
            </div>
            <div className="pick-actions">
              <button className="ghost" onClick={cancelPicking}>
                취소
              </button>
              <button className="primary small" disabled={!pickState.pendingPoint} onClick={confirmPendingPoint}>
                이 위치로 확정
              </button>
            </div>
          </div>
        ) : null}

        <div className={pickState.mode === "idle" ? "map-wrapper" : "map-wrapper picking"}>
          <div ref={mapElementRef} className="map-canvas" />
          {!mapState.ready ? (
            <div className="map-overlay">
              <strong>카카오맵 준비 중</strong>
              <p>{mapState.error || "SDK를 로드하고 부산 지도를 준비하고 있다."}</p>
            </div>
          ) : null}
        </div>
      </section>
    </main>
  );
}
