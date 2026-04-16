package com.example.poc.graphhopper.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.poc.graphhopper.config.GraphHopperLoadProperties;

import jakarta.annotation.PreDestroy;

@Service
@ConditionalOnProperty(prefix = "poc.graphhopper-load", name = "enabled", havingValue = "true")
public class GraphHopperLoadService {

	private final GraphHopperLoadProperties properties;
	private final IeumGraphHopperFactory graphHopperFactory;
	private final GraphHopperLoadState loadState;
	private final Clock clock;
	private IeumGraphHopper hopper;

	public GraphHopperLoadService(
			GraphHopperLoadProperties properties,
			IeumGraphHopperFactory graphHopperFactory,
			GraphHopperLoadState loadState,
			Clock clock) {
		this.properties = properties;
		this.graphHopperFactory = graphHopperFactory;
		this.loadState = loadState;
		this.clock = clock;
	}

	public void loadArtifact() throws IOException {
		Path artifactDirectory = requireDirectory(properties.getArtifactDirectory(), "graphhopper load artifact directory");
		Path graphDirectory = artifactDirectory.resolve(properties.getGraphDirectoryName()).toAbsolutePath().normalize();
		if (!Files.isDirectory(graphDirectory)) {
			throw new IllegalArgumentException("graphhopper graph directory does not exist: " + graphDirectory);
		}
		Path readyMarker = requireReadyMarker(properties.getReadyMarker());
		Files.deleteIfExists(readyMarker);
		hopper = graphHopperFactory.createForLoad(graphDirectory, properties.isAllowWrites());
		if (!hopper.load()) {
			throw new IllegalStateException("Failed to load graphhopper artifact from " + graphDirectory);
		}
		String loadedAtUtc = Instant.now(clock).toString();
		loadState.markReady(graphDirectory, loadedAtUtc);
		Files.writeString(readyMarker, loadedAtUtc + System.lineSeparator());
	}

	@PreDestroy
	public void close() {
		if (hopper != null) {
			hopper.close();
		}
	}

	private Path requireDirectory(Path path, String label) throws IOException {
		if (path == null) {
			throw new IllegalArgumentException(label + " is required");
		}
		Path normalized = path.toAbsolutePath().normalize();
		Files.createDirectories(normalized);
		return normalized;
	}

	private Path requireReadyMarker(Path path) throws IOException {
		if (path == null) {
			throw new IllegalArgumentException("poc.graphhopper-load.ready-marker is required");
		}
		Path normalized = path.toAbsolutePath().normalize();
		Path parent = normalized.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		return normalized;
	}
}
