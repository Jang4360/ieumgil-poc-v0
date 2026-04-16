package com.example.poc.network.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.poc.network.model.NetworkBuildMetadata;

@Component
@Order(10)
@ConditionalOnProperty(prefix = "poc.network-build", name = "enabled", havingValue = "true")
public class NetworkBuildRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(NetworkBuildRunner.class);

	private final NetworkBuildService networkBuildService;

	public NetworkBuildRunner(NetworkBuildService networkBuildService) {
		this.networkBuildService = networkBuildService;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		NetworkBuildMetadata metadata = networkBuildService.buildSnapshot();
		log.info(
				"network snapshot build finished: eligibleWays={}, anchors={}, roadNodes={}, roadSegments={}, outputDir={}",
				metadata.eligibleWayCount(),
				metadata.anchorNodeCount(),
				metadata.roadNodeCount(),
				metadata.roadSegmentCount(),
				metadata.outputDirectory());
	}
}
