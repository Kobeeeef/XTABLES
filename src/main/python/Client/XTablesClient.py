
import json

from Client.RequestAction import RequestAction
from Client.SocketClient import SocketClient


class XTablesClient:
    def __init__(self, server_address, server_port):
        self.client = SocketClient(server_address, server_port, 1000)
        self.client.connect()
        self.gateson = json.JSONEncoder()

    def put_raw(self, key, value):
        return self._request_action(f"PUT {key} {value}")

    def put_array(self, key, value):
        parsed_value = json.dumps(value)
        return self._request_action(f"PUT {key} {parsed_value}")

    def put_integer(self, key, value):
        return self._request_action(f"PUT {key} {value}")

    def delete(self, key):
        return self._request_action(f"DELETE {key}")

    def get_raw(self, key):
        return self._request_action(f"GET {key}")

    def get_raw_json(self):
        return self._request_action("GET_RAW_JSON")

    def get_string(self, key):
        return self._request_action(f"GET {key}")

    def get_integer(self, key):
        return self._request_action(f"GET {key}", int)

    def get_array(self, key, response_type):
        return self._request_action(f"GET {key}", response_type)

    def get_tables(self, key):
        return self._request_action(f"GET_TABLES {key}", list)

    def _request_action(self, message, response_type=None):
        return RequestAction(self.client, message, response_type)
