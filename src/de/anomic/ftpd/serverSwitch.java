/**
 *  serverSwitch
 *  Copyright 2004 by Michael Peter Christen,
 *  mc@anomic.de, Frankfurt a. M., Germany
 *  first published on http://www.anomic.de
 *  last major change: 04.02.2004
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 this is an interface for possible switchboard implementations
 Its purpose is to provide a mechanism which cgi pages can use
 to influence the behavior of a concurrntly running application
 */

package de.anomic.ftpd;

import java.util.Enumeration;
import java.util.Hashtable;

public interface serverSwitch {

	// the switchboard can be used to set and read properties
	public void setConfig(String key, String value);

	public String getConfig(String key, String dflt);

	public int getConfigInt(String key, int dflt);

	public Enumeration configKeys();

	// the switchboard also shall maintain a job list
	// jobs can be queued by submitting a job object
	// to work off a queue job, use deQueue, which is meant to
	// work off exactly only one job, not all
	public int queueSize();

	public void enQueue(Object job);

	public void deQueue();

	// ask the switchboard to perform an action
	// the result is a properties structure with the result of the action
	// The actionName selects an action
	// the actionInput is an input for the selected action
	public Hashtable action(String actionName, Hashtable actionInput);

}
