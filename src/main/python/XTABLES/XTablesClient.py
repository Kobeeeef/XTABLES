# import socket
# import time
# import zmq
# import struct
# import XTableProto_pb2 as XTableProto
#
#
# class XTablesClient:
#     # ================================================================
#     # Static Variables
#     # ================================================================
#     SUCCESS_BYTE = bytes([0x01])
#     FAIL_BYTE = bytes([0x00])
#     # ================================================================
#     # Instance Variables
#     # ================================================================
#     XTABLES_CLIENT_VERSION = "XTABLES Python Client v1.0.0 | Build Date: 12/24/2024"
#
#     def __init__(self, ip=None, push_port=1735, req_port=1736, sub_port=1737):
#         self.context = zmq.Context()
#         self.push_socket = self.context.socket(zmq.PUSH)
#         self.push_socket.set_hwm(500)
#         self.req_socket = self.context.socket(zmq.REQ)
#         self.req_socket.set_hwm(500)
#         self.req_socket.setsockopt(zmq.RCVTIMEO, 3000)
#         self.sub_socket = self.context.socket(zmq.SUB)
#         self.sub_socket.set_hwm(500)
#
#         if ip is None:
#             ip = self.resolve_host_by_name()
#
#         if ip is None:
#             raise XTablesServerNotFound("Could not resolve XTABLES hostname server.")
#
#         print(f"\nConnecting to XTABLES Server...\n"
#               f"------------------------------------------------------------\n"
#               f"Server IP: {ip}\n"
#               f"Push Socket Port: {push_port}\n"
#               f"Request Socket Port: {req_port}\n"
#               f"Subscribe Socket Port: {sub_port}\n"
#               f"------------------------------------------------------------")
#
#         self.push_socket.connect(f"tcp://{ip}:{push_port}")
#         self.req_socket.connect(f"tcp://{ip}:{req_port}")
#         self.sub_socket.connect(f"tcp://{ip}:{sub_port}")
#
#     def resolve_host_by_name(self):
#         try:
#             return socket.gethostbyname('XTABLES.local')
#         except socket.gaierror:
#             return None
#
#     def send_push_message(self, command, key, value, msg_type):
#         message = XTableProto.XTableMessage()
#         message.key = key
#         message.command = command
#         message.value = value
#         message.type = msg_type
#         try:
#             self.push_socket.send(message.SerializeToString())
#             return True
#         except zmq.ZMQError:
#             print(f"Error sending message: {e}")
#             return False
#
#     # ====================
#     # PUT Methods
#     # ====================
#     def execute_put_bytes(self, key, value):
#         return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value,
#                                       XTableProto.XTableMessage.Type.UNKNOWN)
#
#     def execute_put_string(self, key, value):
#         return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value.encode('utf-8'),
#                                       XTableProto.XTableMessage.Type.STRING)
#
#     def execute_put_integer(self, key, value):
#         value_bytes = struct.pack('!i', value)
#         return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
#                                       XTableProto.XTableMessage.Type.INT64)
#
#     def execute_put_long(self, key, value):
#         value_bytes = struct.pack('!q', value)
#         return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
#                                       XTableProto.XTableMessage.Type.INT64)
#
#     def execute_put_double(self, key, value):
#         value_bytes = struct.pack('!d', value)
#         return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
#                                       XTableProto.XTableMessage.Type.DOUBLE)
#
#     def execute_put_boolean(self, key, value):
#         value_bytes = self.SUCCESS_BYTE if value else self.FAIL_BYTE
#         return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
#                                       XTableProto.XTableMessage.Type.BOOL)
#
#     def execute_put_array(self, key, values):
#         """
#         Handles putting arrays to the server.
#         Assumes `values` is an array of values that can be serialized into a byte array.
#         """
#         serialized_array = [struct.pack('!i', value) for value in values]  # Example with integers
#         array_value = b''.join(serialized_array)
#         return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, array_value,
#                                       XTableProto.XTableMessage.Type.ARRAY)
#
#     # ====================
#     # GET Methods
#     # ====================
#
#     def execute_get_string(self, key):
#         message = self.get_xtable_message(key)
#         print(message)
#         if message is not None and message.type == XTableProto.XTableMessage.Type.STRING:
#             return message.value.decode('utf-8')
#         elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
#             return None
#         else:
#             raise ValueError(f"Expected STRING type, but got: {message.type}")
#
#     def execute_get_integer(self, key):
#         message = self.get_xtable_message(key)
#         if message is not None and message.type == XTableProto.XTableMessage.Type.INT64:
#             return struct.unpack('!i', message.value)[0]
#         elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
#             return None
#         else:
#             raise ValueError(f"Expected INT type, but got: {message.type}")
#
#     def execute_get_boolean(self, key):
#         message = self.get_xtable_message(key)
#         if message is not None and message.type == XTableProto.XTableMessage.Type.BOOL:
#             return message.value == self.SUCCESS_BYTE
#         elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
#             return None
#         else:
#             raise ValueError(f"Expected BOOL type, but got: {message.type}")
#
#     def execute_get_long(self, key):
#         message = self.get_xtable_message(key)
#         if message is not None and message.type == XTableProto.XTableMessage.Type.INT64:
#             return struct.unpack('!q', message.value)[0]
#         elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
#             return None
#         else:
#             raise ValueError(f"Expected LONG type, but got: {message.type}")
#
#     def execute_get_double(self, key):
#         message = self.get_xtable_message(key)
#         if message is not None and message.type == XTableProto.XTableMessage.Type.DOUBLE:
#             return struct.unpack('!d', message.value)[0]
#         elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
#             return None
#         else:
#             raise ValueError(f"Expected DOUBLE type, but got: {message.type}")
#
#     def execute_get_array(self, key):
#         message = self.get_xtable_message(key)
#         if message is not None and message.type == XTableProto.XTableMessage.Type.ARRAY:
#             return message.value
#         elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
#             return None
#         else:
#             raise ValueError(f"Expected ARRAY type, but got: {message.type}")
#
#     def get_xtable_message(self, key):
#         try:
#             message = XTableProto.XTableMessage()
#             message.key = key
#             message.command = XTableProto.XTableMessage.Command.GET
#             self.req_socket.send(message.SerializeToString())
#             response_bytes = self.req_socket.recv()
#             response_message = XTableProto.XTableMessage.FromString(response_bytes)
#
#             return response_message
#
#         except Exception as e:
#             print(f"Error occurred: {e}")
#             return None
#
#     # ====================
#     # Version and Properties Methods
#     # ====================
#     def get_client_version(self):
#         return self.XTABLES_CLIENT_VERSION
#
#
#
# class XTablesServerNotFound(Exception):
#     pass
#
#
# # Sample usage
# if __name__ == "__main__":
#     client = XTablesClient("localhost")
#     client.execute_put_boolean("name", True)
#     print(client.execute_get_boolean("name"))
#     print(client.execute_get_integer("age"))
#     print(client.execute_get_array("numbers"))
