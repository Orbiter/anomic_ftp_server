/**
 *  ftpdPermissions
 *  Copyright 2004 by Michael Peter Christen,
 *  mc@anomic.de, Frankfurt a. M., Germany
 *  first published 26.02.2004 on http://www.anomic.de
 *  last major change: 29.11.2010
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
 ftpdPermissions documentation:

 The AnomicFTPD server provides 'hooks' that can be used to perform actions
 bevore a special task is performed and after the task has been completed.
 This class is used by the ftpdControl class that
 provides these hooks. log-in, read and write permissions are requested
 as pre-actions from ftpdPermissions.
 By altering this class you can adopt permission management to your needs.
 */

package de.anomic.ftpd;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

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

	/**
	 * get the root path from the ftpd.groups file
	 * @param user
	 * @return the root file path for the given user. The path has no ending "/" or "\\".
	 */
	public static String getRoot(final String user) {
        return getPath(user, ftpdControl.slash);
    }

	/**
	 * get the server path from the ftpd.groups file according to a given user working directory
	 * @param user
	 * @param userWD
	 * @return the server file path for the given user and user working directory.
	 */
	public static String getPath(final String user, String userWD) {
        final String groupname = getGroup(user);
        String groupdef = (String) groups.get(groupname);
        if (groupdef == null || groupdef.length() <= 10) {
            return null;
        }
        String path = groupdef.substring(10);
        if (path.startsWith("{")) {
            if (!path.endsWith("}")) {
                System.out.println("wrong ftpd.groups configuration: path declaration for user '" + user + "' does not end with '}'");
                return null;
            }
            Properties p = new Properties();
            try {
                p.load(new ByteArrayInputStream(path.substring(1, path.length() - 1).getBytes()));
            } catch (IOException e) {
                System.out.println("wrong ftpd.groups configuration: path declaration for user '" + user + "' is not a property list: " + path);
                return null;
            }
            String q = (String) p.getProperty(userWD);
            if (q.endsWith(ftpdControl.slash) || q.endsWith(ftpdControl.backslash)) {
                return q.substring(1);
            } else {
                return q;
            }
        } else {
            // this does only apply for the root path
            if (!userWD.equals(ftpdControl.slash)) {
                System.out.println("wrong ftpd.groups configuration: path declaration for user '" + user + "' contains no symbolic link declaration other than root path");
                return null;
            }
            if (path.length() > 3 &&
                (path.endsWith(ftpdControl.slash) || path.endsWith(ftpdControl.backslash))) {
                path = path.substring(0, path.length() - 1);
            }
            return path;
        }
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
