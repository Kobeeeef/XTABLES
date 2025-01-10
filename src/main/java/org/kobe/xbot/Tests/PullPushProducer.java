package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.XTableValues;
import org.kobe.xbot.Utilities.TempConnectionManager;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class PullPushProducer {
    public static void main(String[] args) throws IOException, InterruptedException {
        XTablesClient client = new XTablesClient();
        Thread.sleep(1000);

        // byte[] imageBytes = Files.readAllBytes(Paths.get("D:\\stuff\\IdeaProjects\\XTABLES\\src\\main\\resources\\static\\logo.png"));
        List<XTableValues.Coordinate> coordinates = new ArrayList<>();
        coordinates.add(XTableValues.Coordinate.newBuilder()
                        .setX(123)
                        .setY(456.123)
                .build());
        coordinates.add(XTableValues.Coordinate.newBuilder()
                .setX(1543)
                .setY(78658756)
                .build());
       // client.putCoordinates("test", coordinates);


        System.out.println(client.getCoordinates("test"));
    }
}
