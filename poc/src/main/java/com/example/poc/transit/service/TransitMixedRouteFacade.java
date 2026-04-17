package com.example.poc.transit.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.poc.route.model.RouteOption;
import com.example.poc.route.model.RoutingPolicy;
import com.example.poc.transit.client.BusanBimsTransitClient;
import com.example.poc.transit.client.BusanSubwayOperationClient;
import com.example.poc.transit.client.OdsayTransitClient;
import com.example.poc.transit.config.TransitMixedRoutingProperties;
import com.example.poc.transit.model.DisabilityType;
import com.example.poc.transit.model.TransitCandidateResponse;
import com.example.poc.transit.model.TransitCandidateSummaryResponse;
import com.example.poc.transit.model.TransitGeometryResponse;
import com.example.poc.transit.model.TransitLegResponse;
import com.example.poc.transit.model.TransitLegType;
import com.example.poc.transit.model.TransitMarkerResponse;
import com.example.poc.transit.model.TransitMarkerType;
import com.example.poc.transit.model.TransitMixedRouteRequest;
import com.example.poc.transit.model.TransitMixedRouteResponse;
import com.example.poc.transit.model.TransitVerificationStatus;

@Service
@ConditionalOnProperty(prefix = "poc.transit-mixed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransitMixedRouteFacade {

	private final TransitMixedRoutingProperties properties;
	private final OdsayTransitClient odsayTransitClient;
	private final BusanBimsTransitClient busanBimsTransitClient;
	private final BusanSubwayOperationClient busanSubwayOperationClient;
	private final TransitWalkLegRouter transitWalkLegRouter;
	private final Clock clock;

	public TransitMixedRouteFacade(
			TransitMixedRoutingProperties properties,
			OdsayTransitClient odsayTransitClient,
			BusanBimsTransitClient busanBimsTransitClient,
			BusanSubwayOperationClient busanSubwayOperationClient,
			TransitWalkLegRouter transitWalkLegRouter,
			Clock clock) {
		this.properties = properties;
		this.odsayTransitClient = odsayTransitClient;
		this.busanBimsTransitClient = busanBimsTransitClient;
		this.busanSubwayOperationClient = busanSubwayOperationClient;
		this.transitWalkLegRouter = transitWalkLegRouter;
		this.clock = clock;
	}

	public TransitMixedRouteResponse searchCandidates(TransitMixedRouteRequest request) {
		OffsetDateTime departureAt = request.departureAt() == null
				? OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.ofHours(9))
				: request.departureAt();
		int limit = request.maxCandidates() == null
				? properties.getMaxCandidates()
				: Math.min(request.maxCandidates(), properties.getMaxCandidates());
		List<TransitCandidateResponse> rankedCandidates = odsayTransitClient.searchSeeds(request, departureAt).stream()
				.map(seed -> assembleCandidate(seed, request, departureAt))
				.sorted(Comparator
						.comparing((TransitCandidateResponse candidate) -> candidate.verificationStatus() != TransitVerificationStatus.VERIFIED)
						.thenComparingInt(TransitCandidateResponse::totalDurationMinute)
						.thenComparing(TransitCandidateResponse::arrivalEstimatedAt)
						.thenComparingDouble(TransitCandidateResponse::totalWalkDistanceMeter)
						.thenComparingInt(TransitCandidateResponse::transferCount))
				.limit(limit)
				.toList();

		List<TransitCandidateResponse> normalizedRanks = new ArrayList<>();
		for (int index = 0; index < rankedCandidates.size(); index++) {
			TransitCandidateResponse candidate = rankedCandidates.get(index);
			normalizedRanks.add(new TransitCandidateResponse(
					candidate.candidateId(),
					index + 1,
					candidate.combinationType(),
					candidate.verificationStatus(),
					candidate.totalDurationMinute(),
					candidate.arrivalEstimatedAt(),
					candidate.dataSource(),
					candidate.walkLegGraphHopperBacked(),
					candidate.fallbackWalkLegCount(),
					candidate.fallbackReasonSummary(),
					candidate.totalWalkDistanceMeter(),
					candidate.transferCount(),
					candidate.summary(),
					candidate.geometry(),
					candidate.markers(),
					candidate.legs()));
		}
		return new TransitMixedRouteResponse(
				"transit-mixed-" + UUID.randomUUID(),
				request.disabilityType(),
				RouteOption.TRANSIT_MIXED,
				RoutingPolicy.ACCESSIBLE_TRANSIT,
				walkProfile(request.disabilityType()),
				"TRANSIT_MIXED",
				describeDataSource(),
				departureAt,
				normalizedRanks);
	}

	private TransitCandidateResponse assembleCandidate(
			TransitCandidateSeed seed,
			TransitMixedRouteRequest request,
			OffsetDateTime departureAt) {
		TransitWaypoint start = new TransitWaypoint(request.startPoint().lat(), request.startPoint().lng());
		TransitWaypoint end = new TransitWaypoint(request.endPoint().lat(), request.endPoint().lng());
		List<TransitLegResponse> legs = new ArrayList<>();
		List<TransitMarkerResponse> markers = new ArrayList<>();
		List<List<Double>> combinedCoordinates = new ArrayList<>();
		double totalWalkDistanceMeter = 0d;
		boolean allWalkLegsVerified = true;
		int fallbackWalkLegCount = 0;
		List<String> fallbackReasons = new ArrayList<>();
		OffsetDateTime currentTime = departureAt;
		markers.add(new TransitMarkerResponse(TransitMarkerType.START, "Start", start.lat(), start.lng(), 0));

		for (int index = 0; index < seed.legs().size(); index++) {
			TransitLegSeed legSeed = seed.legs().get(index);
			TransitWaypoint legStart = interpolate(start, end, legSeed.startRatio());
			TransitWaypoint legEnd = interpolate(start, end, legSeed.endRatio());
			TransitGeometryResponse geometry;
			TransitLegResponse leg;

			if (legSeed.type() == TransitLegType.WALK) {
				TransitWalkLegRouter.WalkRoutingResult walkResult = transitWalkLegRouter.route(
						legStart,
						legEnd,
						request.disabilityType(),
						RoutingPolicy.SAFE_WALK);
				totalWalkDistanceMeter += walkResult.distanceMeter();
				allWalkLegsVerified = allWalkLegsVerified && walkResult.graphHopperBacked();
				if (walkResult.fallbackUsed()) {
					fallbackWalkLegCount++;
					if (walkResult.fallbackReason() != null && !walkResult.fallbackReason().isBlank()) {
						fallbackReasons.add(walkResult.fallbackReason());
					}
				}
				OffsetDateTime arrivalAt = currentTime.plusMinutes(walkResult.durationMinute());
				geometry = new TransitGeometryResponse("LineString", walkResult.coordinates());
				leg = new TransitLegResponse(
						TransitLegType.WALK,
						index + 1,
						legSeed.title(),
						round1(walkResult.distanceMeter()),
						walkResult.durationMinute(),
						currentTime,
						arrivalAt,
						geometry,
						walkResult.walkPolicy() + ":" + walkResult.appliedProfile(),
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						null);
				currentTime = arrivalAt;
			} else if (legSeed.type() == TransitLegType.BUS) {
				BusanBimsTransitClient.BusArrivalEnrichment busInfo = busanBimsTransitClient.enrichBusLeg(
						legSeed.routeId(),
						legSeed.routeNo(),
						legSeed.boardLabel(),
						currentTime);
				OffsetDateTime boardAt = currentTime.plusMinutes(busInfo.arrivalMinute());
				OffsetDateTime alightAt = boardAt.plusMinutes(legSeed.plannedDurationMinute());
				geometry = lineGeometry(legStart, legEnd);
				leg = new TransitLegResponse(
						TransitLegType.BUS,
						index + 1,
						legSeed.title(),
						round1(distanceMeters(legStart, legEnd)),
						legSeed.plannedDurationMinute() + busInfo.arrivalMinute(),
						currentTime,
						alightAt,
						geometry,
						null,
						legSeed.routeId(),
						legSeed.routeNo(),
						legSeed.boardLabel(),
						legSeed.alightLabel(),
						busInfo.arrivalMinute(),
						busInfo.lowFloorExpected(),
						null,
						null,
						null,
						null,
						null);
				addMarker(markers, legSeed.boardMarkerType(), legSeed.boardLabel(), legStart, markers.size());
				addMarker(markers, legSeed.alightMarkerType(), legSeed.alightLabel(), legEnd, markers.size());
				currentTime = alightAt;
			} else {
				BusanSubwayOperationClient.SubwayScheduleEnrichment subwayInfo = busanSubwayOperationClient.enrichSubwayLeg(
						legSeed.lineName(),
						legSeed.boardLabel(),
						legSeed.alightLabel(),
						currentTime,
						legSeed.plannedDurationMinute());
				geometry = lineGeometry(legStart, legEnd);
				leg = new TransitLegResponse(
						TransitLegType.SUBWAY,
						index + 1,
						legSeed.title(),
						round1(distanceMeters(legStart, legEnd)),
						(int) java.time.Duration.between(currentTime, subwayInfo.arrivalAt()).toMinutes(),
						currentTime,
						subwayInfo.arrivalAt(),
						geometry,
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						legSeed.lineName(),
						legSeed.boardLabel(),
						legSeed.alightLabel(),
						subwayInfo.departureAt(),
						subwayInfo.arrivalAt());
				addMarker(markers, legSeed.boardMarkerType(), legSeed.boardLabel(), legStart, markers.size());
				addMarker(markers, legSeed.alightMarkerType(), legSeed.alightLabel(), legEnd, markers.size());
				currentTime = subwayInfo.arrivalAt();
			}

			appendCoordinates(combinedCoordinates, geometry.coordinates());
			legs.add(leg);
		}

		markers.add(new TransitMarkerResponse(TransitMarkerType.END, "End", end.lat(), end.lng(), markers.size()));
		TransitVerificationStatus status = allWalkLegsVerified ? TransitVerificationStatus.VERIFIED : TransitVerificationStatus.PARTIAL;
		TransitCandidateSummaryResponse summary = new TransitCandidateSummaryResponse(
				seed.primaryTransitLabel(),
				firstBusDisplay(legs),
				firstSubwayDisplay(legs));
		return new TransitCandidateResponse(
				"candidate-" + seed.combinationType().name().toLowerCase() + "-" + UUID.randomUUID(),
				0,
				seed.combinationType(),
				status,
				(int) java.time.Duration.between(departureAt, currentTime).toMinutes(),
				currentTime,
				describeDataSource(),
				allWalkLegsVerified,
				fallbackWalkLegCount,
				summarizeFallbackReasons(fallbackReasons),
				round1(totalWalkDistanceMeter),
				seed.transferCount(),
				summary,
				new TransitGeometryResponse("LineString", combinedCoordinates),
				markers,
				legs);
	}

	private String firstBusDisplay(List<TransitLegResponse> legs) {
		return legs.stream()
				.filter(leg -> leg.type() == TransitLegType.BUS && leg.busArrivalMinute() != null)
				.findFirst()
				.map(leg -> "Bus " + leg.busRouteNo() + " in " + leg.busArrivalMinute() + " min")
				.orElse(null);
	}

	private String firstSubwayDisplay(List<TransitLegResponse> legs) {
		return legs.stream()
				.filter(leg -> leg.type() == TransitLegType.SUBWAY && leg.subwayDepartureAt() != null)
				.findFirst()
				.map(leg -> leg.subwayLineName() + " departs at " + leg.subwayDepartureAt().toLocalTime().withSecond(0).withNano(0))
				.orElse(null);
	}

	private void addMarker(
			List<TransitMarkerResponse> markers,
			TransitMarkerType markerType,
			String label,
			TransitWaypoint waypoint,
			int sequence) {
		if (markerType == null || label == null) {
			return;
		}
		markers.add(new TransitMarkerResponse(markerType, label, waypoint.lat(), waypoint.lng(), sequence));
	}

	private TransitWaypoint interpolate(TransitWaypoint start, TransitWaypoint end, double ratio) {
		double lat = start.lat() + ((end.lat() - start.lat()) * ratio);
		double lng = start.lng() + ((end.lng() - start.lng()) * ratio);
		return new TransitWaypoint(lat, lng);
	}

	private TransitGeometryResponse lineGeometry(TransitWaypoint start, TransitWaypoint end) {
		return new TransitGeometryResponse(
				"LineString",
				List.of(
						List.of(start.lng(), start.lat()),
						List.of(end.lng(), end.lat())));
	}

	private void appendCoordinates(List<List<Double>> target, List<List<Double>> coordinates) {
		for (List<Double> coordinate : coordinates) {
			if (!target.isEmpty() && target.get(target.size() - 1).equals(coordinate)) {
				continue;
			}
			target.add(coordinate);
		}
	}

	private double distanceMeters(TransitWaypoint start, TransitWaypoint end) {
		double latDistance = start.lat() - end.lat();
		double lngDistance = start.lng() - end.lng();
		return Math.sqrt((latDistance * latDistance) + (lngDistance * lngDistance)) * 111_000d;
	}

	private double round1(double value) {
		return Math.round(value * 10d) / 10d;
	}

	private String walkProfile(DisabilityType disabilityType) {
		return switch (disabilityType) {
			case VISUAL -> "visual_safe";
			case WHEELCHAIR -> "wheelchair_safe";
		};
	}

	private String describeDataSource() {
		if (odsayTransitClient.getClass().getSimpleName().startsWith("Stub")) {
			return "STUB";
		}
		return "LIVE";
	}

	private String summarizeFallbackReasons(List<String> fallbackReasons) {
		if (fallbackReasons.isEmpty()) {
			return null;
		}
		return fallbackReasons.stream()
				.distinct()
				.collect(Collectors.joining(" | "));
	}
}
