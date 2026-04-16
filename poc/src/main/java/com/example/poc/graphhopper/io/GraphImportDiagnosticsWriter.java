package com.example.poc.graphhopper.io;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.example.poc.graphhopper.model.GraphImportDiagnostics;
import com.example.poc.graphhopper.model.GraphImportMismatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Component
public class GraphImportDiagnosticsWriter {

	private final ObjectMapper prettyObjectMapper;
	private final ObjectMapper compactObjectMapper;

	public GraphImportDiagnosticsWriter(ObjectMapper objectMapper) {
		this.prettyObjectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
		this.compactObjectMapper = objectMapper.copy();
	}

	public void write(GraphImportDiagnostics diagnostics, Path artifactDirectory) throws IOException {
		Files.createDirectories(artifactDirectory);
		try (Writer summaryWriter = Files.newBufferedWriter(artifactDirectory.resolve("mismatch-summary.json"))) {
			prettyObjectMapper.writeValue(summaryWriter, diagnostics.reasonCounts());
		}
		try (Writer jsonlWriter = Files.newBufferedWriter(artifactDirectory.resolve("mismatch-analysis.jsonl"))) {
			for (GraphImportMismatch mismatch : diagnostics.mismatches()) {
				jsonlWriter.write(compactObjectMapper.writeValueAsString(mismatch));
				jsonlWriter.write(System.lineSeparator());
			}
		}
	}
}
