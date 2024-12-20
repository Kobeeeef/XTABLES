import logging
import socket
import threading
import uuid
import traceback
from typing import Optional
import time
import ClientStatistics
import CircularBuffer


def _dedupe_buffer_key_func(event: Optional[list[str]]) -> Optional[str]:
    if event is None or len(event) < 3:
        return None
    event_parts = event[0].strip(":")
    if len(event_parts) < 2:
        return None

    return f"{event_parts[1]}_{event[1]}"


class SocketClient:
    def __init__(self, ip, port, buffer_size=100):
        self.version = "XTABLES Client v4.0.0 | Python"
        self.logger = logging.getLogger(__name__)
        self.ip = ip
        self.debug = False
        self.port = port
        self.sock = None
        self.subscriptions = {}
        self.response_map = {}
        self.response_lock = threading.Lock()
        self.connected = False
        self.stop_threads = threading.Event()
        self.lock = threading.Lock()
        self.circular_buffer = CircularBuffer.CircularBuffer(buffer_size,
                                                             dedupe_buffer_key=_dedupe_buffer_key_func)

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
                    self.sock.sendall((message + "\n").encode())
                except:
                    if self.debug:
                        traceback.print_exc()
                    self.connected = False
                    self._reconnect()

    def send_bytes(self, message):
        with self.lock:
            if self.connected:
                try:
                    # Ensure the message is in bytes, without encoding
                    self.sock.sendall(message + b"\n")
                except:
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
            response = self.send_request_and_retrieve("SUBSCRIBE_UPDATE", key)
            if response == "OK":
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
            response = self.send_request_and_retrieve("SUBSCRIBE_UPDATE")
            if response == "OK":
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

    def send_request_and_retrieve(self, command, value=None, TIMEOUT=3000):
        """
        Generalized method to send a request to the server and retrieve the response.

        :param command: The type of command (e.g., GET or GET_TABLES).
        :param value: The optional key for the command.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The response value or None if the request times out.
        """
        if value is not None and not isinstance(value, str):
            raise ValueError("Key must be a string.")
        request_id = str(uuid.uuid4())
        response_event = threading.Event()
        with self.response_lock:
            self.response_map[request_id] = {
                'event': response_event,
                'value': None
            }
        if value is None:
            self.send_message(f"{request_id}:{command}")
        else:
            self.send_message(f"{request_id}:{command} {value}")
        if response_event.wait(TIMEOUT / 1000):
            with self.response_lock:
                value = self.response_map.pop(request_id)['value']
            return value
        else:
            with self.response_lock:
                self.response_map.pop(request_id, None)
            self.logger.error(f"Timeout waiting for response for command: {command}, key: {value}")
            return None

    def _process_update(self, parts):
        try:
            key = parts[1]
            value = " ".join(parts[2:])

            if "" in self.subscriptions:
                consumers = self.subscriptions[""]
                for consumer in consumers:
                    consumer(key, value)
            if key in self.subscriptions:
                consumers = self.subscriptions[key]
                for consumer in consumers:
                    consumer(key, value)
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

    def _message_listener(self):
        buffer = ""
        while not self.stop_threads.is_set():
            if not self.connected:
                self._reconnect()
            try:
                data = self.sock.recv(4096).decode()
                if not data:
                    self.connected = False
                    self._reconnect()
                    continue
                buffer += data
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)

                    try:
                        line = line.strip()
                        parts = line.split(" ")
                        tokens = parts[0].split(":")
                        if len(tokens) != 2:
                            self.logger.error(f"Invalid token format in message: {line}")
                            continue
                        request_id = tokens[0].strip()
                        method_type = tokens[1].strip()
                        response_value = " ".join(parts[1:]).strip()
                        if method_type == "INFORMATION":
                            if len(parts) < 1:
                                self.logger.error(f"INFORMATION message invalid: {line}")
                                continue
                            self.send_message(
                                "null:INFORMATION " + ClientStatistics.ClientStatistics(self.version).to_json())
                            continue
                        if len(parts) < 2:
                            self.logger.error(f"Message format invalid: {line} (too few parts)")
                            continue
                        with self.response_lock:
                            if request_id in self.response_map:
                                self.response_map[request_id]['value'] = response_value
                                self.response_map[request_id]['event'].set()
                        if method_type == "UPDATE_EVENT":
                            if len(parts) < 3:
                                self.logger.error(f"UPDATE_EVENT message invalid: {line}")
                                continue
                            self.circular_buffer.write(parts)
                    except Exception as e:
                        if self.debug:
                            traceback.print_exc()
            except Exception as e:
                if self.debug:
                    traceback.print_exc()
                self.connected = False
                self.logger.info("Reconnecting due to an error in the message listener.")
                self._reconnect()
