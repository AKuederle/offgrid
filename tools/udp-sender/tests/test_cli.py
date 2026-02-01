"""Tests for CLI commands."""

from unittest.mock import MagicMock, patch

from click.testing import CliRunner

from udp_sender.cli import main


class TestSendCommand:
    """Tests for the send command."""

    def test_send_requires_host(self) -> None:
        """send command requires --host argument."""
        runner = CliRunner()
        result = runner.invoke(main, ["send", "-m", "test"])
        assert result.exit_code != 0
        assert "host" in result.output.lower() or "required" in result.output.lower()

    def test_send_requires_message(self) -> None:
        """send command requires --message argument."""
        runner = CliRunner()
        result = runner.invoke(main, ["send", "-h", "localhost"])
        assert result.exit_code != 0

    @patch("udp_sender.cli.UdpSender")
    def test_send_uses_default_port(self, mock_sender_class: MagicMock) -> None:
        """send command uses default port 5000."""
        mock_sender = MagicMock()
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(main, ["send", "-h", "localhost", "-m", "test"])

        assert result.exit_code == 0
        mock_sender_class.assert_called_once_with("localhost", 5000, app_id=None)

    @patch("udp_sender.cli.UdpSender")
    def test_send_accepts_custom_port(self, mock_sender_class: MagicMock) -> None:
        """send command accepts --port argument."""
        mock_sender = MagicMock()
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(
            main, ["send", "-h", "localhost", "-p", "9999", "-m", "test"]
        )

        assert result.exit_code == 0
        mock_sender_class.assert_called_once_with("localhost", 9999, app_id=None)

    @patch("udp_sender.cli.UdpSender")
    def test_send_calls_sender_send(self, mock_sender_class: MagicMock) -> None:
        """send command calls UdpSender.send() with message."""
        mock_sender = MagicMock()
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(main, ["send", "-h", "localhost", "-m", "hello world"])

        assert result.exit_code == 0
        mock_sender.send.assert_called_once_with("hello world")

    @patch("udp_sender.cli.UdpSender")
    def test_send_long_options(self, mock_sender_class: MagicMock) -> None:
        """send command accepts long option names."""
        mock_sender = MagicMock()
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(
            main,
            ["send", "--host", "192.168.1.1", "--port", "8080", "--message", "test"],
        )

        assert result.exit_code == 0
        mock_sender_class.assert_called_once_with("192.168.1.1", 8080, app_id=None)
        mock_sender.send.assert_called_once_with("test")


class TestFloodCommand:
    """Tests for the flood command."""

    def test_flood_requires_host(self) -> None:
        """flood command requires --host argument."""
        runner = CliRunner()
        result = runner.invoke(main, ["flood"])
        assert result.exit_code != 0

    @patch("udp_sender.cli.UdpSender")
    def test_flood_uses_default_port(self, mock_sender_class: MagicMock) -> None:
        """flood command uses default port 5000."""
        mock_sender = MagicMock()
        mock_sender.flood.return_value = 100
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(main, ["flood", "-h", "localhost"])

        assert result.exit_code == 0
        mock_sender_class.assert_called_once_with("localhost", 5000, app_id=None)

    @patch("udp_sender.cli.UdpSender")
    def test_flood_uses_default_rate_and_duration(
        self, mock_sender_class: MagicMock
    ) -> None:
        """flood command uses default rate=100 and duration=10."""
        mock_sender = MagicMock()
        mock_sender.flood.return_value = 1000
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(main, ["flood", "-h", "localhost"])

        assert result.exit_code == 0
        mock_sender.flood.assert_called_once_with(rate=100, duration=10)

    @patch("udp_sender.cli.UdpSender")
    def test_flood_accepts_custom_rate(self, mock_sender_class: MagicMock) -> None:
        """flood command accepts --rate argument."""
        mock_sender = MagicMock()
        mock_sender.flood.return_value = 500
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(main, ["flood", "-h", "localhost", "-r", "50"])

        assert result.exit_code == 0
        mock_sender.flood.assert_called_once_with(rate=50, duration=10)

    @patch("udp_sender.cli.UdpSender")
    def test_flood_accepts_custom_duration(self, mock_sender_class: MagicMock) -> None:
        """flood command accepts --duration argument."""
        mock_sender = MagicMock()
        mock_sender.flood.return_value = 3000
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(main, ["flood", "-h", "localhost", "-d", "30"])

        assert result.exit_code == 0
        mock_sender.flood.assert_called_once_with(rate=100, duration=30)

    @patch("udp_sender.cli.UdpSender")
    def test_flood_long_options(self, mock_sender_class: MagicMock) -> None:
        """flood command accepts long option names."""
        mock_sender = MagicMock()
        mock_sender.flood.return_value = 250
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(
            main,
            [
                "flood",
                "--host",
                "192.168.1.1",
                "--port",
                "8080",
                "--rate",
                "50",
                "--duration",
                "5",
            ],
        )

        assert result.exit_code == 0
        mock_sender_class.assert_called_once_with("192.168.1.1", 8080, app_id=None)
        mock_sender.flood.assert_called_once_with(rate=50, duration=5)

    @patch("udp_sender.cli.UdpSender")
    def test_flood_displays_packet_count(self, mock_sender_class: MagicMock) -> None:
        """flood command displays the number of packets sent."""
        mock_sender = MagicMock()
        mock_sender.flood.return_value = 1234
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(main, ["flood", "-h", "localhost"])

        assert result.exit_code == 0
        assert "1234" in result.output


class TestInteractiveCommand:
    """Tests for the interactive command."""

    def test_interactive_requires_host(self) -> None:
        """interactive command requires --host argument."""
        runner = CliRunner()
        result = runner.invoke(main, ["interactive"])
        assert result.exit_code != 0

    @patch("udp_sender.cli.UdpSender")
    def test_interactive_uses_default_port(self, mock_sender_class: MagicMock) -> None:
        """interactive command uses default port 5000."""
        mock_sender = MagicMock()
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        # Simulate immediate EOF (empty input)
        result = runner.invoke(main, ["interactive", "-h", "localhost"], input="")

        assert result.exit_code == 0
        mock_sender_class.assert_called_once_with("localhost", 5000, app_id=None)

    @patch("udp_sender.cli.UdpSender")
    def test_interactive_sends_input_lines(self, mock_sender_class: MagicMock) -> None:
        """interactive command sends each input line."""
        mock_sender = MagicMock()
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(
            main,
            ["interactive", "-h", "localhost"],
            input="line1\nline2\nline3\n",
        )

        assert result.exit_code == 0
        assert mock_sender.send.call_count == 3
        mock_sender.send.assert_any_call("line1")
        mock_sender.send.assert_any_call("line2")
        mock_sender.send.assert_any_call("line3")

    @patch("udp_sender.cli.UdpSender")
    def test_interactive_long_options(self, mock_sender_class: MagicMock) -> None:
        """interactive command accepts long option names."""
        mock_sender = MagicMock()
        mock_sender_class.return_value = mock_sender

        runner = CliRunner()
        result = runner.invoke(
            main,
            ["interactive", "--host", "192.168.1.1", "--port", "8080"],
            input="test\n",
        )

        assert result.exit_code == 0
        mock_sender_class.assert_called_once_with("192.168.1.1", 8080, app_id=None)


class TestMainGroup:
    """Tests for the main CLI group."""

    def test_main_shows_help(self) -> None:
        """Main group shows help with available commands."""
        runner = CliRunner()
        result = runner.invoke(main, ["--help"])

        assert result.exit_code == 0
        assert "send" in result.output
        assert "flood" in result.output
        assert "interactive" in result.output

    def test_main_without_command_shows_help(self) -> None:
        """Main group without command shows help."""
        runner = CliRunner()
        result = runner.invoke(main, [])

        # Should either show help or exit with specific code
        assert "send" in result.output or result.exit_code == 0
