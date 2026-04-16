package com.example.poc.graphhopper.model;

import java.util.Map;

public record GraphImportStats(
		int importedWayCount,
		int matchedSegmentCount,
		int unmatchedSegmentCount,
		int syntheticBarrierEdgeCount,
		Map<String, Integer> mismatchReasonCounts) {

	public GraphImportStats {
		mismatchReasonCounts = Map.copyOf(mismatchReasonCounts);
	}

	public int importedEdgeCount() {
		return matchedSegmentCount + unmatchedSegmentCount + syntheticBarrierEdgeCount;
	}
}
