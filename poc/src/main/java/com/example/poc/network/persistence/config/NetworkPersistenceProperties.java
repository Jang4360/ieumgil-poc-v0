package com.example.poc.network.persistence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poc.network-persistence")
public class NetworkPersistenceProperties {

	private boolean enabled;
	private String jdbcUrl;
	private String username;
	private String password;
	private boolean truncateBeforeLoad = true;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
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

	public boolean isTruncateBeforeLoad() {
		return truncateBeforeLoad;
	}

	public void setTruncateBeforeLoad(boolean truncateBeforeLoad) {
		this.truncateBeforeLoad = truncateBeforeLoad;
	}
}
