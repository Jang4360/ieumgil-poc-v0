package com.example.poc.graphhopper.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.poc.graphhopper.model.RoadSegmentImportLookup;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.util.CustomModel;

@Component
public class IeumGraphHopperFactory {

	private static final String FOOT_PROFILE = "foot";

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

	private IeumGraphHopper baseConfiguredHopper(Path graphDirectory, RoadSegmentImportLookup lookup) {
		IeumGraphHopper hopper = new IeumGraphHopper(lookup);
		hopper.setGraphHopperLocation(graphDirectory.toString());
		hopper.setProfiles(new Profile(FOOT_PROFILE)
				.setWeighting(CustomWeighting.NAME)
				.setCustomModel(baseCustomModel()));
		hopper.setEncodedValuesString(String.join(", ", List.of(
				VehicleAccess.key(FOOT_PROFILE),
				VehicleSpeed.key(FOOT_PROFILE),
				Roundabout.KEY,
				RoadClass.KEY,
				RoadEnvironment.KEY)));
		return hopper;
	}

	public String profileName() {
		return FOOT_PROFILE;
	}

	private CustomModel baseCustomModel() {
		return new CustomModel()
				.addToSpeed(Statement.If("true", Statement.Op.LIMIT, VehicleSpeed.key(FOOT_PROFILE)));
	}
}
