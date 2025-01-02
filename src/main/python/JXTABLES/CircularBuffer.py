import threading
from typing import Callable, TypeVar, Generic, Optional

T = TypeVar('T')


class CircularBuffer(Generic[T]):
    """
    A thread-safe circular buffer implementation with a customizable removal condition.

    Attributes:
        capacity (int): The maximum capacity of the buffer.
        buffer (list): Internal storage for buffer elements.
        write_index (int): Index where the next element will be written.
        size (int): Current number of elements in the buffer.
        lock (threading.Lock): Lock for thread-safe operations.
        not_empty (threading.Condition): Condition variable for waiting when the buffer is empty.
        should_remove (Callable[[T, T], bool]): Function to determine which elements should be removed.
    """

    def __init__(self, capacity: int, should_remove: Callable[[T, T], bool]):
        if capacity <= 0:
            raise ValueError("Buffer size must be greater than 0")

        self.capacity = capacity
        self.buffer = [None] * capacity
        self.write_index = 0
        self.size = 0
        self.lock = threading.Lock()
        self.not_empty = threading.Condition(self.lock)
        self.should_remove = should_remove

    def write(self, data: T):
        """
        Writes data to the buffer. Overwrites the oldest data if the buffer is full.

        Args:
            data (T): The data to write to the buffer.
        """
        with self.lock:
            self.buffer[self.write_index] = data
            self.write_index = (self.write_index + 1) % self.capacity
            if self.size < self.capacity:
                self.size += 1
            self.not_empty.notify_all()

    def read_and_block(self) -> T:
        """
        Reads and removes the oldest data from the buffer. Blocks if the buffer is empty.

        Returns:
            T: The oldest data in the buffer.
        """
        with self.not_empty:
            while self.size == 0:
                self.not_empty.wait()

            read_index = (self.write_index - self.size + self.capacity) % self.capacity
            data = self.buffer[read_index]
            self.buffer[read_index] = None
            self.size -= 1
            return data

    def read(self) -> Optional[T]:
        """
        Reads and removes the oldest data from the buffer without blocking.

        Returns:
            Optional[T]: The oldest data in the buffer, or None if the buffer is empty.
        """
        with self.lock:
            if self.size == 0:
                return None

            read_index = (self.write_index - self.size + self.capacity) % self.capacity
            data = self.buffer[read_index]
            self.buffer[read_index] = None
            self.size -= 1
            return data

    def read_latest_and_clear_on_function(self) -> T:
        """
        Reads the latest data and clears elements from the buffer based on the removal function.

        Returns:
            T: The latest data in the buffer.
        """
        with self.not_empty:
            while self.size == 0:
                self.not_empty.wait()

            latest_index = (self.write_index - 1 + self.capacity) % self.capacity
            latest_data = self.buffer[latest_index]

            new_size = 0
            current_index = (self.write_index - self.size + self.capacity) % self.capacity
            for _ in range(self.size):
                current_data = self.buffer[current_index]
                if not self.should_remove(latest_data, current_data):
                    self.buffer[new_size] = current_data
                    new_size += 1
                current_index = (current_index + 1) % self.capacity

            self.size = new_size
            self.write_index = new_size % self.capacity

            return latest_data

    def is_empty(self) -> bool:
        """
        Checks if the buffer is empty.

        Returns:
            bool: True if the buffer is empty, False otherwise.
        """
        with self.lock:
            return self.size == 0

    def get_size(self) -> int:
        """
        Gets the current size of the buffer.

        Returns:
            int: The current number of elements in the buffer.
        """
        with self.lock:
            return self.size
