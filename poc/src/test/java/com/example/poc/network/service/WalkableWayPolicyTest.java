package com.example.poc.network.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WalkableWayPolicyTest {

	private final WalkableWayPolicy policy = new WalkableWayPolicy();

	@Test
	void shouldAcceptIntrinsicFootways() {
		assertThat(policy.isEligible(Map.of("highway", "footway"))).isTrue();
	}

	@Test
	void shouldRejectMotorways() {
		assertThat(policy.isEligible(Map.of("highway", "motorway"))).isFalse();
	}

	@Test
	void shouldRejectExplicitlyForbiddenFootAccess() {
		assertThat(policy.isEligible(Map.of("highway", "residential", "foot", "no"))).isFalse();
	}

	@Test
	void shouldAcceptRoadsWithUsableSidewalk() {
		assertThat(policy.isEligible(Map.of("highway", "primary", "sidewalk", "both"))).isTrue();
	}
}
