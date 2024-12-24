//package org.kobe.xbot.Tests;
//
//import com.google.protobuf.ByteString;
//import org.kobe.xbot.Utilities.Entities.XTableProto;
//import org.kobe.xbot.Utilities.Logger.XTablesLogger;
//
//import java.nio.ByteBuffer;
//import java.nio.charset.StandardCharsets;
//import java.util.concurrent.ThreadLocalRandom;
//
//
//public class Test {
//    private static final XTablesLogger logger = XTablesLogger.getLogger();
//    public static void main(final String[] args) {
//
//
//
//        warmupProtobuf();
//
//
//        String stringValue = "Hello, world!";
//        long time = System.nanoTime();
//        long id = ThreadLocalRandom.current().nextLong();
//        byte[] stringBytes = stringValue.getBytes(StandardCharsets.UTF_8);
//        XTableProto.XTableMessage message = XTableProto.XTableMessage.newBuilder()
//                .setId(id)
//
//                .setValue(ByteString.copyFrom(stringBytes))
//                .build();
//        message.toByteArray();
//        long durationMs = ((System.nanoTime() - time) - 100) ;
//        System.out.println("Protobuf Total time: " + durationMs + " ns");
//    }
//
//
//
//
//}
