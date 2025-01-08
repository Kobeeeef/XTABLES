import zmq
import threading
from collections import defaultdict
import logging
import traceback
# Logger setup
logger = logging.getLogger('XTablesSocketMonitor')
logger.setLevel(logging.INFO)


class XTablesSocketMonitor(threading.Thread):
    # Enum representing the status of a socket during monitoring.
    class SocketStatus:
        CONNECTED = "CONNECTED"
        CONNECT_DELAYED = "CONNECT_DELAYED"
        CONNECT_RETRIED = "CONNECT_RETRIED"
        DISCONNECTED = "DISCONNECTED"
        MONITOR_STOPPED = "MONITOR_STOPPED"
        UNKNOWN = "UNKNOWN"

    def __init__(self, instance, context):
        super().__init__(daemon=True)
        self.instance = instance
        self.context = context
        self.monitor_socket_names = {}
        self.socket_statuses = defaultdict(lambda: self.SocketStatus.UNKNOWN)
        self.poller = zmq.Poller()
        self.running = True

    def add_socket(self, socket_name, socket):
        monitor_address = f"inproc://monitor-{hash(socket)}"
        socket.monitor(monitor_address, zmq.EVENT_CONNECTED | zmq.EVENT_CONNECT_DELAYED |
                       zmq.EVENT_CONNECT_RETRIED | zmq.EVENT_DISCONNECTED | zmq.EVENT_MONITOR_STOPPED)
        monitor_socket = self.context.socket(zmq.PAIR)
        monitor_socket.connect(monitor_address)

        self.monitor_socket_names[monitor_socket] = socket_name
        self.socket_statuses[socket_name] = self.SocketStatus.UNKNOWN
        self.poller.register(monitor_socket, zmq.POLLIN)
        return self

    def remove_socket_by_name(self, socket_name):
        for monitor_socket, name in list(self.monitor_socket_names.items()):
            if name == socket_name:
                self.poller.unregister(monitor_socket)
                monitor_socket.close()
                del self.monitor_socket_names[monitor_socket]
                del self.socket_statuses[socket_name]
                break
        return self

    def remove_socket_by_instance(self, socket):
        socket_name = self.monitor_socket_names.pop(socket, None)
        if socket_name:
            self.poller.unregister(socket)
            socket.close()
            del self.socket_statuses[socket_name]
        return self

    def get_status(self, socket_name):
        return self.socket_statuses.get(socket_name, self.SocketStatus.UNKNOWN)

    def get_simplified_message(self):
        status_count = defaultdict(int)
        for status in self.socket_statuses.values():
            status_count[status] += 1

        simplified_message = ""
        for status, count in status_count.items():
            if status != self.SocketStatus.UNKNOWN:
                simplified_message += f"{count}{status[0]}"
        return simplified_message

    def is_connected(self, socket_name):
        return self.get_status(socket_name) == self.SocketStatus.CONNECTED

    def handle_event(self, socket_name, event):
        try:
            event_type_bytes = event[0][:1]  # Only the first byte
            event_type = event_type_bytes[0]

            event_address = event[1].decode()
            if event_type == zmq.EVENT_CONNECTED:
                logger.info(f"Client socket connected: {socket_name} to {event_address}")
                self.socket_statuses[socket_name] = self.SocketStatus.CONNECTED
            elif event_type == zmq.EVENT_CONNECT_DELAYED:
                if self.instance.debug:
                    logger.warning(f"Connection delayed for socket: {socket_name}")
                self.socket_statuses[socket_name] = self.SocketStatus.CONNECT_DELAYED
            elif event_type == zmq.EVENT_CONNECT_RETRIED:
                if self.instance.debug:
                    logger.info(f"Connection retried for socket: {socket_name}")
                self.socket_statuses[socket_name] = self.SocketStatus.CONNECT_RETRIED
            elif event_type == 0:
                logger.error(f"Client socket disconnected: {socket_name} from {event_address}")
                self.socket_statuses[socket_name] = self.SocketStatus.DISCONNECTED
            elif event_type == zmq.EVENT_MONITOR_STOPPED:
                logger.critical(f"Monitor stopped for socket: {socket_name}")
                self.socket_statuses[socket_name] = self.SocketStatus.MONITOR_STOPPED
            else:
                logger.error(f"Unhandled event: {event_type} on socket: {socket_name}")
                self.socket_statuses[socket_name] = self.SocketStatus.UNKNOWN
        except Exception as e:
            if self.instance.debug:
                traceback.print_exc()
            logger.error("Exception inside of handling socket event: " + e)

    def run(self):
        try:
            while self.running:
                events = dict(self.poller.poll(timeout=1000))
                for monitor_socket in events.keys():
                    event = monitor_socket.recv_multipart()
                    if event:
                        socket_name = self.monitor_socket_names.get(monitor_socket)
                        self.handle_event(socket_name, event)
        except Exception as e:
            if self.instance.debug:
                traceback.print_exc()
        finally:
            self.cleanup()

    def interrupt(self):
        self.running = False
        self.cleanup()

    def cleanup(self):
        for monitor_socket in self.monitor_socket_names:
            self.poller.unregister(monitor_socket)
            monitor_socket.close()
        self.monitor_socket_names.clear()
        self.socket_statuses.clear()
