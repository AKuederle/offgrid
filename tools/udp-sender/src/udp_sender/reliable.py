"""Reliable UDP protocol implementation.

Matches the Kotlin reliable-udp module wire format.

Wire format (11-byte header, big-endian):
    - Type (1 byte): DATA=0x01, ACK=0x02, PING=0x03
    - MessageID (4 bytes): Unique identifier for logical message
    - SequenceNumber (4 bytes): Strictly increasing packet number
    - FragmentIndex (1 byte): 1-based fragment position
    - FragmentTotal (1 byte): Total fragments in message

ACK Frame format:
    - LargestAcked (4 bytes): Highest acked sequence number
    - AckDelay (2 bytes): Microseconds since largest was received
    - RangeCount (1 byte): Number of ACK ranges
    - Ranges (4 bytes each): Gap(2) + AckCount(2)
"""

import socket
import struct
from dataclasses import dataclass
from enum import IntEnum
from typing import Optional

# Protocol constants matching Kotlin ReliableUdpConstants
HEADER_SIZE = 11  # Type(1) + MsgID(4) + SeqNum(4) + FragIdx(1) + FragTotal(1)
MAX_PAYLOAD_SIZE = 1400  # Typical Ethernet MTU (1500) minus IP/UDP headers
MAX_PACKET_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE
MAX_MESSAGE_SIZE = 64 * 1024  # 64KB limit per spec


class PacketType(IntEnum):
    """Packet type identifier for reliable UDP protocol."""

    DATA = 0x01
    ACK = 0x02
    PING = 0x03

    @classmethod
    def from_byte(cls, value: int) -> Optional["PacketType"]:
        """Parse packet type from wire format byte."""
        try:
            return cls(value)
        except ValueError:
            return None


@dataclass
class Header:
    """Reliable UDP packet header.

    Wire format (11 bytes, big-endian):
        Type(1) + MsgID(4) + SeqNum(4) + FragIdx(1) + FragTotal(1)
    """

    packet_type: PacketType
    message_id: int
    sequence_number: int
    fragment_index: int
    fragment_total: int

    def __post_init__(self) -> None:
        """Validate header fields."""
        if self.fragment_index < 1 or self.fragment_index > 255:
            raise ValueError(f"fragment_index must be 1-255, got {self.fragment_index}")
        if self.fragment_total < 1 or self.fragment_total > 255:
            raise ValueError(f"fragment_total must be 1-255, got {self.fragment_total}")
        if self.fragment_index > self.fragment_total:
            raise ValueError(
                f"fragment_index ({self.fragment_index}) cannot exceed "
                f"fragment_total ({self.fragment_total})"
            )

    def to_bytes(self) -> bytes:
        """Serialize header to wire format bytes (big-endian)."""
        return struct.pack(
            ">BIIBB",  # big-endian: byte, uint, uint, byte, byte = 11 bytes
            self.packet_type.value,
            self.message_id & 0xFFFFFFFF,
            self.sequence_number & 0xFFFFFFFF,
            self.fragment_index,
            self.fragment_total,
        )

    @classmethod
    def from_bytes(cls, data: bytes, offset: int = 0) -> Optional["Header"]:
        """Parse header from wire format bytes.

        Args:
            data: Byte array containing at least HEADER_SIZE bytes.
            offset: Starting position in array.

        Returns:
            Parsed header, or None if invalid.
        """
        if len(data) - offset < HEADER_SIZE:
            return None

        try:
            type_byte, msg_id, seq_num, frag_idx, frag_total = struct.unpack_from(
                ">BIIBB", data, offset
            )

            packet_type = PacketType.from_byte(type_byte)
            if packet_type is None:
                return None

            if frag_idx < 1 or frag_total < 1 or frag_idx > frag_total:
                return None

            return cls(
                packet_type=packet_type,
                message_id=msg_id,
                sequence_number=seq_num,
                fragment_index=frag_idx,
                fragment_total=frag_total,
            )
        except struct.error:
            return None


@dataclass
class AckRange:
    """Contiguous range of acknowledged packet sequence numbers."""

    smallest: int
    largest: int

    def __post_init__(self) -> None:
        """Validate range."""
        if self.smallest > self.largest:
            raise ValueError(
                f"Invalid range: smallest ({self.smallest}) > largest ({self.largest})"
            )

    def contains(self, sequence_number: int) -> bool:
        """Check if a sequence number is within this range."""
        return self.smallest <= sequence_number <= self.largest

    @property
    def size(self) -> int:
        """Number of sequence numbers in this range."""
        return self.largest - self.smallest + 1


@dataclass
class AckFrame:
    """Selective ACK (SACK) frame for acknowledging received packets.

    Wire format:
        LargestAcked(4) + Delay(2) + Count(1) + Ranges(4 each)

    Each Range: Gap(2) + AckCount(2)
    """

    largest_acked: int
    ack_delay_micros: int
    ranges: list[AckRange]

    def __post_init__(self) -> None:
        """Validate ACK frame."""
        if not self.ranges:
            raise ValueError("ACK frame must have at least one range")
        if self.ranges[0].largest != self.largest_acked:
            raise ValueError(
                f"First range largest ({self.ranges[0].largest}) "
                f"must equal largest_acked ({self.largest_acked})"
            )

    def contains(self, sequence_number: int) -> bool:
        """Check if a sequence number is acknowledged by this frame."""
        return any(r.contains(sequence_number) for r in self.ranges)

    def to_bytes(self) -> bytes:
        """Serialize to wire format bytes."""
        # Header: 4 (largest) + 2 (delay) + 1 (count) = 7 bytes
        header = struct.pack(
            ">IHB",
            self.largest_acked & 0xFFFFFFFF,
            self.ack_delay_micros & 0xFFFF,
            len(self.ranges),
        )

        # Encode ranges from largest to smallest
        range_bytes = b""
        previous_smallest = self.largest_acked + 1
        for r in self.ranges:
            # Gap: packets between previous range's smallest and this range's largest
            gap = previous_smallest - r.largest - 1
            # AckCount: number of packets in this range minus 1
            ack_count = r.size - 1
            range_bytes += struct.pack(">HH", gap & 0xFFFF, ack_count & 0xFFFF)
            previous_smallest = r.smallest

        return header + range_bytes

    @classmethod
    def from_bytes(cls, data: bytes, offset: int = 0) -> Optional["AckFrame"]:
        """Parse ACK frame from wire format bytes."""
        if len(data) - offset < 7:
            return None

        try:
            largest, delay, count = struct.unpack_from(">IHB", data, offset)
            if count == 0:
                return None
            if len(data) - offset < 7 + (count * 4):
                return None

            ranges: list[AckRange] = []
            current_largest = largest
            pos = offset + 7

            for i in range(count):
                gap, ack_count = struct.unpack_from(">HH", data, pos)
                pos += 4

                # First range: no gap before it
                if i == 0:
                    range_largest = current_largest
                else:
                    range_largest = current_largest - gap - 1

                range_smallest = range_largest - ack_count

                if range_smallest < 0:
                    return None

                ranges.append(AckRange(range_smallest, range_largest))
                current_largest = range_smallest

            return cls(
                largest_acked=largest,
                ack_delay_micros=delay,
                ranges=ranges,
            )
        except struct.error:
            return None

    @classmethod
    def from_packet_numbers(
        cls, packet_numbers: list[int], ack_delay_micros: int = 0
    ) -> Optional["AckFrame"]:
        """Build ACK frame from a collection of received packet sequence numbers.

        Coalesces individual packet numbers into contiguous ranges.
        """
        if not packet_numbers:
            return None

        sorted_nums = sorted(packet_numbers)
        ranges: list[AckRange] = []

        range_start = sorted_nums[0]
        range_end = range_start

        for pn in sorted_nums[1:]:
            if pn == range_end + 1:
                # Extend current range
                range_end = pn
            else:
                # Gap detected - finalize current range and start new one
                ranges.append(AckRange(range_start, range_end))
                range_start = pn
                range_end = pn

        # Add final range
        ranges.append(AckRange(range_start, range_end))

        # Reverse to get descending order (largest first)
        ranges.reverse()

        return cls(
            largest_acked=sorted_nums[-1],
            ack_delay_micros=ack_delay_micros,
            ranges=ranges,
        )


def fragment_message(
    payload: bytes,
    message_id: int,
    max_payload_size: int = MAX_PAYLOAD_SIZE,
    start_sequence: int = 0,
) -> list[tuple[Header, bytes]]:
    """Fragment a message into packets.

    Args:
        payload: Message payload to fragment.
        message_id: Unique identifier for this message.
        max_payload_size: Maximum payload per fragment.
        start_sequence: Starting sequence number.

    Returns:
        List of (header, payload) tuples for each fragment.
    """
    if not payload:
        return []

    # Calculate number of fragments needed
    fragment_count = (len(payload) + max_payload_size - 1) // max_payload_size

    fragments: list[tuple[Header, bytes]] = []
    for i in range(fragment_count):
        start = i * max_payload_size
        end = min(start + max_payload_size, len(payload))
        frag_payload = payload[start:end]

        header = Header(
            packet_type=PacketType.DATA,
            message_id=message_id,
            sequence_number=start_sequence + i,
            fragment_index=i + 1,  # 1-based
            fragment_total=fragment_count,
        )
        fragments.append((header, frag_payload))

    return fragments


class ReliableSender:
    """Reliable UDP sender with header framing.

    This is a basic sender that adds reliable protocol headers.
    It does not implement retransmission or ACK handling (fire-and-forget).
    """

    def __init__(self, host: str, port: int) -> None:
        """Initialize ReliableSender with target host and port."""
        self.host = host
        self.port = port
        # Counters wrap at 2^32 due to wire format masking (& 0xFFFFFFFF)
        self._sequence_number = 0
        self._message_id = 0

    def send(self, payload: bytes) -> int:
        """Send a message with reliable protocol headers.

        Args:
            payload: The message payload to send.

        Returns:
            The message ID assigned to this message.

        Raises:
            ValueError: If payload exceeds MAX_MESSAGE_SIZE (64KB).
        """
        if len(payload) > MAX_MESSAGE_SIZE:
            raise ValueError(
                f"Payload size {len(payload)} exceeds maximum {MAX_MESSAGE_SIZE}"
            )

        message_id = self._message_id
        self._message_id += 1

        fragments = fragment_message(
            payload,
            message_id=message_id,
            start_sequence=self._sequence_number,
        )

        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            for header, frag_payload in fragments:
                packet = header.to_bytes() + frag_payload
                sock.sendto(packet, (self.host, self.port))
                self._sequence_number += 1

        return message_id

    def send_string(self, message: str) -> int:
        """Send a string message (UTF-8 encoded)."""
        return self.send(message.encode("utf-8"))
