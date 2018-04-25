/* 
 * Copyright 2010 Aalto Universit	y, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.List;
import java.util.Random;

import report.TrafficAppReporter;
import core.Application;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Road;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;

import movement.map.SimMap;
import movement.Path;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;

import java.util.ArrayList;
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
	
	public static final String mTYPE = "type";
	public static final String mLOCATION = "location";
	public static final String mSPEED = "speed";
	public static final String mCURRENT_ROAD = "currentRoad";
	public static final String mCURRENT_ROAD_STATUS = "currentRoadStatus";
	public static final String mTIME_CREATED = "timeCreated";
	public static final String mDISTANCE_TO_FRONTNODE = "distanceToFrontNode";
	
	public static final String HEAVY_TRAFFIC = "HEAVY_TRAFFIC";
	public static final String LIGHT_MODERATE_TRAFFIC = "LIGHT_TO_MODERATE_TRAFFIC";
	
	public static final String FREE_FLOW = "FREE_FLOW";
	public static final String MEDIUM_FLOW = "MEDIUM_FLOW";
	public static final String TRAFFIC_JAM = "MIGHT CAUSE HEAVY TRAFFIC!!!!";

	public static final String LOW = "LOW_DENSITY";
	public static final String MEDIUM = "MEDIUM_DENSITY";
	public static final String HIGH = "HIGH_DENSITY";
	
	private static boolean doneRoadSegmentation = false;
	
	private static double FRESHNESS = 10.0;
	private String currentRoadCondition = "";
	private static final int VEHICLE_SIZE = 2;
	// Private vars
	
	private double	lastAppUpdate = 0;
	private double	appUpdateInterval = 5;
	private double nodeOnRoad;
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private int		appMsgSize=1;
	private Random	rng;
	private List<Message> msgs_list;
	private List<Message> neededMsgs;
	private List<Message> unneededMsgs;
	private HashMap<DTNHost, Message> msgsHash;
	private double averageRoadSpeed;
		
	private List<DTNHost> sameLaneNodes;
	private List<Message> frontNodesMsgs;
	private DijkstraPathFinder alternativePathFinder;
	private boolean limiter = false;
	private int roadCapacity;
	private String roadDensity = "";
	
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
		this.sameLaneNodes = new ArrayList<DTNHost>();
		this.frontNodesMsgs = new ArrayList<Message>();
		this.neededMsgs = new ArrayList<Message>();
		this.msgsHash = new HashMap<DTNHost, Message>();
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
		String type = (String)msg.getProperty(mTYPE);

		String basis = "";
		try {
			 if (type==null) return msg;
			 
				if (type.equalsIgnoreCase("traffic")) {

					if(!this.msgsHash.containsKey(msg.getFrom())) {
						msgsHash.put(msg.getFrom(), msg);
					}
					else {
						Message m = msgsHash.get(msg.getFrom());
						if(msg.getCreationTime() > m.getCreationTime()) {
							msgsHash.put(msg.getFrom(), msg);
						}
					}

					classifyMsgs(msgsHash, host);
					if(getTrafficCondition(this.neededMsgs, host) == TRAFFIC_JAM && !host.toString().startsWith("s")) {
						System.out.println(host + " MUST REROUTE!!!!!");
						getAlternativePath(host.getPreviousDestination(), host.getCurrentDestination(), host.getPath(), host, host.getCurrentSpeed());
					}
					if(this.neededMsgs.size() < 1)
						basis = " based on own speed ";
					else
						basis = " based on " + this.neededMsgs.size() + " same lane nodes ";
					
//					super.sendEventToListeners("TrafficReport", host.getCurrentRoad(), basis, SimClock.getTime(), 
//							getAverageRoadSpeed(this.neededMsgs, host), this.currentRoadCondition, null, host);
					
					this.limiter = false;
				}				
		 }catch(Exception e) {			 
		 }		
		return msg;
	}

	public void classifyMsgs(HashMap<DTNHost, Message> msgs, DTNHost host) {
		this.neededMsgs.clear();

		for(Message m : msgs.values()) {
			if(SimClock.getTime() - m.getCreationTime() > 10.0) {
//				msgs.remove(m.getFrom(), m);
				continue;
			}

			if(host.getCurrentRoad().getRoadName().equals(((Road) m.getProperty(mCURRENT_ROAD)).getRoadName())) {
				this.neededMsgs.add(m);
				this.sameLaneNodes.add(m.getFrom());
			}
		}
	}
	
	private double getAverageRoadSpeed(List<Message> msgs, DTNHost host) {
		this.averageRoadSpeed = host.getCurrentSpeed();
		int nrofhosts = 1;

//		System.out.println(host + " is commputing ave spd of " + msgs.size() + " same lane nodes");
		
		for(Message m : msgs) {
			double spd = (double) m.getProperty(mSPEED);
//			System.out.println(host + " is adding " + m + " of " + m.getFrom() + ": spd=" + spd + " m.speed=" + (double) m.getProperty(mSPEED));
			
			this.averageRoadSpeed += spd;
			nrofhosts++;
		}
//		System.out.println(host + " ave road spd: " + this.averageRoadSpeed/(double)nrofhosts + "nrofsamelanenodes: " + nrofhosts);
		if(nrofhosts > 0)
			this.averageRoadSpeed = this.averageRoadSpeed/(double)nrofhosts;
		else
			this.averageRoadSpeed = host.getCurrentSpeed();
		
		return this.averageRoadSpeed;
	}
	
	private double getAverageFrontNodeDistance(List<Message> msgs, DTNHost host) {
		double averageFrontDistance = 0;
		double frontDistance;
		for(Message m : msgs) {
			frontDistance = (double) m.getProperty(mDISTANCE_TO_FRONTNODE);
			averageFrontDistance = averageFrontDistance + frontDistance;
		}
//		System.out.println(host + " same lane average front distance " + msgs.size() + ": " + averageFrontDistance);
		return (double)averageFrontDistance/msgs.size();
	}

	public double getOppositeLaneNodesAverageFrontDistance(List<DTNHost> hosts) {
		double ave = 0;
		
		for(DTNHost h : hosts) {
//			ave = ave + h.getFrontDistance();
		}
//		System.out.println("Opposite lane Front distance ave" + hosts.size() + ": " + ave);
		return (double)ave/hosts.size();
	}
	
	public double getOppositeLaneAverageSpeed(List<DTNHost> hosts) {
		double oppo_ave = 0;
		
		for(DTNHost h : hosts) {
			oppo_ave = oppo_ave + h.getCurrentSpeed();
		}
		return oppo_ave;
	}
	
	public List<Message> filterFrontNodes(List<Message> msgs, DTNHost host){
		this.frontNodesMsgs.clear();
//		System.out.print(host + " front nodes:");
		for(Message m : msgs) {
			DTNHost h = m.getFrom();
			if(h.getLocation().distance(h.getCurrentDestination()) < host.getLocation().distance(host.getCurrentDestination())) {
//				System.out.print(" " + h);
				this.frontNodesMsgs.add(m);
			}
		}
//		System.out.println();
		return this.frontNodesMsgs;
	}
	
	public int getRoadCapacity(Road r) {
		this.roadCapacity =(int) (((Coord)r.getStartpoint()).distance((Coord)r.getEndpoint()) / VEHICLE_SIZE);
		return this.roadCapacity;
	}
	
	public String getRoadDensity(Road r, int nrOfVehicles) {

		if(nrOfVehicles >= getRoadCapacity(r)/2)
			this.roadDensity = HIGH;
		else if(nrOfVehicles <= getRoadCapacity(r)/4)
			this.roadDensity = LOW;
		else
			this.roadDensity = MEDIUM;
		
		return this.roadDensity;
	}
	
	//changed msgs to consider for local average speed computation. only frontNodes will be considered 
	public String getTrafficCondition(List<Message> msgs, DTNHost host) {
		double ave_speed = getAverageRoadSpeed(filterFrontNodes(msgs, host), host);
		double ave_frontDistance = getAverageFrontNodeDistance(msgs, host);

		if(ave_speed >= 8.0) {
			if(getRoadDensity(host.getCurrentRoad(), msgs.size()).equals(HIGH))
				this.currentRoadCondition = MEDIUM_FLOW;
			else
				this.currentRoadCondition = FREE_FLOW;
		}
		else if(ave_speed <= 0.5) {
			if(getRoadDensity(host.getCurrentRoad(), msgs.size()).equals(HIGH))
				this.currentRoadCondition = TRAFFIC_JAM;
			else if(getRoadDensity(host.getCurrentRoad(), msgs.size()).equals(MEDIUM))
				this.currentRoadCondition = MEDIUM_FLOW;
			else {
				this.currentRoadCondition = FREE_FLOW;
			}
		}
		else {
			if(getRoadDensity(host.getCurrentRoad(), msgs.size()).equals(LOW))
				this.currentRoadCondition = FREE_FLOW;
			else
				this.currentRoadCondition = MEDIUM_FLOW;
		}
		
//		System.out.println(host + "road cap: " + getRoadCapacity(host.getCurrentRoad()) + " averageSpeed: " + ave_speed 
//				+ " density: " + msgs.size() + " " + getRoadDensity(host.getCurrentRoad(), msgs.size()) + " " + this.currentRoadCondition);
		
//		//if not high speed
//		else {
////			if(ave_frontDistance <= 5.0 && msgs.size() > 5) {
////				this.currentRoadCondition = TRAFFIC_JAM;
////			}
//			if(ave_speed <= 0.5 && msgs.size() > 5 && host.getOppositeLaneNodes().size() > 5 && 
//					!host.canOvertake()) {
//				this.currentRoadCondition = TRAFFIC_JAM;
//			}
//			else {
//				this.currentRoadCondition = MEDIUM_FLOW;
//			}
//		}
//		//high speed && low density
//		if(ave_speed > 5.0 && host.getOppositeLaneNodes().size() < 5 && msgs.size() < 5) {
//			this.currentRoadCondition = FREE_FLOW;
//		}
//		//low speed &&
//		else {
//			//dako it opposite lane density pero bulag bulag hira
//			if(ave_frontDistance > 5.0) {
//				this.currentRoadCondition = FREE_FLOW;
//			}
//			//dako it front density ngan dako it opposite lane density ngan dikit dikit masyado plus gutiay average speed;
//			else if(ave_frontDistance <= 5.0 && msgs.size() > 5 && 
//					 host.getOppositeLaneNodes().size() > 5 && getOppositeLaneNodesAverageFrontDistance(host.getOppositeLaneNodes()) <= 5.0) {
//				this.currentRoadCondition = TRAFFIC_JAM;
//			}
//			//dako it opposite lane density pero bulag bulag hira
//			else {
//				this.currentRoadCondition = MEDIUM_FLOW;
//			}
//		}
		
//		System.out.println(host + " road condition: " + this.currentRoadCondition + " ------------" + SimClock.getTime());
//		System.out.println("===============================================================================================");
		return this.currentRoadCondition;
	}

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
//			for(Connection con : host.getConnections()) {
//				if (con.isUp()) {
					if ((curTime - this.lastAppUpdate)% 2.0 == 0) {
						
						// Time to send a new ping
						String id = host + "traffic-" + SimClock.getTime();
//						String idd = host + "traffic" + con.getOtherNode(host);
						
						Message m = new Message(host, null, id, getAppMsgSize());
						m.addProperty(mTYPE, "traffic");
//						m.addProperty("commonID", idd);
						m.addProperty(mLOCATION, host.getLocation());
						m.addProperty(mSPEED, host.getCurrentSpeed());
						m.addProperty(mCURRENT_ROAD, host.getCurrentRoad());
						m.addProperty(mCURRENT_ROAD_STATUS, host.getCurrentRoadStatus());
						m.addProperty(mTIME_CREATED, SimClock.getTime()); //para pagcheck freshness
						m.addProperty(mDISTANCE_TO_FRONTNODE, host.getLocation().distance(host.getFrontNode(host.getSameLaneNodes()).getLocation()));

//						m.setTo(con.getOtherNode(host));
						m.setAppID(APP_ID);
						host.createNewMessage(m);

						super.sendEventToListeners("SentPing", null, host);
						//System.out.println(SimClock.getTime() + " --- " + host + " has sent a msg to connection: " + con.getOtherNode(host) + " @" +m.getCreationTime());
						this.lastAppUpdate = curTime;
						this.limiter = true;
					}
//				}

//			}
			
		}catch(Exception e) {

		}
	}

	private List<DTNHost> getSameLaneNodes() {
		return this.sameLaneNodes;
	}
//reroute path must be based on data/msgs received from other nodes
//subpath (currentDest to endOfPath) must be added to reroute path(prevDest to currentDest)
//an reroute path dre pa fastest.
//kailangan an slowdown asya na para an possible movement han node sakto la based ha iya speed(na mahinay)
//kailangan an basis han fastest path kay an average road speed han mga roads na iya aagian
	
	public Path getAlternativePath(Coord start, Coord destination, Path path, DTNHost host, double speed) {
		this.alternativePathFinder = new DijkstraPathFinder(host.getMovementModel().getOkMapNodeTypes2());
		System.out.println("okmapnodes: " + host.getMovementModel().getOkMapNodeTypes2());
		Path p = new Path(path.getSpeed());
		MapNode s = host.getMovementModel().getMap().getNodeByCoord(start);
		MapNode dest = host.getMovementModel().getMap().getNodeByCoord(destination);
		List<MapNode> altMapNodes = new ArrayList<MapNode>();
		altMapNodes = this.alternativePathFinder.getAlternativePath(s, dest, host.getLocation(), path, host.getCurrentSpeed());
		System.out.println("re: path= " + this.alternativePathFinder.getAlternativePath(s, dest, host.getLocation(), path, host.getCurrentSpeed()));
//		System.out.println("Getting reroute path");
		for(MapNode n : altMapNodes) {
			p.addWaypoint(n.getLocation());
		}

		System.out.println("in app: " + p);
		System.out.println("called host reroute for " + host);
		host.reroute(p);
		System.out.println("done calling host reroute=================================");
		return p;
	}

	//compute travel time of host on current path
	public void computeTravelTime(DTNHost h, Coord from, Path p, double speed) {
		
	}
	
	public void computeTotalTravelTime(){
		
	}
	
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
	
	public List<Message> getMsgsList(){
		return this.msgs_list;
	}

}
