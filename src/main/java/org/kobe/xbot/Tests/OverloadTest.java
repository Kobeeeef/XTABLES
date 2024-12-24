//package org.kobe.xbot.Tests;
//
//
//
//
//import org.kobe.xbot.ClientLite.XTablesClient;
//
//import java.io.IOException;
//
//public class OverloadTest {
//    private static final String SERVER_ADDRESS = "localhost"; // Server address
//    private static final int SERVER_PORT = 1735; // Server port
//
//    public static void main(String[] args) throws IOException, InterruptedException {
//        // Initialize a new client with address and port
//        Object a = new byte[] { 1, 2, 3 };
//        System.out.println(a);
//        if(1==1) return;
//        XTablesClient client = new org.kobe.xbot.ClientLite.XTablesClient("localhost", 1735);
//        int i = 0;
//
//        while(true) {
//            client.executePutInteger("SmartDashboard", i+25);
//            client.executePutInteger("SmartDashboard.blackmeta", i);
//            client.executePutInteger("SmartDashboard.motorWheelSpeed", i);
//            client.executePutString("robot", "DISABLED");
//            i++;
//
//
//        }
////        Double[] response = client.getObject("test", Double[].class).complete();
////        System.out.println(Arrays.toString(response));
////
////
////        String b = client.getString("test").complete();
////        System.out.println(b);
////        System.out.println(b == null);
//
//    }
//
//
//}
