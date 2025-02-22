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

    private static final String BUILD_DATE = "2/21/2025";
    private static final String VERSION = "v5.3.1";
    private static final String JAVA_VERSION = System.getProperty("java.version");
    /**
     * The version string for the XTablesServer.
     * Includes the software name, version, build date, and Java version.
     */
    public static final String XTABLES_SERVER_VERSION =
            "XTABLES Jero Server " + VERSION + " | Build Date: " + BUILD_DATE + " | Java " + JAVA_VERSION;

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
        System.setProperty("file.encoding", "UTF-8");
        logger.info("\n" + """                                                                                                                         
                         ███████ ███████  ██████████████████                                                                                                                             \s
                             █████  █████████  ███████   █████                                                                                                                           \s
                               █████  █████   ██████████   █████      ████   ███ ███████      ██████   ██████████    ███████            ████                     ███                     \s
                                █████   ██   █████  █████   █████      ████ ███  █████████  ██████████ ██████████    █████████          ████                ███  ███                     \s
                                 █████  ██  █████    █████   ████       ██████   ███  ████ ████    ████   ███        ███   ███  ██████  ████████   ██████  █████████   █████  █████      \s
                 ████    ████     ████ ████ ████      ████    ████       ████    █████████ ███      ███   ███        ████████  ████ ███ █████████ ████ ████ ███  ████████ ███ ██████     \s
                  ████   █████   █████  ██  █████                       ███████  ███   ███ ████    ████   ███        ███████  ████  ████████  ███████  ████ ███  ███████      ██████     \s
                   ████   █████ █████   ██   █████                    ████  ████ █████████  ██████████    ███        ███ ████  ████████ █████████ ████████  ███  ████ ███████ ███████    \s
                   ██████  █████████  █████   █████                   ███    ███████████       ████       ███        ███   ███   ████    ██████     ████     ██  ███    ████   ████      \s
                     ██████ ███████ █████████   █████                                                                                                                                    \s
                        ███████████████   ███████ ███████                                                                                                                                                                                                                                                                                                \s
                \s""");
        boolean additionalFeatures = true;
        int pull = 48800;
        int rep = 48801;
        int pub = 48802;

        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("--additional_features=") || args[i].startsWith("--additional-features=") || args[i].startsWith("--af=")) {
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
            logger.info(XTABLES_SERVER_VERSION);

            logger.info("Developed by XBOT ROBOTICS - Kobe Lei");
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
            logger.info("""
                    
                    /-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\|/-\\
                    |                                                                                                                                                 |
                    \\     ___    ___ _________  ________  ________  ___       _______   ________                              ___   ___  ________  ________           /
                    -    |\\  \\  /  /|\\___   ___\\\\   __  \\|\\   __  \\|\\  \\     |\\  ___ \\ |\\   ____\\                            |\\  \\ |\\  \\|\\   __  \\|\\   __  \\          -
                    /    \\ \\  \\/  / ||___ \\  \\_\\ \\  \\|\\  \\ \\  \\|\\ /\\ \\  \\    \\ \\   __/|\\ \\  \\___|_         ____________      \\ \\  \\\\_\\  \\ \\  \\|\\  \\ \\  \\|\\  \\         \\
                    |     \\ \\    / /     \\ \\  \\ \\ \\   __  \\ \\   __  \\ \\  \\    \\ \\  \\_|/_\\ \\_____  \\       |\\____________\\     \\ \\______  \\ \\   __  \\ \\   __  \\        |
                    \\      /     \\/       \\ \\  \\ \\ \\  \\ \\  \\ \\  \\|\\  \\ \\  \\____\\ \\  \\_|\\ \\|____|\\  \\      \\|____________|      \\|_____|\\  \\ \\  \\|\\  \\ \\  \\|\\  \\       /
                    -     /  /\\   \\        \\ \\__\\ \\ \\__\\ \\__\\ \\_______\\ \\_______\\ \\_______\\____\\_\\  \\                                 \\ \\__\\ \\_______\\ \\_______\\      -
                    /    /__/ /\\ __\\        \\|__|  \\|__|\\|__|\\|_______|\\|_______|\\|_______|\\_________\\                                 \\|__|\\|_______|\\|_______|      \\
                    |    |__|/ \\|__|                                                      \\|_________|                                                                |
                    \\                                                                                                                                                 /
                    -                                                                                                                                                 -
                    /                 _________  ___  ___  _______           _____ ______   ________  _________  ________  ___     ___    ___                         \\
                    |                |\\___   ___\\\\  \\|\\  \\|\\  ___ \\         |\\   _ \\  _   \\|\\   __  \\|\\___   ___\\\\   __  \\|\\  \\   |\\  \\  /  /|                        |
                    \\                \\|___ \\  \\_\\ \\  \\\\\\  \\ \\   __/|        \\ \\  \\\\\\__\\ \\  \\ \\  \\|\\  \\|___ \\  \\_\\ \\  \\|\\  \\ \\  \\  \\ \\  \\/  / /                        /
                    -                     \\ \\  \\ \\ \\   __  \\ \\  \\_|/__       \\ \\  \\\\|__| \\  \\ \\   __  \\   \\ \\  \\ \\ \\   _  _\\ \\  \\  \\ \\    / /                         -
                    /                      \\ \\  \\ \\ \\  \\ \\  \\ \\  \\_|\\ \\       \\ \\  \\    \\ \\  \\ \\  \\ \\  \\   \\ \\  \\ \\ \\  \\\\  \\\\ \\  \\  /     \\/                          \\
                    |                       \\ \\__\\ \\ \\__\\ \\__\\ \\_______\\       \\ \\__\\    \\ \\__\\ \\__\\ \\__\\   \\ \\__\\ \\ \\__\\\\ _\\\\ \\__\\/  /\\   \\                          |
                    \\                        \\|__|  \\|__|\\|__|\\|_______|        \\|__|     \\|__|\\|__|\\|__|    \\|__|  \\|__|\\|__|\\|__/__/ /\\ __\\                         /
                    -                                                                                                             |__|/ \\|__|                         -
                    /                                                                                                                                                 \\
                    |                                                                                                                                                 |
                    \\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/|\\-/
                    """);

            XTablesServer.initialize(XTABLES_SERVER_VERSION, pull, rep, pub, additionalFeatures);

        } catch (NumberFormatException e) {
            logger.severe("Error: One or more specified ports are not valid integers.");
        }
    }
}
