package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** The SOCKS5 server refused or dropped a UDP association; [unsupported] when it looks like it cannot do UDP at all. */
class Socks5UdpException(val unsupported: Boolean, message: String) : IOException(message)

/**
 * A SOCKS5 UDP association (RFC 1928) with a local proxy. The TCP control connection stays open for as long as the
 * association is used; closing it, or this object, ends the association.
 */
internal class Socks5Udp private constructor(
    private val control: Socket,
    private val udp: DatagramSocket,
    private val relay: InetSocketAddress,
) : Closeable {
    private val buffer = ByteArray(RECEIVE_BUFFER)

    /** Sends [payload] to the IPv4 address [destination] on [port] through the relay. */
    fun send(destination: ByteArray, port: Int, payload: ByteArray) {
        val packet = encodeHeader(destination, port) + payload
        udp.send(DatagramPacket(packet, packet.size, relay))
    }

    /** The payload of the next datagram from the relay, or null when none comes within [timeoutMs]. */
    fun receive(timeoutMs: Int): ByteArray? {
        udp.soTimeout = timeoutMs.coerceAtLeast(1)
        val packet = DatagramPacket(buffer, buffer.size)
        return try {
            udp.receive(packet)
            decodeDatagram(buffer, packet.length)
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    /**
     * True when the server has ended the association by closing the control connection. UDP sends to a relay that is gone
     * do not fail, so without this its silence would read as a datagram size that does not get through.
     */
    fun associationLost(): Boolean {
        return try {
            control.soTimeout = 1
            control.getInputStream().read() == -1
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: IOException) {
            true
        }
    }

    override fun close() {
        runCatching { udp.close() }
        runCatching { control.close() }
    }

    companion object {
        private const val RECEIVE_BUFFER = 65535
        private const val VERSION = 5
        private const val CMD_UDP_ASSOCIATE = 3
        private const val ATYP_IPV4 = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6 = 4

        /** The 10-byte request header of a datagram to an IPv4 [destination]. */
        fun encodeHeader(destination: ByteArray, port: Int): ByteArray {
            require(destination.size == 4) { "IPv4 address expected" }
            return byteArrayOf(0, 0, 0, ATYP_IPV4.toByte()) + destination +
                byteArrayOf((port shr 8).toByte(), port.toByte())
        }

        /** The payload of a relayed datagram of [length] bytes in [data]; null when its header is not well formed. */
        fun decodeDatagram(data: ByteArray, length: Int): ByteArray? {
            if (length < 4 || data[2].toInt() != 0) return null // a fragment is not reassembled
            val header = when (data[3].toInt()) {
                ATYP_IPV4 -> 10
                ATYP_IPV6 -> 22
                ATYP_DOMAIN -> if (length > 4) 7 + (data[4].toInt() and 0xFF) else return null
                else -> return null
            }
            if (length < header) return null
            return data.copyOfRange(header, length)
        }

        /** Opens an association with the SOCKS5 server on [host]:[port]; throws [Socks5UdpException] or [IOException]. */
        fun open(port: Int, host: String = AppConfig.LOOPBACK, timeoutMs: Int = 3000): Socks5Udp {
            val control = Socket()
            var udp: DatagramSocket? = null
            try {
                control.connect(InetSocketAddress(host, port), timeoutMs)
                control.soTimeout = timeoutMs
                val output = control.getOutputStream()
                val input = DataInputStream(control.getInputStream())

                output.write(byteArrayOf(VERSION.toByte(), 1, 0))
                output.flush()
                val greeting = ByteArray(2).also { input.readFully(it) }
                if (greeting[0].toInt() != VERSION || greeting[1].toInt() != 0) {
                    throw Socks5UdpException(false, "the server wants an authentication this probe does not offer")
                }

                output.write(byteArrayOf(VERSION.toByte(), CMD_UDP_ASSOCIATE.toByte(), 0, ATYP_IPV4.toByte(), 0, 0, 0, 0, 0, 0))
                output.flush()
                val reply = ByteArray(4).also { input.readFully(it) }
                if (reply[0].toInt() != VERSION) throw Socks5UdpException(false, "not a SOCKS5 reply")
                if (reply[1].toInt() != 0) throw Socks5UdpException(true, "UDP associate refused, code ${reply[1]}")
                val address = when (reply[3].toInt()) {
                    ATYP_IPV4 -> ByteArray(4)
                    ATYP_IPV6 -> ByteArray(16)
                    ATYP_DOMAIN -> ByteArray(input.readUnsignedByte())
                    else -> throw Socks5UdpException(false, "unknown address type in the reply")
                }
                input.readFully(address)
                val relayPort = input.readUnsignedShort()
                if (relayPort == 0) throw Socks5UdpException(false, "the relay has no port")

                // A relay bound to the wildcard address is reached on the address the control connection used.
                val relayHost = if (address.size == 4 || address.size == 16) {
                    InetAddress.getByAddress(address).takeUnless { it.isAnyLocalAddress } ?: InetAddress.getByName(host)
                } else {
                    InetAddress.getByName(host)
                }
                control.soTimeout = 0
                udp = DatagramSocket()
                return Socks5Udp(control, udp, InetSocketAddress(relayHost, relayPort))
            } catch (e: EOFException) {
                // A server that hangs up on the request does not do UDP associate.
                runCatching { control.close() }
                udp?.close()
                throw Socks5UdpException(true, "the server closed the connection on the UDP associate request")
            } catch (e: Exception) {
                runCatching { control.close() }
                udp?.close()
                throw e
            }
        }
    }
}
