package com.example.poc.graphhopper.web;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.poc.graphhopper.service.GraphHopperLoadState;

@RestController
@ConditionalOnProperty(prefix = "poc.graphhopper-load", name = "enabled", havingValue = "true")
public class GraphHopperHealthController {

	private final GraphHopperLoadState loadState;

	public GraphHopperHealthController(GraphHopperLoadState loadState) {
		this.loadState = loadState;
	}

	@GetMapping("/internal/health")
	public Map<String, Object> health() {
		return Map.of(
				"ready", loadState.isReady(),
				"loadedAtUtc", loadState.getLoadedAtUtc(),
				"graphDirectory", loadState.getGraphDirectory());
	}
}
