# Settings cleanup — design

Date: 2026-11-17

## Intent

The Settings tab is a 10-card, ~2,500 dp scroll with three different interaction
patterns for the same kind of setting, loud helper copy, and developer noise
(build info, updates) mixed into everyday content. This change restructures it
into an intent-ordered screen with one reusable row model, quieter copy, and
collapsed detail behind chevrons/disclosures.

## Approaches considered

- **A. Restructure + row model (picked).** Reorder cards, introduce one
  `SettingRow` component, trim helper copy, collapse advanced/build detail.
  Biggest visible improvement, no navigation change.
- B. Polish only — reorder + restyle in place. Smallest diff but the driver
  profile and replay compression cards still dominate.
- C. Settings hub with sub-screens. Scales best but adds taps to simple
  toggles and is a much bigger change.

## Decisions

- Approach A; driver profile stays an inline card (a sheet is a follow-up).
- App updates move inside the Developer card and render only when the
  `Developer tools` toggle is on. On `stable` channel builds there is no
  manual update entry: the automatic launch check and `UpdatePrompt` dialog
  still run.
- Rows are icon-free (v0.1 keeps the icon set minimal).

## Design

### Card order (everyday → setup → data → developer/footer)

1. **Display & playback** — Imperial units, Frame ruler haptics, Telemetry HUD
   (toggle rows); Default playback speed, Chart window (dropdown rows).
2. **Console** — `PS5 address` row: shows the saved value (or `Not set`) with an
   `Edit` affordance; editing opens an `AlertDialog` with the IP field and Save.
   Validation, normalization, and error copy unchanged ("Enter a valid IPv4
   address", "Saved 192.168.1.50").
3. **Replay compression** — `Preset` dropdown row plus an `Advanced` disclosure
   holding Resolution / Codec / Keyframes / Audio (existing
   `ReplayCompressionControls`, `Changed:` captions preserved).
4. **Driver profile** — same capabilities as today (PSN field, fetch/remove,
   saved drivers with per-driver refresh/remove, `Refresh profiles (N)`,
   optional API key subsection), restyled onto the shared row model.
5. **Library** — unchanged content, restyled rows.
6. **Event tags** — toggle + refresh + status, restyled.
7. **Developer** (preview channel only) — Developer tools toggle, Preview empty
   library toggle, and — when Developer tools is on — an `Updates` subsection
   with the existing status line + action states.
8. **About** — collapsed by default to `Version X · channel · sha`; expanded
   shows version code / build number / commit and the GT GridStats
   acknowledgements row.

### Row model

New `SettingRow` in `ui/Components.kt`: label, optional one-line supporting
text, optional value + trailing content (switch, value pill, chevron), whole
row tappable, 44 dp minimum height. All settings rows use it.

- Toggle rows use `toggleable(role = Role.Switch)` with
  `onCheckedChange = null` on the `Switch` so each row is one semantic node
  (fixes the current double-clickable a11y issue).
- Dropdown rows put the menu trigger on the whole row; the trailing chevron is
  decorative.
- Helper copy is at most one line, only where the label is not
  self-explanatory ("Ticks as you drag the ruler." becomes the haptics row's
  supporting text).

### Behaviour preserved

- All `testTag`s keep their values (`imperial`, `haptics`, `telemetry-hud`,
  `default-playback-speed`, `chart-window`, `event-tags`, `dev-preview-empty`,
  `dev-developer-tools`, `settings-ps5-ip`, `settings-profile-*`,
  `settings-driver-*`, `settings-gridstats-*`, `refresh-events`,
  `settings-library-*`, `replay-*`, `ack-gridstats`, plus new
  `settings-ps5-edit`, `replay-advanced`, `about-expand`).
- Copy asserted by tests stays: `REPLAY COMPRESSION`, `Balanced`,
  `Changed: 720p HEVC ~3 Mbps`, update strings, IP validation strings,
  `DR (B) · SR (S) · updated`.
- Settings persist exactly as today (`SettingsStore` untouched).
- Export/import, event refresh, update download/install flows unchanged.

### Test changes

- The two update tests enable `SettingsStore.developerTools = true` first;
  `@Before` resets it.
- Existing row-level tests pass unchanged (tags and copy preserved).

### Docs

- `DESIGN.md` Settings section rewritten to the new card order and row model;
  Updates bullet moves under Developer.
- `AGENTS.md` updated for the new component and any convention notes.

## Files touched

- `app/.../ui/Components.kt` — add `SettingRow`.
- `app/.../ui/SettingsScreen.kt` — restructure; extract `UpdatesContent`.
- `app/.../ui/ReplayCompressionControls.kt` — Advanced disclosure support.
- `app/.../ui/DriverProfileSection.kt` — row restyle.
- `app/.../ui/LibrarySection.kt` — row restyle (minor).
- `app/src/test/.../SettingsScreenTest.kt` — developer tools for update tests.
- `DESIGN.md`, `AGENTS.md`.

## Non-goals

- No sub-screen navigation, no search, no icons, no driver profile sheet.
- No SettingsStore schema or persistence changes.
