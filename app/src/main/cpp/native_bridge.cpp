#include <jni.h>
#include <android/log.h>
#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/interface.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>

#include <array>
#include <algorithm>
#include <chrono>
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
#if !defined(NDEBUG)
uint64_t gFrameLockWaitNanos = 0;
uint64_t gFrameCoreNanos = 0;
uint64_t gFrameTransferNanos = 0;
uint64_t gAudioLockWaitNanos = 0;
uint64_t gAudioReadNanos = 0;
uint64_t gAudioReadCount = 0;
uint64_t gAudioPollCount = 0;
uint64_t gAudioEmptyPollCount = 0;
#endif

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
    if (romSize < 0xC0 || romSize > kMaxRomBytes) {
        rom->close(rom);
        return 3;
    }

    logInfo("ROM load stage 3: ROM size accepted; creating mGBA core.");
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
    core->setVideoBuffer(core, gVideoBuffer.data(), kGbaWidth);
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
    logInfo("ROM load complete: GBA core is ready for frame execution.");
    return 4;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nexora_app_NativeBridge_nativeRunFrame(
    JNIEnv* env, jobject /* instance */, jintArray output
) {
    if (output == nullptr || env->GetArrayLength(output) < kGbaWidth * kGbaHeight) {
        return JNI_FALSE;
    }

#if !defined(NDEBUG)
    const auto callStarted = std::chrono::steady_clock::now();
#endif
    std::lock_guard<std::mutex> lock(gCoreMutex);
#if !defined(NDEBUG)
    const auto lockAcquired = std::chrono::steady_clock::now();
#endif
    if (gActiveCore == nullptr || !gRomLoaded) {
        return JNI_FALSE;
    }

    if (gFrameCount == 0) {
        logInfo("Frame stage 1: entering the first mGBA runFrame call.");
    }
    const auto coreStarted = std::chrono::steady_clock::now();
    gActiveCore->runFrame(gActiveCore);
    const auto coreFinished = std::chrono::steady_clock::now();
    if (gFrameCount == 0) {
        logInfo("Frame stage 2: first mGBA runFrame call returned.");
    }

    // Write converted pixels directly into the Java-owned frame array. This
    // removes the extra native framebuffer and SetIntArrayRegion copy.
    jint* outputPixels = static_cast<jint*>(
        env->GetPrimitiveArrayCritical(output, nullptr)
    );
    if (outputPixels == nullptr) {
        return JNI_FALSE;
    }
    for (size_t i = 0; i < gVideoBuffer.size(); ++i) {
        outputPixels[i] = toAndroidArgb(gVideoBuffer[i]);
    }
    env->ReleasePrimitiveArrayCritical(output, outputPixels, 0);
    const auto transferFinished = std::chrono::steady_clock::now();
    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }
#if !defined(NDEBUG)
    gFrameLockWaitNanos += static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(lockAcquired - callStarted).count()
    );
    gFrameCoreNanos += static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(coreFinished - coreStarted).count()
    );
    gFrameTransferNanos += static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(transferFinished - coreFinished).count()
    );
#endif
    ++gFrameCount;
#if !defined(NDEBUG)
    if (gFrameCount % 300 == 0) {
        __android_log_print(
            ANDROID_LOG_INFO, kLogTag,
            "PERF frames=%llu avg_lock_wait=%.3fms avg_mgba=%.3fms avg_pixel_transfer=%.3fms",
            static_cast<unsigned long long>(gFrameCount),
            gFrameLockWaitNanos / 300000000.0,
            gFrameCoreNanos / 300000000.0,
            gFrameTransferNanos / 300000000.0
        );
        gFrameLockWaitNanos = 0;
        gFrameCoreNanos = 0;
        gFrameTransferNanos = 0;
    }
#endif

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

#if !defined(NDEBUG)
    const auto audioCallStarted = std::chrono::steady_clock::now();
#endif
    std::lock_guard<std::mutex> lock(gCoreMutex);
#if !defined(NDEBUG)
    const auto audioLockAcquired = std::chrono::steady_clock::now();
#endif
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
#if !defined(NDEBUG)
        gAudioLockWaitNanos += static_cast<uint64_t>(
            std::chrono::duration_cast<std::chrono::nanoseconds>(audioLockAcquired - audioCallStarted).count()
        );
        ++gAudioPollCount;
        ++gAudioEmptyPollCount;
        if (gAudioPollCount % 600 == 0) {
            __android_log_print(
                ANDROID_LOG_INFO, kLogTag,
                "PERF audio_polls=%llu empty_polls=%llu avg_lock_wait=%.3fms",
                static_cast<unsigned long long>(gAudioPollCount),
                static_cast<unsigned long long>(gAudioEmptyPollCount),
                gAudioLockWaitNanos / 600000000.0
            );
            gAudioLockWaitNanos = 0;
            gAudioReadNanos = 0;
            gAudioReadCount = 0;
            gAudioEmptyPollCount = 0;
        }
#endif
        return 0;
    }

    jshort* samples = env->GetShortArrayElements(output, nullptr);
    if (samples == nullptr) {
        return 0;
    }

    const int leftRead = blip_read_samples(left, samples, available, 2);
    const int rightRead = blip_read_samples(right, samples + 1, leftRead, 2);
    env->ReleaseShortArrayElements(output, samples, 0);
#if !defined(NDEBUG)
    const auto audioReadFinished = std::chrono::steady_clock::now();
    gAudioLockWaitNanos += static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(audioLockAcquired - audioCallStarted).count()
    );
    gAudioReadNanos += static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(audioReadFinished - audioLockAcquired).count()
    );
    ++gAudioReadCount;
    ++gAudioPollCount;
    if (gAudioPollCount % 600 == 0) {
        __android_log_print(
            ANDROID_LOG_INFO, kLogTag,
            "PERF audio_polls=%llu empty_polls=%llu successful_reads=%llu avg_lock_wait=%.3fms avg_read=%.3fms",
            static_cast<unsigned long long>(gAudioPollCount),
            static_cast<unsigned long long>(gAudioEmptyPollCount),
            static_cast<unsigned long long>(gAudioReadCount),
            gAudioLockWaitNanos / 600000000.0,
            gAudioReadCount > 0 ? gAudioReadNanos / (gAudioReadCount * 1000000.0) : 0.0
        );
        gAudioLockWaitNanos = 0;
        gAudioReadNanos = 0;
        gAudioReadCount = 0;
        gAudioEmptyPollCount = 0;
    }
#endif
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
