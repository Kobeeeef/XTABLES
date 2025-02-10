package org.kobe.xbot.Tests;

import org.kobe.xbot.Utilities.Entities.XTableValues;

import java.util.List;

public class CurveDemo {


    public static void main(String[] args) {


        XTableValues.BezierCurve curve = XTableValues.BezierCurve.newBuilder()
                .setMetersPerSecond(0.3d)
                .setTimeToTraverse(30.2)
                .addAllControlPoints(List.of(XTableValues.ControlPoint.newBuilder().setX(20).setY(20).build()))
                .build();

        XTableValues.BezierCurves byteCurves = XTableValues.BezierCurves.newBuilder()
                .addCurves(curve)
                .build();

        // Get Curves

        List<XTableValues.BezierCurve> curves = byteCurves.getCurvesList();

        System.out.println(curves.size());

        // get first curve

        XTableValues.BezierCurve firstCurve = curves.get(0);
        // speed
        System.out.println(firstCurve.getMetersPerSecond());
        // time
        System.out.println(firstCurve.getTimeToTraverse());
        // control points
        List<XTableValues.ControlPoint> controlPoints = firstCurve.getControlPointsList();
        System.out.println(controlPoints.size());

        // get first control point
        XTableValues.ControlPoint firstPoint = controlPoints.get(0);
        // Extract values
        System.out.println(firstPoint.getX());
        System.out.println(firstPoint.getY());




    }

}
