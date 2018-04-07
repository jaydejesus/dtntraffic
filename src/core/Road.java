package core;

import movement.map.MapNode;

public class Road<String, S, E> {
	
	private final S startpoint;
	private final E endpoint;
	private final String roadName;
	private String roadStatus;

	public static <String, S, E> Road<String, S, E> makeRoad(String roadName, S startpoint, E endpoint){
		return new Road<String, S, E>(roadName, startpoint, endpoint);
	}
	
	public Road(String roadName, S startpoint, E endpoint) {
		this.roadName = roadName;
		this.startpoint = startpoint;
		this.endpoint = endpoint;
	}
	
	public S getStartpoint() {
		return this.startpoint;
	}

	public E getEndpoint() {
		return this.endpoint;
	}

	public String getRoadName() {
		return this.roadName;
	}

	public String getRoadStatus() {
		return this.roadStatus;
	}

	public void setRoadStatus(String roadStatus) {
		this.roadStatus = roadStatus;
	}
	
	public boolean isIntersection() {
		MapNode m = (MapNode) this.endpoint;
		if(m.getNeighbors().size() > 2)
			return true;
		return false;
	}
	
	public boolean isOppositeLane(Road r) {
		if(this.startpoint.equals(r.getEndpoint()) && this.endpoint.equals(r.getStartpoint()))
			return true;
		return false;
		
	}
}
