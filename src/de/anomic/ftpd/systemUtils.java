// systemUtils.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 11.03.2004
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

package de.anomic.ftpd;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Properties;

public class systemUtils {

	// constants for system identification
	public static final int systemMacOSC = 0; // 'classic' Mac OS 7.6.1/8.*/9.*
	public static final int systemMacOSX = 1; // all Mac OS X
	public static final int systemUnix = 2; // all Unix/Linux type systems
	public static final int systemWindows = 3; // all Windows 95/98/NT/2K/XP
	public static final int systemUnknown = -1; // any other system

	// constants for file type identification (Mac only)
	public static final String blankTypeString = "____";

	// system-identification statics
	private static int systemOS = systemUnknown;
	private static boolean isMacArchitecture = false;
	// private static boolean isUnixFS = false;

	// Macintosh-specific statics
	private static Class macMRJFileUtils = null;
	private static Class macMRJOSType = null;
	private static Constructor macMRJOSTypeConstructor = null;
	private static Object macMRJOSNullObj = null;
	private static Method macGetFileCreator = null;
	private static Method macGetFileType = null;
	private static Method macSetFileCreator = null;
	private static Method macSetFileType = null;
	public static Hashtable macFSTypeCache = null;
	public static Hashtable macFSCreatorCache = null;

	// static initialization
	static {
		// check operation system type
		final Properties sysprop = System.getProperties();
		final String sysname = sysprop.getProperty("os.name", "").toLowerCase();
		if (sysname.startsWith("mac os x")) {
			systemOS = systemMacOSX;
		} else if (sysname.startsWith("mac os")) {
			systemOS = systemMacOSC;
		} else if (sysname.startsWith("windows")) {
			systemOS = systemWindows;
		} else if ((sysname.startsWith("linux"))
				|| (sysname.startsWith("unix"))) {
			systemOS = systemUnix;
		} else {
			systemOS = systemUnknown;
		}

		isMacArchitecture = ((systemOS == systemMacOSC) || (systemOS == systemMacOSX));
		// isUnixFS = ((systemOS == systemMacOSX) || (systemOS == systemUnix));

		// set up the MRJ Methods through reflection
		if (isMacArchitecture) {
			try {
				macMRJFileUtils = Class.forName("com.apple.mrj.MRJFileUtils");
				macMRJOSType = Class.forName("com.apple.mrj.MRJOSType");
				macGetFileType = macMRJFileUtils.getMethod("getFileType",
						new Class[] { Class.forName("java.io.File") });
				macGetFileCreator = macMRJFileUtils.getMethod("getFileCreator",
						new Class[] { Class.forName("java.io.File") });
				macSetFileType = macMRJFileUtils.getMethod("setFileType",
						new Class[] { Class.forName("java.io.File"),
								macMRJOSType });
				macSetFileCreator = macMRJFileUtils.getMethod("setFileCreator",
						new Class[] { Class.forName("java.io.File"),
								macMRJOSType });
				macMRJOSTypeConstructor = macMRJOSType
						.getConstructor(new Class[] { Class
								.forName("java.lang.String") });
				final byte[] nullb = new byte[4];
				for (int i = 0; i < 4; i++) {
					nullb[i] = 0;
				}
				macMRJOSNullObj = macMRJOSTypeConstructor
						.newInstance(new Object[] { new String(nullb) });
				macFSTypeCache = new Hashtable();
				macFSCreatorCache = new Hashtable();
			} catch (final Exception e) {
				// e.printStackTrace();
				macMRJFileUtils = null;
				macMRJOSType = null;
			}
		}
	}

	public static Object getMacOSTS(final String s) {
		if ((isMacArchitecture) && (macMRJFileUtils != null)) {
			try {
				if ((s == null) || (s.equals(blankTypeString)))
					return macMRJOSNullObj;
				else
					return macMRJOSTypeConstructor
							.newInstance(new Object[] { s });
			} catch (final Exception e) {
				return macMRJOSNullObj;
			}
		} else
			return null;
	}

	public static String getMacFSType(final File f) {
		if ((isMacArchitecture) && (macMRJFileUtils != null)) {
			try {
				final String s = macGetFileType
						.invoke(null, new Object[] { f }).toString();
				if ((s == null) || (s.charAt(0) == 0))
					return blankTypeString;
				else
					return s;
			} catch (final Exception e) {
				return null;
			}
		} else
			return null;
	}

	public static String getMacFSCreator(final File f) {
		if ((isMacArchitecture) && (macMRJFileUtils != null)) {
			try {
				final String s = macGetFileCreator.invoke(null,
						new Object[] { f }).toString();
				if ((s == null) || (s.charAt(0) == 0))
					return blankTypeString;
				else
					return s;
			} catch (final Exception e) {
				return null;
			}
		} else
			return null;
	}

	public static void setMacFSType(final File f, final String t) {
		if ((isMacArchitecture) && (macMRJFileUtils != null)) {
			try {
				macSetFileType.invoke(null, new Object[] { f, getMacOSTS(t) });
			} catch (final Exception e) {/*
										 * System.out.println(e.getMessage());
										 * e.printStackTrace();
										 */
			}
		}
	}

	public static void setMacFSCreator(final File f, final String t) {
		if ((isMacArchitecture) && (macMRJFileUtils != null)) {
			try {
				macSetFileCreator.invoke(null,
						new Object[] { f, getMacOSTS(t) });
			} catch (final Exception e) {/*
										 * System.out.println(e.getMessage());
										 * e.printStackTrace();
										 */
			}
		}
	}

	public static boolean aquireMacFSType(final File f) {
		if ((!(isMacArchitecture)) || (macMRJFileUtils == null))
			return false;
		final String name = f.toString();

		// check file type
		final int dot = name.lastIndexOf(".");
		if ((dot < 0) || (dot + 1 >= name.length()))
			return false;
		final String type = getMacFSType(f);
		if ((type == null) || (type.equals(blankTypeString)))
			return false;
		final String ext = name.substring(dot + 1).toLowerCase();
		final String oldType = (String) macFSTypeCache.get(ext);
		if ((oldType != null) && (oldType.equals(type)))
			return false;
		macFSTypeCache.put(ext, type);
		return true;
	}

	public static boolean aquireMacFSCreator(final File f) {
		if ((!(isMacArchitecture)) || (macMRJFileUtils == null))
			return false;
		final String name = f.toString();

		// check creator
		final String creator = getMacFSCreator(f);
		if ((creator == null) || (creator.equals(blankTypeString)))
			return false;
		final String oldCreator = (String) macFSCreatorCache.get(name);
		if ((oldCreator != null) && (oldCreator.equals(creator)))
			return false;
		macFSCreatorCache.put(name, creator);
		return true;
	}

	public static boolean applyMacFSType(final File f) {
		if ((!(isMacArchitecture)) || (macMRJFileUtils == null))
			return false;
		final String name = f.toString();

		// reconstruct file type
		final int dot = name.lastIndexOf(".");
		if ((dot < 0) || (dot + 1 >= name.length()))
			return false;
		final String type = (String) macFSTypeCache.get(name.substring(dot + 1)
				.toLowerCase());
		if (type == null)
			return false;
		final String oldType = getMacFSType(f);
		if ((oldType != null) && (oldType.equals(type)))
			return false;
		setMacFSType(f, type);
		return getMacFSType(f).equals(type);
	}

	public static boolean applyMacFSCreator(final File f) {
		if ((!(isMacArchitecture)) || (macMRJFileUtils == null))
			return false;
		final String name = f.toString();

		// reconstruct file creator
		final String creator = (String) macFSCreatorCache.get(name);
		if (creator == null)
			return false;
		final String oldCreator = getMacFSCreator(f);
		if ((oldCreator != null) && (oldCreator.equals(creator)))
			return false;
		// System.out.println("***Setting creator for " + f.toString() + " to "
		// + creator);
		setMacFSCreator(f, creator);
		return getMacFSCreator(f).equals(creator); // this is not always true! I
													// guess it's caused by
													// deprecation of the
													// interface in 1.4er Apple
													// Extensions
	}

	public static void main(final String[] args) {
		// try{System.getProperties().list(new PrintStream(new
		// FileOutputStream(new File("system.properties.txt"))));} catch
		// (FileNotFoundException e) {}
		// System.out.println("nullstr=" + macMRJOSNullObj.toString());
		if (args.length != 1) {
			System.exit(0);
		}
		final File f = new File(args[0]);
		System.out.println("File " + f.toString() + ": creator = "
				+ getMacFSCreator(f) + "; type = " + getMacFSType(f));
	}

}

/*
 * table of common system properties comparisment between different operation
 * systems
 * 
 * property |Mac OS 9.22 |Mac OSX 10.1.5 |Windows 98 |Linux Kernel 2.4.22 |
 * ------
 * -------------+----------------------+----------------------+------------
 * ----------+----------------------+ file.encoding |MacTEC |MacRoman |Cp1252
 * |ANSI_X3.4-1968 | file.separator |/ |/ |\ |/ | java.class.path |/hdisc/... |.
 * |. |/usr/lib/j2se/ext | java.class.version |45.3 |47.0 |48.0 |47.0 |
 * java.home |/hdisc/... |/System/Library/... |C:\PROGRAM\...
 * |/usr/lib/j2se/1.3/jre | java.vendor |Apple Computer, Inc. |Apple Computer,
 * Inc. |Sun Microsystems Inc. |Blackdown Java-Linux | java.version |1.1.8
 * |1.3.1 |1.4.0_02 |1.3.1 | os.arch |PowerPC |ppc |x86 |i386 | os.name |Mac OS
 * |Mac OS X |Windows 98 |Linux | os.version |9.2.2 |10.1.5 |4.10 |2.4.22 |
 * path.separator |: |: |; |: | user.dir |/hdisc/... |/mydir/... |C:\mydir\...
 * |/home/public | user.home |/hdisc/... |/Users/myself |C:\WINDOWS
 * |/home/public | user.language |de |de |de |en | user.name |Bob |myself |User
 * |public | user.timezone |ECT |Europe/Berlin |Europe/Berlin | |
 * ----------------
 * ---+----------------------+----------------------+------------
 * ----------+----------------------+
 */
