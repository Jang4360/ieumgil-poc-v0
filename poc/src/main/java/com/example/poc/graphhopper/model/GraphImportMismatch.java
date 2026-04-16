package com.example.poc.graphhopper.model;

import java.util.List;
import java.util.Map;

public record GraphImportMismatch(
		long wayId,
		double startLat,
		double startLon,
		double endLat,
		double endLon,
		int pointCount,
		String pointSequenceHash,
		int endpointCandidateCount,
		int sameWayCandidateCount,
		List<Map<String, Object>> candidateSegments,
		MismatchReason reason) {

	public GraphImportMismatch {
		candidateSegments = List.copyOf(candidateSegments);
	}
}
