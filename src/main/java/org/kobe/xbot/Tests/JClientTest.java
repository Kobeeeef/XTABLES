package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.CachedSubscriber;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.JClient.XTablesClientManager;
import org.kobe.xbot.Utilities.Entities.XTableValues;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        XTablesClientManager xTablesClient = XTablesClient.getDefaultClientAsynchronously();
        xTablesClient.getAndBlock();
       CachedSubscriber subscriber =   xTablesClient.getAndBlock().subscribe("photonvisionfrontleft.active_agents.Orange_Pi_Process.log");

       Thread.sleep(1000000);

    }
}

