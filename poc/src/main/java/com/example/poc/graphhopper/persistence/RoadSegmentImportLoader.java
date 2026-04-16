package com.example.poc.graphhopper.persistence;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.poc.graphhopper.config.GraphHopperImportProperties;
import com.example.poc.graphhopper.model.RoadSegmentImportLookup;
import com.example.poc.network.model.RoadSegment;

@Service
@ConditionalOnProperty(prefix = "poc.graphhopper-import", name = "enabled", havingValue = "true")
public class RoadSegmentImportLoader {

	private final JdbcTemplate jdbcTemplate;
	private final GraphHopperImportProperties properties;

	public RoadSegmentImportLoader(
			@Qualifier("graphhopperImportJdbcTemplate") JdbcTemplate jdbcTemplate,
			GraphHopperImportProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.properties = properties;
	}

	public RoadSegmentImportLookup load() {
		List<RoadSegment> segments = jdbcTemplate.query("""
				SELECT
				    edge_id,
				    from_node_id,
				    to_node_id,
				    ST_AsText(geom) AS geom,
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
				FROM road_segments
				ORDER BY source_way_id, source_osm_from_node_id, source_osm_to_node_id, segment_ordinal
				""", (rs, rowNum) -> new RoadSegment(
					rs.getLong("edge_id"),
					rs.getLong("from_node_id"),
					rs.getLong("to_node_id"),
					rs.getString("geom"),
					rs.getDouble("length_meter"),
					rs.getLong("source_way_id"),
					rs.getLong("source_osm_from_node_id"),
					rs.getLong("source_osm_to_node_id"),
					rs.getInt("segment_ordinal"),
					toDouble(rs.getBigDecimal("avg_slope_percent")),
					toDouble(rs.getBigDecimal("width_meter")),
					rs.getString("walk_access"),
					rs.getString("braille_block_state"),
					rs.getString("audio_signal_state"),
					rs.getString("curb_ramp_state"),
					rs.getString("width_state"),
					rs.getString("surface_state"),
					rs.getString("stairs_state"),
					rs.getString("elevator_state"),
					rs.getString("crossing_state")));
		if (segments.isEmpty()) {
			throw new IllegalStateException("road_segments is empty, graphhopper import cannot proceed");
		}
		RoadSegmentImportLookup lookup =
				RoadSegmentImportLookup.fromSegments(segments, properties.isFailOnDuplicateStableKey());
		if (properties.isFailOnDuplicateStableKey() && lookup.duplicateCount() != 0) {
			throw new IllegalStateException("Duplicate stable keys detected in road_segments");
		}
		return lookup;
	}

	private Double toDouble(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}
}
