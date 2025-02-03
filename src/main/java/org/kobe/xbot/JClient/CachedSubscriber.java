package org.kobe.xbot.JClient;


import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.CircularBuffer;
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
    private final CircularBuffer<XTableProto.XTableMessage.XTableUpdate> circularBuffer;

    public CachedSubscriber(String key, XTablesClient client, int readQueueSize) {
        this.client = client;
        this.circularBuffer = new CircularBuffer<>(readQueueSize);
        this.subscriber = this.circularBuffer::write;
        client.subscribe(key, this.subscriber);
    }

    public XTableProto.XTableMessage.XTableUpdate[] readAll() {
        return this.circularBuffer.readAll();
    }

    public void clear() {
        this.circularBuffer.clear();
    }

    public CachedSubscriber(String key, XTablesClient client) {
        this(key, client, 1);
    }

    public void write(XTableProto.XTableMessage.XTableUpdate update) {
        this.circularBuffer.write(update);
    }

    public void write(XTableProto.XTableMessage.Type type, byte[] data) {
        this.circularBuffer.write(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                .setType(type)
                .setValue(ByteString.copyFrom(data))
                .buildPartial());
    }

    public void write(byte[] data) {
        this.circularBuffer.write(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                .setType(XTableProto.XTableMessage.Type.UNKNOWN)
                .setValue(ByteString.copyFrom(data))
                .buildPartial());
    }

    public boolean unsubscribe() {
        return client.unsubscribe(this.subscriber);
    }

    public XTableProto.XTableMessage.XTableUpdate get() {
        return this.circularBuffer.read();
    }

    public String getAsString(String defaultValue) {
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.STRING)) {
            return new String(lastUpdate.getValue().toByteArray(), StandardCharsets.UTF_8);
        }
        return defaultValue;
    }

    public boolean getAsBoolean(boolean defaultValue) {
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.BOOL)) {
            return lastUpdate.getValue().equals(successByte);
        }
        return defaultValue;
    }

    public int getAsInteger(int defaultValue) {
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.INT64)) {
            return ByteBuffer.wrap(lastUpdate.getValue().toByteArray()).getInt();
        }
        return defaultValue;
    }

    public long getAsLong(long defaultValue) {
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.INT64)) {
            return ByteBuffer.wrap(lastUpdate.getValue().toByteArray()).getLong();
        }
        return defaultValue;
    }

    public double getAsDouble(double defaultValue) {
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.DOUBLE)) {
            return ByteBuffer.wrap(lastUpdate.getValue().toByteArray()).getDouble();
        }
        return defaultValue;
    }

    public List<XTableValues.Coordinate> getAsCoordinates(List<XTableValues.Coordinate> defaultValue) {
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
        if (lastUpdate != null && (lastUpdate.getType().equals(XTableProto.XTableMessage.Type.BYTES) || lastUpdate.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN))) {
            try {
                return XTableValues.CoordinateList.parseFrom(lastUpdate.getValue().toByteArray()).getCoordinatesList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public List<String> getAsStringList(List<String> defaultValue) {
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
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
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
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
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
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
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
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
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.LONG_LIST)) {
            try {
                return XTableValues.FloatList.parseFrom(lastUpdate.getValue().toByteArray()).getVList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public List<Double> getAsDoubleList(List<Double> defaultValue) {
        XTableProto.XTableMessage.XTableUpdate lastUpdate = get();
        if (lastUpdate != null && lastUpdate.getType().equals(XTableProto.XTableMessage.Type.DOUBLE_LIST)) {
            try {
                return XTableValues.DoubleList.parseFrom(lastUpdate.getValue().toByteArray()).getVList();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
