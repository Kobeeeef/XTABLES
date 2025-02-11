package org.kobe.xbot.Tests;


import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.util.Units;
import org.kobe.xbot.JClient.XTableContext;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.BatchedPushRequests;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Entities.XTableValues;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        XTablesClient client = new XTablesClient();
        List<XTableValues.Coordinate> coordinates = new ArrayList<>();
        coordinates.add(XTableValues.Coordinate.newBuilder()
                .setX(3)
                .setY(2).build());
        coordinates.add(XTableValues.Coordinate.newBuilder()
                .setX(Math.random())
                .setY(3).build());
        int degrees = 0;


       XTableValues.BezierCurve curve = XTableValues.BezierCurve.newBuilder()
                .setTimeToTraverse(30.2)
                .addAllControlPoints(List.of(XTableValues.ControlPoint.newBuilder().setX(20).setY(20).build()))
               .build();

       XTableValues.BezierCurves curves = XTableValues.BezierCurves.newBuilder()
               .addCurves(curve)
               .build();

       //





        while (true) {
            degrees -= 5;
            client.putPose2d("PoseSubsystem.RobotPose", new Pose2d(Math.random() * 50,Math.random() * 30 ,new Rotation2d(Units.degreesToRadians(degrees))));
            Thread.sleep(10);
        }


//       Thread.sleep(1000000);

    }
}

