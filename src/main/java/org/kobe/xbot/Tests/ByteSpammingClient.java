package org.kobe.xbot.Tests;

import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

public class ByteSpammingClient {

    public static void main(String[] args) {
        String serverAddress = "localhost"; // Replace with server IP if needed
        int serverPort = 8080; // Replace with server port

        try (Socket socket = new Socket(serverAddress, serverPort)) {
            System.out.println("Connected to server: " + serverAddress + ":" + serverPort);

            // Get the output stream to send data
            OutputStream outputStream = socket.getOutputStream();

            // Create a random byte generator
            Random random = new Random();
            byte randomByte = (byte) random.nextInt(256); // 0 to 255
            while (true) {
                outputStream.write(randomByte);
                outputStream.flush();

            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
