package com.example.poc.network.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.poc.network.model.EligibleOsmWay;
import com.example.poc.network.model.EligibleWayDataset;
import com.example.poc.network.model.OsmNodePoint;
import com.example.poc.network.model.RoadNetworkBuildStats;
import com.example.poc.network.model.RoadNetworkSnapshot;
import com.example.poc.network.model.RoadNode;
import com.example.poc.network.model.RoadSegment;
import com.example.poc.network.model.RoadSegmentLookupKey;
import com.example.poc.network.model.RoadSegmentStableKey;
import com.example.poc.network.util.GeoUtils;
import com.example.poc.network.util.LineStringWktFormatter;
import com.example.poc.network.util.PointWktFormatter;

@Component
public class RoadNetworkBuilder {

	private final AnchorNodeResolver anchorNodeResolver;

	public RoadNetworkBuilder(AnchorNodeResolver anchorNodeResolver) {
		this.anchorNodeResolver = anchorNodeResolver;
	}

	public RoadNetworkSnapshot build(EligibleWayDataset dataset, boolean failOnDuplicateStableKey) {
		Set<Long> anchorNodeIds = anchorNodeResolver.resolve(dataset.ways(), dataset.nodeUsageCount());
		Map<Long, OsmNodePoint> anchorPointByOsmNodeId = new LinkedHashMap<>();
		List<SegmentDraft> segmentDrafts = new ArrayList<>();

		for (EligibleOsmWay way : dataset.ways()) {
			segmentDrafts.addAll(splitWayIntoSegments(way, anchorNodeIds, anchorPointByOsmNodeId));
		}

		List<RoadNode> sortedNodes = buildRoadNodes(anchorPointByOsmNodeId);
		Map<Long, Long> vertexIdByOsmNodeId = new LinkedHashMap<>();
		for (RoadNode roadNode : sortedNodes) {
			vertexIdByOsmNodeId.put(roadNode.osmNodeId(), roadNode.vertexId());
		}
		List<RoadSegment> roadSegments = buildRoadSegments(segmentDrafts, vertexIdByOsmNodeId);
		RoadSegmentLookupIndex index = RoadSegmentLookupIndex.fromSegments(roadSegments, failOnDuplicateStableKey);

		return new RoadNetworkSnapshot(
				sortedNodes,
				roadSegments,
				new RoadNetworkBuildStats(
						dataset.ways().size(),
						dataset.nodeUsageCount().size(),
						anchorNodeIds.size(),
						sortedNodes.size(),
						roadSegments.size(),
						index.duplicateCount()));
	}

	private List<RoadNode> buildRoadNodes(Map<Long, OsmNodePoint> anchorPointByOsmNodeId) {
		List<OsmNodePoint> sortedAnchorPoints = anchorPointByOsmNodeId.values().stream()
				.sorted(Comparator.comparingLong(OsmNodePoint::nodeId))
				.toList();
		List<RoadNode> roadNodes = new ArrayList<>(sortedAnchorPoints.size());
		long vertexId = 1L;
		for (OsmNodePoint anchorPoint : sortedAnchorPoints) {
			roadNodes.add(RoadNode.fromPoint(vertexId, anchorPoint, PointWktFormatter.format(anchorPoint)));
			vertexId++;
		}
		return List.copyOf(roadNodes);
	}

	private List<RoadSegment> buildRoadSegments(List<SegmentDraft> segmentDrafts, Map<Long, Long> vertexIdByOsmNodeId) {
		List<SegmentDraft> sortedDrafts = segmentDrafts.stream()
				.sorted(Comparator
						.comparingLong(SegmentDraft::sourceWayId)
						.thenComparingLong(SegmentDraft::sourceOsmFromNodeId)
						.thenComparingLong(SegmentDraft::sourceOsmToNodeId)
						.thenComparingInt(SegmentDraft::segmentOrdinal))
				.toList();
		List<RoadSegment> roadSegments = new ArrayList<>(sortedDrafts.size());
		long edgeId = 1L;
		for (SegmentDraft draft : sortedDrafts) {
			roadSegments.add(new RoadSegment(
					edgeId,
					requiredVertexId(vertexIdByOsmNodeId, draft.sourceOsmFromNodeId()),
					requiredVertexId(vertexIdByOsmNodeId, draft.sourceOsmToNodeId()),
					draft.geom(),
					draft.lengthMeter(),
					draft.sourceWayId(),
					draft.sourceOsmFromNodeId(),
					draft.sourceOsmToNodeId(),
					draft.segmentOrdinal(),
					null,
					null,
					"UNKNOWN",
					"UNKNOWN",
					"UNKNOWN",
					"UNKNOWN",
					"UNKNOWN",
					"UNKNOWN",
					"UNKNOWN",
					"UNKNOWN",
					"UNKNOWN"));
			edgeId++;
		}
		return List.copyOf(roadSegments);
	}

	private long requiredVertexId(Map<Long, Long> vertexIdByOsmNodeId, long osmNodeId) {
		Long vertexId = vertexIdByOsmNodeId.get(osmNodeId);
		if (vertexId == null) {
			throw new IllegalStateException("Missing vertex id for anchor node " + osmNodeId);
		}
		return vertexId;
	}

	private List<SegmentDraft> splitWayIntoSegments(
			EligibleOsmWay way,
			Set<Long> anchorNodeIds,
			Map<Long, OsmNodePoint> anchorPointByOsmNodeId) {
		List<SegmentDraft> segmentDrafts = new ArrayList<>();
		List<OsmNodePoint> segment = new ArrayList<>();
		int nextOrdinal = 1;
		for (OsmNodePoint node : way.nodes()) {
			if (anchorNodeIds.contains(node.nodeId()) && !segment.isEmpty()) {
				segment.add(node);
				nextOrdinal = addLoopAwareSegments(
						way.wayId(),
						segment,
						nextOrdinal,
						anchorPointByOsmNodeId,
						segmentDrafts);
				segment = new ArrayList<>();
				segment.add(node);
			} else {
				segment.add(node);
			}
		}
		if (segment.size() > 1) {
			addLoopAwareSegments(way.wayId(), segment, nextOrdinal, anchorPointByOsmNodeId, segmentDrafts);
		}
		if (segmentDrafts.isEmpty()) {
			throw new IllegalStateException("Way does not contain enough anchor nodes to segment");
		}
		return List.copyOf(segmentDrafts);
	}

	private int addLoopAwareSegments(
			long wayId,
			List<OsmNodePoint> segment,
			int nextOrdinal,
			Map<Long, OsmNodePoint> anchorPointByOsmNodeId,
			List<SegmentDraft> segmentDrafts) {
		if (segment.size() < 2) {
			throw new IllegalStateException("Segment size must be >= 2");
		}
		boolean isLoop = segment.getFirst().nodeId() == segment.getLast().nodeId();
		if (segment.size() == 2 && isLoop) {
			return nextOrdinal;
		}
		if (isLoop) {
			nextOrdinal = addSegmentDraft(
					wayId,
					segment.subList(0, segment.size() - 1),
					nextOrdinal,
					anchorPointByOsmNodeId,
					segmentDrafts);
			return addSegmentDraft(
					wayId,
					segment.subList(segment.size() - 2, segment.size()),
					nextOrdinal,
					anchorPointByOsmNodeId,
					segmentDrafts);
		}
		return addSegmentDraft(wayId, segment, nextOrdinal, anchorPointByOsmNodeId, segmentDrafts);
	}

	private int addSegmentDraft(
			long wayId,
			List<OsmNodePoint> segmentPoints,
			int segmentOrdinal,
			Map<Long, OsmNodePoint> anchorPointByOsmNodeId,
			List<SegmentDraft> segmentDrafts) {
		List<OsmNodePoint> immutablePoints = List.copyOf(segmentPoints);
		OsmNodePoint fromPoint = immutablePoints.getFirst();
		OsmNodePoint toPoint = immutablePoints.getLast();
		segmentDrafts.add(new SegmentDraft(
				wayId,
				fromPoint.nodeId(),
				toPoint.nodeId(),
				segmentOrdinal,
				LineStringWktFormatter.format(immutablePoints),
				GeoUtils.measureLengthMeters(immutablePoints)));
		anchorPointByOsmNodeId.putIfAbsent(fromPoint.nodeId(), fromPoint);
		anchorPointByOsmNodeId.putIfAbsent(toPoint.nodeId(), toPoint);
		return segmentOrdinal + 1;
	}

	private record SegmentDraft(
			long sourceWayId,
			long sourceOsmFromNodeId,
			long sourceOsmToNodeId,
			int segmentOrdinal,
			String geom,
			double lengthMeter) {
	}
}
