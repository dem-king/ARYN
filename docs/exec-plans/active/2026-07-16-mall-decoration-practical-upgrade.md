# Mall Decoration Practical Upgrade Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task, `test-driven-development` for behavior changes, and `verification-before-completion` at every phase gate.

**Goal:** Replace the separate homepage and micro-page editors with one versioned workflow supporting drafts, preview, publishing, rollback, templates, efficient editing, and six retail components without breaking existing pages.

**Architecture:** Keep `page_design` as page identity and mutable draft storage, add immutable `page_design_version` snapshots and tenant templates, and separate admin draft APIs from app published-page APIs. Admin Vue and UniApp consume the same v2 schema through separate typed registries; v1 `{ components, formData }` payloads are adapted on read and upgraded on save.

**Tech Stack:** Spring Boot 3, MyBatis-Plus, Redis/Redisson, Sa-Token, Vue 3, TypeScript, Element Plus, Vitest, UniApp, Alova, Maven, pnpm.

**Repository constraints:** Use the current directory only; `AGENTS.md` forbids Git worktrees. Preserve unrelated changes. Run Graphify before changing entities, tenant configuration, menus, or shared APIs. Never import one `*-biz` module from another; cross-domain data uses API/Dubbo contracts or public endpoints.

---

## Execution Order

1. [Phase 1: Schema and Publishing Foundation](2026-07-16-mall-decoration-phase-1-foundation.md)
2. [Phase 2: Unified Admin Editor](2026-07-16-mall-decoration-phase-2-admin-editor.md)
3. [Phase 3: Retail Components and Cross-Client Release](2026-07-16-mall-decoration-phase-3-retail-release.md)

Do not begin a later phase until the previous gate passes. Each task follows the same TDD loop: write a focused failing test, run it and observe the expected failure, implement the minimum behavior, rerun focused and module checks, inspect the diff, then commit only task-owned files.

## Task Map

| Phase | Task | Outcome |
|---|---|---|
| 1 | Baseline and Graphify | Known ownership and baseline blockers |
| 1 | v2 schema and adapter | Typed schema and deterministic v1 migration |
| 1 | Persistence | Draft revision, versions, templates, tenant coverage |
| 1 | Draft service | Optimistic draft saves without published changes |
| 1 | Publish service | Immutable publish, rollback, unpublish, cache safety |
| 1 | Preview/public API | Secure preview tokens and published-only app reads |
| 2 | Page management | Typed APIs and complete lifecycle actions |
| 2 | State engine | History, dirty state, autosave, conflicts |
| 2 | Editor workspace | Three-column editor and responsive visual QA |
| 2 | Legacy registry | Existing 11 components moved to typed definitions |
| 2 | Templates and links | Page settings, structured links, preview/publish UX |
| 3 | Retail admin components | Six common retail component editors/previews |
| 3 | UniApp registry | v1/v2 typed registry rendering and page settings |
| 3 | Retail mobile renderers | Six runtime renderers with live business data |
| 3 | Release hardening | Permissions, E2E, migration rehearsal, docs |

## Phase Gates

- **Gate 1:** Schema migration, tenant tables, optimistic drafts, immutable publishing, preview security, and promotion tests pass.
- **Gate 2:** Unified editor, all 11 existing components, templates, typed links, admin tests, typecheck, build, and desktop visual QA pass.
- **Gate 3:** Six new components render consistently in admin, H5, and WeChat; old pages migrate safely; tenant isolation, E2E, migration rehearsal, and full verification pass.

## Final Verification

```bash
cd aryn-mall-java && mvn test -pl aryn-boot -am
cd aryn-mall-ui && pnpm check:type && pnpm test:unit && pnpm lint && pnpm build:ele
cd aryn-mall-uniapp && pnpm type-check && pnpm build:h5 && pnpm build:mp-weixin
git diff --check
```

Task-owned checks must pass. Existing baseline blockers must be reported with command output and cannot be represented as passing. After all gates pass, update `docs/20-业务与数据/`, `docs/40-接口与风险/`, and `docs/90-记录归档/需求记录.md`, then move all four plan files from `active/` to `completed/`.

