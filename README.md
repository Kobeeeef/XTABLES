
# XTablesClient Documentation

`XTablesClient` is part of the `org.kobe.xbot.Client` package, providing an interface to interact with a server for storing, retrieving, and managing data updates in various formats. This document details the methods available in the `XTablesClient` class, including their usage and new enhancements related to caching and error handling.

## Constructor

- **XTablesClient(String SERVER_ADDRESS, int SERVER_PORT, int MAX_THREADS, boolean useCache)**
  - Initializes the client with server connection and optional caching.

## Connection Management

- **stopAll()**
  - Stops all client connections and interrupts the cache management thread.
- **updateServerAddress(String SERVER_ADDRESS, int SERVER_PORT)**
  - Updates the server address and port for the client connection.

## Cache Management

- **getCacheFetchCooldown()**
  - Retrieves the cooldown time between cache fetches.
- **setCacheFetchCooldown(long cacheFetchCooldown)**
  - Sets the cooldown time between cache fetches. This defines how frequently, in milliseconds, the client automatically fetches updates from the server to ensure the cache remains synchronized. The default value is 10000 milliseconds (10 seconds).
- **isCacheReady()**
  - Checks if the cache is ready for use.
- **getCache()**
  - Retrieves the current state of the cache.

### How Cache Works

The caching mechanism in `XTablesClient` is designed to minimize network traffic and enhance performance by maintaining a local copy of server data. Upon initialization, the client fetches all current data from the server to populate the local cache. It then subscribes to updates from the server; any subsequent updates on the server side are automatically pushed to the client, ensuring that the local cache is always synchronized with the server.

Additionally, the client automatically fetches updates at intervals defined by the `cacheFetchCooldown` to prevent any discrepancies, especially in scenarios where real-time update notifications might fail or get delayed.

This setup reduces the number of network messages needed, as the client does not have to query the server for each data retrieval and relies on real-time updates for any changes.

**Note:** Any updates to the local cache are local only and do not affect server-side data. This means that changes made directly to the cache will not be reflected on the server unless explicitly sent via an update method (e.g., `putString`, `putInteger`).

### Data Compression and Dynamic Compression Level Adjustment

To further optimize network bandwidth usage, `XTablesClient` implements data compression using Gzip, followed by Base64 encoding for network transmission. The compression level is dynamically adjusted based on the time it takes to compress data. This dynamic adjustment helps balance the trade-off between compression efficiency and processing speed, ensuring optimal performance under varying network conditions.

The compression level is adjusted as follows:

- If the compression time is faster than the set average, the compression level is increased to achieve higher compression ratios and reduce network payload size.
- Conversely, if the compression time is slower than the set average, the compression level is decreased to prioritize faster processing speed and reduce computational overhead.

You can customize the average compression speed threshold by using the `setSpeedAverageMS(double milliseconds)` method in the `org.kobe.xbot.Utilites.DataCompression` class. This allows you to fine-tune the compression behavior based on your specific network environment and performance requirements.

## Data Management Methods

### Put Methods

- **putRaw(String key, String value)**
  - Stores a raw string value under a specified key.
- **putString(String key, String value)**
  - Stores a string value after converting it to JSON.
- **putInteger(String key, Integer value)**
  - Stores an integer value under a specified key.
- **putBoolean(String key, Boolean value)**
  - Stores a Boolean value under a specified key.
- **putObject(String key, Object value)**
  - Stores any serializable object under the specified key by converting it to JSON.
- **putArray(String key, List<T> value)**
  - Stores a list of objects under a specified key, serialized to JSON format.
- **renameKey(String key, String newName)**
  - Renames an existing key to a new name, effectively updating the key while preserving associated data.

### Get Methods

- **getRaw(String key)**
  - Retrieves a raw string value using the specified key.
- **getString(String key)**
  - Retrieves a string value from the server after converting from JSON.
- **getInteger(String key)**
  - Retrieves an integer value using the specified key.
- **getBoolean(String key)**
  - Retrieves a Boolean value using the specified key.
- **getObject(String key, Class<T> type)**
  - Retrieves an object of the specified type using the given key.
- **getArray(String key, Class<T> type)**
  - Retrieves an array of objects of the specified type using the given key.
- **getRawJSON()**
  - Retrieves the raw JSON data from the server.

### Delete Method

- **delete(String key)**
  - Deletes the specified key and its associated data from the server.
- **deleteAll()**
  - Deletes the entire dataset and its associated data from the server.

### Update Event Subscription

- **subscribeUpdateEvent(String key, Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer)**
  - Subscribes to updates for a specific key using a consumer.
- **subscribeUpdateEvent(Consumer<SocketClient.KeyValuePair<String>> consumer)**
  - Subscribes to any update event and returns the updates as raw JSON strings.
- **unsubscribeUpdateEvent(String key, Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer)**
  - Unsubscribes from updates for a specific key, removing the consumer.

### Handling Multiple Requests

- **completeAll(List<RequestAction<T>> requestActions)**
  - Completes all provided asynchronous request actions synchronously, returning a list of results.
- **queueAll(List<RequestAction<T>> requestActions)**
  - Queues all provided request actions for asynchronous processing without waiting for results.

### Miscellaneous

- **rebootServer()**
  - Sends a command to reboot the server.
- **sendCustomMessage(MethodType method, String message, Class<T> type)**
  - Sends a custom message to the server using a specified method and expects a return type as specified.
- **ping_latency()**
  - Measures the network and round-trip latency in milliseconds.

## Usage Examples

```java
XTablesClient client = new XTablesClient("localhost", 1735, 10, true);
// Initialize cache and handle updates
client.subscribeUpdateEvent("session_id", Integer.class, kv -> {
    System.out.println("Update received for session_id: " + kv.getValue());
});
// Synchronous operation to put a new session ID
client.putInteger("session_id", 1001).complete();
```
## Logging

The latest version of `XTablesClient` introduces custom logging capabilities, allowing users to configure logging levels for enhanced control over debugging and monitoring. To set the logging level, utilize the `XTablesLogger.setLoggingLevel(Level)` method.

## Advanced Features

- Handling JSON parsing errors and interruptions during update processing.
- Efficient management of multiple consumers per update key using a hashmap.

```
This comprehensive guide ensures clarity on all functionalities and methods available in the `XTablesClient` class, facilitating effective server interaction and data management.
```