/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.List;
import java.util.Map;
import java.awt.geom.Line2D;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Random;

import report.TrafficAppReporter;
import core.Application;
import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Road;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;

import gui.playfield.MapGraphic;
import movement.map.SimMap;
import movement.Path;
import movement.map.MapNode;
import java.util.HashMap;

/**
 * Simple ping application to demonstrate the application support. The 
 * application can be configured to send pings with a fixed interval or to only
 * answer to pings it receives. When the application receives a ping it sends
 * a pong message in response.
 * 
 * The corresponding <code>TrafficAppReporter</code> class can be used to record
 * information about the application behavior.
 * 
 * @see TrafficAppReporter
 * @author teemuk
 */
public class TrafficApp extends Application{
	/** Ping generation interval */
	public static final String TRAFFIC_INTERVAL = "interval";
	/** Ping interval offset - avoids synchronization of ping sending */
	public static final String TRAFFIC_OFFSET = "offset";
	/** Destination address range - inclusive lower, exclusive upper */
	public static final String TRAFFIC_DEST_RANGE = "destinationRange";
	/** Seed for the app's random number generator */
	public static final String TRAFFIC_SEED = "seed";
	/** Size of the ping message */
	public static final String TRAFFIC_MESSAGE_SIZE = "pingSize";
	
	/** Application ID */
	public static final String APP_ID = "fi.tkk.netlab.TrafficApp";
	
	public static String TRAFFIC_HEAVY = "heavyTraffic";
	public static String TRAFFIC_MEDIUM = "mediumTraffic";
	public static String TRAFFIC_LIGHT = "lightTraffic";
	private static boolean doneRoadSegmentation = false;
	// Private vars
	private double average = 0;
	
	private double	lastAppUpdate = 0;
	private double	appUpdateInterval = 5;
	private double nodeOnRoad;
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private int		appMsgSize=1;
	private Random	rng;
	private List<Message> msgs_list;
	
	private Line2D myRoadSegment;
	private List<Coord> road_segments;
	private static HashMap<Coord, List<Coord>> segmentsHashMap;
	
	
	/** 
	 * Creates a new ping application with the given settings.
	 * 
	 * @param s	Settings to use for initializing the application.
	 */
	public TrafficApp(Settings s) {
		if (s.contains(TRAFFIC_INTERVAL)){
			this.appUpdateInterval = s.getDouble(TRAFFIC_INTERVAL);
		}
		if (s.contains(TRAFFIC_OFFSET)){
			this.lastAppUpdate = s.getDouble(TRAFFIC_OFFSET);
		}
		if (s.contains(TRAFFIC_SEED)){
			this.seed = s.getInt(TRAFFIC_SEED);
		}
		if (s.contains(TRAFFIC_MESSAGE_SIZE)) {
			this.appMsgSize = s.getInt(TRAFFIC_MESSAGE_SIZE);
		}
		if (s.contains(TRAFFIC_DEST_RANGE)){
			int[] destination = s.getCsvInts(TRAFFIC_DEST_RANGE,2);
			this.destMin = destination[0];
			this.destMax = destination[1];
		}
		
		rng = new Random(this.seed);
		super.setAppID(APP_ID);
	}	
	
	/** 
	 * Copy-constructor
	 * 
	 * @param a
	 */
	public TrafficApp(TrafficApp a) {
		super(a);
		this.lastAppUpdate = a.getLastPing();
		this.appUpdateInterval = a.getInterval();
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.seed = a.getSeed();
		this.appMsgSize = a.getAppMsgSize();
		this.rng = new Random(this.seed);
		this.msgs_list = new ArrayList<Message>();
		this.segmentsHashMap = new HashMap<Coord, List<Coord>>();
		this.road_segments = new ArrayList<Coord>();
	}
	
	/** 
	 * Handles an incoming message. If the message is a ping message replies
	 * with a pong message. Generates events for ping and pong messages.
	 * 
	 * @param msg	message received by the router
	 * @param host	host to which the application instance is attached
	 */
	@Override
	public Message handle(Message msg, DTNHost host) {
		String type = (String)msg.getProperty("type");

		try {
			 if (type==null) return msg;
				if (msg.getTo()==host && type.equalsIgnoreCase("traffic")) {
//					System.out.println(SimClock.getTime() + " --- " + host + " received " + msg.getFrom() + "'s message");
					if(msgs_list!=null) {
							for(Message mm : msgs_list) {
								//System.out.println("msg: " + msg.getFrom() + " mm: " + mm.getFrom());
								if(msg.getFrom().equals(mm.getFrom()))
									
									msgs_list.remove(mm);
							}
						
					}
					
					msgs_list.add(msg);

					getTrafficCondition(msgs_list, host);
					
					
				}				
		 }catch(Exception e) {			 
		 }		
		return msg;
	}

	public double getTrafficCondition(List<Message> msgs, DTNHost host) {
		this.average = 0;
		double oppAve = 0;
		this.nodeOnRoad = 0;
		int oppCtr = 0;
		String condition = "";
		
		for(Message m : msgs) {
			Road other = (Road) m.getProperty("currentRoad");
			if(host.getCurrentRoad().getRoadName().equals(other.getRoadName())) {
				this.average = this.average + (double) m.getProperty("speed");
					this.nodeOnRoad++;
			}
		}
		this.average = this.average/this.nodeOnRoad;
		if(this.average <= 5) {
			condition = this.TRAFFIC_HEAVY;
//			System.out.println(host + " in " + host.getLocation() + ". HEAVY TRAFFIC in " + host.getCurrentRoad().getRoadName());
			if(host.toString().startsWith("n")) {
				getAlternativePath(host.getPreviousDestination(), host.getCurrentPathDestination(), host.getPath());
//				host.setRerouteWaypoint(host.getPreviousDestination());
			}
		}
		else if(this.average > 10 && this.average <= 8)
			condition = this.TRAFFIC_MEDIUM;
		else
			condition = this.TRAFFIC_LIGHT;
		
		if(this.nodeOnRoad > 0) {
			System.out.println(host + " local average on " + host.getCurrentRoad().getRoadName() + " with " + this.nodeOnRoad + " out of " + msgs.size() + " node/s : " + this.average + " " + condition);
			super.sendEventToListeners("ToReporter", host.getCurrentRoad().getRoadName(), SimClock.getTime(), this.average, condition, this, host);
		}
		
		return this.average;
	}

//	private boolean isInSameRoad(Line2D mine, Line2D l) {
//		if(round(l.getX1()) == round(mine.getX1()) && round(l.getY1()) == round(mine.getY1()) 
//				&& round(l.getX2()) == round(mine.getX2())&& round(l.getY2()) == round(mine.getY2())) { 
//			return true;
//		}
//		else
//			return false;
//	}
//
//	public void makeTraffic(List<Message> mlist, DTNHost h) {
//		for(Connection con : h.getConnections()) {
//			if(h.getCurrentDestination() == con.getOtherNode(h).getCurrentDestination()) {
////				if(isInSameRoad(getMyRoadSegment(), ))
//			}
//		}
//	}

	/** 
	 * Draws a random host from the destination range
	 * 
	 * @return host
	 */
	private DTNHost randomHost() {
		int destaddr = 0;
		if (destMax == destMin) {
			destaddr = destMin;
		}
		destaddr = destMin + rng.nextInt(destMax - destMin);
		World w = SimScenario.getInstance().getWorld();
		return w.getNodeByAddress(destaddr);
	}
	
	@Override
	public Application replicate() {
		return new TrafficApp(this);
	}

	@Override
	public void update(DTNHost host) {
		
		double curTime = SimClock.getTime();

		try {
			for(Connection con : host.getConnections()) {
				
				if (con.isUp()) {

					if ((curTime - this.lastAppUpdate)% 2.0 == 0) {
						
						// Time to send a new ping
						String id = "traffic";
						String idd = SimClock.getIntTime() + "-" + host+" "+host.getCurrentRoad().getRoadName()+": " + host.getCurrentSpeed();
						
						Message m = new Message(host, con.getOtherNode(host), id+idd, getAppMsgSize());
						m.addProperty("type", id);
						m.addProperty("location", host.getLocation());
						m.addProperty("speed", host.getCurrentSpeed());
						m.addProperty("currentRoad", host.getCurrentRoad());
//						m.addProperty("currentRoadStatus", host.getCurrentRoadStatus());
						m.setAppID(APP_ID);
						host.createNewMessage(m);

						super.sendEventToListeners("SentPing", null, host);
						//System.out.println(SimClock.getTime() + " --- " + host + " has sent a msg to connection: " + con.getOtherNode(host) + " @" +m.getCreationTime());
						this.lastAppUpdate = curTime;
					}
				}

			}
			
		}catch(Exception e) {

		}
	}
	
	public void getAlternativePath(Coord start, Coord destination, Path path) {
		System.out.println("Rerouting -- starting from: " + start + " to destination: " + destination);
//		System.out.println("Path: " + path);
	}

	//compute travel time of host on current path
	public void computeTravelTime(DTNHost h, Coord from, Path p, double speed) {
		
	}
	
//	public boolean isOnTheLine(Line2D l, Coord c) {
//		double x1, x2, y1, y2;
//
//		x1 = l.getX1();
//		x2 = l.getX2();
//		y1 = l.getY1();
//		y2 = l.getY2();
//
//		double m = (y2-y1)/(x2-x1);
//		double b = y1 - (m * x1);
//		if(round(c.getY()) == round((m * c.getX())+b)) {
//			return true;
//		}
//		else {
//			return false;
//		}
//	}
	
//	public Line2D getMyRoadSegment() {
//		return this.myRoadSegment;
//	}
//	
//	public Line2D getMyRoad(DTNHost h, Coord location, Coord waypoint) {
//		Line2D myRoad = null;
//		
//		if(!this.segmentsHashMap.get(waypoint).equals(null)) {
//			{
//				for(Coord c : this.segmentsHashMap.get(waypoint)) {
//					//myRoad = new Line2D.Double(round(waypoint.getX()), round(waypoint.getY()), round(c.getX()), round(c.getY()));
//					myRoad = new Line2D.Double(waypoint.getX(), waypoint.getY(), c.getX(), c.getY());
//					if(isOnTheLine(myRoad, location)) {
//						break;
//					}
//				}
//			}
//			
//		}
//		this.myRoadSegment = myRoad;
//		return this.myRoadSegment;
//	}
//	
//	public void initRoadSegments() throws IOException{
//		Coord c, c2;
//		
//		for(MapNode n : SimScenario.getInstance().getMap().getNodes()) {
//			c = n.getLocation();
//			this.road_segments = new ArrayList<Coord>();
//			for(MapNode n2 : n.getNeighbors()) {
//				
//				c2 = n2.getLocation();
//				this.road_segments.add(c2);
//			}
//			this.segmentsHashMap.put(c, this.road_segments);
//		}
//		this.doneRoadSegmentation = true;
//	}
	
	public double round(double value) {
		return (double)Math.round(value * 100)/100;
	}

	
	/**
	 * @return the lastPing
	 */
	public double getLastPing() {
		return lastAppUpdate;
	}

	/**
	 * @param lastPing the lastPing to set
	 */
	public void setLastPing(double lastPing) {
		this.lastAppUpdate = lastPing;
	}

	/**
	 * @return the interval
	 */
	public double getInterval() {
		return appUpdateInterval;
	}

	/**
	 * @param interval the interval to set
	 */
	public void setInterval(double interval) {
		this.appUpdateInterval = interval;
	}

	/**
	 * @return the destMin
	 */
	public int getDestMin() {
		return destMin;
	}

	/**
	 * @param destMin the destMin to set
	 */
	public void setDestMin(int destMin) {
		this.destMin = destMin;
	}

	/**
	 * @return the destMax
	 */
	public int getDestMax() {
		return destMax;
	}

	/**
	 * @param destMax the destMax to set
	 */
	public void setDestMax(int destMax) {
		this.destMax = destMax;
	}

	/**
	 * @return the seed
	 */
	public int getSeed() {
		return seed;
	}

	/**
	 * @param seed the seed to set
	 */
	public void setSeed(int seed) {
		this.seed = seed;
	}

	/**
	 * @return the pingSize
	 */
	public int getAppMsgSize() {
		return this.appMsgSize;
	}

	/**
	 * @param pingSize the pingSize to set
	 */
	public void setPingSize(int size) {
		this.appMsgSize = size;
	}

}
