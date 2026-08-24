package com.contentfilter.feature.vpn.transport

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress

internal enum class Socks5Command(val wireValue: Int) {
    Connect(1),
    UdpAssociate(3),
}

internal data class Socks5Request(
    val command: Socks5Command,
    val target: InetSocketAddress,
)

internal data class Socks5UdpDatagram(
    val target: InetSocketAddress,
    val payload: ByteArray,
)

internal object Socks5Protocol {
    const val Version = 5
    const val UsernamePasswordMethod = 2
    const val NoAcceptableMethod = 0xFF
    const val Success = 0
    const val GeneralFailure = 1
    const val CommandNotSupported = 7
    const val AddressTypeNotSupported = 8

    fun negotiateMethod(
        input: InputStream,
        output: OutputStream,
    ): Boolean {
        if (input.read() != Version) return false
        val count = input.read()
        if (count !in 1..MaximumMethods) return false
        val methods = input.readExactly(count)
        val accepted = methods.any { it.toInt() and 0xFF == UsernamePasswordMethod }
        output.write(
            byteArrayOf(
                Version.toByte(),
                (if (accepted) UsernamePasswordMethod else NoAcceptableMethod).toByte(),
            ),
        )
        output.flush()
        return accepted
    }

    fun authenticate(
        input: InputStream,
        output: OutputStream,
        expectedUsername: ByteArray,
        expectedPassword: ByteArray,
    ): Boolean {
        if (input.read() != AuthVersion) return false
        val username = input.readExactly(input.readBoundedLength())
        val password = input.readExactly(input.readBoundedLength())
        val accepted =
            java.security.MessageDigest.isEqual(username, expectedUsername) &&
                java.security.MessageDigest.isEqual(password, expectedPassword)
        username.fill(0)
        password.fill(0)
        output.write(byteArrayOf(AuthVersion.toByte(), (if (accepted) 0 else 1).toByte()))
        output.flush()
        return accepted
    }

    fun readRequest(input: InputStream): Socks5Request? {
        if (input.read() != Version) return null
        val commandValue = input.read()
        val command = Socks5Command.entries.firstOrNull { it.wireValue == commandValue } ?: return null
        if (input.read() != 0) return null
        val target = readEndpoint(input) ?: return null
        return Socks5Request(command, target)
    }

    fun writeReply(
        output: OutputStream,
        reply: Int,
        endpoint: InetSocketAddress = InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0),
    ) {
        val address = endpoint.address.address
        val addressType = if (address.size == Ipv4Size) AddressIpv4 else AddressIpv6
        output.write(byteArrayOf(Version.toByte(), reply.toByte(), 0, addressType.toByte()))
        output.write(address)
        output.write(byteArrayOf((endpoint.port ushr 8).toByte(), endpoint.port.toByte()))
        output.flush()
    }

    fun parseUdpDatagram(
        bytes: ByteArray,
        length: Int,
    ): Socks5UdpDatagram? {
        if (length !in MinimumUdpPacketSize..bytes.size) return null
        if (bytes[0].toInt() != 0 || bytes[1].toInt() != 0 || bytes[2].toInt() != 0) return null
        var offset = 3
        val addressSize =
            when (bytes[offset++].toInt() and 0xFF) {
                AddressIpv4 -> Ipv4Size
                AddressIpv6 -> Ipv6Size
                else -> return null
            }
        if (offset + addressSize + PortSize > length) return null
        val address = InetAddress.getByAddress(bytes.copyOfRange(offset, offset + addressSize))
        offset += addressSize
        val port = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        offset += PortSize
        return Socks5UdpDatagram(InetSocketAddress(address, port), bytes.copyOfRange(offset, length))
    }

    fun encodeUdpDatagram(
        source: InetSocketAddress,
        payload: ByteArray,
        length: Int,
    ): ByteArray {
        val address = source.address.address
        val addressType = if (address.size == Ipv4Size) AddressIpv4 else AddressIpv6
        return ByteArray(3 + 1 + address.size + PortSize + length).also { result ->
            var offset = 3
            result[offset++] = addressType.toByte()
            address.copyInto(result, offset)
            offset += address.size
            result[offset++] = (source.port ushr 8).toByte()
            result[offset++] = source.port.toByte()
            payload.copyInto(result, offset, endIndex = length)
        }
    }

    private fun readEndpoint(input: InputStream): InetSocketAddress? {
        val address =
            when (input.read()) {
                AddressIpv4 -> InetAddress.getByAddress(input.readExactly(Ipv4Size))
                AddressIpv6 -> InetAddress.getByAddress(input.readExactly(Ipv6Size))
                AddressDomain -> return null
                else -> return null
            }
        val portBytes = input.readExactly(PortSize)
        val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
        return InetSocketAddress(address, port)
    }

    private fun InputStream.readBoundedLength(): Int {
        val length = read()
        if (length !in 1..MaximumCredentialLength) throw EOFException("invalid credential length")
        return length
    }

    private fun InputStream.readExactly(length: Int): ByteArray =
        ByteArray(length).also { bytes ->
            var offset = 0
            while (offset < length) {
                val read = read(bytes, offset, length - offset)
                if (read < 0) throw EOFException()
                offset += read
            }
        }

    private const val AuthVersion = 1
    private const val AddressIpv4 = 1
    private const val AddressDomain = 3
    private const val AddressIpv6 = 4
    private const val Ipv4Size = 4
    private const val Ipv6Size = 16
    private const val PortSize = 2
    private const val MinimumUdpPacketSize = 10
    private const val MaximumMethods = 32
    private const val MaximumCredentialLength = 255
}
