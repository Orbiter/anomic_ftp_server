/**
 *  cachedClassLoader
 *  Copyright 2004 by Michael Peter Christen,
 *  mc@anomic.de, Frankfurt a. M., Germany
 *  first published on http://www.anomic.de
 *  last major change: 11.03.2004
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
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;

public class cachedClassLoader extends ClassLoader {

	Hashtable classes;

	public cachedClassLoader() {
		super();
		this.classes = new Hashtable();
	}

	public cachedClassLoader(final ClassLoader parent) {
		super(parent);
		this.classes = new Hashtable();
	}

	public Class loadClass(String classkey) throws ClassNotFoundException {
		// we consider that the classkey can either be only the name of a class,
		// or a partial or
		// complete path to a class file

		// normalize classkey: strip off '.class'
		if (classkey.endsWith(".class")) {
			classkey = classkey.substring(0, classkey.length() - 6);
		}

		// try to find the class in the hashtable
		Class c = (Class) this.classes.get(classkey);
		if (c != null)
			return c;

		// consider classkey as a file and extract the file name
		File classfile = new File(classkey);
		// this file cannot exist for real, since we stripped off the .class
		// we constructed the classfile for the only purpose to strip off the
		// name:

		// get the class name out of the classfile
		final String classname = classfile.getName();

		// now that we have the name, we can create the real class file
		classfile = new File(classkey + ".class");

		// first try: take the class out of the cache, denoted by the classname
		c = findLoadedClass(classname);
		if (c == null) {
			try {
				// second try: ask the system
				c = findSystemClass(classname);
			} catch (final ClassNotFoundException e) {
				// third try: load the file from the file system
				final int length = (int) classfile.length();
				final byte[] b = new byte[length];
				try {
					final InputStream is = new FileInputStream(classfile);
					is.read(b, 0, b.length);
					is.close();
					// now make a class out of the stream
					c = defineClass(null, b, 0, b.length);
					resolveClass(c);
					this.classes.put(classkey, c);
				} catch (final IOException ee) {
					throw new ClassNotFoundException(classkey);
				}
			}
		}
		return c;
	}

}