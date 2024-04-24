package org.kobe.xbot.Server;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

public class XTables {
    private static final int PORT = 1735;
    private final Gson gson = new Gson();
    private final XTablesData<String> table = new XTablesData<>();
    private final Logger logger = Logger.getLogger(XTables.class.getName());
    private static XTables instance = null;

    public XTables() {
        if (instance != null) {
            logger.severe("There is an already existing instance!");
            logger.severe("Exiting instance!");
            return;
        }
        instance = this;

        logger.info(String.format("Starting server on port %1$s.", PORT));
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server started. Listening on port " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info(String.format("Client connected: %1$s:%2$s", clientSocket.getInetAddress(), clientSocket.getPort()));
                // Handle each client connection in a separate thread
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            logger.severe("Error occurred: " + e.getMessage());
        }
    }

    // Thread to handle each client connection
    private class ClientHandler extends Thread {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    String[] tokens = inputLine.split(" ");
                    if (tokens.length == 2 && tokens[0].equals("GET")) {
                        String key = tokens[1];
                        String result = gson.toJson(table.get(key));
                        out.println(result);
                    } else if (tokens.length == 1 && tokens[0].equals("GET_RAW_JSON")) {
                        out.println(table.toJSON());
                    } else if (tokens.length == 2 && tokens[0].equals("GET_TABLES")) {
                        String key = tokens[1];
                        String result = gson.toJson(table.getTables(key));
                        out.println(result);
                    } else if (tokens.length == 1 && tokens[0].equals("GET_TABLES")) {
                        String result = gson.toJson(table.getTables(""));
                        out.println(result);
                    } else if (tokens.length == 3 && tokens[0].equals("PUT")) {
                        String key = tokens[1];
                        String value = tokens[2];
                        boolean response = table.put(key, value);
                        out.println(response ? "OK" : "FAIL");
                    } else if (tokens.length == 2 && tokens[0].equals("DELETE")) {
                        String key = tokens[1];
                        boolean response = table.delete(key);
                        out.println(response ? "OK" : "FAIL");
                    } else {
                        // Invalid command
                        out.println("UNKNOWN_OPTION!");
                    }
                }
                // Close the streams and socket when done
                out.close();
                in.close();
                clientSocket.close();
            } catch (IOException e) {
                String message = e.getMessage();
                if(message.contains("Connection reset")) {

                    logger.info(String.format("Client disconnected: %1$s:%2$s", clientSocket.getInetAddress(), clientSocket.getPort()));
                } else {
                    logger.severe("Error occurred: " + e.getMessage());
                }
            }
        }
    }
}
