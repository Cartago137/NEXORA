package com.nexora.app.link

import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiLinkTransportTest {

    @Test
    fun joinAddressAcceptsTheDisplayedHostAndPort() {
        assertEquals(
            "192.168.0.102" to 38_271,
            WifiLinkTransport.parseHostEndpoint("192.168.0.102:38271", 38_271)
        )
    }

    @Test
    fun joinAddressAcceptsHostWithoutPortAndTrimsWhitespace() {
        assertEquals(
            "192.168.0.102" to 38_271,
            WifiLinkTransport.parseHostEndpoint("  192.168.0.102  ", 38_271)
        )
    }

    @Test
    fun joinAddressRejectsInvalidExplicitPort() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WifiLinkTransport.parseHostEndpoint("192.168.0.102:abc", 38_271)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WifiLinkTransport.parseHostEndpoint("192.168.0.102:70000", 38_271)
        }
    }

    @Test
    fun twoLocalPeersCompleteHandshakeAndExchangeMessage() {
        val port = ServerSocket(0).use { it.localPort }
        val sessionId = UUID.randomUUID()
        val executor = Executors.newSingleThreadExecutor()
        val hostReady = CountDownLatch(1)
        val hostFuture = executor.submit<WifiLinkTransport> {
            WifiLinkTransport.host(
                gameCode = "BPRE",
                romCrc32 = 0x1234ABCDL,
                port = port,
                sessionId = sessionId,
                onListening = { hostReady.countDown() }
            )
        }
        assertTrue("Host did not start listening", hostReady.await(3, TimeUnit.SECONDS))

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
    fun twoPeersExchangeGbaSerialTransferAndResult() {
        val port = ServerSocket(0).use { it.localPort }
        val sessionId = UUID.randomUUID()
        val executor = Executors.newSingleThreadExecutor()
        val hostReady = CountDownLatch(1)
        val hostFuture = executor.submit<WifiLinkTransport> {
            WifiLinkTransport.host(
                gameCode = "BPRE",
                romCrc32 = 0x1234ABCDL,
                port = port,
                sessionId = sessionId,
                onListening = { hostReady.countDown() }
            )
        }
        assertTrue("Host did not start listening", hostReady.await(3, TimeUnit.SECONDS))

        val client = WifiLinkTransport.join(
            hostAddress = "127.0.0.1",
            expectedSessionId = sessionId,
            gameCode = "BPRE",
            romCrc32 = 0x1234ABCDL,
            port = port
        )
        val host = hostFuture.get(5, TimeUnit.SECONDS)

        val transferReceived = CountDownLatch(1)
        val resultReceived = CountDownLatch(1)
        val hostMessage = arrayOfNulls<LinkProtocol.Message>(1)
        val clientMessage = arrayOfNulls<LinkProtocol.Message>(1)
        host.startReceiving(
            onMessage = {
                hostMessage[0] = it
                if (it is LinkProtocol.SerialTransfer) transferReceived.countDown()
            },
            onDisconnected = {}
        )
        client.startReceiving(
            onMessage = {
                clientMessage[0] = it
                if (it is LinkProtocol.SerialResult) resultReceived.countDown()
            },
            onDisconnected = {}
        )

        val request = LinkProtocol.SerialTransfer(
            transferId = 1L,
            cycle = 1234L,
            mode = LinkProtocol.SerialMode.MULTIPLAYER,
            baudCode = 0,
            bitWidth = 16,
            dataOut = 0x1234,
            sioControl = 0x5083,
            rcnt = 0
        )
        val response = LinkProtocol.SerialResult(
            transferId = 1L,
            cycle = 1300L,
            receivedWords = listOf(0xFFFF, 0xABCD),
            sioControl = 0x0080
        )

        client.send(request)
        assertTrue("Host did not receive the GBA serial transfer", transferReceived.await(3, TimeUnit.SECONDS))
        assertEquals(request, hostMessage[0])

        host.send(response)
        assertTrue("Joiner did not receive the GBA serial result", resultReceived.await(3, TimeUnit.SECONDS))
        assertEquals(response, clientMessage[0])

        client.close()
        host.close()
        executor.shutdownNow()
    }

    @Test
    fun hostRoomCanBeCancelledWhileWaitingForPeer() {
        val port = ServerSocket(0).use { it.localPort }
        val cancelled = AtomicBoolean(false)
        val listening = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<WifiLinkTransport> {
            WifiLinkTransport.host(
                gameCode = "BPRE",
                romCrc32 = 0x1234ABCDL,
                port = port,
                sessionId = UUID.randomUUID(),
                onListening = { listening.countDown() },
                shouldCancel = { cancelled.get() }
            )
        }

        assertTrue("Host did not start listening", listening.await(3, TimeUnit.SECONDS))
        cancelled.set(true)
        val failure = runCatching { future.get(2, TimeUnit.SECONDS) }.exceptionOrNull()
        assertTrue("Host should stop accepting when cancelled", failure is java.util.concurrent.ExecutionException)
        assertTrue("Cancellation should be the underlying cause", failure?.cause is java.util.concurrent.CancellationException)
        executor.shutdownNow()
    }

    @Test
    fun joinRejectsDifferentGameCode() {
        val port = ServerSocket(0).use { it.localPort }
        val sessionId = UUID.randomUUID()
        val executor = Executors.newSingleThreadExecutor()
        val hostReady = CountDownLatch(1)
        val hostFuture = executor.submit<WifiLinkTransport> {
            WifiLinkTransport.host(
                gameCode = "BPRE",
                romCrc32 = 0x1234ABCDL,
                port = port,
                sessionId = sessionId,
                onListening = { hostReady.countDown() }
            )
        }
        assertTrue("Host did not start listening", hostReady.await(3, TimeUnit.SECONDS))

        val failure = runCatching {
            WifiLinkTransport.join(
                hostAddress = "127.0.0.1",
                expectedSessionId = sessionId,
                gameCode = "BPRF",
                romCrc32 = 0x1234ABCDL,
                port = port
            )
        }.exceptionOrNull()

        assertTrue("A mismatched game code must be rejected", failure is IllegalArgumentException)
        runCatching { hostFuture.get(2, TimeUnit.SECONDS).close() }
        executor.shutdownNow()
    }

    @Test
    fun joinRejectsDifferentRomIdentity() {
        val port = ServerSocket(0).use { it.localPort }
        val sessionId = UUID.randomUUID()
        val executor = Executors.newSingleThreadExecutor()
        val hostReady = CountDownLatch(1)
        val hostFuture = executor.submit<WifiLinkTransport> {
            WifiLinkTransport.host(
                gameCode = "BPRE",
                romCrc32 = 0x1234ABCDL,
                port = port,
                sessionId = sessionId,
                onListening = { hostReady.countDown() }
            )
        }
        assertTrue("Host did not start listening", hostReady.await(3, TimeUnit.SECONDS))

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
