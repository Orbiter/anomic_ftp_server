// ftpdPermissions.java 
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 26.02.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
 ftpdPermissions documentation:

 The AnomicFTPD server provides 'hooks' that can be used to perform actions
 bevore a special task is performed and after the task has been completed.
 This class is used by the ftpdControl class that
 provides these hooks. log-in, read and write permissions are requested
 as pre-actions from ftpdPermissions.
 By altering this class you can adopt permission management to your needs.
 */

package de.anomic.ftpd;

import java.util.Enumeration;
import java.util.Hashtable;

public class ftpdPermissions {

	// class variables; only used to carry the permissions of a single account
	public boolean mayRead = false;
	public boolean mayWrite = false;
	public boolean mayExec = false;

	// static variables

	// group definitions "rwx,up:do,root-path"
	private static Hashtable groups = new Hashtable();

	// account settings <name>=<group>,<password>
	private static Hashtable accounts = new Hashtable();

	// static inits
	static {
		loadPermissions();
	}

	public ftpdPermissions() {
		// the permissions that apply if no user is logged in
		this.mayRead = false;
		this.mayWrite = false;
		this.mayExec = false;
	}

	public ftpdPermissions(final String user) {
		// retrieve user permissions from properties
		this.mayRead = permissionRead(user);
		this.mayWrite = permissionWrite(user);
		this.mayExec = permissionExec(user);
	}

	// load permissions. can also be called to re-read the permission files
	public static void loadPermissions() {
		groups = ftpdSwitchboard.loadHashtable("ftpd.groups");
		accounts = ftpdSwitchboard.loadHashtable("ftpd.accounts");
	}

	// return an enumeration of users. Objects of the enumeration are instances
	// of string
	public static Enumeration enumUsers() {
		return accounts.keys();
	}

	public static String getGroup(final String user) {
		final String g = (String) accounts.get(user);
		if (g == null)
			return "guest";
		final int i = g.indexOf(",");
		if (i == -1)
			return g;
		else
			return g.substring(0, i);
	}

	public static String getPassword(final String user) {
		final String g = (String) accounts.get(user);
		if (g == null)
			return null;
		final int i = g.indexOf(",");
		if (i == -1)
			return "*";
		else
			return g.substring(i + 1);
	}

	public static String getRoot(final String user) {
		final String g = getGroup(user);
		String p = (String) groups.get(g);
		if ((p == null) || (p.length() <= 10)) {
			p = "/";
		} else {
			p = p.substring(10);
		}
		if ((p.length() > 3) && ((p.endsWith("/")) || (p.endsWith("\\")))) {
			p = p.substring(0, p.length() - 1);
		}
		return p;
	}

	public static boolean permissionRead(final String user) {
		final String g = getGroup(user);
		final String p = (String) groups.get(g);
		if ((p == null) || (p.length() < 1))
			return false;
		else
			return (p.charAt(0) == 'r');
	}

	public static boolean permissionWrite(final String user) {
		final String g = getGroup(user);
		final String p = (String) groups.get(g);
		if ((p == null) || (p.length() < 2))
			return false;
		else
			return (p.charAt(1) == 'w');
	}

	public static boolean permissionExec(final String user) {
		final String g = getGroup(user);
		final String p = (String) groups.get(g);
		if ((p == null) || (p.length() < 3))
			return false;
		else
			return (p.charAt(2) == 'x');
	}

	public static double getRatio(final String user) {
		final String g = getGroup(user);
		final String p = (String) groups.get(g);
		try {
			final int up = Integer.parseInt(p.substring(4, 6));
			final int down = Integer.parseInt(p.substring(7, 9));
			return ((double) up) / ((double) down);
		} catch (final NumberFormatException e) {
			return 0.0;
		} catch (final ArithmeticException e) {
			return 0.0;
		}
	}

}
