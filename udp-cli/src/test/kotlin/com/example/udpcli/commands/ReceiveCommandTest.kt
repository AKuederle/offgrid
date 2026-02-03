@file:OptIn(ExperimentalCli::class)

package com.example.udpcli.commands

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ReceiveCommand")
class ReceiveCommandTest {

    @Nested
    @DisplayName("argument parsing")
    inner class ArgumentParsing {

        @Test
        @DisplayName("accepts default arguments")
        fun `accepts default arguments`() {
            class TestCmd : Subcommand("receive", "Test") {
                val port by option(ArgType.Int, shortName = "p").default(5000)
                override fun execute() {}
            }

            val cmd = TestCmd()
            val parser = ArgParser("test")
            parser.subcommands(cmd)

            parser.parse(arrayOf("receive"))

            assertEquals(5000, cmd.port)
        }

        @Test
        @DisplayName("parses port argument")
        fun `parses port argument`() {
            class TestCmd : Subcommand("receive", "Test") {
                val port by option(ArgType.Int, shortName = "p").default(5000)
                override fun execute() {}
            }

            val cmd = TestCmd()
            val parser = ArgParser("test")
            parser.subcommands(cmd)

            parser.parse(arrayOf("receive", "-p", "8080"))

            assertEquals(8080, cmd.port)
        }

        @Test
        @DisplayName("parses appId filter")
        fun `parses appId filter`() {
            class TestCmd : Subcommand("receive", "Test") {
                val appId by option(ArgType.String, shortName = "a")
                override fun execute() {}
            }

            val cmd = TestCmd()
            val parser = ArgParser("test")
            parser.subcommands(cmd)

            parser.parse(arrayOf("receive", "-a", "broker"))

            assertEquals("broker", cmd.appId)
        }
    }
}
