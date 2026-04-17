package com.example.poc.graphhopper.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.poc.graphhopper.model.RoadSegmentImportLookup;
import com.example.poc.route.model.RoutingPolicy;
import com.example.poc.transit.model.DisabilityType;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.util.CustomModel;

@Component
public class IeumGraphHopperFactory {

	private static final String FOOT_VEHICLE = "foot";

	public IeumGraphHopper createForImport(
			Path sourcePbf,
			Path graphDirectory,
			RoadSegmentImportLookup lookup) {
		IeumGraphHopper hopper = baseConfiguredHopper(graphDirectory, lookup);
		hopper.setOSMFile(sourcePbf.toString());
		hopper.getReaderConfig()
				.setParseWayNames(false)
				.setMaxWayPointDistance(0)
				.setWorkerThreads(1);
		return hopper;
	}

	public IeumGraphHopper createForLoad(Path graphDirectory, boolean allowWrites) {
		IeumGraphHopper hopper = baseConfiguredHopper(graphDirectory, RoadSegmentImportLookup.empty());
		hopper.setAllowWrites(allowWrites);
		return hopper;
	}

	public String profileName(DisabilityType disabilityType, RoutingPolicy routingPolicy) {
		return switch (routingPolicy) {
			case SAFE_WALK, ACCESSIBLE_TRANSIT -> safeProfileName(disabilityType);
			case FAST_WALK -> fastProfileName(disabilityType);
		};
	}

	public String safeProfileName(DisabilityType disabilityType) {
		return switch (disabilityType) {
			case VISUAL -> WalkProfileType.VISUAL_SAFE.profileName();
			case WHEELCHAIR -> WalkProfileType.WHEELCHAIR_SAFE.profileName();
		};
	}

	public String fastProfileName(DisabilityType disabilityType) {
		return switch (disabilityType) {
			case VISUAL -> WalkProfileType.VISUAL_FAST.profileName();
			case WHEELCHAIR -> WalkProfileType.WHEELCHAIR_FAST.profileName();
		};
	}

	public List<String> profileNames() {
		return List.of(
				WalkProfileType.VISUAL_SAFE.profileName(),
				WalkProfileType.VISUAL_FAST.profileName(),
				WalkProfileType.WHEELCHAIR_SAFE.profileName(),
				WalkProfileType.WHEELCHAIR_FAST.profileName());
	}

	private IeumGraphHopper baseConfiguredHopper(Path graphDirectory, RoadSegmentImportLookup lookup) {
		IeumGraphHopper hopper = new IeumGraphHopper(lookup);
		hopper.setGraphHopperLocation(graphDirectory.toString());
		hopper.setProfiles(List.of(
				profile(WalkProfileType.VISUAL_SAFE),
				profile(WalkProfileType.VISUAL_FAST),
				profile(WalkProfileType.WHEELCHAIR_SAFE),
				profile(WalkProfileType.WHEELCHAIR_FAST)));
		hopper.setEncodedValuesString(String.join(", ", List.of(
				VehicleAccess.key(FOOT_VEHICLE),
				VehicleSpeed.key(FOOT_VEHICLE))));
		return hopper;
	}

	private Profile profile(WalkProfileType walkProfileType) {
		return new Profile(walkProfileType.profileName())
				.setWeighting(CustomWeighting.NAME)
				.setCustomModel(customModel(walkProfileType));
	}

	private CustomModel customModel(WalkProfileType walkProfileType) {
		return new CustomModel()
				.setDistanceInfluence(walkProfileType.distanceInfluence())
				.addToSpeed(Statement.If("true", Statement.Op.LIMIT, VehicleSpeed.key(FOOT_VEHICLE)))
				.addToPriority(Statement.If(walkProfileType.passEncodedValue() + " == false", Statement.Op.MULTIPLY, "0"));
	}
}
