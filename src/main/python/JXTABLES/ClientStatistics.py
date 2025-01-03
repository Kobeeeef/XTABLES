import os
import platform
import psutil
import socket
import time
from enum import Enum


try:
    # Package-level import
    from . import ClientStatistics_pb2 as cspf
except ImportError:
    # Standalone script import
    import ClientStatistics_pb2 as cspf


class HealthStatus(Enum):
    GOOD = "GOOD"
    OKAY = "OKAY"
    STRESSED = "STRESSED"
    OVERLOAD = "OVERLOAD"
    CRITICAL = "CRITICAL"
    UNKNOWN = "UNKNOWN"


class ClientStatistics:
    def __init__(self):
        self.nano_time = time.time_ns()
        self.max_memory_mb = psutil.virtual_memory().total // (1024 * 1024)
        self.free_memory_mb = psutil.virtual_memory().available // (1024 * 1024)
        self.used_memory_mb = self.max_memory_mb - self.free_memory_mb
        self.process_cpu_load_percentage = psutil.cpu_percent(interval=0.1)
        self.available_processors = os.cpu_count()
        self.total_threads = len(psutil.pids())
        self.ip = self.get_local_ip_address()
        self.hostname = self.get_hostname()
        self.process_id = os.getpid()
        self.python_version = platform.python_version()
        self.python_vendor = platform.python_compiler()
        self.jvm_name = platform.python_implementation()
        self.health = self.calculate_health()

        # Optional fields
        self.version = None
        self.type = "PYTHON"
        self.uuid = None
        self.buffer_size = None
        self.max_buffer_size = None

    def set_version(self, version):
        self.version = version
        return self

    def set_uuid(self, uuid):
        self.uuid = uuid
        return self

    def set_buffer_size(self, buffer_size):
        self.buffer_size = buffer_size
        return self

    def set_max_buffer_size(self, max_buffer_size):
        self.max_buffer_size = max_buffer_size
        return self

    @staticmethod
    def get_local_ip_address():
        try:
            return socket.gethostbyname(socket.gethostname())
        except socket.error:
            return "Unknown IP"

    @staticmethod
    def get_hostname():
        try:
            return socket.gethostname()
        except socket.error:
            return "Unknown Host"

    def calculate_health(self):
        if self.used_memory_mb <= self.max_memory_mb * 0.5 and self.process_cpu_load_percentage < 50:
            return HealthStatus.GOOD
        elif self.used_memory_mb <= self.max_memory_mb * 0.6 and self.process_cpu_load_percentage < 70:
            return HealthStatus.OKAY
        elif self.used_memory_mb <= self.max_memory_mb * 0.7 and self.process_cpu_load_percentage < 85:
            return HealthStatus.STRESSED
        elif self.used_memory_mb <= self.max_memory_mb * 0.85 and self.process_cpu_load_percentage < 95:
            return HealthStatus.OVERLOAD
        else:
            return HealthStatus.CRITICAL

    # Method to convert the ClientStatistics object to a Protobuf message
    def to_protobuf(self):
        stats = cspf.ClientStatistics()
        stats.nano_time = self.nano_time
        stats.max_memory_mb = self.max_memory_mb
        stats.free_memory_mb = self.free_memory_mb
        stats.used_memory_mb = self.used_memory_mb
        stats.process_cpu_load_percentage = self.process_cpu_load_percentage
        stats.available_processors = self.available_processors
        stats.total_threads = self.total_threads
        stats.ip = self.ip
        stats.hostname = self.hostname
        stats.process_id = str(self.process_id)
        stats.lang_version = self.python_version
        stats.lang_vendor = self.python_vendor
        stats.jvm_name = self.jvm_name
        stats.health = cspf.HealthStatus.Value(self.health.name)

        # Optional fields
        stats.version = self.version if self.version else ""
        stats.type = self.type
        stats.uuid = self.uuid if self.uuid else ""
        stats.buffer_size = self.buffer_size if self.buffer_size else 0
        stats.max_buffer_size = self.max_buffer_size if self.max_buffer_size else 0

        return stats

    # Method to create a ClientStatistics object from a Protobuf message
    @staticmethod
    def from_protobuf(stats_pb):
        stats = ClientStatistics()
        stats.nano_time = stats_pb.nano_time
        stats.max_memory_mb = stats_pb.max_memory_mb
        stats.free_memory_mb = stats_pb.free_memory_mb
        stats.used_memory_mb = stats_pb.used_memory_mb
        stats.process_cpu_load_percentage = stats_pb.process_cpu_load_percentage
        stats.available_processors = stats_pb.available_processors
        stats.total_threads = stats_pb.total_threads
        stats.ip = stats_pb.ip
        stats.hostname = stats_pb.hostname
        stats.process_id = stats_pb.process_id
        stats.python_version = stats_pb.lang_version
        stats.python_vendor = stats_pb.lang_vendor
        stats.jvm_name = stats_pb.jvm_name
        stats.health = HealthStatus(stats_pb.health)

        # Optional fields
        stats.version = stats_pb.version if stats_pb.version else None
        stats.type = stats_pb.type
        stats.uuid = stats_pb.uuid if stats_pb.uuid else None
        stats.buffer_size = stats_pb.buffer_size if stats_pb.buffer_size else None
        stats.max_buffer_size = stats_pb.max_buffer_size if stats_pb.max_buffer_size else None

        return stats
