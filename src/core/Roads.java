package core;

import java.awt.geom.Line2D;
import java.util.HashMap;
import java.util.List;

import movement.map.MapNode;

public class Roads {

	private static HashMap<Coord, List<Coord>> segmentsHashMap;
	
	private Coord endpoint1;
	private Coord endpoint2;
	private List<MapNode> mapnodes;
	private List<Coord> roadSegments;
	
	public Roads(List<MapNode> mapnodes) {
		this.mapnodes = mapnodes;
		initRoads();
	}

	private void initRoads() {
		Coord c, c2;
		for(MapNode n : this.mapnodes) {
			c = n.getLocation();
			for(MapNode n2 : n.getNeighbors()) {
				c2 = n2.getLocation();
				this.roadSegments.add(c2);
				
			}
			this.segmentsHashMap.put(c, this.roadSegments);
		}
	}
	
	public Line2D getMyRoad(Coord location, Coord waypoint) {
		Line2D road = null;
		
		
		if(!this.segmentsHashMap.get(waypoint).equals(null)) {
			{
				for(Coord c : this.segmentsHashMap.get(waypoint)) {
					//myRoad = new Line2D.Double(round(waypoint.getX()), round(waypoint.getY()), round(c.getX()), round(c.getY()));
					road = new Line2D.Double(waypoint.getX(), waypoint.getY(), c.getX(), c.getY());
					if(isOnTheRoad(road, location)) {
						return road;
					}
				}
			}
			
		}
		return road;
	}
	
	public boolean isOnTheRoad(Line2D l, Coord c) {
		double x1, x2, y1, y2;

		x1 = l.getX1();
		x2 = l.getX2();
		y1 = l.getY1();
		y2 = l.getY2();

		double m = (y2-y1)/(x2-x1);
		double b = y1 - (m * x1);
		if(round(c.getY()) == round((m * c.getX())+b)) {
			return true;
		}
		else {
			return false;
		}
	}

	public double round(double value) {
		return (double)Math.round(value * 100)/100;
	}
}
