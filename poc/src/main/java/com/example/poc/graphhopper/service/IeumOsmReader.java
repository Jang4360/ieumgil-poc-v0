package com.example.poc.graphhopper.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.poc.graphhopper.model.GraphImportDiagnostics;
import com.example.poc.graphhopper.model.GraphImportMismatch;
import com.example.poc.graphhopper.model.GraphImportStats;
import com.example.poc.graphhopper.model.MismatchReason;
import com.example.poc.graphhopper.model.RoadSegmentImportLookup;
import com.example.poc.graphhopper.model.WayNodeSequence;
import com.example.poc.network.model.RoadSegment;
import com.example.poc.network.model.RoadSegmentLookupKey;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.reader.osm.WaySegmentParser;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint3D;

public class IeumOsmReader extends OSMReader {

	private static final String WAY_NODE_SEQUENCE_TAG = "ieum_way_node_sequence";
	private static final Set<String> ANCHOR_HIGHWAY_VALUES = Set.of("crossing", "elevator");

	private final RoadSegmentImportLookup lookup;
	private final Set<Long> importedWayIds = new java.util.LinkedHashSet<>();
	private final List<GraphImportMismatch> mismatches = new ArrayList<>();
	private final EnumMap<MismatchReason, Integer> mismatchReasonCounts = new EnumMap<>(MismatchReason.class);
	private final Map<String, Integer> consumedExactMatchOccurrenceByLookupKey = new java.util.LinkedHashMap<>();
	private int matchedSegmentCount;
	private int unmatchedSegmentCount;
	private int syntheticBarrierEdgeCount;

	public IeumOsmReader(
			BaseGraph baseGraph,
			OSMParsers osmParsers,
			OSMReaderConfig config,
			RoadSegmentImportLookup lookup) {
		super(baseGraph, osmParsers, config);
		this.lookup = lookup;
	}

	@Override
	protected boolean acceptWay(ReaderWay way) {
		boolean accepted = lookup.containsWayId(way.getId()) && super.acceptWay(way);
		if (accepted) {
			importedWayIds.add(way.getId());
		}
		return accepted;
	}

	@Override
	protected boolean isBarrierNode(ReaderNode node) {
		if (node.hasTag("crossing")) {
			return true;
		}
		String highway = node.getTag("highway");
		if (highway != null && ANCHOR_HIGHWAY_VALUES.contains(highway)) {
			return true;
		}
		return "yes".equals(node.getTag("elevator"));
	}

	@Override
	protected void preprocessWay(
			ReaderWay way,
			WaySegmentParser.CoordinateSupplier coordinateSupplier,
			WaySegmentParser.NodeTagSupplier nodeTagSupplier) {
		super.preprocessWay(way, coordinateSupplier, nodeTagSupplier);
		List<WayNodeSequence.WayNodeRef> nodes = new ArrayList<>(way.getNodes().size());
		for (int i = 0; i < way.getNodes().size(); i++) {
			long nodeId = way.getNodes().get(i);
			GHPoint3D coordinate = coordinateSupplier.getCoordinate(nodeId);
			if (coordinate == null) {
				way.setTag(WAY_NODE_SEQUENCE_TAG, WayNodeSequence.empty());
				return;
			}
			nodes.add(new WayNodeSequence.WayNodeRef(nodeId, coordinate.lat, coordinate.lon));
		}
		way.setTag(WAY_NODE_SEQUENCE_TAG, WayNodeSequence.of(nodes));
	}

	@Override
	protected void setArtificialWayTags(
			PointList pointList,
			ReaderWay way,
			double distance,
			List<Map<String, Object>> nodeTags) {
		super.setArtificialWayTags(pointList, way, distance, nodeTags);
		way.setTag(IeumGraphEncodedValues.DB_MATCH, false);
		way.setTag(IeumGraphEncodedValues.SEGMENT_ORDINAL, 0);
		if (way.getTag("gh:barrier_edge", false)) {
			syntheticBarrierEdgeCount++;
			return;
		}

		WayNodeSequence sequence = way.getTag(WAY_NODE_SEQUENCE_TAG, WayNodeSequence.empty());
		if (sequence.isEmpty()) {
			recordMismatch(way.getId(), pointList, List.of(), 0, MismatchReason.NO_WAY_NODE_SEQUENCE);
			return;
		}
		WayNodeSequence.LookupResolution resolution = sequence.resolve(way.getId(), pointList);
		List<RoadSegment> waySegments = lookup.segmentsForWay(way.getId());
		RoadSegment segment = findMatchingSegment(resolution, pointList);
		if (segment == null) {
			recordMismatch(
					way.getId(),
					pointList,
					waySegments,
					resolution.endpointCandidateCount(),
					determineMismatchReason(resolution, waySegments));
			return;
		}
		matchedSegmentCount++;
		way.setTag(IeumGraphEncodedValues.DB_MATCH, true);
		way.setTag(IeumGraphEncodedValues.SEGMENT_ORDINAL, segment.segmentOrdinal());
		way.setTag(IeumGraphEncodedValues.WALK_ACCESS, segment.walkAccess());
		way.setTag(IeumGraphEncodedValues.BRAILLE_BLOCK_STATE, segment.brailleBlockState());
		way.setTag(IeumGraphEncodedValues.AUDIO_SIGNAL_STATE, segment.audioSignalState());
		way.setTag(IeumGraphEncodedValues.CURB_RAMP_STATE, segment.curbRampState());
		way.setTag(IeumGraphEncodedValues.WIDTH_STATE, segment.widthState());
		way.setTag(IeumGraphEncodedValues.SURFACE_STATE, segment.surfaceState());
		way.setTag(IeumGraphEncodedValues.STAIRS_STATE, segment.stairsState());
		way.setTag(IeumGraphEncodedValues.ELEVATOR_STATE, segment.elevatorState());
		way.setTag(IeumGraphEncodedValues.CROSSING_STATE, segment.crossingState());
	}

	public GraphImportStats importStats() {
		return new GraphImportStats(
				importedWayIds.size(),
				matchedSegmentCount,
				unmatchedSegmentCount,
				syntheticBarrierEdgeCount,
				mismatchReasonCounts.entrySet().stream()
						.collect(Collectors.toMap(
								entry -> entry.getKey().name(),
								Map.Entry::getValue,
								(left, right) -> left,
								java.util.LinkedHashMap::new)));
	}

	public GraphImportDiagnostics importDiagnostics() {
		return new GraphImportDiagnostics(mismatches, importStats().mismatchReasonCounts());
	}

	private MismatchReason determineMismatchReason(
			WayNodeSequence.LookupResolution resolution,
			List<RoadSegment> waySegments) {
		if (waySegments.isEmpty()) {
			return MismatchReason.NO_DB_CANDIDATE_FOR_WAY;
		}
		if (resolution.isAmbiguous()) {
			return MismatchReason.AMBIGUOUS_ENDPOINT_CANDIDATE;
		}
		if (resolution.usedEndpointFallback() || hasDuplicateLookupCandidates(resolution)) {
			return MismatchReason.ENDPOINT_MATCH_GEOMETRY_DIFFERENT;
		}
		if (resolution.hasRepeatedNodeIds()) {
			return MismatchReason.WAY_NODE_REPEAT_NOT_ANCHOR;
		}
		return MismatchReason.GRAPHHOPPER_SPLIT_EXTRA;
	}

	private RoadSegment findMatchingSegment(WayNodeSequence.LookupResolution resolution, PointList pointList) {
		if (resolution.exactMatches().size() == 1) {
			return findExactOccurrenceMatch(resolution.exactMatches().getFirst(), pointList);
		}
		List<WayNodeSequence.LookupMatch> candidateMatches = resolution.endpointMatches();
		if (candidateMatches.size() != 1) {
			return null;
		}
		List<RoadSegment> segments = lookup.findAll(candidateMatches.getFirst().lookupKey()).stream()
				.sorted(Comparator.comparingInt(RoadSegment::segmentOrdinal))
				.toList();
		if (segments.isEmpty()) {
			return null;
		}
		if (segments.size() == 1) {
			return segments.getFirst();
		}
		String pointListWkt = formatPointListWkt(pointList);
		List<RoadSegment> exactGeometryMatches = segments.stream()
				.filter(segment -> segment.geom().equals(pointListWkt))
				.toList();
		return exactGeometryMatches.size() == 1 ? exactGeometryMatches.getFirst() : null;
	}

	private RoadSegment findExactOccurrenceMatch(WayNodeSequence.LookupMatch exactMatch, PointList pointList) {
		List<RoadSegment> segments = lookup.findAll(exactMatch.lookupKey()).stream()
				.sorted(Comparator.comparingInt(RoadSegment::segmentOrdinal))
				.toList();
		if (segments.isEmpty()) {
			return null;
		}
		if (segments.size() == 1) {
			return segments.getFirst();
		}
		String occurrenceKey = exactMatch.lookupKey().asMapKey();
		int occurrenceIndex = consumedExactMatchOccurrenceByLookupKey.getOrDefault(occurrenceKey, 0);
		if (occurrenceIndex < segments.size()) {
			consumedExactMatchOccurrenceByLookupKey.put(occurrenceKey, occurrenceIndex + 1);
			return segments.get(occurrenceIndex);
		}
		String pointListWkt = formatPointListWkt(pointList);
		List<RoadSegment> exactGeometryMatches = segments.stream()
				.filter(segment -> segment.geom().equals(pointListWkt))
				.toList();
		if (exactGeometryMatches.size() == 1) {
			return exactGeometryMatches.getFirst();
		}
		return null;
	}

	private boolean hasDuplicateLookupCandidates(WayNodeSequence.LookupResolution resolution) {
		if (!resolution.exactCandidates().isEmpty()) {
			return resolution.exactCandidates().stream().anyMatch(candidate -> lookup.findAll(candidate).size() > 1);
		}
		return resolution.endpointCandidates().stream().anyMatch(candidate -> lookup.findAll(candidate).size() > 1);
	}

	private void recordMismatch(
			long wayId,
			PointList pointList,
			List<RoadSegment> waySegments,
			int endpointCandidateCount,
			MismatchReason reason) {
		unmatchedSegmentCount++;
		mismatchReasonCounts.merge(reason, 1, Integer::sum);
		mismatches.add(new GraphImportMismatch(
				wayId,
				pointList.getLat(0),
				pointList.getLon(0),
				pointList.getLat(pointList.size() - 1),
				pointList.getLon(pointList.size() - 1),
				pointList.size(),
				hashPointSequence(pointList),
				endpointCandidateCount,
				waySegments.size(),
				waySegments.stream()
						.limit(5)
						.map(segment -> Map.<String, Object>of(
								"sourceOsmFromNodeId", segment.sourceOsmFromNodeId(),
								"sourceOsmToNodeId", segment.sourceOsmToNodeId(),
								"segmentOrdinal", segment.segmentOrdinal()))
						.toList(),
				reason));
	}

	private String hashPointSequence(PointList pointList) {
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			for (int i = 0; i < pointList.size(); i++) {
				digest.update(Double.toString(pointList.getLat(i)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				digest.update((byte) '|');
				digest.update(Double.toString(pointList.getLon(i)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				digest.update((byte) ';');
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest is required", exception);
		}
	}

	private String formatPointListWkt(PointList pointList) {
		StringBuilder builder = new StringBuilder("LINESTRING (");
		for (int i = 0; i < pointList.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(String.format(java.util.Locale.ROOT, "%.7f %.7f", pointList.getLon(i), pointList.getLat(i)));
		}
		builder.append(')');
		return builder.toString();
	}
}
