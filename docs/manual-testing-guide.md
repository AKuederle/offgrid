# Manual Testing Guide: UDP Background Reception

This guide explains how to manually test that the UDP service receives messages in the background.

## Prerequisites

1. Android device or emulator running
2. USB debugging enabled (Settings > Developer Options > USB Debugging)
3. Device connected via USB or emulator running

## Install the App

### Connect Device

```bash
# Verify device is connected
adb devices
```

You should see your device listed:
```
List of devices attached
ABC123XYZ    device
```

### Build and Install

```bash
# Build and install in one step
./gradlew :app:installDebug

# Or build first, then install
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Grant Permissions

On Android 13+, you may need to grant notification permission when prompted, or manually:
- Settings > Apps > UDP Broker > Notifications > Enable

## Testing

### 1. Start the Service

1. Open the UDP Broker app
2. Tap "Start" to start the UDP receiver service
3. Note the IP address shown (e.g., `192.168.1.100`)
4. The service is now listening on port `5000`

### 2. Send a Test Packet

Packets must include an **appId prefix** in this format:
```
[length byte][appId bytes][payload bytes]
```

For the UDP Broker app, the appId is `broker` (6 bytes).

#### Option A: Python Script (Recommended)

```python
#!/usr/bin/env python3
"""Send a test packet to the UDP Broker app."""
import socket

def send_packet(host: str, port: int, app_id: str, message: str):
    """Send a packet with appId prefix."""
    app_id_bytes = app_id.encode('utf-8')
    payload = message.encode('utf-8')

    # Format: [length][appId][payload]
    packet = bytes([len(app_id_bytes)]) + app_id_bytes + payload

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.sendto(packet, (host, port))
    sock.close()
    print(f"Sent: appId='{app_id}', message='{message}'")

# Usage - replace with your device IP
send_packet("192.168.1.100", 5000, "broker", "Hello from Python!")
```

Save as `send_test.py` and run:
```bash
python3 send_test.py
```

#### Option B: One-liner with Python

```bash
python3 -c "
import socket
host, port, app_id, msg = '192.168.1.100', 5000, 'broker', 'Hello!'
data = bytes([len(app_id)]) + app_id.encode() + msg.encode()
socket.socket(socket.AF_INET, socket.SOCK_DGRAM).sendto(data, (host, port))
print('Sent!')
"
```

#### Option C: Using netcat with hex

```bash
# broker = 0x62 0x72 0x6f 0x6b 0x65 0x72 (6 bytes)
# Prefix: 0x06 (length=6)
# Message: "Hi" = 0x48 0x69

printf '\x06broker%s' "Hello from netcat!" | nc -u -w1 192.168.1.100 5000
```

### 3. Verify Reception

1. Check the app - the packet should appear in the "Received Packets" list
2. Tap on the packet to see details (hex dump, source IP, timestamp)

## Testing Background Reception

### Test: App in Background

1. Start the service
2. Press Home to put app in background
3. Send packets using any method above
4. Return to app - packets should be in the list

### Test: App Killed

1. Start the service (notification should appear)
2. Force close the app (swipe away or Settings > Apps > Force Stop)
3. The notification should still be visible (service running)
4. Send packets
5. Reopen the app - packets should be persisted and visible

### Test: Device Sleep

1. Start the service
2. Lock the device / let screen turn off
3. Send packets from your computer
4. Unlock device and open app - packets should be present

## Emulator Testing

For emulator, use the special IP `10.0.2.2` which routes to your host machine:

```bash
# From host machine, send to emulator
# First, forward the port:
adb forward tcp:5000 tcp:5000

# Then send to localhost (forwards to emulator)
python3 -c "
import socket
data = bytes([6]) + b'broker' + b'Hello emulator!'
socket.socket(socket.AF_INET, socket.SOCK_DGRAM).sendto(data, ('127.0.0.1', 5000))
"
```

Or send directly to emulator's virtual network:
```bash
# The emulator listens on 10.0.2.15 (or similar)
# Send from inside the emulator using adb shell
adb shell
# Then use a UDP tool inside the emulator
```

## Packet Format Reference

| Byte(s) | Content | Example |
|---------|---------|---------|
| 0 | AppId length (1-255) | `0x06` (6) |
| 1..N | AppId (UTF-8) | `broker` |
| N+1.. | Payload | `Hello!` |

**Example packet** for `appId="broker"`, `payload="Hi"`:
```
Hex: 06 62 72 6F 6B 65 72 48 69
     ^  ^-----------------^ ^--^
     |         |              |
  length    "broker"        "Hi"
```

## Troubleshooting

### Packet not appearing

1. **Check appId**: Must be exactly `broker` for the UDP Broker app
2. **Check format**: First byte must be the length of appId
3. **Check IP**: Use the IP shown in the app, not `localhost`
4. **Check firewall**: Ensure UDP port 5000 is not blocked
5. **Check service**: Notification should be visible when running

### Service stops unexpectedly

- Check battery optimization settings for the app
- Ensure the app has background activity permission

### Logcat debugging

```bash
adb logcat -s UdpSocket:D UdpReceiverService:D
```

This shows:
- `Received packet: X bytes` - packet received
- `Dropped packet: invalid appId prefix` - wrong format
- `Dropped packet: unregistered appId` - wrong appId
