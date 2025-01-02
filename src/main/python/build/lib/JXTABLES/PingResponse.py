import locale


class PingResponse:
    """
    PingResponse - A class that encapsulates the result of a ping operation.

    This class contains information about the success or failure of a ping,
    as well as the round-trip time in nanoseconds. It also provides a method
    to convert the round-trip time to milliseconds.
    """

    def __init__(self, success: bool, round_trip_nano_seconds: int):
        """
        Constructor for initializing the PingResponse.

        :param success: Whether the ping was successful or not
        :param round_trip_nano_seconds: The round-trip time in nanoseconds
        """
        self.success = success
        self.round_trip_nano_seconds = round_trip_nano_seconds

    def is_success(self) -> bool:
        """
        Gets the success status of the ping.

        :return: True if the ping was successful, False otherwise
        """
        return self.success

    def get_round_trip_nano_seconds(self) -> int:
        """
        Gets the round-trip time in nanoseconds.

        :return: The round-trip time in nanoseconds
        """
        return self.round_trip_nano_seconds

    def get_round_trip_milliseconds(self) -> float:
        """
        Gets the round trip time in milliseconds.

        :return: The round-trip time in milliseconds
        """
        return self.round_trip_nano_seconds / 1_000_000

    def __str__(self) -> str:
        """
        Provides a formatted string representation of the PingResponse object.

        If the round trip time is under one million nanoseconds, it is displayed in nanoseconds,
        otherwise in milliseconds.

        :return: A string representing the success and round-trip time
        """
        locale.setlocale(locale.LC_ALL, '')  # Set locale to the user's default
        formatted_number = locale.format_string("%d", self.round_trip_nano_seconds, grouping=True)

        if self.round_trip_nano_seconds < 1_000_000:
            return f"PingResponse(success={self.success}, roundTripTime={formatted_number} ns)"
        else:
            formatted_ms = locale.format_string("%0.2f", self.get_round_trip_milliseconds(), grouping=True)
            return f"PingResponse(success={self.success}, roundTripTime={formatted_ms} ms)"
