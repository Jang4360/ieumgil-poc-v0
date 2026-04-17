package com.example.poc.route.model;

public record RouteSummaryResponse(
		String recommendedReason,
		String busArrivalDisplay,
		String subwayArrivalDisplay) {
}
