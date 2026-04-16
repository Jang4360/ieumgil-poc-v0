package com.example.poc.network.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.springframework.stereotype.Component;

import com.example.poc.network.model.EligibleOsmWay;
import com.example.poc.network.model.EligibleWayDataset;
import com.example.poc.network.model.OsmNodePoint;
import com.example.poc.network.service.WalkableWayPolicy;

import crosby.binary.osmosis.OsmosisReader;

@Component
public class OsmPbfEligibleWayReader {

	private final WalkableWayPolicy walkableWayPolicy;

	public OsmPbfEligibleWayReader(WalkableWayPolicy walkableWayPolicy) {
		this.walkableWayPolicy = walkableWayPolicy;
	}

	public EligibleWayDataset read(Path pbfPath) throws IOException {
		Map<Long, WaySkeleton> eligibleWayRefs = readEligibleWayRefs(pbfPath);
		Map<Long, OsmNodePoint> referencedNodes = readReferencedNodes(pbfPath, eligibleWayRefs.values());

		List<EligibleOsmWay> ways = eligibleWayRefs.values().stream()
				.map(way -> toEligibleWay(way, referencedNodes))
				.sorted((left, right) -> Long.compare(left.wayId(), right.wayId()))
				.toList();
		Map<Long, Integer> nodeUsageCount = computeUsageCount(eligibleWayRefs.values());

		return new EligibleWayDataset(ways, nodeUsageCount);
	}

	private Map<Long, WaySkeleton> readEligibleWayRefs(Path pbfPath) throws IOException {
		Map<Long, WaySkeleton> eligibleWays = new LinkedHashMap<>();
		streamPbf(pbfPath, new Sink() {
			@Override
			public void process(EntityContainer entityContainer) {
				if (entityContainer.getEntity().getType() != EntityType.Way) {
					return;
				}
				Way way = ((WayContainer) entityContainer).getEntity();
				Map<String, String> tags = toTagMap(way.getTags());
				if (!walkableWayPolicy.isEligible(tags) || way.getWayNodes().size() < 2) {
					return;
				}
				List<Long> nodeIds = new ArrayList<>(way.getWayNodes().size());
				for (WayNode wayNode : way.getWayNodes()) {
					nodeIds.add(wayNode.getNodeId());
				}
				eligibleWays.put(way.getId(), new WaySkeleton(way.getId(), tags, nodeIds));
			}

			@Override
			public void initialize(Map<String, Object> metaData) {
			}

			@Override
			public void complete() {
			}

			@Override
			public void close() {
			}
		});
		return eligibleWays;
	}

	private Map<Long, OsmNodePoint> readReferencedNodes(Path pbfPath, Collection<WaySkeleton> wayRefs) throws IOException {
		java.util.Set<Long> referencedNodeIds = new java.util.LinkedHashSet<>();
		for (WaySkeleton wayRef : wayRefs) {
			referencedNodeIds.addAll(wayRef.nodeIds());
		}
		Map<Long, OsmNodePoint> nodes = new HashMap<>(referencedNodeIds.size());
		streamPbf(pbfPath, new Sink() {
			@Override
			public void process(EntityContainer entityContainer) {
				if (entityContainer.getEntity().getType() != EntityType.Node) {
					return;
				}
				Node node = ((NodeContainer) entityContainer).getEntity();
				if (!referencedNodeIds.contains(node.getId())) {
					return;
				}
				nodes.put(node.getId(), new OsmNodePoint(
						node.getId(),
						node.getLatitude(),
						node.getLongitude(),
						toTagMap(node.getTags())));
			}

			@Override
			public void initialize(Map<String, Object> metaData) {
			}

			@Override
			public void complete() {
			}

			@Override
			public void close() {
			}
		});
		return nodes;
	}

	private EligibleOsmWay toEligibleWay(WaySkeleton skeleton, Map<Long, OsmNodePoint> nodes) {
		List<OsmNodePoint> geometry = new ArrayList<>(skeleton.nodeIds().size());
		for (Long nodeId : skeleton.nodeIds()) {
			OsmNodePoint node = nodes.get(nodeId);
			if (node == null) {
				throw new IllegalStateException(
						"Missing node coordinates for eligible way " + skeleton.wayId() + " at node " + nodeId);
			}
			geometry.add(node);
		}
		return new EligibleOsmWay(skeleton.wayId(), skeleton.tags(), geometry);
	}

	private Map<Long, Integer> computeUsageCount(Collection<WaySkeleton> wayRefs) {
		Map<Long, Integer> usageCount = new HashMap<>();
		for (WaySkeleton wayRef : wayRefs) {
			for (Long nodeId : wayRef.nodeIds()) {
				usageCount.merge(nodeId, 1, Integer::sum);
			}
		}
		return usageCount;
	}

	private Map<String, String> toTagMap(Collection<Tag> tags) {
		Map<String, String> tagMap = new LinkedHashMap<>();
		for (Tag tag : tags) {
			tagMap.put(tag.getKey(), tag.getValue());
		}
		return Map.copyOf(tagMap);
	}

	private void streamPbf(Path pbfPath, Sink sink) throws IOException {
		OsmosisReader sinkSource = new OsmosisReader(pbfPath.toFile());
		sinkSource.setSink(new ForwardingSink(sink));
		sinkSource.run();
	}

	private record WaySkeleton(long wayId, Map<String, String> tags, List<Long> nodeIds) {
	}

	private static final class ForwardingSink implements Sink {

		private final Sink delegate;

		private ForwardingSink(Sink delegate) {
			this.delegate = delegate;
		}

		@Override
		public void process(EntityContainer entityContainer) {
			delegate.process(entityContainer);
		}

		@Override
		public void initialize(Map<String, Object> metaData) {
		}

		@Override
		public void complete() {
		}

		@Override
		public void close() {
		}
	}
}
