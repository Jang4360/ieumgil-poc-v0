package com.example.poc.network.model;

public record RoadSegmentStableKey(
		long sourceWayId,
		long sourceOsmFromNodeId,
		long sourceOsmToNodeId,
		int segmentOrdinal) {

	public String asEdgeId() {
		return "segment:" + sourceWayId + ":" + sourceOsmFromNodeId + ":" + sourceOsmToNodeId + ":" + segmentOrdinal;
	}

	public String asMapKey() {
		return sourceWayId + ":" + sourceOsmFromNodeId + ":" + sourceOsmToNodeId + ":" + segmentOrdinal;
	}

	public RoadSegmentLookupKey toLookupKey() {
		return new RoadSegmentLookupKey(sourceWayId, sourceOsmFromNodeId, sourceOsmToNodeId);
	}
}
