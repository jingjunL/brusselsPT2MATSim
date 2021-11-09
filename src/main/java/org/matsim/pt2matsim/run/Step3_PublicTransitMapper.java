/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** *
 */

package org.matsim.pt2matsim.run;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.Collections;

/**
 * Argument needed the run this class: PTMapperConfig.xml
 * Some important changes in the config here compared to the default config:
 * 1) ScheduleFreespeedMode:
 * 			the three pt modes in Brussels are bus, subway and tram. For the subway and tram meeting their schedule, change the freespeed of their specific link
 * 			to the speed that can meet the schedule (as the link they are operating on only has one mode, i.e. only subway operates on subway link
 * 2) For the same reason as above, these two modes are added at the end of the config (ensuring subway only operate on link with subway)
 * 		<parameterset type="transportModeAssignment" >
 * 			<param name="networkModes" value="subway" />
 * 			<param name="scheduleMode" value="subway" />
 * 		</parameterset>
 * 		<parameterset type="transportModeAssignment" >
 * 			<param name="networkModes" value="tram" />
 * 			<param name="scheduleMode" value="tram" />
 * 		</parameterset>
 *
 *
 * 	Overall, 4704 transit schdules are generated
 */
public final class Step3_PublicTransitMapper {

	protected static Logger log = Logger.getLogger(Step3_PublicTransitMapper.class);

	private Step3_PublicTransitMapper() {
	}

	/**
	 * Routes the unmapped MATSim Transit Schedule to the network using the file
	 * paths specified in the config. Writes the resulting schedule and network to xml files.<p/>
	 *
	 * @see CreateDefaultPTMapperConfig
	 *
	 * @param args <br/>[0] PublicTransitMapping config file<br/>
	 */
	public static void main(String[] args) {
		if(args.length == 1) {
			run(args[0]);
		} else {
			throw new IllegalArgumentException("Public Transit Mapping config file as argument needed");
		}
	}

	/**
	 * Routes the unmapped MATSim Transit Schedule to the network using the file
	 * paths specified in the config. Writes the resulting schedule and network to xml files.<p/>
	 *
	 * @see CreateDefaultPTMapperConfig
	 *
	 * @param configFile the PublicTransitMapping config file
	 */
	public static void run(String configFile) {
		// Load config
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.loadConfig(configFile);

		// Load input schedule and network
		TransitSchedule schedule = config.getInputScheduleFile() == null ? null : ScheduleTools.readTransitSchedule(config.getInputScheduleFile());
		Network network = config.getInputNetworkFile() == null ? null : NetworkTools.readNetwork(config.getInputNetworkFile());

		// Run PTMapper
		PTMapper.mapScheduleToNetwork(schedule, network, config);
		// or: new PTMapper(schedule, network).run(config);

		// Write the schedule and network to output files (if defined in config)
		if(config.getOutputNetworkFile() != null && config.getOutputScheduleFile() != null) {
			log.info("Writing schedule and network to file...");
			try {
				ScheduleTools.writeTransitSchedule(schedule, config.getOutputScheduleFile());
				NetworkTools.writeNetwork(network, config.getOutputNetworkFile());
			} catch (Exception e) {
				log.error("Cannot write to output directory!");
			}
			if(config.getOutputStreetNetworkFile() != null) {
				NetworkTools.writeNetwork(NetworkTools.createFilteredNetworkByLinkMode(network, Collections.singleton(TransportMode.car)), config.getOutputStreetNetworkFile());
			}
		} else {
			log.info("No output paths defined, schedule and network are not written to files.");
		}
	}
}
