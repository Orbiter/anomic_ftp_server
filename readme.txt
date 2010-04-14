README for the FTP SERVER AnomicFTPD (C) by Michael Peter Christen
---------------------------------------------------------------------------
Please visit www.anomic.de for latest changes or new documentation.
The AnomicFTPD FTP Server comes with ABSOLUTELY NO WARRANTY!
This is free software, and you are welcome to redistribute it
under certain conditions; see file gpl.txt for details.
---------------------------------------------------------------------------

This is a full-featured ftp server in java for almost any system architecture.

The complete documentation can be found inside the 'Doc' subdirectory
in this release. Start browsing the manual by opening the index.html file
with our web browser. Please see the FAQ there if you have trouble accessing
the ftp server.

Startup of the FTP Server:

- on Linux        : start ftpd.sh ("nohup ./ftpd.sh > /dev/null &")
- on Windows      : double-click ftpd.bat
- on Mac OS X     : double-click ftpd.command (alias possible!)
- on any other OS : set your classpath to the 'Classes' folder
                    and execute ftpd.class, while your current system path
                    must target the release directory to access the
                    configuration files.

After start-up, the ftp server runs with a predefined configuration of
user-accounts that instantly allow connection to your complete file system.
The output on the terminal then explains how to connect the server easily
with URL strings.
While this is convenient for first-time and one-time - users, we recommend
to change the default configuration if you use the server in an environment
with more than one user. Please see the files ftpd.groups and ftpd.accounts
to set-up your own accounts.

If you have any questions, please do not hesitate to contact the author:
Send a mail to Michael Christen (mc@anomic.de) with a meaningful subject
including the word 'AnomicFTPD' to prevent that your email gets stuck in
my anti-spam filter.

If you like to have a customized server for special needs, feel free to ask
the author for a business proposal to customize the server according to
your needs. We provide also integration solutions if the server is about
to be integrated into your enterprise application.

Germany, Frankfurt a.M., 11.03.2010
Michael Peter Christen
