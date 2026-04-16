package com.example.poc.network.util;

import java.util.Locale;

import com.example.poc.network.model.OsmNodePoint;

public final class PointWktFormatter {

	private PointWktFormatter() {
	}

	public static String format(OsmNodePoint point) {
		return String.format(Locale.US, "POINT (%.7f %.7f)", point.longitude(), point.latitude());
	}
}
