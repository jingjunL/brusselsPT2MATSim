/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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
 * *********************************************************************** */

package org.matsim.pt2matsim.run;

import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.OsmMultimodalNetworkConverter;
import org.matsim.pt2matsim.osm.lib.*;
import org.matsim.pt2matsim.tools.NetworkTools;

/**
 * The prepared Brussels OSM and boundary files are in the folder "scenario/preprocessing"
 * Run this class with the argument "OSMConfig.xml"
 */
public final class Step1_Osm2MultimodalNetwork {

	private Step1_Osm2MultimodalNetwork() {
	}

	/**
	 * Converts an osm file to a MATSim network. The input and output file as well
	 * as conversion parameters are defined in this file. Run {@link CreateDefaultOsmConfig}
	 * to create a default config.
	 *
	 * @param args [0] the config.xml file<br/>
	 */
	public static void main(String[] args) {
		if(args.length == 1) {
			run(args[0]);
		} else {
			throw new IllegalArgumentException("Config file as argument needed");
		}
	}

	/**
	 * Converts an osm file to a MATSim network. The input and output file as well
	 * as conversion parameters are defined in the config file. Run {@link CreateDefaultOsmConfig}
	 * to create a default config.
	 *
	 * @param configFile the config.xml file
	 */
	public static void run(String configFile) {
		OsmConverterConfigGroup config = OsmConverterConfigGroup.loadConfig(configFile);
		run(config);
	}

	/**
	 * Converts an osm file with default conversion parameters.
	 * @param osmFile the osm file
	 * @param outputNetworkFile the path to the output network file
	 * @param outputCoordinateSystem output coordinate system (no transformation is applied if <tt>null</tt>)
	 */
	public static void run(String osmFile, String outputNetworkFile, String outputCoordinateSystem) {
		OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();
		config.setOsmFile(osmFile);
		config.setOutputNetworkFile(outputNetworkFile);
		config.setOutputCoordinateSystem(outputCoordinateSystem);

		run(config);
	}

	public static void run(OsmConverterConfigGroup config) {
		AllowedTagsFilter filter = new AllowedTagsFilter();
		filter.add(Osm.ElementType.WAY, Osm.Key.HIGHWAY, null);
		filter.add(Osm.ElementType.WAY, Osm.Key.RAILWAY, null);

		OsmData osmData = new OsmDataImpl(filter);
		new OsmFileReader(osmData).readFile(config.getOsmFile());

		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osmData);
		converter.convert(config);

		NetworkTools.writeNetwork(converter.getNetwork(), config.getOutputNetworkFile());
	}
}
