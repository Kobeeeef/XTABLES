package org.kobe.xbot.JServer;

import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.Utilities;

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
    /**
     * The version string for the XTablesServer.
     * Includes the software name, version, build date, and Java version.
     */
    public static final String XTABLES_SERVER_VERSION =
            "XTABLES Jero Server v4.6.7 | Build Date: 1/24/2025 | Java 17";

    /**
     * The logger instance for recording server events, warnings, and errors.
     */
    private static final XTablesLogger logger = XTablesLogger.getLogger();

    /**
     * Main method - The entry point of the application.
     * <p>
     * Parses command-line arguments to configure server settings and initialize the XTablesServer.
     * Accepts three optional arguments for PULL, REQ/REP, and PUB ports and an additional optional flag
     * `--additional_features=true/false` to enable or disable additional server features.
     * <p>
     * If no ports are provided, the server defaults to using 1735 for PULL, 1736 for REQ/REP, and 1737 for PUB.
     * The method validates all provided ports to ensure they are within the range of 0 to 65535.
     * <p>
     * Errors such as invalid port formats or ranges are logged using the `logger` instance.
     *
     * @param args Command-line arguments for server configuration.
     */
    public static void main(String[] args) {
        boolean additionalFeatures = false;
        int pull = 1735;
        int rep = 1736;
        int pub = 1737;

        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("--additional_features=") || args[i].startsWith("--additional-features=")) {
                    String[] split = args[i].split("=", 2);
                    if (split.length == 2) {
                        additionalFeatures = Boolean.parseBoolean(split[1]);
                    } else {
                        logger.severe("Invalid format for --additional_features. Expected format: --additional_features=true/false");
                        return;
                    }
                } else if (i < 3) {
                    switch (i) {
                        case 0 -> pull = Integer.parseInt(args[i]);
                        case 1 -> rep = Integer.parseInt(args[i]);
                        case 2 -> pub = Integer.parseInt(args[i]);
                    }
                }
            }
            if (pull < 0 || pull > 65535 || rep < 0 || rep > 65535 || pub < 0 || pub > 65535) {
                logger.severe("Error: One or more specified ports are outside the valid range (0-65535).");
                return;
            }
            String ip = Utilities.getLocalIPAddress();
            logger.info(
                    "Starting XTABLES Server:\n" +
                            "------------------------------------------------------------\n" +
                            "Server IP: " + (ip == null ? "Unknown" : ip) + "\n" +
                            "Pull Socket Port: " + pull + "\n" +
                            "Reply Socket Port: " + rep + "\n" +
                            "Publish Socket Port: " + pub + "\n" +
                            "Additional Features: " + additionalFeatures + "\n" +
                            "Web Interface: " + "http://" + (ip == null ? "localhost" : ip) + ":4880/" + "\n" +
                            "------------------------------------------------------------");
            XTablesServer.initialize(XTABLES_SERVER_VERSION, pull, rep, pub, additionalFeatures);

        } catch (NumberFormatException e) {
            logger.severe("Error: One or more specified ports are not valid integers.");
        }
    }
}
