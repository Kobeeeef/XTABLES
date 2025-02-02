package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.XTableValues;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.List;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {
        XTablesClient xTablesClient = new XTablesClient();
        xTablesClient.subscribe("target_waypoints", (xTableUpdate -> {
           System.out.println(xTableUpdate.getKey());
        }));

        while (true) {
            List<XTableValues.Coordinate> coordinates = xTablesClient.getCoordinates("target_waypoints");
            for (XTableValues.Coordinate coordinate : coordinates) {
                System.out.println(coordinate);
            }
        }


//        Thread.sleep(100000000);


    }
}

