package com.example.poc.transit.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.example.poc.graphhopper.service.GraphHopperLoadService;
import com.example.poc.graphhopper.service.IeumGraphHopperFactory;
import com.example.poc.network.model.OsmNodePoint;
import com.example.poc.network.util.GeoUtils;
import com.example.poc.route.model.RoutingPolicy;
import com.example.poc.transit.model.DisabilityType;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;

@Service
public class TransitWalkLegRouter {

	private static final Logger log = LoggerFactory.getLogger(TransitWalkLegRouter.class);

	private final ObjectProvider<GraphHopperLoadService> graphHopperLoadServiceProvider;
	private final IeumGraphHopperFactory graphHopperFactory;

	public TransitWalkLegRouter(
			ObjectProvider<GraphHopperLoadService> graphHopperLoadServiceProvider,
			IeumGraphHopperFactory graphHopperFactory) {
		this.graphHopperLoadServiceProvider = graphHopperLoadServiceProvider;
		this.graphHopperFactory = graphHopperFactory;
	}

	public WalkRoutingResult route(TransitWaypoint from, TransitWaypoint to, DisabilityType disabilityType) {
		return route(from, to, disabilityType, RoutingPolicy.SAFE_WALK);
	}

	public WalkRoutingResult route(
			TransitWaypoint from,
			TransitWaypoint to,
			DisabilityType disabilityType,
			RoutingPolicy routingPolicy) {
		GraphHopperLoadService loadService = graphHopperLoadServiceProvider.getIfAvailable();
		String appliedProfile = graphHopperFactory.profileName(disabilityType, routingPolicy);
		String fallbackReason = null;
		if (loadService != null && loadService.isReady()) {
			try {
				GHRequest request = new GHRequest(from.lat(), from.lng(), to.lat(), to.lng())
						.setProfile(appliedProfile);
				GHResponse response = loadService.currentHopper()
						.orElseThrow(() -> new IllegalStateException("graphhopper hopper is not ready"))
						.route(request);
				if (!response.hasErrors()) {
					ResponsePath path = response.getBest();
					return new WalkRoutingResult(
							path.getDistance(),
							Math.max(1, (int) Math.ceil(path.getTime() / 60_000d)),
							routingPolicy.name(),
							appliedProfile,
							true,
							false,
							null,
							toCoordinates(path.getPoints()));
				}
				fallbackReason = response.getErrors().stream()
						.map(Throwable::getMessage)
						.reduce((left, right) -> left + " | " + right)
						.orElse("graphhopper response had errors");
			} catch (Exception exception) {
				fallbackReason = exception.getMessage() == null
						? exception.getClass().getSimpleName()
						: exception.getMessage();
			}
			log.warn(
					"Falling back to synthetic walk route. profile={}, from={},{} to={},{} reason={}",
					appliedProfile,
					from.lat(),
					from.lng(),
					to.lat(),
					to.lng(),
					fallbackReason);
		} else {
			fallbackReason = loadService == null
					? "graphhopper load service is unavailable"
					: "graphhopper artifact is not ready";
			log.warn(
					"Falling back to synthetic walk route. profile={}, from={},{} to={},{} reason={}",
					appliedProfile,
					from.lat(),
					from.lng(),
					to.lat(),
					to.lng(),
					fallbackReason);
		}

		double distance = GeoUtils.distanceMeters(
				new OsmNodePoint(0L, from.lat(), from.lng(), Map.of()),
				new OsmNodePoint(0L, to.lat(), to.lng(), Map.of()));
		double detourFactor = routingPolicy == RoutingPolicy.FAST_WALK ? 1.04d : 1.12d;
		double adjustedDistance = distance * detourFactor;
		double metersPerMinute = routingPolicy == RoutingPolicy.FAST_WALK ? 78d : 67d;
		int durationMinute = Math.max(1, (int) Math.ceil(adjustedDistance / metersPerMinute));
		return new WalkRoutingResult(
				adjustedDistance,
				durationMinute,
				routingPolicy.name(),
				appliedProfile,
				false,
				true,
				fallbackReason,
				fallbackCoordinates(from, to, routingPolicy));
	}

	private List<List<Double>> toCoordinates(PointList pointList) {
		List<List<Double>> coordinates = new ArrayList<>();
		for (int index = 0; index < pointList.size(); index++) {
			coordinates.add(List.of(pointList.getLon(index), pointList.getLat(index)));
		}
		return coordinates;
	}

	private List<List<Double>> fallbackCoordinates(
			TransitWaypoint from,
			TransitWaypoint to,
			RoutingPolicy routingPolicy) {
		if (routingPolicy == RoutingPolicy.FAST_WALK) {
			double midLat = (from.lat() + to.lat()) / 2d;
			double midLng = (from.lng() + to.lng()) / 2d;
			double offsetLat = (to.lng() - from.lng()) * 0.03d;
			double offsetLng = (from.lat() - to.lat()) * 0.03d;
			return List.of(
					List.of(from.lng(), from.lat()),
					List.of(midLng + offsetLng, midLat + offsetLat),
					List.of(to.lng(), to.lat()));
		}
		return List.of(
				List.of(from.lng(), from.lat()),
				List.of(to.lng(), to.lat()));
	}

	public record WalkRoutingResult(
			double distanceMeter,
			int durationMinute,
			String walkPolicy,
			String appliedProfile,
			boolean graphHopperBacked,
			boolean fallbackUsed,
			String fallbackReason,
			List<List<Double>> coordinates) {
	}
}
