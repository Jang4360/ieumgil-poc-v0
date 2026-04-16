package com.example.poc.graphhopper.model;

import java.util.List;
import java.util.Map;

public record GraphImportDiagnostics(
		List<GraphImportMismatch> mismatches,
		Map<String, Integer> reasonCounts) {

	public GraphImportDiagnostics {
		mismatches = List.copyOf(mismatches);
		reasonCounts = Map.copyOf(reasonCounts);
	}
}
