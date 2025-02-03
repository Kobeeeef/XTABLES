import socket
import time
import traceback
import zmq
import struct
import zmq.constants
from typing import Callable, Dict, List
import uuid
import logging
from zeroconf import Zeroconf

try:
    # Package-level imports
    from . import XTableProto_pb2 as XTableProto
    from . import XTableValues_pb2 as XTableValues
    from .SubscribeHandler import SubscribeHandler
    from .PingResponse import PingResponse
    from .XTablesByteUtils import XTablesByteUtils
    from . import TempConnectionManager as tcm
    from .XTablesSocketMonitor import XTablesSocketMonitor
except ImportError:
    # Standalone script imports
    import XTableProto_pb2 as XTableProto
    import XTableValues_pb2 as XTableValues
    from TempConnectionManager import TempConnectionManager as tcm
    from SubscribeHandler import SubscribeHandler
    from PingResponse import PingResponse
    from XTablesByteUtils import XTablesByteUtils
    from XTablesSocketMonitor import XTablesSocketMonitor


class XTablesClient:
    # ================================================================
    # Static Variables
    # ================================================================
    SUCCESS_BYTE = bytes([0x01])
    FAIL_BYTE = bytes([0x00])
    # ================================================================
    # Instance Variables
    # ================================================================
    XTABLES_CLIENT_VERSION = "XTABLES JPython Client v5.2.8 | Build Date: 2/2/2025"

    def __init__(self, ip=None, push_port=48800, req_port=48801, sub_port=48802, buffer_size=500, debug_mode=True,
                 ghost=False):
        self.debug = debug_mode
        self.BUFFER_SIZE = buffer_size
        self.context = zmq.Context(3)
        self.push_socket = self.context.socket(zmq.PUSH)
        self.push_socket.setsockopt(zmq.RECONNECT_IVL, 1000)
        self.push_socket.setsockopt(zmq.RECONNECT_IVL_MAX, 1000)
        self.ghost = ghost
        if not ghost:
            self.registry_socket = self.context.socket(zmq.PUSH)
            self.registry_socket.setsockopt(zmq.RECONNECT_IVL, 1000)
            self.registry_socket.setsockopt(zmq.RECONNECT_IVL_MAX, 5000)
        self.req_socket = self.context.socket(zmq.REQ)
        self.req_socket.set_hwm(500)
        self.req_socket.setsockopt(zmq.RCVTIMEO, 3000)
        self.sub_socket = self.context.socket(zmq.SUB)
        self.sub_socket.setsockopt(zmq.RECONNECT_IVL, 1000)
        self.sub_socket.setsockopt(zmq.RECONNECT_IVL_MAX, 1000)
        self.sub_socket.set_hwm(500)
        self.subscribe_messages_count = 0
        self.subscription_consumers = {}
        self.uuid = str(uuid.uuid4())
        self.ip = ip
        self.logger = logging.getLogger(__name__)
        self.push_port = push_port
        self.req_port = req_port
        self.sub_port = sub_port
        if self.ip is None:
            self.ip = self.resolve_host_by_name()

        if self.ip is None:
            raise XTablesServerNotFound("Could not resolve XTABLES hostname server.")

        print(f"Connecting to XTABLES Server...\n"
              f"------------------------------------------------------------\n"
              f"Server IP: {self.ip}\n"
              f"Push Socket Port: {self.push_port}\n"
              f"Request Socket Port: {self.req_port}\n"
              f"Subscribe Socket Port: {self.sub_port}\n"
              f"Web Interface: http://{self.ip}:4880/\n"
              f"Debug Mode: {'Enabled' if self.debug else 'Disabled'}\n"
              f"Ghost: {'Enabled' if ghost else 'Disabled'}\n"
              f"------------------------------------------------------------")
        self.socketMonitor = XTablesSocketMonitor(self, self.context)
        self.socketMonitor.add_socket("PUSH", self.push_socket)
        self.socketMonitor.add_socket("SUBSCRIBE", self.sub_socket)
        self.socketMonitor.add_socket("REQUEST", self.req_socket)
        if not ghost:
            self.socketMonitor.add_socket("REGISTRY", self.registry_socket)
        self.socketMonitor.start()
        self.push_socket.connect(f"tcp://{self.ip}:{self.push_port}")
        if not ghost:
            self.registry_socket.connect(f"tcp://{self.ip}:{self.push_port}")
        self.req_socket.connect(f"tcp://{self.ip}:{self.req_port}")
        self.sub_socket.connect(f"tcp://{self.ip}:{self.sub_port}")
        message = XTableProto.XTableMessage.XTableUpdate()
        message.category = XTableProto.XTableMessage.XTableUpdate.Category.REGISTRY
        bytes_registry = message.SerializeToString()
        self.sub_socket.setsockopt(zmq.SUBSCRIBE, bytes_registry)
        message.category = XTableProto.XTableMessage.XTableUpdate.Category.INFORMATION
        bytes_information = message.SerializeToString()
        self.sub_socket.setsockopt(zmq.SUBSCRIBE, bytes_information)
        self.subscribe_handler = SubscribeHandler(self.sub_socket, self)
        self.subscribe_handler.start()

    def shutdown(self):
        if self.subscribe_handler:
            self.subscribe_handler.interrupt()
        if self.socketMonitor:
            self.socketMonitor.interrupt()
        self.push_socket.close()
        self.req_socket.close()
        self.sub_socket.close()
        self.context.term()

    def _reconnect_req(self):
        self.socketMonitor.remove_socket_by_name("REQUEST")
        self.req_socket.close()
        self.req_socket = self.context.socket(zmq.REQ)
        self.req_socket.set_hwm(500)
        self.req_socket.setsockopt(zmq.REQ_RELAXED, 1)
        self.req_socket.setsockopt(zmq.TCP_KEEPALIVE, 1)
        self.req_socket.setsockopt(zmq.RECONNECT_IVL, 1000)
        self.req_socket.setsockopt(zmq.RECONNECT_IVL_MAX, 1000)
        self.req_socket.setsockopt(zmq.RCVTIMEO, 3000)
        self.socketMonitor.add_socket("REQUEST", self.req_socket)
        self.req_socket.connect(f"tcp://{self.ip}:{self.req_port}")

    def connect(self):
        """
        Attempts to reconnect the sockets to the server.
        """
        try:
            # Ensure sockets are closed before reconnecting
            self.push_socket.close()
            self.req_socket.close()
            self.sub_socket.close()

            # Create new sockets and reconnect
            self.push_socket = self.context.socket(zmq.PUSH)
            self.req_socket = self.context.socket(zmq.REQ)
            self.sub_socket = self.context.socket(zmq.SUB)

            self.req_socket.setsockopt(zmq.RCVTIMEO, 3000)
            self.sub_socket.set_hwm(500)
            self.req_socket.set_hwm(500)
            self.push_socket.connect(f"tcp://{self.ip}:{self.push_port}")
            self.req_socket.connect(f"tcp://{self.ip}:{self.req_port}")
            self.sub_socket.connect(f"tcp://{self.ip}:{self.sub_port}")

            # Re-initialize subscription
            message = XTableProto.XTableMessage.XTableUpdate()
            message.category = XTableProto.XTableMessage.XTableUpdate.Category.REGISTRY
            bytes_registry = message.SerializeToString()
            self.sub_socket.setsockopt(zmq.SUBSCRIBE, bytes_registry)
            message.category = XTableProto.XTableMessage.XTableUpdate.Category.INFORMATION
            bytes_information = message.SerializeToString()
            self.sub_socket.setsockopt(zmq.SUBSCRIBE, bytes_information)
            if self.subscribe_handler:
                self.subscribe_handler.interrupt()
            self.subscribe_handler = SubscribeHandler(self.sub_socket, self)
            self.subscribe_handler.start()
            print("Reconnected to XTABLES Server.")
        except zmq.ZMQError as e:
            print(f"Error reconnecting: {e}")
            if self.debug:
                traceback.print_exc()

    def resolve_host_by_name(self):
        temp_ip = tcm.get()
        if temp_ip:
            if self.debug:
                self.logger.info(f"Retrieved cached server IP: {temp_ip}")
            return temp_ip

        address = None
        zeroconf = Zeroconf()
        try:
            while not address:
                try:
                    if self.debug:
                        self.logger.info("Attempting to resolve host 'XTABLES.local'...")
                    address = socket.gethostbyname("XTABLES.local")
                    if self.debug:
                        self.logger.info(f"Host resolution successful: IP address found at {address}")
                        self.logger.info("Proceeding with socket client initialization.")
                except socket.gaierror:
                    if self.debug:
                        traceback.print_exc()
                        self.logger.error("Failed to resolve 'XTABLES.local'. Host not found. Now attempting "
                                          "zeroconf...")
                    try:
                        info = zeroconf.get_service_info("_xtables._tcp.local.", "XTablesService._xtables._tcp.local.")
                        if info:
                            addresses = info.parsed_addresses()
                            if addresses:
                                address = addresses[0]
                                if self.debug:
                                    self.logger.info(f"Zeroconf resolution successful: IP address found at {address}")
                            else:
                                if self.debug:
                                    self.logger.error("No valid IP address found from Zeroconf.")
                        else:
                            if self.debug:
                                self.logger.error("Failed to resolve 'XTablesService' using Zeroconf. Retrying in 1 "
                                                  "second...")
                    except Exception as e:
                        if self.debug:
                            traceback.print_exc()
                            self.logger.error(f"Error resolving service: {e}")

                    time.sleep(1)

        finally:
            zeroconf.close()

        if address:
            tcm.set(address)
            return address

        return None

    def send_push_message(self, command, key, value, msg_type):
        message = XTableProto.XTableMessage()
        message.key = key
        message.command = command
        message.value = value
        message.type = msg_type
        try:
            self.push_socket.send(message.SerializeToString(), zmq.constants.DONTWAIT)
            return True
        except zmq.ZMQError and zmq.error.Again:
            if self.debug:
                traceback.print_exc()
            return False

    def publish(self, key, value):
        message = XTableProto.XTableMessage()
        message.key = key
        message.command = XTableProto.XTableMessage.Command.PUBLISH
        message.value = value
        try:
            self.push_socket.send(message.SerializeToString(), zmq.constants.DONTWAIT)
            return True
        except zmq.ZMQError and zmq.error.Again:
            if self.debug:
                traceback.print_exc()
            return False

    def subscribe(self, key: str, consumer: Callable):
        """
        Subscribes to a specific key and associates a consumer to process updates for that key.

        :param key: The key to subscribe to.
        :param consumer: The consumer function that processes updates for the specified key.
        :return: True if the subscription and consumer addition were successful, False otherwise.
        """
        message = XTableProto.XTableMessage.XTableUpdate()
        message.key = key
        bytes_key = message.SerializeToString()
        try:
            self.sub_socket.setsockopt(zmq.SUBSCRIBE, bytes_key)
            if key not in self.subscription_consumers:
                self.subscription_consumers[key] = []
            self.subscription_consumers[key].append(consumer)
            return True
        except zmq.ZMQError as e:
            if self.debug:
                traceback.print_exc()
            print(f"Error subscribing to key {key}: {e}")
            return False

    def subscribe_all(self, consumer: Callable):
        """
        Subscribes to updates for all keys and associates a consumer to process updates for all keys.

        :param consumer: The consumer function that processes updates for any key.
        :return: True if the subscription and consumer addition were successful, False otherwise.
        """
        try:
            self.sub_socket.setsockopt(zmq.SUBSCRIBE, "".encode())
            if "" not in self.subscription_consumers:
                self.subscription_consumers[""] = []
            self.subscription_consumers[""].append(consumer)
            return True
        except zmq.ZMQError as e:
            if self.debug:
                traceback.print_exc()
            print(f"Error subscribing to all keys: {e}")
            return False

    def unsubscribe(self, key: str, consumer: Callable):
        """
        Unsubscribes a specific consumer from a given key. If no consumers remain for the key,
        it unsubscribes the key from the subscription socket.

        :param key: The key to unsubscribe from.
        :param consumer: The consumer function to remove from the key's subscription.
        :return: True if the consumer was successfully removed or the key was unsubscribed, False otherwise.
        """
        try:
            if key in self.subscription_consumers:
                consumers = self.subscription_consumers[key]
                if consumer in consumers:
                    consumers.remove(consumer)
                    if not consumers:
                        # If no consumers are left for this key, unsubscribe
                        del self.subscription_consumers[key]
                        message = XTableProto.XTableMessage.XTableUpdate()
                        message.key = key
                        bytes_key = message.SerializeToString()
                        self.sub_socket.setsockopt(zmq.UNSUBSCRIBE, bytes_key)
                    return True
            return False
        except zmq.ZMQError as e:
            if self.debug:
                traceback.print_exc()
            print(f"Error unsubscribing to key {key}: {e}")
            return False

    def unsubscribe_all(self, consumer: Callable):
        """
        Unsubscribes a specific consumer from all keys. If no consumers remain for all keys,
        it unsubscribes from the subscription socket for all keys.

        :param consumer: The consumer function to remove from all key subscriptions.
        :return: True if the consumer was successfully removed or unsubscribed from all keys, False otherwise.
        """
        try:
            if "" in self.subscription_consumers:
                consumers = self.subscription_consumers[""]
                if consumer in consumers:
                    consumers.remove(consumer)
                    if not consumers:
                        # If no consumers are left for all keys, unsubscribe from all
                        del self.subscription_consumers[""]
                        self.sub_socket.setsockopt(zmq.UNSUBSCRIBE, "".encode())
                    return True
            return False
        except zmq.ZMQError as e:
            if self.debug:
                traceback.print_exc()
            print(f"Error subscribing to all keys: {e}")
            return False

    # ====================
    # PUT Methods
    # ====================
    def putBytes(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value,
                                      XTableProto.XTableMessage.Type.BYTES)

    def putUnknownBytes(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value,
                                      XTableProto.XTableMessage.Type.UNKNOWN)

    def putString(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value.encode('utf-8'),
                                      XTableProto.XTableMessage.Type.STRING)

    def putInteger(self, key, value):
        value_bytes = struct.pack('!i', value)
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
                                      XTableProto.XTableMessage.Type.INT64)

    def putLong(self, key, value):
        value_bytes = struct.pack('!q', value)
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
                                      XTableProto.XTableMessage.Type.INT64)

    def putCoordinates(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key,
                                      XTableValues.CoordinateList(coordinates=value).SerializeToString(),
                                      XTableProto.XTableMessage.Type.BYTES)

    def putDouble(self, key, value):
        value_bytes = struct.pack('!d', value)
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
                                      XTableProto.XTableMessage.Type.DOUBLE)

    def putBoolean(self, key, value):
        value_bytes = self.SUCCESS_BYTE if value else self.FAIL_BYTE
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
                                      XTableProto.XTableMessage.Type.BOOL)

    def putDoubleList(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key,
                                      XTableValues.DoubleList(v=value).SerializeToString(),
                                      XTableProto.XTableMessage.Type.DOUBLE_LIST)

    def putStringList(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key,
                                      XTableValues.StringList(v=value).SerializeToString(),
                                      XTableProto.XTableMessage.Type.STRING_LIST)

    def putIntegerList(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key,
                                      XTableValues.IntegerList(v=value).SerializeToString(),
                                      XTableProto.XTableMessage.Type.INTEGER_LIST)

    def putBytesList(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key,
                                      XTableValues.BytesList(v=value).SerializeToString(),
                                      XTableProto.XTableMessage.Type.BYTES_LIST)

    def putLongList(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key,
                                      XTableValues.LongList(v=value).SerializeToString(),
                                      XTableProto.XTableMessage.Type.LONG_LIST)

    def putFloatList(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key,
                                      XTableValues.FloatList(v=value).SerializeToString(),
                                      XTableProto.XTableMessage.Type.FLOAT_LIST)

    def putBooleanList(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key,
                                      XTableValues.BoolList(v=value).SerializeToString(),
                                      XTableProto.XTableMessage.Type.BOOLEAN_LIST)

    # ====================
    # GET Methods
    # ====================

    def getString(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.STRING:
            return message.value.decode('utf-8')
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected STRING type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getCoordinates(self, key):
        message = self._get_xtable_message(key)
        if message is None:
            raise ValueError("No message received from the XTABLES server.")
        if not message.HasField("value"):
            return None
        if message.type == XTableProto.XTableMessage.Type.BYTES:
            try:
                return XTableValues.CoordinateList.FromString(message.value).coordinates
            except Exception:
                traceback.print_exc()
                raise ValueError(f"Invalid bytes returned from server: {message.value}")

        raise ValueError(f"Expected BYTES type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getDoubleList(self, key):
        message = self._get_xtable_message(key)
        if message is None:
            raise ValueError("No message received from the XTABLES server.")
        if not message.HasField("value"):
            return None
        if message.type == XTableProto.XTableMessage.Type.DOUBLE_LIST:
            try:
                return XTableValues.DoubleList.FromString(message.value).v
            except Exception:
                traceback.print_exc()
                raise ValueError(f"Invalid bytes returned from server: {message.value}")
        raise ValueError(f"Expected DOUBLE_LIST type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getStringList(self, key):
        message = self._get_xtable_message(key)
        if message is None:
            raise ValueError("No message received from the XTABLES server.")
        if not message.HasField("value"):
            return None
        if message.type == XTableProto.XTableMessage.Type.STRING_LIST:
            try:
                return XTableValues.StringList.FromString(message.value).v
            except Exception:
                traceback.print_exc()
                raise ValueError(f"Invalid bytes returned from server: {message.value}")
        raise ValueError(f"Expected STRING_LIST type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getIntegerList(self, key):
        message = self._get_xtable_message(key)
        if message is None:
            raise ValueError("No message received from the XTABLES server.")
        if not message.HasField("value"):
            return None
        if message.type == XTableProto.XTableMessage.Type.INTEGER_LIST:
            try:
                return XTableValues.IntegerList.FromString(message.value).v
            except Exception:
                traceback.print_exc()
                raise ValueError(f"Invalid bytes returned from server: {message.value}")
        raise ValueError(f"Expected INTEGER_LIST type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getBytesList(self, key):
        message = self._get_xtable_message(key)
        if message is None:
            raise ValueError("No message received from the XTABLES server.")
        if not message.HasField("value"):
            return None
        if message.type == XTableProto.XTableMessage.Type.BYTES_LIST:
            try:
                return XTableValues.BytesList.FromString(message.value).v
            except Exception:
                traceback.print_exc()
                raise ValueError(f"Invalid bytes returned from server: {message.value}")
        raise ValueError(f"Expected BYTES_LIST type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getLongList(self, key):
        message = self._get_xtable_message(key)
        if message is None:
            raise ValueError("No message received from the XTABLES server.")
        if not message.HasField("value"):
            return None
        if message.type == XTableProto.XTableMessage.Type.LONG_LIST:
            try:
                return XTableValues.LongList.FromString(message.value).v
            except Exception:
                traceback.print_exc()
                raise ValueError(f"Invalid bytes returned from server: {message.value}")
        raise ValueError(f"Expected LONG_LIST type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getFloatList(self, key):
        message = self._get_xtable_message(key)
        if message is None:
            raise ValueError("No message received from the XTABLES server.")
        if not message.HasField("value"):
            return None
        if message.type == XTableProto.XTableMessage.Type.FLOAT_LIST:
            try:
                return XTableValues.FloatList.FromString(message.value).v
            except Exception:
                traceback.print_exc()
                raise ValueError(f"Invalid bytes returned from server: {message.value}")
        raise ValueError(f"Expected FLOAT_LIST type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getBooleanList(self, key):
        message = self._get_xtable_message(key)
        if message is None:
            raise ValueError("No message received from the XTABLES server.")
        if not message.HasField("value"):
            return None
        if message.type == XTableProto.XTableMessage.Type.BOOLEAN_LIST:
            try:
                return XTableValues.BoolList.FromString(message.value).v
            except Exception:
                traceback.print_exc()
                raise ValueError(f"Invalid bytes returned from server: {message.value}")
        raise ValueError(f"Expected BOOLEAN_LIST type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getInteger(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.INT64:
            return struct.unpack('!i', message.value)[0]
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected INT type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getBoolean(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.BOOL:
            return message.value == self.SUCCESS_BYTE
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected BOOL type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getLong(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.INT64:
            return struct.unpack('!q', message.value)[0]
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected LONG type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getDouble(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.DOUBLE:
            return struct.unpack('!d', message.value)[0]
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected DOUBLE type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getBytes(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.BYTES:
            return message.value
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected BYTES type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getUnknownBytes(self, key):
        message = self._get_xtable_message(key)
        if message is not None:
            return message.value
        else:
            raise ValueError(f"Expected BYTES type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def _get_xtable_message(self, key):
        try:
            message = XTableProto.XTableMessage()
            message.key = key
            message.command = XTableProto.XTableMessage.Command.GET
            self.req_socket.send(message.SerializeToString(), zmq.constants.DONTWAIT)
            response_bytes = self.req_socket.recv()
            response_message = XTableProto.XTableMessage.FromString(response_bytes)

            return response_message
        except zmq.error.ZMQError:
            if self.debug:
                traceback.print_exc()
            print("Exception on REQ socket. Reconnecting to clear states.")
            self._reconnect_req()
            return None
        except Exception:
            if self.debug:
                traceback.print_exc()
            return None

    def ping(self):
        try:
            start_time = time.perf_counter_ns()
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PING
            self.req_socket.send(message.SerializeToString(), zmq.constants.DONTWAIT)

            response_bytes = self.req_socket.recv()
            round_trip_time = time.perf_counter_ns() - start_time

            if not response_bytes:
                return PingResponse(False, -1)

            response_message = XTableProto.XTableMessage.FromString(response_bytes)

            if response_message.HasField("value"):
                success = response_message.value == self.SUCCESS_BYTE
                return PingResponse(success, round_trip_time)
            else:
                return PingResponse(False, -1)
        except zmq.error.ZMQError:
            if self.debug:
                traceback.print_exc()
            print("Exception on REQ socket. Reconnecting to clear states.")
            self._reconnect_req()
            return PingResponse(False, -1)
        except Exception:
            if self.debug:
                traceback.print_exc()
            return PingResponse(False, -1)

    def set_server_debug(self, value):
        try:
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.DEBUG
            message.value = self.SUCCESS_BYTE if value else self.FAIL_BYTE
            self.req_socket.send(message.SerializeToString(), zmq.constants.DONTWAIT)

            response_bytes = self.req_socket.recv()
            if not response_bytes:
                return False

            response_message = XTableProto.XTableMessage.FromString(response_bytes)

            if response_message.HasField("value"):
                return response_message.value == self.SUCCESS_BYTE
            else:
                return False
        except zmq.error.ZMQError:
            if self.debug:
                traceback.print_exc()
            print("Exception on REQ socket. Reconnecting to clear states.")
            self._reconnect_req()
            return False
        except Exception:
            if self.debug:
                traceback.print_exc()
            return False

    def getTables(self, key=None):
        try:
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.GET_TABLES
            if key is not None:
                message.key = key
            self.req_socket.send(message.SerializeToString(), zmq.constants.DONTWAIT)

            response_bytes = self.req_socket.recv()
            if not response_bytes:
                return False

            response_message = XTableProto.XTableMessage.FromString(response_bytes)

            if response_message.HasField("value"):
                return XTableValues.StringList.FromString(response_message.value).v
            else:
                return []
        except zmq.error.ZMQError:
            if self.debug:
                traceback.print_exc()
            print("Exception on REQ socket. Reconnecting to clear states.")
            self._reconnect_req()
            return []
        except Exception:
            if self.debug:
                traceback.print_exc()
            return []

    # ====================
    # Version and Properties Methods
    # ====================
    def get_version(self):
        return self.XTABLES_CLIENT_VERSION

    def add_client_version_property(self, value):
        self.XTABLES_CLIENT_VERSION = self.XTABLES_CLIENT_VERSION + " | " + value

    def set_client_debug(self, value):
        self.debug = value


class XTablesServerNotFound(Exception):
    pass


# # def consumer(test):
# #     print("UPDATE: " + test.key + " " + str(
# #         XTablesByteUtils.to_int(test.value)) + " TYPE: " + XTableProto.XTableMessage.Type.Name(test.type))
#
#
# if __name__ == "__main__":
#     client = XTablesClient(debug_mode=False)
#     # client.subscribe_all(consumer)
#     # coordinates = [XTableValues.Coordinate(x=1, y=2), XTableValues.Coordinate(x=3, y=4)]
#     #
#     # client.putCoordinates("test", coordinates)
#     #
#     # print(client.getCoordinates("test")[0].x)
#
#     client.putIntegerList("test", [1])
#
#     print(client.getStringList("test"))
#     time.sleep(100000)
#
#     # print(client.getUnknownBytes("name"))
#     # print(client.getString("age"))
#     # print(client.getArray("numbers"))
