package com.example.poc.network.batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.example.poc.network.config.NetworkBuildProperties;
import com.example.poc.network.io.OsmPbfEligibleWayReader;
import com.example.poc.network.io.RoadNetworkSnapshotWriter;
import com.example.poc.network.model.EligibleWayDataset;
import com.example.poc.network.model.NetworkBuildMetadata;
import com.example.poc.network.model.RoadNetworkSnapshot;
import com.example.poc.network.persistence.service.RoadNetworkPersistenceResult;
import com.example.poc.network.persistence.service.RoadNetworkPersistenceService;
import com.example.poc.network.service.RoadNetworkBuilder;

@Service
public class NetworkBuildService {

	private final NetworkBuildProperties properties;
	private final OsmPbfEligibleWayReader eligibleWayReader;
	private final RoadNetworkBuilder roadNetworkBuilder;
	private final RoadNetworkSnapshotWriter snapshotWriter;
	private final RoadNetworkPersistenceService persistenceService;
	private final Clock clock;

	public NetworkBuildService(
			NetworkBuildProperties properties,
			OsmPbfEligibleWayReader eligibleWayReader,
			RoadNetworkBuilder roadNetworkBuilder,
			RoadNetworkSnapshotWriter snapshotWriter,
			org.springframework.beans.factory.ObjectProvider<RoadNetworkPersistenceService> persistenceServiceProvider,
			Clock clock) {
		this.properties = properties;
		this.eligibleWayReader = eligibleWayReader;
		this.roadNetworkBuilder = roadNetworkBuilder;
		this.snapshotWriter = snapshotWriter;
		this.persistenceService = persistenceServiceProvider.getIfAvailable();
		this.clock = clock;
	}

	public NetworkBuildMetadata buildSnapshot() throws IOException {
		Path sourcePbf = requireExisting(properties.getSourcePbf(), "source PBF");
		Path outputDirectory = requireDirectory(properties.getOutputDirectory(), "output directory");
		EligibleWayDataset dataset = eligibleWayReader.read(sourcePbf);
		RoadNetworkSnapshot snapshot =
				roadNetworkBuilder.build(dataset, properties.isFailOnDuplicateStableKey());
		NetworkBuildMetadata metadata = new NetworkBuildMetadata(
				sourcePbf.toAbsolutePath().normalize().toString(),
				outputDirectory.toAbsolutePath().normalize().toString(),
				Instant.now(clock).toString(),
				properties.getBuildIdentifier(),
				properties.getCodeRevision(),
				snapshot.stats().eligibleWayCount(),
				snapshot.stats().referencedNodeCount(),
				snapshot.stats().anchorNodeCount(),
				snapshot.stats().roadNodeCount(),
					snapshot.stats().roadSegmentCount(),
					snapshot.stats().duplicateStableKeyCount());
		snapshotWriter.write(snapshot, metadata, outputDirectory);
		if (persistenceService != null) {
			RoadNetworkPersistenceResult persistenceResult = persistenceService.persist(snapshot);
			if (persistenceResult.duplicateStableKeyCount() != 0) {
				throw new IllegalStateException("Duplicate stable keys remain after DB persistence");
			}
		}
		return metadata;
	}

	private Path requireExisting(Path path, String label) throws IOException {
		if (path == null) {
			throw new IllegalArgumentException(label + " is required");
		}
		Path normalized = path.toAbsolutePath().normalize();
		if (!Files.exists(normalized)) {
			throw new IllegalArgumentException(label + " does not exist: " + normalized);
		}
		if (!Files.isRegularFile(normalized)) {
			throw new IllegalArgumentException(label + " is not a file: " + normalized);
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
