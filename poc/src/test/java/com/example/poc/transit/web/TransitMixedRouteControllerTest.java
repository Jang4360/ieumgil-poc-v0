package com.example.poc.transit.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.poc.route.model.RouteOption;
import com.example.poc.route.model.RoutingPolicy;
import com.example.poc.transit.model.DisabilityType;
import com.example.poc.transit.model.TransitMixedRouteResponse;
import com.example.poc.transit.service.TransitMixedRouteFacade;

@WebMvcTest(TransitMixedRouteController.class)
class TransitMixedRouteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private TransitMixedRouteFacade transitMixedRouteFacade;

	@Test
	void returnsTransitCandidates() throws Exception {
		when(transitMixedRouteFacade.searchCandidates(any()))
				.thenReturn(new TransitMixedRouteResponse(
						"search-1",
						DisabilityType.VISUAL,
						RouteOption.TRANSIT_MIXED,
						RoutingPolicy.ACCESSIBLE_TRANSIT,
						"visual_safe",
						"TRANSIT_MIXED",
						"STUB",
						OffsetDateTime.parse("2026-04-17T18:10:00+09:00"),
						List.of()));

		mockMvc.perform(post("/routes/transit-mixed/candidates")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "startPoint": { "lat": 35.1796, "lng": 129.0756 },
								  "endPoint": { "lat": 35.1577, "lng": 129.0590 },
								  "disabilityType": "VISUAL"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.searchId").value("search-1"))
				.andExpect(jsonPath("$.data.walkProfile").value("visual_safe"))
				.andExpect(jsonPath("$.data.dataSource").value("STUB"));
	}

	@Test
	void rejectsRequestWithoutDisabilityType() throws Exception {
		mockMvc.perform(post("/routes/transit-mixed/candidates")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "startPoint": { "lat": 35.1796, "lng": 129.0756 },
								  "endPoint": { "lat": 35.1577, "lng": 129.0590 }
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void acceptsWheelchairType() throws Exception {
		when(transitMixedRouteFacade.searchCandidates(any()))
				.thenReturn(new TransitMixedRouteResponse(
						"search-2",
						DisabilityType.WHEELCHAIR,
						RouteOption.TRANSIT_MIXED,
						RoutingPolicy.ACCESSIBLE_TRANSIT,
						"wheelchair_safe",
						"TRANSIT_MIXED",
						"STUB",
						OffsetDateTime.parse("2026-04-17T18:10:00+09:00"),
						List.of()));

		mockMvc.perform(post("/routes/transit-mixed/candidates")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "startPoint": { "lat": 35.1796, "lng": 129.0756 },
								  "endPoint": { "lat": 35.1577, "lng": 129.0590 },
								  "disabilityType": "WHEELCHAIR"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.searchId").value("search-2"));
	}
}
