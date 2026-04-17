package com.example.poc.transit.service;

import com.example.poc.transit.model.TransitLegType;
import com.example.poc.transit.model.TransitMarkerType;

public record TransitLegSeed(
		TransitLegType type,
		double startRatio,
		double endRatio,
		String title,
		int plannedDurationMinute,
		String routeId,
		String routeNo,
		String lineName,
		String boardLabel,
		String alightLabel,
		TransitMarkerType boardMarkerType,
		TransitMarkerType alightMarkerType) {
}
