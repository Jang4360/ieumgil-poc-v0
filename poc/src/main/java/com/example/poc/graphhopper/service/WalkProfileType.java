package com.example.poc.graphhopper.service;

public enum WalkProfileType {
	VISUAL_SAFE("visual_safe", "ieum_visual_safe_pass", 90d),
	VISUAL_FAST("visual_fast", "ieum_visual_fast_pass", 45d),
	WHEELCHAIR_SAFE("wheelchair_safe", "ieum_wheelchair_safe_pass", 95d),
	WHEELCHAIR_FAST("wheelchair_fast", "ieum_wheelchair_fast_pass", 50d);

	private final String profileName;
	private final String passEncodedValue;
	private final double distanceInfluence;

	WalkProfileType(String profileName, String passEncodedValue, double distanceInfluence) {
		this.profileName = profileName;
		this.passEncodedValue = passEncodedValue;
		this.distanceInfluence = distanceInfluence;
	}

	public String profileName() {
		return profileName;
	}

	public String passEncodedValue() {
		return passEncodedValue;
	}

	public double distanceInfluence() {
		return distanceInfluence;
	}
}
