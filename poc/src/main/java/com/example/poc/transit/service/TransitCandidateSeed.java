package com.example.poc.transit.service;

import java.util.List;

import com.example.poc.transit.model.TransitCombinationType;

public record TransitCandidateSeed(
		TransitCombinationType combinationType,
		List<TransitLegSeed> legs,
		String primaryTransitLabel,
		int transferCount) {
}
