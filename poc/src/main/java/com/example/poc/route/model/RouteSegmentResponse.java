package com.example.poc.route.model;

public record RouteSegmentResponse(
		int sequence,
		String name,
		double distanceMeter,
		Double slopePercent,
		boolean hasCrosswalk,
		boolean hasSignal,
		boolean hasAudioSignal,
		boolean hasBrailleBlock,
		boolean hasStairs,
		boolean hasCurbGap,
		String guidanceMessage) {
}
