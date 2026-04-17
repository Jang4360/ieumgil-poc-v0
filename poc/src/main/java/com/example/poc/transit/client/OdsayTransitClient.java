package com.example.poc.transit.client;

import java.time.OffsetDateTime;
import java.util.List;

import com.example.poc.transit.model.TransitMixedRouteRequest;
import com.example.poc.transit.service.TransitCandidateSeed;

public interface OdsayTransitClient {

	List<TransitCandidateSeed> searchSeeds(TransitMixedRouteRequest request, OffsetDateTime departureAt);
}
