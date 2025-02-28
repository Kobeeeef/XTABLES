package org.kobe.xbot.Utilities.Entities;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.70.0)",
    comments = "Source: src/main/proto/XTableValues.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class VisionCoprocessorGrpc {

  private VisionCoprocessorGrpc() {}

  public static final java.lang.String SERVICE_NAME = "org.kobe.xbot.Utilities.Entities.VisionCoprocessor";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage,
      org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves> getRequestBezierPathWithOptionsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RequestBezierPathWithOptions",
      requestType = org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage.class,
      responseType = org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage,
      org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves> getRequestBezierPathWithOptionsMethod() {
    io.grpc.MethodDescriptor<org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage, org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves> getRequestBezierPathWithOptionsMethod;
    if ((getRequestBezierPathWithOptionsMethod = VisionCoprocessorGrpc.getRequestBezierPathWithOptionsMethod) == null) {
      synchronized (VisionCoprocessorGrpc.class) {
        if ((getRequestBezierPathWithOptionsMethod = VisionCoprocessorGrpc.getRequestBezierPathWithOptionsMethod) == null) {
          VisionCoprocessorGrpc.getRequestBezierPathWithOptionsMethod = getRequestBezierPathWithOptionsMethod =
              io.grpc.MethodDescriptor.<org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage, org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RequestBezierPathWithOptions"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves.getDefaultInstance()))
              .setSchemaDescriptor(new VisionCoprocessorMethodDescriptorSupplier("RequestBezierPathWithOptions"))
              .build();
        }
      }
    }
    return getRequestBezierPathWithOptionsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static VisionCoprocessorStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VisionCoprocessorStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VisionCoprocessorStub>() {
        @java.lang.Override
        public VisionCoprocessorStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VisionCoprocessorStub(channel, callOptions);
        }
      };
    return VisionCoprocessorStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static VisionCoprocessorBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VisionCoprocessorBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VisionCoprocessorBlockingV2Stub>() {
        @java.lang.Override
        public VisionCoprocessorBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VisionCoprocessorBlockingV2Stub(channel, callOptions);
        }
      };
    return VisionCoprocessorBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static VisionCoprocessorBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VisionCoprocessorBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VisionCoprocessorBlockingStub>() {
        @java.lang.Override
        public VisionCoprocessorBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VisionCoprocessorBlockingStub(channel, callOptions);
        }
      };
    return VisionCoprocessorBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static VisionCoprocessorFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VisionCoprocessorFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VisionCoprocessorFutureStub>() {
        @java.lang.Override
        public VisionCoprocessorFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VisionCoprocessorFutureStub(channel, callOptions);
        }
      };
    return VisionCoprocessorFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void requestBezierPathWithOptions(org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage request,
        io.grpc.stub.StreamObserver<org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRequestBezierPathWithOptionsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service VisionCoprocessor.
   */
  public static abstract class VisionCoprocessorImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return VisionCoprocessorGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service VisionCoprocessor.
   */
  public static final class VisionCoprocessorStub
      extends io.grpc.stub.AbstractAsyncStub<VisionCoprocessorStub> {
    private VisionCoprocessorStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VisionCoprocessorStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VisionCoprocessorStub(channel, callOptions);
    }

    /**
     */
    public void requestBezierPathWithOptions(org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage request,
        io.grpc.stub.StreamObserver<org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRequestBezierPathWithOptionsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service VisionCoprocessor.
   */
  public static final class VisionCoprocessorBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<VisionCoprocessorBlockingV2Stub> {
    private VisionCoprocessorBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VisionCoprocessorBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VisionCoprocessorBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves requestBezierPathWithOptions(org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestBezierPathWithOptionsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service VisionCoprocessor.
   */
  public static final class VisionCoprocessorBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<VisionCoprocessorBlockingStub> {
    private VisionCoprocessorBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VisionCoprocessorBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VisionCoprocessorBlockingStub(channel, callOptions);
    }

    /**
     */
    public org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves requestBezierPathWithOptions(org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestBezierPathWithOptionsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service VisionCoprocessor.
   */
  public static final class VisionCoprocessorFutureStub
      extends io.grpc.stub.AbstractFutureStub<VisionCoprocessorFutureStub> {
    private VisionCoprocessorFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VisionCoprocessorFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VisionCoprocessorFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves> requestBezierPathWithOptions(
        org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRequestBezierPathWithOptionsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REQUEST_BEZIER_PATH_WITH_OPTIONS = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REQUEST_BEZIER_PATH_WITH_OPTIONS:
          serviceImpl.requestBezierPathWithOptions((org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage) request,
              (io.grpc.stub.StreamObserver<org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRequestBezierPathWithOptionsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.kobe.xbot.Utilities.Entities.XTableValues.RequestVisionCoprocessorMessage,
              org.kobe.xbot.Utilities.Entities.XTableValues.BezierCurves>(
                service, METHODID_REQUEST_BEZIER_PATH_WITH_OPTIONS)))
        .build();
  }

  private static abstract class VisionCoprocessorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    VisionCoprocessorBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return org.kobe.xbot.Utilities.Entities.XTableValues.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("VisionCoprocessor");
    }
  }

  private static final class VisionCoprocessorFileDescriptorSupplier
      extends VisionCoprocessorBaseDescriptorSupplier {
    VisionCoprocessorFileDescriptorSupplier() {}
  }

  private static final class VisionCoprocessorMethodDescriptorSupplier
      extends VisionCoprocessorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    VisionCoprocessorMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (VisionCoprocessorGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new VisionCoprocessorFileDescriptorSupplier())
              .addMethod(getRequestBezierPathWithOptionsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
