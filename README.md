# XTablesClient Documentation

`XTablesClient` is part of the `org.kobe.xbot.Client` package, providing an interface to interact with a server for storing and retrieving data in various formats. This document details the methods available in the `XTablesClient` class, including their synchronous and asynchronous usage.

## Constructor

- **XTablesClient(String SERVER_ADDRESS, int SERVER_PORT)**
  - Initializes a connection to the specified server address and port.
  - **Parameters:**
    - `SERVER_ADDRESS`: The IP address or hostname of the server.
    - `SERVER_PORT`: The port number on which the server is listening.

## Methods

### Put Methods
Store data on the server under a specified key.

- **putRaw(String key, String value)**
  - Stores a raw string under the given key.
  - **Synchronous**: `complete()` blocks until the operation is completed, with a timeout of 5 seconds.
  - **Asynchronous**: `queue()` accepts success and failure callbacks.

- **putArray(String key, List<T> value)**
  - Stores an array under the given key, serialized to JSON format.
  - **Synchronous/Asynchronous**: Same as above.

- **putInteger(String key, Integer value)**
  - Stores an integer value under the specified key.
  - **Synchronous/Asynchronous**: Same as above.

- **putObject(String key, Object value)**
  - Stores any serializable object under the given key by converting it to JSON.
  - **Synchronous/Asynchronous**: Same as above.

### Get Methods
Retrieve data from the server using the specified key.

- **getRaw(String key)**
  - Retrieves a raw string value.
  - **Synchronous/Asynchronous**: Same as above.

- **getString(String key)**
  - Retrieves a string value.
  - **Synchronous/Asynchronous**: Same as above.

- **getInteger(String key)**
  - Retrieves an integer value.
  - **Synchronous/Asynchronous**: Same as above.

- **getObject(String key, Class<T> type)**
  - Retrieves an object of type T.
  - **Synchronous/Asynchronous**: Same as above.

- **getArray(String key, Class<T> type)**
  - Retrieves an array of type T.
  - **Synchronous/Asynchronous**: Same as above.

### Delete Method
- **delete(String key)**
  - Deletes the value associated with the specified key.
  - **Synchronous/Asynchronous**: Same as above.

### Miscellaneous
- **getTables(String key)**
  - Retrieves a list of table names or similar structures.
  - **Synchronous/Asynchronous**: Same as above.

## Usage Examples

```java
XTablesClient client = new XTablesClient("localhost", 8000);
// Synchronous use
client.putInteger("session_id", 1001).complete();
// Asynchronous use
client.getInteger("session_id").queue(
    result -> System.out.println("Session ID: " + result),
    error -> System.err.println("Error retrieving session ID")
);
