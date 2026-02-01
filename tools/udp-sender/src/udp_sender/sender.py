"""UDP sender module for sending UDP packets."""

import socket
import time


def encode_packet(app_id: str, payload: bytes) -> bytes:
    """Encode a packet with the appId length-prefix format.

    Format:
        - Byte 0: Length of appId (1-255)
        - Bytes 1..length: AppId as UTF-8 string
        - Remaining bytes: Payload data

    Args:
        app_id: Application identifier (max 255 bytes UTF-8).
        payload: The packet payload.

    Returns:
        Encoded packet data.

    Raises:
        ValueError: If appId is empty or too long.
    """
    app_id_bytes = app_id.encode("utf-8")
    if not app_id_bytes or len(app_id_bytes) > 255:
        raise ValueError(f"appId must be 1-255 bytes, got {len(app_id_bytes)}")
    return bytes([len(app_id_bytes)]) + app_id_bytes + payload


class UdpSender:
    """UDP sender class for sending messages to a specified host and port."""

    def __init__(self, host: str, port: int = 5000, app_id: str | None = None) -> None:
        """Initialize UdpSender with target host and port.

        Args:
            host: Target hostname or IP address.
            port: Target UDP port (default: 5000).
            app_id: Application identifier for packet prefix (optional).
        """
        self.host = host
        self.port = port
        self.app_id = app_id

    def send(self, message: str) -> None:
        """Send a single UDP packet with the given message.

        The message is encoded as UTF-8 before sending.
        If app_id is set, the packet includes the length-prefixed appId.

        Args:
            message: The message to send.
        """
        payload = message.encode("utf-8")
        if self.app_id:
            data = encode_packet(self.app_id, payload)
        else:
            data = payload

        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.sendto(data, (self.host, self.port))

    def flood(self, rate: int = 100, duration: int = 10) -> int:
        """Send numbered UDP packets at the specified rate for the given duration.

        Sends messages in the format "packet #N" where N is the packet number.
        If app_id is set, packets include the length-prefixed appId.

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
                payload = message.encode("utf-8")
                if self.app_id:
                    data = encode_packet(self.app_id, payload)
                else:
                    data = payload
                sock.sendto(data, (self.host, self.port))
                time.sleep(interval)

        return count
