package org.kobe.xbot.JClient;


import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Entities.XTableValues;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.kobe.xbot.JClient.XTablesClient.successByte;

public class CachedSubscriber {
    private final XTablesClient client;
    private final Consumer<XTableProto.XTableMessage.XTableUpdate> subscriber;
    private XTableProto.XTableMessage.XTableUpdate lastUpdate;

    public CachedSubscriber(String key, XTablesClient client) {
        this.client = client;
        this.subscriber = xTable -> this.lastUpdate = xTable;
        client.subscribe(key, this.subscriber);
    }

    public boolean unsubscribe() {
        return client.unsubscribe(this.subscriber);
    }

    public XTableProto.XTableMessage.XTableUpdate get() {
        return lastUpdate;
    }

    public String getAsString(String defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.STRING)) {
            return new String(lastUpdate.getValue().toByteArray(), StandardCharsets.UTF_8);
        }
        return defaultValue;
    }

    public boolean getAsBoolean(boolean defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.BOOL)) {
            return lastUpdate.getValue().equals(successByte);
        }
        return defaultValue;
    }

    public int getAsInteger(int defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.INT64)) {
            return ByteBuffer.wrap(lastUpdate.getValue().toByteArray()).getInt();
        }
        return defaultValue;
    }

    public long getAsLong(long defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.INT64)) {
            return ByteBuffer.wrap(lastUpdate.getValue().toByteArray()).getLong();
        }
        return defaultValue;
    }

    public double getAsDouble(double defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.DOUBLE)) {
            return ByteBuffer.wrap(lastUpdate.getValue().toByteArray()).getDouble();
        }
        return defaultValue;
    }

    public List<XTableValues.Coordinate> getAsCoordinates(List<XTableValues.Coordinate> defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.BYTES)) {
            try {
                return XTableValues.CoordinateList.parseFrom(lastUpdate.getValue().toByteArray()).getCoordinatesList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public List<String> getAsStringList(List<String> defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.STRING_LIST)) {
            try {
                return XTableValues.StringList.parseFrom(lastUpdate.getValue().toByteArray()).getVList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public List<Integer> getAsIntegerList(List<Integer> defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.INTEGER_LIST)) {
            try {
                return XTableValues.IntegerList.parseFrom(lastUpdate.getValue().toByteArray()).getVList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public List<Boolean> getAsBooleanList(List<Boolean> defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.BOOLEAN_LIST)) {
            try {
                return XTableValues.BoolList.parseFrom(lastUpdate.getValue().toByteArray()).getVList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public List<Long> getAsLongList(List<Long> defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.LONG_LIST)) {
            try {
                return XTableValues.LongList.parseFrom(lastUpdate.getValue().toByteArray()).getVList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public List<Float> getAsFloatList(List<Float> defaultValue) {
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.LONG_LIST)) {
            try {
                return XTableValues.FloatList.parseFrom(lastUpdate.getValue().toByteArray()).getVList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }


}
