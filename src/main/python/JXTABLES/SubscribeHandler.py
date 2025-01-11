import threading
from typing import Callable, List, Dict, Any
import zmq
import traceback
from google.protobuf.message import DecodeError

try:
    # Package-level imports
    from .BaseHandler import BaseHandler
    from .ClientStatistics import ClientStatistics
    from .CircularBuffer import CircularBuffer
    from . import Utilities
    from . import XTableProto_pb2 as XTableProto
except ImportError:
    # Standalone script imports
    from BaseHandler import BaseHandler
    from ClientStatistics import ClientStatistics
    from CircularBuffer import CircularBuffer
    import Utilities
    import XTableProto_pb2 as XTableProto


class SubscribeHandler(BaseHandler):
    """
    A handler for processing incoming subscription messages using ZeroMQ.
    """

    def __init__(self, socket: zmq.Socket, instance: Any):
        super().__init__("XTABLES-SUBSCRIBE-HANDLER-DAEMON", True, socket)
        self._stop = False
        self.BUFFER_SIZE = instance.BUFFER_SIZE
        self.instance = instance
        self.buffer = CircularBuffer(self.BUFFER_SIZE, lambda latest, current: latest.key == current.key)
        self.consumer_handling_thread = self.ConsumerHandlingThread(self)
        self.consumer_handling_thread.start()

    def run(self):
        """
        Main loop for handling incoming messages.
        """
        try:
            while not self._stop:
                try:
                    bytes_message = self.socket.recv()
                    message = XTableProto.XTableMessage.XTableUpdate.FromString(bytes_message)

                    if message.category in {XTableProto.XTableMessage.XTableUpdate.Category.INFORMATION,
                                            XTableProto.XTableMessage.XTableUpdate.Category.REGISTRY}:
                        if not self.instance.ghost:
                            stats = ClientStatistics()
                            stats.set_buffer_size(self.buffer.size)
                            stats.set_uuid(self.instance.uuid)
                            stats.set_version(self.instance.get_version())
                            stats.set_max_buffer_size(self.BUFFER_SIZE)
                            info_bytes = stats.to_protobuf()
                            response = XTableProto.XTableMessage(
                                id=message.value,
                                value=info_bytes.SerializeToString(),
                                command=XTableProto.XTableMessage.Command.INFORMATION
                                if message.category == XTableProto.XTableMessage.XTableUpdate.Category.INFORMATION
                                else XTableProto.XTableMessage.Command.REGISTRY,
                            )
                            self.instance.registry_socket.send(response.SerializeToString(), zmq.DONTWAIT)
                    else:
                        self.buffer.write(message)

                except DecodeError:
                    if self.instance.debug:
                        traceback.print_exc()
                except Exception as e:
                    if self.instance.debug:
                        traceback.print_exc()
                    self.handle_exception(e)
        except Exception as e:
            if self.instance.debug:
                traceback.print_exc()
            self.handle_exception(e)

    def interrupt(self):
        """
        Handles cleanup and interrupts the consumer handling thread.
        """
        self._stop = True
        super().interrupt()

    class ConsumerHandlingThread(threading.Thread):
        """
        A thread for handling subscription consumers for each received update.
        """

        def __init__(self, parent_handler: 'SubscribeHandler'):
            super().__init__(name="XTABLES-CONSUMER-HANDLER-DAEMON", daemon=True)
            self.parent_handler = parent_handler

        def run(self):
            """
            Continuously processes updates from the buffer and invokes subscribed consumers.
            """
            try:
                while not self.parent_handler._stop:
                    try:
                        update = self.parent_handler.buffer.read_latest_and_clear_on_function()

                        if update.category in {XTableProto.XTableMessage.XTableUpdate.Category.UPDATE,
                                               XTableProto.XTableMessage.XTableUpdate.Category.PUBLISH}:
                            self._process_consumers(update)
                        elif update.category == XTableProto.XTableMessage.XTableUpdate.Category.LOG:
                            self._process_log_consumers(update)

                    except Exception as e:
                        if self.parent_handler.instance.debug:
                            traceback.print_exc()
                        self.parent_handler.handle_exception(e)
            except Exception as e:
                if self.parent_handler.instance.debug:
                    traceback.print_exc()
                self.parent_handler.handle_exception(e)

        def _process_consumers(self, update):
            """
            Process consumers for UPDATE and PUBLISH categories.
            """
            if update.key in self.parent_handler.instance.subscription_consumers:
                consumers = self.parent_handler.instance.subscription_consumers[update.key]
                for consumer in consumers:
                    consumer(update)

            if "" in self.parent_handler.instance.subscription_consumers:
                general_consumers = self.parent_handler.instance.subscription_consumers[""]
                for consumer in general_consumers:
                    consumer(update)

        def _process_log_consumers(self, update):
            """
            Process log consumers for LOG category.
            """
            for consumer in self.parent_handler.instance.log_consumers:
                log_message = XTableProto.XTableMessage.XTableLog.FromString(update.value)
                consumer(log_message)
