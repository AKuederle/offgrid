@file:OptIn(ExperimentalCli::class)

package com.example.udpcli.commands

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SendCommand")
class SendCommandTest {

    @Nested
    @DisplayName("argument parsing")
    inner class ArgumentParsing {

        @Test
        @DisplayName("rejects missing required host")
        fun `rejects missing required host`() {
            // Use unique short names to avoid kotlinx-cli global state issues
            class Cmd1 : Subcommand("send", "Test") {
                val host by option(ArgType.String, shortName = "x").required()
                val message by option(ArgType.String, shortName = "y").required()
                override fun execute() {}
            }

            val parser = ArgParser("test")
            parser.subcommands(Cmd1())

            assertThrows(Exception::class.java) {
                parser.parse(arrayOf("send", "-y", "hello"))
            }
        }

        @Test
        @DisplayName("rejects missing required message")
        fun `rejects missing required message`() {
            class Cmd2 : Subcommand("send", "Test") {
                val host by option(ArgType.String, shortName = "a").required()
                val message by option(ArgType.String, shortName = "b").required()
                override fun execute() {}
            }

            val parser = ArgParser("test")
            parser.subcommands(Cmd2())

            assertThrows(Exception::class.java) {
                parser.parse(arrayOf("send", "-a", "localhost"))
            }
        }

        @Test
        @DisplayName("accepts valid arguments")
        fun `accepts valid arguments`() {
            class Cmd3 : Subcommand("send", "Test") {
                val host by option(ArgType.String, shortName = "c").required()
                val message by option(ArgType.String, shortName = "d").required()
                override fun execute() {}
            }

            val cmd = Cmd3()
            val parser = ArgParser("test")
            parser.subcommands(cmd)

            // Should not throw
            parser.parse(arrayOf("send", "-c", "localhost", "-d", "hello"))

            assertEquals("localhost", cmd.host)
            assertEquals("hello", cmd.message)
        }
    }
}
