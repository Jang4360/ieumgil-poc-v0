package com.example.poc.graphhopper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.poc.graphhopper.model.WayNodeSequence;
import com.example.poc.network.model.RoadSegmentLookupKey;
import com.graphhopper.util.PointList;

class WayNodeSequenceTest {

	@Test
	void resolvesLookupKeyForMatchingContiguousSegment() {
		WayNodeSequence sequence = WayNodeSequence.of(List.of(
				new WayNodeSequence.WayNodeRef(100L, 35.1000, 129.1000),
				new WayNodeSequence.WayNodeRef(101L, 35.1005, 129.1005),
				new WayNodeSequence.WayNodeRef(102L, 35.1010, 129.1010)));
		PointList pointList = new PointList(3, false);
		pointList.add(35.1000, 129.1000);
		pointList.add(35.1005, 129.1005);
		pointList.add(35.1010, 129.1010);

		assertThat(sequence.resolveLookupKey(55L, pointList))
				.contains(new RoadSegmentLookupKey(55L, 100L, 102L));
	}

	@Test
	void returnsEmptyWhenPointListDoesNotMatchSequence() {
		WayNodeSequence sequence = WayNodeSequence.of(List.of(
				new WayNodeSequence.WayNodeRef(100L, 35.1000, 129.1000),
				new WayNodeSequence.WayNodeRef(101L, 35.1005, 129.1005)));
		PointList pointList = new PointList(2, false);
		pointList.add(35.2000, 129.2000);
		pointList.add(35.2005, 129.2005);

		assertThat(sequence.resolveLookupKey(55L, pointList)).isEmpty();
	}

	@Test
	void fallsBackToEndpointMatchingWhenInteriorGeometryDiffers() {
		WayNodeSequence sequence = WayNodeSequence.of(List.of(
				new WayNodeSequence.WayNodeRef(100L, 35.1000, 129.1000),
				new WayNodeSequence.WayNodeRef(101L, 35.1005, 129.1005),
				new WayNodeSequence.WayNodeRef(102L, 35.1010, 129.1010),
				new WayNodeSequence.WayNodeRef(103L, 35.1015, 129.1015)));
		PointList pointList = new PointList(2, false);
		pointList.add(35.1000, 129.1000);
		pointList.add(35.1015, 129.1015);

		assertThat(sequence.resolveLookupKey(55L, pointList))
				.contains(new RoadSegmentLookupKey(55L, 100L, 103L));
	}

	@Test
	void resolveShouldExposeAmbiguousEndpointCandidates() {
		WayNodeSequence sequence = WayNodeSequence.of(List.of(
				new WayNodeSequence.WayNodeRef(100L, 35.1000, 129.1000),
				new WayNodeSequence.WayNodeRef(101L, 35.1005, 129.1005),
				new WayNodeSequence.WayNodeRef(102L, 35.1010, 129.1010),
				new WayNodeSequence.WayNodeRef(201L, 35.1005, 129.1005),
				new WayNodeSequence.WayNodeRef(202L, 35.1010, 129.1010)));
		PointList pointList = new PointList(2, false);
		pointList.add(35.1005, 129.1005);
		pointList.add(35.1010, 129.1010);

		WayNodeSequence.LookupResolution resolution = sequence.resolve(55L, pointList);

		assertThat(resolution.resolvedLookupKey()).isEmpty();
		assertThat(resolution.endpointCandidates())
				.isEmpty();
		assertThat(resolution.exactCandidates())
				.containsExactly(
						new RoadSegmentLookupKey(55L, 101L, 102L),
						new RoadSegmentLookupKey(55L, 201L, 202L));
		assertThat(resolution.isAmbiguous()).isTrue();
	}
}
