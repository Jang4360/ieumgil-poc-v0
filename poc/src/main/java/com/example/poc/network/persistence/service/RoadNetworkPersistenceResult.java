package com.example.poc.network.persistence.service;

public record RoadNetworkPersistenceResult(
		int insertedRoadNodeCount,
		int insertedRoadSegmentCount,
		int duplicateStableKeyCount) {
}
