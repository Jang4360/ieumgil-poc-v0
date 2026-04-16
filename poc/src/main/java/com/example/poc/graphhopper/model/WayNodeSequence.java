package com.example.poc.graphhopper.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.poc.network.model.RoadSegmentLookupKey;
import com.graphhopper.util.PointList;

public final class WayNodeSequence {

	private static final double COORDINATE_TOLERANCE = 1.0e-7;

	private final List<WayNodeRef> nodes;

	public WayNodeSequence(List<WayNodeRef> nodes) {
		this.nodes = List.copyOf(nodes);
	}

	public static WayNodeSequence of(List<WayNodeRef> nodes) {
		return new WayNodeSequence(nodes);
	}

	public static WayNodeSequence empty() {
		return new WayNodeSequence(List.of());
	}

	public boolean isEmpty() {
		return nodes.isEmpty();
	}

	public Optional<RoadSegmentLookupKey> resolveLookupKey(long wayId, PointList pointList) {
		return resolve(wayId, pointList).resolvedLookupKey();
	}

	public LookupResolution resolve(long wayId, PointList pointList) {
		if (nodes.isEmpty() || pointList.isEmpty() || pointList.size() > nodes.size()) {
			return new LookupResolution(List.of(), List.of(), hasRepeatedNodeIds());
		}
		List<LookupMatch> exactMatches = new ArrayList<>();
		for (int start = 0; start <= nodes.size() - pointList.size(); start++) {
			if (!matchesPoint(nodes.get(start), pointList, 0)) {
				continue;
			}
			if (!matchesSubSequence(start, pointList)) {
				continue;
			}
			WayNodeRef from = nodes.get(start);
			WayNodeRef to = nodes.get(start + pointList.size() - 1);
			exactMatches.add(new LookupMatch(
					new RoadSegmentLookupKey(wayId, from.nodeId(), to.nodeId()),
					start,
					start + pointList.size() - 1,
					pointList.size()));
		}
		if (!exactMatches.isEmpty()) {
			return new LookupResolution(List.copyOf(exactMatches), List.of(), hasRepeatedNodeIds());
		}
		return new LookupResolution(List.of(), resolveByEndpoints(wayId, pointList), hasRepeatedNodeIds());
	}

	public List<WayNodeRef> nodes() {
		return new ArrayList<>(nodes);
	}

	private boolean matchesSubSequence(int start, PointList pointList) {
		for (int i = 0; i < pointList.size(); i++) {
			if (!matchesPoint(nodes.get(start + i), pointList, i)) {
				return false;
			}
		}
		return true;
	}

	private boolean matchesPoint(WayNodeRef node, PointList pointList, int pointIndex) {
		return Math.abs(node.lat() - pointList.getLat(pointIndex)) <= COORDINATE_TOLERANCE
				&& Math.abs(node.lon() - pointList.getLon(pointIndex)) <= COORDINATE_TOLERANCE;
	}

	private List<LookupMatch> resolveByEndpoints(long wayId, PointList pointList) {
		List<LookupMatch> bestMatches = new ArrayList<>();
		int bestSpan = Integer.MAX_VALUE;
		for (int start = 0; start < nodes.size() - 1; start++) {
			if (!matchesPoint(nodes.get(start), pointList, 0)) {
				continue;
			}
			for (int end = start + 1; end < nodes.size(); end++) {
				if (!matchesPoint(nodes.get(end), pointList, pointList.size() - 1)) {
					continue;
				}
				int span = end - start;
				if (span < bestSpan) {
					bestSpan = span;
					bestMatches.clear();
				}
				if (span == bestSpan) {
					bestMatches.add(new LookupMatch(
							new RoadSegmentLookupKey(
									wayId,
									nodes.get(start).nodeId(),
									nodes.get(end).nodeId()),
							start,
							end,
							pointList.size()));
				}
			}
		}
		return List.copyOf(bestMatches);
	}

	private boolean hasRepeatedNodeIds() {
		Set<Long> visited = new LinkedHashSet<>();
		for (WayNodeRef node : nodes) {
			if (!visited.add(node.nodeId())) {
				return true;
			}
		}
		return false;
	}

	public record LookupResolution(
			List<LookupMatch> exactMatches,
			List<LookupMatch> endpointMatches,
			boolean hasRepeatedNodeIds) {

		public LookupResolution {
			exactMatches = List.copyOf(exactMatches);
			endpointMatches = List.copyOf(endpointMatches);
		}

		public Optional<RoadSegmentLookupKey> resolvedLookupKey() {
			if (exactMatches.size() == 1) {
				return Optional.of(exactMatches.getFirst().lookupKey());
			}
			if (exactMatches.isEmpty() && endpointMatches.size() == 1) {
				return Optional.of(endpointMatches.getFirst().lookupKey());
			}
			return Optional.empty();
		}

		public int endpointCandidateCount() {
			return endpointMatches.size();
		}

		public boolean usedEndpointFallback() {
			return exactMatches.isEmpty() && endpointMatches.size() == 1;
		}

		public boolean isAmbiguous() {
			return exactMatches.size() > 1 || endpointMatches.size() > 1;
		}

		public List<RoadSegmentLookupKey> exactCandidates() {
			return exactMatches.stream().map(LookupMatch::lookupKey).toList();
		}

		public List<RoadSegmentLookupKey> endpointCandidates() {
			return endpointMatches.stream().map(LookupMatch::lookupKey).toList();
		}
	}

	public record LookupMatch(RoadSegmentLookupKey lookupKey, int startIndex, int endIndex, int pointCount) {
	}

	public record WayNodeRef(long nodeId, double lat, double lon) {
	}
}
