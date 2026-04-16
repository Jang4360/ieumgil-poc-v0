package com.example.poc.graphhopper.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poc.graphhopper-load")
public class GraphHopperLoadProperties {

	private boolean enabled;
	private Path artifactDirectory;
	private String graphDirectoryName = "graph-cache";
	private Path readyMarker;
	private boolean allowWrites;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Path getArtifactDirectory() {
		return artifactDirectory;
	}

	public void setArtifactDirectory(Path artifactDirectory) {
		this.artifactDirectory = artifactDirectory;
	}

	public String getGraphDirectoryName() {
		return graphDirectoryName;
	}

	public void setGraphDirectoryName(String graphDirectoryName) {
		this.graphDirectoryName = graphDirectoryName;
	}

	public Path getReadyMarker() {
		return readyMarker;
	}

	public void setReadyMarker(Path readyMarker) {
		this.readyMarker = readyMarker;
	}

	public boolean isAllowWrites() {
		return allowWrites;
	}

	public void setAllowWrites(boolean allowWrites) {
		this.allowWrites = allowWrites;
	}
}
