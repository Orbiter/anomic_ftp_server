/**
 *  serverAbstractSwitch
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

abstract class serverAbstractSwitch implements serverSwitch {

	// configuration management
	private final File configFile;
	private Hashtable configProps;
	private final String configComment;

	public serverAbstractSwitch(final String init, final String config)
			throws IOException {
		// we initialize the switchboard with a property file,
		// but maintain these properties then later in a new 'config' file
		// to reset all changed configs, the config file must
		// be deleted, but not the init file
		// the only attribute that will always be read from the init is the
		// file name of the config file
		this.configComment = "this is an automatically generated file, updated by serverAbstractSwitch and initialized by "
				+ init;
		this.configFile = propertiesFile(config);
		if (this.configFile.exists()) {
			// load the configs from it's file
			this.configProps = loadHashtable(this.configFile);
		} else {
			// init the configs with the init Properties
			this.configProps = loadHashtable(propertiesFile(init));
			// and save it the first time
			saveConfig();
		}

	}

	public static File propertiesFile(final String name) {
		// ensure path variables are set
		// this is not made static, since that does not work on EPOC java (no
		// reflection in statics)
		String localPathString;
		// find path
		try {
			localPathString = System.getProperty("user.dir");
		} catch (final Exception e) {
			localPathString = "";
		}
		return new File(new File(localPathString), name);
	}

	public static Hashtable loadHashtable(final File f) {
		// load props
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream(f));
		} catch (final IOException e1) {
			System.err.println("ERROR: " + f.toString()
					+ " not found in settings path");
			prop = null;
		}
		return prop;
	}

	public static void saveHashtable(final File f, final Hashtable props,
			final String comment) throws IOException {
		final PrintWriter pw = new PrintWriter(new FileOutputStream(f));
		pw.println("# " + comment);
		final Enumeration e = props.keys();
		String key, value;
		while (e.hasMoreElements()) {
			key = (String) e.nextElement();
			value = (String) props.get(key);
			pw.println(key + "=" + value);
		}
		pw.println("# EOF");
		pw.close();
	}

	public void setConfig(final String key, final String value) {
		this.configProps.put(key, value);
		saveConfig();
	}

	public String getConfig(final String key, final String dflt) {
		final String s = (String) this.configProps.get(key);
		if (s == null)
			return dflt;
		else
			return s;
	}

	public int getConfigInt(final String key, final int dflt) {
		final String s = (String) this.configProps.get(key);
		if (s == null)
			return dflt;
		else {
			try {
				return Integer.parseInt(s);
			} catch (final NumberFormatException e) {
				return dflt;
			}
		}
	}

	public Enumeration configKeys() {
		return this.configProps.keys();
	}

	private void saveConfig() {
		try {
			saveHashtable(this.configFile, this.configProps, this.configComment);
		} catch (final IOException e) {
			System.out.println("ERROR: cannot write config file "
					+ this.configFile.toString());
		}
	}

	abstract public int queueSize();

	abstract public void enQueue(Object job);

	abstract public void deQueue();

	abstract public Hashtable action(String actionName, Hashtable actionInput);

	public String toString() {
		return this.configProps.toString();
	}

}
