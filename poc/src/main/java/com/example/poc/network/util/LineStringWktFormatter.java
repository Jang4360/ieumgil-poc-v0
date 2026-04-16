package com.example.poc.network.util;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.example.poc.network.model.OsmNodePoint;

public final class LineStringWktFormatter {

	private LineStringWktFormatter() {
	}

	public static String format(List<OsmNodePoint> points) {
		if (points.size() < 2) {
			throw new IllegalArgumentException("LINESTRING requires at least two points");
		}
		return "LINESTRING (" + points.stream()
				.map(point -> String.format(Locale.US, "%.7f %.7f", point.longitude(), point.latitude()))
				.collect(Collectors.joining(", ")) + ")";
	}
}
