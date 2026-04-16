package com.example.poc.network.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poc.network-build")
public class NetworkBuildProperties {

	private boolean enabled;
	private Path sourcePbf;
	private Path outputDirectory;
	private String buildIdentifier = "local-manual";
	private String codeRevision = "unknown";
	private boolean failOnDuplicateStableKey = true;

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

	public Path getOutputDirectory() {
		return outputDirectory;
	}

	public void setOutputDirectory(Path outputDirectory) {
		this.outputDirectory = outputDirectory;
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

	public boolean isFailOnDuplicateStableKey() {
		return failOnDuplicateStableKey;
	}

	public void setFailOnDuplicateStableKey(boolean failOnDuplicateStableKey) {
		this.failOnDuplicateStableKey = failOnDuplicateStableKey;
	}
}
