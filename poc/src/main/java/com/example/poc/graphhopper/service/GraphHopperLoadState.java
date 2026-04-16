package com.example.poc.graphhopper.service;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

@Component
public class GraphHopperLoadState {

	private volatile boolean ready;
	private volatile String loadedAtUtc;
	private volatile String graphDirectory;

	public boolean isReady() {
		return ready;
	}

	public String getLoadedAtUtc() {
		return loadedAtUtc;
	}

	public String getGraphDirectory() {
		return graphDirectory;
	}

	public void markReady(Path graphDirectory, String loadedAtUtc) {
		this.ready = true;
		this.graphDirectory = graphDirectory.toAbsolutePath().normalize().toString();
		this.loadedAtUtc = loadedAtUtc;
	}
}
