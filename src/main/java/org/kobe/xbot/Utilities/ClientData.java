package org.kobe.xbot.Utilities;

public class ClientData {
    private final String clientIP;
    private final int messages;

    public ClientData(String clientIP, int messages) {
        this.clientIP = clientIP;
        this.messages = messages;
    }

    public int getMessages() {
        return messages;
    }

    public String getClientIP() {
        return clientIP;
    }
}
