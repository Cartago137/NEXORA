package com.nexora.app

/**
 * JNI boundary for the GBA engine. Rendering and input stay behind this API
 * so another console core can be added without changing the Compose UI.
 */
object NativeBridge {
    init {
        System.loadLibrary("nexora_native")
    }

    /** Returns 2 when the mGBA GBA core can be initialized. */
    external fun nativeStatus(): Int

    /**
     * Loads a ROM from an app-readable local path.
     * 4 = loaded, 3 = unsupported/invalid ROM, 2 = file open failed,
     * 1 = core initialization failed, 0 = core creation failed.
     */
    external fun nativeLoadRom(path: String): Int

    /** Runs one GBA frame into a reusable 240x160 ARGB pixel buffer. */
    external fun nativeRunFrame(output: IntArray): Boolean

    /** Opens or creates the per-game battery save file through mGBA. */
    external fun nativeLoadBatterySave(path: String): Boolean

    /** Returns the native PCM sample rate used by the active mGBA core. */
    external fun nativeGetAudioSampleRate(): Int

    /** Reads interleaved stereo PCM samples at the active core's sample rate. */
    external fun nativeReadAudio(output: ShortArray): Int

    /** Discards buffered audio when gameplay is paused or leaves the foreground. */
    external fun nativeClearAudio()

    /** Replaces the active GBA key bitmask. */
    external fun nativeSetKeys(keys: Int)

    /** Adds a pressed key without releasing other simultaneously held keys. */
    external fun nativeAddKeys(keys: Int)

    /** Releases only the supplied key bits. */
    external fun nativeClearKeys(keys: Int)

    /** Returns the four-character GBA game code for the active ROM, or empty when no ROM is loaded. */
    external fun nativeGetGameCode(): String

    /** Returns the active ROM CRC32 as an unsigned value stored in a Long, or zero when unavailable. */
    external fun nativeGetRomCrc32(): Long

    /** Attaches the GBA MULTI SIO driver after the Wi-Fi handshake (0=host/P0, 1=client/P1). */
    external fun nativeLinkAttach(role: Int): Boolean

    /** Updates the transport connection state without waiting for the emulation-core lock. */
    external fun nativeLinkSetConnected(connected: Boolean)

    /** 1=connected, 0=disconnected, -1=link transfer/queue failure. */
    external fun nativeLinkStatus(): Int

    /** Queues a host-to-client GBA MULTI transfer for the native SIO driver. */
    external fun nativeLinkReceiveSerialTransfer(
        transferId: Long,
        cycle: Long,
        baudCode: Int,
        dataOut: Int,
        sioControl: Int,
        rcnt: Int
    ): Boolean

    /** Supplies the client's 16-bit response to the pending host transfer. */
    external fun nativeLinkReceiveSerialResult(
        transferId: Long,
        cycle: Long,
        dataOut: Int,
        sioControl: Int,
        rcnt: Int
    ): Boolean

    /** Returns one outgoing serial event as [kind, id, cycle, baud, data, SIOCNT, RCNT]. */
    external fun nativeLinkTakeOutgoing(): LongArray?

    /** Detaches the native Link driver and clears session queues. */
    external fun nativeLinkDetach()

    /** Stops the active session and releases the native core. */
    external fun nativeUnloadRom()
}
