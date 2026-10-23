package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * PattNG: whether the SOCKS5 server on a loopback port answers its greeting, asked over at most one connection per port
 * at a time.
 *
 * An Aether core of 2.3.4 or later binds its listener as it starts and serves it only once its tunnel is up. Until then
 * the kernel queues every connection to it, and nothing answers. A probe that hung up after [waitMs] and connected anew
 * at its next try left a dead connection in that queue each time: a few a second over a long Tor or Psiphon warm-up, and
 * one for each profile a "test all" measures beside the session, ahead of the clients the tunnel serves first, in a
 * backlog of 128 on older Android kernels. Here a greeting that got no answer yet is kept waiting, and the next question
 * about that port reads on, on the same connection, while it stays unanswered; questions asked at the same time share one
 * answer. Once the kept connection is answered or ended, a new one tells whether the port is served now: an answer that
 * waited there can be older than the server's end.
 *
 * At most one connection per port stays open while nobody asks.
 */
internal class SocksAnswers(
    private val waitMs: Int,
    private val connectTimeoutMs: Int = waitMs,
) {
    /** A question about one port under way; whoever asks about that port meanwhile takes its answer. */
    private class Question {
        val done = CountDownLatch(1)

        @Volatile
        var answered = false
    }

    private enum class Reply { ANSWERED, WAITING, ENDED }

    private val lock = Any()

    /** The questions under way, by port. Guarded by [lock]. */
    private val asking = HashMap<Int, Question>()

    /** The connections whose greeting got no answer yet, by port. Guarded by [lock]. */
    private val waiting = HashMap<Int, Socket>()

    /**
     * True when the SOCKS5 server on the loopback [port] answers the greeting with its version within [waitMs], on the
     * connection an earlier question left waiting where there is one. Blocks the calling thread.
     */
    fun answers(port: Int): Boolean {
        var mine = false
        val question = synchronized(lock) {
            asking[port] ?: Question().also {
                asking[port] = it
                mine = true
            }
        }
        if (!mine) {
            // The question under way answers this one as well, within the longest it can take.
            val longest = connectTimeoutMs + 2L * waitMs + SHARED_WAIT_SLACK_MS
            return try {
                question.done.await(longest, TimeUnit.MILLISECONDS) && question.answered
            } catch (_: InterruptedException) {
                // An interrupted wait gives up as a timeout does, and leaves the thread marked as interrupted.
                Thread.currentThread().interrupt()
                false
            }
        }
        try {
            question.answered = ask(port)
        } finally {
            synchronized(lock) { asking.remove(port) }
            question.done.countDown()
        }
        return question.answered
    }

    /**
     * Reads on over the connection left waiting for [port] while it stays unanswered, so that no new one queues behind it;
     * asks over a new connection where there is none, or once the kept one was answered or ended. An answer that waited
     * on the kept one can be older than the end of the server that sent it, so only a new connection tells that the port
     * is served now.
     */
    private fun ask(port: Int): Boolean {
        val kept = synchronized(lock) { waiting.remove(port) }
        if (kept != null && read(kept) == Reply.WAITING) return keep(port, kept)
        val socket = greet(port) ?: return false
        return when (read(socket)) {
            Reply.ANSWERED -> true
            Reply.WAITING -> keep(port, socket)
            Reply.ENDED -> false
        }
    }

    /** A connection to [port] with the greeting sent, null when none could be made. */
    private fun greet(port: Int): Socket? {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(AppConfig.LOOPBACK, port), connectTimeoutMs)
            socket.getOutputStream().write(GREETING)
            socket
        } catch (_: IOException) {
            closeQuietly(socket)
            null
        }
    }

    /** Waits at most [waitMs] for the answer on [socket], which stays open only while the answer is still to come. */
    private fun read(socket: Socket): Reply = try {
        socket.soTimeout = waitMs
        val reply = if (socket.getInputStream().read() == SOCKS_VERSION) Reply.ANSWERED else Reply.ENDED
        closeQuietly(socket)
        reply
    } catch (_: SocketTimeoutException) {
        Reply.WAITING
    } catch (_: IOException) {
        closeQuietly(socket)
        Reply.ENDED
    }

    /** Keeps [socket] waiting for its answer, in place of any other for [port]; false, as no answer came yet. */
    private fun keep(port: Int, socket: Socket): Boolean {
        synchronized(lock) { waiting.put(port, socket) }?.let(::closeQuietly)
        return false
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        /** The SOCKS5 greeting, no authentication offered, and the version byte a server answers it with. */
        val GREETING = byteArrayOf(5, 1, 0)
        const val SOCKS_VERSION = 5

        /** Beyond the longest a question takes, for the thread that asks it to be scheduled. */
        const val SHARED_WAIT_SLACK_MS = 500L
    }
}
