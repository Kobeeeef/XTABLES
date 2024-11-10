package org.kobe.xbot.Utilities;

public class ClientData {
    private final String clientIP;
    private final String hostname;
    private final int messages;
    private final String identifier;

    public ClientData(String clientIP, String hostname, int messages, String identifier) {
        this.clientIP = clientIP;
        this.hostname = hostname;
        this.messages = messages;
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public int getMessages() {
        return messages;
    }

    public String getClientIP() {
        return clientIP;
    }
}
