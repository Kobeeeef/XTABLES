package org.kobe.xbot.Utilities;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class XTablesLogger extends Logger {
    private static final String loggerName = "XTablesLogger";
    private static Level defaultLevel = Level.ALL;
    private static XTablesLogger instance = null;
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";

    protected XTablesLogger(String name, String resourceBundleName) {
        super(name, resourceBundleName);
    }

    public static XTablesLogger getLogger() {
        if (instance == null) {
            XTablesLogger logger = new XTablesLogger(loggerName, null);
            logger.setLevel(defaultLevel);
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new XTablesFormatter());
            logger.addHandler(consoleHandler);
            instance = logger;
            return logger;
        } else return instance;
    }

    // Custom formatter for XTablesLogger
    private static class XTablesFormatter extends java.util.logging.Formatter {

        @Override
        public String format(java.util.logging.LogRecord record) {
            StringBuilder builder = new StringBuilder();

            // Get current time and date
            ZonedDateTime dateTime = ZonedDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm:ss a");
            String formattedDateTime = dateTime.format(formatter);

            // Get class and method names
            String classPath = record.getSourceClassName();
            String methodName = record.getSourceMethodName();
            String className = classPath.split("\\.")[classPath.split("\\.").length - 1];

            builder.append(getColorFromLevel(record.getLevel()))
                    .append("[")
                    .append(record.getLevel().getName())
                    .append("] ")
                    .append(formattedDateTime)
                    .append(" ")
                    .append(classPath)
                    .append(" ")
                    .append(RESET)
                    .append(methodName)
                    .append(": \n")
                    .append(getColorFromLevel(record.getLevel()))
                    .append("[")
                    .append(record.getLevel().getName())
                    .append("] ")
                    .append("[")
                    .append(className)
                    .append("] ")
                    .append(RESET)
                    .append(record.getMessage())
                    .append(System.lineSeparator());
            return builder.toString();
        }
    }

    private static String getColorFromLevel(Level level) {
        if (level == Level.SEVERE) {
            return RED;
        } else if (level == Level.WARNING) {
            return YELLOW;
        } else if (level == Level.INFO) {
            return BLUE;
        }
        return "";
    }

    public static void setLoggingLevel(Level level) {
        if (instance == null) defaultLevel = level;
        else instance.setLevel(level);
    }
}
