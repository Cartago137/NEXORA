package com.nexora.app.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Versioned, transport-independent wire protocol for NEXORA GBA link sessions.
 *
 * This codec deliberately does not open sockets or call mGBA. It defines the
 * bounded messages that a later Wi-Fi/Bluetooth transport and native SIO
 * adapter can exchange. All integers use network byte order (big endian).
 */
object LinkProtocol {
    const val VERSION = 1
    const val MAX_PAYLOAD_BYTES = 64 * 1024
    const val MAX_BUFFERED_BYTES = MAX_PAYLOAD_BYTES * 4

    private val MAGIC = byteArrayOf(0x4E, 0x58, 0x4C, 0x50) // "NXLP"
    private const val HEADER_BYTES = 10

    private const val TYPE_HELLO = 1
    private const val TYPE_HELLO_ACK = 2
    private const val TYPE_SERIAL_TRANSFER = 3
    private const val TYPE_SERIAL_RESULT = 4
    private const val TYPE_PING = 5
    private const val TYPE_PONG = 6
    private const val TYPE_GOODBYE = 7
    private const val TYPE_ERROR = 8

    enum class SerialMode(val wireValue: Int) {
        NORMAL_8(0),
        NORMAL_32(1),
        MULTIPLAYER(2),
        JOYBUS(3);

        companion object {
            fun fromWire(value: Int): SerialMode =
                entries.firstOrNull { it.wireValue == value }
                    ?: throw IllegalArgumentException("Unknown serial mode: $value")
        }
    }

    sealed interface Message

    data class Hello(
        val sessionId: UUID,
        val playerId: Int,
        val maxPlayers: Int,
        val gameCode: String,
        /** Unsigned 32-bit ROM CRC stored in a Long. */
        val romCrc32: Long
    ) : Message {
        init {
            require(playerId in 0..3) { "playerId must be between 0 and 3" }
            require(maxPlayers in 2..4) { "maxPlayers must be between 2 and 4" }
            require(playerId < maxPlayers) { "playerId must be lower than maxPlayers" }
            require(gameCode.matches(Regex("[A-Z0-9]{4}"))) {
                "gameCode must contain exactly four uppercase ASCII letters/digits"
            }
            require(romCrc32 in 0L..0xFFFF_FFFFL) { "romCrc32 must be unsigned 32-bit" }
        }
    }

    data class HelloAck(
        val accepted: Boolean,
        val assignedPlayerId: Int,
        val playerCount: Int,
        val reason: String = ""
    ) : Message {
        init {
            require(assignedPlayerId in 0..3)
            require(playerCount in 1..4)
            require(assignedPlayerId < playerCount)
            require(reason.toByteArray(StandardCharsets.UTF_8).size <= 256)
        }
    }

    /**
     * A request to execute one GBA serial transfer. cycle is the sender core's
     * monotonically increasing emulated-cycle timestamp, not wall-clock time.
     * dataOut carries the sender's SIO data register value.
     */
    data class SerialTransfer(
        val transferId: Long,
        val cycle: Long,
        val mode: SerialMode,
        val baudCode: Int,
        val bitWidth: Int,
        val dataOut: Int,
        val sioControl: Int,
        val rcnt: Int
    ) : Message {
        init {
            require(transferId in 0L..0xFFFF_FFFFL)
            require(cycle >= 0L)
            require(baudCode in 0..3)
            require(bitWidth == 8 || bitWidth == 16 || bitWidth == 32)
            require(sioControl in 0..0xFFFF)
            require(rcnt in 0..0xFFFF)
        }
    }

    /** Values are ordered by GBA player ID; unused slots are zero-filled. */
    data class SerialResult(
        val transferId: Long,
        val cycle: Long,
        val receivedWords: List<Int>,
        val sioControl: Int
    ) : Message {
        init {
            require(transferId in 0L..0xFFFF_FFFFL)
            require(cycle >= 0L)
            require(receivedWords.size in 1..4)
            require(receivedWords.all { it in 0..0xFFFF })
            require(sioControl in 0..0xFFFF)
        }
    }

    data class Ping(val nonce: Long) : Message
    data class Pong(val nonce: Long) : Message
    data class Goodbye(val reason: String = "") : Message {
        init {
            require(reason.toByteArray(StandardCharsets.UTF_8).size <= 256)
        }
    }
    data class ProtocolError(val code: Int, val message: String) : Message {
        init {
            require(code in 0..0xFFFF)
            require(message.toByteArray(StandardCharsets.UTF_8).size <= 256)
        }
    }

    fun encode(message: Message): ByteArray {
        val type: Int
        val payloadBytes = ByteArrayOutputStream()
        DataOutputStream(payloadBytes).use { out ->
            type = when (message) {
                is Hello -> {
                    out.writeLong(message.sessionId.mostSignificantBits)
                    out.writeLong(message.sessionId.leastSignificantBits)
                    out.writeByte(message.playerId)
                    out.writeByte(message.maxPlayers)
                    writeShortString(out, message.gameCode, 4)
                    out.writeInt(message.romCrc32.toInt())
                    TYPE_HELLO
                }
                is HelloAck -> {
                    out.writeBoolean(message.accepted)
                    out.writeByte(message.assignedPlayerId)
                    out.writeByte(message.playerCount)
                    writeShortString(out, message.reason, 256)
                    TYPE_HELLO_ACK
                }
                is SerialTransfer -> {
                    out.writeInt(message.transferId.toInt())
                    out.writeLong(message.cycle)
                    out.writeByte(message.mode.wireValue)
                    out.writeByte(message.baudCode)
                    out.writeByte(message.bitWidth)
                    out.writeInt(message.dataOut)
                    out.writeShort(message.sioControl)
                    out.writeShort(message.rcnt)
                    TYPE_SERIAL_TRANSFER
                }
                is SerialResult -> {
                    out.writeInt(message.transferId.toInt())
                    out.writeLong(message.cycle)
                    out.writeByte(message.receivedWords.size)
                    message.receivedWords.forEach(out::writeShort)
                    out.writeShort(message.sioControl)
                    TYPE_SERIAL_RESULT
                }
                is Ping -> {
                    out.writeLong(message.nonce)
                    TYPE_PING
                }
                is Pong -> {
                    out.writeLong(message.nonce)
                    TYPE_PONG
                }
                is Goodbye -> {
                    writeShortString(out, message.reason, 256)
                    TYPE_GOODBYE
                }
                is ProtocolError -> {
                    out.writeShort(message.code)
                    writeShortString(out, message.message, 256)
                    TYPE_ERROR
                }
            }
        }

        val payload = payloadBytes.toByteArray()
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Link message is too large" }
        val frame = ByteArrayOutputStream(HEADER_BYTES + payload.size)
        DataOutputStream(frame).use { out ->
            out.write(MAGIC)
            out.writeByte(VERSION)
            out.writeByte(type)
            out.writeInt(payload.size)
            out.write(payload)
        }
        return frame.toByteArray()
    }

    /**
     * Incremental TCP-stream decoder. It handles partial headers, partial
     * payloads and multiple frames arriving in a single read.
     */
    class Decoder {
        private var pending = ByteArray(0)

        fun feed(chunk: ByteArray, offset: Int = 0, length: Int = chunk.size): List<Message> {
            require(offset >= 0 && length >= 0 && offset + length <= chunk.size)
            if (length == 0) return emptyList()
            require(pending.size.toLong() + length <= MAX_BUFFERED_BYTES) {
                "Too much unprocessed link data"
            }

            pending = pending + chunk.copyOfRange(offset, offset + length)
            val decoded = ArrayList<Message>()

            while (pending.size >= HEADER_BYTES) {
                for (i in MAGIC.indices) {
                    if (pending[i] != MAGIC[i]) {
                        pending = ByteArray(0)
                        throw IllegalArgumentException("Invalid NEXORA link frame magic")
                    }
                }
                val header = DataInputStream(ByteArrayInputStream(pending, 4, HEADER_BYTES - 4))
                val version = header.readUnsignedByte()
                require(version == VERSION) { "Unsupported link protocol version: $version" }
                val type = header.readUnsignedByte()
                val payloadLength = header.readInt()
                require(payloadLength in 0..MAX_PAYLOAD_BYTES) { "Invalid link payload length" }
                val frameLength = HEADER_BYTES + payloadLength
                if (pending.size < frameLength) break

                val payload = pending.copyOfRange(HEADER_BYTES, frameLength)
                decoded += decodePayload(type, payload)
                pending = pending.copyOfRange(frameLength, pending.size)
            }
            return decoded
        }

        fun reset() {
            pending = ByteArray(0)
        }
    }

    private fun decodePayload(type: Int, payload: ByteArray): Message {
        val input = DataInputStream(ByteArrayInputStream(payload))
        val message = when (type) {
            TYPE_HELLO -> {
                val id = UUID(input.readLong(), input.readLong())
                val playerId = input.readUnsignedByte()
                val maxPlayers = input.readUnsignedByte()
                val gameCode = readShortString(input, 4)
                val crc = input.readInt().toLong() and 0xFFFF_FFFFL
                Hello(id, playerId, maxPlayers, gameCode, crc)
            }
            TYPE_HELLO_ACK -> HelloAck(
                accepted = input.readBoolean(),
                assignedPlayerId = input.readUnsignedByte(),
                playerCount = input.readUnsignedByte(),
                reason = readShortString(input, 256)
            )
            TYPE_SERIAL_TRANSFER -> SerialTransfer(
                transferId = input.readInt().toLong() and 0xFFFF_FFFFL,
                cycle = input.readLong(),
                mode = SerialMode.fromWire(input.readUnsignedByte()),
                baudCode = input.readUnsignedByte(),
                bitWidth = input.readUnsignedByte(),
                dataOut = input.readInt(),
                sioControl = input.readUnsignedShort(),
                rcnt = input.readUnsignedShort()
            )
            TYPE_SERIAL_RESULT -> {
                val transferId = input.readInt().toLong() and 0xFFFF_FFFFL
                val cycle = input.readLong()
                val count = input.readUnsignedByte()
                require(count in 1..4) { "Invalid serial result player count" }
                val words = List(count) { input.readUnsignedShort() }
                SerialResult(transferId, cycle, words, input.readUnsignedShort())
            }
            TYPE_PING -> Ping(input.readLong())
            TYPE_PONG -> Pong(input.readLong())
            TYPE_GOODBYE -> Goodbye(readShortString(input, 256))
            TYPE_ERROR -> ProtocolError(input.readUnsignedShort(), readShortString(input, 256))
            else -> throw IllegalArgumentException("Unknown link message type: $type")
        }
        require(input.available() == 0) { "Unexpected trailing bytes in link message" }
        return message
    }

    private fun writeShortString(out: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= maxBytes) { "String exceeds $maxBytes UTF-8 bytes" }
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    private fun readShortString(input: DataInputStream, maxBytes: Int): String {
        val size = input.readUnsignedShort()
        require(size <= maxBytes && size <= input.available()) { "Invalid string length" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }
}
