package org.kobe.xbot.Tests;


import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import org.kobe.xbot.JClient.XTableContext;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.BatchedPushRequests;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.concurrent.ExecutionException;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        XTablesClient client = new XTablesClient();


        client.putPose2d("test", new Pose2d(1,5,new Rotation2d(123)));
        System.out.println(XTablesByteUtils.pose2dToString(XTablesByteUtils.packPose2d(client.getPose2d("test"))));
       Thread.sleep(1000000);

    }
}

