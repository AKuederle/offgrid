"""Tests for reliable UDP protocol implementation."""

import struct
from unittest.mock import MagicMock, patch

import pytest


class TestPacketType:
    """Tests for PacketType enum."""

    def test_data_type_value(self) -> None:
        """DATA type has value 0x01."""
        from udp_sender.reliable import PacketType

        assert PacketType.DATA.value == 0x01

    def test_ack_type_value(self) -> None:
        """ACK type has value 0x02."""
        from udp_sender.reliable import PacketType

        assert PacketType.ACK.value == 0x02

    def test_ping_type_value(self) -> None:
        """PING type has value 0x03."""
        from udp_sender.reliable import PacketType

        assert PacketType.PING.value == 0x03

    def test_from_byte_valid(self) -> None:
        """from_byte returns correct PacketType for valid bytes."""
        from udp_sender.reliable import PacketType

        assert PacketType.from_byte(0x01) == PacketType.DATA
        assert PacketType.from_byte(0x02) == PacketType.ACK
        assert PacketType.from_byte(0x03) == PacketType.PING

    def test_from_byte_invalid(self) -> None:
        """from_byte returns None for invalid bytes."""
        from udp_sender.reliable import PacketType

        assert PacketType.from_byte(0x00) is None
        assert PacketType.from_byte(0xFF) is None


class TestHeader:
    """Tests for Header class."""

    def test_header_size_is_11_bytes(self) -> None:
        """Header serializes to exactly 11 bytes."""
        from udp_sender.reliable import Header, PacketType

        header = Header(
            packet_type=PacketType.DATA,
            message_id=1,
            sequence_number=1,
            fragment_index=1,
            fragment_total=1,
        )
        assert len(header.to_bytes()) == 11

    def test_header_serialization_big_endian(self) -> None:
        """Header serializes in big-endian format."""
        from udp_sender.reliable import Header, PacketType

        header = Header(
            packet_type=PacketType.DATA,
            message_id=0x12345678,
            sequence_number=0xABCDEF01,
            fragment_index=5,
            fragment_total=10,
        )
        data = header.to_bytes()

        # Type byte
        assert data[0] == 0x01
        # Message ID (big-endian)
        assert data[1:5] == bytes([0x12, 0x34, 0x56, 0x78])
        # Sequence number (big-endian)
        assert data[5:9] == bytes([0xAB, 0xCD, 0xEF, 0x01])
        # Fragment index and total
        assert data[9] == 5
        assert data[10] == 10

    def test_header_roundtrip(self) -> None:
        """Header serialization/deserialization roundtrips correctly."""
        from udp_sender.reliable import Header, PacketType

        original = Header(
            packet_type=PacketType.DATA,
            message_id=42,
            sequence_number=100,
            fragment_index=3,
            fragment_total=5,
        )
        data = original.to_bytes()
        parsed = Header.from_bytes(data)

        assert parsed is not None
        assert parsed.packet_type == original.packet_type
        assert parsed.message_id == original.message_id
        assert parsed.sequence_number == original.sequence_number
        assert parsed.fragment_index == original.fragment_index
        assert parsed.fragment_total == original.fragment_total

    def test_header_from_bytes_with_offset(self) -> None:
        """Header can parse from bytes with offset."""
        from udp_sender.reliable import Header, PacketType

        header = Header(
            packet_type=PacketType.ACK,
            message_id=99,
            sequence_number=50,
            fragment_index=1,
            fragment_total=1,
        )
        # Add prefix bytes
        data = bytes([0xFF, 0xFF]) + header.to_bytes()
        parsed = Header.from_bytes(data, offset=2)

        assert parsed is not None
        assert parsed.packet_type == PacketType.ACK
        assert parsed.message_id == 99

    def test_header_from_bytes_too_short(self) -> None:
        """Header.from_bytes returns None for insufficient data."""
        from udp_sender.reliable import Header

        assert Header.from_bytes(bytes(10)) is None
        assert Header.from_bytes(bytes(0)) is None

    def test_header_from_bytes_invalid_type(self) -> None:
        """Header.from_bytes returns None for invalid packet type."""
        from udp_sender.reliable import Header

        # Build invalid header with unknown type byte
        data = bytes([0xFF]) + bytes(10)
        assert Header.from_bytes(data) is None

    def test_header_fragment_validation(self) -> None:
        """Header validates fragment indices."""
        from udp_sender.reliable import Header, PacketType

        # Valid: index <= total
        h = Header(PacketType.DATA, 1, 1, 3, 5)
        assert h.fragment_index == 3
        assert h.fragment_total == 5

        # Invalid: index > total
        with pytest.raises(ValueError):
            Header(PacketType.DATA, 1, 1, 6, 5)

        # Invalid: zero index
        with pytest.raises(ValueError):
            Header(PacketType.DATA, 1, 1, 0, 5)

        # Invalid: zero total
        with pytest.raises(ValueError):
            Header(PacketType.DATA, 1, 1, 1, 0)


class TestAckRange:
    """Tests for AckRange class."""

    def test_ack_range_contains(self) -> None:
        """AckRange.contains() checks sequence number membership."""
        from udp_sender.reliable import AckRange

        r = AckRange(smallest=10, largest=20)

        assert r.contains(10)
        assert r.contains(15)
        assert r.contains(20)
        assert not r.contains(9)
        assert not r.contains(21)

    def test_ack_range_size(self) -> None:
        """AckRange.size returns number of sequence numbers in range."""
        from udp_sender.reliable import AckRange

        assert AckRange(10, 10).size == 1
        assert AckRange(10, 20).size == 11
        assert AckRange(0, 99).size == 100

    def test_ack_range_validation(self) -> None:
        """AckRange validates smallest <= largest."""
        from udp_sender.reliable import AckRange

        # Valid
        AckRange(10, 20)
        AckRange(10, 10)

        # Invalid
        with pytest.raises(ValueError):
            AckRange(20, 10)


class TestAckFrame:
    """Tests for AckFrame class."""

    def test_ack_frame_single_range_serialization(self) -> None:
        """AckFrame with single range serializes correctly."""
        from udp_sender.reliable import AckFrame, AckRange

        frame = AckFrame(
            largest_acked=100, ack_delay_micros=500, ranges=[AckRange(90, 100)]
        )
        data = frame.to_bytes()

        # Header: 4 (largest) + 2 (delay) + 1 (count) = 7
        # Range: 2 (gap) + 2 (ack_count) = 4
        assert len(data) == 11

        # Parse manually
        largest = struct.unpack(">I", data[0:4])[0]
        delay = struct.unpack(">H", data[4:6])[0]
        count = data[6]

        assert largest == 100
        assert delay == 500
        assert count == 1

    def test_ack_frame_roundtrip(self) -> None:
        """AckFrame serialization/deserialization roundtrips."""
        from udp_sender.reliable import AckFrame, AckRange

        original = AckFrame(
            largest_acked=100, ack_delay_micros=1000, ranges=[AckRange(90, 100)]
        )
        data = original.to_bytes()
        parsed = AckFrame.from_bytes(data)

        assert parsed is not None
        assert parsed.largest_acked == 100
        assert parsed.ack_delay_micros == 1000
        assert len(parsed.ranges) == 1
        assert parsed.ranges[0].smallest == 90
        assert parsed.ranges[0].largest == 100

    def test_ack_frame_multiple_ranges(self) -> None:
        """AckFrame handles multiple ranges with gaps."""
        from udp_sender.reliable import AckFrame, AckRange

        # Ranges: 90-100, 70-80 (gap of 9 at 81-89)
        original = AckFrame(
            largest_acked=100,
            ack_delay_micros=0,
            ranges=[AckRange(90, 100), AckRange(70, 80)],
        )
        data = original.to_bytes()
        parsed = AckFrame.from_bytes(data)

        assert parsed is not None
        assert len(parsed.ranges) == 2
        assert parsed.ranges[0].smallest == 90
        assert parsed.ranges[0].largest == 100
        assert parsed.ranges[1].smallest == 70
        assert parsed.ranges[1].largest == 80

    def test_ack_frame_from_packet_numbers(self) -> None:
        """AckFrame.from_packet_numbers coalesces into ranges."""
        from udp_sender.reliable import AckFrame

        # Numbers: 1,2,3,5,6,7,10 -> ranges [5-7], [1-3], missing 4,8,9
        # Wait, we want descending order, so [10], [5-7], [1-3]
        frame = AckFrame.from_packet_numbers([1, 2, 3, 5, 6, 7, 10])

        assert frame is not None
        assert frame.largest_acked == 10
        assert len(frame.ranges) == 3

        # Ranges should be descending by largest
        assert frame.ranges[0].largest == 10
        assert frame.ranges[0].smallest == 10
        assert frame.ranges[1].largest == 7
        assert frame.ranges[1].smallest == 5
        assert frame.ranges[2].largest == 3
        assert frame.ranges[2].smallest == 1

    def test_ack_frame_from_packet_numbers_empty(self) -> None:
        """AckFrame.from_packet_numbers returns None for empty list."""
        from udp_sender.reliable import AckFrame

        assert AckFrame.from_packet_numbers([]) is None

    def test_ack_frame_contains(self) -> None:
        """AckFrame.contains checks if sequence number is acked."""
        from udp_sender.reliable import AckFrame, AckRange

        frame = AckFrame(
            largest_acked=100,
            ack_delay_micros=0,
            ranges=[AckRange(90, 100), AckRange(70, 80)],
        )

        assert frame.contains(95)
        assert frame.contains(75)
        assert not frame.contains(85)  # In gap
        assert not frame.contains(50)  # Before all ranges


class TestMessageFragmentation:
    """Tests for message fragmentation."""

    def test_fragment_small_message(self) -> None:
        """Small message creates single fragment."""
        from udp_sender.reliable import fragment_message

        payload = b"Hello"
        fragments = fragment_message(payload, message_id=1, max_payload_size=1400)

        assert len(fragments) == 1
        header, data = fragments[0]
        assert header.fragment_index == 1
        assert header.fragment_total == 1
        assert data == payload

    def test_fragment_exact_size(self) -> None:
        """Message exactly at max size creates single fragment."""
        from udp_sender.reliable import fragment_message

        payload = b"x" * 1400
        fragments = fragment_message(payload, message_id=1, max_payload_size=1400)

        assert len(fragments) == 1

    def test_fragment_large_message(self) -> None:
        """Large message creates multiple fragments."""
        from udp_sender.reliable import fragment_message

        payload = b"x" * 3000
        fragments = fragment_message(payload, message_id=1, max_payload_size=1400)

        assert len(fragments) == 3  # ceil(3000/1400) = 3
        assert fragments[0][0].fragment_total == 3
        assert fragments[1][0].fragment_total == 3
        assert fragments[2][0].fragment_total == 3

        # Fragment indices are 1-based
        assert fragments[0][0].fragment_index == 1
        assert fragments[1][0].fragment_index == 2
        assert fragments[2][0].fragment_index == 3

        # Reassembled should match original
        reassembled = b"".join(f[1] for f in fragments)
        assert reassembled == payload

    def test_fragment_message_ids_sequential(self) -> None:
        """Each fragment shares the same message_id."""
        from udp_sender.reliable import fragment_message

        payload = b"x" * 3000
        fragments = fragment_message(payload, message_id=42, max_payload_size=1400)

        for header, _ in fragments:
            assert header.message_id == 42

    def test_fragment_empty_message(self) -> None:
        """Empty message returns empty list."""
        from udp_sender.reliable import fragment_message

        fragments = fragment_message(b"", message_id=1, max_payload_size=1400)
        assert fragments == []

    def test_fragment_max_message_size(self) -> None:
        """64KB message fragments correctly into max fragments."""
        from udp_sender.reliable import MAX_MESSAGE_SIZE, fragment_message

        payload = b"x" * MAX_MESSAGE_SIZE  # 64KB
        fragments = fragment_message(payload, message_id=1, max_payload_size=1400)

        # ceil(65536 / 1400) = 47 fragments
        assert len(fragments) == 47
        assert all(h.fragment_total == 47 for h, _ in fragments)

        # Verify reassembly
        reassembled = b"".join(f[1] for f in fragments)
        assert reassembled == payload


class TestReliableSender:
    """Tests for ReliableSender class."""

    def test_reliable_sender_init(self) -> None:
        """ReliableSender initializes with host and port."""
        from udp_sender.reliable import ReliableSender

        sender = ReliableSender("192.168.1.1", 5000)
        assert sender.host == "192.168.1.1"
        assert sender.port == 5000

    @patch("udp_sender.reliable.socket.socket")
    def test_send_creates_reliable_packet(self, mock_socket_class: MagicMock) -> None:
        """send() creates packet with reliable header."""
        from udp_sender.reliable import HEADER_SIZE, Header, ReliableSender

        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        sender = ReliableSender("localhost", 5000)
        sender.send(b"Hello")

        # Verify packet was sent
        assert mock_socket.sendto.called
        sent_data = mock_socket.sendto.call_args[0][0]

        # Should have header + payload
        assert len(sent_data) == HEADER_SIZE + 5

        # Parse header
        header = Header.from_bytes(sent_data)
        assert header is not None
        from udp_sender.reliable import PacketType

        assert header.packet_type == PacketType.DATA
        assert header.fragment_index == 1
        assert header.fragment_total == 1

        # Verify payload
        assert sent_data[HEADER_SIZE:] == b"Hello"

    @patch("udp_sender.reliable.socket.socket")
    def test_send_increments_sequence_number(
        self, mock_socket_class: MagicMock
    ) -> None:
        """Each send increments sequence number."""
        from udp_sender.reliable import Header, ReliableSender

        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        sender = ReliableSender("localhost", 5000)
        sender.send(b"First")
        sender.send(b"Second")

        calls = mock_socket.sendto.call_args_list
        header1 = Header.from_bytes(calls[0][0][0])
        header2 = Header.from_bytes(calls[1][0][0])

        assert header1 is not None and header2 is not None
        assert header2.sequence_number == header1.sequence_number + 1

    @patch("udp_sender.reliable.socket.socket")
    def test_send_increments_message_id(self, mock_socket_class: MagicMock) -> None:
        """Each send uses unique message ID."""
        from udp_sender.reliable import Header, ReliableSender

        mock_socket = MagicMock()
        mock_socket_class.return_value.__enter__ = MagicMock(return_value=mock_socket)
        mock_socket_class.return_value.__exit__ = MagicMock(return_value=False)

        sender = ReliableSender("localhost", 5000)
        sender.send(b"First")
        sender.send(b"Second")

        calls = mock_socket.sendto.call_args_list
        header1 = Header.from_bytes(calls[0][0][0])
        header2 = Header.from_bytes(calls[1][0][0])

        assert header1 is not None and header2 is not None
        assert header2.message_id == header1.message_id + 1

    def test_send_rejects_oversized_payload(self) -> None:
        """send() raises ValueError for payloads exceeding MAX_MESSAGE_SIZE."""
        from udp_sender.reliable import MAX_MESSAGE_SIZE, ReliableSender

        sender = ReliableSender("localhost", 5000)
        oversized = b"x" * (MAX_MESSAGE_SIZE + 1)

        with pytest.raises(ValueError, match="exceeds maximum"):
            sender.send(oversized)
