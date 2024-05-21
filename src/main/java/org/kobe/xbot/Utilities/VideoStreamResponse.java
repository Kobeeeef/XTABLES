package org.kobe.xbot.Utilities;

import org.kobe.xbot.Client.VideoStreamClient;
import org.kobe.xbot.Client.VideoStreamServer;

public class VideoStreamResponse {
    private VideoStreamServer streamServer;
    private VideoStreamClient streamClient;
    private ResponseStatus status;
    private String address;

    public VideoStreamResponse(VideoStreamServer streamServer, ResponseStatus status) {
        this.streamServer = streamServer;
        this.status = status;
    }

    public VideoStreamResponse(VideoStreamClient streamClient, ResponseStatus status) {
        this.streamClient = streamClient;
        this.status = status;
    }
    public VideoStreamResponse(ResponseStatus status) {
        this.status = status;
    }

    public String getAddress() {
        return address;
    }

    public VideoStreamResponse setAddress(String address) {
        this.address = address;
        return this;
    }

    public VideoStreamServer getStreamServer() {
        return streamServer;
    }

    public VideoStreamClient getStreamClient() {
        return streamClient;
    }

    public VideoStreamResponse setStreamClient(VideoStreamClient streamClient) {
        this.streamClient = streamClient;
        return this;
    }

    public void setStreamServer(VideoStreamServer streamServer) {
        this.streamServer = streamServer;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public VideoStreamResponse setStatus(ResponseStatus status) {
        this.status = status;
        return this;
    }
}
