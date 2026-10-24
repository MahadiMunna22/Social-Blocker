package ch.puc.blocker.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketTest {
    private val dns = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0)

    /** IPv4/UDP from 10.111.222.2:40000 to 10.111.222.1:53 carrying [dns]. */
    private fun request(port: Int = 53) = ByteArray(28 + dns.size).also { p ->
        p[0] = 0x45; p[9] = 17
        p[2] = 0; p[3] = p.size.toByte()
        byteArrayOf(10, 111, 222.toByte(), 2).copyInto(p, 12)
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(p, 16)
        p[20] = (40000 shr 8).toByte(); p[21] = 40000.toByte()
        p[22] = (port shr 8).toByte(); p[23] = port.toByte()
        p[24] = 0; p[25] = (8 + dns.size).toByte()
        dns.copyInto(p, 28)
    }

    @Test fun extractsDnsQueriesOnly() {
        assertArrayEquals(dns, DnsPacket.query(request()))
        assertNull(DnsPacket.query(request(port = 443)))
    }

    @Test fun replyGoesBackToSenderWithValidChecksum() {
        val r = DnsPacket.reply(request(), dns)
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 1), r.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 2), r.copyOfRange(16, 20))
        assertEquals(53, (r[20].toInt() and 0xFF shl 8) or (r[21].toInt() and 0xFF))
        assertEquals(40000, (r[22].toInt() and 0xFF shl 8) or (r[23].toInt() and 0xFF))
        assertEquals(0, DnsPacket.checksum(r, 0, 20)) // a valid header sums to 0xFFFF, so its checksum is 0
        assertArrayEquals(dns, DnsPacket.query(r.also { it[22] = 0; it[23] = 53 }))
    }
}
