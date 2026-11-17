package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class Socks5UdpTest {

    @Test
    fun `the request header names the IPv4 destination and port`() {
        val header = Socks5Udp.encodeHeader(byteArrayOf(1, 1, 1, 1), 53)

        assertArrayEquals(byteArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 0, 53), header)
    }

    @Test
    fun `an IPv4 datagram gives its payload`() {
        val data = byteArrayOf(0, 0, 0, 1, 8, 8, 8, 8, 0, 53, 9, 9, 9)

        assertArrayEquals(byteArrayOf(9, 9, 9), Socks5Udp.decodeDatagram(data, data.size))
    }

    @Test
    fun `an IPv6 and a domain datagram give their payload`() {
        val v6 = ByteArray(22) + byteArrayOf(7)
        v6[3] = 4
        val domain = byteArrayOf(0, 0, 0, 3, 3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0, 53, 5, 6)

        assertArrayEquals(byteArrayOf(7), Socks5Udp.decodeDatagram(v6, v6.size))
        assertArrayEquals(byteArrayOf(5, 6), Socks5Udp.decodeDatagram(domain, domain.size))
    }

    @Test
    fun `a fragment, a short header and an unknown address type are dropped`() {
        assertNull(Socks5Udp.decodeDatagram(byteArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 0, 53, 1), 11))
        assertNull(Socks5Udp.decodeDatagram(byteArrayOf(0, 0, 0, 1, 1, 1), 6))
        assertNull(Socks5Udp.decodeDatagram(byteArrayOf(0, 0, 0, 9, 1, 1, 1, 1, 0, 53, 1), 11))
    }

    /** A one-connection SOCKS5 server that answers the greeting with [greeting] and then replies with [reply] or hangs up. */
    private fun fakeServer(greeting: ByteArray, reply: ByteArray?): Pair<ServerSocket, Thread> {
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            server.accept().use { client ->
                val input = client.getInputStream()
                input.readNBytes(3)
                client.getOutputStream().write(greeting)
                client.getOutputStream().flush()
                input.readNBytes(10)
                if (reply != null) {
                    client.getOutputStream().write(reply)
                    client.getOutputStream().flush()
                    Thread.sleep(300)
                }
            }
        }
        return server to worker
    }

    @Test
    fun `a refused UDP associate is reported as unsupported`() {
        val (server, worker) = fakeServer(byteArrayOf(5, 0), byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0))

        val error = assertThrows(Socks5UdpException::class.java) { Socks5Udp.open(server.localPort, timeoutMs = 2000) }

        assertTrue(error.unsupported)
        worker.join(2000)
        server.close()
    }

    @Test
    fun `a server that hangs up on the request is reported as unsupported`() {
        val (server, worker) = fakeServer(byteArrayOf(5, 0), null)

        val error = assertThrows(Socks5UdpException::class.java) { Socks5Udp.open(server.localPort, timeoutMs = 2000) }

        assertTrue(error.unsupported)
        worker.join(2000)
        server.close()
    }

    @Test
    fun `a server that wants authentication is not reported as unsupported`() {
        val (server, worker) = fakeServer(byteArrayOf(5, 2), null)

        val error = assertThrows(Socks5UdpException::class.java) { Socks5Udp.open(server.localPort, timeoutMs = 2000) }

        assertFalse(error.unsupported)
        worker.join(2000)
        server.close()
    }

    @Test
    fun `an accepted association gives a session that can be closed`() {
        val (server, worker) = fakeServer(byteArrayOf(5, 0), byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0x30, 0x39))

        val session = Socks5Udp.open(server.localPort, timeoutMs = 2000)
        assertEquals(null, session.receive(50))
        session.close()
        worker.join(2000)
        server.close()
    }
}
