package org.kobe.xbot.Utilities.Entities;

public class Client {
    private final String address;
    private String hostname;


    public Client(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public String getHostname() {
        return hostname;
    }

    public Client setHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }
}
