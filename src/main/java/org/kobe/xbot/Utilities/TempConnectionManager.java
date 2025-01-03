package org.kobe.xbot.Utilities;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class TempConnectionManager {

    public static String get() {
        File tempFile = new File(System.getProperty("java.io.tmpdir"), "XTABLES-TEMP-CONNECTION.tmp");
        if (tempFile.exists()) {
            try {
                return Files.readString(tempFile.toPath()).trim();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static void set(String ipAddress) {
        try {
            File tempFile = new File(System.getProperty("java.io.tmpdir"), "XTABLES-TEMP-CONNECTION.tmp");
            if (!tempFile.exists()) {
                tempFile.createNewFile();
            }
            Files.writeString(tempFile.toPath(), ipAddress, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
