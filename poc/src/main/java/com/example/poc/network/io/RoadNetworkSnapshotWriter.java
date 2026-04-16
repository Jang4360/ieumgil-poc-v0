package com.example.poc.network.io;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.poc.network.model.NetworkBuildMetadata;
import com.example.poc.network.model.RoadNetworkSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Component
public class RoadNetworkSnapshotWriter {

	private final ObjectMapper prettyObjectMapper;
	private final ObjectMapper compactObjectMapper;

	public RoadNetworkSnapshotWriter(ObjectMapper objectMapper) {
		this.prettyObjectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
		this.compactObjectMapper = objectMapper.copy();
	}

	public void write(RoadNetworkSnapshot snapshot, NetworkBuildMetadata metadata, Path outputDirectory)
			throws IOException {
		Files.createDirectories(outputDirectory);
		writeJson(outputDirectory.resolve("build-metadata.json"), metadata);
		writeJsonLines(outputDirectory.resolve("road-nodes.jsonl"), snapshot.roadNodes());
		writeJsonLines(outputDirectory.resolve("road-segments.jsonl"), snapshot.roadSegments());
	}

	private void writeJson(Path path, Object value) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path)) {
			prettyObjectMapper.writeValue(writer, value);
		}
	}

	private void writeJsonLines(Path path, List<?> values) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path)) {
			for (Object value : values) {
				writer.write(compactObjectMapper.writeValueAsString(value));
				writer.write(System.lineSeparator());
			}
		}
	}
}
