package com.nexora.app

/**
 * JNI boundary for the mGBA GBA and GB/GBC engines. Rendering and input stay
 * behind this API so console-specific details do not leak into Compose.
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

    /** Active frame dimensions; GB/GBC normally use 160x144, GBA uses 240x160. */
    external fun nativeGetVideoWidth(): Int
    external fun nativeGetVideoHeight(): Int

    /** Returns 0 for GBA and 1 for the shared GB/GBC core. */
    external fun nativeGetPlatform(): Int

    /** Runs one frame into a tightly packed ARGB pixel buffer of the active dimensions. */
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

    /** Stops the active session and releases the native core. */
    external fun nativeUnloadRom()
}
