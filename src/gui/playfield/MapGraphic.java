/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package gui.playfield;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import movement.map.MapNode;
import movement.map.SimMap;
import core.Coord;

/**
 * PlayfieldGraphic for SimMap visualization
 *
 */
public class MapGraphic extends PlayFieldGraphic {
	private SimMap simMap;
	private  SimMap simMap2;
	private final Color PATH_COLOR = Color.LIGHT_GRAY;
	private final Color BG_COLOR = Color.WHITE;
	
	//added
	public static HashMap<Coord, List<Coord>> roads;
	public List<Coord> endpoints;
	
	public MapGraphic(SimMap simMap) {
		this.simMap = simMap;
		this.simMap2 = simMap;
	}	
	
	public SimMap getMapNodes() {
		return this.simMap2;
	}
	
	// TODO: draw only once and store to buffer
	@Override
	public void draw(Graphics2D g2) {
		Coord c,c2;
		
		if (simMap == null) {
			return;
		}
		
		g2.setColor(PATH_COLOR);
		g2.setBackground(BG_COLOR);
		
		// draws all edges between map nodes (bidirectional edges twice)
		for (MapNode n : simMap.getNodes()) {
			c = n.getLocation();
			
			// draw a line to adjacent nodes
			for (MapNode n2 : n.getNeighbors()) {
				endpoints = new ArrayList<Coord>();
				c2 = n2.getLocation();
				
				this.endpoints.add(c2);
				
				//pagdisplay han coordinates han tanan na road segments han whole map 
				g2.drawString(c.toString(), scale(c.getX()), scale(c.getY()));
				g2.drawString(c2.toString(), scale(c2.getX()), scale(c2.getY()));
				//end
				
				//original function pagdraw han map
				g2.drawLine(scale(c2.getX()), scale(c2.getY()),
						scale(c.getX()), scale(c.getY()));
				
				
				//alternative para each line iba iba
				//l2d = new Line2D.Double(scale(c.getX()), scale(c.getY()), scale(c2.getX()), scale(c2.getY()));
				//roadID++;	
				//lineList.add(l2d);
				//g2.draw(l2d);
			}
		}
	}	
}
