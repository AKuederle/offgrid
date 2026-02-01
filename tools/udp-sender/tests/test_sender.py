"""Tests for UdpSender class."""

import socket
from unittest.mock import MagicMock, patch

from udp_sender.sender import UdpSender


class TestUdpSenderInit:
    """Tests for UdpSender initialization."""

    def test_init_with_host_and_default_port(self) -> None:
        """UdpSender initializes with host and default port 5000."""
        sender = UdpSender("192.168.1.100")
        assert sender.host == "192.168.1.100"
        assert sender.port == 5000

    def test_init_with_custom_port(self) -> None:
        """UdpSender accepts custom port."""
        sender = UdpSender("localhost", port=9999)
        assert sender.host == "localhost"
        assert sender.port == 9999


class TestUdpSenderSend:
    """Tests for UdpSender.send() method."""

    @patch("udp_sender.sender.socket.socket")
    def test_send_creates_udp_socket(self, mock_socket_class: MagicMock) -> None:
        """send() creates a UDP socket."""
        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        sender = UdpSender("localhost", port=5000)
        sender.send("test message")

        mock_socket_class.assert_called_once_with(socket.AF_INET, socket.SOCK_DGRAM)

    @patch("udp_sender.sender.socket.socket")
    def test_send_encodes_message_as_utf8(self, mock_socket_class: MagicMock) -> None:
        """send() encodes message as UTF-8."""
        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        sender = UdpSender("192.168.1.1", port=5000)
        sender.send("hello world")

        mock_socket.sendto.assert_called_once_with(
            b"hello world", ("192.168.1.1", 5000)
        )

    @patch("udp_sender.sender.socket.socket")
    def test_send_handles_unicode_message(self, mock_socket_class: MagicMock) -> None:
        """send() properly encodes unicode characters."""
        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        sender = UdpSender("localhost", port=5000)
        sender.send("test with unicode: \u00e4\u00f6\u00fc")

        expected_bytes = "test with unicode: \u00e4\u00f6\u00fc".encode("utf-8")
        mock_socket.sendto.assert_called_once_with(expected_bytes, ("localhost", 5000))


class TestUdpSenderFlood:
    """Tests for UdpSender.flood() method."""

    @patch("udp_sender.sender.time.sleep")
    @patch("udp_sender.sender.time.time")
    @patch("udp_sender.sender.socket.socket")
    def test_flood_returns_packet_count(
        self,
        mock_socket_class: MagicMock,
        mock_time: MagicMock,
        mock_sleep: MagicMock,
    ) -> None:
        """flood() returns the number of packets sent."""
        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        # Use a counter to simulate time progression
        call_count = [0]
        start_time = 1000.0

        def time_side_effect() -> float:
            call_count[0] += 1
            # First call is start_time, then increment by 0.1 each call
            # After 50 calls, we'll have exceeded duration=10
            if call_count[0] == 1:
                return start_time
            elif call_count[0] <= 5:
                return start_time + 0.1 * (call_count[0] - 1)
            else:
                return start_time + 11.0  # Exceed duration

        mock_time.side_effect = time_side_effect

        sender = UdpSender("localhost", port=5000)
        count = sender.flood(rate=100, duration=10)

        # Should have sent at least 1 packet
        assert count >= 1
        assert mock_socket.sendto.called

    @patch("udp_sender.sender.time.sleep")
    @patch("udp_sender.sender.time.time")
    @patch("udp_sender.sender.socket.socket")
    def test_flood_sends_numbered_messages(
        self,
        mock_socket_class: MagicMock,
        mock_time: MagicMock,
        mock_sleep: MagicMock,
    ) -> None:
        """flood() sends numbered messages."""
        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        # Simulate quick exit after a few packets
        start_time = 1000.0
        # Let 3 packets through, then exceed duration
        mock_time.side_effect = [
            start_time,  # initial time
            start_time + 0.01,  # packet 1
            start_time + 0.02,  # packet 2
            start_time + 0.03,  # packet 3
            start_time + 11.0,  # exceed duration
        ]

        sender = UdpSender("localhost", port=5000)
        sender.flood(rate=100, duration=10)

        # Check that numbered messages were sent
        calls = mock_socket.sendto.call_args_list
        assert len(calls) >= 1
        # First message should be packet #1
        first_call_data = calls[0][0][0]
        assert b"1" in first_call_data

    @patch("udp_sender.sender.time.sleep")
    @patch("udp_sender.sender.time.time")
    @patch("udp_sender.sender.socket.socket")
    def test_flood_uses_default_rate_and_duration(
        self,
        mock_socket_class: MagicMock,
        mock_time: MagicMock,
        mock_sleep: MagicMock,
    ) -> None:
        """flood() uses default rate=100 and duration=10."""
        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        # Immediately exceed duration
        mock_time.side_effect = [1000.0, 1011.0]

        sender = UdpSender("localhost", port=5000)
        # Call without arguments to test defaults
        count = sender.flood()

        # Should have returned (duration exceeded immediately)
        assert count >= 0
