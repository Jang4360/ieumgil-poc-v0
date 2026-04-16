package com.example.poc.graphhopper.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poc.graphhopper-import")
public class GraphHopperImportProperties {

	private boolean enabled;
	private Path sourcePbf;
	private Path artifactDirectory;
	private String graphDirectoryName = "graph-cache";
	private String buildIdentifier = "local-manual";
	private String codeRevision = "unknown";
	private String jdbcUrl;
	private String username;
	private String password;
	private boolean deleteExistingArtifact = true;
	private boolean failOnDuplicateStableKey = true;
	private boolean failOnUnmatchedSegment = true;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Path getSourcePbf() {
		return sourcePbf;
	}

	public void setSourcePbf(Path sourcePbf) {
		this.sourcePbf = sourcePbf;
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

	public String getBuildIdentifier() {
		return buildIdentifier;
	}

	public void setBuildIdentifier(String buildIdentifier) {
		this.buildIdentifier = buildIdentifier;
	}

	public String getCodeRevision() {
		return codeRevision;
	}

	public void setCodeRevision(String codeRevision) {
		this.codeRevision = codeRevision;
	}

	public String getJdbcUrl() {
		return jdbcUrl;
	}

	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isDeleteExistingArtifact() {
		return deleteExistingArtifact;
	}

	public void setDeleteExistingArtifact(boolean deleteExistingArtifact) {
		this.deleteExistingArtifact = deleteExistingArtifact;
	}

	public boolean isFailOnDuplicateStableKey() {
		return failOnDuplicateStableKey;
	}

	public void setFailOnDuplicateStableKey(boolean failOnDuplicateStableKey) {
		this.failOnDuplicateStableKey = failOnDuplicateStableKey;
	}

	public boolean isFailOnUnmatchedSegment() {
		return failOnUnmatchedSegment;
	}

	public void setFailOnUnmatchedSegment(boolean failOnUnmatchedSegment) {
		this.failOnUnmatchedSegment = failOnUnmatchedSegment;
	}
}
