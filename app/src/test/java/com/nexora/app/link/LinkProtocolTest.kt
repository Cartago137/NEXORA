package com.nexora.app.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LinkProtocolTest {
    @Test
    fun roomCodeCreatesTheSameSessionForHostAndJoiner() {
        val hostSession = WifiLinkTransport.roomSessionId("4827")
        val joinSession = WifiLinkTransport.roomSessionId("4827")

        assertEquals(hostSession, joinSession)
        assertNotEquals(hostSession, WifiLinkTransport.roomSessionId("4828"))
    }

    @Test
    fun roomCodeMustContainExactlyFourDigits() {
        assertThrows(IllegalArgumentException::class.java) {
            WifiLinkTransport.roomSessionId("123")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WifiLinkTransport.roomSessionId("12A4")
        }
    }

    @Test
    fun helloRoundTrips() {
        val message = LinkProtocol.Hello(
            sessionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            playerId = 1,
            maxPlayers = 4,
            gameCode = "BPRE",
            romCrc32 = 0xF1234567L
        )

        val decoded = LinkProtocol.Decoder().feed(LinkProtocol.encode(message))
        assertEquals(listOf(message), decoded)
    }

    @Test
    fun serialTransferRoundTripsUnsignedTransferId() {
        val message = LinkProtocol.SerialTransfer(
            transferId = 0xF1234567L,
            cycle = 123456789L,
            mode = LinkProtocol.SerialMode.MULTIPLAYER,
            baudCode = 2,
            bitWidth = 16,
            dataOut = 0x0000ABCD,
            sioControl = 0x5083,
            rcnt = 0x8000
        )

        assertEquals(listOf(message), LinkProtocol.Decoder().feed(LinkProtocol.encode(message)))
    }

    @Test
    fun decoderAcceptsFragmentedAndCoalescedFrames() {
        val first = LinkProtocol.Ping(42L)
        val second = LinkProtocol.Pong(42L)
        val bytes = LinkProtocol.encode(first) + LinkProtocol.encode(second)
        val decoder = LinkProtocol.Decoder()

        assertTrue(decoder.feed(bytes, 0, 7).isEmpty())
        assertEquals(listOf(first, second), decoder.feed(bytes, 7, bytes.size - 7))
    }

    @Test
    fun serialResultPreservesPlayerOrder() {
        val message = LinkProtocol.SerialResult(
            transferId = 9L,
            cycle = 9876L,
            receivedWords = listOf(0x1111, 0x2222, 0x3333, 0x4444),
            sioControl = 0x0080
        )

        assertEquals(listOf(message), LinkProtocol.Decoder().feed(LinkProtocol.encode(message)))
    }
}
