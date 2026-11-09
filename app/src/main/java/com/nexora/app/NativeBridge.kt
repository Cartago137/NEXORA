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

    /** Runs one GBA frame and returns 240x160 ARGB pixels, or null if stopped. */
    external fun nativeRunFrame(): IntArray?

    /** Reads interleaved stereo PCM samples at 44.1 kHz; returns sample frames. */
    external fun nativeReadAudio(output: ShortArray): Int

    /** Replaces the active GBA key bitmask. */
    external fun nativeSetKeys(keys: Int)

    /** Adds a pressed key without releasing other simultaneously held keys. */
    external fun nativeAddKeys(keys: Int)

    /** Releases only the supplied key bits. */
    external fun nativeClearKeys(keys: Int)

    /** Stops the active session and releases the native core. */
    external fun nativeUnloadRom()
}
