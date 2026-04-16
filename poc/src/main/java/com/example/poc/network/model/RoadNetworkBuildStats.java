package com.example.poc.network.model;

public record RoadNetworkBuildStats(
		int eligibleWayCount,
		int referencedNodeCount,
		int anchorNodeCount,
		int roadNodeCount,
		int roadSegmentCount,
		int duplicateStableKeyCount) {
}
