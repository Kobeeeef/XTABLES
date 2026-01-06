import grpc
from concurrent import futures
import XTableValues_pb2_grpc as XTableGRPC
import XTableValues_pb2 as XTableValues

class VisionCoprocessorServicer(XTableGRPC.VisionCoprocessorServicer):
    def RequestBezierPathWithOptions(self, request, context):
        print(f"Received request with option: {request}")

        # Simulate response
        response = XTableValues.BezierCurves()
        curves = response.curves.add()
        p = curves.controlPoints.add()
        p.x = 2
        p.y = 2
        return response


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    XTableGRPC.add_VisionCoprocessorServicer_to_server(VisionCoprocessorServicer(), server)

    server.add_insecure_port("[::]:9281")  # Listen on all interfaces
    server.start()
    print("Python gRPC Server is running on port 9281...")

    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        print("Shutting down gRPC server.")


if __name__ == "__main__":
    serve()
