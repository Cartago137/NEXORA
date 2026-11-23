# NEXORA 0.4.2 — Stability candidate

## Android gameplay frame transfer
- Replaced the JNI critical-array pin in the native frame loop with a bounded staging buffer and `SetIntArrayRegion`.
- Kept the active-console dimensions and packed framebuffer layout unchanged.
- This is a conservative runtime-stability change; successful CI compilation does not replace a real-device gameplay test.

## Validation status
- Pending Android CI for this commit.
- Pending real-device test on the target Android phone, including opening a GBA ROM and running gameplay.
