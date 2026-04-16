package com.example.poc.graphhopper.service;

import static com.graphhopper.util.GHUtility.readCountries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.poc.graphhopper.model.GraphImportStats;
import com.example.poc.graphhopper.model.GraphImportDiagnostics;
import com.example.poc.graphhopper.model.RoadSegmentImportLookup;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.ImportUnit;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.PMap;

public class IeumGraphHopper extends GraphHopper {

	private static final Logger log = LoggerFactory.getLogger(IeumGraphHopper.class);

	private final RoadSegmentImportLookup lookup;
	private GraphImportStats lastImportStats = new GraphImportStats(0, 0, 0, 0, Map.of());
	private GraphImportDiagnostics lastImportDiagnostics = new GraphImportDiagnostics(List.of(), Map.of());

	public IeumGraphHopper(RoadSegmentImportLookup lookup) {
		this.lookup = lookup;
	}

	@Override
	protected EncodingManager buildEncodingManager(
			Map<String, PMap> encodedValuesWithProps,
			Map<String, ImportUnit> activeImportUnits,
			Map<String, List<String>> restrictionVehicleTypesByProfile) {
		List<EncodedValue> encodedValues = new ArrayList<>(activeImportUnits.entrySet().stream()
				.map(entry -> {
					Function<PMap, EncodedValue> factory = entry.getValue().getCreateEncodedValue();
					return factory == null ? null : factory.apply(encodedValuesWithProps.getOrDefault(entry.getKey(), new PMap()));
				})
				.filter(Objects::nonNull)
				.toList());
		encodedValues.addAll(createSubnetworkEncodedValues());
		encodedValues.addAll(IeumGraphEncodedValues.createEncodedValues());
		List<String> sortedEncodedValues = getEVSortIndex(getProfiles().stream()
				.collect(java.util.stream.Collectors.toMap(
						com.graphhopper.config.Profile::getName,
						Function.identity(),
						(left, right) -> left,
						java.util.LinkedHashMap::new)));
		encodedValues.sort(Comparator.comparingInt(ev -> sortedEncodedValues.indexOf(ev.getName())));
		EncodingManager.Builder builder = new EncodingManager.Builder();
		encodedValues.forEach(builder::add);
		restrictionVehicleTypesByProfile.entrySet().stream()
				.filter(entry -> !entry.getValue().isEmpty())
				.forEach(entry -> builder.addTurnCostEncodedValue(
						com.graphhopper.routing.ev.TurnRestriction.create(entry.getKey())));
		return builder.build();
	}

	@Override
	protected OSMParsers buildOSMParsers(
			Map<String, PMap> encodedValuesWithProps,
			Map<String, ImportUnit> activeImportUnits,
			Map<String, List<String>> restrictionVehicleTypesByProfile,
			List<String> ignoredHighways) {
		OSMParsers parsers = super.buildOSMParsers(
				encodedValuesWithProps,
				activeImportUnits,
				restrictionVehicleTypesByProfile,
				ignoredHighways);
		parsers.addWayTagParser(createIeumTagParser());
		return parsers;
	}

	@Override
	protected void importOSM() {
		if (getOSMFile() == null) {
			throw new IllegalStateException("OSM file is required for import");
		}
		log.info("start creating ieum graph from {}", getOSMFile());
		AreaIndex<CustomArea> areaIndex = new AreaIndex<>(readCountries());
		BaseGraph baseGraph = getBaseGraph().getBaseGraph();
		OSMReader reader = new IeumOsmReader(baseGraph, getOSMParsers(), getReaderConfig(), lookup)
				.setFile(_getOSMFile())
				.setAreaIndex(areaIndex)
				.setElevationProvider(getElevationProvider())
				.setCountryRuleFactory(getCountryRuleFactory());
		createBaseGraphAndProperties();
		try {
			reader.readGraph();
		} catch (IOException exception) {
			throw new RuntimeException("Cannot read OSM file " + getOSMFile(), exception);
		}
		lastImportStats = ((IeumOsmReader) reader).importStats();
		lastImportDiagnostics = ((IeumOsmReader) reader).importDiagnostics();
		getProperties().put("ieum.lookup.segment_count", lookup.segmentCount());
		getProperties().put("ieum.lookup.way_count", lookup.wayCount());
		getProperties().put("ieum.import.way_count", lastImportStats.importedWayCount());
		getProperties().put("ieum.import.matched_segment_count", lastImportStats.matchedSegmentCount());
		getProperties().put("ieum.import.unmatched_segment_count", lastImportStats.unmatchedSegmentCount());
		getProperties().put("ieum.import.synthetic_barrier_edge_count", lastImportStats.syntheticBarrierEdgeCount());
	}

	public GraphImportStats getLastImportStats() {
		return lastImportStats;
	}

	public GraphImportDiagnostics getLastImportDiagnostics() {
		return lastImportDiagnostics;
	}

	private TagParser createIeumTagParser() {
		return new IeumRoadSegmentTagParser(getEncodingManager());
	}
}
