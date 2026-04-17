package com.example.poc.route.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.poc.route.model.DistancePolicyResponse;
import com.example.poc.route.model.RouteCandidateResponse;
import com.example.poc.route.model.RouteOption;
import com.example.poc.route.model.RouteSearchResponse;
import com.example.poc.route.model.RouteSummaryResponse;
import com.example.poc.route.model.RoutingPolicy;
import com.example.poc.route.service.RouteSearchFacade;
import com.example.poc.transit.model.DisabilityType;

@WebMvcTest(RouteSearchController.class)
class RouteSearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private RouteSearchFacade routeSearchFacade;

	@Test
	void returnsRouteSearchPayload() throws Exception {
		when(routeSearchFacade.search(any()))
				.thenReturn(new RouteSearchResponse(
						"route-search-1",
						DisabilityType.VISUAL,
						new DistancePolicyResponse(1000d, 1300d),
						List.of(RouteOption.SAFE, RouteOption.SHORTEST, RouteOption.TRANSIT_MIXED),
						List.of(new RouteCandidateResponse(
								"route-1",
								RouteOption.SAFE,
								RoutingPolicy.SAFE_WALK,
								"Safe Walk",
								DisabilityType.VISUAL,
								"visual_safe",
								"WALK",
								true,
								false,
								null,
								1300d,
								21,
								"LOW",
								null,
								new RouteSummaryResponse("Reason", null, null),
								null,
								List.of()))));

		mockMvc.perform(post("/routes/search")
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
				.andExpect(jsonPath("$.data.searchId").value("route-search-1"))
				.andExpect(jsonPath("$.data.userType").value("VISUAL"))
				.andExpect(jsonPath("$.data.routes[0].policyName").value("SAFE_WALK"))
				.andExpect(jsonPath("$.data.routes[0].graphHopperBacked").value(true));
	}
}
