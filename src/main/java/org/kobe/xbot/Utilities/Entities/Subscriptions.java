package org.kobe.xbot.Utilities.Entities;

import com.google.protobuf.ByteString;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import org.kobe.xbot.JClient.CachedSubscriber;
import org.kobe.xbot.Utilities.Exceptions.XTablesException;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.List;
import java.util.function.Consumer;

public interface Subscriptions {
    boolean subscribeToServerLogs(Consumer<XTableProto.XTableMessage.XTableLog> consumer);

    boolean unsubscribeToServerLogs(Consumer<XTableProto.XTableMessage.XTableLog> consumer);

    boolean subscribe(String key, Consumer<XTableProto.XTableMessage.XTableUpdate> consumer);

    boolean subscribe(Consumer<XTableProto.XTableMessage.XTableUpdate> consumer);

    boolean unsubscribe(String key, Consumer<XTableProto.XTableMessage.XTableUpdate> consumer);

    boolean unsubscribe(Consumer<XTableProto.XTableMessage.XTableUpdate> consumer);

    CachedSubscriber subscribe(String key);

    CachedSubscriber subscribe(String key, int queue);

    default <E> boolean subscribe(String key, Consumer<E> consumer, XTableProto.XTableMessage.Type type) {
        Consumer<XTableProto.XTableMessage.XTableUpdate> checkConsumer = null;

        if (type.equals(XTableProto.XTableMessage.Type.STRING)) {
            @SuppressWarnings("unchecked") Consumer<String> stringConsumer = (Consumer<String>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.STRING) {
                    stringConsumer.accept(update.getValue().toStringUtf8());
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.INT64)) {
            @SuppressWarnings("unchecked") Consumer<Long> stringConsumer = (Consumer<Long>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.INT64) {
                    Long value = XTablesByteUtils.toLong(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.INT32)) {
            @SuppressWarnings("unchecked") Consumer<Integer> stringConsumer = (Consumer<Integer>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.INT32) {
                    Integer value = XTablesByteUtils.toInteger(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.DOUBLE)) {
            @SuppressWarnings("unchecked") Consumer<Double> stringConsumer = (Consumer<Double>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.DOUBLE) {
                    Double value = XTablesByteUtils.toDouble(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.BOOL)) {
            @SuppressWarnings("unchecked") Consumer<Boolean> stringConsumer = (Consumer<Boolean>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.DOUBLE) {
                    Boolean value = XTablesByteUtils.toBoolean(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.COORDINATES)) {
            @SuppressWarnings("unchecked") Consumer<List<XTableValues.Coordinate>> stringConsumer = (Consumer<List<XTableValues.Coordinate>>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.COORDINATES) {
                    List<XTableValues.Coordinate> value = XTablesByteUtils.unpack_coordinates_list(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.POSE2D)) {
            @SuppressWarnings("unchecked") Consumer<Pose2d> stringConsumer = (Consumer<Pose2d>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.POSE2D) {
                    Pose2d value = XTablesByteUtils.unpackPose2d(update.getValue().toByteArray());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.POSE3D)) {
            @SuppressWarnings("unchecked") Consumer<Pose3d> stringConsumer = (Consumer<Pose3d>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.POSE3D) {
                    Pose3d value = XTablesByteUtils.unpackPose3d(update.getValue().toByteArray());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.STRING_LIST)) {
            @SuppressWarnings("unchecked") Consumer<List<String>> stringConsumer = (Consumer<List<String>>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.STRING_LIST) {
                    List<String> value = XTablesByteUtils.unpack_string_list(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.DOUBLE_LIST)) {
            @SuppressWarnings("unchecked") Consumer<List<Double>> stringConsumer = (Consumer<List<Double>>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.DOUBLE_LIST) {
                    List<Double> value = XTablesByteUtils.unpack_double_list(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.FLOAT_LIST)) {
            @SuppressWarnings("unchecked") Consumer<List<Float>> stringConsumer = (Consumer<List<Float>>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.FLOAT_LIST) {
                    List<Float> value = XTablesByteUtils.unpack_float_list(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.INTEGER_LIST)) {
            @SuppressWarnings("unchecked") Consumer<List<Integer>> stringConsumer = (Consumer<List<Integer>>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.INTEGER_LIST) {
                    List<Integer> value = XTablesByteUtils.unpack_integer_list(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.LONG_LIST)) {
            @SuppressWarnings("unchecked") Consumer<List<Long>> stringConsumer = (Consumer<List<Long>>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.LONG_LIST) {
                    List<Long> value = XTablesByteUtils.unpack_long_list(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.BOOLEAN_LIST)) {
            @SuppressWarnings("unchecked") Consumer<List<Boolean>> stringConsumer = (Consumer<List<Boolean>>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.BOOLEAN_LIST) {
                    List<Boolean> value = XTablesByteUtils.unpack_boolean_list(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else if (type.equals(XTableProto.XTableMessage.Type.BYTES_LIST)) {
            @SuppressWarnings("unchecked") Consumer<List<ByteString>> stringConsumer = (Consumer<List<ByteString>>) consumer;

            checkConsumer = (update) -> {
                if (update.getType() == XTableProto.XTableMessage.Type.BYTES_LIST) {
                    List<ByteString> value = XTablesByteUtils.unpack_bytes_list(update.getValue());
                    if (value == null) return;
                    stringConsumer.accept(value);
                }
            };
        } else {
            throw new XTablesException("This type of update is not supported");
        }


        return this.subscribe(key, checkConsumer);
    }
}
