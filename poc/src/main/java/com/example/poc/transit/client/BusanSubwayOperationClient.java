package com.example.poc.transit.client;

import java.time.OffsetDateTime;

public interface BusanSubwayOperationClient {

	SubwayScheduleEnrichment enrichSubwayLeg(
			String lineName,
			String boardStationName,
			String alightStationName,
			OffsetDateTime earliestDeparture,
			int plannedRideMinute);

	record SubwayScheduleEnrichment(
			OffsetDateTime departureAt,
			OffsetDateTime arrivalAt,
			String displayLabel) {
	}
}
