package com.example.poc.transit.client;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

@Component
public class StubBusanSubwayOperationClient implements BusanSubwayOperationClient {

	@Override
	public SubwayScheduleEnrichment enrichSubwayLeg(
			String lineName,
			String boardStationName,
			String alightStationName,
			OffsetDateTime earliestDeparture,
			int plannedRideMinute) {
		int waitMinute = Math.max(2, 4 - (earliestDeparture.getMinute() % 4));
		OffsetDateTime departureAt = earliestDeparture.plusMinutes(waitMinute);
		OffsetDateTime arrivalAt = departureAt.plusMinutes(plannedRideMinute);
		return new SubwayScheduleEnrichment(
				departureAt,
				arrivalAt,
				lineName + " departs at " + departureAt.toLocalTime().withSecond(0).withNano(0));
	}
}
