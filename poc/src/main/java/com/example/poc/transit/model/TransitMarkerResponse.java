package com.example.poc.transit.model;

public record TransitMarkerResponse(
		TransitMarkerType markerType,
		String label,
		double lat,
		double lng,
		int sequence) {
}
