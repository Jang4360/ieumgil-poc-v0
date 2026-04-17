package com.example.poc.graphhopper.service;

import java.util.List;

import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.IntEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.ev.StringEncodedValue;

public final class IeumGraphEncodedValues {

	public static final String DB_MATCH = "ieum_db_match";
	public static final String SEGMENT_ORDINAL = "ieum_segment_ordinal";
	public static final String WALK_ACCESS = "ieum_walk_access";
	public static final String BRAILLE_BLOCK_STATE = "ieum_braille_block_state";
	public static final String AUDIO_SIGNAL_STATE = "ieum_audio_signal_state";
	public static final String CURB_RAMP_STATE = "ieum_curb_ramp_state";
	public static final String WIDTH_STATE = "ieum_width_state";
	public static final String SURFACE_STATE = "ieum_surface_state";
	public static final String STAIRS_STATE = "ieum_stairs_state";
	public static final String ELEVATOR_STATE = "ieum_elevator_state";
	public static final String CROSSING_STATE = "ieum_crossing_state";
	public static final String VISUAL_SAFE_PASS = "ieum_visual_safe_pass";
	public static final String VISUAL_FAST_PASS = "ieum_visual_fast_pass";
	public static final String WHEELCHAIR_SAFE_PASS = "ieum_wheelchair_safe_pass";
	public static final String WHEELCHAIR_FAST_PASS = "ieum_wheelchair_fast_pass";

	private IeumGraphEncodedValues() {
	}

	public static List<EncodedValue> createEncodedValues() {
		return List.of(
				new SimpleBooleanEncodedValue(DB_MATCH),
				new IntEncodedValueImpl(SEGMENT_ORDINAL, 12, false),
				new StringEncodedValue(WALK_ACCESS, 8),
				new StringEncodedValue(BRAILLE_BLOCK_STATE, 8),
				new StringEncodedValue(AUDIO_SIGNAL_STATE, 8),
				new StringEncodedValue(CURB_RAMP_STATE, 8),
				new StringEncodedValue(WIDTH_STATE, 8),
				new StringEncodedValue(SURFACE_STATE, 8),
				new StringEncodedValue(STAIRS_STATE, 8),
				new StringEncodedValue(ELEVATOR_STATE, 8),
				new StringEncodedValue(CROSSING_STATE, 8),
				new SimpleBooleanEncodedValue(VISUAL_SAFE_PASS),
				new SimpleBooleanEncodedValue(VISUAL_FAST_PASS),
				new SimpleBooleanEncodedValue(WHEELCHAIR_SAFE_PASS),
				new SimpleBooleanEncodedValue(WHEELCHAIR_FAST_PASS));
	}
}
