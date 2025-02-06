package org.kobe.xbot.Tests;


import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.util.Units;
import org.kobe.xbot.JClient.XTableContext;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.BatchedPushRequests;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.concurrent.ExecutionException;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        XTablesClient client = new XTablesClient();

        while(true) {
            client.putPose2d("PoseSubsystem.RobotPose", new Pose2d(4,2,new Rotation2d(Units.degreesToRadians(0))));
Thread.sleep(1000  );
        }
//       Thread.sleep(1000000);

    }
}

