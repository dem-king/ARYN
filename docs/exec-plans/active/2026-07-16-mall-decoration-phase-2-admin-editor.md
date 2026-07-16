# Mall Decoration Phase 2: Unified Admin Editor

**Parent plan:** [Mall Decoration Practical Upgrade](2026-07-16-mall-decoration-practical-upgrade.md)

## Vue Rules

Use Vue 3 Composition API and `<script setup lang="ts">`. Route views remain composition surfaces. Source state lives in composables, derived state uses `computed`, and child components use typed props down/events up.

## Task 7: Typed APIs and Page Management

**Files:**
- Modify: `aryn-mall-ui/apps/web-ele/src/api/promotion/page-design.ts`
- Test: `.../src/api/promotion/page-design.test.ts`
- Modify: `.../views/promotion/page-design/index.vue`
- Create: `.../page-design/components/{version-dialog.vue,preview-dialog.vue}`
- Modify: `.../src/router/routes/modules/dashboard.ts`

1. Mock `requestClient` in failing tests and assert exact methods/URLs for detail, draft, copy, publish, unpublish, versions, rollback, and preview token.
2. Replace page-design API `any` contracts with transport types separate from schema types.
3. Show page type, draft state, online version, edited/published times, and icon actions for copy, edit, preview, publish/unpublish, history, and delete.
4. Apply permission directives and tooltips. Route both page types to `/page-designer/:id?`; temporarily redirect old editor routes.
5. Run focused Vitest, changed-file lint, typecheck, and build.
6. Commit: `feat: expand decoration page management`.

## Task 8: Editor State and Command History

**Files:**
- Create: `.../views/promotion/page-designer/index.vue`
- Create: `.../page-designer/composables/{use-page-designer.ts,use-command-history.ts,use-draft-save.ts}`
- Test: matching `*.test.ts` files for history and draft save

1. Write failing history tests for add/remove/move/duplicate with new ID, grouped input, undo/redo, 50-step limit, and redo invalidation.
2. Implement pure deterministic commands independent of DOM drag events.
3. Write failing draft tests for dirty detection, debounced autosave, manual save, status transitions, stale revision, unload protection, and no overlapping writes.
4. Implement `usePageDesigner` as the single source for document, selection, history, validation, revision, and publish flow.
5. Run focused tests and typecheck.
6. Commit: `feat: add decoration editor state engine`.

## Task 9: Three-Column Workspace

**Files:**
- Create: `.../page-designer/components/designer-toolbar.vue`
- Create: `component-library.vue`, `page-outline.vue`, `phone-canvas.vue`, `property-panel.vue`, `page-settings.vue`
- Modify: `.../page-designer/index.vue`

1. Before markup, record contracts: toolbar emits commands; library add/insert; outline select/reorder; canvas select/reorder/actions; panels emit typed patches.
2. Implement stable 260px left rail, responsive center, 375px canvas, and 380px right rail. Avoid nested cards; use icons and tooltips for familiar tools.
3. Support click-to-add, pointer drag, keyboard focus, disabled undo/redo, selection, zoom, long-page outline, and visible save state.
4. Start `pnpm dev:ele`; capture 1280x720 and 1920x1080 with browser/Playwright. Check console, overflow, overlap, insertion, and scrolling.
5. Run lint, tests, typecheck, and build.
6. Commit: `feat: build unified decoration workspace`.

## Task 10: Register Existing 11 Components

**Files:**
- Create: `.../page-designer/registry/component-registry.ts` and `.test.ts`
- Create: `.../page-designer/fixtures/all-components-v2.ts`
- Modify: `.../page-design/componentsMap.ts`
- Modify: `.../page-design/components/**/{index.vue,setting.vue}`

1. Write completeness tests: every legacy type has unique key, label, category, version, default factory, preview, settings, and validation; no component starts with `{}`.
2. Implement lazy typed registry definitions. Keep `componentsMap.ts` only as a temporary old-route adapter.
3. Convert settings to typed `modelValue`/complete update contracts. Keep legacy field migration in schema adapter, not individual components.
4. Add one valid v2 fixture per component for admin and later UniApp contract tests.
5. Run registry tests, typecheck, build, and visual fixture review.
6. Commit: `refactor: register existing decoration components`.

## Task 11: Templates, Page Settings, Links, and Publish UX

**Files:**
- Create: `.../page-designer/components/{template-dialog.vue,publish-dialog.vue}`
- Refactor: `.../page-design/components/common/link-url/**`
- Modify: `page-settings.vue`, `use-page-designer.ts`
- Backend: complete tenant template CRUD around Phase 1 entities

1. Write failing tests: applying templates regenerates all component IDs; page settings serialize under `document.page`; structured links validate type/target/path/params.
2. Add read-only system templates and tenant templates protected by manage permission. Copy configuration only, never business records.
3. Replace free text with typed selectors for goods, category, micro-page, coupon/activity, external mini-program, customer service, and custom path. Add v1 link adapter.
4. Preview dialog shows expiring QR code/time. Publish dialog shows change summary and blocking schema/reference errors.
5. Run promotion tests, focused UI tests, typecheck, build, and manual preview-token check.
6. Commit: `feat: add decoration templates and publishing UX`.

## Gate 2

Run:

```bash
cd aryn-mall-ui && pnpm check:type && pnpm test:unit && pnpm lint && pnpm build:ele
```

Visually verify both desktop sizes, 50-component outline/canvas performance, autosave states, stale revision handling, all legacy fixtures, template cloning, preview QR, and blocked/valid publishing. Do not start new components until changed-file failures are zero.

