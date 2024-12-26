package org.kobe.xbot.JServer;

import org.kobe.xbot.Utilities.Logger.XTablesLogger;

/**
 * Main - The entry point for initializing and starting the XTablesServer with JeroMQ-based messaging.
 * <p>
 * This class processes command-line arguments to configure the server ports for PULL, REQ/REP, and PUB messaging.
 * If no ports are provided, default ports (1735 for PULL, 1736 for REQ/REP, and 1737 for PUB) are used.
 * The ports must be within the valid range of 0 to 65535.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: org.kobe.xbot.JServer
 * <p>
 * This is part of the XTABLES project and facilitates server initialization
 * for messaging and service discovery using JeroMQ.
 */
public class Main {

    public static final String XTABLES_SERVER_VERSION =
            "XTABLES Jero Server v1.0.0 | Build Date: 12/24/2024 | Java 17";


    private static final XTablesLogger logger = XTablesLogger.getLogger();

    public static void main(String[] args) {
        if (args.length == 3) {
            try {
                int pull = Integer.parseInt(args[0]);
                int rep = Integer.parseInt(args[1]);
                int pub = Integer.parseInt(args[2]);
                if (pull < 0 || pull > 65535 || rep < 0 || rep > 65535 || pub < 0 || pub > 65535) {
                    logger.severe("Error: The specified port '" + args[0] + "' is outside the specified range of valid port values.");
                } else {
                    XTablesServer.initialize(XTABLES_SERVER_VERSION, pull, rep, pub);
                }
            } catch (NumberFormatException e) {
                logger.severe("Error: The specified port '" + args[0] + "' is not a valid integer.");
                logger.severe("Error: The specified port '" + args[1] + "' is not a valid integer.");
                logger.severe("Error: The specified port '" + args[2] + "' is not a valid integer.");
            }
        } else {
            logger.info("No port number provided. Default ports 1735 (pull), 1736 (reply), 1737 (publish) is being used.");
            XTablesServer.initialize(XTABLES_SERVER_VERSION, 1735, 1736, 1737);
        }
    }
}
