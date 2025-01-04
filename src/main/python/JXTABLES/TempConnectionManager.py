import os
import tempfile


class TempConnectionManager:
    """
    TempConnectionManager - A utility class for managing temporary connection information.

    This class handles the reading and writing of a temporary file that stores an IP address
    used for a network connection. The file is located in the system's temporary directory.

    Author: Kobe Lei
    Version: 1.0
    Package: XTABLES
    This is part of the XTABLES project, providing utility functions for managing temporary
    network connection data across application restarts.
    """

    def __init__(self):
        pass

    @staticmethod
    def get():
        """
        Retrieves the stored IP address from the temporary connection file.

        If the temporary file exists and contains data, the IP address is read and returned.
        Otherwise, None is returned.

        @return: the stored IP address, or None if the file does not exist or cannot be read.
        """
        try:
            temp_dir = tempfile.gettempdir()
            temp_file = os.path.join(temp_dir, "PYTHON-XTABLES-TEMP-CONNECTION.tmp")
            if os.path.exists(temp_file):
                try:
                    with open(temp_file, 'r') as file:
                        return file.read().strip()
                except IOError as e:
                    print(f"Error reading the file: {e}")
        except Exception as e:
            print(f"Error getting temp IP: {e}")
        return None

    @staticmethod
    def invalidate():
        """
        Deletes the temporary connection file.

        This method removes the temporary file where the IP address is stored. If the file
        does not exist, nothing happens.
        """
        try:
            temp_dir = tempfile.gettempdir()
            temp_file = os.path.join(temp_dir, "PYTHON-XTABLES-TEMP-CONNECTION.tmp")
            if os.path.exists(temp_file):
                try:
                    os.remove(temp_file)
                    print("Temporary connection file deleted.")
                    return True
                except IOError as e:
                    print(f"Error deleting the file: {e}")
                    return False
            else:
                print("No temporary connection file found to delete.")
                return False
        except Exception as e:
            print(f"Error invalidating temp IP: {e}")
            return False

    @staticmethod
    def set(ip_address):
        """
        Sets the provided IP address in the temporary connection file.

        If the temporary file does not exist, it is created. The IP address is then written to
        the file, ensuring that the connection information persists across application restarts.

        @param ip_address: the IP address to store in the temporary file.
        """
        try:
            temp_dir = tempfile.gettempdir()
            temp_file = os.path.join(temp_dir, "PYTHON-XTABLES-TEMP-CONNECTION.tmp")
            try:
                with open(temp_file, 'w') as file:
                    file.write(ip_address)
                return True
            except IOError as e:
                print(f"Error writing to the file: {e}")
                return False
        except Exception as e:
            print(f"Error setting temp IP: {e}")
            return False
