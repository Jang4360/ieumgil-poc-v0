package com.example.poc.graphhopper.model;

public record GraphArtifactMetadata(
		String sourcePbfPath,
		String artifactDirectory,
		String graphDirectory,
		String builtAtUtc,
		String buildIdentifier,
		String codeRevision,
		String importJdbcUrl,
		int loadedRoadSegmentCount,
		int loadedWayCount,
		int duplicateStableKeyCount,
		int importedWayCount,
		int matchedSegmentCount,
		int unmatchedSegmentCount,
		int importedEdgeCount,
		int graphNodeCount,
		int graphEdgeCount,
		String profileName) {
}
