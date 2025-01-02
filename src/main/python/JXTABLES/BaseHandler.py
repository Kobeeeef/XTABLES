import threading
import logging
import zmq


class BaseHandler(threading.Thread):
    """
    BaseHandler - A general base class for threads with common setup and cleanup functionality.

    This class provides a standardized way to handle thread initialization, resource cleanup,
    and logging for threads used in the XTABLES project.

    Author: Kobe Lei
    Version: 1.0
    """

    # Set up a logger for the class
    logger = logging.getLogger("BaseHandler")
    logging.basicConfig(level=logging.INFO)

    def __init__(self, thread_name: str, is_daemon: bool, socket: zmq.Socket):
        """
        Constructor to initialize the thread with a custom name, daemon status, and socket.

        :param thread_name: The name of the thread
        :param is_daemon: Whether the thread should run as a daemon
        :param socket: The zmq.Socket instance to manage
        """
        super().__init__(name=thread_name, daemon=is_daemon)
        self.socket = socket

    def clean_up(self):
        """
        Performs cleanup by closing the zmq.Socket.
        """
        if self.socket is not None:
            try:
                self.socket.close()
                self.logger.info("Socket closed successfully")
            except Exception as e:
                self.logger.warning(f"Exception during socket cleanup: {e}")

    def run(self):
        """
        Override this method in subclasses to define thread execution logic.
        """
        raise NotImplementedError("Subclasses must implement the run method")

    def handle_exception(self, exception: Exception):
        """
        Logs exceptions that occur during thread execution.

        :param exception: The exception that occurred
        """
        self.logger.error(f"Exception in thread {self.name}: {exception}")

    def interrupt(self):
        """
        Handles cleanup and logs exceptions when the thread is interrupted.
        """
        try:
            self.clean_up()
        except Exception as e:
            self.logger.warning(f"Exception during cleanup: {e}")
        super().join(0)  # In Python, `join(0)` can be used to exit the thread
