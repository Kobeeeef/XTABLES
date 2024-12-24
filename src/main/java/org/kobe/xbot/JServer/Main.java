package org.kobe.xbot.JServer;

import org.kobe.xbot.Utilities.Logger.XTablesLogger;

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
                if (pull < 0 || pull > 65535 || rep < 0 || rep > 65535|| pub < 0 || pub > 65535) {
                    logger.severe("Error: The specified port '" + args[0] + "' is outside the specified range of valid port values.");
                } else {
                    XTablesServer.initialize(pull, rep, pub);
                }
            } catch (NumberFormatException e) {
                logger.severe("Error: The specified port '" + args[0] + "' is not a valid integer.");
                logger.severe("Error: The specified port '" + args[1] + "' is not a valid integer.");
                logger.severe("Error: The specified port '" + args[2] + "' is not a valid integer.");
            }
        } else {
            logger.info("No port number provided. Default ports 1735 (pull), 1736 (reply), 1737 (publish) is being used.");
            XTablesServer.initialize(1735, 1736, 1737);
        }
    }
}
