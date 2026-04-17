package com.example.poc.route.model;

import com.example.poc.transit.model.DisabilityType;
import com.example.poc.transit.model.TransitPointRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RouteSearchRequest(
		@NotNull @Valid TransitPointRequest startPoint,
		@NotNull @Valid TransitPointRequest endPoint,
		@NotNull DisabilityType disabilityType) {
}
