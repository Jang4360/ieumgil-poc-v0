package com.example.poc.transit.model;

import java.util.List;

public record TransitGeometryResponse(
		String type,
		List<List<Double>> coordinates) {
}
