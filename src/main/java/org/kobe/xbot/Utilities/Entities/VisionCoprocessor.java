package org.kobe.xbot.Utilities.Entities;

public enum VisionCoprocessor {
    ORIN1("orin-nano-1"),
    ORIN2("orin-nano-2"),
    ORIN3("orin-nano-3"),
    ORANGE_PI_FRONT_RIGHT("photonvisionfrontright"),
    ORANGE_PI_FRONT_LEFT("photonvisionfrontleft"),
    ORANGE_PI_REAR_RIGHT("photonvisionrearright"),
    ORANGE_PI_REAR_LEFT("photonvisionrearleft"),
    ORANGE_PI_BACK("photonvisionback");
    private final String hostname;
    VisionCoprocessor(String hostname) {
        this.hostname = hostname;
    }

    public String getQualifiedHostname() {
        return hostname + ".local";
    }
}
