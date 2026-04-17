package com.example.poc.route.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.poc.route.model.DistancePolicyResponse;
import com.example.poc.route.model.RouteCandidateResponse;
import com.example.poc.route.model.RouteOption;
import com.example.poc.route.model.RouteSearchRequest;
import com.example.poc.route.model.RouteSearchResponse;
import com.example.poc.route.model.RouteSegmentResponse;
import com.example.poc.route.model.RouteSummaryResponse;
import com.example.poc.route.model.RoutingPolicy;
import com.example.poc.transit.model.DisabilityType;
import com.example.poc.transit.model.TransitCandidateResponse;
import com.example.poc.transit.model.TransitGeometryResponse;
import com.example.poc.transit.model.TransitMixedRouteRequest;
import com.example.poc.transit.service.TransitMixedRouteFacade;
import com.example.poc.transit.service.TransitWalkLegRouter;
import com.example.poc.transit.service.TransitWaypoint;

@Service
public class RouteSearchFacade {

	private static final double TRANSIT_THRESHOLD_METER = 1_000d;

	private final TransitWalkLegRouter transitWalkLegRouter;
	private final TransitMixedRouteFacade transitMixedRouteFacade;

	public RouteSearchFacade(
			TransitWalkLegRouter transitWalkLegRouter,
			TransitMixedRouteFacade transitMixedRouteFacade) {
		this.transitWalkLegRouter = transitWalkLegRouter;
		this.transitMixedRouteFacade = transitMixedRouteFacade;
	}

	public RouteSearchResponse search(RouteSearchRequest request) {
		TransitWaypoint start = new TransitWaypoint(request.startPoint().lat(), request.startPoint().lng());
		TransitWaypoint end = new TransitWaypoint(request.endPoint().lat(), request.endPoint().lng());

		TransitWalkLegRouter.WalkRoutingResult safeResult = transitWalkLegRouter.route(
				start,
				end,
				request.disabilityType(),
				RoutingPolicy.SAFE_WALK);
		RouteCandidateResponse safeRoute = buildWalkRoute(
				request.disabilityType(),
				RouteOption.SAFE,
				RoutingPolicy.SAFE_WALK,
				"Safe Walk",
				"LOW",
				"Conservative route that keeps stricter accessibility constraints.",
				safeResult,
				true);

		TransitWalkLegRouter.WalkRoutingResult fastResult = transitWalkLegRouter.route(
				start,
				end,
				request.disabilityType(),
				RoutingPolicy.FAST_WALK);
		RouteCandidateResponse shortestRoute = buildWalkRoute(
				request.disabilityType(),
				RouteOption.SHORTEST,
				RoutingPolicy.FAST_WALK,
				"Fast Walk",
				"MEDIUM",
				"Time-prioritized route that still stays within the user policy.",
				fastResult,
				false);

		List<RouteOption> availableRouteOptions = new ArrayList<>(List.of(RouteOption.SAFE, RouteOption.SHORTEST));
		List<RouteCandidateResponse> routes = new ArrayList<>(List.of(safeRoute, shortestRoute));

		double primaryDistance = safeRoute.distanceMeter();
		if (primaryDistance > TRANSIT_THRESHOLD_METER) {
			availableRouteOptions.add(RouteOption.TRANSIT_MIXED);
			TransitMixedRouteRequest transitRequest = new TransitMixedRouteRequest(
					request.startPoint(),
					request.endPoint(),
					request.disabilityType(),
					null,
					3);
			List<TransitCandidateResponse> candidates = transitMixedRouteFacade.searchCandidates(transitRequest).candidates();
			TransitCandidateResponse bestCandidate = candidates.isEmpty() ? null : candidates.getFirst();
			routes.add(buildTransitOverview(request.disabilityType(), primaryDistance, safeRoute, bestCandidate));
		}

		return new RouteSearchResponse(
				"route-search-" + UUID.randomUUID(),
				request.disabilityType(),
				new DistancePolicyResponse(TRANSIT_THRESHOLD_METER, round1(primaryDistance)),
				availableRouteOptions,
				routes);
	}

	private RouteCandidateResponse buildWalkRoute(
			DisabilityType disabilityType,
			RouteOption routeOption,
			RoutingPolicy routingPolicy,
			String title,
			String riskLevel,
			String recommendedReason,
			TransitWalkLegRouter.WalkRoutingResult walkResult,
			boolean safe) {
		return new RouteCandidateResponse(
				"route-" + routeOption.name().toLowerCase() + "-" + UUID.randomUUID(),
				routeOption,
				routingPolicy,
				title,
				disabilityType,
				walkResult.appliedProfile(),
				"WALK",
				walkResult.graphHopperBacked(),
				walkResult.fallbackUsed(),
				walkResult.fallbackReason(),
				round1(walkResult.distanceMeter()),
				walkResult.durationMinute(),
				riskLevel,
				new TransitGeometryResponse("LineString", walkResult.coordinates()),
				new RouteSummaryResponse(recommendedReason, null, null),
				null,
				buildWalkSegments(walkResult.distanceMeter(), disabilityType, safe));
	}

	private RouteCandidateResponse buildTransitOverview(
			DisabilityType disabilityType,
			double primaryDistance,
			RouteCandidateResponse safeRoute,
			TransitCandidateResponse bestCandidate) {
		return new RouteCandidateResponse(
				"route-transit-" + UUID.randomUUID(),
				RouteOption.TRANSIT_MIXED,
				RoutingPolicy.ACCESSIBLE_TRANSIT,
				"Transit Mixed",
				disabilityType,
				disabilityType == DisabilityType.VISUAL ? "visual_safe" : "wheelchair_safe",
				"TRANSIT_MIXED_OVERVIEW",
				bestCandidate == null || bestCandidate.walkLegGraphHopperBacked(),
				bestCandidate != null && bestCandidate.fallbackWalkLegCount() > 0,
				bestCandidate == null ? "transit candidates are not available yet" : bestCandidate.fallbackReasonSummary(),
				bestCandidate == null ? primaryDistance : bestCandidate.totalWalkDistanceMeter(),
				bestCandidate == null ? safeRoute.estimatedTimeMinute() : bestCandidate.totalDurationMinute(),
				bestCandidate == null ? "LOW" : bestCandidate.verificationStatus().name(),
				bestCandidate == null ? null : bestCandidate.geometry(),
				new RouteSummaryResponse(
						bestCandidate == null ? "Transit candidates can be requested for this OD." : bestCandidate.summary().primaryTransitLabel(),
						bestCandidate == null ? null : bestCandidate.summary().busArrivalDisplay(),
						bestCandidate == null ? null : bestCandidate.summary().subwayArrivalDisplay()),
				bestCandidate == null ? null : "Walk legs are recalculated with the safe walking profile for the selected user type.",
				List.of());
	}

	private List<RouteSegmentResponse> buildWalkSegments(double distanceMeter, DisabilityType disabilityType, boolean safe) {
		double firstDistance = round1(distanceMeter * 0.32d);
		double secondDistance = round1(distanceMeter * 0.28d);
		double thirdDistance = round1(Math.max(0d, distanceMeter - firstDistance - secondDistance));
		boolean wheelchair = disabilityType == DisabilityType.WHEELCHAIR;
		return List.of(
				new RouteSegmentResponse(
						1,
						"Departure access segment",
						firstDistance,
						wheelchair ? 2.0 : 1.2,
						true,
						true,
						!wheelchair,
						!wheelchair,
						false,
						false,
						"Leave the origin and enter the main pedestrian corridor."),
				new RouteSegmentResponse(
						2,
						safe ? "Protected accessibility segment" : "Time-priority segment",
						secondDistance,
						wheelchair ? 3.0 : 2.1,
						true,
						true,
						!wheelchair && safe,
						!wheelchair && safe,
						false,
						false,
						safe
								? "Follow the corridor with stronger accessibility facilities."
								: "Follow the shorter connector toward the destination."),
				new RouteSegmentResponse(
						3,
						"Destination access segment",
						thirdDistance,
						wheelchair ? 1.8 : 1.0,
						false,
						false,
						false,
						false,
						false,
						false,
						"Proceed straight to the destination entrance."));
	}

	private double round1(double value) {
		return Math.round(value * 10d) / 10d;
	}
}
