# Settings Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the Settings tab into an intent-ordered screen built on one reusable `SettingRow` component, with app updates moved under Developer tools.

**Architecture:** Add a shared `SettingRow` to `ui/Components.kt`, rewrite `ui/SettingsScreen.kt` onto it with a new card order (Display & playback → Console → Replay compression → Driver profile → Library → Event tags → Developer(+Updates) → About), and restyle `DriverProfileSection` / `LibrarySection` / `ReplayCompressionControls` onto the same row model. No persistence or navigation changes.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Robolectric Compose tests.

**Spec:** `docs/superpowers/specs/2026-11-17-settings-cleanup-design.md`

## Global Constraints

- Dark tokens only via `MaterialTheme.colorScheme.*`; no hardcoded hex.
- Typography per `DESIGN.md`; `NumericText` for numeric values.
- Minimum touch target 44 dp.
- Preserve every existing `testTag` value and all copy asserted by `SettingsScreenTest` (`REPLAY COMPRESSION`, `Balanced`, `Changed: 720p HEVC ~3 Mbps`, update strings, IP validation strings, `DR (B) · SR (S) · updated`, `Fetch profile`).
- `SettingsStore` API is untouched; settings commit immediately as changed.
- Build command: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest'` for this screen; full check before merge is `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Copy style: terse, sentence case, no exclamation marks.

## Review Focus

- IP dialog validation: empty/invalid input keeps the dialog open with `Enter a valid IPv4 address` and does not persist — pinned by the console test in Task 2.
- Update controls must be invisible when `developerTools` is off and present when on (both preview-channel builds) — pinned in Task 2.
- Every toggle row remains a single clickable node (no double-toggle when tapping the switch) — pinned by the existing imperial/haptics tests in Task 2.
- Replay `Advanced` disclosure must expose `replay-resolution/codec/keyframes/audio` tags only when open and keep the preset flow working — pinned in Task 3.
- Dialog dismissal (back/outside tap) must not persist a half-typed address — pinned in Task 2.

---

### Task 1: `SettingRow` component

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/Components.kt`

**Interfaces:**
- Produces:
  - `SettingRow(label: String, modifier: Modifier = Modifier, supporting: String? = null, value: String? = null, trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null)` — label `titleMedium` left, supporting `bodySmall` `onSurfaceVariant` under it, `value` `bodyMedium` right-aligned before `trailing`, row min-height 44 dp, whole row `clickable` when `onClick != null`.
  - `SettingToggleRow(label: String, supporting: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier)` — wraps `SettingRow` with `toggleable(value = checked, role = Role.Switch)` and a `Switch(onCheckedChange = null)` as trailing.
- Consumes: nothing new.

- [ ] **Step 1: Implement both composables in `Components.kt`** (no new test file; behavior is pinned by `SettingsScreenTest` toggles in Task 2).
- [ ] **Step 2: Verify it compiles**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/Components.kt
git commit -m "feat(ui): shared SettingRow and SettingToggleRow"
```

### Task 2: Restructure `SettingsScreen`

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/SettingsScreen.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/lapcraft/app/ui/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `SettingRow`, `SettingToggleRow` (Task 1); existing `SectionCard`, `GhostPill`, `SectionLabel`, `ScreenHeader`, `NumericText`.
- Produces: private `UpdatesContent(update, onCheckUpdate, onDownloadUpdate, onInstallUpdate)` composable; card order and new tags `settings-ps5-edit`, `about-expand`.

Changes:
1. Card order: Display & playback, Console, Replay compression, Developer (preview only), Driver profile, Library, Event tags, About.
2. Display & playback: `SettingToggleRow` for imperial/haptics (haptics supporting text "Ticks as you drag the ruler."), HUD toggle with its existing supporting line; `SettingRow` for Default playback speed and Chart window — row `onClick` opens the existing `DropdownMenu`, trailing chevron `Icons.Filled.KeyboardArrowDown`; keep tags `default-playback-speed`, `chart-window`.
3. Console: `SettingRow("PS5 address", value = savedIp ?: "Not set", tag settings-ps5-edit, onClick opens AlertDialog)`; dialog holds the existing `OutlinedTextField` (tag `settings-ps5-ip`), a `Save IP` confirm, and `Cancel`; validation/normalization unchanged; invalid keeps dialog open with `Enter a valid IPv4 address`; save closes the dialog and the row shows the value.
4. Developer card (preview only): Developer tools + Preview empty library toggles (tags unchanged); when `developerTools` is true render `SectionLabel("Updates")` + `UpdatesContent` (moved from the old Updates card verbatim: status line, action `when`, all copy).
5. About: `SettingRow` collapsed line `Version ${VERSION_NAME} · ${UPDATE_CHANNEL} · ${GIT_SHA}` with tag `about-expand`, `onClick` toggles expansion revealing BuildRow list (Version, Version code, Update channel, Build number, Commit) and the GT GridStats acknowledgements row (tag `ack-gridstats`, existing intent code moved).
6. Delete the standalone Updates and Build cards.

Test edits (`SettingsScreenTest`):
- `@Before` also sets `SettingsStore(context).developerTools = false`.
- `shows an available update and downloads it` and `up to date says so`: set `SettingsStore(context).developerTools = true` before `setContent`.
- `saving the console address normalizes it and rejects invalid input`: rewrite for the dialog — click `settings-ps5-edit`, type into `settings-ps5-ip`, click `Save IP`, assert store value `192.168.1.50`; then reopen, enter `999.1.1.1`, click `Save IP`, assert `Enter a valid IPv4 address` exists and the store is unchanged.

- [ ] **Step 1: Update the three tests as above**
- [ ] **Step 2: Run them to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest'`
Expected: FAIL (Updates card still present unconditionally, no `settings-ps5-edit`)

- [ ] **Step 3: Implement the restructure**
- [ ] **Step 4: Run the suite again**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest'`
Expected: PASS (all tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/SettingsScreen.kt \
  app/src/test/kotlin/io/github/jlai403/lapcraft/app/ui/SettingsScreenTest.kt
git commit -m "feat(settings): intent-ordered cards, dialog IP entry, updates under developer tools"
```

### Task 3: Replay compression `Advanced` disclosure

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/ReplayCompressionControls.kt`

**Interfaces:**
- Consumes: `SettingRow` (Task 1); existing `SettingChoice`, captions, enums.
- Produces: same public signature `ReplayCompressionControls(...)`; new tag `replay-advanced` on the disclosure row.

Changes:
1. Preset row becomes `SettingRow("Preset", value = preset.label, tag replay-preset, onClick opens menu)`.
2. Add a disclosure `SettingRow("Advanced", value = "Resolution · Codec · Keyframes · Audio", tag replay-advanced, onClick toggles)`; when open, render the four existing `SettingChoice` blocks (unchanged internals, captions and `Changed:` logic intact).
3. Import-sync usage (`highlightChanges = true` there too) keeps working: `Advanced` starts collapsed there as well.

- [ ] **Step 1: Implement**
- [ ] **Step 2: Verify existing replay test still passes**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest' --tests '*ImportSync*'`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/ReplayCompressionControls.kt
git commit -m "feat(settings): collapse replay compression detail behind Advanced"
```

### Task 4: Restyle driver profile and library sections

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/DriverProfileSection.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/LibrarySection.kt`

**Interfaces:**
- Consumes: `SettingRow`, `SettingToggleRow`, `SectionLabel` (Task 1).
- Produces: no signature changes; all `settings-profile-*`, `settings-driver-*`, `settings-gridstats-*`, `settings-library-*` tags preserved.

Changes:
1. Driver profile: keep structure and flows; rows (saved drivers) restyled with consistent spacing, `SectionLabel("GT GridStats API key (optional)")` for the key subsection, helper copy trimmed to one line each.
2. Library section: summary line + two GhostPills unchanged (tags kept); trim the backup helper line to one sentence.

- [ ] **Step 1: Implement**
- [ ] **Step 2: Run the settings suite**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest'`
Expected: PASS (profile tests untouched)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/DriverProfileSection.kt \
  app/src/main/kotlin/io/github/jlai403/lapcraft/app/ui/LibrarySection.kt
git commit -m "refactor(settings): restyle profile and library sections onto SettingRow"
```

### Task 5: Docs

**Files:**
- Modify: `DESIGN.md` (Screens → Settings section)
- Modify: `AGENTS.md` (Key conventions — settings notes)

Changes: rewrite the Settings bullet list to the new card order, row model, dialog IP entry, Advanced disclosure, About collapse, and Updates-under-Developer (dev-tools gated, none on stable). Add `SettingRow`/`SettingToggleRow` to the Components table.

- [ ] **Step 1: Update both docs and commit**

```bash
git add DESIGN.md AGENTS.md
git commit -m "docs: settings redesign reference"
```

### Task 6: Full check and merge

- [ ] **Step 1: Run the full check**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Merge to main**

```bash
git checkout main && git merge --no-ff feature/settings-cleanup
```
