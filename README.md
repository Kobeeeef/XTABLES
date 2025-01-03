# XTABLES - HIGH-PERFORMANCE NETWORK TABLES 

![image](https://github.com/user-attachments/assets/d95c6d07-a409-471d-bcca-cfcb07864079)


# XTablesClient Documentation

`XTablesClient` is part of the `org.kobe.xbot.Client` package, providing an interface to interact with a server for storing, retrieving, and managing data updates in various formats. This document details the methods available in the `XTablesClient` class, including their usage and new enhancements related to caching and error handling.

## Javadocs

You can access the Javadocs for XTABLES at the following link:

[XTABLES Javadocs](https://kobeeeef.github.io/XTABLES/index.html)

For more detailed documentation on XTABLES:

[Vision Documentation](https://team488.gitbook.io/xtables/java-client-setup)

## New Dashboard

# Client & Server Debugging Information
![image](https://github.com/user-attachments/assets/e7fa937a-262c-4db3-9df0-ada2719fd4c2)

# Main Page
![image](https://github.com/user-attachments/assets/7861da8f-72ed-4654-b3c1-e05d8cf48f5d)

## Comparison with FRC Network Tables

`XTablesClient` boasts superior performance and efficiency compared to traditional FRC Network Tables. Here are some key statistics highlighting the differences:

- **Message Rate**:
  - **FRC Network Tables**: Approximately 10 to 20 thousand messages per second.
  - **XTablesClient**: Capable of sending around ~1,500,000 updates per second, which translates to 90 million messages per minute with 0% loss.

- **Latency**:
  - **FRC Network Tables**: Typically higher latency due to lower message rates and less optimized data handling.
  - **XTablesClient**: Average round-trip latency of 0.13ms in a local wireless network, ensuring almost zero latency for real-time applications.

- **Efficiency**:
  - **FRC Network Tables**: Basic data management and limited functionalities.
  - **XTablesClient**: Enhanced with advanced features such as dynamic data compression, caching, and hybrid P2P video streaming for maximum optimization.

- **Functionality**:
  - **FRC Network Tables**: Primarily designed for basic data sharing and communication.
  - **XTablesClient**: Includes a comprehensive suite of features such as mDNS integration, custom logging, server-side scripting, and extensive caching mechanisms, making it a versatile tool for complex robotics applications and competitions.

## mDNS Integration

### Overview

The latest version of `XTablesClient` introduces support for mDNS (Multicast DNS) to facilitate automatic service discovery. This allows the client to locate and connect to the server using a service name, simplifying network configuration and connection management.

### How It Works

- **Server Side**: The server registers its service with mDNS, broadcasting its presence on the network with a specified name and port.
- **Client Side**: The client uses mDNS to discover the server by its service name, extracting the necessary connection details (such as IP address and port) to establish a connection.

This approach is particularly useful in dynamic or large-scale network environments where hardcoding server addresses is impractical.

### Important Note on IPv6 Compatibility

Some operating systems prefer IPv6, which might not be fully supported by the mDNS implementation used in `XTablesClient`. If you encounter issues related to `java.net.SocketException: Invalid argument: no further information`, it is likely due to the system preferring IPv6.

To resolve this issue, you must set the JVM option to prefer the IPv4 stack. Add the following option when starting your Java application:

```sh
-Djava.net.preferIPv4Stack=true
```

This setting forces the JVM to use the IPv4 stack, which should resolve the socket exception issue. Make sure to include this option in your command line or IDE configuration when running the application.


Here’s a simple `.md` documentation for both workflows without the YAML code:

---

## GitHub Actions Workflows

### 1. Create ZIP on Push

This workflow automates the creation of a ZIP file containing specific Java files from the repository whenever there is a push to the `master` branch. The workflow includes the following steps:
- It checks out the repository code.
- Installs the necessary `zip` utility.
- Creates a ZIP file of the `JClient` and `Utilities` Java files.
- Commits and pushes the ZIP file to a separate branch, `zip-files`.

### 2. Upload Python Package

This workflow automates the process of building and uploading a Python package to PyPI when a push occurs to the `master` branch. The workflow involves:
- Checking out the repository code.
- Setting up the appropriate Python version.
- Installing required dependencies (`setuptools`, `wheel`, `twine`).
- Cleaning up any previous build files.
- Building a source distribution and wheel package from the Python package.
- Uploading the package to PyPI using `twine`.

---

These workflows help automate repetitive tasks in your development process, such as creating ZIP archives for Java files and pushing Python packages to PyPI, ensuring smooth deployment and integration.
