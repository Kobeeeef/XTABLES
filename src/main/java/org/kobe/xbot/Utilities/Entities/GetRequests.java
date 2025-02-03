package org.kobe.xbot.Utilities.Entities;

import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.SystemStatistics;

import java.util.List;

public interface GetRequests {
    byte[] getBytes(String key);

    byte[] getUnknownBytes(String key);

    String getString(String key);

    List<XTableValues.Coordinate> getCoordinates(String key);

    Integer getInteger(String key);

    Long getLong(String key);

    Double getDouble(String key);

    Boolean getBoolean(String key);

    List<Double> getDoubleList(String key);

    List<String> getStringList(String key);

    List<Integer> getIntegerList(String key);

    List<ByteString> getBytesList(String key);

    List<Long> getLongList(String key);

    List<Float> getFloatList(String key);

    List<Boolean> getBooleanList(String key);

    PingResponse getPing();

    String getRawJson();

    List<String> getTables();
    List<String> getTables(String key);
    SystemStatistics getServerStatistics();
    boolean setServerDebug(boolean debug);
    boolean delete();
    boolean delete(String key);
    boolean reboot();
}
