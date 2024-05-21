package org.kobe.xbot.Client;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.kobe.xbot.Utilities.ImageStreamStatus;
import org.kobe.xbot.Utilities.VideoStreamResponse;

import java.io.IOException;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT, 5, false);
        VideoStreamResponse response = client.registerImageStreamServer("oki2").complete();
        if(response.getStatus().equals(ImageStreamStatus.OKAY)) {
            VideoCapture camera = new VideoCapture(0);
            Mat frame = new Mat();
            while (true) {
                if (camera.read(frame)) {
                    byte[] byteArray = matToByteArray(frame);
                    response.getStreamServer().updateFrame(byteArray);
                }
            }
        }
    }

    private static byte[] matToByteArray(Mat mat) {
        BytePointer bytePointer = new BytePointer();
        opencv_imgcodecs.imencode(".jpg", mat, bytePointer); // Encode the image
        byte[] byteArray = new byte[(int) bytePointer.limit()];
        bytePointer.get(byteArray);
        return byteArray;
    }
}
