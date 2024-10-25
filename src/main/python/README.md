
# XTablesClient

`XTablesClient` is a Python client library for connecting to an XTABLES server, supporting both direct socket and ZeroMQ-based communication. This client provides methods for data operations on network tables and offers convenient functionality for interacting with robotic applications or distributed systems.

## Features
- **Flexible Initialization**: Connects to a server directly or discovers it via mDNS.
- **Data Operations**: Supports multiple data types (`Boolean`, `String`, `Integer`, `Float`, `Array`, `Class`, `Bytes`) for seamless data transmission.
- **ZeroMQ Support**: Configurable ZeroMQ PUSH/PULL and PUB/SUB communication for real-time message streaming.
- **Robust Connection Management**: Includes automatic reconnection and resubscription after disconnection.
- **Subscription**: Allows for subscriptions to data updates for specific keys, invoking consumer functions on updates.

## Installation

Install `xtables-client` from PyPI:
```bash
pip install xtables-client
```

## Usage

### Import and Initialization
```python
from xtables_client import XTablesClient

# Initialize the client
client = XTablesClient(server_ip="192.168.1.10", server_port=1735, useZeroMQ=True)
```

### Data Operations

1. **Setting Values**
   - **Boolean**: `client.executePutBoolean("key", True)`
   - **String**: `client.executePutString("key", "value")`
   - **Integer**: `client.executePutInteger("key", 42)`
   - **Float**: `client.executePutFloat("key", 3.14)`

2. **Retrieving Values**
   - **Get Integer**: `value = client.getInteger("key")`
   - **Get String**: `value = client.getString("key")`
   - **Get Float**: `value = client.getFloat("key")`

3. **Subscriptions**
   - Subscribe to updates for a key and handle updates with a custom function:
     ```python
     def my_consumer(key, value):
         print(f"Update for {key}: {value}")

     client.subscribeForUpdates("key", my_consumer)
     ```

4. **ZeroMQ Operations**
   - **Push Data**: `client.push_frame("identifier", "message")`
   - **Receive Next**: `key, value = client.recv_next()`

### Connection Management
- **Reconnect**: Automatic reconnection and re-subscription to previously subscribed keys.
- **Shutdown**: Gracefully shuts down the client connection.
  ```python
  client.shutdown()
  ```

## Example

```python
from xtables_client import XTablesClient

client = XTablesClient(useZeroMQ=True, server_port=1735)
client.subscribe("image")

while True:
    key, value = client.recv_next()
    if key is not None:
        print(f"Received data for {key}: {value}")
```

## License
This project is licensed under the MIT License.

```

This README provides a clear overview of the client’s capabilities, installation instructions, and usage examples to facilitate integration with any XTABLES server.