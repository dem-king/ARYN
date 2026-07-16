# Mall Decoration Phase 3: Retail Components and Cross-Client Release

**Parent plan:** [Mall Decoration Practical Upgrade](2026-07-16-mall-decoration-practical-upgrade.md)

## Task 12: Add Six Retail Admin Components

**Files:**
- Create under `aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/components/`:
  `goods-group/`, `goods-ranking/`, `limited-activity/`, `countdown/`, `marketing-entry/`, `shop-info/`, each with `index.vue`, `setting.vue`, and `types.ts`
- Modify: `.../page-designer/registry/component-registry.ts` and tests/fixtures

1. Define failing default/validation tests for data source, max count, empty/invalid strategy, and common style fields.
2. Implement one vertical slice at a time: types/default, registry, settings, preview states, focused test.
3. Use product APIs for grouping/ranking, promotion APIs for activities, and tenant/shop APIs for shop information. Extend owning API modules if needed; never import another biz module.
4. Verify editor placeholder, loading, data, empty, invalid, and request failure. Do not serialize admin error hints.
5. Run focused tests, typecheck, lint, build, and fixture visual review.
6. Commit: `feat: add retail decoration components`.

## Task 13: Replace UniApp Conditional Rendering with a Registry

**Files:**
- Modify: `aryn-mall-uniapp/src/api/promotion/pageDesign.ts`
- Modify: `aryn-mall-uniapp/src/components/diy/index.vue`
- Create: `.../components/diy/{registry.ts,unknown-component.vue}`
- Modify: `.../pages/home/index.vue`, `.../sub-pages/promotion/diy-page/index.vue`

1. Add a source contract proving the registry contains every admin fixture type, consumes `item.props`, and migrates v1 before rendering.
2. Replace repeated `v-if` with a typed map. Unknown types render nothing in production and log a structured development warning.
3. Apply navigation, background, share metadata, and refresh behavior from `document.page`; isolate platform differences in a composable.
4. Verify old seed content and the all-components v2 fixture under Node 22.
5. Run `pnpm type-check`, `pnpm build:h5`, and `pnpm build:mp-weixin`.
6. Commit: `refactor: add versioned mobile decoration renderer`.

## Task 14: Add Six UniApp Retail Renderers

**Files:**
- Create under `aryn-mall-uniapp/src/components/diy/`:
  `diy-goods-group/`, `diy-goods-ranking/`, `diy-limited-activity/`, `diy-countdown/`, `diy-marketing-entry/`, `diy-shop-info/`
- Modify: `.../components/diy/registry.ts`
- Create/modify: a focused composable under `aryn-mall-uniapp/src/composables/` only for genuinely shared async state

1. Add contract checks matching every admin component type and required prop field.
2. Implement shared loading/empty/error behavior without hiding component-specific data rules.
3. Consume live price, stock, and activity status; respect count, sorting, empty/invalid strategy, ratio, spacing, and radius.
4. Route clicks through one structured-link resolver; verify goods, category, micro-page, activity, customer service, and custom paths.
5. Capture H5/WeChat loading, data, empty, invalid, and long-page states. Check overflow, overlap, and layout shift.
6. Run type-check and both builds; commit `feat: render retail decoration components`.

## Task 15: Permissions, E2E, Migration Rehearsal, and Docs

**Files:**
- Modify: `aryn-mall-java/db/boot/2aryn_boot.sql`, `aryn-mall-java/db/cloud/2aryn_upms.sql`
- Test: `.../promotion-biz/src/test/java/com/aryn/cloud/promotion/controller/PageDesignControllerTest.java`
- Create E2E in the existing Playwright package/convention after locating it; do not invent an app-local convention if none exists
- Modify: `docs/20-业务与数据/{功能到代码索引.md,数据模型.md}`
- Modify: `docs/40-接口与风险/接口映射.md`
- Modify: `docs/90-记录归档/需求记录.md`

1. Add Boot/Cloud permission seed parity for view, draft edit, publish/unpublish, rollback, delete, and template management; check IDs/parents.
2. Add controller tests proving every admin endpoint's permission and public/preview access rules.
3. Add E2E: create micro-page, apply template, add/reorder/duplicate, save draft, prove public unchanged, preview, publish, load app page, rollback, and reject a second tenant.
4. On a disposable seed database apply both migrations; verify v1 render, v2 save/publish, rollback, row counts, and orphan checks.
5. Run full commands from the parent plan plus `git diff --check`; distinguish baseline blockers from regressions.
6. Document v2 schema, tables, endpoints, permissions, migration, rollback, and actual verification. Archive the requirement and move plans to `completed/` only after success.
7. Commit task-owned files: `test: verify decoration publishing workflow`.

## Gate 3

Release only when admin/H5/WeChat fixtures agree, legacy migration retains order/content/links, invalid data never whitescreens, tenant artifacts cannot cross boundaries, 50-component pages remain usable, E2E passes, and migration rollback has been rehearsed.
