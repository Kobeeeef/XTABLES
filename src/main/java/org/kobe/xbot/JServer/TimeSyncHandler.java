package org.kobe.xbot.JServer;


import com.google.gson.Gson;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;

/**
 * ReplyRequestHandler - A handler for processing reply requests using JeroMQ.
 * <p>
 * This class processes incoming reply requests on a JeroMQ socket. It runs in its own thread
 * and parses messages using Protocol Buffers. If the message contains a valid command, further
 * processing can be implemented inside the `run` method. The thread is set as a daemon, meaning
 * it won't block the JVM from exiting.
 * <p>
 * Author: Kobe Lei
 * Package: XTABLES
 * Version: 1.0
 * <p>
 * This class is part of the XTABLES project and is used for handling server-side reply requests
 * in a multithreaded environment with JeroMQ.
 */
public class TimeSyncHandler extends BaseHandler {
    private static final Gson gson = new Gson();
    private final XTablesServer instance;

    /**
     * Constructor that initializes the handler with the provided socket and server instance.
     *
     * @param socket   The ZeroMQ socket to receive reply requests on
     * @param instance The XTablesServer instance
     */
    public TimeSyncHandler(ZMQ.Socket socket, XTablesServer instance) {
        super("XTABLES-TIME-SYNC-HANDLER-DAEMON", true, socket);
        this.instance = instance;
    }

    /**
     * The main method for handling incoming reply requests.
     * It continuously receives messages from the JeroMQ socket and processes them.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                socket.recv();
                try {
                    socket.send(ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array(), ZMQ.DONTWAIT);
                } catch (Exception e) {
                    handleException(e);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

}
