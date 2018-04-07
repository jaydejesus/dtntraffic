package report;

import java.awt.geom.Line2D;
import applications.TrafficApp;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;

public class TrafficAppReporter extends Report implements ApplicationListener{

	@Override
	public void gotEvent(String event, Line2D myRoad, double time, double averageSpeed, String status, Application app, DTNHost host) {
		double x1, x2, y1, y2;
		x1 = round(myRoad.getX1());
		x2 = round(myRoad.getX2());
		y1 = round(myRoad.getY1());
		y2 = round(myRoad.getY2());
		String report = "@" + time + ": " + host + " on road segment (" + x1 + ", " +y1 + "), (" + x2 + ", " + y2 + "). Average speed is: " 
				+ averageSpeed + " - " + status;
		if (!(app instanceof TrafficApp)) return;
		
		if(event.equalsIgnoreCase("ToReporter")) {
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
	
	public double round(double value) {
		return (double)Math.round(value * 100)/100;
	}
}
