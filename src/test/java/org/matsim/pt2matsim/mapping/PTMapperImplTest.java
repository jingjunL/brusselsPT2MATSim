package org.matsim.pt2matsim.mapping;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

import java.util.List;

/**
 * @author polettif
 */
public class PTMapperImplTest {

	public Network network;
	public TransitSchedule schedule;
	public PublicTransitMappingConfigGroup ptmConfig;

	public static PublicTransitMappingConfigGroup initPTMConfig() {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.getModesToKeepOnCleanUp().add("car");
		PublicTransitMappingConfigGroup.LinkCandidateCreatorParams lccParamsBus = new PublicTransitMappingConfigGroup.LinkCandidateCreatorParams("bus");
		lccParamsBus.setNetworkModesStr("car");
		lccParamsBus.setMaxLinkCandidateDistance(99.0);
		lccParamsBus.setMaxNClosestLinks(4);
		config.addParameterSet(lccParamsBus);

		PublicTransitMappingConfigGroup.ModeRoutingAssignment mraBus = new PublicTransitMappingConfigGroup.ModeRoutingAssignment("bus");
		mraBus.setNetworkModesStr("car,bus");
		config.addParameterSet(mraBus);

		config.addParameterSet(new PublicTransitMappingConfigGroup.ManualLinkCandidates());

		return config;
	}

	@Before
	public void prepare() {
		ptmConfig = initPTMConfig();
		network = NetworkToolsTest.initNetwork();
		schedule = ScheduleToolsTest.initSchedule();
		ScheduleCleaner.combineChildStopsToParentStop(schedule);
		ScheduleCleaner.removeMapping(schedule);
		ScheduleCleaner.removeNotUsedStopFacilities(schedule);

		new PTMapperImpl(ptmConfig, schedule, network).run();

//		NetworkTools.writeNetwork(network, "test/simple/outputNetwork.xml");
//		ScheduleTools.writeTransitSchedule(schedule, "test/simple/outpuSchedule.xml");
	}

	@Test
	public void allowedModes() {
		for(Link l : network.getLinks().values()) {
			Assert.assertFalse(l.getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE));
		}
	}

	@Test
	public void numberOfStopFacilities() {
		Assert.assertEquals(10, schedule.getFacilities().size());
	}

	@Test
	public void linkSequences() {
		TransitSchedule initSchedule = ScheduleToolsTest.initSchedule();

		for(TransitLine l : schedule.getTransitLines().values()) {
			for(TransitRoute r : l.getRoutes().values()) {
				TransitRoute initRoute = initSchedule.getTransitLines().get(l.getId()).getRoutes().get(r.getId());
				List<Id<Link>> initLinkIds = ScheduleTools.getTransitRouteLinkIds(initRoute);
				List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(r);
				if(!r.getId().toString().equals("routeB")) { // route B cantt be guessed by the mapper because there's not enough information
					Assert.assertEquals(initLinkIds, linkIds);
				}
			}
		}
	}
}