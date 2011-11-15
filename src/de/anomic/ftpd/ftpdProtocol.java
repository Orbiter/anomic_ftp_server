/**
 *  ftpdProtocol
 *  Copyright 2004 by Michael Peter Christen,
 *  mc@anomic.de, Frankfurt a. M., Germany
 *  first published on 27.02.2004 http://www.anomic.de
 *  last major change: 20.11.2010
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Hashtable;
import java.util.StringTokenizer;

public class ftpdProtocol extends ftpdControl implements serverHandler {

    // static objects
    private static final int bufferSize = 4048;

    // class objects
    private serverCore.Session session; // holds the session object of the
                                        // calling class
    private int state = 0;              // enum { user?, pass?, port?, file? }
    private String renameFrom = null;   // for RNFR/RNTO
    private InetAddress datahost;       // from last PORT or PASV
    private int dataport;               // from last PORT or PASV
    private ServerSocket ssock;         // in pasv mode the listening server
    private boolean passive = false;    // transfer initiated by port, otherwise with pasv

    // class methods
    public ftpdProtocol() {
        super();
        this.ssock = null;
    }

    public void init(final serverCore.Session session, final serverSwitch switchboard) throws IOException {
        super.switchboard = switchboard;
        super.userAddress = session.userAddress;
        this.session = session;

        // check if we want to allow this socket to connect us
        final String clients = switchboard.getConfig("clients", "*");
        if (clients.length() > 1) {
            final String ipname = this.userAddress.getHostAddress();
            if (clients.indexOf(ipname) < 0) {
                printlog(0, "CONNECTION ATTEMPT FROM " + ipname + " DENIED");
                throw new IOException("CONNECTION FROM " + ipname + " FORBIDDEN");
            }
        }

        init();
    }

    public String greeting() { // OBLIGATORIC FUNCTION
        // a response line upon connection is send to client
        // if no response line is wanted, return "" or null
        try {
            this.session.writeLine("220-" + this.switchboard.getConfig("welcome", "Welcome to the AnomicFTPD FTP Server!"));
            this.session.writeLine(" " + ftpd.copyright);
            this.session.writeLine(" " + "System: " + ftpd.systemOSType());
            this.session.writeLine("220 ready");
        } catch (final IOException e) {
        }
        return null;
    }

    public String error(final Throwable e) { // OBLIGATORIC FUNCTION
        // return string in case of any error that occurs during communication
        // is always (but not only) called if an IO-dependent exception occurrs.
        // e.printStackTrace(); // debug
        if (e instanceof InterruptedIOException)
            return "450 i/o timeout"; // or 421?
        else if (e instanceof NoSuchMethodException) {
            //e.printStackTrace();
            return "550 command not implemented";
        } else {
            // e.printStackTrace();
            return "550 " + e;
        }
    }

    private void init() {
        this.state = 0;
        this.userWD = ftpdControl.slash;
        this.opts = new Hashtable();
    }

    /*
     * private String argument(String request) { // cmd space+ argument int n =
     * request.indexOf(' '); if (n < 0) return ""; while (request.charAt(n) ==
     * ' ') if (++n >= request.length()) return ""; return request.substring(n);
     * }
     */
    private void copy(final OutputStream out, final InputStream in) throws IOException {
        final InputStream bIn = new BufferedInputStream(in, bufferSize);
        final OutputStream bOut = new BufferedOutputStream(out, bufferSize);
        final byte buf[] = new byte[bufferSize];
        int n;
        while ((n = bIn.read(buf)) > 0) {
            bOut.write(buf, 0, n);
        }
        bIn.close();
        bOut.close();
    }

    public String ALLO(final String arg) throws IOException {
        return "200 ok";
    }

    public String CWD(final String arg) throws IOException {
        if (this.state < 2) return "530 not logged in";
        final String path = decodeChar(arg);
        if (path.length() == 0) return "501 syntax error";
        String s = appendUserFile(path);
        if (!s.endsWith(ftpdControl.slash)) s = s+ ftpdControl.slash;
        final File dir = serverFile(s);
        if (!dir.isDirectory() || !dir.canRead()) return "550 \"" + path + "\" bad path";
        this.userWD = s;
        if (this.userWD.length() == 0) this.userWD = ftpdControl.slash;
        printlog(3, " INFO: new server-path " + dir.toString());
        return "250 \"" + this.userWD + "\" is working directory";
    }

    public String CDUP(final String arg) throws IOException {
        if (this.state < 2) return "530 not logged in";
        final String s = removeUserSubPath();
        if (this.userWD.equals(s)) return "550 CDUP not applicable; path is root";
        this.userWD = s;
        if (s.length() == 0) this.userWD = ftpdControl.slash;
        printlog(3, " INFO: new server-path " + serverFile(this.userWD));
        return "250 \"" + this.userWD + "\" is working directory";
    }

    public String DELE(final String arg) throws IOException {
        if (this.state < 2) return "530 not logged in";
        final String path = decodeChar(arg);
        if (path.length() == 0) return "501 syntax error";
        final File file = serverFile(path);
        if (!eventDeleteFilePre(file)) return "550 no permission to delete";
        if (!file.isDirectory() && file.delete()) {
            eventDeleteFilePost(file);
            return "250 " + appendUserFile(path) + " deleted";
        } else {
            return "550 \"" + path + "\" bad path";
        }
    }

    public String EPRT(final String arg) throws IOException, UnknownHostException {
        if (this.state < 2) return "530 not logged in";
        char delim = arg.charAt(0);
        StringTokenizer st = new StringTokenizer(arg.substring(1), "" + delim);
        if (st.countTokens() < 3) return "501 syntax error";
        st.nextToken(); // not used, java can handle the ip address without steering of net-prt
        this.datahost = InetAddress.getByName(st.nextToken());
        this.dataport = Integer.parseInt(st.nextToken());
        
        this.state = 3;
        this.passive = false;
        return "200 received EPRT";
    }
    
    public String EPSV(final String arg) throws IOException, UnknownHostException {
        // we ignore the optional net-prt argument, it is not needed here
        
        // create a data socket and bind it to free port available
        this.dataport = this.switchboard.getConfigInt("dataport", 0);
        if (this.dataport != 0 && this.ssock != null && this.ssock.isBound() && this.ssock.getLocalPort() == this.dataport) {
            // we already have a server socket at the right address
        } else while (true) {
            try { 
                this.ssock = new ServerSocket(this.dataport);
                this.ssock.setSoTimeout(3000);
                this.ssock.setReuseAddress(this.switchboard.getConfigInt("dataport", 0) != 0);
                break;
            } catch (final Exception e) {
                e.printStackTrace();
                if (this.switchboard.getConfigInt("dataport", 0) == 0) {
                    this.dataport++;
                } else try {Thread.sleep(500);} catch (InterruptedException e1) {}
                continue;
            }
        }

        // get port socket has been bound to
        this.dataport = this.ssock.getLocalPort();
        //System.out.println("DATAPORT = " + this.dataport);
        
        // client ip
        String datahostcnfg = this.switchboard.getConfig("datahost", null);
        this.datahost = (datahostcnfg == null) ? InetAddress.getLocalHost() : InetAddress.getByName(datahostcnfg);

        // send port command via control socket:
        // four ip address shorts encoded and two port shorts encoded
        this.state = 3;
        this.passive = true;
        return "229 Entering Extended Passive Mode (|||" + this.dataport + "|)";
    }
    
    public String FEAT(final String arg) throws IOException {
        return "211-Recognized extended commands:\r\n" + " MDTM\r\n"
                + " MFMT\r\n" + " SIZE\r\n" + " UTF8\r\n" +
                // " XCUP\r\n" +
                // " XMKD\r\n" +
                // " XPWD\r\n" +
                // " XRMD\r\n" +
                // MLST size*;create;modify*;perm;media-type
                "211 see www.anomic.de for latest version of AnomicFTPD!";
    }

    public String HELP(final String arg) throws IOException {
        return "214-Recognized commands:\r\n"
                + "    ALLO  CWD   CDUP  DELE  EPRT  EPSV  FEAT  HELP  OPTS\r\n"
                + "    LIST  MDTM  MFMT  MKD   NLST  NOOP  PASS\r\n"
                + "    PASV  PORT  PWD   QUIT  RETR  RNFR  RNTO\r\n"
                + "    RMD   SITE  SIZE  STAT  STOR  SYST  TYPE\r\n"
                + "    UTF8  USER  XCUP  XMKD  XPWD  XRMD\r\n"
                + "214 see www.anomic.de for latest version of AnomicFTPD!";
    }
    
    public String LIST(final String arg) throws IOException {
        return list(arg, true);
    }

    private String list(final String arg, final boolean full) throws IOException {
        if (this.state < 2) return "530 not logged in";
        if (this.state == 2) return "503 need PORT first";
        String path = decodeChar(arg);
        if (path.startsWith("-")) {
            // someone probably tried a unix command option here, since we
            // return almost ls -la this should
            // be sufficient to simply ignore it
            path = "";
        }
        final File file = serverFile(path); // this makes an absolute path out of the relative
        final String listing = ls(file, "", full); // nullpointer exception
        this.session.writeLine("150 opening ASCII data connection");
        // distinguish active or passive mode
        Socket data;
        if (this.passive) {
            data = this.ssock.accept();
        } else {
            data = new Socket(this.datahost, this.dataport);
        }
        data.setSoTimeout(ftpd.timeoutDataConnection);
        final PrintWriter pout = new PrintWriter(new OutputStreamWriter(data.getOutputStream()));
        pout.print(listing);
        pout.flush();
        data.close();
        this.state = 2;
        return "226 closing data connection";
    }

    public String MDTM(final String arg) throws IOException {
        // acording to "draft-ietf-ftpext-mlst-16.txt"
        if (this.state < 2)
            return "530 not logged in";
        final String path = decodeChar(arg);
        if (path.length() == 0)
            return "501 syntax error";
        final File f = serverFile(path);
        if (f.exists())
            return "213 " + MDTMFormatter.format(new Date(f.lastModified())); // YYYYMMDDHHMMSS[.sss]
                                                                                // (GMT)
        else
            return "550 \"" + path + "\" bad path";
    }

    public String MFMT(final String arg) throws IOException {
        // Modify File Modification Time (MFMT)
        // according to 'draft-somers-ftp-mfxx-00: The 'MFxx' Command Extensions for FTP'
        // see http://www.trevezel.com/downloads/draft-somers-ftp-mfxx-00.html
        if (this.state < 2) return "530 not logged in";
        if (!(ftpdPermissions.permissionWrite(this.user))) return "501 no permission to write";
        final String args[] = argument2args(decodeChar(arg));
        if (args.length != 2) return "501 missing parameter: MFMT yyyyMMddHHmmss <file>";
        final File file = serverFile(args[1]);
        if (!(file.exists())) return "501 MFMT argument error: " + appendUserFile(args[1]) + " does not exist";
        if (args[0].length() != 14) return "501 MFMT date/time parameter syntax error: use yyyyMMddHHmmss";
        long date;
        try {
            date = MFMTFormatter.parse(args[0]).getTime();
        } catch (final java.text.ParseException e) {
            return "501 MFMT date/time parameter syntax error: use yyyyMMddHHmmss";
        }
        try { // a Java 1.2 function call inside
            if (file.setLastModified(date))
                return "213 ModifyTime=" + args[0] + " " + appendUserFile(args[1]);
            else
                return "501 MFMT " + args[0] + " " + appendUserFile(args[1]) + " FAILED";
        } catch (final NoSuchMethodError e) {
            printlog(0, "IRREGULARITY: setLastModified not supported (java version too low, you need Java-2)");
            return "501 java 2 (jdk 1.2 - compliant) is necessary to perform this function";
        }
    }

    public String MKD(final String arg) throws IOException {
        if (this.state < 2) return "530 not logged in";
        final String path = decodeChar(arg);
        if (path.length() == 0) return "501 syntax error";
        final File dir = serverFile(path);
        if (!eventMakeFolderPre(dir)) return "550 no write permission";
        if (!dir.exists() && dir.mkdir()) {
            eventMakeFolderPost(dir);
            return "250 " + appendUserFile(path) + " created";
        } else {
            return "550 \"" + path + "\" bad path";
        }
    }

    public String NLST(final String arg) throws IOException {
        return list(arg, false);
    }

    public String NOOP(final String arg) throws IOException {
        return "200 ok";
    }

    public String OPTS(final String arg) throws IOException {
        if (this.state < 2) return "530 not logged in";
        final String args[] = argument2args(decodeChar(arg));
        if (args.length == 0) return "501 no arguments given for OPTS";
        if (args.length > 2) return "501 too many arguments given for OPTS";
        if (this.opts.get("UTF-8") != null || this.opts.get("UTF8") != null) {
            ftpd.charcoding = "UTF-8";
        }
        if (args.length == 1) {
            this.opts.put(args[0].toUpperCase(), null);
            return "200 property set";
        } else {
            this.opts.put(args[0].toUpperCase(), args[1].toUpperCase());
            return "200 property set";
        }
    }

    public String PASS(final String arg) throws IOException {
        if (this.state != 1) return "503 need USER first";
        if (this.user == null) return "503 need USER first";
        final String pw = ftpdPermissions.getPassword(this.user);
        if (pw == null || (pw.length() > 0 && !pw.equals("*") && !arg.equals(pw))) {
            this.session.wrongLogin();
            long penaltyWait = this.session.penaltyLogin();
            if (penaltyWait < 0) {
                return "!221 goodbye";
            }
            printlog(0, "user \"" + this.user + "\": attempt to log in denied");
            this.state = 0;
            if (penaltyWait > 0) {
                this.session.penaltySleep(penaltyWait);
            }
            return "530 authorization failed. not logged in";
        }
        this.userWD = ftpdControl.slash;
        File root = serverPath();
        if (root == null) {
            this.session.wrongLogin();
            printlog(0, "user \"" + this.user + "\": log in denied, no home path assigned");
            this.state = 0;
            return "530 home directory does not exist. not logged in";
        }
        if (root.isAbsolute() && !root.exists() && ftpdPermissions.permissionWrite(this.user)) {
            // create an anonymous access directory on-the-fly
            if (root.mkdirs()) {
                printlog(0, " ATTENTION: the path \""
                        + root.toString() + "\" for the group \""
                        + ftpdPermissions.getGroup(this.user)
                        + "\" has been generated");
            }
        }
        if (!root.isAbsolute() || !root.isDirectory() || !root.canRead()) {
            this.session.wrongLogin();
            printlog(0, "user \"" + this.user + "\": log in denied, no home path \"" + root.toString() + "\"");
            this.state = 0;
            return "530 home directory does not exist. not logged in";
        } else {
            this.session.successfulLogin();
            // userRights = new ftpdPermissions(user);
            printlog(0, "user \"" + this.user + "\": logged in");
            printlog(3, "INFO: root for user \"" + this.user + "\":" + root.toString());
            this.state = 2;
            return "230 logged in";
        }
    }

    public String PASV(final String arg) throws IOException {
        // create a data socket and bind it to free port available
        this.dataport = this.switchboard.getConfigInt("dataport", 0);
        if (this.dataport != 0 && this.ssock != null && this.ssock.isBound() && this.ssock.getLocalPort() == this.dataport) {
            // we already have a server socket at the right address
        } else while (true) {
            try { 
                this.ssock = new ServerSocket(this.dataport);
                this.ssock.setSoTimeout(3000);
                this.ssock.setReuseAddress(this.switchboard.getConfigInt("dataport", 0) != 0);
                break;
            } catch (final Exception e) {
                e.printStackTrace();
                if (this.switchboard.getConfigInt("dataport", 0) == 0) {
                    this.dataport++;
                } else try {Thread.sleep(500);} catch (InterruptedException e1) {}
                continue;
            }
        }

        // get port socket has been bound to
        this.dataport = this.ssock.getLocalPort();
        //System.out.println("DATAPORT = " + this.dataport);
        
        // client ip
        String datahostcnfg = this.switchboard.getConfig("datahost", null);
        this.datahost = (datahostcnfg == null) ? InetAddress.getLocalHost() : InetAddress.getByName(datahostcnfg);
        
        // save ip address in high byte order
        final byte[] Bytes = (ftpd.router_ip == null) ? this.datahost.getAddress() : ftpd.router_ip.getAddress();

        // bytes greater than 127 should not be printed as negative
        final short Shorts[] = new short[4];
        for (int i = 0; i < 4; i++) {
            Shorts[i] = Bytes[i];
            if (Shorts[i] < 0) {
                Shorts[i] += 256;
            }
        }

        // send port command via control socket:
        // four ip address shorts encoded and two port shorts encoded
        this.state = 3;
        this.passive = true;
        return "227 Entering Passive Mode (" + Shorts[0] + "," + Shorts[1]
                + "," + Shorts[2] + "," + Shorts[3] + ","
                + ((this.dataport & 0xff00) >> 8) + ","
                + (this.dataport & 0x00ff) + ")";
    }

    public String PORT(final String arg) throws IOException,
            UnknownHostException {
        if (this.state < 2) return "530 not logged in";
        final StringTokenizer st = new StringTokenizer(arg, ",");
        if (st.countTokens() < 6) return "501 syntax error";
        final int a = Integer.parseInt(st.nextToken());
        final int b = Integer.parseInt(st.nextToken());
        final int c = Integer.parseInt(st.nextToken());
        final int d = Integer.parseInt(st.nextToken());
        this.datahost = InetAddress.getByName(a + "." + b + "." + c + "." + d);
        final int high = Integer.parseInt(st.nextToken());
        final int low = Integer.parseInt(st.nextToken());
        if (high < 0 || high > 255 || low < 0 || low > 255)
            return "501 syntax error";
        this.dataport = (high << 8) + low;
        this.state = 3;
        this.passive = false;
        return "200 received PORT";
    }

    public String PWD(final String arg) throws IOException {
        if (this.state < 2) return "530 not logged in";
        printlog(3, "INFO: new server-path " + this.userWD);
        return "257 \"" + this.userWD + "\" is working directory";
    }

    public String QUIT(final String arg) {
        return "!221 goodbye";
    }

    /*
     * public String REST(String arg) throws IOException { }
     */

    public String REIN(final String arg) throws IOException {
        init();

        ftpd.charcoding = this.switchboard.getConfig("charcoding", null);
        if ((ftpd.charcoding != null) && (ftpd.charcoding.equals("NONE"))) {
            ftpd.charcoding = null;
        }
        return "200 please login";
    }

    public String RETR(final String arg) throws IOException {
        // retrieve file
        // still missing: effect of REST command
        if (this.state < 2)
            return "530 not logged in";
        if (this.state == 2)
            return "503 need PORT first";
        final String path = decodeChar(arg);
        if (path.length() == 0)
            return "501 syntax error";
        final File file = serverFile(path);
        if (!eventDownloadFilePre(file))
            return "550 no read permission";
        InputStream ind = null;
        // if an 'index.html' file is demanded, but no one exist, then create
        // one
        boolean indexCreated = false;
        if ((file.getName().toLowerCase().equals("index.html"))
                && (!file.exists())
                && (this.switchboard.getConfig("createindex", "").toUpperCase().equals("TRUE"))) {
            // create the index file
            ind = createIndex(file);
            indexCreated = true;
        }
        if ((indexCreated) || ((file.isFile()) && (file.exists()))) {
            // long start =
            // GregorianCalendar.getInstance(GMTTimeZone).getTime().getTime();
            final InputStream in = indexCreated ? ind : new FileInputStream(file);
            final long filelength = indexCreated ? in.available() : file.length();
            this.session.writeLine("150 opening BINARY data connection for " + appendUserFile(path) + ", " + filelength + " bytes");
            // active or passive?
            Socket data;
            if (this.passive) {
                data = this.ssock.accept();
            } else {
                data = new Socket(this.datahost, this.dataport);
            }
            data.setSoTimeout(ftpd.timeoutDataConnection);
            copy(data.getOutputStream(), in);
            in.close();
            data.close();
            if (this.passive) {
                this.ssock.close();
                this.ssock = null;
            }
            eventDownloadFilePost(file, filelength);
            this.state = 2;
            return "226 closing data connection";
        } else
            return "550 \"" + path + "\" bad path";
    }

    public String RNFR(final String arg) throws IOException {
        if (this.state < 2) return "530 not logged in";
        final String path = decodeChar(arg);
        if (path.length() == 0) return "501 syntax error";
        final File file = serverFile(path);
        // if (!eventRenameFilePre(file)) return "550 no write permission";
        if (file.exists()) {
            this.renameFrom = path;
            return "350 send RNTO to rename \"" + appendUserFile(path) + "\"";
        } else
            return "550 file \"" + path + "\" does not exist";
    }

    public String RNTO(final String arg) throws IOException {
        if (this.state < 2) return "530 not logged in";
        final String path = decodeChar(arg);
        if (this.renameFrom == null) return "503 need RNFR first";
        if (path.length() == 0) return "553 syntax error";
        final File from = serverFile(this.renameFrom);
        if (!eventRenameFilePre(from)) return "550 no write permission";
        final File to = serverFile(path);
        if (!to.exists()) {
            if (from.renameTo(to)) {
                eventRenameFilePost(to);
                return "250 \"" + appendUserFile(this.renameFrom) + "\" renamed to \"" + appendUserFile(path) + "\"";
            } else
                return "553 rename failed";
        } else {
            return "553 \"" + path + "\" already exists";
        }
    }

    public String RMD(final String arg) throws IOException {
        if (this.state < 2)
            return "530 not logged in";
        final String path = decodeChar(arg);
        if (path.length() == 0)
            return "501 syntax error";
        final File dir = serverFile(path);
        // special treatment of directories with invisible Files: directory may
        // appear empty, but
        // only the invisible files exist. Then the directory cannot be deleted.
        // In that
        // catse, we silently delete the invisible files first.
        if (dir.isDirectory()) {
            if (!eventDeleteFolderPre(dir))
                return "550 error: no permission to remove";
            if (dir.list().length != 0)
                return "550 \"" + path + "\" error: dir is not empty";
            if (dir.delete()) {
                eventDeleteFolderPost(dir);
                return "250 " + appendUserFile(path) + " deleted";
            } else
                return "550 \"" + path + "\" error: dir cannot be deleted";
        } else
            return "550 \"" + path + "\" is not a directory path";
    }

    public String SITE(String arg) throws IOException {
        if (this.state < 2)
            return "530 not logged in";
        arg = decodeChar(arg);
        final String arg1 = car(arg);
        if (arg1 == null)
            return "501 no arguments given for SITE. try SITE HELP.";
        return sitecommand(arg1, cdr(arg));
    }

    public String SIZE(final String arg) throws IOException {
        if (this.state < 2)
            return "530 not logged in";
        final String path = decodeChar(arg);
        if (path.length() == 0)
            return "501 syntax error";
        final File f = serverFile(path);
        if (!(f.exists()))
            return "550 \"" + path + "\" error: does not exist";
        if (f.isDirectory())
            return "213 -1";
        else
            return "213 " + f.length();
    }

    public String STAT(final String arg) throws IOException, SocketException {
        final StringBuffer buf = new StringBuffer();
        buf.append("211-AnomicFTPD status:\n");
        final InetAddress address = this.session.userAddress;
        buf.append("     connected to " + address.getHostName());
        buf.append(" (" + address.getHostAddress() + ")\n");
        buf.append("     control socket options:");
        if (this.session.controlSocket.getTcpNoDelay()) {
            buf.append(" tcp_nodelay");
        }
        int n = this.session.controlSocket.getSoLinger();
        if (n >= 0) {
            buf.append(" so_linger " + n);
        }
        n = this.session.controlSocket.getSoTimeout();
        if (n > 0) {
            buf.append(" so_timeout " + (n / 1000) + " seconds");
        }
        buf.append("\n");
        if (this.state >= 2) {
            buf.append("     logged in as " + this.user + "\n");
            buf.append("     type binary\n");
        }
        buf.append("211 end of status");
        return buf.toString();
    }

    public String STOR(final String arg) throws IOException {
        if (this.state < 2)
            return "530 not logged in";
        if (this.state == 2)
            return "503 need PORT first";
        final String path = decodeChar(arg);
        if (path.length() == 0)
            return "501 syntax error";
        final File file = serverFile(path);
        if (!eventUploadFilePre(file))
            return "532 no write permission";
        if (!file.isDirectory()) {
            this.session.writeLine("125 opening BINARY data connection");
            Socket data;
            if (this.passive) {
                data = this.ssock.accept();
            } else {
                data = new Socket(this.datahost, this.dataport);
            }
            data.setSoTimeout(ftpd.timeoutDataConnection);
            final FileOutputStream fout = new FileOutputStream(file);
            copy(fout, data.getInputStream());
            fout.close();
            data.close();
            eventUploadFilePost(file, file.length());
            this.state = 2;
            return "226 closing data connection for " + appendUserFile(path) + ", "
                    + file.length() + " bytes";
        } else
            return "550 \"" + path + "\" bad path";
    }

    public String SYST(final String arg) throws IOException {
        // send system information
        // the prefix "UNIX Type:" is fixed. it must be UNIX because othervise
        // misunderstood by clients.
        return "215 UNIX Type: " + ftpd.systemOSType();
    }

    public String TYPE(final String arg) throws IOException {
        if (arg.toUpperCase().equals("I"))
            return "200 binary mode set";
        else
            return "200 transfers only in binary mode";
    }

    public String USER(final String arg) throws IOException {
        this.user = arg;
        this.session.setIdentity(this.user);
        final String pw = ftpdPermissions.getPassword(this.user);
        if (pw != null && pw.length() == 0) {
            // userRights = new ftpdPermissions(user);
            this.state = 2;
            return "230 logged in";
        } else {
            this.state = 1;
            return "331 password required";
        }
    }

    public String UTF8(final String arg) throws IOException {
        this.opts.put("UTF-8", "ON");
        ftpd.charcoding = "UTF-8";
        // printlog(2, "terminal character coding is now '" + ftpd.charcoding
        // +"'")
        return "200 UTF-8 active";
    }

    public String XCUP(final String arg) throws IOException {
        return CDUP(arg);
    }

    public String XMKD(final String arg) throws IOException {
        return MKD(arg);
    }

    public String XPWD(final String arg) throws IOException {
        return PWD(arg);
    }

    public String XRMD(final String arg) throws IOException {
        return RMD(arg);
    }

    /*
     * IPv6: PORT and PASV are replaced with EPRT and EPSV (rfc2428) FEAT and
     * OPTS for extra feature support (rfc2389) LPSV and LPRT for FTP Operation
     * Over Big Address Records (rfc1639) also recommended: SPSV, easier than
     * EPRT
     * 
     * sample, failed session: < EPSV > 502 command not implemented < LPSV > 502
     * command not implemented < EPRT |2|::1|51128| > 502 command not
     * implemented < LPRT 6,16,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,199,184 > 502
     * command not implemented < LIST > 503 need PORT first < EPRT |2|::1|51145|
     * > 502 command not implemented < LPRT
     * 6,16,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,199,201 > 502 command not
     * implemented < EPRT |2|::1|51288| > 502 command not implemented < LPRT
     * 6,16,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,200,88 > 502 command not
     * implemented
     */

}
