package org.kobe.xbot.Client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.kobe.xbot.Utilities.XTablesLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class VideoStreamServer {
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private static HttpServer server;
    private static boolean isServerRunning = false;
    private byte[] currentFrame;
    private final String endpoint;

    public VideoStreamServer(String name) {
        this.endpoint = "/" + name;
    }

    public void start() throws IOException {
        if (server == null) {
            server = HttpServer.create(new InetSocketAddress(4888), 0);
            server.setExecutor(Executors.newCachedThreadPool());
        }

        server.createContext(endpoint, new StreamHandler());
        if (!isServerRunning) {
            server.start();
            isServerRunning = true;
            logger.info(("Server started at: http://localhost:" + server.getAddress().getPort()) + endpoint);
        } else {
            logger.info(("Server added endpoint: http://localhost:" + server.getAddress().getPort()) + endpoint);
        }
    }

    public void stop() {
        if (server != null && isServerRunning) {
            server.stop(0);
            isServerRunning = false;
            logger.info("Camera streaming server stopped.");
        }
    }

    public void updateFrame(byte[] frame) {
        this.currentFrame = frame;
    }

    private class StreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (currentFrame != null) {
                exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                exchange.sendResponseHeaders(200, currentFrame.length);
                OutputStream os = exchange.getResponseBody();
                os.write(currentFrame);
                os.close();
            } else {
                exchange.sendResponseHeaders(503, -1); // Service unavailable if no frame available
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Example usage
        VideoStreamServer server = new VideoStreamServer("poopi");
        server.start();

        VideoCapture camera = new VideoCapture(0);
        Mat frame = new Mat();
        while (true) {
            if (camera.read(frame)) {
                byte[] byteArray = matToByteArray(frame);
                server.updateFrame(byteArray);
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
