package com.example.poc.transit.client;

import java.time.OffsetDateTime;

public interface BusanBimsTransitClient {

	BusArrivalEnrichment enrichBusLeg(String routeId, String routeNo, String boardStopName, OffsetDateTime earliestDeparture);

	record BusArrivalEnrichment(
			int arrivalMinute,
			boolean lowFloorExpected,
			String displayLabel) {
	}
}
