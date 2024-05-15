package org.kobe.xbot.Utilities;


public record RequestInfo(String raw) {

    public RequestInfo(String raw) {
        this.raw = raw.replaceAll("\n", "");
    }

    public String getID() {
        String[] requestTokens = getTokens()[0].split(":");
        return requestTokens[0];
    }

    public MethodType getMethod() {
        String[] requestTokens = getTokens()[0].split(":");
        String stringMethod = requestTokens[1];
        return MethodType.valueOf(stringMethod);
    }

    public String[] getTokens() {
        return raw.split(" ");
    }
}
