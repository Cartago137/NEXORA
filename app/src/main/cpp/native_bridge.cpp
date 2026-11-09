#include <jni.h>
#include <mgba/core/core.h>

#include <cstdlib>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nexora_app_NativeBridge_nativeStatus(JNIEnv* env, jobject /* instance */) {
    // Create and initialize a real mGBA GBA core. This is an integration
    // smoke test only; it does not load a ROM or start emulation yet.
    mCore* core = mCoreCreate(mPLATFORM_GBA);
    if (core == nullptr) {
        return env->NewStringUTF("mGBA is linked, but the GBA core could not be created.");
    }

    if (!core->init(core)) {
        // The GBA core's init routine releases any partially allocated board
        // and CPU memory on failure; the core wrapper itself still needs freeing.
        std::free(core);
        return env->NewStringUTF("mGBA is linked, but GBA core initialization failed.");
    }

    core->deinit(core);
    return env->NewStringUTF("mGBA 0.10.5 linked. GBA core initialization passed.");
}
