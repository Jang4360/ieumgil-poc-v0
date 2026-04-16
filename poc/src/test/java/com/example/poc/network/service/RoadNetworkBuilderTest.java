package com.example.poc.network.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.poc.network.model.EligibleOsmWay;
import com.example.poc.network.model.EligibleWayDataset;
import com.example.poc.network.model.OsmNodePoint;
import com.example.poc.network.model.RoadNetworkSnapshot;
import com.example.poc.network.model.RoadSegment;

class RoadNetworkBuilderTest {

	private final RoadNetworkBuilder roadNetworkBuilder = new RoadNetworkBuilder(new AnchorNodeResolver());

	@Test
	void buildShouldSplitWayOnSharedAndCrossingAnchors() {
		EligibleWayDataset dataset = new EligibleWayDataset(
				List.of(
						new EligibleOsmWay(
								100L,
								Map.of("highway", "footway"),
								List.of(
										node(1L, 35.1000, 129.1000),
										node(2L, 35.1004, 129.1004),
										node(3L, 35.1008, 129.1008, Map.of("highway", "crossing")),
										node(4L, 35.1012, 129.1012))),
						new EligibleOsmWay(
								200L,
								Map.of("highway", "footway"),
								List.of(
										node(2L, 35.1004, 129.1004),
										node(5L, 35.1016, 129.1016)))),
				Map.of(1L, 1, 2L, 2, 3L, 1, 4L, 1, 5L, 1));

		RoadNetworkSnapshot snapshot = roadNetworkBuilder.build(dataset, true);

		assertThat(snapshot.roadNodes()).extracting("osmNodeId")
				.containsExactly(1L, 2L, 3L, 4L, 5L);
		assertThat(snapshot.roadNodes()).extracting("vertexId")
				.containsExactly(1L, 2L, 3L, 4L, 5L);
		assertThat(snapshot.roadSegments()).hasSize(4);
		assertThat(snapshot.stats().anchorNodeCount()).isEqualTo(5);
		assertThat(snapshot.stats().duplicateStableKeyCount()).isZero();

		RoadSegment firstSegment = snapshot.roadSegments().getFirst();
		assertThat(firstSegment.edgeId()).isEqualTo(1L);
		assertThat(firstSegment.fromNodeId()).isEqualTo(1L);
		assertThat(firstSegment.toNodeId()).isEqualTo(2L);
		assertThat(firstSegment.sourceWayId()).isEqualTo(100L);
		assertThat(firstSegment.sourceOsmFromNodeId()).isEqualTo(1L);
		assertThat(firstSegment.sourceOsmToNodeId()).isEqualTo(2L);
		assertThat(firstSegment.segmentOrdinal()).isEqualTo(1);
		assertThat(firstSegment.geom()).startsWith("LINESTRING (");
		assertThat(firstSegment.walkAccess()).isEqualTo("UNKNOWN");
		assertThat(firstSegment.crossingState()).isEqualTo("UNKNOWN");
	}

	@Test
	void buildShouldFailWhenDuplicateStableKeysExist() {
		List<RoadSegment> duplicateSegments = List.of(
				new RoadSegment(1L, 1L, 2L, "LINESTRING (0 0, 1 1)", 10d, 10L, 1L, 2L, 1, null, null,
						"UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN"),
				new RoadSegment(2L, 1L, 2L, "LINESTRING (0 0, 1 1)", 10d, 10L, 1L, 2L, 1, null, null,
						"UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN"));

		assertThatThrownBy(() -> RoadSegmentLookupIndex.fromSegments(duplicateSegments, true))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Duplicate stable key");
	}

	@Test
	void buildShouldSplitRepeatedNodeLoopLikeSegmentIntoGraphhopperStyleSegments() {
		EligibleWayDataset dataset = new EligibleWayDataset(
				List.of(
						new EligibleOsmWay(
								300L,
								Map.of("highway", "footway"),
								List.of(
										node(10L, 35.1000, 129.1000),
										node(11L, 35.1005, 129.1005),
										node(12L, 35.1010, 129.1010),
										node(11L, 35.1005, 129.1005),
										node(13L, 35.1015, 129.1015)))),
				Map.of(10L, 1, 11L, 2, 12L, 1, 13L, 1));

		RoadNetworkSnapshot snapshot = roadNetworkBuilder.build(dataset, true);

		assertThat(snapshot.roadNodes()).extracting("osmNodeId")
				.containsExactly(10L, 11L, 12L, 13L);
		assertThat(snapshot.roadSegments())
				.extracting(RoadSegment::sourceWayId, RoadSegment::sourceOsmFromNodeId, RoadSegment::sourceOsmToNodeId, RoadSegment::segmentOrdinal)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(300L, 10L, 11L, 1),
						org.assertj.core.groups.Tuple.tuple(300L, 11L, 12L, 2),
						org.assertj.core.groups.Tuple.tuple(300L, 11L, 13L, 4),
						org.assertj.core.groups.Tuple.tuple(300L, 12L, 11L, 3));
	}

	@Test
	void buildShouldSplitClosedLoopIntoTwoSegmentsWithoutSelfEdge() {
		EligibleWayDataset dataset = new EligibleWayDataset(
				List.of(
						new EligibleOsmWay(
								400L,
								Map.of("highway", "footway"),
								List.of(
										node(20L, 35.1000, 129.1000),
										node(21L, 35.1005, 129.1005),
										node(22L, 35.1010, 129.1010),
										node(20L, 35.1000, 129.1000)))),
				Map.of(20L, 2, 21L, 1, 22L, 1));

		RoadNetworkSnapshot snapshot = roadNetworkBuilder.build(dataset, true);

		assertThat(snapshot.roadSegments())
				.extracting(RoadSegment::sourceWayId, RoadSegment::sourceOsmFromNodeId, RoadSegment::sourceOsmToNodeId, RoadSegment::segmentOrdinal)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(400L, 20L, 22L, 1),
						org.assertj.core.groups.Tuple.tuple(400L, 22L, 20L, 2));
		assertThat(snapshot.roadSegments())
				.noneMatch(segment -> segment.sourceOsmFromNodeId() == segment.sourceOsmToNodeId());
	}

	private static OsmNodePoint node(long id, double lat, double lon) {
		return node(id, lat, lon, Map.of());
	}

	private static OsmNodePoint node(long id, double lat, double lon, Map<String, String> tags) {
		return new OsmNodePoint(id, lat, lon, tags);
	}
}
