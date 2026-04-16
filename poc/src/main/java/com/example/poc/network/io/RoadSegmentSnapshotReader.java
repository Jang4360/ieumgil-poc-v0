package com.example.poc.network.io;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.poc.network.model.RoadSegment;
import com.example.poc.network.service.RoadSegmentLookupIndex;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RoadSegmentSnapshotReader {

	private final ObjectMapper objectMapper;

	public RoadSegmentSnapshotReader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<RoadSegment> read(Path snapshotPath) throws IOException {
		try (Reader reader = Files.newBufferedReader(snapshotPath)) {
			MappingIterator<RoadSegment> iterator =
					objectMapper.readerFor(RoadSegment.class).readValues(reader);
			List<RoadSegment> segments = new ArrayList<>();
			while (iterator.hasNext()) {
				segments.add(iterator.next());
			}
			return List.copyOf(segments);
		}
	}

	public RoadSegmentLookupIndex loadIndex(Path snapshotPath, boolean failOnDuplicateStableKey) throws IOException {
		return RoadSegmentLookupIndex.fromSegments(read(snapshotPath), failOnDuplicateStableKey);
	}
}
