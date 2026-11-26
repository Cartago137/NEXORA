# GBA SIO driver integration notes

This document records the current native integration for NEXORA's first two-device Link Cable milestone (Pokémon FireRed/LeafGreen).

## Pinned core

NEXORA fetches mGBA commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2` (0.10.5).

## Confirmed mGBA extension points

- `mCore` exposes its GBA board through the opaque `board` pointer.
- The concrete GBA board contains `struct GBASIO sio` and a cycle scheduler (`struct mTiming timing`).
- `GBASIOSetDriver(sio, driver, SIO_MULTI)` installs a custom multiplayer serial driver. `GBASIODriver` provides init/deinit/load/unload and `writeRegister` callbacks.
- Multiplayer mode uses `SIOMLT_SEND`, `SIOMULTI0..3`, SIOCNT status bits, transfer timing, and the SIO interrupt. The implementation must reproduce these hardware-visible semantics, not merely exchange payloads.
- mGBA's `GBASIOLockstep` / `GBASIOLockstepNode` remains the reference for correct multiplayer register and cycle behavior. The current remote driver is a first direct SIO implementation, not a distributed port of mGBA's full lockstep system.

## Current implementation

The unified branch `feature/nexora-unified-next-release` carries the implementation from the earlier Link branch and integrates it with the latest player UI.

- Host is player P0; joiner is P1.
- Host writes `SIOMLT_SEND`, starts a MULTI transfer, and queues a bounded `SerialTransfer` message.
- Joiner receives the message on the network reader thread. JNI appends it to a bounded native incoming queue; it does not acquire the core mutex.
- At the beginning of the next `nativeRunFrame`, the joiner consumes the queued request, updates its remote SIO state, and schedules the local mGBA timing event for the transfer duration.
- P1 completes its local timing event, writes the received words/status, raises SIO IRQ if enabled, and queues a `SerialResult` response only while the remote session is still connected. If the peer has disconnected, P1 completes with the SIO error state rather than reporting success.
- P0 receives that response into a native result slot. Its timing callback polls the slot every 2048 GBA cycles while SIOCNT remains BUSY.
- P0 has a 4-second transfer deadline. Disconnect, queue fault, invalid sequence, or timeout marks the transfer failed and clears BUSY with an error status.
- The Kotlin socket reader runs on its own daemon thread. The Compose-side pump forwards outgoing native events through the Wi-Fi transport.

## Non-blocking correction (2026-11-16)

The earlier driver used `condition_variable::wait_for` inside P0's mGBA timing callback. Because `nativeRunFrame` holds the core mutex while executing mGBA, this could freeze emulation for the entire network round trip (up to four seconds).

That blocking wait has been removed. The callback now checks the result queue under a short-lived link mutex and reschedules itself for 2048 GBA cycles when no result has arrived. No socket I/O or condition-variable wait occurs inside the callback.

This avoids a long synchronous stall, but it does **not** establish deterministic distributed lockstep. While SIOCNT is BUSY, the local GBA core may continue advancing until the response is applied. Actual Cable Club behavior, timing tolerance, and game-state correctness must be measured on two devices. If the game requires stricter lockstep, the next architecture step is to adapt mGBA's `mLockstep` callbacks rather than reintroducing a blocking network wait into the timing callback.

## Critical constraints

- Never perform socket I/O from a native SIO callback.
- Preserve lock ordering: core mutex before link mutex whenever both are needed. The network reader must never wait for the core mutex.
- Bound incoming/outgoing queues and validate transfer IDs and monotonic sequence.
- Disconnect/unload must interrupt pending transfers and clear keys/status safely.
- Emulated cycles, not wall-clock arrival timestamps, determine local SIO timing.
- The first acceptance target is two players only. The protocol's four-player fields do not imply four-player support.
- Current driver supports GBA MULTI 16-bit only; NORMAL 8/32, UART, GPIO, JOYBUS and RFU are not implemented.

## Validation sequence

1. Build both Android ABIs and run JVM protocol/transport tests.
2. Add native-level tests for transfer IDs, queue overflow, role assignment, timeout, disconnect during a transfer, unload while active, register values, BUSY/READY and IRQ behavior.
3. Run two-device tests with the exact same FireRed ROM/revision.
4. Enter the Cable Club on both devices, complete and confirm a Pokémon trade, and verify both games' battery saves.
5. Measure transfer latency and ensure no visible long pauses or desynchronization. Only after these checks may Link trading be described as functional.
