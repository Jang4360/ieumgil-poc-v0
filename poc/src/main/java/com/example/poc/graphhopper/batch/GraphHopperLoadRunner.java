package com.example.poc.graphhopper.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.poc.graphhopper.service.GraphHopperLoadService;

@Component
@Order(30)
@ConditionalOnProperty(prefix = "poc.graphhopper-load", name = "enabled", havingValue = "true")
public class GraphHopperLoadRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(GraphHopperLoadRunner.class);

	private final GraphHopperLoadService graphHopperLoadService;

	public GraphHopperLoadRunner(GraphHopperLoadService graphHopperLoadService) {
		this.graphHopperLoadService = graphHopperLoadService;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		graphHopperLoadService.loadArtifact();
		log.info("graphhopper artifact load finished");
	}
}
