package com.example.poc.transit.client;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

@Component
public class StubBusanBimsTransitClient implements BusanBimsTransitClient {

	@Override
	public BusArrivalEnrichment enrichBusLeg(String routeId, String routeNo, String boardStopName, OffsetDateTime earliestDeparture) {
		int arrivalMinute = 3 + Math.floorMod((routeNo == null ? 0 : routeNo.hashCode()), 5);
		boolean lowFloorExpected = routeNo != null && !routeNo.isBlank();
		return new BusArrivalEnrichment(arrivalMinute, lowFloorExpected, "Bus " + routeNo + " in " + arrivalMinute + " min");
	}
}
