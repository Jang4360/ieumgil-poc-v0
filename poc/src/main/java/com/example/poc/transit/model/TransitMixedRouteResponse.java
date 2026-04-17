package com.example.poc.transit.model;

import java.time.OffsetDateTime;
import java.util.List;

import com.example.poc.route.model.RouteOption;
import com.example.poc.route.model.RoutingPolicy;

public record TransitMixedRouteResponse(
		String searchId,
		DisabilityType userType,
		RouteOption routeOption,
		RoutingPolicy policyName,
		String walkProfile,
		String resultType,
		String dataSource,
		OffsetDateTime baseDepartureAt,
		List<TransitCandidateResponse> candidates) {
}
