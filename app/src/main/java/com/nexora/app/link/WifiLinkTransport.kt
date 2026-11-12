package com.nexora.app.link

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Direct local-Wi-Fi transport for a two-device GBA Link session.
 *
 * Call host() or join() from an IO thread. This class handles room discovery,
 * compatibility handshake and framed TCP messages. Native mGBA SIO timing is
 * handled by NativeBridge; this transport never runs emulation callbacks.
 */
class WifiLinkTransport private constructor(
    private val socket: Socket,
    val session: Session
) : Closeable {
    data class Session(
        val id: UUID,
        val localPlayerId: Int,
        val peerPlayerId: Int,
        val gameCode: String,
        val romCrc32: Long
    )

    private val closed = AtomicBoolean(false)
    private val readerStarted = AtomicBoolean(false)
    private val writeLock = Any()
    private val output = DataOutputStream(socket.getOutputStream())

    /** Sends one complete protocol message as a synchronized frame. */
    fun send(message: LinkProtocol.Message) {
        check(!closed.get()) { "Link session is closed" }
        val frame = LinkProtocol.encode(message)
        synchronized(writeLock) {
            check(!closed.get()) { "Link session is closed" }
            output.write(frame)
            output.flush()
        }
    }

    /**
     * Starts the background receive loop. Callbacks run on a dedicated daemon
     * thread; UI callbacks must be dispatched to Android's main thread.
     */
    fun startReceiving(
        onMessage: (LinkProtocol.Message) -> Unit,
        onDisconnected: (Throwable?) -> Unit
    ) {
        check(readerStarted.compareAndSet(false, true)) { "Receive loop already started" }
        thread(name = "NEXORA-Link-WiFi", isDaemon = true) {
            var failure: Throwable? = null
            val decoder = LinkProtocol.Decoder()
            val buffer = ByteArray(8 * 1024)
            val ready = ArrayDeque<LinkProtocol.Message>()
            try {
                val input = socket.getInputStream()
                while (!closed.get()) {
                    val count = input.read(buffer)
                    if (count < 0) throw EOFException("Peer closed the Link connection")
                    ready.addAll(decoder.feed(buffer, 0, count))
                    while (ready.isNotEmpty() && !closed.get()) {
                        onMessage(ready.removeFirst())
                    }
                }
            } catch (error: Throwable) {
                if (!closed.get()) failure = error
            } finally {
                closeSocket()
                onDisconnected(failure)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching {
            val frame = LinkProtocol.encode(LinkProtocol.Goodbye("Session closed"))
            synchronized(writeLock) {
                output.write(frame)
                output.flush()
            }
        }
        runCatching { socket.close() }
    }

    private fun closeSocket() {
        closed.set(true)
        runCatching { socket.close() }
    }

    companion object {
        const val DEFAULT_PORT = 38_271

        /** Maps the four-digit local room code to the same session ID on both phones. */
        fun roomSessionId(roomCode: String): UUID {
            require(roomCode.matches(Regex("[0-9]{4}"))) {
                "Room code must contain exactly four digits"
            }
            return UUID.nameUUIDFromBytes(
                "NEXORA-GBA-LINK:$roomCode".toByteArray(Charsets.US_ASCII)
            )
        }

        /** Returns the device's preferred private IPv4 address for a LAN room. */
        fun localIpv4Address(): String {
            val candidates = NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { network ->
                    network.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
                        .map { network.name.lowercase() to it.hostAddress }
                }
                .orEmpty()
            return candidates.firstOrNull { it.first.startsWith("wlan") }?.second
                ?: candidates.firstOrNull { it.first.startsWith("ap") }?.second
                ?: candidates.firstOrNull()?.second
                ?: "IP indisponível"
        }
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val HANDSHAKE_TIMEOUT_MS = 12_000
        private const val ACCEPT_TIMEOUT_MS = 120_000

        /**
         * Opens a one-peer LAN room. Returns after a compatible peer completes
         * the handshake. Run on Dispatchers.IO or another worker thread.
         */
        fun host(
            gameCode: String,
            romCrc32: Long,
            port: Int = DEFAULT_PORT,
            sessionId: UUID = UUID.randomUUID(),
            onListening: ((String) -> Unit)? = null
        ): WifiLinkTransport {
            val server = ServerSocket()
            var peer: Socket? = null
            try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(port))
                runCatching { onListening?.invoke(localIpv4Address()) }
                server.soTimeout = ACCEPT_TIMEOUT_MS
                peer = server.accept()
                configure(peer)
                peer.soTimeout = HANDSHAKE_TIMEOUT_MS

                val hello = LinkProtocol.Hello(
                    sessionId = sessionId,
                    playerId = 0,
                    maxPlayers = 2,
                    gameCode = gameCode,
                    romCrc32 = romCrc32
                )
                val handshakeOutput = DataOutputStream(peer.getOutputStream())
                handshakeOutput.write(LinkProtocol.encode(hello))
                handshakeOutput.flush()

                val peerHello = readOne(peer)
                if (peerHello !is LinkProtocol.Hello) {
                    throw IllegalArgumentException("Peer did not send a Link handshake")
                }
                val rejection = when {
                    peerHello.sessionId != sessionId -> "Room code/session does not match"
                    peerHello.playerId != 1 || peerHello.maxPlayers != 2 ->
                        "Invalid joining player configuration"
                    peerHello.gameCode != gameCode || peerHello.romCrc32 != romCrc32 ->
                        "The two devices must use the same GBA game and ROM revision"
                    else -> null
                }
                if (rejection != null) {
                    handshakeOutput.write(
                        LinkProtocol.encode(
                            LinkProtocol.HelloAck(
                                accepted = false,
                                assignedPlayerId = 1,
                                playerCount = 2,
                                reason = rejection
                            )
                        )
                    )
                    handshakeOutput.flush()
                    throw IllegalArgumentException(rejection)
                }
                handshakeOutput.write(
                    LinkProtocol.encode(
                        LinkProtocol.HelloAck(
                            accepted = true,
                            assignedPlayerId = 1,
                            playerCount = 2
                        )
                    )
                )
                handshakeOutput.flush()
                peer.soTimeout = 0
                return WifiLinkTransport(
                    peer,
                    Session(sessionId, 0, 1, gameCode, romCrc32)
                )
            } catch (error: Throwable) {
                runCatching { peer?.close() }
                throw error
            } finally {
                runCatching { server.close() }
            }
        }

        /**
         * Joins a host on the same Wi-Fi network. The session ID is derived
         * from the four-digit room code displayed by the host.
         * Run on Dispatchers.IO or another worker thread.
         */
        fun join(
            hostAddress: String,
            expectedSessionId: UUID,
            gameCode: String,
            romCrc32: Long,
            port: Int = DEFAULT_PORT
        ): WifiLinkTransport {
            require(hostAddress.isNotBlank()) { "Host address is required" }
            val peer = Socket()
            try {
                configure(peer)
                peer.connect(InetSocketAddress(hostAddress, port), CONNECT_TIMEOUT_MS)
                peer.soTimeout = HANDSHAKE_TIMEOUT_MS

                val hello = readOne(peer)
                if (hello !is LinkProtocol.Hello) {
                    rejectHandshake(peer, "Host did not send a Link handshake")
                    throw IllegalArgumentException("Host did not send a Link handshake")
                }
                val rejection = when {
                    hello.sessionId != expectedSessionId -> "Room code/session does not match"
                    hello.playerId != 0 || hello.maxPlayers != 2 -> "Unsupported host player configuration"
                    hello.gameCode != gameCode || hello.romCrc32 != romCrc32 ->
                        "The two devices must use the same GBA game and ROM revision"
                    else -> null
                }
                if (rejection != null) {
                    rejectHandshake(peer, rejection)
                    throw IllegalArgumentException(rejection)
                }

                val handshakeOutput = DataOutputStream(peer.getOutputStream())
                handshakeOutput.write(
                    LinkProtocol.encode(
                        LinkProtocol.Hello(
                            sessionId = expectedSessionId,
                            playerId = 1,
                            maxPlayers = 2,
                            gameCode = gameCode,
                            romCrc32 = romCrc32
                        )
                    )
                )
                handshakeOutput.flush()

                val finalAck = readOne(peer)
                require(finalAck is LinkProtocol.HelloAck && finalAck.accepted) {
                    "Host rejected the Link session"
                }
                require(finalAck.assignedPlayerId == 1 && finalAck.playerCount == 2) {
                    "Invalid player assignment from host"
                }
                peer.soTimeout = 0
                return WifiLinkTransport(
                    peer,
                    Session(expectedSessionId, 1, 0, gameCode, romCrc32)
                )
            } catch (error: Throwable) {
                runCatching { peer.close() }
                throw error
            }
        }

        private fun configure(socket: Socket) {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.receiveBufferSize = 64 * 1024
            socket.sendBufferSize = 64 * 1024
        }

        private fun rejectHandshake(socket: Socket, reason: String) {
            runCatching {
                val output = DataOutputStream(socket.getOutputStream())
                output.write(
                    LinkProtocol.encode(
                        LinkProtocol.HelloAck(
                            accepted = false,
                            assignedPlayerId = 1,
                            playerCount = 2,
                            reason = reason
                        )
                    )
                )
                output.flush()
            }
        }

        private fun readOne(socket: Socket): LinkProtocol.Message {
            val input = DataInputStream(socket.getInputStream())
            val decoder = LinkProtocol.Decoder()
            val buffer = ByteArray(4 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) throw EOFException("Peer disconnected during Link handshake")
                val messages = decoder.feed(buffer, 0, count)
                if (messages.isNotEmpty()) return messages.first()
            }
        }
    }
}
