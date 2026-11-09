package com.nexora.app

/**
 * Small JNI boundary for the GBA engine.
 *
 * ROM loading is the first session milestone. Rendering, audio and input are
 * intentionally handled by later layers rather than being mixed into this API.
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

    /** Stops the active session and releases the native core. */
    external fun nativeUnloadRom()
}
