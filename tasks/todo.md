# Badger-Android UI / V1 / Legacy Cleanup — Task List

Branch: `refactor/dev-cleanup-2026-08-31`

## Phase 1 — Audit
- [ ] T1 UI reachability map (structural inventory complete; final external/reflective reference closure still pending)
- [ ] T2 V1 visual comparison matrix (historical reset commits identified; per-screen target matrix still pending)
- [ ] T3 Legacy consumer graph (known V1/V2 boundary + Koin bridge mapped; exhaustive consumer closure still pending)
- [ ] Checkpoint: audit complete

## Phase 2 — Invalid UI
- [x] T4 Remove empty `SettingsPage.UserSettings`
- [x] T5 Remove Social background placeholder interaction
- [x] T6 Remove stale Social `navigateToContacts` compatibility callback (last production caller was removed during App/main-tab split)
- [x] Checkpoint: invalid UI cleanup complete

## Phase 3 — Responsibility decomposition
- [x] T7 Split App root orchestration
  - [x] Secondary route dispatch extracted to `AppRouteHost`
  - [x] Main tab container extracted to `AppMainTabs`
  - [x] Deep-link side effects extracted to `AppDeepLinkEffect`
  - [x] Blur/lifecycle state extracted to `AppVisualEffects`
  - [x] Profile import network/repository work moved behind `ImportProfileFieldsUseCase` + `AppViewModel`
- [ ] T8 Split Person page
  - [x] Removed obsolete `_contactsLoadedFromDb` state from `PersonViewModel`
  - [x] Preserved Room Flow as the single source of truth for the contact list
  - [x] Removed obsolete Paging-only `PersonDeleteFilterPolicyTest` chain
  - [x] Added navigation regression coverage while continuing the structural split
  - [x] Identified and documented stale letter-header state mutation in `PersonPage`
  - [ ] Extract remaining PersonScreen UI blocks into focused components
  - [ ] Remove stale Paging terminology/comments where no longer applicable
  - [ ] Apply/finalize letter-header state fix in the PersonScreen split
- [ ] T9 Split ContactDetail / UserProfileDetail
- [ ] T10 Split Card / CollectionDetail
- [ ] T11 Polish + split Social
- [ ] T12 Consolidate Settings UI
- [ ] Checkpoint: core UI responsibilities separated

## Phase 4 — Legacy cleanup
- [ ] T13 Migrate remaining `KoinComponentBy` UI consumers
- [x] T14 Dead-code / duplicate-component sweep (first pass: removed unused `EmptyStateView` compatibility shim and duplicate `douyin` platform-color entry)
- [ ] Checkpoint: zero removable UI shims without a reason

## Phase 5 — Verification
- [ ] T15 JVM/VM/Compose regression verification
- [ ] Debug APK build passes
- [ ] Runtime verification where device tooling is available
- [ ] Final code review
- [ ] Update `CODE_REVIEW_FOLLOWUP_2026-09-01.md`
