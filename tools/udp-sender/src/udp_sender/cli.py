"""Command-line interface for UDP sender tool."""

import sys

import click

from udp_sender.sender import UdpSender


@click.group(invoke_without_command=True)
@click.pass_context
def main(ctx: click.Context) -> None:
    """UDP sender tool for testing Android UDP service."""
    if ctx.invoked_subcommand is None:
        click.echo(ctx.get_help())


@main.command()
@click.option("-h", "--host", required=True, help="Target hostname or IP address")
@click.option("-p", "--port", default=5000, type=int, help="Target UDP port")
@click.option("-m", "--message", required=True, help="Message to send")
def send(host: str, port: int, message: str) -> None:
    """Send a single UDP packet."""
    sender = UdpSender(host, port)
    sender.send(message)
    click.echo(f"Sent message to {host}:{port}")


@main.command()
@click.option("-h", "--host", required=True, help="Target hostname or IP address")
@click.option("-p", "--port", default=5000, type=int, help="Target UDP port")
@click.option("-r", "--rate", default=100, type=int, help="Packets per second")
@click.option("-d", "--duration", default=10, type=int, help="Duration in seconds")
def flood(host: str, port: int, rate: int, duration: int) -> None:
    """Send packets at rate for duration."""
    sender = UdpSender(host, port)
    click.echo(f"Flooding {host}:{port} at {rate} pps for {duration}s...")
    count = sender.flood(rate=rate, duration=duration)
    click.echo(f"Sent {count} packets")


@main.command()
@click.option("-h", "--host", required=True, help="Target hostname or IP address")
@click.option("-p", "--port", default=5000, type=int, help="Target UDP port")
def interactive(host: str, port: int) -> None:
    """Interactive mode - send each line of input as a packet."""
    sender = UdpSender(host, port)
    click.echo(f"Interactive mode: sending to {host}:{port}")
    click.echo("Enter messages (Ctrl+D to exit):")

    for line in sys.stdin:
        message = line.rstrip("\n")
        if message:
            sender.send(message)


if __name__ == "__main__":
    main()
