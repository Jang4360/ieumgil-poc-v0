package com.example.poc.graphhopper.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import com.example.poc.graphhopper.config.GraphHopperImportProperties;
import com.example.poc.graphhopper.io.GraphArtifactMetadataWriter;
import com.example.poc.graphhopper.io.GraphImportDiagnosticsWriter;
import com.example.poc.graphhopper.model.GraphArtifactMetadata;
import com.example.poc.graphhopper.model.GraphImportDiagnostics;
import com.example.poc.graphhopper.model.GraphImportStats;
import com.example.poc.graphhopper.model.RoadSegmentImportLookup;
import com.example.poc.graphhopper.persistence.RoadSegmentImportLoader;

@Service
@ConditionalOnProperty(prefix = "poc.graphhopper-import", name = "enabled", havingValue = "true")
public class GraphHopperImportService {

	private final GraphHopperImportProperties properties;
	private final RoadSegmentImportLoader roadSegmentImportLoader;
	private final IeumGraphHopperFactory graphHopperFactory;
	private final GraphArtifactMetadataWriter metadataWriter;
	private final GraphImportDiagnosticsWriter diagnosticsWriter;
	private final Clock clock;

	public GraphHopperImportService(
			GraphHopperImportProperties properties,
			RoadSegmentImportLoader roadSegmentImportLoader,
			IeumGraphHopperFactory graphHopperFactory,
			GraphArtifactMetadataWriter metadataWriter,
			GraphImportDiagnosticsWriter diagnosticsWriter,
			Clock clock) {
		this.properties = properties;
		this.roadSegmentImportLoader = roadSegmentImportLoader;
		this.graphHopperFactory = graphHopperFactory;
		this.metadataWriter = metadataWriter;
		this.diagnosticsWriter = diagnosticsWriter;
		this.clock = clock;
	}

	public GraphArtifactMetadata importArtifact() throws IOException {
		Path sourcePbf = requireExisting(properties.getSourcePbf(), "graphhopper source PBF");
		Path artifactDirectory = requireDirectory(properties.getArtifactDirectory(), "graphhopper artifact directory");
		Path graphDirectory = artifactDirectory.resolve(properties.getGraphDirectoryName()).toAbsolutePath().normalize();
		if (properties.isDeleteExistingArtifact() && Files.exists(artifactDirectory)) {
			FileSystemUtils.deleteRecursively(artifactDirectory);
			Files.createDirectories(artifactDirectory);
		}

		RoadSegmentImportLookup lookup = roadSegmentImportLoader.load();
		IeumGraphHopper hopper = graphHopperFactory.createForImport(sourcePbf, graphDirectory, lookup);
		try {
			hopper.importOrLoad();
			GraphImportStats stats = hopper.getLastImportStats();
			GraphImportDiagnostics diagnostics = hopper.getLastImportDiagnostics();
			diagnosticsWriter.write(diagnostics, artifactDirectory);
			if (properties.isFailOnUnmatchedSegment() && stats.unmatchedSegmentCount() != 0) {
				throw new IllegalStateException(
						"Unmatched graphhopper segments detected: " + stats.unmatchedSegmentCount());
			}
			GraphArtifactMetadata metadata = new GraphArtifactMetadata(
					sourcePbf.toAbsolutePath().normalize().toString(),
					artifactDirectory.toAbsolutePath().normalize().toString(),
					graphDirectory.toString(),
					Instant.now(clock).toString(),
					properties.getBuildIdentifier(),
					properties.getCodeRevision(),
					properties.getJdbcUrl(),
					lookup.segmentCount(),
					lookup.wayCount(),
					lookup.duplicateCount(),
					stats.importedWayCount(),
					stats.matchedSegmentCount(),
					stats.unmatchedSegmentCount(),
					stats.importedEdgeCount(),
					hopper.getBaseGraph().getNodes(),
					hopper.getBaseGraph().getEdges(),
					graphHopperFactory.profileNames());
			metadataWriter.write(metadata, artifactDirectory);
			return metadata;
		} finally {
			hopper.close();
		}
	}

	private Path requireExisting(Path path, String label) {
		if (path == null) {
			throw new IllegalArgumentException(label + " is required");
		}
		Path normalized = path.toAbsolutePath().normalize();
		if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
			throw new IllegalArgumentException(label + " does not exist: " + normalized);
		}
		return normalized;
	}

	private Path requireDirectory(Path path, String label) throws IOException {
		if (path == null) {
			throw new IllegalArgumentException(label + " is required");
		}
		Path normalized = path.toAbsolutePath().normalize();
		Files.createDirectories(normalized);
		if (!Files.isDirectory(normalized)) {
			throw new IllegalArgumentException(label + " is not a directory: " + normalized);
		}
		return normalized;
	}
}
