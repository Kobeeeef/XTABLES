package org.kobe.xbot.JServer;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XTablesSocketMonitor extends Thread {
    private final ZContext context;
    private final Map<ZMQ.Socket, String> monitorSocketNames = new HashMap<>();
    private final Map<String, List<String>> clientMap = new HashMap<>();
    private volatile boolean running = true;

    public XTablesSocketMonitor(ZContext context) {
        this.context = context;
        setDaemon(true);
    }

    public XTablesSocketMonitor addSocket(String socketName, ZMQ.Socket socket) {
        String monitorAddress = "inproc://monitor-" + socket.hashCode();
        socket.monitor(monitorAddress, ZMQ.EVENT_ACCEPTED | ZMQ.EVENT_DISCONNECTED);
        ZMQ.Socket monitorSocket = context.createSocket(ZMQ.PAIR);
        monitorSocket.connect(monitorAddress);

        monitorSocketNames.put(monitorSocket, socketName);
        clientMap.put(socketName, new ArrayList<>());
        return this;
    }

    @Override
    public void run() {
        while (running) {
            for (Map.Entry<ZMQ.Socket, String> entry : monitorSocketNames.entrySet()) {
                ZMQ.Socket monitorSocket = entry.getKey();
                String socketName = entry.getValue();

                ZMQ.Event event = ZMQ.Event.recv(monitorSocket, ZMQ.DONTWAIT);
                if (event != null) {
                    String clientAddress = event.getAddress();

                    if (clientAddress == null) continue;

                    switch (event.getEvent()) {
                        case ZMQ.EVENT_ACCEPTED -> {
                            clientMap.get(socketName).add(clientAddress);
                            onClientConnected(socketName, clientAddress, clientMap.get(socketName).size());
                        }
                        case ZMQ.EVENT_DISCONNECTED -> {
                            clientMap.get(socketName).remove(clientAddress);
                            onClientDisconnected(socketName, clientAddress, clientMap.get(socketName).size());
                        }
                    }
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        cleanup();
    }

    @Override
    public void interrupt() {
        running = false;
        super.interrupt();
        cleanup();
    }

    private void cleanup() {
        for (ZMQ.Socket monitorSocket : monitorSocketNames.keySet()) {
            monitorSocket.close();
        }
        monitorSocketNames.clear();
        clientMap.clear();
        System.out.println("XTablesSocketMonitor cleaned up.");
    }

    // Prints the connected client count for each socket
    protected void onClientConnected(String socketName, String clientAddress, int clientCount) {
        System.out.println("Client connected on socket: " + socketName +
                ", Address: " + clientAddress +
                ", Total clients on this socket: " + clientCount);
    }

    // Prints the updated connected client count for each socket
    protected void onClientDisconnected(String socketName, String clientAddress, int clientCount) {
        System.out.println("Client disconnected on socket: " + socketName +
                ", Address: " + clientAddress +
                ", Total clients on this socket: " + clientCount);
    }
}
