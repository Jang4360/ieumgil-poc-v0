package com.example.poc.network.model;

import java.util.List;
import java.util.Map;

public record EligibleOsmWay(
		long wayId,
		Map<String, String> tags,
		List<OsmNodePoint> nodes) {

	public EligibleOsmWay {
		tags = Map.copyOf(tags);
		nodes = List.copyOf(nodes);
	}
}
