/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import static core.Constants.DEBUG;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import movement.MapBasedMovement;
import movement.MovementModel;
import movement.Path;
import routing.MessageRouter;
import routing.util.RoutingInfo;

/**
 * A DTN capable host.
 */
public class DTNHost implements Comparable<DTNHost> {
	private static final double SAFE_OVERTAKE_DISTANCE = 50;
	private static final double FRONT_DISTANCE = 5;
	private static int nextAddress = 0;
	private int address;

	private Coord location; 	// where is the host
	private Coord destination;	// where is it going
	private Coord prevDestination;
	
	
	private MessageRouter router;
	private MovementModel movement;
	private Path path;
	private List<Coord> subpath;
	private double speed;
	private double nextTimeToMove;
	private String name;
	private List<MessageListener> msgListeners;
	private List<MovementListener> movListeners;
	private List<NetworkInterface> net;
	private ModuleCommunicationBus comBus;
	
	private Road currentRoad;
	private List<DTNHost> otherNodesOnRoad;
	private List<DTNHost> oppositeLane;
	private List<String> pathRoads; 
	private HashMap<String, Road> roads;
	private boolean canOvertakeStatus;
	private int pathIndex;

	private double frontDistance;
	static {
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}
	/**
	 * Creates a new DTNHost.
	 * @param msgLs Message listeners
	 * @param movLs Movement listeners
	 * @param groupId GroupID of this host
	 * @param interf List of NetworkInterfaces for the class
	 * @param comBus Module communication bus object
	 * @param mmProto Prototype of the movement model of this host
	 * @param mRouterProto Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs,
			List<MovementListener> movLs,
			String groupId, List<NetworkInterface> interf,
			ModuleCommunicationBus comBus,
			MovementModel mmProto, MessageRouter mRouterProto) {
		this.comBus = comBus;
		this.location = new Coord(0,0);
		this.address = getNextAddress();
		this.name = groupId+address;
		this.net = new ArrayList<NetworkInterface>();

		for (NetworkInterface i : interf) {
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}

		// TODO - think about the names of the interfaces and the nodes
		//this.name = groupId + ((NetworkInterface)net.get(1)).getAddress();

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		this.movement.setHost(this);
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();

		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;
		this.subpath = null;
		this.otherNodesOnRoad = new ArrayList<DTNHost>();
		this.oppositeLane = new ArrayList<DTNHost>();
		this.pathRoads = new ArrayList<String>();
		this.roads = new HashMap<String, Road>();
		this.subpath = new ArrayList<Coord>();
		
		if (movLs != null) { // inform movement listeners about the location
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}
	}

	/**
	 * Returns a new network interface address and increments the address for
	 * subsequent calls.
	 * @return The next address.
	 */
	private synchronized static int getNextAddress() {
		return nextAddress++;
	}

	/**
	 * Reset the host and its interfaces
	 */
	public static void reset() {
		nextAddress = 0;
	}

	public MapBasedMovement getMovementModel() {
		return (MapBasedMovement) this.movement;
	}
	
	/**
	 * Returns true if this node is actively moving (false if not)
	 * @return true if this node is actively moving (false if not)
	 */
	public boolean isMovementActive() {
		return this.movement.isActive();
	}

	/**
	 * Returns true if this node's radio is active (false if not)
	 * @return true if this node's radio is active (false if not)
	 */
	public boolean isRadioActive() {
		// Radio is active if any of the network interfaces are active.
		for (final NetworkInterface i : this.net) {
			if (i.isActive()) return true;
		}
		return false;
	}

	/**
	 * Set a router for this host
	 * @param router The router to set
	 */
	private void setRouter(MessageRouter router) {
		router.init(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router of this host
	 * @return the router of this host
	 */
	public MessageRouter getRouter() {
		return this.router;
	}

	/**
	 * Returns the network-layer address of this host.
	 */
	public int getAddress() {
		return this.address;
	}

	/**
	 * Returns this hosts's ModuleCommunicationBus
	 * @return this hosts's ModuleCommunicationBus
	 */
	public ModuleCommunicationBus getComBus() {
		return this.comBus;
	}

    /**
	 * Informs the router of this host about state change in a connection
	 * object.
	 * @param con  The connection object whose state changed
	 */
	public void connectionUp(Connection con) {
		this.router.changedConnection(con);
	}

	public void connectionDown(Connection con) {
		this.router.changedConnection(con);
	}

	/**
	 * Returns a copy of the list of connections this host has with other hosts
	 * @return a copy of the list of connections this host has with other hosts
	 */
	public List<Connection> getConnections() {
		List<Connection> lc = new ArrayList<Connection>();

		for (NetworkInterface i : net) {
			lc.addAll(i.getConnections());
		}

		return lc;
	}

	/**
	 * Returns the current location of this host.
	 * @return The location
	 */
	public Coord getLocation() {
		return this.location;
	}

	/**
	 * Returns the Path this node is currently traveling or null if no
	 * path is in use at the moment.
	 * @return The path this node is traveling
	 */
	public Path getPath() {
		return this.path;
	}
	
	public List<Coord> getSubpath(){
		return this.subpath;
	}

	/**
	 * Sets the Node's location overriding any location set by movement model
	 * @param location The location to set
	 */
	public void setLocation(Coord location) {
		this.location = location.clone();
	}

	/**
	 * Sets the Node's name overriding the default name (groupId + netAddress)
	 * @param name The name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the messages in a collection.
	 * @return Messages in a collection
	 */
	public Collection<Message> getMessageCollection() {
		return this.router.getMessageCollection();
	}

	/**
	 * Returns the number of messages this node is carrying.
	 * @return How many messages the node is carrying currently.
	 */
	public int getNrofMessages() {
		return this.router.getNrofMessages();
	}

	/**
	 * Returns the buffer occupancy percentage. Occupancy is 0 for empty
	 * buffer but can be over 100 if a created message is bigger than buffer
	 * space that could be freed.
	 * @return Buffer occupancy percentage
	 */
	public double getBufferOccupancy() {
		long bSize = router.getBufferSize();
		long freeBuffer = router.getFreeBufferSize();
		return 100*((bSize-freeBuffer)/(bSize * 1.0));
	}

	/**
	 * Returns routing info of this host's router.
	 * @return The routing info.
	 */
	public RoutingInfo getRoutingInfo() {
		return this.router.getRoutingInfo();
	}

	/**
	 * Returns the interface objects of the node
	 */
	public List<NetworkInterface> getInterfaces() {
		return net;
	}

	/**
	 * Find the network interface based on the index
	 */
	public NetworkInterface getInterface(int interfaceNo) {
		NetworkInterface ni = null;
		try {
			ni = net.get(interfaceNo-1);
		} catch (IndexOutOfBoundsException ex) {
			throw new SimError("No such interface: "+interfaceNo +
					" at " + this);
		}
		return ni;
	}

	/**
	 * Find the network interface based on the interfacetype
	 */
	protected NetworkInterface getInterface(String interfacetype) {
		for (NetworkInterface ni : net) {
			if (ni.getInterfaceType().equals(interfacetype)) {
				return ni;
			}
		}
		return null;
	}

	/**
	 * Force a connection event
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId,
			boolean up) {
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null) {
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			assert (ni != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
			assert (no != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
		} else {
			ni = getInterface(1);
			no = anotherHost.getInterface(1);

			assert (ni.getInterfaceType().equals(no.getInterfaceType())) :
				"Interface types do not match.  Please specify interface type explicitly";
		}

		if (up) {
			ni.createConnection(no);
		} else {
			ni.destroyConnection(no);
		}
	}

	/**
	 * for tests only --- do not use!!!
	 */
	public void connect(DTNHost h) {
		if (DEBUG) Debug.p("WARNING: using deprecated DTNHost.connect" +
			"(DTNHost) Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h,null,true);
	}

	/**
	 * Updates node's network layer and router.
	 * @param simulateConnections Should network layer be updated too
	 */
	public void update(boolean simulateConnections) {
		if (!isRadioActive()) {
			// Make sure inactive nodes don't have connections
			tearDownAllConnections();
			return;
		}

		if (simulateConnections) {
			for (NetworkInterface i : net) {
				i.update();
			}
		}
		this.router.update();
	}

	/**
	 * Tears down all connections for this host.
	 */
	private void tearDownAllConnections() {
		for (NetworkInterface i : net) {
			// Get all connections for the interface
			List<Connection> conns = i.getConnections();
			if (conns.size() == 0) continue;

			// Destroy all connections
			List<NetworkInterface> removeList =
				new ArrayList<NetworkInterface>(conns.size());
			for (Connection con : conns) {
				removeList.add(con.getOtherInterface(i));
			}
			for (NetworkInterface inf : removeList) {
				i.destroyConnection(inf);
			}
		}
	}

	/**
	 * Moves the node towards the next waypoint or waits if it is
	 * not time to move yet
	 * @param timeIncrement How long time the node moves
	 */
	public void move(double timeIncrement) {
		double possibleMovement, posMov;
		double distance;
		double frontDistance;
		double dx, dy;
		DTNHost frontNode;

		if (!isMovementActive() || SimClock.getTime() < this.nextTimeToMove) {
			return;
		}
		if (this.destination == null) {
			if (!setNextWaypoint()) {
				System.out.println("DTNHost-null destination...->setNextWaypoint");
				return;
			}
		}
		//get nodes on host's current road (same and opposite lane)
		this.getOtherNodesOnMyRoad();
		
		//get current node infront of the host
		frontNode = this.getFrontNode(getSameLaneNodes());
		
		if(frontNode != null) {
			//checks host's distance from its front node if there is a node a infront and applies slowdown() if it needs to 
			frontDistance = this.checkFrontDistance(frontNode);
			
			double temp = frontDistance - (frontDistance * 0.75);	
			posMov = temp;
			if(temp <= 10.0 && !this.canOvertake(getOppositeLaneNodes(), frontNode))
				this.slowDown(frontNode);
			//check if host can overtake overtake frontNode
			else if(this.canOvertake(getOppositeLaneNodes(), frontNode) && this.getLocation().distance(frontNode.getLocation()) < FRONT_DISTANCE) {
				overtake();
				posMov = timeIncrement * speed;
			}
			possibleMovement = timeIncrement * speed;
		}
		else {
			if(frontNode == null && this.canOvertake(getOppositeLaneNodes(), frontNode))
				overtake();
			possibleMovement = timeIncrement * speed;
		}
		distance = this.location.distance(this.destination);
		

		while (possibleMovement >= distance) {
			// node can move past its next destination
			this.location.setLocation(this.destination); // snap to destination
			possibleMovement -= distance;
			if (!setNextWaypoint()) { // get a new waypoint
				return; // no more waypoints left
			}
			distance = this.location.distance(this.destination);
		}

		// move towards the point for possibleMovement amount
		dx = (possibleMovement/distance) * (this.destination.getX() -
				this.location.getX());
		dy = (possibleMovement/distance) * (this.destination.getY() -
				this.location.getY());
		this.location.translate(dx, dy);
	}

	/**
	 * Sets the next destination and speed to correspond the next waypoint
	 * on the path.
	 * @return True if there was a next waypoint to set, false if node still
	 * should wait
	 */
	private boolean setNextWaypoint() {
		if (path == null) {
			path = movement.getPath();			
		}

		if (path == null || !path.hasNext()) {
			this.nextTimeToMove = movement.nextPathAvailable();
			this.path = null;
			return false;
		}

		this.prevDestination = this.destination;		
		this.destination = path.getNextWaypoint();
		this.subpath = this.path.getSubpath(path.getWaypointIndex(), path.getPathSize());
		this.pathIndex = this.path.getWaypointIndex();
		getRoadsAhead();
//		System.out.println(this + " : " + this.path.getCoords());
//		System.out.println(this + "'s subpath : " + getSubpath());
		
		if(this.prevDestination != this.destination) {
			String roadName = "[" + this.prevDestination + ", " + this.destination + "]";
			this.currentRoad = new Road(roadName, this.prevDestination, this.destination);
		}
		
		this.speed = path.getSpeed();

		if (this.movListeners != null) {
			for (MovementListener l : this.movListeners) {
				l.newDestination(this, this.destination, this.speed);
			}
		}
		return true;
	}

	/**
	 * Sends a message from this host to another host
	 * @param id Identifier of the message
	 * @param to Host the message should be sent to
	 */
	public void sendMessage(String id, DTNHost to) {
		this.router.sendMessage(id, to);
	}

	/**
	 * Start receiving a message from another host
	 * @param m The message
	 * @param from Who the message is from
	 * @return The value returned by
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int receiveMessage(Message m, DTNHost from) {
		int retVal = this.router.receiveMessage(m, from);

		if (retVal == MessageRouter.RCV_OK) {
			m.addNodeOnPath(this);	// add this node on the messages path
		}

		return retVal;
	}

	/**
	 * Requests for deliverable message from this host to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this host started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con) {
		return this.router.requestDeliverableMessages(con);
	}

	/**
	 * Informs the host that a message was successfully transferred.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 */
	public void messageTransferred(String id, DTNHost from) {
		this.router.messageTransferred(id, from);
	}

	/**
	 * Informs the host that a message transfer was aborted.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		this.router.messageAborted(id, from, bytesRemaining);
	}

	/**
	 * Creates a new message to this host's router
	 * @param m The message to create
	 */
	public void createNewMessage(Message m) {
		this.router.createNewMessage(m);
	}

	/**
	 * Deletes a message from this host
	 * @param id Identifier of the message
	 * @param drop True if the message is deleted because of "dropping"
	 * (e.g. buffer is full) or false if it was deleted for some other reason
	 * (e.g. the message got delivered to final destination). This effects the
	 * way the removing is reported to the message listeners.
	 */
	public void deleteMessage(String id, boolean drop) {
		this.router.deleteMessage(id, drop);
	}

	/**
	 * Returns a string presentation of the host.
	 * @return Host's name
	 */
	public String toString() {
		return name;
	}

	/**
	 * Checks if a host is the same as this host by comparing the object
	 * reference
	 * @param otherHost The other host
	 * @return True if the hosts objects are the same object
	 */
	public boolean equals(DTNHost otherHost) {
		return this == otherHost;
	}

	/**
	 * Compares two DTNHosts by their addresses.
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(DTNHost h) {
		return this.getAddress() - h.getAddress();
	}

	public Coord getCurrentDestination() {
		return this.destination;
	}
	
	public Coord getPreviousDestination() {
		return this.prevDestination;
	}
	
	public Coord getCurrentPathDestination() {
		return this.path.getCoords().get(this.path.getCoords().size()-1);
	}
	
	public Road<String, Coord, Coord> getCurrentRoad(){
		return this.currentRoad;
	}
	
	public List<DTNHost> getOtherNodesOnMyRoad() {
		this.otherNodesOnRoad.clear();
		this.oppositeLane.clear();
		if(!this.getConnections().isEmpty()) {
			for(Connection con : this.getConnections()) {
				Road road1, road2;
				road1 = this.getCurrentRoad();
				road2 = con.getOtherNode(this).getCurrentRoad();
				if(road1.getRoadName().equals(road2.getRoadName())) {
					this.otherNodesOnRoad.add(con.getOtherNode(this));
				}
				else if(isOppositeLane(road2) && !kunNaglaposNa(con.getOtherNode(this))) {
					this.oppositeLane.add(con.getOtherNode(this));
				}
			}
		}
		return this.otherNodesOnRoad;
	}
	
	public void getOppositeLaneCount() {
		
	}
	
	public boolean kunNaglaposNa(DTNHost opposite) {
		//kitaon an distance ni host tikadto ha iya waypoint(host_w) tapos icompare ngadto han distance ha host_w han node nga natapo 
		if(this.getLocation().distance(getCurrentDestination()) < opposite.getLocation().distance(this.getCurrentDestination()))
			return true;//meaning naglapos na kamo ha kada usa 
		return false;
	}
	
	public List<DTNHost> getSameLaneNodes() {
		return this.otherNodesOnRoad;
	}
	
	public List<DTNHost> getOppositeLaneNodes(){
		return this.oppositeLane;
	}
	
	public boolean oppositeLaneClear() {
		int opposite = 0;
		this.oppositeLane.clear();
		for(Connection con : this.getConnections()) {
			Road r1, r2;
			r1 = this.getCurrentRoad();
			r2 = con.getOtherNode(this).getCurrentRoad();
			if(isOppositeLane(r2) && !kunNaglaposNa(con.getOtherNode(this))) {
				this.oppositeLane.add(con.getOtherNode(this));
				opposite++;
			}
		}
		if(opposite == 0)
			return true;
		
		return false;
	}
	
	public boolean isOppositeLane(Road r) {
		try {
			if(this.getCurrentRoad().getStartpoint().equals(r.getEndpoint()) && this.getCurrentRoad().getEndpoint().equals(r.getStartpoint())) {
				return true;
			}
		}catch(Exception e) {
			
		}
		
		return false;
	}
		
	public double checkFrontDistance(DTNHost frontNode) {
		this.frontDistance = 0;
		if(this.getLocation().distance(destination) > frontNode.getLocation().distance(destination) || 
				this.getCurrentDestination() != frontNode.getCurrentDestination()) {
//				this.slowDown(frontNode.getCurrentSpeed());
				this.frontDistance = this.getLocation().distance(frontNode.getLocation());
		}
		
		return this.frontDistance;
	}
	
	public DTNHost getFrontNode(List<DTNHost> sameLaneNodes) {
		DTNHost frontNode = null;
		double frontDistance = 0, temp;
		for(DTNHost n : sameLaneNodes) {
			temp = this.getLocation().distance(n.getLocation());
			if(this.getLocation().distance(destination) > n.getLocation().distance(destination))  {
				if(frontDistance == 0) {
					frontDistance = temp;
					frontNode = n;
				}
				if(frontDistance > temp) {
					frontDistance = temp;
					frontNode = n;
				}
			}
		}
		return frontNode;
	}

	public boolean shouldSlowDown(double frontSpeed) {
		if(this.getCurrentSpeed() > frontSpeed && 
				this.getLocation().distance(this.getFrontNode(getSameLaneNodes()).getLocation()) <= FRONT_DISTANCE)
			return true;
		return false;
	}
	
	public void slowDown(double tempSpeed) {
		if(!this.toString().startsWith("s"))
			this.speed = tempSpeed;
	}
	
	public void slowDown(DTNHost front) {
		if(!this.toString().startsWith("s") && this.speed > front.getCurrentSpeed())
			this.speed = this.speed - (this.speed * 0.25);
		if(!this.toString().startsWith("s") && this.speed < front.getCurrentSpeed())
			this.speed = front.speed;
			
	}
	
	public void overtake() {
		if(!this.toString().startsWith("s"))
			try {
				if(this.speed < this.path.getSpeed())
					this.speed = this.speed + (this.speed * 0.25);
				else
					this.speed = this.path.getSpeed();
			}catch(Exception e) {
				
			}
	}
	
	public boolean canOvertake(List<DTNHost> opposite, DTNHost front) {
		if(opposite.isEmpty()) {
			return this.canOvertakeStatus = true;
		}
		else {
			if(front == null)
				return this.canOvertakeStatus = true;
			else if(kunNaglaposNa(opposite.get(0)))
				return this.canOvertakeStatus = true;
			else {
				if(this.isSafeToOvertake(front, opposite.get(0)))
					return this.canOvertakeStatus = true; 
			}
		}
		return this.canOvertakeStatus = false;
	}

	public boolean canOvertake() {
		return this.canOvertakeStatus;
	}
	
	public boolean isSafeToOvertake(DTNHost front, DTNHost opposite) {
		if(this.getLocation().distance(opposite.getLocation()) >= SAFE_OVERTAKE_DISTANCE)
			return true;
		return false;
	}
	
	public double getCurrentSpeed() {
		// TODO Auto-generated method stub
		return this.speed;
	}

	public String getCurrentRoadStatus() {
		// TODO Auto-generated method stub
		return null;
	}

	public void getRoadsAhead() {
		this.pathRoads.clear();
		
		List<Coord> s = getSubpath();
		String roadName;
		if(!s.isEmpty()) {
			for(int i = 0; i < s.size()-1; i++) {
				roadName = "[" + s.get(i) + ", " + s.get(i+1) +"]";
				Road r = new Road(roadName, s.get(i), s.get(i+1));
				this.pathRoads.add((String) r.getRoadName());
				this.roads.put(roadName, r);
			}
		}
//		System.out.println(this + "'s road/s ahead: " + this.pathRoads);
	}
	
	public void setRerouteWaypoint(Coord previousDestination) {
		this.speed = this.path.getSpeed();
		this.destination = previousDestination;
	}
	
	public void reroute(Path p) {
		System.out.println(this + " is now going to prev destination: " + p.getCoords().get(0));
		System.out.println("Setting reroute path of host: " + p);
//		System.out.println("Subpath: " + this.getSubpath());
		System.out.println("Previous path: " + this.path);
		for(int i = this.pathIndex; i < this.path.getCoords().size(); i++) {
			p.addWaypoint(this.path.getCoords().get(i));
		}
		System.out.println("rerouted path: " + p);
		this.path = p;
		
		this.speed = p.getSpeed();
		this.destination = p.getCoords().get(0);
	}
	
	public void setReroutePath(Path p) {
		System.out.println(this + " is now going to prev destination: " + p.getCoords().get(0));
		System.out.println("Setting reroute path of host: " + p);
		this.path = p;
		this.speed = p.getSpeed();
		this.destination = p.getCoords().get(0);
	}
	
	public void getTotalTravelTime() {
		
	}
	
	public void updateTravelTime(double travelTime) {
		
	}
}
