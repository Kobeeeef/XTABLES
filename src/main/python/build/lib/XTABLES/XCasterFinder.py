import socket
import logging
import time
import threading
import os
from tkinter import Tk, Text, BOTH, END
from zeroconf import Zeroconf, ServiceBrowser, ServiceListener


class XCasterFinderServiceListener(ServiceListener):
    def __init__(self, gui):
        self.logger = logging.getLogger(__name__)
        self.service_resolved = False
        self.resolved_services = set()
        self.gui = gui

    def add_service(self, zeroconf, service_type, name):
        info = zeroconf.get_service_info(service_type, name)
        if info:
            self.resolve_service(info)

    def remove_service(self, name: str) -> None:
        self.logger.info(f"Service removed: {name}")

    def resolve_service(self, info):
        service_address = socket.inet_ntoa(info.addresses[0])
        service_id = f"{info.name}-{service_address}"

        if service_id not in self.resolved_services:
            # Collect service information
            properties = []
            for key, value in info.properties.items():
                property_info = f"  {key.decode('utf-8')}: {value.decode('utf-8') if isinstance(value, bytes) else value}"
                properties.append(property_info)

            # Prepare the service information (IP and properties with better formatting)
            service_info = f"IP: {service_address}\nProperties:\n" + "\n".join(properties) + "\n\n"
            print("--------------------------------------------------------")
            print(service_info)
            print("--------------------------------------------------------")
            self.gui.add_service(service_info)
            self.resolved_services.add(service_id)
        self.service_resolved = True


def search_for_services(gui):
    zeroconf = Zeroconf()
    listener = XCasterFinderServiceListener(gui)
    ServiceBrowser(zeroconf, "_xcaster._tcp.local.", listener)
    no_service_found = True

    try:
        while True:
            listener.service_resolved = False
            time.sleep(5)

            if not listener.service_resolved:
                if no_service_found:
                    logging.info("No services found. Restarting...")
                    zeroconf.close()
                    zeroconf = Zeroconf()
                    ServiceBrowser(zeroconf, "_xcaster._tcp.local.", listener)
                no_service_found = True
            else:
                no_service_found = False

    except KeyboardInterrupt:
        logging.info("Stopping service search...")
    finally:
        zeroconf.close()


class ServiceFinderApp:
    def __init__(self, root):
        self.root = root
        self.root.title("XCASTER")
        self.service_text = Text(root, wrap='word', width=100, height=20)
        self.service_text.pack(padx=10, pady=10, expand=True, fill=BOTH)

    def add_service(self, service_info):
        self.service_text.insert(END, service_info)
        self.service_text.insert(END, "\n")


def start_service_search(app):
    search_thread = threading.Thread(target=search_for_services, args=(app,))
    search_thread.daemon = True
    search_thread.start()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)

    root = Tk()
    app = ServiceFinderApp(root)
    logging.info("Searching for services...\n")
    start_service_search(app)

    root.mainloop()

    logging.fatal("Stopping processes.")
    os._exit(0)
