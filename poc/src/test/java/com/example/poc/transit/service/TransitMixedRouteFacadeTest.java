package com.example.poc.transit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.poc.route.model.RoutingPolicy;
import com.example.poc.transit.client.StubBusanBimsTransitClient;
import com.example.poc.transit.client.StubBusanSubwayOperationClient;
import com.example.poc.transit.client.StubOdsayTransitClient;
import com.example.poc.transit.config.TransitMixedRoutingProperties;
import com.example.poc.transit.model.DisabilityType;
import com.example.poc.transit.model.TransitLegType;
import com.example.poc.transit.model.TransitMixedRouteRequest;
import com.example.poc.transit.model.TransitPointRequest;

class TransitMixedRouteFacadeTest {

	@Test
	void returnsTopThreeCandidatesWithSafeWalkPolicyMetadata() {
		TransitMixedRoutingProperties properties = new TransitMixedRoutingProperties();
		properties.setMaxCandidates(3);
		TransitWalkLegRouter walkLegRouter = mock(TransitWalkLegRouter.class);
		when(walkLegRouter.route(any(), any(), eq(DisabilityType.VISUAL), eq(RoutingPolicy.SAFE_WALK)))
				.thenAnswer(invocation -> {
					TransitWaypoint from = invocation.getArgument(0);
					TransitWaypoint to = invocation.getArgument(1);
					return new TransitWalkLegRouter.WalkRoutingResult(
							120d,
							3,
							"SAFE_WALK",
							"visual_safe",
							true,
							false,
							null,
							List.of(
									List.of(from.lng(), from.lat()),
									List.of(to.lng(), to.lat())));
				});
		Clock clock = Clock.fixed(Instant.parse("2026-04-17T09:00:00Z"), ZoneId.of("UTC"));
		TransitMixedRouteFacade facade = new TransitMixedRouteFacade(
				properties,
				new StubOdsayTransitClient(),
				new StubBusanBimsTransitClient(),
				new StubBusanSubwayOperationClient(),
				walkLegRouter,
				clock);

		TransitMixedRouteRequest request = new TransitMixedRouteRequest(
				new TransitPointRequest(35.1796, 129.0756),
				new TransitPointRequest(35.1577, 129.0590),
				DisabilityType.VISUAL,
				OffsetDateTime.parse("2026-04-17T18:10:00+09:00"),
				3);

		var response = facade.searchCandidates(request);

		assertThat(response.userType()).isEqualTo(DisabilityType.VISUAL);
		assertThat(response.policyName()).isEqualTo(RoutingPolicy.ACCESSIBLE_TRANSIT);
		assertThat(response.walkProfile()).isEqualTo("visual_safe");
		assertThat(response.dataSource()).isEqualTo("STUB");
		assertThat(response.candidates()).hasSize(3);
		assertThat(response.candidates())
				.extracting(candidate -> candidate.rank())
				.containsExactly(1, 2, 3);
		assertThat(response.candidates())
				.extracting(candidate -> candidate.walkLegGraphHopperBacked())
				.containsOnly(true);
		assertThat(response.candidates())
				.flatExtracting(candidate -> candidate.legs())
				.filteredOn(leg -> leg.type() == TransitLegType.WALK)
				.extracting(leg -> leg.walkPolicy())
				.containsOnly("SAFE_WALK:visual_safe");
	}
}
