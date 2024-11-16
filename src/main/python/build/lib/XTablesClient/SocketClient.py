import socket
import threading
import ClientStatistics as ClientStats
import logging
import uuid


class SocketClient:
    def __init__(self, ip, port):
        self.version = "XTABLES Client v4.0.0 | Python"
        self.logger = logging.getLogger(__name__)
        self.ip = ip
        self.port = port
        self.sock = None
        self.subscriptions = {}
        self.response_map = {}
        self.logger = logging.getLogger(__name__)
        self.response_lock = threading.Lock()
        self.connected = False
        self.lock = threading.Lock()
        self.stop_threads = threading.Event()

    def connect(self):
        self._connect()
        self.stop_threads.clear()
        threading.Thread(target=self._message_listener, daemon=True).start()

    def shutdown(self):
        self.stop_threads.set()
        with self.lock:
            self.connected = False
            if self.sock:
                try:
                    self.sock.shutdown(socket.SHUT_RDWR)
                except:
                    pass
                self.sock.close()
                self.sock = None

    def send_message(self, message):
        with self.lock:
            if self.connected:
                try:
                    self.sock.sendall((message + "\n").encode())

                except:
                    self.connected = False
                    self._reconnect()

    def _connect(self):
        try:
            self.logger.info(f"Connecting to {self.ip}:{self.port}...")
            self.sock = socket.create_connection((self.ip, self.port))
            self.connected = True
            self.logger.info(f"Connected to {self.ip}:{self.port}...")
        except Exception as e:
            self.connected = False
            self.logger.fatal(f"Connection failed: " + str(e))

    def _reconnect(self):
        while not self.connected and not self.stop_threads.is_set():
            try:
                self._connect()
            except:
                pass

    def subscribeForUpdates(self, key, consumer):
        """
        Sends a SUBSCRIBE_UPDATE command for the given key and registers the consumer
        to handle update events. Allows multiple consumers to subscribe to the same key.
        Returns True if the subscription was successful, and False otherwise.
        """
        try:
            # Send SUBSCRIBE_UPDATE command
            response = self.getData("SUBSCRIBE_UPDATE", key)
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

    def _process_message(self, message):
        try:
            parts = message.split(" ")
            tokens = parts[0].split(":")
            if len(tokens) != 2:
                self.logger.error(f"Invalid token format in message: {message}")
                return
            request_id = tokens[0].strip()
            method_type = tokens[1].strip()
            response_value = " ".join(parts[1:]).strip()
            if method_type == "INFORMATION":
                if len(parts) < 1:
                    self.logger.error(f"INFORMATION message invalid: {message}")
                    return
                self.send_message("null:INFORMATION " + ClientStats.ClientStatistics(self.version).to_json())
                return
            if len(parts) < 2:
                self.logger.error(f"Message format invalid: {message} (too few parts)")
                return
            if method_type == "UPDATE_EVENT":
                if len(parts) < 3:
                    self.logger.error(f"UPDATE_EVENT message invalid: {message}")
                    return
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
            with self.response_lock:
                if request_id in self.response_map:
                    self.response_map[request_id]['value'] = response_value
                    self.response_map[request_id]['event'].set()
        except Exception as e:
            self.logger.error(f"Invalid message format: {message}. Error: {e}")

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
                    self._process_message(line.strip())
            except Exception as e:
                self.logger.fatal("Exception occurred in message listener: " + str(e))
                self.connected = False
                self._reconnect()
