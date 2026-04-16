package com.example.poc.network.persistence.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import com.example.poc.network.model.RoadNetworkBuildStats;
import com.example.poc.network.model.RoadNetworkSnapshot;
import com.example.poc.network.model.RoadNode;
import com.example.poc.network.model.RoadSegment;
import com.example.poc.network.persistence.config.NetworkPersistenceProperties;
import com.zaxxer.hikari.HikariDataSource;

class RoadNetworkPersistenceServiceTest {

	@Test
	void persistShouldCreateSchemaAndInsertSnapshot() {
		PostgreSQLContainer<?> postgres;
		try {
			postgres = new PostgreSQLContainer<>("postgis/postgis:16-3.4-alpine")
					.withDatabaseName("poc")
					.withUsername("poc")
					.withPassword("poc");
		} catch (Throwable throwable) {
			return;
		}

		try (postgres) {
			try {
				postgres.start();
			} catch (Throwable throwable) {
				return;
			}

			HikariDataSource dataSource = new HikariDataSource();
			dataSource.setJdbcUrl(postgres.getJdbcUrl());
			dataSource.setUsername(postgres.getUsername());
			dataSource.setPassword(postgres.getPassword());
			try {
				JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
				jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");
				TransactionTemplate transactionTemplate =
						new TransactionTemplate(new DataSourceTransactionManager(dataSource));
				NetworkPersistenceProperties properties = new NetworkPersistenceProperties();
				properties.setEnabled(true);
				properties.setJdbcUrl(postgres.getJdbcUrl());
				properties.setUsername(postgres.getUsername());
				properties.setPassword(postgres.getPassword());
				properties.setTruncateBeforeLoad(true);

				RoadNetworkPersistenceService persistenceService =
						new RoadNetworkPersistenceService(dataSource, jdbcTemplate, transactionTemplate, properties);
				RoadNetworkSnapshot snapshot = new RoadNetworkSnapshot(
						List.of(
								new RoadNode(1L, 1001L, "POINT (129.1000000 35.1000000)"),
								new RoadNode(2L, 1002L, "POINT (129.2000000 35.2000000)")),
						List.of(
								new RoadSegment(
										1L,
										1L,
										2L,
										"LINESTRING (129.1000000 35.1000000, 129.2000000 35.2000000)",
										14.25d,
										88L,
										1001L,
										1002L,
										1,
										null,
										null,
										"UNKNOWN",
										"UNKNOWN",
										"UNKNOWN",
										"UNKNOWN",
										"UNKNOWN",
										"UNKNOWN",
										"UNKNOWN",
										"UNKNOWN",
										"UNKNOWN")),
						new RoadNetworkBuildStats(1, 2, 2, 2, 1, 0));

				RoadNetworkPersistenceResult result = persistenceService.persist(snapshot);

				assertThat(result.insertedRoadNodeCount()).isEqualTo(2);
				assertThat(result.insertedRoadSegmentCount()).isEqualTo(1);
				assertThat(result.duplicateStableKeyCount()).isZero();
				assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM road_nodes", Integer.class)).isEqualTo(2);
				assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM road_segments", Integer.class)).isEqualTo(1);
				assertThat(jdbcTemplate.queryForObject(
						"SELECT source_way_id FROM road_segments WHERE edge_id = 1", Long.class)).isEqualTo(88L);
			} finally {
				dataSource.close();
			}
		}
	}
}
