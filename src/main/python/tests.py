import logging
from XTablesClient import XTablesClient

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    client = XTablesClient(useZeroMQ=True, server_port=1735)
    while True:
        client.push_frame("ok", "oko")
