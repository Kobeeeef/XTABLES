package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.XTablesByteUtils;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {
        XTablesClient xTablesClient = new XTablesClient();
        xTablesClient.subscribe((xTableUpdate -> {
           System.out.println(xTableUpdate.getKey() + ": " + XTablesByteUtils.convertXTableUpdateToJsonString(xTableUpdate));
        }));

        Thread.sleep(100000000);


    }
}

