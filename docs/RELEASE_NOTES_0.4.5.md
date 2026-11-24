# NEXORA 0.4.5 — Touch customization and landscape navigation

## Changes
- Added persistent touch-control settings, stored separately from game and save data.
- Increased the default D-pad size to 184 dp.
- Added D-pad size and position controls.
- Added A/B, L/R and MENU/SELECT/START size controls.
- Added grouped position controls for D-pad, A/B, L/R and MENU/SELECT/START, plus global touch-control opacity.
- Added reset-to-default for touch settings.
- Applied the same preferences to portrait and landscape player layouts.
- Made the Settings page vertically scrollable and the landscape navigation rail scrollable.
- Enabled page scrolling for empty/filtered-empty Library states while retaining the lazy grid for populated libraries.
- Preserved the existing control atlas and sprite mapping.

## Data safety
No ROM files, library entries, battery saves or save states are migrated, deleted or rewritten by these changes.

## Validation
Automated build and tests pending. Physical-device acceptance is still required for touch sizing, positioning, hit areas, landscape layout and persistence after restart.

## Known next step
The current editor provides grouped positioning for the D-pad and A/B cluster. Free drag placement of every individual control and multiple saved layouts remain planned follow-up work toward full NEXORA customization.
