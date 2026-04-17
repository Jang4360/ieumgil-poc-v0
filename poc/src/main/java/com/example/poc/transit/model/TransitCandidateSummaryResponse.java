package com.example.poc.transit.model;

public record TransitCandidateSummaryResponse(
		String primaryTransitLabel,
		String busArrivalDisplay,
		String subwayArrivalDisplay) {
}
