# XTablesClient Documentation

`XTablesClient` is part of the `org.kobe.xbot.Client` package and provides an interface for interacting with a server to store and retrieve data in various formats. Below are the methods provided by the `XTablesClient` class, along with their descriptions and usage examples.

## Constructor

- **XTablesClient(String SERVER_ADDRESS, int SERVER_PORT)**
  - Constructs an instance of `XTablesClient` which connects to the specified server address and port.
  - **Parameters:**
    - `SERVER_ADDRESS`: The address of the server.
    - `SERVER_PORT`: The port number on which the server is listening.

## Methods

### Put Methods
These methods store data on the server under a specified key.

- **putRaw(String key, String value)**
  - Stores a raw string value under the given key.
  - **Parameters:**
    - `key`: Key under which the value is stored.
    - `value`: Raw string to be stored.

- **putArray(String key, List<T> value)**
  - Stores an array under the given key, converting it to JSON format.
  - **Type Parameters:**
    - `<T>`: The type of elements in the array.
  - **Parameters:**
    - `key`: Key under which the array is stored.
    - `value`: List of type T to be stored.

- **putInteger(String key, Integer value)**
  - Stores an integer value under the specified key.
  - **Parameters:**
    - `key`: Key under which the value is stored.
    - `value`: Integer to be stored.

- **putObject(String key, Object value)**
  - Stores any object under the given key by converting it to JSON.
  - **Parameters:**
    - `key`: Key under which the object is stored.
    - `value`: Object to be stored.

### Get Methods
These methods retrieve data from the server using the specified key.

- **getRaw(String key)**
  - Retrieves a raw string value from the server.
  - **Parameters:**
    - `key`: Key for which the value is retrieved.

- **getString(String key)**
  - Retrieves a string value from the server.
  - **Parameters:**
    - `key`: Key for which the string is retrieved.

- **getInteger(String key)**
  - Retrieves an integer value from the server.
  - **Parameters:**
    - `key`: Key for which the integer is retrieved.

- **getObject(String key, Class<T> type)**
  - Retrieves an object of type T from the server.
  - **Type Parameters:**
    - `<T>`: The expected type of the returned object.
  - **Parameters:**
    - `key`: Key for which the object is retrieved.
    - `type`: Class object of type T.

- **getArray(String key, Class<T> type)**
  - Retrieves an array of type T from the server.
  - **Type Parameters:**
    - `<T>`: The component type of the array.
  - **Parameters:**
    - `key`: Key for which the array is retrieved.
    - `type`: Class object of type T indicating the component type of the array.

### Delete Method
- **delete(String key)**
  - Deletes the value associated with the specified key from the server.
  - **Parameters:**
    - `key`: Key for which the value is to be deleted.

### Miscellaneous
- **getTables(String key)**
  - Retrieves a list of table names or similar structures from the server.
  - **Parameters:**
    - `key`: Key used to query the server for tables.

## Usage Example

```java
XTablesClient client = new XTablesClient("localhost", 8000);
client.putInteger("session_id", 1001).complete();
Integer sessionId = client.getInteger("session_id").complete();
System.out.println("Session ID: " + sessionId);
