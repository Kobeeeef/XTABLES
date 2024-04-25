package org.kobe.xbot.Server;

import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                int port = Integer.parseInt(args[0]);
                new XTables(port);
            } catch (NumberFormatException e) {
                logger.severe ("Error: The specified port '" + args[0] + "' is not a valid integer.");
            }
        } else {
            logger.info("No port number provided. Default port 1735 is being used.");
            new XTables(1735);
        }
    }
}
