#include <jni.h>
#include <android/log.h>
#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/interface.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>

#include <array>
#include <algorithm>
#include <fcntl.h>
#include <cstdlib>
#include <mutex>

namespace {
constexpr char kLogTag[] = "NEXORA";
constexpr int kMaxVideoWidth = 256;
constexpr int kMaxVideoHeight = 224;
constexpr off_t kMinRomBytes = 0x100;
constexpr off_t kMaxRomBytes = 64 * 1024 * 1024;
std::mutex gCoreMutex;
mCore* gActiveCore = nullptr;
bool gRomLoaded = false;
uint64_t gFrameCount = 0;
std::array<color_t, kMaxVideoWidth * kMaxVideoHeight> gVideoBuffer{};
std::array<jint, kMaxVideoWidth * kMaxVideoHeight> gAndroidPixelBuffer{};
int gVideoWidth = 240;
int gVideoHeight = 160;
mPlatform gActivePlatform = mPLATFORM_NONE;

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
    gActivePlatform = mPLATFORM_NONE;
    gVideoWidth = 240;
    gVideoHeight = 160;
    gFrameCount = 0;
}

jint toAndroidArgb(color_t pixel) {
#ifdef COLOR_16_BIT
    // In 16-bit builds mGBA stores GBA pixels as RGB555.
    const jint red = static_cast<jint>(M_R8(pixel));
    const jint green = static_cast<jint>(M_G8(pixel));
    const jint blue = static_cast<jint>(M_B8(pixel));
#else
    // In the default 32-bit build mGBA expands RGB555 to BGR byte order:
    // red is in the least-significant byte, blue in bits 16-23.
    // M_R8/M_G8/M_B8 are RGB555 extractors and must not be used here.
    const jint red = static_cast<jint>(pixel & 0xFF);
    const jint green = static_cast<jint>((pixel >> 8) & 0xFF);
    const jint blue = static_cast<jint>((pixel >> 16) & 0xFF);
#endif
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

    logInfo("ROM load stage 4: GBA ROM header accepted; initializing core.");
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
    logInfo("ROM load stage 1: opening cached ROM.");
    unloadActiveCore();

    VFile* rom = VFileOpen(nativePath, O_RDONLY);
    env->ReleaseStringUTFChars(path, nativePath);
    if (rom == nullptr) {
        return 2;
    }

    logInfo("ROM load stage 2: cached ROM opened.");
    const off_t romSize = rom->size(rom);
    if (romSize < kMinRomBytes || romSize > kMaxRomBytes) {
        rom->close(rom);
        return 3;
    }

    // Let mGBA inspect the cartridge header instead of trusting the filename.
    // mPLATFORM_GB covers both original Game Boy and Game Boy Color cartridges.
    const mPlatform platform = mCoreIsCompatible(rom);
    if (platform != mPLATFORM_GBA && platform != mPLATFORM_GB) {
        rom->close(rom);
        return 3;
    }

    logInfo("ROM load stage 3: compatible ROM detected; creating matching mGBA core.");
    mCore* core = mCoreCreate(platform);
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
    logInfo("ROM load stage 5: core initialized; applying frontend configuration.");
    mCoreOptions options{};
    // Leave headroom in the emulator mixer to reduce harsh/clipped output.
    options.volume = 0x60;
    options.useBios = false;
    // No BIOS file is bundled or supplied in this milestone. Explicitly skip
    // the BIOS boot path so the CPU starts at the cartridge entry point.
    options.skipBios = true;
    mCoreConfigLoadDefaults(&core->config, &options);
    mCoreLoadForeignConfig(core, &core->config);
    // Use a maximum-size backing stride so both 240x160 GBA and 160x144 GB/CGB
    // frames (plus a possible 256x224 SGB border mode) fit safely.
    core->setVideoBuffer(core, gVideoBuffer.data(), kMaxVideoWidth);
    // Keep mGBA's normal audio queue size; an oversized queue adds latency
    // without improving the steady frame cadence on Android.
    core->setAudioBufferSize(core, 2048);
    logInfo("ROM load stage 6: video buffer configured.");

    logInfo("ROM load stage 7: passing ROM to mGBA.");
    if (!core->loadROM(core, rom)) {
        rom->close(rom);
        mCoreConfigDeinit(&core->config);
        core->deinit(core);
        return 3;
    }

    logInfo("ROM load stage 8: ROM loaded; resetting CPU and video pipeline.");
    // The GB core may select its model while loading the cartridge, so query
    // the final output dimensions only after loadROM has succeeded.
    unsigned videoWidth = 0;
    unsigned videoHeight = 0;
    core->desiredVideoDimensions(core, &videoWidth, &videoHeight);
    if (videoWidth == 0 || videoHeight == 0
        || videoWidth > kMaxVideoWidth || videoHeight > kMaxVideoHeight) {
        core->unloadROM(core);
        mCoreConfigDeinit(&core->config);
        core->deinit(core);
        return 3;
    }
    gVideoWidth = static_cast<int>(videoWidth);
    gVideoHeight = static_cast<int>(videoHeight);
    gActivePlatform = platform;

    core->reset(core);

    // This pinned mGBA revision exposes audio through two blip channels.
    // Configure their resampling rate once, not on every audio read.
    constexpr double kOutputSampleRate = 44100.0;
    const int32_t audioClockRate = core->frequency(core);
    blip_t* leftAudio = core->getAudioChannel(core, 0);
    blip_t* rightAudio = core->getAudioChannel(core, 1);
    if (leftAudio != nullptr && rightAudio != nullptr && audioClockRate > 0) {
        blip_set_rates(leftAudio, audioClockRate, kOutputSampleRate);
        blip_set_rates(rightAudio, audioClockRate, kOutputSampleRate);
    }

    logInfo("ROM load stage 9: reset complete; publishing active core.");
    gActiveCore = core;
    gRomLoaded = true;
    gFrameCount = 0;
    logInfo(platform == mPLATFORM_GBA
        ? "ROM load complete: GBA core is ready for frame execution."
        : "ROM load complete: GB/GBC core is ready for frame execution.");
    return 4;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nexora_app_NativeBridge_nativeRunFrame(
    JNIEnv* env, jobject /* instance */, jintArray output
) {
    if (output == nullptr || env->GetArrayLength(output) < gVideoWidth * gVideoHeight) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore == nullptr || !gRomLoaded) {
        return JNI_FALSE;
    }

    if (gFrameCount == 0) {
        logInfo("Frame stage 1: entering the first mGBA runFrame call.");
    }
    gActiveCore->runFrame(gActiveCore);
    if (gFrameCount == 0) {
        logInfo("Frame stage 2: first mGBA runFrame call returned.");
    }

    // mGBA renders into a max-width backing buffer. Pack only the active
    // dimensions into Android's tightly packed IntArray.
    for (int y = 0; y < gVideoHeight; ++y) {
        for (int x = 0; x < gVideoWidth; ++x) {
            const size_t sourceIndex = static_cast<size_t>(y) * kMaxVideoWidth + x;
            const size_t targetIndex = static_cast<size_t>(y) * gVideoWidth + x;
            gAndroidPixelBuffer[targetIndex] = toAndroidArgb(gVideoBuffer[sourceIndex]);
        }
    }

    env->SetIntArrayRegion(
        output, 0, gVideoWidth * gVideoHeight, gAndroidPixelBuffer.data()
    );
    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }
    ++gFrameCount;

    if (gFrameCount == 1) {
        logInfo("Frame stage 3: first GBA frame copied to the Android framebuffer.");
    } else if (gFrameCount == 60) {
        logInfo("Frame stage 4: 60 frames completed successfully.");
    }
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nexora_app_NativeBridge_nativeLoadBatterySave(
    JNIEnv* env, jobject /* instance */, jstring path
) {
    if (path == nullptr) {
        return JNI_FALSE;
    }

    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    if (nativePath == nullptr) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(gCoreMutex);
    logInfo("Battery-save stage 1: attempting to open the per-game save file.");
    const bool loaded = gActiveCore != nullptr && gRomLoaded
        && mCoreLoadSaveFile(gActiveCore, nativePath, false);
    logInfo(loaded ? "Battery-save stage 2: save file loaded." : "Battery-save stage 2: no existing save file loaded.");
    env->ReleaseStringUTFChars(path, nativePath);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeGetVideoWidth(JNIEnv* /* env */, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    return gActiveCore != nullptr && gRomLoaded ? gVideoWidth : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeGetVideoHeight(JNIEnv* /* env */, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    return gActiveCore != nullptr && gRomLoaded ? gVideoHeight : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeGetPlatform(JNIEnv* /* env */, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    return gActiveCore != nullptr && gRomLoaded ? static_cast<jint>(gActivePlatform) : -1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeGetAudioSampleRate(
    JNIEnv* /* env */, jobject /* instance */
) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore == nullptr || !gRomLoaded) {
        return 0;
    }
    return 44100;
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
    if (gActiveCore == nullptr || !gRomLoaded || gActiveCore->getAudioChannel == nullptr) {
        return 0;
    }

    blip_t* left = gActiveCore->getAudioChannel(gActiveCore, 0);
    blip_t* right = gActiveCore->getAudioChannel(gActiveCore, 1);
    if (left == nullptr || right == nullptr) {
        return 0;
    }

    const jsize capacityFrames = env->GetArrayLength(output) / 2;
    // Both channels must advance equally to preserve stereo alignment.
    int available = std::min(blip_samples_avail(left), blip_samples_avail(right));
    if (available > capacityFrames) {
        available = capacityFrames;
    }
    if (available <= 0) {
        return 0;
    }

    jshort* samples = env->GetShortArrayElements(output, nullptr);
    if (samples == nullptr) {
        return 0;
    }

    const int leftRead = blip_read_samples(left, samples, available, 2);
    const int rightRead = blip_read_samples(right, samples + 1, leftRead, 2);
    env->ReleaseShortArrayElements(output, samples, 0);
    return rightRead < leftRead ? rightRead : leftRead;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeClearAudio(
    JNIEnv* /* env */, jobject /* instance */
) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore == nullptr || !gRomLoaded || gActiveCore->getAudioChannel == nullptr) {
        return;
    }
    blip_t* left = gActiveCore->getAudioChannel(gActiveCore, 0);
    blip_t* right = gActiveCore->getAudioChannel(gActiveCore, 1);
    if (left != nullptr) blip_clear(left);
    if (right != nullptr) blip_clear(right);
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
