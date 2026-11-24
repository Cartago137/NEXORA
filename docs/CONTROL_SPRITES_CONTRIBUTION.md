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
