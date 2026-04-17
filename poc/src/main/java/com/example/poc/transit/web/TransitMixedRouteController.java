package com.example.poc.transit.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.poc.transit.model.TransitMixedRouteRequest;
import com.example.poc.transit.service.TransitMixedRouteFacade;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/routes/transit-mixed")
@ConditionalOnProperty(prefix = "poc.transit-mixed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransitMixedRouteController {

	private final TransitMixedRouteFacade transitMixedRouteFacade;

	public TransitMixedRouteController(TransitMixedRouteFacade transitMixedRouteFacade) {
		this.transitMixedRouteFacade = transitMixedRouteFacade;
	}

	@PostMapping("/candidates")
	public ApiResponse searchCandidates(@Valid @RequestBody TransitMixedRouteRequest request) {
		return new ApiResponse(
				true,
				transitMixedRouteFacade.searchCandidates(request),
				null);
	}

	public record ApiResponse(
			boolean success,
			Object data,
			String message) {
	}
}
