package com.example.poc.route.web;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.poc.route.model.RouteSearchRequest;
import com.example.poc.route.service.RouteSearchFacade;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/routes")
public class RouteSearchController {

	private final RouteSearchFacade routeSearchFacade;

	public RouteSearchController(RouteSearchFacade routeSearchFacade) {
		this.routeSearchFacade = routeSearchFacade;
	}

	@PostMapping("/search")
	public ApiResponse search(@Valid @RequestBody RouteSearchRequest request) {
		return new ApiResponse(true, routeSearchFacade.search(request), null);
	}

	public record ApiResponse(
			boolean success,
			Object data,
			String message) {
	}
}
