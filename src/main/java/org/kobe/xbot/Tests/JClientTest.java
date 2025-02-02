package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.CachedSubscriber;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.XTableValues;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.List;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {
        XTablesClient xTablesClient = new XTablesClient();

       CachedSubscriber subscriber = xTablesClient.subscribe("photonvisionfrontleft.active_agents.Orange_Pi_Process.log");
        
    }
}

