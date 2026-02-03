@file:OptIn(ExperimentalCli::class)

package com.example.udpcli

import kotlinx.cli.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * UDP CLI Tool - Kotlin-based CLI for reliable UDP messaging.
 *
 * Commands:
 *   send      Send a message to a peer
 *   receive   Listen for incoming messages
 *   broadcast Send a presence broadcast
 */
fun main(args: Array<String>) {
    val parser = ArgParser("udp-cli")

    // Define subcommands
    val sendCommand = SendCommand()
    val receiveCommand = ReceiveCommand()
    val broadcastCommand = BroadcastCommand()

    parser.subcommands(sendCommand, receiveCommand, broadcastCommand)

    try {
        parser.parse(args)
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
}

/**
 * Send command - sends a message to a peer.
 */
class SendCommand : Subcommand("send", "Send a message to a peer") {

    private val host by option(
        ArgType.String,
        shortName = "h",
        description = "Target host address"
    ).required()

    private val port by option(
        ArgType.Int,
        shortName = "p",
        description = "Target port"
    ).default(5000)

    private val appId by option(
        ArgType.String,
        shortName = "a",
        description = "App ID prefix for message"
    )

    private val message by option(
        ArgType.String,
        shortName = "m",
        description = "Message to send"
    ).required()

    private val timeout by option(
        ArgType.Int,
        shortName = "t",
        description = "Timeout in milliseconds"
    ).default(10000)

    override fun execute() {
        val client = UdpClientImpl()

        try {
            runBlocking {
                // Build payload with optional appId prefix
                val payload = if (appId != null) {
                    "$appId:$message".toByteArray(Charsets.UTF_8)
                } else {
                    message.toByteArray(Charsets.UTF_8)
                }

                println("Sending to $host:$port...")
                val result = client.send(host, port, payload, timeout.toLong())

                when (result) {
                    is CliSendResult.Delivered -> {
                        println("✓ Delivered (messageId=${result.messageId})")
                    }
                    is CliSendResult.Failed -> {
                        println("✗ Failed: ${result.reason}")
                        exitProcess(1)
                    }
                    is CliSendResult.Timeout -> {
                        println("✗ Timeout")
                        exitProcess(1)
                    }
                }
            }
        } finally {
            client.close()
        }
    }
}

/**
 * Receive command - listens for incoming messages.
 */
class ReceiveCommand : Subcommand("receive", "Listen for incoming messages") {

    private val port by option(
        ArgType.Int,
        shortName = "p",
        description = "Port to listen on"
    ).default(5000)

    private val appId by option(
        ArgType.String,
        shortName = "a",
        description = "Filter by app ID prefix"
    )

    private val count by option(
        ArgType.Int,
        shortName = "n",
        description = "Number of messages to receive (0 = infinite)"
    ).default(0)

    override fun execute() {
        val client = UdpClientImpl()

        println("Listening on port $port" + (appId?.let { " (filtering appId=$it)" } ?: "") + "...")
        println("Press Ctrl+C to stop")
        println()

        try {
            runBlocking {
                var received = 0
                client.receive(port).collect { msg ->
                    val text = msg.payload.toString(Charsets.UTF_8)

                    // Parse appId if present
                    val (msgAppId, content) = if (text.contains(":")) {
                        val idx = text.indexOf(":")
                        text.substring(0, idx) to text.substring(idx + 1)
                    } else {
                        null to text
                    }

                    // Filter by appId if specified
                    if (appId != null && msgAppId != appId) {
                        return@collect
                    }

                    val source = "${msg.source.address.hostAddress}:${msg.source.port}"
                    val prefix = msgAppId?.let { "[$it] " } ?: ""
                    println("[$source] $prefix$content")

                    received++
                    if (count > 0 && received >= count) {
                        return@collect
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore cancellation
        } finally {
            client.close()
        }
    }
}

/**
 * Broadcast command - sends a presence broadcast.
 */
class BroadcastCommand : Subcommand("broadcast", "Send a presence broadcast") {

    private val port by option(
        ArgType.Int,
        shortName = "p",
        description = "Port to broadcast on"
    ).default(5000)

    override fun execute() {
        val client = UdpClientImpl()

        try {
            runBlocking {
                println("Broadcasting presence on port $port...")
                val success = client.broadcastPresence(port)

                if (success) {
                    println("✓ Presence broadcast sent")
                } else {
                    println("✗ Failed to send broadcast")
                    exitProcess(1)
                }
            }
        } finally {
            client.close()
        }
    }
}
