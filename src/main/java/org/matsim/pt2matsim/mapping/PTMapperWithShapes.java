/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.mapping;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkImpl;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.lib.ShapedSchedule;
import org.matsim.pt2matsim.lib.ShapedTransitSchedule;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreator;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreatorUnique;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersWithShapes;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoSchedule;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoScheduleImpl;
import org.matsim.pt2matsim.plausibility.StopFacilityHistogram;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleCleaner;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.Collections;
import java.util.List;

/**
 * Different to the standard implementation, this mapper uses the shapes
 * provided by a GTFS feed to vastly improve mapping results.
 *
 * ---
 *
 * References an unmapped transit schedule to a network. Combines
 * finding link sequences for TransitRoutes and referencing
 * TransitStopFacilities to link. Calculates the least cost path
 * from the transit route's first to its last stop with the constraint
 * that the path must contain a link candidate of every stop.<p/>
 *
 * Additional stop facilities are created if a stop facility has more
 * than one plausible link. Artificial links are added to the network
 * if no path can be found.
 *
 * TODO create entry point in pt2matsim.run, adapt config
 *
 * @author polettif
 */
public class PTMapperWithShapes implements PTMapper {

	protected static Logger log = Logger.getLogger(PTMapperWithShapes.class);

	private PublicTransitMappingConfigGroup config;
	private Network network;
	private ShapedTransitSchedule schedule;

	private final PseudoSchedule pseudoSchedule = new PseudoScheduleImpl();

	/**
	 * Loads the PublicTransitMapping config file. If paths to input files
	 * (schedule and network) are provided in the config, mapping can be run
	 * via {@link #run()}
	 *
	 * @param configPath the config file
	 */
	public PTMapperWithShapes(String configPath, String shapeFile, String outputCoordinateSystem, String routeShapeRefFile) {
		Config configAll = ConfigUtils.loadConfig(configPath, new PublicTransitMappingConfigGroup());
		this.config = ConfigUtils.addOrGetModule(configAll, PublicTransitMappingConfigGroup.GROUP_NAME, PublicTransitMappingConfigGroup.class );
		TransitSchedule transitSchedule = config.getScheduleFile() == null ? null : ScheduleTools.readTransitSchedule(config.getScheduleFile());
		this.network = config.getNetworkFile() == null ? null : NetworkTools.readNetwork(config.getNetworkFile());

		this.schedule = new ShapedSchedule(transitSchedule);
		this.schedule.readShapesFile(shapeFile, outputCoordinateSystem);
		this.schedule.readRouteShapeReferenceFile(routeShapeRefFile);
	}

	/**
	 * Use this constructor if you just want to use the config for mapping parameters.
	 * The provided schedule is expected to contain the stops sequence and
	 * the stop facilities each transit route. The routes will be newly routed,
	 * any former routes will be overwritten. Changes are done on the schedule
	 * network provided here.
	 * <p/>
	 */
	public PTMapperWithShapes(PublicTransitMappingConfigGroup config, ShapedTransitSchedule schedule, Network network) {
		this.config = config;
		this.network = network;
		this.schedule = schedule;
	}

	/**
	 * Reads the schedule and network file specified in the PublicTransitMapping
	 * config and maps the schedule to the network. Writes the output files as
	 * well if defined in config. The mapping parameters defined in the config
	 * are used.
	 */
	@Override
	public void run() {
		if(schedule == null) {
			throw new RuntimeException("No schedule defined!");
		} else if(network == null) {
			throw new RuntimeException("No network defined!");
		}

		setLogLevels();
		config.loadParameterSets();

		log.info("======================================");
		log.info("Mapping transit schedule to network...");

		/**
		 * Some schedule statistics
 		 */
		int nStopFacilities = schedule.getFacilities().size();
		int nTransitRoutes = 0;
		for(TransitLine transitLine : this.schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				nTransitRoutes++;
			}
		}


		/** [1]
		 * Create a separate network for all schedule modes and
		 * initiate routers.
		 */
		log.info("==============================================");
		log.info("Creating routers...");
		ScheduleRouters scheduleRouters = new ScheduleRoutersWithShapes(config, schedule, network);


		/** [2]
		 * Load the closest links and create LinkCandidates. StopFacilities
		 * with no links within search radius are given a dummy loop link right
		 * on their coordinates. Each Link Candidate is a possible new stop facility
		 * after PseudoRouting.
		 */
		log.info("===========================");
		log.info("Creating link candidates...");
		LinkCandidateCreator linkCandidates = new LinkCandidateCreatorUnique(this.schedule, this.network, this.config);


		/** [3]
		 * PseudoRouting
		 * Initiate and start threads, calculate PseudoTransitRoutes
		 * for all transit routes.
		 */
		log.info("==================================");
		log.info("Calculating pseudoTransitRoutes... ("+nTransitRoutes+" transit routes in "+schedule.getTransitLines().size()+" transit lines)");

		// initiate pseudoRouting
		int numThreads = config.getNumOfThreads() > 0 ? config.getNumOfThreads() : 1;
		PseudoRouting[] pseudoRoutingRunnables = new PseudoRouting[numThreads];
		for(int i = 0; i < numThreads; i++) {
			pseudoRoutingRunnables[i] = new PseudoRoutingImpl(config, scheduleRouters, linkCandidates);
		}
		// spread transit lines on runnables
		int thr = 0;
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			pseudoRoutingRunnables[thr++ % numThreads].addTransitLineToQueue(transitLine);
		}

		Thread[] threads = new Thread[numThreads];
		// start pseudoRouting
		for(int i = 0; i < numThreads; i++) {
			threads[i] = new Thread(pseudoRoutingRunnables[i]);
			threads[i].start();
		}
		for(Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}


		/** [4]
		 * Collect artificial links from threads and add them to network.
		 * Collect pseudoSchedules from threads.
		 */
		log.info("=====================================");
		log.info("Adding artificial links to network...");
		for(PseudoRouting prt : pseudoRoutingRunnables) {
			prt.addArtificialLinks(network);
			pseudoSchedule.mergePseudoSchedule(prt.getPseudoSchedule());
		}


		/** [5]
		 * Replace the parent stop facilities in each transitRoute's routeProfile
		 * with child StopFacilities. Add the new transitRoutes to the schedule.
		 */
		log.info("========================================================================");
		log.info("Replacing parent StopFacilities in schedule with child StopFacilities...");
		pseudoSchedule.createAndReplaceFacilities(schedule);

		/** [6]
		 * The final routing should be done on the merged network, so a mode dependent
		 * router for each schedule mode is initialized using the same merged network.
		 */
		log.info("===========================================================================================");
		log.info("Initiating final routers to map transit routes with referenced facilities to the network...");
		ScheduleRouters finalRouters = new ScheduleRoutersWithShapes(config, schedule, network, true);

		/* TODO get rid of finalRouters
		 All routes and subroutes have already been calculated once. This data should be used again
		 instead of initializing all routers again. Possibly needs predictable artificial link ids (e.g.
		 using from- and toNode to generate link sequences in the pseudoschedule
		 */

		/** [7]
		 * Route all transitRoutes with the new referenced links. The shortest path
		 * between child stopFacilities is calculated and added to the schedule.
		 */
		log.info("=============================================");
		log.info("Creating link sequences for transit routes...");
		ScheduleTools.routeSchedule(this.schedule, this.network, finalRouters);

		/** [8]
		 * Now that all lines have been routed, it is possible that a route passes
		 * a link closer to a stop facility than its referenced link.
		 */
		log.info("================================");
		log.info("Pulling child stop facilities...");
		int nPulled = 1;
		while(nPulled != 0) {
			nPulled = UtilsPTMapper.pullChildStopFacilitiesTogether(this.schedule, this.network);
		}

		/** [9]
		 * After all lines are created, clean the schedule and network. Removing
		 * not used transit links includes removing artificial links that
		 * needed to be added to the network for routing purposes.
		 */
		log.info("=============================");
		log.info("Clean schedule and network...");
		cleanScheduleAndNetwork();

		/** [10]
		 * Validate the schedule
		 */
		log.info("======================");
		log.info("Validating schedule...");
		printValidateSchedule();

		/** [11]
		 * Write output files if defined in config
		 */
		log.info("=======================================");
		log.info("Writing schedule and network to file...");
		writeOutputFiles();

		log.info("==================================================");
		log.info("= Mapping transit schedule to network completed! =");
		log.info("==================================================");

		/**
		 * Statistics
		 */
		printStatistics(nStopFacilities);
	}

	private void cleanScheduleAndNetwork() {
		// might have been set higher during pseudo routing
		NetworkTools.resetLinkLength(network, PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE);

		// changing the freespeed of the artificial links (value is used in simulations)
		UtilsPTMapper.setFreeSpeedBasedOnSchedule(network, schedule, config.getScheduleFreespeedModes());

		// Remove unnecessary parts of schedule
		ScheduleCleaner.removeNotUsedTransitLinks(schedule, network, config.getModesToKeepOnCleanUp(), true);
		if(config.getRemoveNotUsedStopFacilities()) ScheduleCleaner.removeNotUsedStopFacilities(schedule);

		// change the network transport modes
		ScheduleTools.assignScheduleModesToLinks(schedule, network);
		if(config.getCombinePtModes()) {
			NetworkTools.replaceNonCarModesWithPT(network);
		} else if(config.getAddPtMode()) {
			ScheduleTools.addPTModeToNetwork(schedule, network);
		}
	}

	/**
	 * Write the schedule and network to output files (if defined in config)
	 */
	private void writeOutputFiles() {
		if(config.getOutputNetworkFile() != null && config.getOutputScheduleFile() != null) {
			try {
				ScheduleTools.writeTransitSchedule(schedule, config.getOutputScheduleFile());
				NetworkTools.writeNetwork(network, config.getOutputNetworkFile());
			} catch (Exception e) {
				log.error("Cannot write to output directory! Trying to write schedule and network file in working directory");
				long t = System.nanoTime() / 1000000;
				try {
					ScheduleTools.writeTransitSchedule(schedule, t + "schedule.xml.gz");
					NetworkTools.writeNetwork(network, t + "network.xml.gz");
				} catch (Exception e1) {
					throw new RuntimeException("Files could not be written in working directory");
				}
			}
			if(config.getOutputStreetNetworkFile() != null) {
				NetworkTools.writeNetwork(NetworkTools.createFilteredNetworkByLinkMode(network, Collections.singleton(TransportMode.car)), config.getOutputStreetNetworkFile());
			}
		} else {
			log.info("");
			log.info("No output paths defined, schedule and network are not written to files.");
		}
	}

	/**
	 * Log the result of the schedule validator
	 */
	private void printValidateSchedule() {
		TransitScheduleValidator.ValidationResult validationResult = TransitScheduleValidator.validateAll(schedule, network);
		if(validationResult.isValid()) {
			log.info("Schedule appears valid!");
		} else {
			log.warn("Schedule is NOT valid!");
		}
		if(validationResult.getErrors().size() > 0) {
			log.info("Validation errors:");
			for(String e : validationResult.getErrors()) {
				log.info(e);
			}
		}
		if(validationResult.getWarnings().size() > 0) {
			log.info("Validation warnings:");
			for(String w : validationResult.getWarnings()) {
				log.info(w);
			}
		}
	}

	/**
	 * Print some basic mapping statistics.
	 */
	private void printStatistics(int inputNStopFacilities) {
		int nArtificialLinks = 0;
		for(Link l : network.getLinks().values()) {
			if(l.getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE)) {
				nArtificialLinks++;
			}
		}
		int withoutArtificialLinks = 0;
		int nRoutes = 0;
		for(TransitLine transitLine : this.schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				nRoutes++;
				boolean routeHasArtificialLink = false;
				List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(transitRoute);
				for(Id<Link> linkId : linkIds) {
					if(network.getLinks().get(linkId).getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE)) {
						routeHasArtificialLink = true;
					}
				}
				if(!routeHasArtificialLink) {
					withoutArtificialLinks++;
				}
			}
		}

		StopFacilityHistogram histogram = new StopFacilityHistogram(schedule);

		log.info("");
		log.info("    Artificial Links:");
		log.info("       created  " + nArtificialLinks);
		log.info("    Stop Facilities:");
		log.info("       total input   " + inputNStopFacilities);
		log.info("       total output  " + schedule.getFacilities().size());
		log.info("       diff.         " + (schedule.getFacilities().size() - inputNStopFacilities));
		log.info("    Child Stop Facilities:");
		log.info("       median nr created   " + String.format("%.0f", histogram.median()));
		log.info("       average nr created  " + String.format("%.2f", histogram.average()));
		log.info("       max nr created      " + String.format("%.0f", histogram.max()));
		log.info("    Transit Routes:");
		log.info("       total routes in schedule         " + nRoutes);
		log.info("       routes without artificial links  " + withoutArtificialLinks);
		log.info("");
		log.info("    Run PlausibilityCheck for further analysis");
		log.info("");
		log.info("==================================================");
	}

	@Override
	public void setConfig(Config config) {
		this.config = ConfigUtils.addOrGetModule(config, PublicTransitMappingConfigGroup.GROUP_NAME, PublicTransitMappingConfigGroup.class );
	}

	@Override
	public void setSchedule(TransitSchedule schedule) {
		this.schedule = new ShapedSchedule(schedule);
	}

	@Override
	public void setNetwork(Network network) {
		this.network = network;
	}

	@Override
	public Config getConfig() {
		Config configAll = ConfigUtils.createConfig();
		configAll.addModule(config);
		return configAll;
	}

	@Override
	public TransitSchedule getSchedule() {
		return schedule;
	}

	@Override
	public Network getNetwork() {
		return network;
	}

	private static void setLogLevels() {
		Logger.getLogger(org.matsim.core.router.Dijkstra.class).setLevel(Level.ERROR); // suppress no route found warnings
		Logger.getLogger(Network.class).setLevel(Level.WARN);
		Logger.getLogger(NetworkImpl.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.network.filter.NetworkFilterManager.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessDijkstra.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessDijkstra.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessEuclidean.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessLandmarks.class).setLevel(Level.WARN);
	}
}
