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
- [ ] T6 Remove stale Social `navigateToContacts` compatibility callback (deferred until App/main-tab split removes the last caller)
- [x] Checkpoint: invalid UI partial cleanup complete; T6 intentionally deferred

## Phase 3 — Responsibility decomposition
- [ ] T7 Split App root orchestration
- [ ] T8 Split Person page
- [ ] T9 Split ContactDetail / UserProfileDetail
- [ ] T10 Split Card / CollectionDetail
- [ ] T11 Polish + split Social
- [ ] T12 Consolidate Settings UI
- [ ] Checkpoint: core UI responsibilities separated

## Phase 4 — Legacy cleanup
- [ ] T13 Migrate remaining `KoinComponentBy` UI consumers
- [ ] T14 Dead-code / duplicate-component sweep
- [ ] Checkpoint: zero removable UI shims without a reason

## Phase 5 — Verification
- [ ] T15 JVM/VM/Compose regression verification
- [ ] Debug APK build passes
- [ ] Runtime verification where device tooling is available
- [ ] Final code review
- [ ] Update `CODE_REVIEW_FOLLOWUP_2026-09-01.md`
