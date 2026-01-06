import socket
import threading
import asyncio
import time
import cv2
import numpy as np
from numpy import ndarray
from zeroconf import Zeroconf
try:
    # Package-level imports
    from . import XDashDebugger_pb2 as XDashDebuggerProto
except ImportError:
    # Standalone script imports
    import XDashDebugger_pb2 as XDashDebuggerProto


class XDashDebugger:
    _ip_cache = {}
    _quality_cache = {}

    def __init__(self, hostname: str = "XDASH.local", port: int = 57341, best_byte_size: int = 54000):
        self.hostname = hostname
        self.port = port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 9999999)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 9999999)
        self._resolved_ip = None
        self._resolve_thread = None
        self.best_byte_size = best_byte_size
        self._resolving = False
        self._ensure_ip_resolved()
        self._loop = asyncio.new_event_loop()
        self._last_task = None  # Track last encoding task
        threading.Thread(target=self._run_event_loop, daemon=True).start()

    def _run_event_loop(self):
        """Runs the asyncio event loop in a separate thread."""
        asyncio.set_event_loop(self._loop)
        self._loop.run_forever()

    def _resolve_ip(self):
        """Resolve the hostname to an IP and cache it."""
        if self._resolving:
            return
        self._resolving = True

        try:
            print(f"XDASH DEBUGGER: Resolving XDASH address: {self.hostname}")
            resolved_ip = self.resolve_host_by_name(self.hostname)
            if resolved_ip is not None:
                XDashDebugger._ip_cache[self.hostname] = resolved_ip
                self._resolved_ip = resolved_ip
                print(f"XDASH DEBUGGER: Resolved XDASH IP to {resolved_ip}")
            else:
                raise Exception("XDASH DEBUGGER: Resolved XDASH IP not found")
        except socket.gaierror:
            print("XDASH DEBUGGER: Could not resolve XDASH Address. Retrying on next request.")
            pass
        except Exception as e:
            print(f"XDASH DEBUGGER: Unexpected error while resolving XDASH Address: {e}")
            pass
        finally:
            self._resolving = False

    def _ensure_ip_resolved(self):
        """Ensure hostname is resolved in a non-blocking way (Only one thread at a time)."""
        if self.hostname in XDashDebugger._ip_cache:
            self._resolved_ip = XDashDebugger._ip_cache[self.hostname]
        elif not self._resolving:  # Only start if no thread is running
            self._resolve_thread = threading.Thread(target=self._resolve_ip, daemon=True)
            self._resolve_thread.start()

    def __send(self, key: str, timestamp, type: XDashDebuggerProto.Message.Type, data: bytes):
        """Send data via UDP, using cached IP or resolving if necessary."""
        if self._resolved_ip:
            message = XDashDebuggerProto.Message()
            message.ty = type
            message.t = timestamp
            message.v = data
            message.k = key
            self.sock.sendto(message.SerializeToString(), (self._resolved_ip, self.port))
        else:
            self._ensure_ip_resolved()

    def send_log(self, key, timestamp, message: str):
        self.__send(key, timestamp, XDashDebuggerProto.Message.Type.LOG, message.encode("utf-8"))

    async def _determine_quality_and_encode(self, key: str, frame: ndarray) -> bytes:
        """Finds the best quality for the given key and encodes the frame asynchronously."""
        if key in XDashDebugger._quality_cache:
            quality = XDashDebugger._quality_cache[key]
        else:
            # Iterate from highest to lowest quality
            for quality in range(60, -10, -8):
                if quality <= 0:
                    XDashDebugger._quality_cache[key] = 1
                    print(f"XDASH DEBUGGER: Cached JPEG quality 1 for key '{key}', no smaller size found.")
                    break

                _, encoded_frame = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, quality])
                frame_bytes = encoded_frame.tobytes()

                if len(frame_bytes) < self.best_byte_size: # This is the maximum packet size for UDP, it will continue to lower quality till it matches!
                    XDashDebugger._quality_cache[key] = quality  # Cache the quality
                    print(f"XDASH DEBUGGER: Cached JPEG quality {quality} for key '{key}', frame size: {len(frame_bytes)} bytes")
                    break

        # Final encoding with the best quality
        _, encoded_frame = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, quality])
        return encoded_frame.tobytes()

    def send_frame(self, key: str, timestamp, frame: ndarray):
        """Encodes and sends the frame asynchronously without blocking the main loop."""
        if self._last_task and not self._last_task.done():
            self._last_task.cancel()
        self._last_task = asyncio.run_coroutine_threadsafe(
            self._async_send_frame(key, timestamp, frame), self._loop
        )

    async def _async_send_frame(self, key: str, timestamp, frame: ndarray):
        frame_bytes = await self._determine_quality_and_encode(key, frame)
        self.__send(key, timestamp, XDashDebuggerProto.Message.Type.IMAGE, frame_bytes)

    def resolve_host_by_name(self, hostname: str):
        zeroconf = Zeroconf()
        try:
            return socket.gethostbyname(hostname)
        except socket.gaierror:
            try:
                info = zeroconf.get_service_info("_xdash._tcp.local.", "XDashService._xdash._tcp.local.")
                if info:
                    addresses = info.parsed_addresses()
                    if addresses:
                        return addresses[0]
                    else:
                        print("XDASH DEBUGGER: Could not resolve XDASH address. No Addresses found.")
                else:
                    print("XDASH DEBUGGER: Could not resolve XDASH address. No Info Returned.")
            except Exception as e:
                print(f"XDASH DEBUGGER: Could not resolve XDASH address. Exception occured: {e}")
        finally:
            zeroconf.close()
        return None

# # Example usage:
# sender = XDashDebugger()
# cap = cv2.VideoCapture(0)
# while True:
#     ret, frame = cap.read()
#     sender.send_frame("exampleKey", time.time(), frame)


