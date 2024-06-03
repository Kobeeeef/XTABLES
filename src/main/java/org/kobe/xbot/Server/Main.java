package org.kobe.xbot.Server;

import org.kobe.xbot.Utilities.Logger.XTablesLogger;

public class Main {
    private static final XTablesLogger logger = XTablesLogger.getLogger();

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                int port = Integer.parseInt(args[0]);
                if (port < 0 || port > 65535) {
                    logger.severe("Error: The specified port '" + args[0] + "' is outside the specified range of valid port values.");
                } else {
                    XTables.startInstance("XTablesService", port);
                }
            } catch (NumberFormatException e) {
                logger.severe("Error: The specified port '" + args[0] + "' is not a valid integer.");
            }
        } else {
            logger.info("No port number provided. Default port 1735 is being used.");
            XTables.startInstance("XTablesService", 1735);
        }
    }
}
