package com.example.poc.network.model;

import java.util.List;

public record RoadNetworkSnapshot(
		List<RoadNode> roadNodes,
		List<RoadSegment> roadSegments,
		RoadNetworkBuildStats stats) {

	public RoadNetworkSnapshot {
		roadNodes = List.copyOf(roadNodes);
		roadSegments = List.copyOf(roadSegments);
	}
}
