import socket
import json
import threading
import logging
from concurrent.futures import ThreadPoolExecutor, Future
from time import sleep

logger = logging.getLogger('SocketClient')
logging.basicConfig(level=logging.INFO)


class SocketClient:
    def __init__(self, server_address, server_port, reconnect_delay_ms):
        self.server_address = server_address
        self.server_port = server_port
        self.reconnect_delay_ms = reconnect_delay_ms / 1000.0
        self.socket = None
        self.out = None
        self.inp = None
        self.executor = ThreadPoolExecutor(1)

    def connect(self):
        while True:
            try:
                logger.info(f"Connecting to server: {self.server_address}:{self.server_port}")
                self.socket = socket.create_connection((self.server_address, self.server_port))
                self.out = self.socket.makefile('w')
                self.inp = self.socket.makefile('r')
                logger.info(f"Connected to server: {self.server_address}:{self.server_port}")
                self.executor.submit(self.auto_reconnect)
                break
            except IOError as e:
                logger.warning("Failed to connect to server. Retrying...")
                sleep(self.reconnect_delay_ms)

    def auto_reconnect(self):
        initial_delay = 1
        max_delay = 120
        delay = initial_delay

        while True:
            if not self.is_connected():
                logger.warning("Disconnected from the server. Reconnecting...")
                self.connect()
                delay = min(delay * 2, max_delay)
            else:
                delay = initial_delay
            sleep(delay)

    def is_connected(self):
        try:
            self.out.write("PING\n")
            self.out.flush()
            response = self.inp.readline().strip()
            return response == "ACTIVE"
        except IOError:
            return False

    def send_async(self, message, response_type=None):
        future = Future()
        threading.Thread(target=self._send, args=(message, future, response_type)).start()
        return future

    def _send(self, message, future, response_type=None):
        try:
            self.out.write(message + "\n")
            self.out.flush()
            response = self.inp.readline().strip()
            if response_type:
                if response_type == int:
                    response = int(response)
                elif response_type == float:
                    response = float(response)
                else:
                    response = json.loads(response)
            future.set_result(response)
        except Exception as e:
            future.set_exception(e)

    def send_complete(self, message, response_type=None):
        response = self.send_async(message).result(timeout=5)
        return json.loads(response) if response_type else response
