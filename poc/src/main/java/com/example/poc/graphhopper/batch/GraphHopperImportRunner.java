package com.example.poc.graphhopper.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.poc.graphhopper.model.GraphArtifactMetadata;
import com.example.poc.graphhopper.service.GraphHopperImportService;

@Component
@Order(20)
@ConditionalOnProperty(prefix = "poc.graphhopper-import", name = "enabled", havingValue = "true")
public class GraphHopperImportRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(GraphHopperImportRunner.class);

	private final GraphHopperImportService graphHopperImportService;

	public GraphHopperImportRunner(GraphHopperImportService graphHopperImportService) {
		this.graphHopperImportService = graphHopperImportService;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		GraphArtifactMetadata metadata = graphHopperImportService.importArtifact();
		log.info(
				"graphhopper artifact import finished: matchedSegments={}, unmatchedSegments={}, graphEdges={}, artifactDir={}",
				metadata.matchedSegmentCount(),
				metadata.unmatchedSegmentCount(),
				metadata.graphEdgeCount(),
				metadata.artifactDirectory());
	}
}
