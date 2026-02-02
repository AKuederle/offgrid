"""Command-line interface for UDP sender tool."""

import sys

import click

from udp_sender.reliable import ReliableSender
from udp_sender.sender import encode_packet


@click.group(invoke_without_command=True)
@click.pass_context
def main(ctx: click.Context) -> None:
    """UDP sender tool for testing Android UDP service.

    All commands use the reliable UDP protocol with automatic
    retransmission and fragmentation support.
    """
    if ctx.invoked_subcommand is None:
        click.echo(ctx.get_help())


@main.command()
@click.option("-h", "--host", required=True, help="Target hostname or IP address")
@click.option("-p", "--port", default=5000, type=int, help="Target UDP port")
@click.option("-a", "--app-id", default=None, help="App ID prefix (e.g., 'broker')")
@click.option("-m", "--message", required=True, help="Message to send")
def send(host: str, port: int, app_id: str | None, message: str) -> None:
    """Send a single message using reliable UDP protocol."""
    payload = message.encode("utf-8")
    if app_id:
        payload = encode_packet(app_id, payload)

    sender = ReliableSender(host, port)
    msg_id = sender.send(payload)

    prefix = f" (appId={app_id})" if app_id else ""
    click.echo(f"Sent message (id={msg_id}) to {host}:{port}{prefix}")


@main.command()
@click.option("-h", "--host", required=True, help="Target hostname or IP address")
@click.option("-p", "--port", default=5000, type=int, help="Target UDP port")
@click.option("-a", "--app-id", default=None, help="App ID prefix (e.g., 'broker')")
@click.option("-r", "--rate", default=100, type=int, help="Messages per second")
@click.option("-d", "--duration", default=10, type=int, help="Duration in seconds")
def flood(host: str, port: int, app_id: str | None, rate: int, duration: int) -> None:
    """Send messages at rate for duration using reliable UDP protocol."""
    import time

    sender = ReliableSender(host, port)
    prefix = f" (appId={app_id})" if app_id else ""
    click.echo(f"Flooding {host}:{port}{prefix} at {rate} mps for {duration}s...")

    interval = 1.0 / rate
    count = 0
    start_time = time.monotonic()
    end_time = start_time + duration

    while time.monotonic() < end_time:
        payload = f"flood-{count}".encode("utf-8")
        if app_id:
            payload = encode_packet(app_id, payload)
        sender.send(payload)
        count += 1
        # Simple rate limiting
        next_send = start_time + (count * interval)
        sleep_time = next_send - time.monotonic()
        if sleep_time > 0:
            time.sleep(sleep_time)

    click.echo(f"Sent {count} messages")


@main.command()
@click.option("-h", "--host", required=True, help="Target hostname or IP address")
@click.option("-p", "--port", default=5000, type=int, help="Target UDP port")
@click.option("-a", "--app-id", default=None, help="App ID prefix (e.g., 'broker')")
def interactive(host: str, port: int, app_id: str | None) -> None:
    """Interactive mode - send each line of input as a message."""
    sender = ReliableSender(host, port)
    prefix = f" (appId={app_id})" if app_id else ""
    click.echo(f"Interactive mode: sending to {host}:{port}{prefix}")
    click.echo("Enter messages (Ctrl+D to exit):")

    for line in sys.stdin:
        message = line.rstrip("\n")
        if message:
            payload = message.encode("utf-8")
            if app_id:
                payload = encode_packet(app_id, payload)
            msg_id = sender.send(payload)
            click.echo(f"  -> sent (id={msg_id})")


if __name__ == "__main__":
    main()
