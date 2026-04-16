package com.example.poc.graphhopper.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@ConditionalOnProperty(prefix = "poc.graphhopper-import", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GraphHopperImportProperties.class)
public class GraphHopperImportConfiguration {

	@Bean(name = "graphhopperImportDataSource", destroyMethod = "close")
	DataSource graphhopperImportDataSource(GraphHopperImportProperties properties) {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(required(properties.getJdbcUrl(), "poc.graphhopper-import.jdbc-url"));
		dataSource.setUsername(required(properties.getUsername(), "poc.graphhopper-import.username"));
		dataSource.setPassword(required(properties.getPassword(), "poc.graphhopper-import.password"));
		dataSource.setMaximumPoolSize(2);
		dataSource.setMinimumIdle(1);
		dataSource.setPoolName("graphhopper-import");
		return dataSource;
	}

	@Bean(name = "graphhopperImportJdbcTemplate")
	JdbcTemplate graphhopperImportJdbcTemplate(
			@Qualifier("graphhopperImportDataSource") DataSource graphhopperImportDataSource) {
		return new JdbcTemplate(graphhopperImportDataSource);
	}

	private String required(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(propertyName + " is required when graphhopper import is enabled");
		}
		return value;
	}
}
