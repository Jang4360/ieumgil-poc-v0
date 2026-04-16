package com.example.poc.graphhopper.io;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.example.poc.graphhopper.model.GraphArtifactMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Component
public class GraphArtifactMetadataWriter {

	private final ObjectMapper objectMapper;

	public GraphArtifactMetadataWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
	}

	public void write(GraphArtifactMetadata metadata, Path artifactDirectory) throws IOException {
		Files.createDirectories(artifactDirectory);
		try (Writer writer = Files.newBufferedWriter(artifactDirectory.resolve("artifact-metadata.json"))) {
			objectMapper.writeValue(writer, metadata);
		}
	}
}
