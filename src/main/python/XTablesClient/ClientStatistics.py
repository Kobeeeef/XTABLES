import psutil
import platform
import socket
import os
import time
import json

class ClientStatistics:
    class HealthStatus:
        GOOD = "GOOD"
        OKAY = "OKAY"
        STRESSED = "STRESSED"
        OVERLOAD = "OVERLOAD"
        CRITICAL = "CRITICAL"
        UNKNOWN = "UNKNOWN"

    def __init__(self):
        # System information
        self.nanoTime = time.time_ns()
        self.maxMemoryMB = psutil.virtual_memory().total // (1024 * 1024)
        self.freeMemoryMB = psutil.virtual_memory().available // (1024 * 1024)
        self.usedMemoryMB = self.maxMemoryMB - self.freeMemoryMB
        self.processCpuLoadPercentage = psutil.cpu_percent(interval=1)
        self.availableProcessors = os.cpu_count()
        self.totalThreads = len(psutil.pids())
        self.ip = self.get_local_ip_address()

        # Hostname
        self.hostname = socket.gethostname()

        # Process and environment information
        self.processId = str(os.getpid())
        self.pythonVersion = platform.python_version()
        self.pythonVendor = platform.python_implementation()
        self.pythonCompiler = platform.python_compiler()

        # Health status based on system usage
        if self.usedMemoryMB <= self.maxMemoryMB * 0.5 and self.processCpuLoadPercentage < 50 and self.totalThreads <= self.availableProcessors * 4:
            self.health = self.HealthStatus.GOOD
        elif self.usedMemoryMB <= self.maxMemoryMB * 0.6 and self.processCpuLoadPercentage < 70 and self.totalThreads <= self.availableProcessors * 6:
            self.health = self.HealthStatus.OKAY
        elif self.usedMemoryMB <= self.maxMemoryMB * 0.7 and self.processCpuLoadPercentage < 85 and self.totalThreads <= self.availableProcessors * 8:
            self.health = self.HealthStatus.STRESSED
        elif self.usedMemoryMB <= self.maxMemoryMB * 0.85 and self.processCpuLoadPercentage < 95 and self.totalThreads <= self.availableProcessors * 10:
            self.health = self.HealthStatus.OVERLOAD
        else:
            self.health = self.HealthStatus.CRITICAL

    def get_local_ip_address(self):
        try:
            return socket.gethostbyname(socket.gethostname())
        except socket.error:
            return "Unknown IP"

    def setVersion(self, version):
        self.version = version
        return self

    # Method to convert attributes to JSON with Java-compatible names
    def to_json(self):
        return json.dumps({
            "nanoTime": self.nanoTime,
            "maxMemoryMB": self.maxMemoryMB,
            "freeMemoryMB": self.freeMemoryMB,
            "usedMemoryMB": self.usedMemoryMB,
            "processCpuLoadPercentage": self.processCpuLoadPercentage,
            "availableProcessors": self.availableProcessors,
            "totalThreads": self.totalThreads,
            "ip": self.ip,
            "hostname": self.hostname,
            "processId": self.processId,
            "pythonVersion": self.pythonVersion,
            "pythonVendor": self.pythonVendor,
            "pythonCompiler": self.pythonCompiler,
            "health": self.health
        }, indent=4)


