package com.example.poc.network.model;

public record NetworkBuildMetadata(
		String sourcePbfPath,
		String outputDirectory,
		String buildStartedAtUtc,
		String buildIdentifier,
		String codeRevision,
		int eligibleWayCount,
		int referencedNodeCount,
		int anchorNodeCount,
		int roadNodeCount,
		int roadSegmentCount,
		int duplicateStableKeyCount) {
}
