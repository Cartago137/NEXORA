package com.nexora.app.link

import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiLinkTransportTest {
    @Test
    fun twoLocalPeersCompleteHandshakeAndExchangeMessage() {
        val port = ServerSocket(0).use { it.localPort }
        val sessionId = UUID.randomUUID()
        val executor = Executors.newSingleThreadExecutor()
        val hostFuture = executor.submit<WifiLinkTransport> {
            WifiLinkTransport.host(
                gameCode = "BPRE",
                romCrc32 = 0x1234ABCDL,
                port = port,
                sessionId = sessionId
            )
        }
        Thread.sleep(100) // Give the host worker time to bind the ephemeral test port.

        val client = WifiLinkTransport.join(
            hostAddress = "127.0.0.1",
            expectedSessionId = sessionId,
            gameCode = "BPRE",
            romCrc32 = 0x1234ABCDL,
            port = port
        )
        val host = hostFuture.get(5, TimeUnit.SECONDS)

        assertEquals(0, host.session.localPlayerId)
        assertEquals(1, client.session.localPlayerId)
        assertEquals(sessionId, client.session.id)

        val received = CountDownLatch(1)
        val hostMessage = arrayOfNulls<LinkProtocol.Message>(1)
        host.startReceiving(
            onMessage = {
                hostMessage[0] = it
                received.countDown()
            },
            onDisconnected = {}
        )
        client.startReceiving(onMessage = {}, onDisconnected = {})

        client.send(LinkProtocol.Ping(42L))
        assertTrue("Host did not receive the test frame", received.await(3, TimeUnit.SECONDS))
        assertEquals(LinkProtocol.Ping(42L), hostMessage[0])

        client.close()
        host.close()
        executor.shutdownNow()
    }

    @Test
    fun joinRejectsDifferentRomIdentity() {
        val port = ServerSocket(0).use { it.localPort }
        val sessionId = UUID.randomUUID()
        val executor = Executors.newSingleThreadExecutor()
        val hostFuture = executor.submit<WifiLinkTransport> {
            WifiLinkTransport.host(
                gameCode = "BPRE",
                romCrc32 = 0x1234ABCDL,
                port = port,
                sessionId = sessionId
            )
        }
        Thread.sleep(100) // Give the host worker time to bind the ephemeral test port.

        val failure = runCatching {
            WifiLinkTransport.join(
                hostAddress = "127.0.0.1",
                expectedSessionId = sessionId,
                gameCode = "BPRE",
                romCrc32 = 0x99999999L,
                port = port
            )
        }.exceptionOrNull()

        assertTrue("A mismatched ROM must be rejected", failure is IllegalArgumentException)
        runCatching { hostFuture.get(2, TimeUnit.SECONDS).close() }
        executor.shutdownNow()
    }
}
