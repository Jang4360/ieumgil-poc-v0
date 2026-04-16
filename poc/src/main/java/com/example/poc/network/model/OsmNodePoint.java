package com.example.poc.network.model;

import java.util.Map;

public record OsmNodePoint(
		long nodeId,
		double latitude,
		double longitude,
		Map<String, String> tags) {

	public OsmNodePoint {
		tags = Map.copyOf(tags);
	}
}
