"""UDP sender module for sending UDP packets."""

import socket
import time


class UdpSender:
    """UDP sender class for sending messages to a specified host and port."""

    def __init__(self, host: str, port: int = 5000) -> None:
        """Initialize UdpSender with target host and port.

        Args:
            host: Target hostname or IP address.
            port: Target UDP port (default: 5000).
        """
        self.host = host
        self.port = port

    def send(self, message: str) -> None:
        """Send a single UDP packet with the given message.

        The message is encoded as UTF-8 before sending.

        Args:
            message: The message to send.
        """
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.sendto(message.encode("utf-8"), (self.host, self.port))

    def flood(self, rate: int = 100, duration: int = 10) -> int:
        """Send numbered UDP packets at the specified rate for the given duration.

        Sends messages in the format "packet #N" where N is the packet number.

        Args:
            rate: Number of packets to send per second (default: 100).
            duration: Total duration in seconds to send packets (default: 10).

        Returns:
            The total number of packets sent.
        """
        interval = 1.0 / rate
        count = 0
        start_time = time.time()

        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            while time.time() - start_time < duration:
                count += 1
                message = f"packet #{count}"
                sock.sendto(message.encode("utf-8"), (self.host, self.port))
                time.sleep(interval)

        return count
