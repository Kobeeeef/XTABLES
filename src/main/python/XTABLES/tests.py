
import logging
import time

from XTablesClient import XTablesClient

if __name__ == "__main__":
    c = XTablesClient(server_port=1735, server_ip="localhost")

    i = 0
    while True:
        i = i + 1
        c.executePutString("testag", "OOKOK")

    #c.subscribe_to_key("test", consumer)
    time.sleep(1000000)
