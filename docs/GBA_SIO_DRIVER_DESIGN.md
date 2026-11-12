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
- mGBA's in-process `GBASIOLockstep` is a useful behavioral reference:
  it collects each player's send word, coordinates transfer phases, fills
  receive registers, clears BUSY, sets player ID, and raises the serial IRQ.

## Critical design constraint

The current Android frame loop calls `mCore->runFrame` while holding
`gCoreMutex`. A SIO callback must never perform blocking socket I/O or wait
for a remote device: doing so would stall rendering, audio coordination, and
the emulated CPU.

The network reader/writer therefore needs bounded, thread-safe queues. The
native serial side must have an explicit transfer state machine and a defined
policy for a peer that has not supplied the matching transfer yet. The first
implementation must favor correctness and deterministic register behavior
over pretending that a TCP message exchange alone is a Link Cable.

## Work still required

1. Define the native-to-Kotlin transfer queue API and lifecycle.
2. Implement the multiplayer SIO driver's register semantics and timing events.
3. Connect completed local transfers to the existing versioned Wi-Fi protocol.
4. Add timeout/disconnect handling that safely releases a stalled transfer.
5. Add native tests for SIOCNT, SIOMLT_SEND, receive registers, BUSY/READY,
   player IDs, IRQ, and peer loss.
6. Add a minimal in-app host/join flow and only then produce a test APK.
7. Verify on two physical Android devices with the same FireRed ROM revision,
   entering the Cable Club and completing an actual trade.

## Current status

This is an engineering investigation note, not an implementation claim. The
existing Wi-Fi transport and message codec remain separate from the GBA SIO
driver. Until the work above is implemented and tested, NEXORA does not support
in-game Pokémon trading.
