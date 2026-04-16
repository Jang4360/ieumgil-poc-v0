package com.example.poc.network.util;

import java.util.List;

import com.example.poc.network.model.OsmNodePoint;

public final class GeoUtils {

	private static final double EARTH_RADIUS_METER = 6_371_000d;

	private GeoUtils() {
	}

	public static double measureLengthMeters(List<OsmNodePoint> points) {
		double total = 0d;
		for (int i = 0; i < points.size() - 1; i++) {
			total += distanceMeters(points.get(i), points.get(i + 1));
		}
		return total;
	}

	public static double distanceMeters(OsmNodePoint left, OsmNodePoint right) {
		double lat1 = Math.toRadians(left.latitude());
		double lat2 = Math.toRadians(right.latitude());
		double deltaLat = lat2 - lat1;
		double deltaLon = Math.toRadians(right.longitude() - left.longitude());
		double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_METER * c;
	}
}
