package com.example.poc.network.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.poc.network.model.EligibleOsmWay;
import com.example.poc.network.model.OsmNodePoint;

@Component
public class AnchorNodeResolver {

	public Set<Long> resolve(List<EligibleOsmWay> ways, Map<Long, Integer> nodeUsageCount) {
		Set<Long> anchorNodeIds = new LinkedHashSet<>();
		for (EligibleOsmWay way : ways) {
			List<OsmNodePoint> nodes = way.nodes();
			if (nodes.isEmpty()) {
				continue;
			}
			anchorNodeIds.add(nodes.getFirst().nodeId());
			anchorNodeIds.add(nodes.getLast().nodeId());
			for (OsmNodePoint node : nodes) {
				if (nodeUsageCount.getOrDefault(node.nodeId(), 0) > 1 || hasAnchorTag(node)) {
					anchorNodeIds.add(node.nodeId());
				}
			}
		}
		return Set.copyOf(anchorNodeIds);
	}

	private boolean hasAnchorTag(OsmNodePoint node) {
		Map<String, String> tags = node.tags();
		if (tags.containsKey("crossing")) {
			return true;
		}
		if ("crossing".equals(tags.get("highway"))) {
			return true;
		}
		if ("elevator".equals(tags.get("highway"))) {
			return true;
		}
		return "yes".equals(tags.get("elevator"));
	}
}
