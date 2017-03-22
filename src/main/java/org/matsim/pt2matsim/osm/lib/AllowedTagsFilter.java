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

package org.matsim.pt2matsim.osm.lib;

import org.matsim.core.utils.collections.MapUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author polettif
 */
public class AllowedTagsFilter {

	private final static String MATCH_ALL = "*";
	private final Map<Osm.ElementType, Map<String, Set<String>>> keyValuePairs = new HashMap<>();
	private final Map<Osm.ElementType, Map<String, Set<String>>> keyValueExceptions = new HashMap<>();

	/**
	 * @return a filter that contains the usual pt elements
	 */
	public static AllowedTagsFilter getDefaultPTFilter() {
		AllowedTagsFilter filter = new AllowedTagsFilter();
		filter.add(Osm.ElementType.WAY, Osm.Key.HIGHWAY, null);
		filter.add(Osm.ElementType.WAY, Osm.Key.RAILWAY, null);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.BUS);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.TROLLEYBUS);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.RAIL);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.TRAM);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.LIGHT_RAIL);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.FUNICULAR);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.MONORAIL);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.SUBWAY);

		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.BUS);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.TROLLEYBUS);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.TRAM);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.MONORAIL);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.SUBWAY);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.FERRY);

		return filter;
	}

	/**
	 * @return <code>true</code> if at least one of the given tags matches any one of the specified filter-tags.
	 * Also returns true if no tags have been defined for the given element type.
	 */
	public boolean matches(Osm.Element element) {
		Map<String, Set<String>> checkPairs = keyValuePairs.get(element.getType());
		Map<String, Set<String>> checkExceptions = keyValueExceptions.get(element.getType());
		Map<String, String> tags = element.getTags();

		if(checkExceptions != null) {
			// check for exceptions
			for(Map.Entry<String, Set<String>> e : checkExceptions.entrySet()) {
				if(tags.containsKey(e.getKey()) && e.getValue().size() == 0) {
					return false;
				}
				String value = tags.get(e.getKey());
				if(value != null && (e.getValue().contains(value) || e.getValue().contains(MATCH_ALL))) {
					return false;
				}
			}
		}
		if(checkPairs != null) {
			// check for positive list
			for(Map.Entry<String, Set<String>> e : checkPairs.entrySet()) {
				if(tags.containsKey(e.getKey()) && e.getValue().size() == 0) {
					return true;
				}
				String value = tags.get(e.getKey());
				if(value != null && (e.getValue().contains(value) || e.getValue().contains(MATCH_ALL))) {
					return true;
				}
			}
		}

		return (checkPairs == null && checkExceptions == null);
	}

	/**
	 * @param elementType osm element type (node/way/relation)
	 * @param key         tag name
	 * @param value       <code>null</code> if all values should be taken
	 */
	public void add(Osm.ElementType elementType, final String key, final String value) {
		Map<String, Set<String>> map = MapUtils.getMap(elementType, keyValuePairs);
		if(value == null) {
			map.put(key, new HashSet<>());
		} else {
			Set<String> values = MapUtils.getSet(key, map);
			values.add(value);
		}
	}

	/**
	 * Adds an exception to the filter, that is if this key and value appears,
	 * the filter will return false
	 */
	public void addException(Osm.ElementType elementType, final String key, final String value) {
		Map<String, Set<String>> map = MapUtils.getMap(elementType, keyValueExceptions);
		if(value == null) {
			map.put(key, null);
		} else {
			Set<String> values = MapUtils.getSet(key, map);
			values.add(value);
		}
	}

	/*pckg*/ void mergeFilter(AllowedTagsFilter f) {
		for(Map.Entry<Osm.ElementType, Map<String, Set<String>>> typeMapEntry : f.keyValuePairs.entrySet()) {
			for(Map.Entry<String, Set<String>> entry : typeMapEntry.getValue().entrySet()) {
				if(entry.getKey() != null) {
					if(entry.getValue() == null) {
						MapUtils.getSet(entry.getKey(), MapUtils.getMap(typeMapEntry.getKey(), this.keyValuePairs)).add(null);
					} else {
						MapUtils.getSet(entry.getKey(), MapUtils.getMap(typeMapEntry.getKey(), this.keyValuePairs)).addAll(entry.getValue());
					}
				}

			}
		}
		for(Map.Entry<Osm.ElementType, Map<String, Set<String>>> typeMapEntry : f.keyValueExceptions.entrySet()) {
			for(Map.Entry<String, Set<String>> entry : typeMapEntry.getValue().entrySet()) {
				if(entry.getKey() != null) {
					if(entry.getValue() == null) {
						MapUtils.getSet(entry.getKey(), MapUtils.getMap(typeMapEntry.getKey(), this.keyValueExceptions)).add(null);
					} else {
						MapUtils.getSet(entry.getKey(), MapUtils.getMap(typeMapEntry.getKey(), this.keyValueExceptions)).addAll(entry.getValue());
					}				}
			}
		}
	}
}