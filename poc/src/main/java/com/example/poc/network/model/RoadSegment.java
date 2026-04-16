package com.example.poc.network.model;

public record RoadSegment(
		long edgeId,
		long fromNodeId,
		long toNodeId,
		String geom,
		double lengthMeter,
		long sourceWayId,
		long sourceOsmFromNodeId,
		long sourceOsmToNodeId,
		int segmentOrdinal,
		Double avgSlopePercent,
		Double widthMeter,
		String walkAccess,
		String brailleBlockState,
		String audioSignalState,
		String curbRampState,
		String widthState,
		String surfaceState,
		String stairsState,
		String elevatorState,
		String crossingState) {

	public RoadSegment {
		walkAccess = defaultState(walkAccess);
		brailleBlockState = defaultState(brailleBlockState);
		audioSignalState = defaultState(audioSignalState);
		curbRampState = defaultState(curbRampState);
		widthState = defaultState(widthState);
		surfaceState = defaultState(surfaceState);
		stairsState = defaultState(stairsState);
		elevatorState = defaultState(elevatorState);
		crossingState = defaultState(crossingState);
	}

	public RoadSegmentLookupKey lookupKey() {
		return new RoadSegmentLookupKey(sourceWayId, sourceOsmFromNodeId, sourceOsmToNodeId);
	}

	public RoadSegmentStableKey stableKey() {
		return new RoadSegmentStableKey(sourceWayId, sourceOsmFromNodeId, sourceOsmToNodeId, segmentOrdinal);
	}

	private static String defaultState(String value) {
		return value == null || value.isBlank() ? "UNKNOWN" : value;
	}
}
