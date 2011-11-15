/**
 *  ftpdSwitchboard
 *  Copyright 2004 by Michael Peter Christen,
 *  mc@anomic.de, Frankfurt a. M., Germany
 *  first published on http://www.anomic.de
 *  last major change: 09.03.2004
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

package de.anomic.ftpd;

import java.io.IOException;
import java.util.Hashtable;

public class ftpdSwitchboard extends serverAbstractSwitch {

	public ftpdSwitchboard(final String init, final String config)
			throws IOException {
		super(init, config);
	}

	public static Hashtable loadHashtable(final String name) {
		return loadHashtable(propertiesFile(name));
	}

	public int queueSize() {
		// future enhancements: no function now
		return 0;
	}

	public void enQueue(final Object job) {
		// future enhancements: no function now
	}

	public void deQueue() {
		// future enhancements: no function now
	}

	public Hashtable action(final String actionName, final Hashtable actionInput) {
		// future enhancements: no function now
		return null;
	}

}
