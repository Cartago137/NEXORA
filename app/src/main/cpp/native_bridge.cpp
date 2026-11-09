#include <jni.h>
#include <mgba/core/core.h>
#include <mgba/core/interface.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>

#include <array>
#include <fcntl.h>
#include <cstdlib>
#include <mutex>

namespace {
constexpr int kGbaWidth = 240;
constexpr int kGbaHeight = 160;
std::mutex gCoreMutex;
mCore* gActiveCore = nullptr;
bool gRomLoaded = false;
std::array<color_t, kGbaWidth * kGbaHeight> gVideoBuffer{};

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

jint toAndroidArgb(color_t pixel) {
    // mGBA's native color layout is BGR-oriented; Android Bitmap expects ARGB.
    const jint red = static_cast<jint>(M_R8(pixel));
    const jint green = static_cast<jint>(M_G8(pixel));
    const jint blue = static_cast<jint>(M_B8(pixel));
    return static_cast<jint>(0xFF000000u | (static_cast<unsigned>(red) << 16)
        | (static_cast<unsigned>(green) << 8) | static_cast<unsigned>(blue));
}
} // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeStatus(JNIEnv* /* env */, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
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
    JNIEnv* env, jobject /* instance */, jstring path
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
        rom->close(rom);
        core->deinit(core);
        std::free(core);
        return 3;
    }

    core->setVideoBuffer(core, gVideoBuffer.data(), kGbaWidth);
    gActiveCore = core;
    gRomLoaded = true;
    return 4;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_nexora_app_NativeBridge_nativeRunFrame(JNIEnv* env, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore == nullptr || !gRomLoaded) {
        return nullptr;
    }

    gActiveCore->runFrame(gActiveCore);
    jint pixels[kGbaWidth * kGbaHeight];
    for (size_t i = 0; i < gVideoBuffer.size(); ++i) {
        pixels[i] = toAndroidArgb(gVideoBuffer[i]);
    }

    jintArray result = env->NewIntArray(kGbaWidth * kGbaHeight);
    if (result != nullptr) {
        env->SetIntArrayRegion(result, 0, kGbaWidth * kGbaHeight, pixels);
    }
    return result;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeReadAudio(
    JNIEnv* env, jobject /* instance */, jshortArray output
) {
    if (output == nullptr) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore == nullptr || !gRomLoaded) {
        return 0;
    }

    blip_t* left = gActiveCore->getAudioChannel(gActiveCore, 0);
    blip_t* right = gActiveCore->getAudioChannel(gActiveCore, 1);
    if (left == nullptr || right == nullptr) {
        return 0;
    }

    constexpr double kOutputSampleRate = 44100.0;
    const int32_t clockRate = gActiveCore->frequency(gActiveCore);
    blip_set_rates(left, clockRate, kOutputSampleRate);
    blip_set_rates(right, clockRate, kOutputSampleRate);

    jshort* samples = env->GetShortArrayElements(output, nullptr);
    if (samples == nullptr) {
        return 0;
    }

    const jsize capacityFrames = env->GetArrayLength(output) / 2;
    int available = blip_samples_avail(left);
    if (available > capacityFrames) {
        available = capacityFrames;
    }

    if (available > 0) {
        blip_read_samples(left, samples, available, 1);
        blip_read_samples(right, samples + 1, available, 1);
    }

    env->ReleaseShortArrayElements(output, samples, 0);
    return available;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeSetKeys(
    JNIEnv* /* env */, jobject /* instance */, jint keys
) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore != nullptr && gRomLoaded) {
        gActiveCore->setKeys(gActiveCore, static_cast<uint32_t>(keys));
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeUnloadRom(JNIEnv* /* env */, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    unloadActiveCore();
}
