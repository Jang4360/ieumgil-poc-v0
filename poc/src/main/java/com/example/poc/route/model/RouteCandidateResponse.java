package com.example.poc.route.model;

import java.util.List;

import com.example.poc.transit.model.DisabilityType;
import com.example.poc.transit.model.TransitGeometryResponse;

public record RouteCandidateResponse(
		String routeId,
		RouteOption routeOption,
		RoutingPolicy policyName,
		String title,
		DisabilityType disabilityType,
		String appliedProfile,
		String resultType,
		boolean graphHopperBacked,
		boolean fallbackUsed,
		String fallbackReason,
		double distanceMeter,
		int estimatedTimeMinute,
		String riskLevel,
		TransitGeometryResponse geometry,
		RouteSummaryResponse summary,
		String rejectedConstraintSummary,
		List<RouteSegmentResponse> segments) {
}
