package com.example.poc.transit.client;

import static com.example.poc.transit.model.TransitLegType.BUS;
import static com.example.poc.transit.model.TransitLegType.SUBWAY;
import static com.example.poc.transit.model.TransitLegType.WALK;
import static com.example.poc.transit.model.TransitMarkerType.BUS_STOP_ALIGHT;
import static com.example.poc.transit.model.TransitMarkerType.BUS_STOP_BOARD;
import static com.example.poc.transit.model.TransitMarkerType.SUBWAY_ENTRANCE;
import static com.example.poc.transit.model.TransitMarkerType.SUBWAY_EXIT;
import static com.example.poc.transit.model.TransitMarkerType.TRANSFER_POINT;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.poc.transit.model.TransitCombinationType;
import com.example.poc.transit.model.TransitMixedRouteRequest;
import com.example.poc.transit.service.TransitCandidateSeed;
import com.example.poc.transit.service.TransitLegSeed;

@Component
public class StubOdsayTransitClient implements OdsayTransitClient {

	@Override
	public List<TransitCandidateSeed> searchSeeds(TransitMixedRouteRequest request, OffsetDateTime departureAt) {
		return List.of(
				new TransitCandidateSeed(
						TransitCombinationType.BUS_SUBWAY,
						List.of(
								new TransitLegSeed(WALK, 0.00, 0.12, "Walk to bus stop", 0, null, null, null, null, null, null, null),
								new TransitLegSeed(BUS, 0.12, 0.42, "Bus 86", 9, "5200000086", "86", null, "Busan City Hall", "Seomyeon", BUS_STOP_BOARD, BUS_STOP_ALIGHT),
								new TransitLegSeed(WALK, 0.42, 0.48, "Transfer to subway entrance", 0, null, null, null, null, null, null, null),
								new TransitLegSeed(SUBWAY, 0.48, 0.82, "Busan Line 1", 8, null, null, "Busan Line 1", "Seomyeon Station", "Nampo Station", SUBWAY_ENTRANCE, SUBWAY_EXIT),
								new TransitLegSeed(WALK, 0.82, 1.00, "Walk to destination", 0, null, null, null, null, null, null, null)),
						"Bus 86 + Busan Line 1",
						1),
				new TransitCandidateSeed(
						TransitCombinationType.SUBWAY_BUS,
						List.of(
								new TransitLegSeed(WALK, 0.00, 0.10, "Walk to subway entrance", 0, null, null, null, null, null, null, null),
								new TransitLegSeed(SUBWAY, 0.10, 0.44, "Busan Line 2", 9, null, null, "Busan Line 2", "City Hall Station", "Seomyeon Station", SUBWAY_ENTRANCE, SUBWAY_EXIT),
								new TransitLegSeed(WALK, 0.44, 0.52, "Transfer to bus stop", 0, null, null, null, null, null, TRANSFER_POINT, TRANSFER_POINT),
								new TransitLegSeed(BUS, 0.52, 0.86, "Bus 1001", 10, "5200010001", "1001", null, "Seomyeon Transfer Center", "Nampo-dong", BUS_STOP_BOARD, BUS_STOP_ALIGHT),
								new TransitLegSeed(WALK, 0.86, 1.00, "Walk to destination", 0, null, null, null, null, null, null, null)),
						"Busan Line 2 + Bus 1001",
						1),
				new TransitCandidateSeed(
						TransitCombinationType.SUBWAY_SUBWAY,
						List.of(
								new TransitLegSeed(WALK, 0.00, 0.08, "Walk to subway entrance", 0, null, null, null, null, null, null, null),
								new TransitLegSeed(SUBWAY, 0.08, 0.40, "Busan Line 1", 7, null, null, "Busan Line 1", "City Hall Station", "Seomyeon Station", SUBWAY_ENTRANCE, SUBWAY_EXIT),
								new TransitLegSeed(WALK, 0.40, 0.47, "Transfer inside station", 0, null, null, null, null, null, TRANSFER_POINT, TRANSFER_POINT),
								new TransitLegSeed(SUBWAY, 0.47, 0.84, "Busan Line 2", 9, null, null, "Busan Line 2", "Seomyeon Station", "Jungang Station", SUBWAY_ENTRANCE, SUBWAY_EXIT),
								new TransitLegSeed(WALK, 0.84, 1.00, "Walk to destination", 0, null, null, null, null, null, null, null)),
						"Busan Line 1 + Busan Line 2",
						1),
				new TransitCandidateSeed(
						TransitCombinationType.BUS,
						List.of(
								new TransitLegSeed(WALK, 0.00, 0.14, "Walk to bus stop", 0, null, null, null, null, null, null, null),
								new TransitLegSeed(BUS, 0.14, 0.80, "Bus 179", 14, "5200000179", "179", null, "Busan City Hall", "Nampo-dong", BUS_STOP_BOARD, BUS_STOP_ALIGHT),
								new TransitLegSeed(WALK, 0.80, 1.00, "Walk to destination", 0, null, null, null, null, null, null, null)),
						"Bus 179",
						0),
				new TransitCandidateSeed(
						TransitCombinationType.SUBWAY,
						List.of(
								new TransitLegSeed(WALK, 0.00, 0.10, "Walk to subway entrance", 0, null, null, null, null, null, null, null),
								new TransitLegSeed(SUBWAY, 0.10, 0.88, "Busan Line 1", 16, null, null, "Busan Line 1", "City Hall Station", "Nampo Station", SUBWAY_ENTRANCE, SUBWAY_EXIT),
								new TransitLegSeed(WALK, 0.88, 1.00, "Walk to destination", 0, null, null, null, null, null, null, null)),
						"Busan Line 1",
						0),
				new TransitCandidateSeed(
						TransitCombinationType.BUS_BUS,
						List.of(
								new TransitLegSeed(WALK, 0.00, 0.10, "Walk to bus stop", 0, null, null, null, null, null, null, null),
								new TransitLegSeed(BUS, 0.10, 0.38, "Bus 110", 8, "5200000110", "110", null, "Busan City Hall", "Seomyeon", BUS_STOP_BOARD, BUS_STOP_ALIGHT),
								new TransitLegSeed(WALK, 0.38, 0.46, "Transfer to next bus stop", 0, null, null, null, null, null, TRANSFER_POINT, TRANSFER_POINT),
								new TransitLegSeed(BUS, 0.46, 0.84, "Bus 508", 9, "5200000508", "508", null, "Seomyeon", "Nampo-dong", BUS_STOP_BOARD, BUS_STOP_ALIGHT),
								new TransitLegSeed(WALK, 0.84, 1.00, "Walk to destination", 0, null, null, null, null, null, null, null)),
						"Bus 110 + Bus 508",
						1));
	}
}
