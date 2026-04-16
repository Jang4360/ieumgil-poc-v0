package com.example.poc.graphhopper.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

class IeumRoadSegmentTagParserTest {

	@Test
	void writesCustomEncodedValuesFromArtificialWayTags() {
		EncodingManager.Builder builder = new EncodingManager.Builder();
		IeumGraphEncodedValues.createEncodedValues().forEach(builder::add);
		EncodingManager encodingManager = builder.build();
		IeumRoadSegmentTagParser parser = new IeumRoadSegmentTagParser(encodingManager);
		ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getBytesForFlags());
		ReaderWay way = new ReaderWay(1L);
		way.setTag(IeumGraphEncodedValues.DB_MATCH, true);
		way.setTag(IeumGraphEncodedValues.SEGMENT_ORDINAL, 7);
		way.setTag(IeumGraphEncodedValues.WALK_ACCESS, "UNKNOWN");
		way.setTag(IeumGraphEncodedValues.CROSSING_STATE, "UNKNOWN");
		way.setTag(IeumGraphEncodedValues.BRAILLE_BLOCK_STATE, "UNKNOWN");
		way.setTag(IeumGraphEncodedValues.AUDIO_SIGNAL_STATE, "UNKNOWN");
		way.setTag(IeumGraphEncodedValues.CURB_RAMP_STATE, "UNKNOWN");
		way.setTag(IeumGraphEncodedValues.WIDTH_STATE, "UNKNOWN");
		way.setTag(IeumGraphEncodedValues.SURFACE_STATE, "UNKNOWN");
		way.setTag(IeumGraphEncodedValues.STAIRS_STATE, "UNKNOWN");
		way.setTag(IeumGraphEncodedValues.ELEVATOR_STATE, "UNKNOWN");

		parser.handleWayTags(0, edgeIntAccess, way, new IntsRef(2));

		assertThat(encodingManager.getBooleanEncodedValue(IeumGraphEncodedValues.DB_MATCH)
				.getBool(false, 0, edgeIntAccess)).isTrue();
		assertThat(encodingManager.getIntEncodedValue(IeumGraphEncodedValues.SEGMENT_ORDINAL)
				.getInt(false, 0, edgeIntAccess)).isEqualTo(7);
		assertThat(encodingManager.getStringEncodedValue(IeumGraphEncodedValues.WALK_ACCESS)
				.getString(false, 0, edgeIntAccess)).isEqualTo("UNKNOWN");
	}
}
