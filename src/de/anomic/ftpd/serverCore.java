// serverCore.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2002-2004
// last major change: 09.03.2004
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;

public class serverCore implements Runnable {

	// class variables
	private ServerSocket socket; // listener
	private int maxSessions = 0; // max. number of sessions; 0=unlimited
	private int sessionCounter = 0; // counter that enumerates the running
									// sessions
	private static int loglevel = 2; // log level
	private serverSwitch switchboard; // external values
	private int timeout; // connection time-out of the socket
	private Hashtable activeThreads; // contains the active threads
	private Hashtable sleepingThreads; // contains the threads that are alive
										// since the sleepthreashold
	private boolean termSleepingThreads; // if true then threads over
											// sleepthreashold are killed
	private final int sleepthreshold = 30000; // after that time a thread is
												// considered as beeing sleeping
												// (1/2 minute)
	private final int sleeplimit = 3600000; // after that time a thread is
											// considered as beeing dead-locked
											// (1 hour)
	private Class commandClass; // the command class (a serverHandler)
	private Class[] commandInitClasses; // the init's methods arguments

	// class initializer
	public serverCore(final int port, final int maxSessions, final int timeout,
			final boolean termSleepingThreads, final String commandClassName,
			final serverSwitch switchboard, final int logl) throws IOException {
		try {
			this.socket = new ServerSocket(port);
		} catch (final java.net.BindException e) {
			System.out.println("FATAL ERROR: " + e.getMessage() + " - probably root access rights needed. check port number");
			System.exit(0);
		}
		try {
			this.commandClass = Class.forName(commandClassName);
			this.commandInitClasses = new Class[] {
					Class.forName("de.anomic.ftpd.serverCore$Session"),
					Class.forName("de.anomic.ftpd.serverSwitch") };
			this.maxSessions = maxSessions;
			this.sessionCounter = 0;
			this.socket.setSoTimeout(0); // unlimited
			this.switchboard = switchboard;
			this.timeout = timeout;
			this.termSleepingThreads = termSleepingThreads;
			loglevel = logl;
			this.activeThreads = new Hashtable();
			this.sleepingThreads = new Hashtable();
		} catch (final java.lang.ClassNotFoundException e) {
			System.out.println("FATAL ERROR: " + e.getMessage()
					+ " - Class Not Found");
			System.exit(0);
		}
	}

	// helper methods for system date
	public static final SimpleDateFormat logFormatter = new SimpleDateFormat(
			"yyyyMMdd HH:mm:ss", Locale.ENGLISH);

	public static String logDate() {
		return logFormatter.format(new Date());
	}

	private void printlog(final int level, final String a, final String message) {
		// 0 - print connection statements and error messages only
		// 1 - print also download/upload information
		// 2 - print every command received/sent on telnet channel
		if (level <= loglevel) {
			System.out.println(logDate() + " " + a + " " + message);
		}
	}

	// class body
	public void run() {
		printlog(0, "*", "server started");
		try {
			Thread t;
			do {
				// prepare for new connection
				printlog(1, "*", "waiting for connections, "
						+ this.sessionCounter + " sessions running, "
						+ this.sleepingThreads.size() + " sleeping");
				final Socket controlSocket = this.socket.accept();
				controlSocket.setSoTimeout(this.timeout);
				Session connection;
				synchronized (this) {
					try {
						connection = new Session(controlSocket,
								this.switchboard);
					} catch (final IOException e) {
						printlog(0, "*", "ERROR: " + e.getMessage());
						continue;
					}
				}
				// start the thread
				t = new Thread(connection);
				t.start();
				this.activeThreads.put(t, new Long(System.currentTimeMillis()));
				// idle until number of maximal threads is (again) reached
				synchronized (this) {
					while ((this.maxSessions > 0)
							&& (this.sessionCounter >= this.maxSessions)) {
						try {
							wait(1000);
						} catch (final InterruptedException e) {
						}
					}
				}
			} while (true);
		} catch (final IOException e) {
			System.err.println("ERROR: " + e.getMessage());
		}
		printlog(0, "*", "terminated");
	}

	// idle sensor: the thread is idle if there are no sessions running
	public boolean idle() {
		// simple check
		if (this.sessionCounter == 0)
			return true;

		// complex check
		Enumeration threadEnum;
		Thread t;
		// look for sleeping threads
		threadEnum = this.activeThreads.keys();
		while (threadEnum.hasMoreElements()) {
			t = (Thread) (threadEnum.nextElement());
			if (t.isAlive()) {
				// check the age of the thread
				if (System.currentTimeMillis()
						- ((Long) this.activeThreads.get(t)).longValue() > this.sleepthreshold) {
					// move thread from the active threads to the sleeping
					this.sleepingThreads.put(t, this.activeThreads.remove(t));
				}
			} else {
				// the thread is dead, remove it
				this.activeThreads.remove(t);
			}
		}

		// look for dead threads
		threadEnum = this.sleepingThreads.keys();
		while (threadEnum.hasMoreElements()) {
			t = (Thread) (threadEnum.nextElement());
			if (t.isAlive()) {
				// check the age of the thread
				if (System.currentTimeMillis()
						- ((Long) this.sleepingThreads.get(t)).longValue() > this.sleeplimit) {
					// kill the thread
					if (this.termSleepingThreads) {
						t.interrupt(); // hopefully this wakes him up.
					}
					this.sleepingThreads.remove(t);
				}
			} else {
				// the thread is dead, remove it
				this.sleepingThreads.remove(t);
			}
		}

		if ((this.sessionCounter == 0) && (this.activeThreads.size() > 0)) {
			System.out
					.println("ERROR at idle-test: active Thread estimation wrong ("
							+ this.activeThreads.size() + ")");
		}

		// finally return a heuristic
		return (this.activeThreads.size() == 0);
	}

	public class Session implements Runnable {

		private String request; // current command line
		private int commandCounter; // for logging: number of commands in this
									// session
		private Object cmdObject; // the initialized instance of the
									// commandClass; executes the session's
									// commands
		private String identity; // a string that identifies the client (i.e.
									// ftp: account name)
		public Socket controlSocket; // dialog socket
		public InetAddress userAddress; // the address of the client
		public PushbackInputStream in; // on control input stream
		public OutputStream out; // on control output stream, autoflush

		public Session(final Socket controlSocket,
				final serverSwitch switchboard) throws IOException {
			this.identity = "-";
			this.userAddress = controlSocket.getInetAddress();
			// String ipname = userAddress.getHostAddress();
			// check if we want to allow this socket to connect us
			this.controlSocket = controlSocket;
			this.in = new PushbackInputStream(controlSocket.getInputStream());
			this.out = controlSocket.getOutputStream();
			this.commandCounter = 0;
			// initiate the command class
			// we pass the input and output stream to the commands,
			// so that they can take over communication, if needed
			try {
				final Object[] initObjects = { this, switchboard };
				this.cmdObject = serverCore.this.commandClass.getConstructor(new Class[0]).newInstance((Object[]) null);
				serverCore.this.commandClass.getMethod("init", serverCore.this.commandInitClasses).invoke(this.cmdObject, initObjects);
			} catch (final java.lang.NoSuchMethodException e) {
				System.out.println("FATAL ERROR: No Such Method - " + e.getMessage());
				System.exit(0);
			} catch (final java.lang.InstantiationException e) {
				System.out.println("FATAL ERROR: Wrong Instantiation - " + e.getMessage());
				System.exit(0);
			} catch (final java.lang.IllegalAccessException e) {
				System.out.println("FATAL ERROR: Illegal Access - " + e.getMessage());
				System.exit(0);
			} catch (final java.lang.reflect.InvocationTargetException e) {
				System.out.println("FATAL ERROR: Wrong Invocation - " + e.getMessage());
				System.exit(0);
			}
		}

		public void setIdentity(final String id) {
			this.identity = id;
		}

		public void log(final int level, final boolean outgoing,
				final String request) {
			printlog(level, this.userAddress.getHostAddress() + "/"
					+ this.identity, "[" + serverCore.this.sessionCounter
					+ ", " + this.commandCounter
					+ ((outgoing) ? "] > " : "] < ") + request);
		}

		public void writeLine(final String messg) throws IOException {
			send(this.out, messg);
			log(2, true, messg);
		}

		public byte[] readLine() {
			return receive(this.in);
		}

		public final void run() {
			serverCore.this.sessionCounter++;
			try {
				listen();
			} finally {
				try {
					this.controlSocket.close();
				} catch (final IOException e) {
					System.err.println("ERROR: (internal) " + e);
				}
				synchronized (this) {
					this.notify();
				}
			}
			serverCore.this.sessionCounter--;
		}

		private void listen() {
			try {
				// set up some reflection
				final Class[] stringType = { "".getClass() };
				final Class[] exceptionType = { Class
						.forName("java.lang.Throwable") };
				// System.out.println("commandClass" + ((commandClass == null) ?
				// "null" : commandClass.toString()));
				final Method greeting = serverCore.this.commandClass.getMethod(
						"greeting", (Class[]) null);
				final Method error = serverCore.this.commandClass.getMethod(
						"error", exceptionType);

				// send greeting
				Object result = greeting.invoke(this.cmdObject, new Object[0]);
				if (result != null) {
					if ((result instanceof String)
							&& (((String) result).length() > 0)) {
						writeLine((String) result);
					}
				}

				// start dialog
				byte[] requestBytes = null;
				boolean terminate = false;
				int pos;
				String cmd;
				String tmp;
				final Object[] stringParameter = new String[1];
				while ((this.in != null)
						&& ((requestBytes = readLine()) != null)) {
					this.commandCounter++;
					this.request = new String(requestBytes);
					log(2, false, this.request);
					try {
						pos = this.request.indexOf(' ');
						if (pos < 0) {
							cmd = this.request.trim().toUpperCase();
							stringParameter[0] = "";
						} else {
							cmd = this.request.substring(0, pos).trim().toUpperCase();
							stringParameter[0] = this.request.substring(pos).trim();
						}

						// exec command and return value
						result = serverCore.this.commandClass.getMethod(cmd, stringType).invoke(this.cmdObject, stringParameter);
						if (result == null) {
							/*
							 * log(2, true, "(NULL RETURNED/STREAM PASSED)");
							 */
							break;
						} else if (result instanceof String) {
							if (((String) result).startsWith("!")) {
								result = ((String) result).substring(1);
								terminate = true;
							}
							writeLine((String) result);
						} else if (result instanceof InputStream) {
							tmp = send(this.out, (InputStream) result);
							if ((tmp.length() > 4)
									&& (tmp.toUpperCase().startsWith("PASS"))) {
								log(2, true, "PASS ********");
							} else {
								log(2, true, tmp);
							}
							tmp = null;
						}
						if (terminate) {
							break;
						}
					} catch (final InvocationTargetException ite) {
						// we extract a target exception and let the thread
						// survive
						final Object[] errorParameter = { ite.getTargetException() };
						writeLine((String) error.invoke(this.cmdObject, errorParameter));
					} catch (final NoSuchMethodException nsme) {
						// the client requested a command that does not exist
						final Object[] errorParameter = { nsme };
						writeLine((String) error.invoke(this.cmdObject, errorParameter));
					} catch (final IllegalAccessException iae) {
						// wrong parameters: this can only be an internal
						// problem
						final Object[] errorParameter = { iae };
						writeLine((String) error.invoke(this.cmdObject, errorParameter));
					} catch (final java.lang.ClassCastException e) {
						// ??
						final Object[] errorParameter = { e };
						writeLine((String) error.invoke(this.cmdObject, errorParameter));
					} catch (final Exception e) {
						// whatever happens: the thread has to survive!
						final Object[] errorParameter = { e };
						writeLine((String) error.invoke(this.cmdObject, errorParameter));
					}
				}
			} catch (final java.lang.ClassNotFoundException e) {
				System.out.println("Internal Error: wrapper class not found: " + e.getMessage());
				System.exit(0);
			} catch (final java.lang.NoSuchMethodException e) {
				System.out.println("Internal Error: wrapper does not provide 'greeting' or 'error' method: "
								+ e.getMessage());
				System.exit(0);
			} catch (final java.lang.IllegalAccessException e) {
				System.out.println("Internal Error: wrapper implements 'greeting' or 'error' method wrong: "
								+ e.getMessage());
				System.exit(0);
			} catch (final java.lang.reflect.InvocationTargetException e) {
				System.out.println("Internal Error: wrapper calls 'greeting' or 'error' method wrong: "
								+ e.getMessage());
				System.exit(0);
			} catch (final java.io.IOException e) {
				// connection interruption: more or less normal
			}
		}

	}

	// generic input/output static methods
	public static final byte cr = 13;
	public static final byte lf = 10;
	public static final byte[] crlf = { cr, lf };

	public static byte[] receive(final PushbackInputStream pbis) {
		int bufferSize = 0;
		try {
			while ((bufferSize = pbis.available()) == 0) {
				try {
					Thread.currentThread().join(100);
				} catch (final InterruptedException e) {
				}
			}
		} catch (final IOException e) {
			return null;
		}

		byte[] buffer = new byte[bufferSize];
		byte[] bufferBkp;
		bufferSize = 0;
		int b = 0;

		try {
			while ((b = pbis.read()) > 31) {
				// we have a valid byte in b, add it to the buffer
				if (buffer.length == bufferSize) {
					// the buffer is full, double its size
					bufferBkp = buffer;
					buffer = new byte[bufferSize * 2];
					java.lang.System.arraycopy(bufferBkp, 0, buffer, 0,
							bufferSize);
					bufferBkp = null;
				}
				buffer[bufferSize++] = (byte) b;
			}
			// we have catched a possible line end
			if (b == cr) {
				// maybe a lf follows, read it:
				if ((b = pbis.read()) != lf)
					if (b >= 0) {
						pbis.unread(b); // we push back the byte
					}
			}

			// finally shrink buffer
			bufferBkp = buffer;
			buffer = new byte[bufferSize];
			java.lang.System.arraycopy(bufferBkp, 0, buffer, 0, bufferSize);
			bufferBkp = null;

			// return only the byte[]
			return buffer;
		} catch (final IOException e) {
			return null;
		}
	}

	public static void send(final OutputStream os, final String buf)
			throws IOException {
		os.write(buf.getBytes());
		os.write(crlf);
		os.flush();
	}

	public static String send(final OutputStream os, final InputStream is)
			throws IOException {
		final int bufferSize = is.available();
		final byte[] buffer = new byte[((bufferSize < 1) || (bufferSize > 4096)) ? 4096
				: bufferSize];
		int l;
		while ((l = is.read(buffer)) > 0) {
			os.write(buffer, 0, l);
		}
		os.write(crlf);
		os.flush();
		if (bufferSize > 80)
			return "<LONG STREAM>";
		else
			return new String(buffer);
	}

}
