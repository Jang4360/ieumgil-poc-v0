package com.example.poc.network.persistence.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@ConditionalOnProperty(prefix = "poc.network-persistence", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(NetworkPersistenceProperties.class)
public class NetworkPersistenceConfiguration {

	@Bean(destroyMethod = "close")
	DataSource networkPersistenceDataSource(NetworkPersistenceProperties properties) {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(required(properties.getJdbcUrl(), "poc.network-persistence.jdbc-url"));
		dataSource.setUsername(required(properties.getUsername(), "poc.network-persistence.username"));
		dataSource.setPassword(required(properties.getPassword(), "poc.network-persistence.password"));
		dataSource.setMaximumPoolSize(4);
		dataSource.setMinimumIdle(1);
		dataSource.setPoolName("network-persistence");
		return dataSource;
	}

	@Bean
	JdbcTemplate networkPersistenceJdbcTemplate(DataSource networkPersistenceDataSource) {
		return new JdbcTemplate(networkPersistenceDataSource);
	}

	@Bean
	DataSourceTransactionManager networkPersistenceTransactionManager(DataSource networkPersistenceDataSource) {
		return new DataSourceTransactionManager(networkPersistenceDataSource);
	}

	@Bean
	TransactionTemplate networkPersistenceTransactionTemplate(
			DataSourceTransactionManager networkPersistenceTransactionManager) {
		return new TransactionTemplate(networkPersistenceTransactionManager);
	}

	private String required(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(propertyName + " is required when persistence is enabled");
		}
		return value;
	}
}
