package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsPacketTest {

    @Test
    fun `a query has exactly the requested size`() {
        for (size in listOf(100, 576, 1000, 1200, 1264, 1472)) {
            assertEquals(size, DnsPacket.buildQuery(0x1234, "example.com", size).size)
        }
    }

    @Test
    fun `a query carries its id and asks one question with one OPT record`() {
        val query = DnsPacket.buildQuery(0xABCD, "example.com", 200)

        assertEquals(0xAB, query[0].toInt() and 0xFF)
        assertEquals(0xCD, query[1].toInt() and 0xFF)
        assertEquals(1, query[5].toInt()) // QDCOUNT
        assertEquals(1, query[11].toInt()) // ARCOUNT
    }

    @Test
    fun `the padding option declares the bytes that follow it`() {
        val query = DnsPacket.buildQuery(1, "example.com", 300)

        // The OPT rdata is the padding option, code 12, its length, then zeros up to the end of the packet.
        val optionAt = query.size - (300 - 40) // an unpadded query is 40 bytes, and its empty rdata length is 2 of them
        assertEquals(12, query[optionAt + 1].toInt())
        val declared = ((query[optionAt + 2].toInt() and 0xFF) shl 8) or (query[optionAt + 3].toInt() and 0xFF)
        assertEquals(query.size - optionAt - 4, declared)
        assertTrue(query.drop(optionAt + 4).all { it.toInt() == 0 })
    }

    @Test
    fun `a size below the unpadded query gives the unpadded query`() {
        val query = DnsPacket.buildQuery(1, "example.com", 10)

        assertEquals(40, query.size)
    }

    @Test
    fun `a response with the expected id is recognised`() {
        val response = DnsPacket.buildQuery(0x0102, "example.com", 60)
        response[2] = (response[2].toInt() or 0x80).toByte() // QR

        val reply = DnsPacket.parseResponse(response, 0x0102)

        assertNotNull(reply)
        assertFalse(reply!!.truncated)
        assertEquals(response.size, reply.length)
    }

    @Test
    fun `a truncated response is recognised as one`() {
        val response = DnsPacket.buildQuery(5, "example.com", 60)
        response[2] = (response[2].toInt() or 0x82).toByte() // QR and TC

        assertTrue(DnsPacket.parseResponse(response, 5)!!.truncated)
    }

    @Test
    fun `an answer with another id, a query and a short datagram are not replies`() {
        val response = DnsPacket.buildQuery(7, "example.com", 60)
        response[2] = (response[2].toInt() or 0x80).toByte()

        assertNull(DnsPacket.parseResponse(response, 8))
        assertNull(DnsPacket.parseResponse(DnsPacket.buildQuery(7, "example.com", 60), 7))
        assertNull(DnsPacket.parseResponse(ByteArray(5), 7))
    }
}
