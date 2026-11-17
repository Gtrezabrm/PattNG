package com.v2ray.ang.core

import java.io.ByteArrayOutputStream

/** A parsed DNS answer: only what the MTU probe needs to tell a reply to its query from stray traffic. */
data class DnsReply(val id: Int, val truncated: Boolean, val rcode: Int, val length: Int)

/**
 * Builds DNS queries of an exact size, padded with the EDNS(0) padding option (RFC 7830), and recognises their answers.
 * Any answer to a query proves that the whole query arrived, whatever its response code.
 */
object DnsPacket {
    private const val HEADER_SIZE = 12
    private const val OPT_RECORD_SIZE = 11
    private const val PADDING_OPTION_HEADER = 4
    private const val OPTION_PADDING = 12
    private const val TYPE_A = 1
    private const val TYPE_OPT = 41
    private const val CLASS_IN = 1
    private const val FLAG_RECURSION_DESIRED = 0x0100
    private const val FLAG_RESPONSE = 0x8000
    private const val FLAG_TRUNCATED = 0x0200

    /**
     * An A query for [name] of [totalSize] bytes. A size under what the query needs without padding, or within the
     * 4 bytes of an option header above it, gives the unpadded query.
     */
    fun buildQuery(id: Int, name: String, totalSize: Int, udpBuffer: Int = 1232): ByteArray {
        val question = encodeName(name)
        val unpadded = HEADER_SIZE + question.size + 4 + OPT_RECORD_SIZE
        val padding = (totalSize - unpadded - PADDING_OPTION_HEADER).takeIf { it >= 0 }
        val out = ByteArrayOutputStream(maxOf(totalSize, unpadded))
        out.write16(id)
        out.write16(FLAG_RECURSION_DESIRED)
        out.write16(1) // questions
        out.write16(0) // answers
        out.write16(0) // authority
        out.write16(1) // additional: the OPT record
        out.write(question)
        out.write16(TYPE_A)
        out.write16(CLASS_IN)
        out.write(0) // OPT owner: the root
        out.write16(TYPE_OPT)
        out.write16(udpBuffer)
        out.write16(0) // extended rcode and version
        out.write16(0) // flags
        if (padding == null) {
            out.write16(0)
        } else {
            out.write16(PADDING_OPTION_HEADER + padding)
            out.write16(OPTION_PADDING)
            out.write16(padding)
            out.write(ByteArray(padding))
        }
        return out.toByteArray()
    }

    /** The answer in [bytes] when it is a response with [expectedId]; null for anything else. */
    fun parseResponse(bytes: ByteArray, expectedId: Int): DnsReply? {
        if (bytes.size < HEADER_SIZE) return null
        val id = read16(bytes, 0)
        val flags = read16(bytes, 2)
        if (id != expectedId || (flags and FLAG_RESPONSE) == 0) return null
        return DnsReply(id, (flags and FLAG_TRUNCATED) != 0, flags and 0x000F, bytes.size)
    }

    private fun encodeName(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        name.trim('.').split('.').filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII).copyOf(minOf(label.length, 63))
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.write16(value: Int) {
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun read16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
}
