package org.kobe.xbot.JClient;

import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.TempConnectionManager;

public class ClearTemp {
    public static void main(String[] args) {
        XTablesLogger.getLogger().info("Clearing temp connection...");
        TempConnectionManager.invalidate();
        XTablesLogger.getLogger().info("Finished, exiting.");
        System.exit(0);
    }
}
