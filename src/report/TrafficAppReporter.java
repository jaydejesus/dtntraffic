package report;

import java.awt.geom.Line2D;
import applications.TrafficApp;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;
import core.Road;

public class TrafficAppReporter extends Report implements ApplicationListener{

	@Override
	public void gotEvent(String event, Road myRoad, String basis, double time, double averageSpeed, 
			String trafficCondition, Application app, DTNHost host) {
	
		//must add travel time 
		
		String report = host + " @ " + time + " on road segment " + myRoad.getStartpoint()+ ", " + 
				myRoad.getEndpoint() + " Basis: " + basis + " Average speed is: " 
				+ averageSpeed + " - " + trafficCondition;
		if (!(app instanceof TrafficApp)) return;
		
		if(event.equalsIgnoreCase("TrafficReport")) {
			write(report);
		}
	}

	@Override
	public void gotEvent(String event, Object params, Application app, DTNHost host) {
		
	}
	
	@Override
	public void done() {
		write("Done!");
		super.done();
	}
}
