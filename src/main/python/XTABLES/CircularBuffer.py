from threading import Condition
from typing import Any, Optional, Callable


class CircularBuffer:
    def __init__(self, size, dedupe_buffer_key: Optional[Callable[[Any], Optional[str]]] = None):
        self.size = size
        self.buffer = [None] * size
        self.head = 0
        self.tail = 0
        self.count = 0
        self.dedupe_buffer_key = dedupe_buffer_key
        self.condition = Condition()  # Condition for notifying consumers

    def write(self, data):
        with self.condition:
            self.buffer[self.head] = data
            self.head = (self.head + 1) % self.size
            if self.count == self.size:
                self.tail = (self.tail + 1) % self.size  # Overwrite oldest data
            else:
                self.count += 1
            self.condition.notify()  # Notify waiting threads

    def read_latest(self):
        with self.condition:
            if self.count == 0:
                return None  # Buffer is empty
            latest_index = (self.head - 1 + self.size) % self.size
            if self.buffer[latest_index] is None:
                return None
            latest_data = self.buffer[latest_index]
            if self.dedupe_buffer_key is None:
                self.tail = self.head  # Clear all old data
            else:
                self._increment_tail_and_manage_duplicates(latest_data)
            self.count = self.tail - self.head if self.tail >= self.head else (self.size - self.tail) + self.head
            return latest_data

    def read(self):
        with self.condition:
            while self.count == 0:
                self.condition.wait()
            data = self.buffer[self.tail]
            self.tail = (self.tail + 1) % self.size
            self.count -= 1
            return data

    def get_size(self):
        with self.condition:
            return self.count


    def _increment_tail_and_manage_duplicates(self, data: Any) -> None:
        """
        This will resolve what happens after the latest message is received and will handle any
        duplicates that exists in the list.

        We start by incrementing tail until not dupes our found. Next any other dupes from tail to
        head we replace with None. Finally we move back the head until either no other dupe is found
        or the tail is reached.
        """
        assert self.dedupe_buffer_key is not None
        buffer_key = self.dedupe_buffer_key(data)
        # Increment tail until the either the head is found or no more duplicates have been found.
        while (self.dedupe_buffer_key(self.buffer[self.tail]) == buffer_key and self.tail != self.head):
            self.tail = (self.tail + 1) % self.size

        if self.tail == self.head:
            return

        # Replace any other found duplicates with None between the tail and the head.
        counter = self.tail
        while (counter != self.head):
            if self.dedupe_buffer_key(self.buffer[counter]) == buffer_key:
                self.buffer[counter] = None

            counter = (counter + 1) % self.size

        self.head = (self.head - 1 + self.size) % self.size

        if self.tail == self.head:
            return

        # Move the head back until we have found either the tail or a non empty entry.
        while (self.buffer[self.head] is None and self.tail != self.head):
            self.head = (self.head - 1 + self.size) % self.size
