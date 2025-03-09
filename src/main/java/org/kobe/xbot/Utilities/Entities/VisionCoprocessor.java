package org.kobe.xbot.Utilities.Entities;

public enum VisionCoprocessor {
    ORIN1("orin-nano-1", true),
    ORIN2("orin-nano-2", true),
    ORIN3_DHCP("orin-nano-3", true),
    ORIN3_STATIC("10.4.88.7", false),
    ORIN3_XCASTER("orin-3", true),
    ORANGE_PI_FRONT_RIGHT("photonvisionfrontright", true),
    ORANGE_PI_FRONT_RIGHT_STATIC("10.4.88.9", false),

    ORANGE_PI_FRONT_LEFT("photonvisionfrontleft", true),
    ORANGE_PI_FRONT_LEFT_STATIC("10.4.88.8", false),

    ORANGE_PI_REAR_RIGHT("photonvisionrearright", true),
    ORANGE_PI_REAR_LEFT("photonvisionrearleft", true),
    ORANGE_PI_BACK("photonvisionback", true),
    ORANGE_PI_BACK_STATIC("10.4.88.10", false),

    LOCALHOST("localhost", true);
    private final String hostname;
    private final boolean isHostname;

    VisionCoprocessor(String hostname, boolean isHostname) {
        this.hostname = hostname;
        this.isHostname = isHostname;
    }

    public String getQualifiedHostname() {
        return this.isHostname ? (this.equals(LOCALHOST) ? hostname : hostname + ".local") : hostname;
    }
}
