package com.example.poc.network.model;

import java.util.List;
import java.util.Map;

public record EligibleWayDataset(
		List<EligibleOsmWay> ways,
		Map<Long, Integer> nodeUsageCount) {

	public EligibleWayDataset {
		ways = List.copyOf(ways);
		nodeUsageCount = Map.copyOf(nodeUsageCount);
	}
}
