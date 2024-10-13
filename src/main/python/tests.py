import logging
from XTablesClient import XTablesClient

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    client = XTablesClient(useZeroMQ=True)
    while True:
        client.push_message("ok", "oko")
