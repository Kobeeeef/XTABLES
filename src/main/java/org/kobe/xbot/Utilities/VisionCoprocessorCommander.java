package org.kobe.xbot.Utilities;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.kobe.xbot.Utilities.Entities.VisionCoprocessor;
import org.kobe.xbot.Utilities.Entities.VisionCoprocessorGrpc;
import org.kobe.xbot.Utilities.Entities.XTableValues;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;

import java.util.concurrent.TimeUnit;


public class VisionCoprocessorCommander implements AutoCloseable {
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private final ManagedChannel channel;
    private final VisionCoprocessorGrpc.VisionCoprocessorBlockingV2Stub blockingStub;

    /**
     * Constructs the client manager with the given host and port.
     *
     * @param coprocessor the vision host
     */
    public VisionCoprocessorCommander(VisionCoprocessor coprocessor) {
        // Create a channel to the gRPC server.
        this.channel = ManagedChannelBuilder.forAddress(coprocessor.getQualifiedHostname(), 9281)
                .usePlaintext()
                .build();
        ConnectivityState state = this.channel.getState(true);
        if (state != ConnectivityState.READY) {
            channel.notifyWhenStateChanged(state, new Runnable() {
                @Override
                public void run() {
                    // This callback is invoked once the state has changed.
                    logger.info("Channel connection state changed; now attempting to resolve.");
                }
            });
        }
        this.blockingStub = VisionCoprocessorGrpc.newBlockingV2Stub(channel);
    }

    public VisionCoprocessorCommander(String hostname) {
        // Create a channel to the gRPC server.
        this.channel = ManagedChannelBuilder.forAddress(hostname, 9281)
                .usePlaintext()
                .build();
        this.blockingStub = VisionCoprocessorGrpc.newBlockingV2Stub(channel);
    }

    /**
     * Sends a Bezier path request with options to the server.
     *
     * @param options the request options as defined in the proto
     * @return the server's response, or null if the RPC fails
     */
    public XTableValues.BezierCurves requestBezierPathWithOptions(XTableValues.RequestVisionCoprocessorMessage options, long timeout, TimeUnit timeUnit) {
        try {
            return blockingStub.withDeadlineAfter(timeout, timeUnit).withWaitForReady().requestBezierPathWithOptions(options);
        } catch (StatusRuntimeException e) {
            e.printStackTrace();
            logger.info("RPC failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Shuts down the gRPC channel.
     */
    private void shutdown() {
        channel.shutdown();
    }

    /**
     * AutoCloseable implementation to allow try-with-resources usage.
     */
    @Override
    public void close() {
        shutdown();
    }


//    public static void main(String[] args) throws InterruptedException {
//        new VisionCoprocessorCommander(VisionCoprocessor.ORIN1)
//                .requestBezierPathWithOptions(XTableValues.RequestVisionCoprocessorMessage.newBuilder()
//                        .setStart(XTableValues.ControlPoint.newBuilder()
//                                .setX(1)
//                                .setY(10)
//                                .build()
//                        )
//                        .setEnd(XTableValues.ControlPoint.newBuilder()
//                                .setX(2.1)
//                                .setY(3.1)
//                                .setRotationDegrees(10)
//                                .build()
//                        )
//                        .build(), 3, TimeUnit.SECONDS);
//    }
}
