# Mall Decoration Phase 1: Schema and Publishing Foundation

**Parent plan:** [Mall Decoration Practical Upgrade](2026-07-16-mall-decoration-practical-upgrade.md)

## Task 1: Establish Baseline and Impact Boundaries

**Files:** Read the approved requirement, `docs/20-业务与数据/功能到代码索引.md`, `docs/40-接口与风险/接口映射.md`, and all current page-design changes.

1. Run `git status --short`; record user-owned decoration changes and do not restore or reformat them.
2. Use Graphify on `PageDesign`, tenant tables, controllers, menu permissions, and the shared API. Expected: promotion API/biz, boot, admin UI, and UniApp are affected; no biz-to-biz dependency is added.
3. Run `mvn test -pl aryn-promotion/aryn-promotion-biz -am`, UI `pnpm test:unit`, and UniApp `pnpm type-check`; record baseline failures.
4. Commit only if execution notes or paths required correction.

## Task 2: Add the v2 Schema and v1 Adapter

**Files:**
- Create: `aryn-mall-ui/apps/web-ele/src/views/promotion/page-designer/schema/{types.ts,defaults.ts,migrate.ts,migrate.test.ts}`
- Create: `aryn-mall-uniapp/src/components/diy/schema/{types.ts,migrate.ts}`

1. Write failing Vitest cases proving v1 `formData` becomes v2 `props`, defaults include page settings, v2 input is not mutated, and unknown components round-trip.
2. Run `pnpm vitest run apps/web-ele/src/views/promotion/page-designer/schema/migrate.test.ts --dom`; expect missing-module failure.
3. Define `DecorationDocument`, `PageSettings`, `DecorationComponent<T>`, `DecorationLink`, and `ComponentDefinition<T>` with `schemaVersion: 2` and component `version`.
4. Implement pure deterministic `migratePageContent`; keep Vue and HTTP out of schema code.
5. Mirror the root consumer contract in UniApp; component-specific types remain beside components.
6. Run focused Vitest, web typecheck, and UniApp type-check.
7. Commit: `feat: add versioned decoration schema`.

Core assertion:

```ts
expect(migratePageContent({
  components: [{ id: 'a', type: 'notice', formData: { text: 'x' } }],
}).components[0]).toEqual({
  id: 'a', type: 'notice', version: 1, props: { text: 'x' },
});
```

## Task 3: Add Version and Template Persistence

**Files:**
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/PageDesign.java`
- Create: `.../api/entity/PageDesignVersion.java`, `PageDesignTemplate.java`
- Create: `aryn-mall-java/aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/mapper/PageDesignVersionMapper.java`, `PageDesignTemplateMapper.java`
- Create: `aryn-mall-java/db/{boot,cloud}/11page_design_publish.sql`
- Modify: `aryn-mall-java/aryn-boot/src/main/resources/application.yml`
- Modify: `aryn-mall-java/db/cloud/3aryn_nacos.sql`

1. Write migration assertions: `page_design` gains draft revision, schema version, published version pointer/time/status; `page_content` stays draft storage.
2. Require tenant-scoped unique `(tenant_id,page_design_id,version_no)` on versions; both new tables contain `tenant_id`, audit fields, and `del_flag`.
3. Implement matching additive Boot/Cloud migrations and backfill currently published pages as version 1 without overwriting draft JSON.
4. Add Lombok/MyBatis-Plus entities in API and mappers in biz.
5. Add both tables to Boot and promotion Nacos `hx.tenant.tables`.
6. Run `mvn compile -pl aryn-promotion/aryn-promotion-biz -am`; compare both migration files structurally.
7. Commit: `feat: add decoration draft and version storage`.

## Task 4: Save Drafts with Optimistic Locking

**Files:**
- Create: `.../api/dto/PageDesignDraftDTO.java`
- Create: `.../api/vo/PageDesignEditorVO.java`
- Modify: `.../service/IPageDesignService.java`, `.../service/impl/PageDesignServiceImpl.java`, `.../controller/admin/PageDesignController.java`
- Test: `.../src/test/java/com/aryn/cloud/promotion/service/impl/PageDesignServiceImplTest.java`

1. Write failing tests: a matching revision increments once; stale revision throws; public pointer stays unchanged; another tenant cannot update.
2. Run focused Maven test and confirm missing-method failure.
3. Implement one conditional update on ID and `draft_revision`; update count zero means conflict. Do not use read-then-write locking.
4. Add typed editor-detail and draft-save endpoints returning parsed content, revision, publish state, and current version metadata rather than entities.
5. Run focused and full promotion tests.
6. Commit: `feat: add optimistic decoration drafts`.

## Task 5: Publish, Roll Back, and Unpublish

**Files:**
- Create: `.../api/dto/PageDesignPublishDTO.java`, `.../api/vo/PageDesignVersionVO.java`
- Create: `.../service/IPageDesignVersionService.java`, `.../service/impl/PageDesignVersionServiceImpl.java`
- Modify: `PageDesignServiceImpl.java`, `PageDesignController.java`
- Test: `.../service/impl/PageDesignPublishServiceTest.java`

1. Write failing tests: publish creates N+1 and switches atomically; validation failure creates nothing; rollback creates N+1 from history; unpublish keeps history; homepage publish is tenant-serialized.
2. Add a validator interface returning structured schema/reference errors. Product/material checks use owning API/Dubbo contracts, never product-biz imports.
3. In one transaction, recheck draft revision, validate, insert immutable version, and update published pointer. Evict public cache only after commit.
4. Add history, publish, unpublish, and rollback endpoints with separate permission codes.
5. Run all promotion tests.
6. Commit: `feat: add decoration publishing and rollback`.

## Task 6: Published Reads and Secure Preview

**Files:**
- Create: `.../api/vo/AppPageDesignVO.java`
- Create: `.../service/PageDesignPreviewService.java`
- Modify: admin and app `PageDesignController` files
- Test: `.../service/impl/PageDesignPreviewServiceTest.java`

1. Write failing tests: app reads only published snapshots; drafts stay invisible; token binds tenant/page/revision; expired or cross-tenant token fails.
2. Store an opaque random Redis token with short TTL and tenant/page/revision value; do not trust raw query data.
3. Return app VO with page ID/name/type, schema version, parsed content, and published version. Cache keys include tenant and version.
4. Preserve unknown components in payload without server failure.
5. Run all promotion tests and inspect transaction/cache ordering.
6. Commit: `feat: secure decoration preview and public reads`.

## Gate 1

Run promotion tests, entity/tenant Graphify follow-up, SQL parity review, and `git diff --check`. Do not start Phase 2 until draft isolation, stale-write rejection, immutable history, published-only reads, tenant isolation, and preview expiry are proven.
