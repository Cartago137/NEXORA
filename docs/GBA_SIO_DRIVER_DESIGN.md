# GBA SIO driver integration notes

This document records the native integration investigation for NEXORA's first
two-device Link Cable milestone (Pokémon FireRed/LeafGreen).

## Pinned core

NEXORA currently fetches mGBA commit
`26b7884bc25a5933960f3cdcd98bac1ae14d42e2` (0.10.5).

## Confirmed mGBA extension points

- `mCore` exposes its GBA board through the opaque `board` pointer.
- The concrete GBA board contains `struct GBASIO sio` and a cycle scheduler
  (`struct mTiming timing`).
- `GBASIOSetDriver(sio, driver, SIO_MULTI)` installs a custom multiplayer
  serial driver. `GBASIODriver` provides init/deinit/load/unload and
  writeRegister callbacks.
- Multiplayer mode is selected by the SIOCNT mode bits. The game writes
  `SIOMLT_SEND`, starts a transfer through SIOCNT, then reads SIOMULTI0..3
  and status/IRQ state. A valid implementation must reproduce those register
  and timing semantics, not merely exchange payloads.
- mGBA's in-process `GBASIOLockstep` is the behavioral reference. Its
  `GBASIOLockstepNode` driver already implements multiplayer register
  behavior, transfer timing events, BUSY/READY, player IDs, receive registers,
  and serial IRQ delivery.
- `GBASIOLockstep` embeds an `mLockstep` interface with synchronization
  callbacks (`lock/unlock/signal/wait/addCycles/useCycles/unusedCycles/unload`).
  The stock node calls these callbacks from its timing event loop to keep
  emulated participants at the same transfer boundary.

## Key architectural discovery: remote lockstep

The first design considered a bespoke SIO driver with an independent timing
state machine. That would duplicate a substantial amount of mGBA's existing
and subtle serial behavior. The better path is to reuse the pinned core's
`GBASIOLockstepNode` implementation and provide a distributed implementation
of its `mLockstep` synchronization callbacks.

The network reader must run independently of `mCore->runFrame`. It receives
bounded, framed protocol messages and signals a condition variable/event to
wake the emulation thread. The `mLockstep.wait` callback may wait for the
matching remote phase because it runs on the emulation worker, not the Android
UI thread; the socket reader itself must never run inside an SIO callback.
This allows the GBA CPU to stop at the correct transfer boundary until the
remote peer catches up, instead of allowing the local game to run ahead and
apply a late result.

This is a design direction, not yet a completed implementation. In particular,
the distributed callbacks must be proven against the stock node's exact phase
and cycle accounting before enabling them in gameplay.

## Critical design constraints

- The current Android frame loop calls `mCore->runFrame` while holding
  `gCoreMutex`. Native SIO driver callbacks execute under that same core
  execution path. They must not perform blocking socket I/O.
- The lockstep implementation needs bounded queues, explicit disconnect and
  timeout behavior, and strict ordering/sequence numbers for phase messages.
- A remote wait must release the lockstep's own mutex while sleeping and must
  be interruptible by disconnect/unload. It must not hold a mutex needed by
  the network reader while waiting.
- Cycle accounting must be deterministic. Wall-clock arrival time must not be
  substituted for GBA emulated cycles.
- Host and join roles must map consistently to local player IDs 0 and 1.
- The first milestone is two players only. The wire protocol remains prepared
  for up to four players, but this implementation must not imply four-player
  support.

## Implementation sequence

1. Add a native remote-lockstep object with bounded phase/message queues and
   condition-variable-based synchronization callbacks.
2. Reuse `GBASIOLockstep` and `GBASIOLockstepNode` for the GBA SIO register
   and timing behavior; attach the local driver with the correct host/join ID.
3. Add JNI lifecycle methods to create, attach, detach, signal disconnect, and
   exchange serial phase data without blocking the frame loop on socket I/O.
4. Bridge those JNI queues to the existing Kotlin Wi-Fi transport using a
   dedicated network worker.
5. Add native tests for callback ordering, transfer completion, player ID,
   timeout, peer loss, unload while waiting, and no deadlock.
6. Add a minimal host/join screen and automated end-to-end transport tests.
7. Build debug APKs and validate on two physical Android devices using the
   same FireRed ROM revision, entering the Cable Club and completing an actual
   trade.

## Current status

The existing Wi-Fi transport and versioned message codec are implemented.
The native integration is still under development: the distributed lockstep
callbacks, SIO attachment, JNI lifecycle, UI, and physical two-device trade
have not yet been implemented or validated. NEXORA does not currently support
in-game Pokémon trading.
