package com.example.poc.transit.model;

import java.time.OffsetDateTime;
import java.util.List;

public record TransitCandidateResponse(
		String candidateId,
		int rank,
		TransitCombinationType combinationType,
		TransitVerificationStatus verificationStatus,
		int totalDurationMinute,
		OffsetDateTime arrivalEstimatedAt,
		String dataSource,
		boolean walkLegGraphHopperBacked,
		int fallbackWalkLegCount,
		String fallbackReasonSummary,
		double totalWalkDistanceMeter,
		int transferCount,
		TransitCandidateSummaryResponse summary,
		TransitGeometryResponse geometry,
		List<TransitMarkerResponse> markers,
		List<TransitLegResponse> legs) {
}
