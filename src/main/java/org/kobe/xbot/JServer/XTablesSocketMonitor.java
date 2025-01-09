package org.kobe.xbot.JServer;

import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;

import java.util.*;

public class XTablesSocketMonitor extends Thread {
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private final ZContext context;
    private final Map<ZMQ.Socket, String> monitorSocketNames = new HashMap<>();
    private final Map<String, List<String>> clientMap = new HashMap<>();
    private final Poller poller;
    private volatile boolean running = true;

    public XTablesSocketMonitor(ZContext context) {
        this.context = context;
        this.poller = context.createPoller(0); // Create a poller with no initial sockets
        setDaemon(true);
    }

    public XTablesSocketMonitor addSocket(String socketName, ZMQ.Socket socket) {
        String monitorAddress = "inproc://monitor-" + socket.hashCode();
        socket.monitor(monitorAddress,
                ZMQ.EVENT_ACCEPTED |
                        ZMQ.EVENT_BIND_FAILED |
                        ZMQ.EVENT_CLOSED |
                        ZMQ.EVENT_CONNECTED |
                        ZMQ.EVENT_CONNECT_DELAYED |
                        ZMQ.EVENT_CONNECT_RETRIED |
                        ZMQ.EVENT_DISCONNECTED |
                        ZMQ.EVENT_LISTENING |
                        ZMQ.EVENT_MONITOR_STOPPED);
        ZMQ.Socket monitorSocket = context.createSocket(ZMQ.PAIR);
        monitorSocket.connect(monitorAddress);

        monitorSocketNames.put(monitorSocket, socketName);
        clientMap.put(socketName, new ArrayList<>());
        poller.register(monitorSocket, Poller.POLLIN); // Register monitor socket to poll for events
        return this;
    }

    @Override
    public void run() {
        try {
            while (running) {
                int events = poller.poll(1000);
                if (events > 0) {
                    for (int i = 0; i < poller.getSize(); i++) {
                        if (poller.pollin(i)) {
                            ZMQ.Socket monitorSocket = poller.getSocket(i);
                            ZMQ.Event event = ZMQ.Event.recv(monitorSocket);
                            if (event != null) {
                                handleEvent(monitorSocketNames.get(monitorSocket), event);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in ZeroMQ event loop: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleEvent(String socketName, ZMQ.Event event) {
        String clientAddress = event.getAddress();
        if (clientAddress == null && event.getEvent() != ZMQ.EVENT_MONITOR_STOPPED) return;

        switch (event.getEvent()) {
            case ZMQ.EVENT_ACCEPTED -> onClientConnected(socketName, clientAddress, clientMap.get(socketName).size());
            case ZMQ.EVENT_DISCONNECTED ->
                    onClientDisconnected(socketName, clientAddress, clientMap.get(socketName).size());
            case ZMQ.EVENT_CONNECTED -> logger.info("Socket connected: " + socketName + " to " + clientAddress);
            case ZMQ.EVENT_CONNECT_DELAYED ->
                    logger.warning("Connection delayed: " + socketName + " to " + clientAddress);
            case ZMQ.EVENT_CONNECT_RETRIED -> logger.info("Connection retried: " + socketName + " to " + clientAddress);
            case ZMQ.EVENT_BIND_FAILED -> logger.severe("Bind failed on socket: " + socketName);
            case ZMQ.EVENT_CLOSED -> logger.info("Socket closed: " + socketName);
            case ZMQ.EVENT_LISTENING -> logger.info("Socket is listening: " + socketName);
            case ZMQ.EVENT_MONITOR_STOPPED -> logger.warning("Monitor stopped for socket: " + socketName);
            default -> logger.fatal("Unhandled event: " + event.getEvent() + " on socket: " + socketName);
        }
    }

    @Override
    public void interrupt() {
        running = false;
        super.interrupt();
        cleanup();
    }

    private void cleanup() {
        for (ZMQ.Socket monitorSocket : new HashSet<>(monitorSocketNames.keySet())) {
            monitorSocket.close();
        }
        monitorSocketNames.clear();
        clientMap.clear();
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
