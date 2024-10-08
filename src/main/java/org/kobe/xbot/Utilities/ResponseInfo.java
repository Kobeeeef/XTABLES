package org.kobe.xbot.Utilities;

import java.util.Arrays;
import java.util.UUID;

public class ResponseInfo {
    private String ID;
    private final String method;
    private final String response;

    public ResponseInfo(String ID, MethodType method, String response) {
        this.response = response;
        this.ID = ID == null ? UUID.randomUUID().toString() : ID;
        this.method = method.toString();
    }
    public ResponseInfo(String ID, String method, String response) {
        this.response = response;
        this.ID = ID == null ? UUID.randomUUID().toString() : ID;
        this.method = method;
    }
    public ResponseInfo(String ID, MethodType method) {
        this.response = "";
        this.ID = ID == null ? UUID.randomUUID().toString() : ID;
        this.method = method.toString();
    }
    public ResponseInfo(String ID, String method) {
        this.response = "";
        this.ID = ID == null ? UUID.randomUUID().toString() : ID;
        this.method = method;
    }

    public ResponseInfo setID(String ID) {
        this.ID = ID;
        return this;
    }

    public String getID() {
        return ID;
    }

    public MethodType getMethod() {
        return MethodType.valueOf(method);
    }

    public String getResponse() {
        return response;
    }

    public String parsed() {
        return String.format("%1$s:%2$s %3$s", this.ID, this.method, this.response.replaceAll("\n", ""));
    }

    public static ResponseInfo from(String raw) {
        String[] tokens = raw.split(" ");
        String[] requestTokens = tokens[0].split(":");
        String ID = requestTokens[0];
        String stringMethod = requestTokens[1];
        return new ResponseInfo(ID, stringMethod, String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)));
    }
}
