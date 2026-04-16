package com.example.poc.network.persistence.service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.poc.network.model.RoadNetworkSnapshot;
import com.example.poc.network.model.RoadNode;
import com.example.poc.network.model.RoadSegment;
import com.example.poc.network.persistence.config.NetworkPersistenceProperties;

@Service
@ConditionalOnProperty(prefix = "poc.network-persistence", name = "enabled", havingValue = "true")
public class RoadNetworkPersistenceService {

	private static final String INSERT_ROAD_NODE_SQL = """
			INSERT INTO road_nodes (vertex_id, osm_node_id, point)
			VALUES (?, ?, ST_GeomFromText(?, 4326))
			""";

	private static final String INSERT_ROAD_SEGMENT_SQL = """
			INSERT INTO road_segments (
			    edge_id,
			    from_node_id,
			    to_node_id,
			    geom,
			    length_meter,
			    source_way_id,
			    source_osm_from_node_id,
			    source_osm_to_node_id,
			    segment_ordinal,
			    avg_slope_percent,
			    width_meter,
			    walk_access,
			    braille_block_state,
			    audio_signal_state,
			    curb_ramp_state,
			    width_state,
			    surface_state,
			    stairs_state,
			    elevator_state,
			    crossing_state
			)
			VALUES (?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private final DataSource dataSource;
	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final NetworkPersistenceProperties properties;

	public RoadNetworkPersistenceService(
			DataSource dataSource,
			JdbcTemplate jdbcTemplate,
			TransactionTemplate transactionTemplate,
			NetworkPersistenceProperties properties) {
		this.dataSource = dataSource;
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.properties = properties;
	}

	public RoadNetworkPersistenceResult persist(RoadNetworkSnapshot snapshot) {
		initializeSchema();
		return transactionTemplate.execute(status -> {
			if (properties.isTruncateBeforeLoad()) {
				truncateTables();
			}
			batchInsertRoadNodes(snapshot.roadNodes());
			batchInsertRoadSegments(snapshot.roadSegments());
			synchronizeSequences();
			validateCounts(snapshot);
			return new RoadNetworkPersistenceResult(
					snapshot.roadNodes().size(),
					snapshot.roadSegments().size(),
					countDuplicateStableKeys());
		});
	}

	private void initializeSchema() {
		ResourceDatabasePopulator populator =
				new ResourceDatabasePopulator(false, false, "UTF-8", new ClassPathResource("sql/network-schema.sql"));
		populator.execute(dataSource);
	}

	private void truncateTables() {
		jdbcTemplate.execute("TRUNCATE TABLE road_segments RESTART IDENTITY CASCADE");
		jdbcTemplate.execute("TRUNCATE TABLE road_nodes RESTART IDENTITY CASCADE");
	}

	private void batchInsertRoadNodes(List<RoadNode> roadNodes) {
		jdbcTemplate.batchUpdate(INSERT_ROAD_NODE_SQL, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int index) throws SQLException {
				RoadNode roadNode = roadNodes.get(index);
				ps.setLong(1, roadNode.vertexId());
				ps.setLong(2, roadNode.osmNodeId());
				ps.setString(3, roadNode.point());
			}

			@Override
			public int getBatchSize() {
				return roadNodes.size();
			}
		});
	}

	private void batchInsertRoadSegments(List<RoadSegment> roadSegments) {
		jdbcTemplate.batchUpdate(INSERT_ROAD_SEGMENT_SQL, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int index) throws SQLException {
				RoadSegment roadSegment = roadSegments.get(index);
				ps.setLong(1, roadSegment.edgeId());
				ps.setLong(2, roadSegment.fromNodeId());
				ps.setLong(3, roadSegment.toNodeId());
				ps.setString(4, roadSegment.geom());
				ps.setBigDecimal(5, numericValue(roadSegment.lengthMeter()));
				ps.setLong(6, roadSegment.sourceWayId());
				ps.setLong(7, roadSegment.sourceOsmFromNodeId());
				ps.setLong(8, roadSegment.sourceOsmToNodeId());
				ps.setInt(9, roadSegment.segmentOrdinal());
				setNullableNumeric(ps, 10, roadSegment.avgSlopePercent());
				setNullableNumeric(ps, 11, roadSegment.widthMeter());
				ps.setString(12, roadSegment.walkAccess());
				ps.setString(13, roadSegment.brailleBlockState());
				ps.setString(14, roadSegment.audioSignalState());
				ps.setString(15, roadSegment.curbRampState());
				ps.setString(16, roadSegment.widthState());
				ps.setString(17, roadSegment.surfaceState());
				ps.setString(18, roadSegment.stairsState());
				ps.setString(19, roadSegment.elevatorState());
				ps.setString(20, roadSegment.crossingState());
			}

			@Override
			public int getBatchSize() {
				return roadSegments.size();
			}
		});
	}

	private void synchronizeSequences() {
		jdbcTemplate.execute("""
				SELECT setval(pg_get_serial_sequence('road_nodes', 'vertex_id'),
				              COALESCE((SELECT MAX(vertex_id) FROM road_nodes), 1), true)
				""");
		jdbcTemplate.execute("""
				SELECT setval(pg_get_serial_sequence('road_segments', 'edge_id'),
				              COALESCE((SELECT MAX(edge_id) FROM road_segments), 1), true)
				""");
	}

	private void validateCounts(RoadNetworkSnapshot snapshot) {
		assertCount("road_nodes", snapshot.roadNodes().size());
		assertCount("road_segments", snapshot.roadSegments().size());
		if (countDuplicateStableKeys() != 0) {
			throw new IllegalStateException("Duplicate stable keys detected after persistence");
		}
	}

	private void assertCount(String tableName, int expectedCount) {
		Integer actualCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
		if (actualCount == null || actualCount != expectedCount) {
			throw new IllegalStateException(
					"Unexpected row count for " + tableName + ": expected=" + expectedCount + ", actual=" + actualCount);
		}
	}

	private int countDuplicateStableKeys() {
		Integer duplicateCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM (
				    SELECT source_way_id, source_osm_from_node_id, source_osm_to_node_id, segment_ordinal
				    FROM road_segments
				    GROUP BY source_way_id, source_osm_from_node_id, source_osm_to_node_id, segment_ordinal
				    HAVING COUNT(*) > 1
				) duplicates
				""", Integer.class);
		return duplicateCount == null ? 0 : duplicateCount;
	}

	private BigDecimal numericValue(double value) {
		return BigDecimal.valueOf(value);
	}

	private void setNullableNumeric(PreparedStatement ps, int parameterIndex, Double value) throws SQLException {
		if (value == null) {
			ps.setNull(parameterIndex, Types.NUMERIC);
			return;
		}
		ps.setBigDecimal(parameterIndex, BigDecimal.valueOf(value));
	}
}
