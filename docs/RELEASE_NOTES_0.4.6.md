# NEXORA 0.4.6 — Visual Touch Layout Editor

## Highlights

- Added a dedicated visual touch-layout editor using the approved NEXORA control atlas.
- D-pad, A, B, L, R, MENU, SELECT and START can each be moved independently and resized with a pinch gesture.
- Changes are saved automatically in the dedicated touch-preferences store; ROMs, battery saves and Save States are not modified.
- Added a reset-to-default action and an opacity control in the editor.
- Reorganized Settings into focused categories so touch customization no longer fills the main Settings page.
- Improved vertical scrolling behavior in Library and History for compact landscape screens.

## Validation status

- Source changes committed to the development branch.
- Android CI and physical-device behavior must be checked before calling this version release-ready.
- Portrait/landscape control bounds and Library/History scrolling require Motorola G54 acceptance testing.

## Scope notes

- The current editor saves one layout shared across orientations. Independent orientation layouts and named profiles are planned follow-up work.
