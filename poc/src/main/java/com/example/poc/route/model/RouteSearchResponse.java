package com.example.poc.route.model;

import java.util.List;

import com.example.poc.transit.model.DisabilityType;

public record RouteSearchResponse(
		String searchId,
		DisabilityType userType,
		DistancePolicyResponse distancePolicy,
		List<RouteOption> availableRouteOptions,
		List<RouteCandidateResponse> routes) {
}
