package org.kobe.xbot.Client;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_highgui;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.kobe.xbot.Utilities.XTablesLogger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VideoStreamClient {
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private final String serverUrl;

    public VideoStreamClient(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void start() {
        try {
            while (true) {
                HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
                connection.setRequestMethod("GET");

                try (InputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
                    byte[] byteArray = inputStream.readAllBytes();
                    if (byteArray.length == 0) {
                        logger.severe("Received empty byte array.");
                        continue;
                    }

                    Mat frame = byteArrayToMat(byteArray);
                    if (frame.empty()) {
                        logger.severe("Failed to decode frame.");
                        continue;
                    }

                    processFrame(frame); // Process the frame (e.g., display or save)
                } catch (IOException e) {
                    logger.severe("Error reading stream: " + e.getMessage());
                } finally {
                    connection.disconnect();
                }
            }
        } catch (IOException e) {
            logger.severe("Error connecting to server: " + e.getMessage());
        }
    }

    private Mat byteArrayToMat(byte[] byteArray) {
        Mat mat = new Mat(new BytePointer(byteArray));
        return opencv_imgcodecs.imdecode(mat, opencv_imgcodecs.IMREAD_COLOR);
    }

    private void processFrame(Mat frame) {
        // Process the frame (e.g., display or save)
        // This is a placeholder method. Implement as needed.
        opencv_highgui.imshow("Camera Stream", frame);
        opencv_highgui.waitKey(1);

    }

    public static void main(String[] args) {
        // Example usage
        String serverUrl = "http://localhost:4888/poopi";
        VideoStreamClient client = new VideoStreamClient(serverUrl);
        client.start();
    }
}
