CREATE TABLE IF NOT EXISTS road_nodes (
    vertex_id BIGSERIAL PRIMARY KEY,
    osm_node_id BIGINT NOT NULL,
    point GEOMETRY(POINT, 4326) NOT NULL
);

CREATE TABLE IF NOT EXISTS road_segments (
    edge_id BIGSERIAL PRIMARY KEY,
    from_node_id BIGINT NOT NULL,
    to_node_id BIGINT NOT NULL,
    geom GEOMETRY(LINESTRING, 4326) NOT NULL,
    length_meter NUMERIC(10, 2) NOT NULL,
    source_way_id BIGINT NOT NULL,
    source_osm_from_node_id BIGINT NOT NULL,
    source_osm_to_node_id BIGINT NOT NULL,
    segment_ordinal INT NOT NULL,
    avg_slope_percent NUMERIC(6, 2),
    width_meter NUMERIC(6, 2),
    walk_access VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    braille_block_state VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    audio_signal_state VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    curb_ramp_state VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    width_state VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    surface_state VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    stairs_state VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    elevator_state VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    crossing_state VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    CONSTRAINT fk_road_segments_from_node FOREIGN KEY (from_node_id) REFERENCES road_nodes (vertex_id),
    CONSTRAINT fk_road_segments_to_node FOREIGN KEY (to_node_id) REFERENCES road_nodes (vertex_id),
    CONSTRAINT uq_road_segments_stable_key UNIQUE (
        source_way_id,
        source_osm_from_node_id,
        source_osm_to_node_id,
        segment_ordinal
    )
);

CREATE INDEX IF NOT EXISTS idx_road_nodes_osm_node_id ON road_nodes (osm_node_id);
CREATE INDEX IF NOT EXISTS idx_road_segments_from_node_id ON road_segments (from_node_id);
CREATE INDEX IF NOT EXISTS idx_road_segments_to_node_id ON road_segments (to_node_id);
CREATE INDEX IF NOT EXISTS idx_road_segments_stable_lookup
    ON road_segments (source_way_id, source_osm_from_node_id, source_osm_to_node_id);
