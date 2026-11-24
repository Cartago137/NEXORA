# Control Sprite Contribution

**Date received:** 2026-11-23 (local time; GitHub commit recorded on 2026-11-24 UTC)  
**Contributor:** Cartago137  
**Branch:** `feature/nexora-unified-next-release`  
**Upload commit:** `0fd9d3cd189fe648aa0b27155aec8990e3831578` (`Add files via upload`)

## Contribution

The project owner uploaded the first official NEXORA touch-control sprite assets to the repository. The files are currently stored in `artwork/`:

- D-pad: `dpad_normal.png`
- D-pad pressed states: `dpad_cima_pressionado.png`, `dpad_baixo_pressionado.png`, `dpad_esquerda_pressionado.png`, `dpad_direita_pressionado.png`
- Face buttons: `botao_a.png`, `botao_a_pressionado.png`, `botao_b.png`, `botao_b_pressionado.png`
- Shoulder buttons: `botao_l.png`, `botao_l_pressionado.png`, `botao_r.png`, `botao_r_pressionado.png`
- Additional button artwork: `botao_liso.png`, `botao_liso_pressionado.png`

Total: **15 PNG files**.

## Integration and validation status

- The assets are committed to the development branch, not to `main`.
- The upload commit is part of PR #33.
- The GitHub Actions pull-request build associated with the upload completed successfully.
- The files have not yet been visually inspected for dimensions, transparency, alignment, or consistency.
- They have not yet been integrated into the app or validated on a physical device.
- Existing in-app control assets and behavior must remain unchanged until review and explicit approval of the integration.

## Next steps

1. Inspect every PNG's appearance, dimensions, transparency, and visual consistency.
2. Map each asset to its intended control and pressed state.
3. Integrate the approved assets on the development branch only.
4. Build and test the controls on the target Android device, including touch feedback and continuous D-pad gestures.


## Existing control-rendering architecture reviewed

The current Android player renders controls from the packaged resource
`app/src/main/res/drawable-nodpi/nexora_controls_atlas.webp` through
`NexoraAtlasSprite` in `MainActivity.kt`. The atlas is currently read as a
5-column by 3-row grid of 96×96 cells:

| Cell | Existing use |
|---:|---|
| 0 | D-pad neutral |
| 1–4 | D-pad up, down, left and right |
| 5–6 | MENU normal / pressed |
| 7–8 | A normal / pressed |
| 9–10 | B normal / pressed |
| 11–12 | L normal / pressed |
| 13–14 | R normal / pressed |

The live D-pad updates its visual state while the pointer moves and retains
continuous direction changes/diagonals. The button component switches between
normal and pressed cells while maintaining the native key-down/key-up calls.
The visual editor currently previews the same atlas cells.

## Integration approach

The uploaded standalone PNGs should be integrated only after inspection. The
eventual implementation should preserve the existing touch hit areas, key masks,
pointer lifecycle, drag-to-change-direction behavior, diagonals, editor sizing,
position/opacity preferences, and portrait/landscape layouts. Only the visual
asset source and its state mapping should change unless a separately reviewed
technical need is found.

Do not flatten the individual PNGs into a new atlas or alter their proportions
until their actual dimensions, transparent padding and visual alignment have
been inspected. Keep the current WebP atlas as a safe fallback during
development until the replacement is verified.

## Current review limitation

Repository metadata confirms the 15 PNG paths and their byte sizes, but the
available GitHub file reader cannot decode or return binary PNG contents.
Therefore image dimensions, alpha channels and visual appearance remain
**unverified**. No sprite has been changed in the Android app.
