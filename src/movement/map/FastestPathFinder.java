/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement.map;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import core.Coord;

/**
 * Implementation of the Dijkstra's shortest path algorithm.
 */
public class FastestPathFinder {
	/** Value for infinite distance  */
	private static final Double INFINITY = Double.MAX_VALUE;
	/** Initial size of the priority queue */
	private static final int PQ_INIT_SIZE = 11;

	/** Map of node travel times (based on path speed) from the source node */
	private TravelTimeMap travelTimes;
	/** Set of already visited nodes (where the shortest path is known) */
	private Set<MapNode> visited;
	/** Priority queue of unvisited nodes discovered so far */
	private Queue<MapNode> unvisited;
	/** Map of previous nodes on the shortest path(s) */
	private Map<MapNode, MapNode> prevNodes;

	private int [] okMapNodes;

	/**
	 * Constructor.
	 * @param okMapNodes The map node types that are OK for paths or null if
	 * all nodes are OK
	 */
	public FastestPathFinder(int [] okMapNodes) {
		super();
		this.okMapNodes = okMapNodes;
	}

	/**
	 * Initializes a new search with a source node
	 * @param node The path's source node
	 */
	private void initWith(MapNode node) {
		assert (okMapNodes != null ? node.isType(okMapNodes) : true);

		// create needed data structures
		this.unvisited = new PriorityQueue<MapNode>(PQ_INIT_SIZE,
				new TravelTimeComparator());
		this.visited = new HashSet<MapNode>();
		this.prevNodes = new HashMap<MapNode, MapNode>();
		this.travelTimes = new TravelTimeMap();

		// set traveltime to source 0 and initialize unvisited queue
		this.travelTimes.put(node, 0);
		this.unvisited.add(node);
	}
	/**
	 * Relaxes the neighbors of a node (updates the shortest travelTimes).
	 * @param node The node whose neighbors are relaxed
	 */
	private void relax(MapNode node, MapNode prevDest, MapNode currDest, double pathSpeed) {
		double nodeTravTime = travelTimes.get(node);
		for (MapNode n : node.getNeighbors()) {
			if(node == prevDest && n == currDest) {
				continue; //skip current traffic
			}
			if (visited.contains(n)) {
				continue; // skip visited nodes
			}

			if (okMapNodes != null && !n.isType(okMapNodes)) {
				continue; // skip nodes that are not OK
			}

			// n node's traveltime from path's source node
			double nTravTime = nodeTravTime + getTravelTime(node, n, pathSpeed);

			if (travelTimes.get(n) > nTravTime) { // stored distance > found dist?
				prevNodes.put(n, node);
				setTravelTime(n, nTravTime);
			}
		}
	}

	/**
	 * Sets the distance from source node to a node
	 * @param n The node whose distance is set
	 * @param distance The distance of the node from the source node
	 */
	private void setTravelTime(MapNode n, double travelTime) {
		unvisited.remove(n); // remove node from old place in the queue
		travelTimes.put(n, travelTime); // update travel time
		unvisited.add(n); // insert node to the new place in the queue
	}

	/**
	 * Returns the travel time = (euclidean)distance/speed between the two map nodes
	 * @param from The first node
	 * @param to The second node
	 * @return Euclidean distance / speed between the two map nodes
	 */
	private double getTravelTime(MapNode from, MapNode to, double speed) {
		return from.getLocation().distance(to.getLocation())/speed;
	}
	
	/**
	 * Returns the travel time = (euclidean)distance/speed between the two map nodes
	 * @param currLocation The current location of the host upon method call
	 * @param prevDest The previous destination, also the rerouting start point
	 * @param currDest The current destination of the host
	 * @param finalDest The final destination of the host
	 * @param slowSpeed The speed based on the traffic
	 * @param pathSpeed The speed to be used as basis for rerouting
	 * @param contPathCoords The coordinates ahead of the current path 
	 * @return Resulting fastest route based on algorithm calculation/estimation 
	 */
	public List<MapNode> getAlternativePath(MapNode prevDest, MapNode currDest, MapNode finalDest, Coord currLocation, double slowSpeed, 
			double pathSpeed, List<Coord> contPathCoords) {
		List<MapNode> path = new LinkedList<MapNode>();

		double rerouteTravTime = currLocation.distance(prevDest.getLocation())/pathSpeed;
		double currentPathTravTime = currLocation.distance(currDest.getLocation())/slowSpeed;
		
		if (prevDest.compareTo(finalDest) == 0) { // source and destination are the same
			path.add(prevDest); // return a list containing only source node
			return path;
		}

		initWith(prevDest);
		MapNode node = null;

		// always take the node with shortest distance
		while ((node = unvisited.poll()) != null) {
			if (node == finalDest) {
				break; // we found the destination -> no need to search further
			}

			visited.add(node); // mark the node as visited
			relax(node, prevDest, currDest, pathSpeed); // add/update neighbor nodes' travelTimes
		}

		// now we either have the path or such path wasn't available
		if (node == finalDest) { // found a path
			path.add(0,finalDest);
			MapNode prev = prevNodes.get(finalDest);
			while (prev != prevDest) {
				path.add(0, prev);	// always put previous node to beginning
				prev = prevNodes.get(prev);
			}

			path.add(0, prevDest); // finally put the source node to first node
		}
		System.out.println("reroute path: " + path);
		
		System.out.println("reroute paaaath traveltimes");
		for(int i = 0; i < path.size()-1; i++) {
			Coord c1 = path.get(i).getLocation();
			Coord c2 = path.get(i+1).getLocation();
//			System.out.println("from " + c1 + " to " + c2 + " : " + (c1.distance(c2)/pathSpeed));
			rerouteTravTime = rerouteTravTime + (c1.distance(c2)/pathSpeed);
		}
		System.out.println("reroute path total travel time : " + rerouteTravTime);
		System.out.println("current paaaath traveltimes");
		for(int i = 0; i < contPathCoords.size()-1; i++) {
			Coord c1 = contPathCoords.get(i);
			Coord c2 = contPathCoords.get(i+1);
//			System.out.println("from " + c1 + " to " + c2 + " : " + (c1.distance(c2)/pathSpeed));
			currentPathTravTime = currentPathTravTime + (c1.distance(c2)/pathSpeed);
		}
		System.out.println("current path total travel time : " + currentPathTravTime);
		if(rerouteTravTime > currentPathTravTime)
			return null;
		return path;
	}
	

	/**
	 * Comparator that compares two map nodes by their distance from
	 * the source node.
	 */
	private class TravelTimeComparator implements Comparator<MapNode> {

		/**
		 * Compares two map nodes by their distance from the source node
		 * @return -1, 0 or 1 if node1's distance is smaller, equal to, or
		 * bigger than node2's distance
		 */
		public int compare(MapNode node1, MapNode node2) {
			double dist1 = travelTimes.get(node1);
			double dist2 = travelTimes.get(node2);

			if (dist1 > dist2) {
				return 1;
			}
			else if (dist1 < dist2) {
				return -1;
			}
			else {
				return node1.compareTo(node2);
			}
		}
	}

	/**
	 * Simple Map implementation for storing travelTimes.
	 */
	private class TravelTimeMap {
		private HashMap<MapNode, Double> map;

		/**
		 * Constructor. Creates an empty distance map
		 */
		public TravelTimeMap() {
			this.map = new HashMap<MapNode, Double>();
		}

		/**
		 * Returns the distance to a node. If no distance value
		 * is found, returns {@link FastestPathFinder#INFINITY} as the value.
		 * @param node The node whose distance is requested
		 * @return The distance to that node
		 */
		public double get(MapNode node) {
			Double value = map.get(node);
			if (value != null) {
				return value;
			}
			else {
				return INFINITY;
			}
		}

		/**
		 * Puts a new distance value for a map node
		 * @param node The node
		 * @param distance Distance to that node
		 */
		public void  put(MapNode node, double travelTime) {
			map.put(node, travelTime);
		}

		/**
		 * Returns a string representation of the map's contents
		 * @return a string representation of the map's contents
		 */
		public String toString() {
			return map.toString();
		}
	}
}