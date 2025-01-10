package org.kobe.xbot.Tests;

import com.google.protobuf.InvalidProtocolBufferException;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.XTablesByteUtils;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.Arrays;

public class PullPushConsumer {

    public static void main(String[] args) throws InterruptedException {
        XTablesClient client = new XTablesClient();
        client.subscribe("test.ok.lol", (a) -> {
            System.out.println(XTablesByteUtils.toInt(a.getValue().toByteArray()))  ;
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(99999999);
        Thread.sleep(99999999);
    }
}
