# XTablesClient

**XTablesClient** is a Python client library designed to interact with the XTables server, allowing you to send and receive messages, subscribe to updates, and manage communication through ZeroMQ (ZMQ) sockets. The client supports publishing, subscribing, and retrieving various data types (strings, integers, booleans, arrays, and more) to/from the server.

## Features

- **Socket Communication**: Uses ZeroMQ for push, request, and subscription communication with the XTables server.
- **Subscription Management**: Allows subscribing to specific keys or to all updates.
- **Multiple Data Types**: Supports sending and receiving a variety of data types, including strings, integers, booleans, arrays, and raw bytes.
- **mDNS Support**: Automatically resolves the server's IP address using Zeroconf if the hostname is not provided.

## Installation

To install `XTablesClient`, simply use `pip`:

```bash
pip install XTablesClient
```

## Usage

### Basic Example

```python
from XTablesClient import XTablesClient

# Initialize the client
client = XTablesClient()

# Publish a string message
client.publish("some_key", b"Hello, XTables!")

# Subscribe to a key and handle incoming messages
def message_handler(message):
    print(f"Received message for key: {message.key} with value: {message.value}")

client.subscribe("some_key", message_handler)

# Retrieve data
data = client.getString("some_key")
print(data)
```

### Available Methods

#### Publishing Data
- `publish(key: str, value: str)`: Publish a string value to the server.
- `putString(key: str, value: str)`: Put a string value in the XTable.
- `putInteger(key: str, value: int)`: Put an integer value.
- `putBoolean(key: str, value: bool)`: Put a boolean value.

#### Subscribing to Updates
- `subscribe(key: str, consumer: Callable)`: Subscribe to updates for a specific key.
- `subscribe_all(consumer: Callable)`: Subscribe to updates for all keys.

#### Retrieving Data
- `getString(key: str)`: Get a string value from the server.
- `getInteger(key: str)`: Get an integer value.
- `getBoolean(key: str)`: Get a boolean value.
- `getArray(key: str)`: Get an array of values.

## Documentation

- **XTablesClient** is built with simplicity in mind and uses ZeroMQ for efficient communication with the XTables server. It can be easily extended or modified for custom use cases.
- The package uses **Zeroconf** to automatically discover the server in your network if the IP is not provided.

## License

`XTablesClient` is licensed under the MIT License. See the LICENSE file for more details.
