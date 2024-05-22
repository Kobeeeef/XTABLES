package org.kobe.xbot.Client;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_highgui;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.kobe.xbot.Utilities.ImageStreamStatus;
import org.kobe.xbot.Utilities.Utilities;
import org.kobe.xbot.Utilities.VideoStreamResponse;

import java.io.IOException;

import static org.kobe.xbot.Utilities.Utilities.matToByteArray;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT, 5, false);
        VideoStreamResponse response = client.registerImageStreamServer("cam1").complete();
        VideoCapture capture = new VideoCapture(0);

        Mat frame = new Mat();
        while (true) {
            if(capture.read(frame)) {
                response.getStreamServer().updateFrame(Utilities.matToByteArray(frame));
            }
        }

    }


}
