package org.kobe.xbot.JClient;

import org.kobe.xbot.Utilities.Entities.Requests;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.zeromq.ZMQ;

public class XTableContext extends Requests implements AutoCloseable {
    private static final XTablesLogger logger = XTablesLogger.getLogger(XTableContext.class);
    private final ZMQ.Socket push;
    private final ZMQ.Socket get;
    private final XTablesClient xTablesClient;
    private final String key;

    public XTableContext(ZMQ.Socket push, ZMQ.Socket get, String key, XTablesClient xTablesClient) {
        super(xTablesClient, key);
        this.push = push;
        this.get = get;
        this.xTablesClient = xTablesClient;
        this.key = key;

        super.setSockets(get, push);
    }

    public XTablesClient getxTablesClient() {
        return xTablesClient;
    }

    @Override
    public void close() {
        this.push.close();
        this.get.close();
        this.xTablesClient.shutdownXTableContext(this.key);
    }

}
