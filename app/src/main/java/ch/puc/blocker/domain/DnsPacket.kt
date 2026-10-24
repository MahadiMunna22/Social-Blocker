package ch.puc.blocker.domain

/** Minimal IPv4/UDP framing for DNS packets read from and written to the filter's tunnel. */
object DnsPacket {
    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v shr 8).toByte(); b[i + 1] = v.toByte() }

    /** The DNS payload if [ip] is an IPv4/UDP packet to port 53, else null. */
    fun query(ip: ByteArray): ByteArray? {
        if (ip.size < 28 || ip[0].toInt() shr 4 != 4 || ip[9].toInt() != 17) return null
        val ihl = (ip[0].toInt() and 0xF) * 4
        if (ip.size < ihl + 8 || u16(ip, ihl + 2) != 53) return null
        val end = (ihl + u16(ip, ihl + 4)).coerceAtMost(ip.size)
        return if (end > ihl + 8) ip.copyOfRange(ihl + 8, end) else null
    }

    /** Wraps a DNS [answer] in an IPv4/UDP packet back to whoever sent [request]. */
    fun reply(request: ByteArray, answer: ByteArray): ByteArray {
        val ihl = (request[0].toInt() and 0xF) * 4
        val out = ByteArray(28 + answer.size)
        out[0] = 0x45
        put16(out, 2, out.size)
        out[8] = 64 // TTL
        out[9] = 17 // UDP
        request.copyInto(out, 12, 16, 20) // source = the address they asked
        request.copyInto(out, 16, 12, 16) // destination = them
        put16(out, 10, checksum(out, 0, 20))
        put16(out, 20, u16(request, ihl + 2))
        put16(out, 22, u16(request, ihl))
        put16(out, 24, 8 + answer.size) // UDP checksum stays 0: optional on IPv4
        answer.copyInto(out, 28)
        return out
    }

    fun checksum(b: ByteArray, from: Int, len: Int): Int {
        var sum = 0
        for (i in from until from + len step 2) sum += u16(b, i)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
