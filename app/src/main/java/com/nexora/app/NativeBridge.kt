package com.nexora.app

/**
 * Thin JNI boundary. The first native build only verifies that the bridge loads.
 * mGBA is not linked yet; that is the next native integration milestone.
 */
object NativeBridge {
    init {
        System.loadLibrary("nexora_native")
    }

    external fun nativeStatus(): String
}
