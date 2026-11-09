package com.nexora.app

/**
 * Thin JNI boundary for the native GBA core.
 * The status call creates and initializes the core as an integration smoke test.
 */
object NativeBridge {
    init {
        System.loadLibrary("nexora_native")
    }

    /** Returns 2 on success, 1 if core initialization fails, and 0 if creation fails. */
    external fun nativeStatus(): Int
}
