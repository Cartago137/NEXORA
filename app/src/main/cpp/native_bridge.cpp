#include <jni.h>
#include <mgba/core/core.h>
#include <mgba-util/vfs.h>

#include <fcntl.h>
#include <cstdlib>
#include <mutex>

namespace {
std::mutex gCoreMutex;
mCore* gActiveCore = nullptr;
bool gRomLoaded = false;

void unloadActiveCore() {
    if (gActiveCore == nullptr) {
        return;
    }

    if (gRomLoaded) {
        // mGBA owns the VFile after a successful loadROM call.
        gActiveCore->unloadROM(gActiveCore);
        gRomLoaded = false;
    }

    gActiveCore->deinit(gActiveCore);
    std::free(gActiveCore);
    gActiveCore = nullptr;
}
} // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeStatus(JNIEnv* /* env */, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);

    // A short integration check; the active game session is managed separately.
    mCore* core = mCoreCreate(mPLATFORM_GBA);
    if (core == nullptr) {
        return 0;
    }

    if (!core->init(core)) {
        std::free(core);
        return 1;
    }

    core->deinit(core);
    std::free(core);
    return 2;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeLoadRom(
    JNIEnv* env,
    jobject /* instance */,
    jstring path
) {
    if (path == nullptr) {
        return 2;
    }

    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    if (nativePath == nullptr) {
        return 2;
    }

    std::lock_guard<std::mutex> lock(gCoreMutex);
    unloadActiveCore();

    VFile* rom = VFileOpen(nativePath, O_RDONLY);
    env->ReleaseStringUTFChars(path, nativePath);

    if (rom == nullptr) {
        return 2;
    }

    mCore* core = mCoreCreate(mPLATFORM_GBA);
    if (core == nullptr) {
        rom->close(rom);
        return 0;
    }

    if (!core->init(core)) {
        rom->close(rom);
        std::free(core);
        return 1;
    }

    if (!core->loadROM(core, rom)) {
        // Ownership transfers to mGBA only when loadROM succeeds.
        rom->close(rom);
        core->deinit(core);
        std::free(core);
        return 3;
    }

    gActiveCore = core;
    gRomLoaded = true;
    return 4;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeUnloadRom(
    JNIEnv* /* env */,
    jobject /* instance */
) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    unloadActiveCore();
}
