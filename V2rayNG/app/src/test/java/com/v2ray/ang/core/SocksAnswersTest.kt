package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SocksAnswersTest {

    /** A listener bound and not served, as a core's own is until its tunnel is up: the kernel queues what connects. */
    private fun quietListener() = ServerSocket(0, 50, InetAddress.getLoopbackAddress())

    /** The connections queued on [listener] so far, taken from it once no more come within [waitMs]. */
    private fun takeQueued(listener: ServerSocket, waitMs: Int = 300): List<Socket> {
        val taken = mutableListOf<Socket>()
        listener.soTimeout = waitMs
        while (true) {
            try {
                taken += listener.accept()
            } catch (_: SocketTimeoutException) {
                return taken
            }
        }
    }

    /** Answers the greeting [client] sent, as a SOCKS5 server does. */
    private fun answer(client: Socket) {
        val input = DataInputStream(client.getInputStream())
        input.readByte()
        repeat(input.readUnsignedByte()) { input.readByte() }
        client.getOutputStream().write(byteArrayOf(5, 0))
    }

    @Test
    fun aQuietListenerIsAskedOverOneConnectionUntilItAnswers() {
        val answers = SocksAnswers(waitMs = 100)
        quietListener().use { listener ->
            repeat(3) { assertFalse(answers.answers(listener.localPort)) }
            // One connection waits in the listener's queue, not one for each question.
            val queued = takeQueued(listener)
            assertEquals(1, queued.size)
            // The tunnel comes up and answers it: the next question reads that answer on the same connection.
            queued.single().use { client ->
                answer(client)
                assertTrue(answers.answers(listener.localPort))
            }
            assertEquals(0, takeQueued(listener).size)
        }
    }

    @Test
    fun aConnectionTheServerEndedIsAskedAnew() {
        val answers = SocksAnswers(waitMs = 100)
        quietListener().use { listener ->
            assertFalse(answers.answers(listener.localPort))
            // The core that held it ends it, and the listener is served from then on.
            takeQueued(listener).single().close()
            val server = thread(isDaemon = true) {
                runCatching {
                    listener.soTimeout = 5_000
                    listener.accept().use(::answer)
                }
            }
            assertTrue(answers.answers(listener.localPort))
            server.join(5_000)
        }
    }

    @Test
    fun aServedListenerAnswersAtOnce() {
        val answers = SocksAnswers(waitMs = 1_000)
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { listener ->
            val server = thread(isDaemon = true) { runCatching { listener.accept().use(::answer) } }
            assertTrue(answers.answers(listener.localPort))
            server.join(5_000)
        }
    }

    @Test
    fun aPortNothingListensOnAnswersNothing() {
        val answers = SocksAnswers(waitMs = 100)
        val closedPort = ServerSocket(0).use { it.localPort }
        assertFalse(answers.answers(closedPort))
        assertFalse(answers.answers(closedPort))
    }

    @Test
    fun questionsAskedTogetherShareOneConnection() {
        val answers = SocksAnswers(waitMs = 300)
        quietListener().use { listener ->
            val pool = Executors.newFixedThreadPool(8)
            try {
                val replies = List(8) { pool.submit<Boolean> { answers.answers(listener.localPort) } }
                    .map { it.get(10, TimeUnit.SECONDS) }
                assertTrue(replies.none { it })
            } finally {
                pool.shutdownNow()
            }
            assertEquals(1, takeQueued(listener).size)
        }
    }
}
