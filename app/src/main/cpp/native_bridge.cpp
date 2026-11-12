#include <jni.h>
#include <android/log.h>
#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/interface.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>
#include <mgba/gba/interface.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/io.h>
#include <mgba/internal/gba/sio.h>
#include <mgba/core/timing.h>

#include <condition_variable>
#include <cstdint>
#include <deque>

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


void detachRemoteSioDriverLocked();

void unloadActiveCore() {
    if (gActiveCore == nullptr) {
        return;
    }

    if (gRomLoaded) {
        detachRemoteSioDriverLocked();
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

enum class RemotePacketKind : int64_t {
    SerialTransfer = 1,
    SerialResult = 2
};

struct RemoteLinkPacket {
    RemotePacketKind kind = RemotePacketKind::SerialTransfer;
    uint64_t transferId = 0;
    uint64_t cycle = 0;
    int baudCode = 0;
    uint16_t dataOut = 0;
    uint16_t sioControl = 0;
    uint16_t rcnt = 0;
};

struct RemoteSioDriver {
    GBASIODriver driver{};
    mTimingEvent event{};
    int role = -1;
    bool attached = false;
    bool loaded = false;
    bool transferActive = false;
    uint64_t nextTransferId = 1;
    uint64_t currentTransferId = 0;
    uint64_t lastIncomingTransferId = 0;
    uint64_t currentCycle = 0;
    uint16_t localWord = 0xFFFF;
    uint16_t remoteWord = 0xFFFF;
    int currentBaud = 0;
};

std::mutex gLinkMutex;
std::condition_variable gLinkCondition;
std::deque<RemoteLinkPacket> gLinkOutgoing;
std::deque<RemoteLinkPacket> gLinkIncoming;
RemoteSioDriver gRemoteSio;
bool gLinkConnected = false;
bool gLinkFault = false;
bool gLinkResultReady = false;
uint64_t gLinkResultTransferId = 0;
uint64_t gLinkExpectedTransferId = 0;
uint16_t gLinkResultWord = 0xFFFF;

constexpr size_t kLinkQueueLimit = 32;
constexpr auto kLinkTransferTimeout = std::chrono::milliseconds(4000);

static bool queueLinkOutgoing(const RemoteLinkPacket& packet) {
    std::lock_guard<std::mutex> lock(gLinkMutex);
    if (!gLinkConnected || gLinkFault || gLinkOutgoing.size() >= kLinkQueueLimit) {
        gLinkFault = true;
        gLinkConnected = false;
        gLinkCondition.notify_all();
        return false;
    }
    gLinkOutgoing.push_back(packet);
    if (packet.kind == RemotePacketKind::SerialTransfer) {
        gLinkExpectedTransferId = packet.transferId;
    }
    gLinkCondition.notify_all();
    return true;
}

static void setRemoteSioStatus(GBASIO* sio, int role, bool busy, bool error) {
    uint16_t value = sio->siocnt;
    value &= static_cast<uint16_t>(~((3u << 4) | (1u << 2) | (1u << 3) | (1u << 6) | (1u << 7)));
    value |= static_cast<uint16_t>((role & 0x3) << 4);
    value |= static_cast<uint16_t>(1u << 3); // The remote endpoint is present and ready.
    if (role == 1) value |= static_cast<uint16_t>(1u << 2); // P1 is the multiplayer slave.
    if (busy) value |= static_cast<uint16_t>(1u << 7);
    if (error) value |= static_cast<uint16_t>(1u << 6);
    sio->siocnt = value;
}

static void finishRemoteTransfer(RemoteSioDriver* remote, uint16_t player0Word,
                                 uint16_t player1Word, bool failed) {
    GBASIO* sio = remote->driver.p;
    if (!sio || !sio->p) return;
    GBA* gba = sio->p;

    gba->memory.io[REG_SIOMULTI0 >> 1] = player0Word;
    gba->memory.io[REG_SIOMULTI1 >> 1] = player1Word;
    gba->memory.io[REG_SIOMULTI2 >> 1] = 0xFFFF;
    gba->memory.io[REG_SIOMULTI3 >> 1] = 0xFFFF;
    setRemoteSioStatus(sio, remote->role, false, failed);
    if (sio->siocnt & (1u << 14)) {
        GBARaiseIRQ(gba, GBA_IRQ_SIO, 0);
    }
    remote->transferActive = false;
}

static void remoteSioEvent(struct mTiming* timing, void* context, uint32_t cyclesLate) {
    (void) timing;
    (void) cyclesLate;
    auto* remote = static_cast<RemoteSioDriver*>(context);
    if (!remote || !remote->loaded || !remote->transferActive) return;

    if (remote->role == 0) {
        uint16_t received = 0xFFFF;
        bool completed = false;
        bool hasResult = false;
        {
            std::unique_lock<std::mutex> lock(gLinkMutex);
            completed = gLinkCondition.wait_for(lock, kLinkTransferTimeout, [&] {
                return !gLinkConnected || gLinkFault ||
                    (gLinkResultReady && gLinkResultTransferId == remote->currentTransferId);
            });
            if (completed && gLinkResultReady &&
                gLinkResultTransferId == remote->currentTransferId && !gLinkFault) {
                received = gLinkResultWord;
                hasResult = true;
                gLinkResultReady = false;
                gLinkExpectedTransferId = 0;
            } else {
                gLinkFault = true;
                gLinkConnected = false;
            }
        }
        finishRemoteTransfer(remote, remote->localWord, received, !hasResult);
        gLinkCondition.notify_all();
        return;
    }

    const uint16_t localWord =
        remote->driver.p->p->memory.io[REG_SIOMLT_SEND >> 1];
    finishRemoteTransfer(remote, remote->remoteWord, localWord, false);

    RemoteLinkPacket response;
    response.kind = RemotePacketKind::SerialResult;
    response.transferId = remote->currentTransferId;
    response.cycle = remote->currentCycle;
    response.dataOut = localWord;
    response.sioControl = remote->driver.p->siocnt;
    response.rcnt = remote->driver.p->rcnt;
    queueLinkOutgoing(response);
}

static bool remoteSioInit(GBASIODriver* driver) {
    auto* remote = reinterpret_cast<RemoteSioDriver*>(driver);
    remote->event.context = remote;
    remote->event.name = "NEXORA remote GBA Link Cable";
    remote->event.callback = remoteSioEvent;
    remote->event.priority = 0x80;
    remote->loaded = false;
    remote->transferActive = false;
    return true;
}

static void remoteSioDeinit(GBASIODriver* driver) {
    auto* remote = reinterpret_cast<RemoteSioDriver*>(driver);
    remote->loaded = false;
    remote->transferActive = false;
}

static uint16_t remoteSioWriteRegister(GBASIODriver* driver, uint32_t address, uint16_t value);

static bool remoteSioLoad(GBASIODriver* driver) {
    auto* remote = reinterpret_cast<RemoteSioDriver*>(driver);
    if (!driver->p || remote->role < 0 || remote->role > 1) return false;
    remote->loaded = true;
    remote->transferActive = false;
    driver->writeRegister = remoteSioWriteRegister;
    GBASIO* sio = driver->p;
    setRemoteSioStatus(sio, remote->role, false, false);
    return true;
}

static bool remoteSioUnload(GBASIODriver* driver) {
    auto* remote = reinterpret_cast<RemoteSioDriver*>(driver);
    if (driver->p && driver->p->p &&
        mTimingIsScheduled(&driver->p->p->timing, &remote->event)) {
        mTimingDeschedule(&driver->p->p->timing, &remote->event);
    }
    remote->loaded = false;
    remote->transferActive = false;
    gLinkCondition.notify_all();
    return true;
}

static uint16_t remoteSioWriteRegister(GBASIODriver* driver, uint32_t address, uint16_t value) {
    auto* remote = reinterpret_cast<RemoteSioDriver*>(driver);
    GBASIO* sio = driver->p;
    if (!sio || !sio->p) return value;

    if (address == REG_SIOCNT) {
        value &= 0xFF83;
        value |= sio->siocnt & 0x007C;

        if (remote->role == 0 && remote->loaded && !remote->transferActive &&
            (value & 0x0080)) {
            const int baud = value & 0x3;
            const uint64_t transferId = remote->nextTransferId++;
            const uint64_t cycle = mTimingGlobalTime(&sio->p->timing);
            const uint16_t localWord = sio->p->memory.io[REG_SIOMLT_SEND >> 1];
            RemoteLinkPacket request;
            request.kind = RemotePacketKind::SerialTransfer;
            request.transferId = transferId;
            request.cycle = cycle;
            request.baudCode = baud;
            request.dataOut = localWord;
            request.sioControl = value;
            request.rcnt = sio->rcnt;

            if (transferId == 0 || transferId > 0xFFFFFFFFULL ||
                !queueLinkOutgoing(request)) {
                value &= static_cast<uint16_t>(~0x0080u);
                sio->siocnt = value;
                setRemoteSioStatus(sio, remote->role, false, true);
                return sio->siocnt;
            }

            remote->currentTransferId = transferId;
            remote->currentCycle = cycle;
            remote->currentBaud = baud;
            remote->localWord = localWord;
            remote->transferActive = true;
            sio->siocnt = value;
            sio->rcnt &= static_cast<uint16_t>(~1u);
            setRemoteSioStatus(sio, remote->role, true, false);
            const int32_t cycles = GBASIOCyclesPerTransfer[baud][1];
            mTimingDeschedule(&sio->p->timing, &remote->event);
            mTimingSchedule(&sio->p->timing, &remote->event, cycles);
            return sio->siocnt;
        }

        // The joining endpoint is the hardware slave: it waits for the host's clock.
        if (remote->role == 1 || remote->transferActive) {
            value &= static_cast<uint16_t>(~0x0080u);
        }
        return value;
    }
    return value;
}

static void processRemoteIncomingTransfers() {
    if (!gActiveCore || !gRomLoaded || !gRemoteSio.attached ||
        gRemoteSio.role != 1 || !gRemoteSio.loaded || gRemoteSio.transferActive) {
        return;
    }
    auto* gba = static_cast<GBA*>(gActiveCore->board);
    if (!gba || gba->sio.mode != SIO_MULTI) return;

    RemoteLinkPacket packet;
    {
        std::lock_guard<std::mutex> lock(gLinkMutex);
        if (!gLinkConnected || gLinkFault || gLinkIncoming.empty()) return;
        packet = gLinkIncoming.front();
        if (packet.transferId <= gRemoteSio.lastIncomingTransferId) {
            gLinkIncoming.pop_front();
            return;
        }
        if (packet.transferId != gRemoteSio.lastIncomingTransferId + 1) {
            gLinkFault = true;
            gLinkConnected = false;
            gLinkCondition.notify_all();
            return;
        }
        gLinkIncoming.pop_front();
    }

    gRemoteSio.lastIncomingTransferId = packet.transferId;
    gRemoteSio.currentTransferId = packet.transferId;
    gRemoteSio.currentCycle = packet.cycle;
    gRemoteSio.currentBaud = packet.baudCode;
    gRemoteSio.remoteWord = packet.dataOut;
    gRemoteSio.transferActive = true;
    setRemoteSioStatus(&gba->sio, 1, true, false);
    const int32_t cycles = GBASIOCyclesPerTransfer[packet.baudCode][1];
    mTimingDeschedule(&gba->timing, &gRemoteSio.event);
    mTimingSchedule(&gba->timing, &gRemoteSio.event, cycles);
}

static void detachRemoteSioDriverLocked() {
    if (!gRemoteSio.attached) return;
    if (gActiveCore && gRomLoaded && gActiveCore->board) {
        auto* gba = static_cast<GBA*>(gActiveCore->board);
        GBASIOSetDriver(&gba->sio, nullptr, SIO_MULTI);
    }
    gRemoteSio.attached = false;
    gRemoteSio.loaded = false;
    gRemoteSio.transferActive = false;
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
    // Fast Forward can generate up to three frames of audio before the
    // Android output worker gets scheduled. mGBA stops adding samples when
    // this queue reaches its configured limit, so the normal 2048-frame
    // buffer can drop audio during accelerated batches. Use the GBA core's
    // supported maximum (8192 frames) to provide safe headroom at 3x.
    core->setAudioBufferSize(core, 8192);
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

    processRemoteIncomingTransfers();

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
JNIEXPORT jstring JNICALL
Java_com_nexora_app_NativeBridge_nativeGetGameCode(
    JNIEnv* env, jobject /* instance */
) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore == nullptr || !gRomLoaded || gActiveCore->getGameCode == nullptr) {
        return env->NewStringUTF("");
    }
    char gameCode[5] = {};
    gActiveCore->getGameCode(gActiveCore, gameCode);
    gameCode[4] = '\0';
    return env->NewStringUTF(gameCode);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_nexora_app_NativeBridge_nativeGetRomCrc32(
    JNIEnv* /* env */, jobject /* instance */
) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    if (gActiveCore == nullptr || !gRomLoaded || gActiveCore->checksum == nullptr) {
        return 0;
    }
    uint32_t checksum = 0;
    gActiveCore->checksum(gActiveCore, &checksum, mCHECKSUM_CRC32);
    return static_cast<jlong>(checksum);
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nexora_app_NativeBridge_nativeLinkAttach(JNIEnv*, jobject, jint role) {
    if (role < 0 || role > 1) return JNI_FALSE;
    std::lock_guard<std::mutex> coreLock(gCoreMutex);
    if (!gActiveCore || !gRomLoaded || !gActiveCore->board) return JNI_FALSE;

    detachRemoteSioDriverLocked();
    {
        std::lock_guard<std::mutex> linkLock(gLinkMutex);
        if (!gLinkConnected || gLinkFault) return JNI_FALSE;
        gLinkIncoming.clear();
        gLinkOutgoing.clear();
        gLinkResultReady = false;
        gLinkResultTransferId = 0;
        gLinkExpectedTransferId = 0;
        gLinkResultWord = 0xFFFF;
    }

    gRemoteSio = RemoteSioDriver{};
    gRemoteSio.role = role;
    gRemoteSio.driver.init = remoteSioInit;
    gRemoteSio.driver.deinit = remoteSioDeinit;
    gRemoteSio.driver.load = remoteSioLoad;
    gRemoteSio.driver.unload = remoteSioUnload;
    gRemoteSio.driver.writeRegister = remoteSioWriteRegister;

    auto* gba = static_cast<GBA*>(gActiveCore->board);
    GBASIOSetDriver(&gba->sio, &gRemoteSio.driver, SIO_MULTI);
    gRemoteSio.attached = gba->sio.drivers.multiplayer == &gRemoteSio.driver;
    return gRemoteSio.attached ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeLinkDetach(JNIEnv*, jobject) {
    {
        std::lock_guard<std::mutex> lock(gLinkMutex);
        gLinkConnected = false;
        gLinkIncoming.clear();
        gLinkOutgoing.clear();
        gLinkResultReady = false;
        gLinkExpectedTransferId = 0;
    }
    gLinkCondition.notify_all();
    std::lock_guard<std::mutex> coreLock(gCoreMutex);
    detachRemoteSioDriverLocked();
    gRemoteSio.role = -1;
    {
        std::lock_guard<std::mutex> lock(gLinkMutex);
        gLinkFault = false;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeLinkSetConnected(JNIEnv*, jobject, jboolean connected) {
    {
        std::lock_guard<std::mutex> lock(gLinkMutex);
        gLinkConnected = connected == JNI_TRUE && !gLinkFault;
        if (!gLinkConnected) {
            gLinkIncoming.clear();
            gLinkOutgoing.clear();
        }
    }
    gLinkCondition.notify_all();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nexora_app_NativeBridge_nativeLinkStatus(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gLinkMutex);
    if (gLinkFault) return -1;
    return gLinkConnected ? 1 : 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nexora_app_NativeBridge_nativeLinkReceiveSerialTransfer(
    JNIEnv*, jobject, jlong transferId, jlong cycle, jint baudCode,
    jint dataOut, jint sioControl, jint rcnt) {
    if (transferId <= 0 || static_cast<uint64_t>(transferId) > 0xFFFFFFFFULL ||
        cycle < 0 || baudCode < 0 || baudCode > 3 ||
        dataOut < 0 || dataOut > 0xFFFF ||
        sioControl < 0 || sioControl > 0xFFFF ||
        rcnt < 0 || rcnt > 0xFFFF) {
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> lock(gLinkMutex);
    if (!gLinkConnected || gLinkFault || gRemoteSio.role != 1 ||
        gLinkIncoming.size() >= kLinkQueueLimit) {
        gLinkFault = true;
        gLinkConnected = false;
        gLinkCondition.notify_all();
        return JNI_FALSE;
    }
    RemoteLinkPacket packet;
    packet.kind = RemotePacketKind::SerialTransfer;
    packet.transferId = static_cast<uint64_t>(transferId);
    packet.cycle = static_cast<uint64_t>(cycle);
    packet.baudCode = baudCode;
    packet.dataOut = static_cast<uint16_t>(dataOut);
    packet.sioControl = static_cast<uint16_t>(sioControl);
    packet.rcnt = static_cast<uint16_t>(rcnt);
    gLinkIncoming.push_back(packet);
    gLinkCondition.notify_all();
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nexora_app_NativeBridge_nativeLinkReceiveSerialResult(
    JNIEnv*, jobject, jlong transferId, jlong cycle, jint dataOut,
    jint sioControl, jint rcnt) {
    if (transferId <= 0 || static_cast<uint64_t>(transferId) > 0xFFFFFFFFULL ||
        cycle < 0 || dataOut < 0 || dataOut > 0xFFFF ||
        sioControl < 0 || sioControl > 0xFFFF ||
        rcnt < 0 || rcnt > 0xFFFF) {
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> lock(gLinkMutex);
    if (!gLinkConnected || gLinkFault || gRemoteSio.role != 0 ||
        gLinkExpectedTransferId != static_cast<uint64_t>(transferId)) {
        return JNI_FALSE;
    }
    gLinkResultTransferId = static_cast<uint64_t>(transferId);
    gLinkResultWord = static_cast<uint16_t>(dataOut);
    gLinkResultReady = true;
    gLinkCondition.notify_all();
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jlongArray JNICALL
Java_com_nexora_app_NativeBridge_nativeLinkTakeOutgoing(JNIEnv* env, jobject) {
    RemoteLinkPacket packet;
    {
        std::lock_guard<std::mutex> lock(gLinkMutex);
        if (gLinkOutgoing.empty()) return nullptr;
        packet = gLinkOutgoing.front();
        gLinkOutgoing.pop_front();
    }
    const jlong values[7] = {
        static_cast<jlong>(packet.kind),
        static_cast<jlong>(packet.transferId),
        static_cast<jlong>(packet.cycle),
        static_cast<jlong>(packet.baudCode),
        static_cast<jlong>(packet.dataOut),
        static_cast<jlong>(packet.sioControl),
        static_cast<jlong>(packet.rcnt)
    };
    jlongArray result = env->NewLongArray(7);
    if (!result) return nullptr;
    env->SetLongArrayRegion(result, 0, 7, values);
    if (env->ExceptionCheck()) return nullptr;
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexora_app_NativeBridge_nativeUnloadRom(JNIEnv* /* env */, jobject /* instance */) {
    std::lock_guard<std::mutex> lock(gCoreMutex);
    unloadActiveCore();
}
