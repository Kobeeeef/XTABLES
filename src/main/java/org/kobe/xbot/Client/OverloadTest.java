package org.kobe.xbot.Client;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.kobe.xbot.Utilities.ResponseStatus;
import org.kobe.xbot.Utilities.Utilities;
import org.kobe.xbot.Utilities.VideoStreamResponse;

import java.io.IOException;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient("XTablesService", 5, false);


    }


}
