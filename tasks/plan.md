# Badger-Android UI / V1 / Legacy Cleanup — Implementation Plan

**Branch:** `refactor/dev-cleanup-2026-08-31`  
**Base:** `dev`  
**Scope:** Global UI cleanup, V1 visual reconciliation, legacy UI/architecture retirement, responsibility-oriented decomposition.

## Overview

The current codebase is a V2 architecture with V1 data compatibility, but the UI still contains mixed-era presentation, compatibility wrappers, oversized Composables, stale routes and placeholder interactions. This plan deliberately separates **visual restoration**, **dead UI removal**, **architecture cleanup**, and **file decomposition** so that we do not reintroduce the old architecture while recovering useful V1 UX.

## Findings from the read-only audit

### High-risk / high-value files

| Area | Evidence | Classification |
|---|---|---|
| `App.kt` | ~27 KB; startup/auth/onboarding/deeplink/Pager/navigation/blur all in one file | Refactor |
| `PersonPage.kt` | ~43 KB | Refactor |
| `ContactDetailPage.kt` | ~44.6 KB; 20+ local UI state variables | Refactor |
| `UserProfileDetailPage.kt` | ~37 KB | Refactor |
| `CardPage.kt` | ~36.1 KB | Refactor |
| `CollectionDetailPage.kt` | ~34.7 KB | Refactor |
| `SocialPage.kt` | mixed route/screen/dialog/picker/NFC/QR responsibilities | Polish + refactor |
| `SettingsSubPage.kt` | `UserSettings` route is an empty `{}` branch | Delete candidate |

### Confirmed dead / placeholder UI candidates

1. `SettingsPage.UserSettings` currently resolves to an empty branch in `SettingsSubPage`; it has no screen implementation.
2. Social “更换背景图” still opens a picker/crop flow but ultimately says “暂未支持自定义背景图”; it is a placeholder interaction rather than a real feature.
3. `SocialRoute.navigateToContacts` is explicitly marked `UNUSED_PARAMETER` and is not used by `SocialScreen`; verify all consumers before removal.

### Legacy architecture observations

- V1 Room entities/DAOs remain intentionally for compatibility; they must not be removed solely because V2 exists.
- `KoinComponentBy` remains as a compatibility bridge with UI consumers still listed in `AGENTS.md`; migrate consumers before deleting the bridge.
- Custom `AppNavigator` is the active navigation system. The repository includes Navigation Compose / Navigation 3 skills, but there is no current requirement to migrate navigation just because the skill exists.
- UI and state should converge on Route → Screen → ViewModel → UseCase/Repository boundaries. The Route-Screen pattern in the repository's Compose skills is the target shape.

### V1 / historical visual evidence

The `dev` history contains explicit UI reset/redesign commits for Social, Auth, Setup and core pages. The Social reset commit documents a target structure with TopAppBar actions, profile header, platform chips, platform info card and QR card; the Auth reset commit documents the current hero/segmented-control/form-card direction; Setup has a deliberate six-step flow with locked pager navigation. These historical designs are treated as visual references, not as reasons to restore the old implementation wholesale.

## Architecture decisions

1. **Preserve V2 data/sync architecture.** V1 data compatibility stays until active consumers are migrated and the compatibility need is proven gone.
2. **Keep the custom navigator for this pass.** Refactor responsibilities around it before considering a navigation-library migration.
3. **Prefer Route + Screen + focused UI modules.** Route owns ViewModel acquisition and navigation callbacks; Screen is stateless/presentational where practical.
4. **Delete fake UI before adding new features.** Placeholder interactions that cannot complete a real workflow should disappear rather than gain more code.
5. **Recover V1 visual strengths without copying V1 code.** Port layout intent, hierarchy, spacing and interaction patterns, not old state/data plumbing.
6. **No mechanical file splitting.** A new file must own a coherent responsibility and reduce the concepts a reader needs to hold.

## Task List

### Phase 1 — Complete audit and inventory

### Task 1: Build UI reachability map
**Description:** Trace top-level routes, settings routes, deep links, dialogs and external entry points to identify real and orphaned UI.

**Acceptance criteria:**
- [ ] Every `Route` / `SettingsPage` has a recorded consumer and destination.
- [ ] Dead/empty/placeholder candidates are separated from compatibility-only code.
- [ ] External Intent/DeepLink/reflective consumers are checked before deletion.

**Verification:** Search references and inspect route dispatchers.

**Dependencies:** None

**Files likely touched:** `tasks/*`, `UI_REVIEW_PLAN_2026-09-01.md`

**Estimated scope:** M

### Task 2: Build V1 visual comparison matrix
**Description:** Compare historical `dev` UI reset commits with current implementations for Social/Auth/Setup/Person/Card/ContactDetail/Settings/Scanner.

**Acceptance criteria:**
- [ ] Each target screen has keep/restore/drop notes.
- [ ] Visual behavior is distinguished from old data/architecture implementation.
- [ ] Regressions introduced by recent cleanup are listed separately.

**Verification:** Historical commit inspection and current source comparison.

**Dependencies:** Task 1

**Estimated scope:** M

### Task 3: Confirm legacy consumer graph
**Description:** Trace `KoinComponentBy`, V1 entities/DAOs, old helpers and compatibility wrappers to active consumers.

**Acceptance criteria:**
- [ ] Every retained compatibility layer has a named active consumer/reason.
- [ ] Every removable shim has a migration target.
- [ ] No compatibility code is deleted before consumer migration.

**Verification:** Repository-wide symbol/reference search.

**Dependencies:** Task 1

**Estimated scope:** M

### Checkpoint — Audit complete
- [ ] Reachability map complete
- [ ] V1 visual matrix complete
- [ ] Legacy consumer graph complete

### Phase 2 — Remove / repair invalid UI

### Task 4: Remove empty `UserSettings` destination
**Description:** Remove the empty route branch and its route entry only after reference verification shows it is not a supported external destination.

**Acceptance criteria:**
- [ ] No navigation path points to an empty destination.
- [ ] `SettingsSubPage` has no no-op branch.
- [ ] Build passes.

**Dependencies:** Task 1

**Estimated scope:** S

### Task 5: Remove or fully implement Social background picker
**Description:** Treat the current picker/crop flow as invalid placeholder UX; unless a real `backgroundURL` contract exists, remove the fake interaction and its orphaned state/imports.

**Acceptance criteria:**
- [ ] No menu item opens an interaction that cannot persist its result.
- [ ] All related dead state and imports disappear.
- [ ] Existing profile/platform/NFC/QR flows remain unchanged.

**Dependencies:** Task 1, Task 2

**Estimated scope:** M

### Task 6: Remove stale compatibility callback(s)
**Description:** Remove obsolete UI callback parameters such as `navigateToContacts` only after repository-wide reference checks.

**Acceptance criteria:**
- [ ] No production/test consumer requires the callback.
- [ ] Route and Screen signatures are simpler.
- [ ] Build/tests pass.

**Dependencies:** Task 1

**Estimated scope:** S

### Phase 3 — Responsibility-oriented decomposition

### Task 7: Split App root orchestration
**Description:** Extract app bootstrap, deep-link handling, main-tab container, navigation dispatch, and visual-effect lifecycle from `App.kt`.

**Acceptance criteria:**
- [ ] `App.kt` becomes a thin application composition root.
- [ ] No DB/network writes remain inside root UI composition.
- [ ] Navigation behavior and state preservation remain unchanged.

**Dependencies:** Tasks 4-6

**Estimated scope:** L

### Task 8: Split Person page
**Description:** Separate list content, toolbar/search, selection mode, dialogs and page coordination while keeping Paging/state behavior intact.

**Acceptance criteria:**
- [ ] Page is orchestration-focused.
- [ ] List/search/selection responsibilities have focused owners.
- [ ] Paging and scroll state are preserved across detail navigation.

**Dependencies:** Task 7

**Estimated scope:** L

### Task 9: Split ContactDetail / UserProfileDetail
**Description:** Reduce mutable UI state concentration by extracting action bar, profile header, field sections, platform sections, dialogs/sheets and image actions. Move any remaining DB/network work out of Composables into ViewModel/UseCase boundaries.

**Acceptance criteria:**
- [ ] No business/database/network action is performed directly by the Screen.
- [ ] Special modes have explicit BackHandler semantics.
- [ ] Dialog state ownership is centralized and predictable.

**Dependencies:** Task 7

**Estimated scope:** XL → split into smaller sub-slices before implementation

### Task 10: Split Card / CollectionDetail
**Description:** Make page files responsible for orchestration while moving cards, empty states, menus, dialogs and collection-specific sections into focused modules.

**Acceptance criteria:**
- [ ] No large monolithic Composable remains in Card/CollectionDetail.
- [ ] Existing collection flows and scanner navigation remain unchanged.
- [ ] Shared card/dialog patterns are reused rather than duplicated.

**Dependencies:** Task 7

**Estimated scope:** L

### Task 11: Polish and split Social
**Description:** Restore the strongest V1 hierarchy while retaining the corrected platform/QR/NFC behavior; separate route, screen, profile header, platform section and dialog host.

**Acceptance criteria:**
- [ ] Social screen has one clear visual hierarchy.
- [ ] Platform selection/editing does not expose state-machine details to layout code.
- [ ] QR/NFC/profile flows remain functional.

**Dependencies:** Tasks 2, 5, 7

**Estimated scope:** M

### Task 12: Consolidate Settings UI
**Description:** Remove stale routes/entries, then split settings home, settings routing and larger sub-pages by responsibility without changing the existing Miuix design language.

**Acceptance criteria:**
- [ ] No empty destination remains.
- [ ] Settings home only owns home-level state and navigation callbacks.
- [ ] Large sub-pages are decomposed where responsibility is genuinely separate.

**Dependencies:** Tasks 4, 7

**Estimated scope:** L

### Phase 4 — Legacy cleanup

### Task 13: Migrate remaining `KoinComponentBy` UI consumers
**Description:** Move active UI consumers to constructor-injected ViewModels or explicit dependencies, then remove the compatibility bridge only when reference count reaches zero.

**Acceptance criteria:**
- [ ] No UI consumer calls `KoinComponentBy.get<T>()`.
- [ ] `KoinComponentBy` is either deleted or left only for non-UI consumers with an explicit reason.
- [ ] DI startup tests pass.

**Dependencies:** Tasks 7-12

**Estimated scope:** L

### Task 14: Dead-code and duplicate-component sweep
**Description:** Search unused imports, orphaned Composables, duplicate dialogs, old helpers, placeholder states and unreachable navigation after structural refactor.

**Acceptance criteria:**
- [ ] Every deleted symbol has evidence of zero supported consumers.
- [ ] Duplicate patterns are consolidated only when behavior is identical.
- [ ] No stale “removed” compatibility comments remain without purpose.

**Dependencies:** Tasks 4-13

**Estimated scope:** L

### Phase 5 — Verification and final review

### Task 15: UI regression verification
**Description:** Run focused JVM/VM tests, Compose UI checks where available, build verification, and repository-level code review on correctness/readability/architecture/security/performance.

**Acceptance criteria:**
- [ ] Focused tests pass.
- [ ] Debug APK build passes.
- [ ] Critical flows have runtime verification evidence where device tooling is available.
- [ ] Final code review finds no high-confidence unresolved regression.

**Dependencies:** Tasks 1-14

**Estimated scope:** L

### Checkpoint — Complete
- [ ] V1 visual intent reconciled
- [ ] Invalid UI removed
- [ ] App/Page responsibilities separated
- [ ] Legacy compatibility consumers migrated
- [ ] Dead-code sweep complete
- [ ] CI/build verified
- [ ] `CODE_REVIEW_FOLLOWUP_2026-09-01.md` updated

## Known risks

| Risk | Impact | Mitigation |
|---|---|---|
| V1 UI behavior is coupled to V1 data APIs | High | Preserve data compatibility until consumers migrate |
| Large ContactDetail split causes state regressions | High | Split one responsibility at a time and keep focused tests |
| Placeholder UI deletion breaks hidden external entry point | Medium | Reference + Intent + DeepLink check before deletion |
| Custom navigator assumptions are spread through App | Medium | Extract orchestration first; do not migrate navigation library in same pass |
| Visual regressions are hard to detect without device UI | Medium | Use source/history comparison plus available layout/Compose tests; never use prohibited screenshot tooling |
