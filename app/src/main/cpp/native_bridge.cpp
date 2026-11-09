#include <jni.h>
#include <android/log.h>
#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/interface.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>

#include <array>
#include <fcntl.h>
#include <cstdlib>
#include <mutex>

namespace {
constexpr char kLogTag[] = "NEXORA";
constexpr int kGbaWidth = 240;
constexpr int kGbaHeight = 160;
constexpr off_t kMaxRomBytes = 64 * 1024 * 1024;
std::mutex gCoreMutex;
mCore* gActiveCore = nullptr;
bool gRomLoaded = false;
uint64_t gFrameCount = 0;
std::array<color_t, kGbaWidth * kGbaHeight> gVideoBuffer{};

void logInfo(const char* message) {
    __android_log_write(ANDROID_LOG_INFO, kLogTag, message);
}

void unloadActiveCore() {
    if (gActiveCore == nullptr) {
        return;
    }

    if (gRomLoaded) {
        gActiveCore->unloadROM(gActiveCore);
        gRomLoaded = false;
    }

    mCoreConfigDeinit(&gActiveCore->config);
    // mGBA's GBA deinit implementation releases the core allocation itself.
    gActiveCore->deinit(gActiveCore);
    gActiveCore = nullptr;
    gFrameCount = 0;
}

jint toAndroidArgb(color_t pixel) {
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

    mCoreInitConfig(core, nullptr);
    if (!core->init(core)) {
        mCoreConfigDeinit(&core->config);
        std::free(core);
        return 1;
    }

    mCoreConfigDeinit(&core->config);
    core->deinit(core);
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

    const off_t romSize = rom->size(rom);
    if (romSize < 0xC0 || romSize > kMaxRomBytes) {
        rom->close(rom);
        return 3;
    }

    mCore* core = mCoreCreate(mPLATFORM_GBA);
    if (core == nullptr) {
        rom->close(rom);
        return 0;
    }

    // A filename extension is not proof that the file is a GBA cartridge.
    // Validate the mGBA header before allowing untrusted bytes into the CPU.
    if (core->isROM == nullptr || !core->isROM(rom)) {
        rom->close(rom);
        std::free(core);
        return 3;
    }

    mCoreInitConfig(core, nullptr);
    if (!core->init(core)) {
        mCoreConfigDeinit(&core->config);
        std::free(core);
        rom->close(rom);
        return 1;
    }

    // Match mGBA's own frontend setup order: configure output before loading
    // the ROM, then reset the core after the cartridge has been attached.
    mCoreOptions options{};
    options.volume = 0x100;
    options.useBios = false;
    mCoreConfigLoadDefaults(&core->config, &options);
    mCoreLoadForeignConfig(core, &core->config);
    core->setVideoBuffer(core, gVideoBuffer.data(), kGbaWidth);

    if (!core->loadROM(core, rom)) {
        rom->close(rom);
        mCoreConfigDeinit(&core->config);
        core->deinit(core);
        return 3;
    }

    core->reset(core);
    gActiveCore = core;
    gRomLoaded = true;
    gFrameCount = 0;
    logInfo("GBA core initialized, ROM loaded, video configured, and core reset.");
    return 4;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_nexora_app_NativeBridge_nativeRunFrame(JNIEnv* env, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore == nullptr || !gRomLoaded) {
        return nullptr;
    }

    if (gFrameCount == 0) {
        logInfo("Starting first GBA frame.");
    }
    gActiveCore->runFrame(gActiveCore);

    std::array<jint, kGbaWidth * kGbaHeight> pixels{};
    for (size_t i = 0; i < gVideoBuffer.size(); ++i) {
        pixels[i] = toAndroidArgb(gVideoBuffer[i]);
    }

    jintArray result = env->NewIntArray(kGbaWidth * kGbaHeight);
    if (result == nullptr) {
        return nullptr;
    }
    env->SetIntArrayRegion(result, 0, kGbaWidth * kGbaHeight, pixels.data());
    ++gFrameCount;

    if (gFrameCount == 1) {
        logInfo("First GBA frame completed and copied to the Android framebuffer.");
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
        blip_read_samples(left, samples, available, 2);
        blip_read_samples(right, samples + 1, available, 2);
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
Java_com_nexora_app_NativeBridge_nativeAddKeys(
    JNIEnv* /* env */, jobject /* instance */, jint keys
) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore != nullptr && gRomLoaded) {
        gActiveCore->addKeys(gActiveCore, static_cast<uint32_t>(keys));
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeClearKeys(
    JNIEnv* /* env */, jobject /* instance */, jint keys
) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore != nullptr && gRomLoaded) {
        gActiveCore->clearKeys(gActiveCore, static_cast<uint32_t>(keys));
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeUnloadRom(JNIEnv* /* env */, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    unloadActiveCore();
}
