package com.example.poc.transit.model;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TransitMixedRouteRequest(
		@NotNull @Valid TransitPointRequest startPoint,
		@NotNull @Valid TransitPointRequest endPoint,
		@NotNull DisabilityType disabilityType,
		OffsetDateTime departureAt,
		@Min(1) @Max(3) Integer maxCandidates) {
}
