package org.kobe.xbot.Utilities.Entities;

import com.google.protobuf.ByteString;
import org.kobe.xbot.JClient.XTablesClient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BatchedPushRequests implements PushRequests {
    private final List<XTableProto.XTableMessage> data;

    public BatchedPushRequests() {
        this.data = new ArrayList<>();
    }

    public List<XTableProto.XTableMessage> getData() {
        return data;
    }

    @Override
    public boolean publish(String key, byte[] value) {
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUBLISH)
                .setValue(ByteString.copyFrom(value))
                .build()
        );
    }

    @Override
    public boolean putDoubleList(String key, List<Double> value) {
        XTableValues.DoubleList.Builder builder = XTableValues.DoubleList.newBuilder()
                .addAllV(value);
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(builder.build().toByteString())
                .setType(XTableProto.XTableMessage.Type.DOUBLE_LIST)
                .build()
        );
    }

    @Override
    public boolean putStringList(String key, List<String> value) {
        XTableValues.StringList.Builder builder = XTableValues.StringList.newBuilder()
                .addAllV(value);
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(builder.build().toByteString())
                .setType(XTableProto.XTableMessage.Type.STRING_LIST)
                .build()
        );
    }

    @Override
    public boolean putIntegerList(String key, List<Integer> value) {
        XTableValues.IntegerList.Builder builder = XTableValues.IntegerList.newBuilder()
                .addAllV(value);
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(builder.build().toByteString())
                .setType(XTableProto.XTableMessage.Type.INTEGER_LIST)
                .build()
        );
    }

    @Override
    public boolean putBytesList(String key, List<ByteString> value) {
        XTableValues.BytesList.Builder builder = XTableValues.BytesList.newBuilder()
                .addAllV(value);
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(builder.build().toByteString())
                .setType(XTableProto.XTableMessage.Type.BYTES_LIST)
                .build()
        );
    }

    @Override
    public boolean putLongList(String key, List<Long> value) {
        XTableValues.LongList.Builder builder = XTableValues.LongList.newBuilder()
                .addAllV(value);
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(builder.build().toByteString())
                .setType(XTableProto.XTableMessage.Type.LONG_LIST)
                .build()
        );
    }

    @Override
    public boolean putFloatList(String key, List<Float> value) {
        XTableValues.FloatList.Builder builder = XTableValues.FloatList.newBuilder()
                .addAllV(value);
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(builder.build().toByteString())
                .setType(XTableProto.XTableMessage.Type.FLOAT_LIST)
                .build()
        );
    }


    @Override
    public boolean putBooleanList(String key, List<Boolean> value) {
        XTableValues.BoolList.Builder builder = XTableValues.BoolList.newBuilder()
                .addAllV(value);
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(builder.build().toByteString())
                .setType(XTableProto.XTableMessage.Type.BOOL)
                .build()
        );
    }

    @Override
    public boolean putBoolean(String key, boolean value) {
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(value ? XTablesClient.successByte : XTablesClient.failByte)
                .setType(XTableProto.XTableMessage.Type.BOOL)
                .build()
        );
    }

    @Override
    public boolean putDouble(String key, Double value) {
        byte[] valueBytes = ByteBuffer.allocate(8).putDouble(value).array();
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(valueBytes))
                .setType(XTableProto.XTableMessage.Type.DOUBLE)
                .build()
        );
    }

    @Override
    public boolean putLong(String key, Long value) {
        byte[] valueBytes = ByteBuffer.allocate(8).putLong(value).array();
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(valueBytes))
                .setType(XTableProto.XTableMessage.Type.INT64)
                .build()
        );
    }

    @Override
    public boolean putInteger(String key, Integer value) {
        byte[] valueBytes = ByteBuffer.allocate(4).putInt(value).array();
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(valueBytes))
                .setType(XTableProto.XTableMessage.Type.INT64)
                .build()
        );
    }

    @Override
    public boolean putCoordinates(String key, List<XTableValues.Coordinate> value) {
        XTableValues.CoordinateList list = XTableValues.CoordinateList.newBuilder()
                .addAllCoordinates(value).build();
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(list.toByteString())
                .setType(XTableProto.XTableMessage.Type.BYTES)
                .build()
        );
    }

    @Override
    public boolean putString(String key, String value) {
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(value.getBytes(StandardCharsets.UTF_8)))
                .setType(XTableProto.XTableMessage.Type.STRING)
                .build()
        );
    }

    @Override
    public boolean putUnknownBytes(String key, byte[] value) {
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(value))
                .setType(XTableProto.XTableMessage.Type.UNKNOWN)
                .build()
        );
    }

    @Override
    public boolean putBytes(String key, byte[] value, XTableProto.XTableMessage.Type type) {
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(value))
                .setType(type)
                .build()
        );
    }

    @Override
    public boolean putBytes(String key, byte[] value) {
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(value))
                .setType(XTableProto.XTableMessage.Type.BYTES)
                .build()
        );
    }
}
