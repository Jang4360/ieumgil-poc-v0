package com.example.poc.network.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.poc.network.model.RoadSegment;
import com.example.poc.network.model.RoadSegmentLookupKey;
import com.example.poc.network.model.RoadSegmentStableKey;

public final class RoadSegmentLookupIndex {

	private final Map<RoadSegmentStableKey, RoadSegment> segmentByStableKey;
	private final Map<RoadSegmentLookupKey, List<RoadSegment>> segmentsByLookupKey;
	private final int duplicateCount;

	private RoadSegmentLookupIndex(
			Map<RoadSegmentStableKey, RoadSegment> segmentByStableKey,
			Map<RoadSegmentLookupKey, List<RoadSegment>> segmentsByLookupKey,
			int duplicateCount) {
		this.segmentByStableKey = Map.copyOf(segmentByStableKey);
		this.segmentsByLookupKey = Map.copyOf(segmentsByLookupKey);
		this.duplicateCount = duplicateCount;
	}

	public static RoadSegmentLookupIndex fromSegments(
			Collection<RoadSegment> segments,
			boolean failOnDuplicateStableKey) {
		Map<RoadSegmentStableKey, RoadSegment> stableKeyIndex = new LinkedHashMap<>();
		Map<RoadSegmentLookupKey, List<RoadSegment>> lookupKeyIndex = new LinkedHashMap<>();
		int duplicates = 0;
		for (RoadSegment segment : segments) {
			RoadSegment previous = stableKeyIndex.putIfAbsent(segment.stableKey(), segment);
			if (previous != null) {
				duplicates++;
				if (failOnDuplicateStableKey) {
					throw new IllegalStateException(
							"Duplicate stable key for way/from/to/ordinal: " + segment.stableKey().asMapKey());
				}
			}
			lookupKeyIndex.computeIfAbsent(segment.lookupKey(), ignored -> new java.util.ArrayList<>()).add(segment);
		}
		return new RoadSegmentLookupIndex(
				stableKeyIndex,
				lookupKeyIndex.entrySet().stream()
						.collect(java.util.stream.Collectors.toMap(
								Map.Entry::getKey,
								entry -> List.copyOf(entry.getValue()),
								(left, right) -> left,
								LinkedHashMap::new)),
				duplicates);
	}

	public Optional<RoadSegment> find(RoadSegmentLookupKey lookupKey) {
		List<RoadSegment> segments = segmentsByLookupKey.get(lookupKey);
		if (segments == null || segments.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(segments.getFirst());
	}

	public List<RoadSegment> findAll(RoadSegmentLookupKey lookupKey) {
		return segmentsByLookupKey.getOrDefault(lookupKey, List.of());
	}

	public int size() {
		return segmentByStableKey.size();
	}

	public int duplicateCount() {
		return duplicateCount;
	}
}
