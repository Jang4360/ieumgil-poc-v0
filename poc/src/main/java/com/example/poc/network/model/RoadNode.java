package com.example.poc.network.model;

public record RoadNode(
		long vertexId,
		long osmNodeId,
		String point) {

	public static RoadNode fromPoint(long vertexId, OsmNodePoint point, String pointWkt) {
		return new RoadNode(vertexId, point.nodeId(), pointWkt);
	}
}
