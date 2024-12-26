package org.kobe.xbot.JServer;

import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.ClientStatistics;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.Utilities;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ClientRegistry - A service for managing connected clients in the XTables server.
 * <p>
 * This class monitors and manages the list of connected clients. It periodically updates the server
 * with a new session ID and sends client updates to the server instance. The registry is also responsible
 * for tracking session ID changes and notifying relevant components of any updates.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: XTABLES
 * <p>
 * This class runs as a background thread, performing regular updates and managing client connections
 * for the XTABLES project.
 */
public class ClientRegistry extends Thread {
    private final static XTablesLogger logger = XTablesLogger.getLogger();
    private final static long loopInterval = 3000;
    private final XTablesServer instance;
    private final AtomicReference<ByteString> sessionId = new AtomicReference<>(null);
    private final LinkedList<ClientStatistics> clients;
    private long lastLoopTime = 0;

    /**
     * Constructor to initialize the ClientRegistry with the XTablesServer instance.
     *
     * @param instance the XTablesServer instance used to notify client updates
     */
    public ClientRegistry(XTablesServer instance) {
        this.instance = instance;
        this.clients = new LinkedList<>();
        setDaemon(true);
        setName("XTABLES-CLIENT-REGISTRY");
    }

    /**
     * The run method handles the periodic task of updating the client registry and session ID.
     * It runs in a loop, checking every 3 seconds to clear the client list and generate a new session ID.
     * Once the session ID is updated, it notifies the server of the update.
     * The loop runs until the thread is interrupted, and it sleeps for 100 milliseconds between iterations
     * to reduce CPU usage.
     */
    @Override
    public void run() {
        try {
            executeTask();
            while (!Thread.currentThread().isInterrupted()) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLoopTime >= loopInterval) {
                    executeTask();
                    lastLoopTime = currentTime;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Executes the periodic task of clearing the client list, updating the session ID,
     * and notifying the instance of the update.
     */
    private void executeTask() {
        clients.clear();
        sessionId.set(ByteString.copyFrom(Utilities.generateRandomBytes(10)));
        instance.notifyUpdateClients(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.REGISTRY)
                .setValue(sessionId.get())
                .build());
    }

    /**
     * Returns the number of milliseconds before the next loop will execute.
     *
     * @return milliseconds before the next loop
     */
    public long getMillisBeforeNextLoop() {
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastLoopTime >= loopInterval) ? 0 : loopInterval - (currentTime - lastLoopTime);
    }

    /**
     * Returns the current session ID.
     *
     * @return the session ID
     */
    public ByteString getSessionId() {
        return sessionId.get();
    }

    /**
     * Returns the list of connected clients.
     *
     * @return the list of connected clients
     */
    public LinkedList<ClientStatistics> getClients() {
        return clients;
    }

    /**
     * Logs exceptions that occur during thread execution.
     *
     * @param exception the exception to log
     */
    protected void handleException(Exception exception) {
        logger.severe("Exception in thread " + getName() + ": " + exception.getMessage());
    }
}
