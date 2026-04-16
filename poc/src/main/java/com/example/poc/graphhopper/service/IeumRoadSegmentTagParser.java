package com.example.poc.graphhopper.service;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

public class IeumRoadSegmentTagParser implements TagParser {

	private final BooleanEncodedValue dbMatchEnc;
	private final IntEncodedValue segmentOrdinalEnc;
	private final StringEncodedValue walkAccessEnc;
	private final StringEncodedValue brailleBlockStateEnc;
	private final StringEncodedValue audioSignalStateEnc;
	private final StringEncodedValue curbRampStateEnc;
	private final StringEncodedValue widthStateEnc;
	private final StringEncodedValue surfaceStateEnc;
	private final StringEncodedValue stairsStateEnc;
	private final StringEncodedValue elevatorStateEnc;
	private final StringEncodedValue crossingStateEnc;

	public IeumRoadSegmentTagParser(com.graphhopper.routing.ev.EncodedValueLookup lookup) {
		this.dbMatchEnc = lookup.getBooleanEncodedValue(IeumGraphEncodedValues.DB_MATCH);
		this.segmentOrdinalEnc = lookup.getIntEncodedValue(IeumGraphEncodedValues.SEGMENT_ORDINAL);
		this.walkAccessEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.WALK_ACCESS);
		this.brailleBlockStateEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.BRAILLE_BLOCK_STATE);
		this.audioSignalStateEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.AUDIO_SIGNAL_STATE);
		this.curbRampStateEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.CURB_RAMP_STATE);
		this.widthStateEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.WIDTH_STATE);
		this.surfaceStateEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.SURFACE_STATE);
		this.stairsStateEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.STAIRS_STATE);
		this.elevatorStateEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.ELEVATOR_STATE);
		this.crossingStateEnc = lookup.getStringEncodedValue(IeumGraphEncodedValues.CROSSING_STATE);
	}

	@Override
	public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
		dbMatchEnc.setBool(false, edgeId, edgeIntAccess, Boolean.TRUE.equals(way.getTag(IeumGraphEncodedValues.DB_MATCH, false)));
		segmentOrdinalEnc.setInt(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.SEGMENT_ORDINAL, 0));
		walkAccessEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.WALK_ACCESS, "UNKNOWN"));
		brailleBlockStateEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.BRAILLE_BLOCK_STATE, "UNKNOWN"));
		audioSignalStateEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.AUDIO_SIGNAL_STATE, "UNKNOWN"));
		curbRampStateEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.CURB_RAMP_STATE, "UNKNOWN"));
		widthStateEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.WIDTH_STATE, "UNKNOWN"));
		surfaceStateEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.SURFACE_STATE, "UNKNOWN"));
		stairsStateEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.STAIRS_STATE, "UNKNOWN"));
		elevatorStateEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.ELEVATOR_STATE, "UNKNOWN"));
		crossingStateEnc.setString(false, edgeId, edgeIntAccess, way.getTag(IeumGraphEncodedValues.CROSSING_STATE, "UNKNOWN"));
	}
}
