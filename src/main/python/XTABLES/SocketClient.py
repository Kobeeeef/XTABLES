import logging
import socket
import threading
import uuid
import traceback
from typing import Optional
import time
import ClientStatistics
import CircularBuffer
import struct
import XTableProto_pb2 as XTableProto
import random


# def _dedupe_buffer_key_func(event: Optional[list[str]]) -> Optional[str]:
#     if event is None or len(event) < 3:
#         return None
#     event_parts = event[0].strip(":")
#     if len(event_parts) < 2:
#         return None
#
#     return f"{event_parts[1]}_{event[1]}"


class SocketClient:
    def __init__(self, ip, port, buffer_size=100):
        self.version = "XTABLES Client v4.0.0 | Python"
        self.logger = logging.getLogger(__name__)
        self.ip = ip
        self.delimit = b'\n'
        self.debug = False
        self.port = port
        self.sock = None
        self.subscriptions = {}
        self.response_map = {}
        self.response_lock = threading.Lock()
        self.connected = False
        self.stop_threads = threading.Event()
        self.lock = threading.Lock()
        self.circular_buffer = CircularBuffer.CircularBuffer(buffer_size)

    def add_version_property(self, str):
        self.version = self.version + " | " + str

    def set_debug(self, bool):
        self.debug(bool is True)

    def connect(self):
        self.__connect()
        self.stop_threads.clear()
        threading.Thread(target=self._message_listener, daemon=True).start()
        threading.Thread(target=self._process_buffered_messages, daemon=True).start()

    def shutdown(self):
        self.stop_threads.set()
        with self.lock:
            self.connected = False
            if self.sock:
                try:
                    self.sock.shutdown(socket.SHUT_RDWR)
                except:
                    if self.debug:
                        traceback.print_exc()
                    pass
                self.sock.close()
                self.sock = None

    def send_message(self, message):
        with self.lock:
            if self.connected:
                try:
                    # Serialize the Protobuf message to bytes
                    message_bytes = message.SerializeToString()

                    self.sock.sendall(message_bytes + self.delimit)

                except Exception as e:
                    if self.debug:
                        traceback.print_exc()
                    self.connected = False
                    self._reconnect()

    def __connect(self):
        try:
            self.logger.info(f"Connecting to {self.ip}:{self.port}...")
            if self.sock:
                self.sock.close()
            self.sock = socket.create_connection((self.ip, self.port))
            self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, True)
            self.connected = True
            self.logger.info(f"Connected to {self.ip}:{self.port}...")
        except Exception as e:
            if self.debug:
                traceback.print_exc()
            self.connected = False
            self.logger.fatal(f"Connection failed: " + str(e))

    def _reconnect(self):
        while not self.connected and not self.stop_threads.is_set():
            try:
                self.__connect()
                self._resubscribe_all()
            except:
                if self.debug:
                    traceback.print_exc()
                time.sleep(0.1)
                pass

    def _resubscribe_all(self):
        """
        Re-subscribes to all previously subscribed keys after a reconnection.
        """
        for key, consumer in self.subscriptions.items():
            self.logger.info("Attempting re-subscription to key: " + key)
            if key == "":
                self.subscribeForAllUpdates(consumer)
            else:
                self.subscribeForUpdates(key, consumer)

    def subscribeForUpdates(self, key, consumer):
        """
        Sends a SUBSCRIBE_UPDATE command for the given key and registers the consumer
        to handle update events. Allows multiple consumers to subscribe to the same key.
        Returns True if the subscription was successful, and False otherwise.
        """
        try:
            # Send SUBSCRIBE_UPDATE command
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.SUBSCRIBE_UPDATE
            message.key = key
            response = self.send_request_and_retrieve(message)
            if response == bytes([0x01]):  # 1 is success
                self.logger.info(f"Subscribed to updates for key: {key}")

                # Add the consumer to the subscriptions map (as a list of consumers)
                if key not in self.subscriptions:
                    self.subscriptions[key] = []  # Initialize list if not present

                self.subscriptions[key].append(consumer)  # Add consumer to list
                return True
            else:
                self.logger.error(f"Failed to subscribe to updates for key: {key}")
                return False
        except Exception as e:
            self.logger.error(f"Error subscribing to updates for key {key}: {e}")
            return False

    def subscribeForAllUpdates(self, consumer):
        """
        Sends a SUBSCRIBE_UPDATE command for the given key and registers the consumer
        to handle update events. Allows multiple consumers to subscribe to the same key.
        Returns True if the subscription was successful, and False otherwise.
        """
        try:
            # Send SUBSCRIBE_UPDATE command
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.SUBSCRIBE_UPDATE
            response = self.send_request_and_retrieve(message)
            if response == bytes([0x01]):
                self.logger.info(f"Subscribed to all updates.")
                # Add the consumer to the subscriptions map (as a list of consumers)
                if "" not in self.subscriptions:
                    self.subscriptions[""] = []  # Initialize list if not present
                self.subscriptions[""].append(consumer)  # Add consumer to list
                return True
            else:
                self.logger.error(f"Failed to subscribe to all updates.")
                return False
        except Exception as e:
            self.logger.error(f"Error subscribing to all updates: {e}")
            return False

    def send_request_and_retrieve(self, message, TIMEOUT=3000):
        """
        Generalized method to send a request to the server and retrieve the response.

        :param message: The message in protobuf.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The response value or None if the request times out.
        """
        message.id = random.getrandbits(64) & 0x7FFFFFFFFFFFFFFF
        response_event = threading.Event()
        with self.response_lock:
            self.response_map[message.id] = {
                'event': response_event,
                'value': None
            }

        self.send_message(message)
        if response_event.wait(TIMEOUT / 1000):
            with self.response_lock:
                value = self.response_map.pop(message.id)['value']
            return value
        else:
            with self.response_lock:
                self.response_map.pop(message.id, None)
            self.logger.error(f"Timeout waiting for response for message: {message}")
            return None

    def _process_update(self, message):
        try:
            key = message.key
            if "" in self.subscriptions:
                consumers = self.subscriptions[""]
                for consumer in consumers:
                    consumer(message)
            if key in self.subscriptions:
                consumers = self.subscriptions[key]
                for consumer in consumers:
                    consumer(message)
        except Exception as e:
            self.logger.error(f"Invalid message format: {' '.join(parts)}. Error: {e}")
            if self.debug:
                traceback.print_exc()

    def _process_buffered_messages(self):
        while not self.stop_threads.is_set():
            with self.circular_buffer.condition:
                while self.circular_buffer.count == 0:
                    self.circular_buffer.condition.wait()  # Wait until notified
                message = self.circular_buffer.read_latest()
            if message is not None:
                self._process_update(message)

    def _find_delimiter(self, buffer, delimiter=b'\n'):
        """Finds the index of the first delimiter in the buffer."""
        try:
            return buffer.index(delimiter)
        except ValueError:
            return -1

    def _message_listener(self):
        buffer = bytearray()  # Using bytearray for mutable byte storage
        delimiter = b'\n'  # Example delimiter
        while not self.stop_threads.is_set():
            if not self.connected:
                self._reconnect()
            try:
                data = self.sock.recv(4096)
                if not data:
                    self.connected = False
                    self._reconnect()
                    continue

                buffer.extend(data)

                while True:
                    delimiter_index = self._find_delimiter(buffer, delimiter)

                    if delimiter_index != -1:
                        message_bytes = buffer[:delimiter_index]
                        buffer = buffer[delimiter_index + 1:]

                        try:
                            message = XTableProto.XTableMessage.FromString(message_bytes)
                            if message.HasField('id'):
                                request_id = message.id
                                with self.response_lock:
                                    if request_id in self.response_map:
                                        self.response_map[request_id]['value'] = message.value if message.HasField('value') else None
                                        self.response_map[request_id]['event'].set()

                            if message.HasField('command'):
                                if message.command == XTableProto.XTableMessage.Command.INFORMATION:
                                    response = XTableProto.XTableMessage()
                                    response.command = XTableProto.XTableMessage.Command.INFORMATION
                                    response.value = ClientStatistics.ClientStatistics(self.version).to_json().encode(
                                        "UTF-8")
                                    self.send_message(response)
                                elif message.command == XTableProto.XTableMessage.Command.UPDATE_EVENT:
                                    pass
                                    self.circular_buffer.write(message)
                        except Exception:
                            if self.debug:
                                traceback.print_exc()
                            self.logger.error(f"Failed to decode Protobuf message: {message_bytes}")
                    else:

                        if len(buffer) > 4096:  # Prevent infinite growth in case of missing delimiter
                            self.logger.warning("Buffer full with no delimiter found. Clearing buffer.")
                            buffer.clear()
                        break
            except Exception:
                if self.debug:
                    traceback.print_exc()
                self.connected = False
                self.logger.info("Reconnecting due to an error in the message listener.")
                self._reconnect()
