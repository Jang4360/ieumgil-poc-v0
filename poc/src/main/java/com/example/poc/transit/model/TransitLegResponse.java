package com.example.poc.transit.model;

import java.time.OffsetDateTime;

public record TransitLegResponse(
		TransitLegType type,
		int sequence,
		String title,
		double distanceMeter,
		int durationMinute,
		OffsetDateTime departureAt,
		OffsetDateTime arrivalAt,
		TransitGeometryResponse geometry,
		String walkPolicy,
		String busRouteId,
		String busRouteNo,
		String boardStopName,
		String alightStopName,
		Integer busArrivalMinute,
		Boolean isLowFloorExpected,
		String subwayLineName,
		String boardStationName,
		String alightStationName,
		OffsetDateTime subwayDepartureAt,
		OffsetDateTime subwayArrivalAt) {
}
