package com.example.poc.network.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.poc.network.model.RoadSegment;
import com.example.poc.network.model.RoadSegmentLookupKey;
import com.fasterxml.jackson.databind.ObjectMapper;

class RoadSegmentSnapshotReaderTest {

	@Test
	void loadIndexShouldBulkLoadSegmentsFromSnapshot() throws Exception {
		Path tempFile = Files.createTempFile("road-segments", ".jsonl");
		ObjectMapper objectMapper = new ObjectMapper();
		List<RoadSegment> segments = List.of(
				new RoadSegment(
						1L,
						1L,
						2L,
						"LINESTRING (129.0000000 35.0000000, 129.1000000 35.1000000)",
						12d,
						10L,
						1L,
						2L,
						1,
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
		try {
			Files.writeString(tempFile, objectMapper.writeValueAsString(segments.getFirst()) + System.lineSeparator());
			RoadSegmentSnapshotReader reader = new RoadSegmentSnapshotReader(objectMapper);

			assertThat(reader.loadIndex(tempFile, true)
					.find(new RoadSegmentLookupKey(10L, 1L, 2L)))
					.hasValueSatisfying(segment -> assertThat(segment.edgeId()).isEqualTo(1L));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
}
