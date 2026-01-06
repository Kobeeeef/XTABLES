package org.kobe.xbot.Tests;


import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.util.Units;
import org.kobe.xbot.JClient.XTableContext;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.BatchedPushRequests;
import org.kobe.xbot.Utilities.Entities.VisionCoprocessor;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Entities.XTableValues;
import org.kobe.xbot.Utilities.VisionCoprocessorCommander;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        XTablesClient client = new XTablesClient();
//        List<XTableValues.Coordinate> coordinates = new ArrayList<>();
//        coordinates.add(XTableValues.Coordinate.newBuilder()
//                .setX(3)
//                .setY(2).build());
//        coordinates.add(XTableValues.Coordinate.newBuilder()
//                .setX(Math.random())
//                .setY(3).build());
//        int degrees = 0;




       //

//        VisionCoprocessorCommander commander = new VisionCoprocessorCommander(VisionCoprocessor.LOCALHOST);
//        XTableValues.BezierCurves curves = commander
//                .requestBezierPathWithOptions(XTableValues.RequestVisionCoprocessorMessage.newBuilder()
//                        .setStart(XTableValues.ControlPoint.newBuilder()
//                                .setY(3)
//                                .setX(2).build())
//                        .setEnd(XTableValues.ControlPoint.newBuilder()
//                                .setX(Math.random())
//                                .setY(2)
//                                .setRotationDegrees(2)
//                                .build())
//                        .setOptions(XTableValues.TraversalOptions.newBuilder()
//                                .setStartFaceNearestReefAprilTagPathThresholdPercentage(10)
//                                .setSnapToNearestAprilTag(true)
//                                .setEndFaceNearestReefAprilTagPathThresholdPercentage(80).build())
//                        .build(), 5, TimeUnit.SECONDS);
//
//        System.out.println(curves);



        while (true) {

            client.putPose2d("PoseSubsystem.RobotPose",new Pose2d(4.002, 5.174, Rotation2d.fromDegrees(-60)));

        }


//       Thread.sleep(1000000);

    }
}

