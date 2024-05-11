package org.kobe.xbot.Utilites;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class DataCompression {
    private static final Logger logger = Logger.getLogger(DataCompression.class.getName());
    private static int compressionLevel = Deflater.BEST_SPEED;
    private static double speedAverageMS = 0.3;

    public static String compressAndConvertBase64(String raw) {
        byte[] compressedData = compress(raw.getBytes());
        return Base64.getEncoder().encodeToString(compressedData);
    }

    public static String decompressAndConvertBase64(String base64) {
        byte[] decompressedDataString = Base64.getDecoder().decode(base64);
        byte[] decompressedData = decompress(decompressedDataString);
        assert decompressedData != null;
        return new String(decompressedData);
    }

    public static double getSpeedAverageMS() {
        return speedAverageMS;
    }

    public static void setSpeedAverageMS(double speedAverageMS) {
        DataCompression.speedAverageMS = speedAverageMS;
    }

    public static byte[] compress(byte[] data) {
        try {
            long startTime = System.nanoTime();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(compressionLevel);
            DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(outputStream, deflater);
            deflaterOutputStream.write(data);
            deflaterOutputStream.close();
            adjustCompressionLevel(System.nanoTime() - startTime);
            return outputStream.toByteArray();
        } catch (IOException e) {
            logger.severe(e.getMessage());
            return null;
        }
    }


    public static byte[] decompress(byte[] compressedData) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Inflater inflater = new Inflater();
            InflaterInputStream inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(compressedData), inflater);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inflaterInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inflaterInputStream.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            logger.severe(e.getMessage());
            return null;
        }
    }

    private static void adjustCompressionLevel(long elapsed) {
        double ms = elapsed / 1e6;

        if (ms < DataCompression.speedAverageMS) {
            // If compression takes less time than the average, increase compression level
            if (DataCompression.compressionLevel < Deflater.BEST_COMPRESSION) {
                DataCompression.compressionLevel++;
                logger.info("Compression time (" + ms + " ms) is faster than average. Increasing compression level to: " + DataCompression.compressionLevel);
            }
        } else {
            // If compression takes more time than the average, decrease compression level
            if (DataCompression.compressionLevel > Deflater.NO_COMPRESSION) {
                DataCompression.compressionLevel--;
                logger.info("Compression time (" + ms + " ms) is slower than average. Reducing compression level to: " + DataCompression.compressionLevel);
            }
        }
    }


}