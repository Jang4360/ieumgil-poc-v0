package com.example.poc.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.poc.route.model.RouteOption;
import com.example.poc.route.model.RouteSearchRequest;
import com.example.poc.route.model.RoutingPolicy;
import com.example.poc.transit.model.DisabilityType;
import com.example.poc.transit.model.TransitCandidateResponse;
import com.example.poc.transit.model.TransitCandidateSummaryResponse;
import com.example.poc.transit.model.TransitCombinationType;
import com.example.poc.transit.model.TransitGeometryResponse;
import com.example.poc.transit.model.TransitMixedRouteResponse;
import com.example.poc.transit.model.TransitPointRequest;
import com.example.poc.transit.model.TransitVerificationStatus;
import com.example.poc.transit.service.TransitMixedRouteFacade;
import com.example.poc.transit.service.TransitWalkLegRouter;
import com.example.poc.transit.service.TransitWaypoint;

class RouteSearchFacadeTest {

	@Test
	void includesTransitMixedOptionWhenDistanceExceedsThreshold() {
		TransitWalkLegRouter walkRouter = mock(TransitWalkLegRouter.class);
		when(walkRouter.route(any(TransitWaypoint.class), any(TransitWaypoint.class), eq(DisabilityType.WHEELCHAIR), eq(RoutingPolicy.SAFE_WALK)))
				.thenReturn(new TransitWalkLegRouter.WalkRoutingResult(
						1520d,
						25,
						"SAFE_WALK",
						"wheelchair_safe",
						true,
						false,
						null,
						List.of(List.of(129.0756, 35.1796), List.of(129.0590, 35.1577))));
		when(walkRouter.route(any(TransitWaypoint.class), any(TransitWaypoint.class), eq(DisabilityType.WHEELCHAIR), eq(RoutingPolicy.FAST_WALK)))
				.thenReturn(new TransitWalkLegRouter.WalkRoutingResult(
						1410d,
						20,
						"FAST_WALK",
						"wheelchair_fast",
						true,
						false,
						null,
						List.of(List.of(129.0756, 35.1796), List.of(129.0670, 35.1680), List.of(129.0590, 35.1577))));

		TransitMixedRouteFacade transitFacade = mock(TransitMixedRouteFacade.class);
		when(transitFacade.searchCandidates(any()))
				.thenReturn(new TransitMixedRouteResponse(
						"search",
						DisabilityType.WHEELCHAIR,
						RouteOption.TRANSIT_MIXED,
						RoutingPolicy.ACCESSIBLE_TRANSIT,
						"wheelchair_safe",
						"TRANSIT_MIXED",
						"STUB",
						OffsetDateTime.parse("2026-04-17T18:10:00+09:00"),
						List.of(new TransitCandidateResponse(
								"candidate-1",
								1,
								TransitCombinationType.BUS_SUBWAY,
								TransitVerificationStatus.VERIFIED,
								18,
								OffsetDateTime.parse("2026-04-17T18:28:00+09:00"),
								"STUB",
								true,
								0,
								null,
								330d,
								1,
								new TransitCandidateSummaryResponse("Bus 86 + Line 1", "Bus in 4 min", "Line 1 departs at 18:20"),
								new TransitGeometryResponse("LineString", List.of(List.of(129.0756, 35.1796), List.of(129.0590, 35.1577))),
								List.of(),
								List.of()))));

		RouteSearchFacade facade = new RouteSearchFacade(walkRouter, transitFacade);

		var response = facade.search(new RouteSearchRequest(
				new TransitPointRequest(35.1796, 129.0756),
				new TransitPointRequest(35.1577, 129.0590),
				DisabilityType.WHEELCHAIR));

		assertThat(response.userType()).isEqualTo(DisabilityType.WHEELCHAIR);
		assertThat(response.availableRouteOptions()).containsExactly(
				RouteOption.SAFE,
				RouteOption.SHORTEST,
				RouteOption.TRANSIT_MIXED);
		assertThat(response.routes()).hasSize(3);
		assertThat(response.routes().get(0).appliedProfile()).isEqualTo("wheelchair_safe");
		assertThat(response.routes().get(0).graphHopperBacked()).isTrue();
		assertThat(response.routes().get(1).appliedProfile()).isEqualTo("wheelchair_fast");
		assertThat(response.routes().get(2).fallbackUsed()).isFalse();
		assertThat(response.routes().get(2).summary().busArrivalDisplay()).isEqualTo("Bus in 4 min");
	}
}
