package org.kobe.xbot.Client;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_highgui;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Point;

import java.io.IOException;
import java.util.List;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT, 5, false);

        VideoCapture camera = new VideoCapture(0); // 0 is the default camera

        if (!camera.isOpened()) {
            System.out.println("Error: Camera not accessible.");
            return;
        }

        Mat frame = new Mat();
        long startTime = System.nanoTime();
        int frameCount = 0;
        double fps = 0.0;

        while (true) {
            // Capture a frame from the camera
            if (camera.read(frame)) {
                frameCount++;
                long currentTime = System.nanoTime();
                double elapsedTime = (currentTime - startTime) / 1e9; // Convert to seconds
                if (elapsedTime >= 1.0) {
                    fps = frameCount / elapsedTime;
                    frameCount = 0;
                    startTime = currentTime;
                }

                // Display FPS on the frame
                opencv_imgproc.putText(frame, String.format("FPS: %.2f", fps), new Point(10, 30),
                        opencv_imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 255, 0, 0), 2, opencv_imgproc.LINE_AA, false);

                // Display the frame using HighGui
                opencv_highgui.imshow("Camera Stream", frame);
                byte[] byteArray = matToByteArray(frame);
                client.putArray("camera1", List.of(byteArray)).execute();

                // Exit the loop if the user presses the 'q' key
                if (opencv_highgui.waitKey(1) == 'q') {
                    break;
                }
            } else {
                System.out.println("Error: Unable to capture frame.");
                break;
            }
        }

        // Release the camera and close any OpenCV windows
        camera.release();
        opencv_highgui.destroyAllWindows();
        client.stopAll();
    }

    private static byte[] matToByteArray(Mat mat) {
        BytePointer bytePointer = new BytePointer();
        opencv_imgcodecs.imencode(".jpg", mat, bytePointer); // Encode the image
        byte[] byteArray = new byte[(int) bytePointer.limit()];
        bytePointer.get(byteArray);
        return byteArray;
    }
}
