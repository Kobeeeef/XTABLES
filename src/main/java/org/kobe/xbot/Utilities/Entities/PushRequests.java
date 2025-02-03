package org.kobe.xbot.Utilities.Entities;

import com.google.protobuf.ByteString;

import java.util.List;

public interface PushRequests {
    boolean putBytes(String key, byte[] value);

    boolean putBytes(String key, byte[] value, XTableProto.XTableMessage.Type type);

    boolean putUnknownBytes(String key, byte[] value);

    boolean putString(String key, String value);

    boolean putCoordinates(String key, List<XTableValues.Coordinate> value);

    boolean putInteger(String key, Integer value);

    boolean putLong(String key, Long value);

    boolean putDouble(String key, Double value);

    boolean putBoolean(String key, boolean value);

    boolean putDoubleList(String key, List<Double> value);

    boolean putStringList(String key, List<String> value);

    boolean putIntegerList(String key, List<Integer> value);

    boolean putBytesList(String key, List<ByteString> value);

    boolean putLongList(String key, List<Long> value);

    boolean putFloatList(String key, List<Float> value);

    boolean putBooleanList(String key, List<Boolean> value);

    boolean publish(String key, byte[] value);

    boolean putTypedBytes(String key, XTableProto.XTableMessage.Type type, byte[] value);
}
