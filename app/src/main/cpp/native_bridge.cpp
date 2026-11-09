#include <jni.h>
#include <mgba/core/core.h>

#include <cstdlib>

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeStatus(JNIEnv* /* env */, jobject /* instance */) {
    // Create and initialize a real mGBA GBA core. This is an integration
    // smoke test only; it does not load a ROM or start emulation yet.
    mCore* core = mCoreCreate(mPLATFORM_GBA);
    if (core == nullptr) {
        return 0;
    }

    if (!core->init(core)) {
        // The GBA core's init routine releases partially allocated CPU/board
        // memory on failure; the core wrapper itself still needs freeing.
        std::free(core);
        return 1;
    }

    core->deinit(core);
    return 2;
}
