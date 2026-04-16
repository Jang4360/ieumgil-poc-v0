package com.example.poc.network.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class WalkableWayPolicy {

	private static final Set<String> INTRINSICALLY_WALKABLE_HIGHWAYS = Set.of(
			"footway",
			"path",
			"pedestrian",
			"living_street",
			"steps",
			"track",
			"residential",
			"service",
			"unclassified",
			"tertiary",
			"tertiary_link",
			"secondary",
			"secondary_link",
			"primary",
			"primary_link",
			"road");

	private static final Set<String> NON_WALKABLE_HIGHWAYS = Set.of(
			"motorway",
			"motorway_link",
			"trunk",
			"trunk_link",
			"proposed",
			"construction",
			"raceway",
			"bus_guideway");

	private static final Set<String> ACCESS_DENIED_VALUES = Set.of("no", "private");
	private static final Set<String> ACCESS_ALLOWED_VALUES = Set.of("yes", "designated", "permissive", "destination");

	public boolean isEligible(Map<String, String> tags) {
		String highway = tags.get("highway");
		if (highway == null || highway.isBlank()) {
			return false;
		}
		if ("yes".equals(tags.get("area"))) {
			return false;
		}
		if (NON_WALKABLE_HIGHWAYS.contains(highway)) {
			return false;
		}
		String foot = tags.get("foot");
		String access = tags.get("access");
		if (isDenied(foot)) {
			return false;
		}
		if (isDenied(access) && !isAllowed(foot)) {
			return false;
		}
		if (hasUsableSidewalk(tags)) {
			return true;
		}
		if (isAllowed(foot)) {
			return true;
		}
		return INTRINSICALLY_WALKABLE_HIGHWAYS.contains(highway);
	}

	private boolean hasUsableSidewalk(Map<String, String> tags) {
		String sidewalk = tags.get("sidewalk");
		if (sidewalk == null || sidewalk.isBlank()) {
			return false;
		}
		return !Set.of("no", "none", "separate").contains(sidewalk);
	}

	private boolean isDenied(String value) {
		return value != null && ACCESS_DENIED_VALUES.contains(value);
	}

	private boolean isAllowed(String value) {
		return value != null && ACCESS_ALLOWED_VALUES.contains(value);
	}
}
