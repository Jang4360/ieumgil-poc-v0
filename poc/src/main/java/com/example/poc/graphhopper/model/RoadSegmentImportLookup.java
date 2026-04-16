package com.example.poc.graphhopper.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.example.poc.network.model.RoadSegment;
import com.example.poc.network.model.RoadSegmentLookupKey;
import com.example.poc.network.service.RoadSegmentLookupIndex;

public final class RoadSegmentImportLookup {

	private static final RoadSegmentImportLookup EMPTY =
			new RoadSegmentImportLookup(RoadSegmentLookupIndex.fromSegments(Set.of(), false), Set.of(), Map.of());

	private final RoadSegmentLookupIndex index;
	private final Set<Long> wayIds;
	private final Map<Long, List<RoadSegment>> segmentsByWayId;

	private RoadSegmentImportLookup(
			RoadSegmentLookupIndex index,
			Set<Long> wayIds,
			Map<Long, List<RoadSegment>> segmentsByWayId) {
		this.index = index;
		this.wayIds = Set.copyOf(wayIds);
		this.segmentsByWayId = Map.copyOf(segmentsByWayId);
	}

	public static RoadSegmentImportLookup empty() {
		return EMPTY;
	}

	public static RoadSegmentImportLookup fromSegments(
			Collection<RoadSegment> segments,
			boolean failOnDuplicateStableKey) {
		RoadSegmentLookupIndex index = RoadSegmentLookupIndex.fromSegments(segments, failOnDuplicateStableKey);
		Set<Long> wayIds = new LinkedHashSet<>();
		Map<Long, List<RoadSegment>> segmentsByWayId = new LinkedHashMap<>();
		for (RoadSegment segment : segments) {
			wayIds.add(segment.sourceWayId());
			segmentsByWayId.computeIfAbsent(segment.sourceWayId(), ignored -> new java.util.ArrayList<>())
					.add(segment);
		}
		return new RoadSegmentImportLookup(
				index,
				wayIds,
				segmentsByWayId.entrySet().stream()
						.collect(java.util.stream.Collectors.toMap(
								Map.Entry::getKey,
								entry -> List.copyOf(entry.getValue()),
								(left, right) -> left,
								LinkedHashMap::new)));
	}

	public Optional<RoadSegment> find(RoadSegmentLookupKey lookupKey) {
		return index.find(lookupKey);
	}

	public List<RoadSegment> findAll(RoadSegmentLookupKey lookupKey) {
		return index.findAll(lookupKey);
	}

	public boolean containsWayId(long wayId) {
		return wayIds.contains(wayId);
	}

	public List<RoadSegment> segmentsForWay(long wayId) {
		return segmentsByWayId.getOrDefault(wayId, List.of());
	}

	public int segmentCount() {
		return index.size();
	}

	public int wayCount() {
		return wayIds.size();
	}

	public int duplicateCount() {
		return index.duplicateCount();
	}
}
