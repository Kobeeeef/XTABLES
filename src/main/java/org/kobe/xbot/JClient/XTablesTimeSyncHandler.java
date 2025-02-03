//package org.kobe.xbot.JClient;
//
//import org.kobe.xbot.Utilities.Logger.XTablesLogger;
//import org.zeromq.SocketType;
//import org.zeromq.ZContext;
//import org.zeromq.ZMQ;
//
//import java.nio.ByteBuffer;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//
///**
// * XTablesTimeSyncHandler - A handler for managing time synchronization using JeroMQ.
// * <p>
// * This class initializes a ScheduledExecutorService to periodically perform
// * time synchronization tasks. It replaces the previous approach with a cleaner,
// * non-blocking design using scheduled tasks.
// * <p>
// * Author: Kobe Lei
// * Version: 1.1
// * Package: org.kobe.xbot.JClient
// * <p>
// * This is part of the XTABLES project and facilitates subscribing and handling incoming messages from the server.
// */
//public class XTablesTimeSyncHandler {
//    private final XTablesClient instance;
//    private final ScheduledExecutorService scheduler;
//    private ZMQ.Socket socket;
//    private final byte[] bytes = new byte[]{};
//    private static final XTablesLogger logger = XTablesLogger.getLogger(XTablesTimeSyncHandler.class);
//    private long offset = 0;
//
//    /**
//     * Constructor that initializes the handler with the provided socket.
//     *
//     * @param socket The ZeroMQ socket to send/receive messages.
//     */
//    public XTablesTimeSyncHandler(ZMQ.Socket socket, XTablesClient instance) {
//        this.socket = socket;
//        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
//            Thread thread = new Thread(r, "XTABLES-TIME-SYNC-HANDLER-THREAD");
//            thread.setDaemon(true); // Mark as a daemon thread
//            return thread;
//        });
//        this.instance = instance;
//        startSyncTask();
//    }
//
//    /**
//     * Starts the time synchronization task using a ScheduledExecutorService.
//     * This method schedules a periodic task to simulate the synchronization process.
//     */
//    private void startSyncTask() {
//        scheduler.scheduleWithFixedDelay(() -> {
//            try {
//                long t1 = System.currentTimeMillis();
//                socket.send(bytes, ZMQ.DONTWAIT);
//                byte[] bytes = socket.recv();
//                long t4 = System.currentTimeMillis();
//                long t3 = ByteBuffer.wrap(bytes).getLong();
//                offset = t3 - ((t1 + t4) / 2);
//                System.out.println(getSyncedCurrentTimeMillis());
//            } catch (Exception e) {
//                reconnectRequestSocket();
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException ignored) {
//                }
//            }
//        }, 0, 1, TimeUnit.MILLISECONDS);
//    }
//    private void reconnectRequestSocket() {
//        this.socket.close();
//        this.instance.getSocketMonitor().removeSocket("TIMESYNC");
//        this.socket = this.instance.getContext().createSocket(SocketType.REQ);
//        this.socket.setHWM(2);
//        this.socket.setReceiveTimeOut(1000);
//        this.socket.setReconnectIVL(1000);
//        this.socket.setReconnectIVLMax(1000);
//        this.instance.getSocketMonitor().addSocket("TIMESYNC", this.socket);
//        this.socket.connect("tcp://" + this.instance.getIp() + ":" + XTablesClient.TIME_SYNC_PORT);
//    }
//
//    public long getOffset() {
//        return offset;
//    }
//
//    public long getSyncedCurrentTimeMillis() {
//        return System.currentTimeMillis() + offset;
//    }
//
//    /**
//     * Stops the scheduled task and cleans up resources.
//     * This method shuts down the executor service.
//     */
//    public void shutdown() {
//        try {
//            scheduler.shutdownNow(); // Attempt to stop the scheduler immediately
//            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
//                logger.severe("Scheduler did not terminate in the expected time.");
//            }
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            logger.fatal("Interrupted while shutting down the scheduler.");
//        }
//        logger.info("TimeSyncHandler has been shut down.");
//    }
//}
