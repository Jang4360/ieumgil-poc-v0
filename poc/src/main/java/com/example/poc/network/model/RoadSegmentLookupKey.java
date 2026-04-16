package com.example.poc.network.model;

public record RoadSegmentLookupKey(
		long sourceWayId,
		long sourceOsmFromNodeId,
		long sourceOsmToNodeId) {

	public String asMapKey() {
		return sourceWayId + ":" + sourceOsmFromNodeId + ":" + sourceOsmToNodeId;
	}
}
