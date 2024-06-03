package org.kobe.xbot.Client;


import org.bytedeco.opencv.global.opencv_highgui;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.kobe.xbot.Utilities.Utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient("10.0.0.52", 1735, 5, false);

        org.bytedeco.opencv.opencv_videoio.VideoCapture capture = new VideoCapture(0);
        org.bytedeco.opencv.opencv_core.Mat frame = new Mat();
        while(capture.read(frame)) {
            client.putByteFrame("camera", Utilities.matToByteArray(frame)).execute();
            opencv_highgui.imshow("ok", frame);
            opencv_highgui.waitKey(1);
        }

    }


}
