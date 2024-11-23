from threading import Condition


class CircularBuffer:
    def __init__(self, size):
        self.size = size
        self.buffer = [""] * size
        self.head = 0
        self.tail = 0
        self.count = 0
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
            latest_data = self.buffer[latest_index]
            self.tail = self.head  # Clear all old data
            self.count = 0
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
