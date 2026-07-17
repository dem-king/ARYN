# Page Design Review Fixes Implementation Plan

**Status:** Completed on 2026-07-16

> **For Codex:** Execute this plan task-by-task with test-first checks and preserve unrelated worktree changes.

**Goal:** Fix the reviewed homepage decoration, coupon, page-list, and cross-client rendering defects without changing the dedicated-homepage product model.

**Architecture:** Keep `page_design` as the source of truth. Move homepage initialization behind the existing tenant-scoped Redisson lock, keep mobile reads tolerant of historical duplicates, and make the admin editor a thin composition surface backed by a composable and focused panels. Reuse the existing promotion coupon APIs rather than introducing new endpoints.

**Tech Stack:** Spring Boot 3, MyBatis-Plus, Redisson, Vue 3, TypeScript, Element Plus, UniApp, Vitest/static contract checks.

---

### Task 1: Lock homepage initialization and centralize backend behavior

**Files:**
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-biz/pom.xml`
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/service/IPageDesignService.java`
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/service/impl/PageDesignServiceImpl.java`
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/controller/admin/PageDesignController.java`
- Create: `aryn-mall-java/aryn-promotion/aryn-promotion-biz/src/test/java/com/aryn/cloud/promotion/service/impl/PageDesignServiceImplTest.java`

1. Add a failing service test proving that missing homepage initialization acquires the tenant lock and inserts exactly one default homepage.
2. Run the focused test and confirm it fails because `getOrCreateHomePage` does not exist.
3. Add the service method, reuse the existing homepage lock key, and make lock release ownership-safe.
4. Delegate `home-edit` to the service and add `limit 1` to mobile homepage reads so historical duplicates cannot crash `selectOne`.
5. Run the focused service test and promotion module test lifecycle.

### Task 2: Restore coherent micro-page actions

**Files:**
- Modify: `aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/index.vue`

1. Run a source contract check proving the list filters `pageType='0'` while actions require `pageType='1'`.
2. Remove the obsolete set-home action and show delete for micro pages.
3. Re-run the source contract check and confirm no mutually exclusive action remains.

### Task 3: Fix coupon API contracts and mobile styling

**Files:**
- Modify: `aryn-mall-uniapp/src/components/diy/diy-coupon-receive/index.vue`
- Modify: `aryn-mall-uniapp/src/components/diy/diy-category-nav/index.vue`

1. Run contract checks that fail on `/shop/app/couponinfo/available`, `couponInfoId`, and missing category style consumption.
2. Reuse `getPage` from `api/promotion/couponInfo`, pass `current/size`, and submit `{ couponId }`.
3. Map category `commonStyle`, `imgRadius`, and `fontColor` into the UniApp renderer.
4. Re-run the contract checks and mobile type-check when Node 22/type dependencies are available.

### Task 4: Make homepage saving failure-safe

**Files:**
- Create: `aryn-mall-ui/apps/web-ele/src/views/promotion/home-decoration/use-home-decoration.ts`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/promotion/home-decoration/index.vue`

1. Establish a source check that fails because saving has no `finally` and broadcasts before success.
2. Move editor state and actions into `useHomeDecoration`, implement saving with `async/await`, reset loading in `finally`, and broadcast only after a successful update.
3. Re-run the source check and the web-ele type checker.

### Task 5: Split the homepage editor and repair carousel typing

**Files:**
- Create: `aryn-mall-ui/apps/web-ele/src/views/promotion/home-decoration/components/component-panel.vue`
- Create: `aryn-mall-ui/apps/web-ele/src/views/promotion/home-decoration/components/phone-preview.vue`
- Create: `aryn-mall-ui/apps/web-ele/src/views/promotion/home-decoration/components/settings-panel.vue`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/promotion/home-decoration/index.vue`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/components/swiper-banner/index.vue`

1. Confirm the route component exceeds 500 lines and `vue-tsc` reports the invalid carousel prop.
2. Extract the three panel responsibilities with typed props/emits and scope the shared layout styles under the route page wrapper.
3. Remove the invalid `indicator-position='inside'` value and bind supported carousel settings.
4. Confirm every new file is below 500 lines and the changed-file type-check output is clean.

### Task 6: Final verification

1. Run `mvn test -pl aryn-promotion/aryn-promotion-biz -am`.
2. Run `pnpm --filter @vben/web-ele exec vue-tsc --noEmit --skipLibCheck` and distinguish repository baseline errors from changed-file errors.
3. Run `pnpm test:unit` for the admin workspace.
4. Run `pnpm type-check` in UniApp and report environment blockers precisely.
5. Run `git diff --check` and inspect only task-owned diffs.

## Execution Result

- Homepage initialization and switching now run in the service behind the tenant lock; cache eviction occurs after commit and lock release is ownership-safe.
- Concurrent initialization is covered with two executor tasks backed by a real `ReentrantLock`; both calls return the same page and insert once.
- First-time homepage creation evicts the prior negative cache.
- Admin page actions, coupon contracts, mobile DIY styles, save ordering, carousel typing, and editor component boundaries were corrected.
- Promotion tests, admin task-file ESLint/Prettier/Stylelint, admin unit/build, changed-file type checks, mobile task-file ESLint, source contracts, and `git diff --check` passed.
- Full `aryn-boot` tests remain blocked in `aryn-user-biz` by missing JUnit/Mockito test dependencies.
- UniApp type-check remains blocked by Node 20 (requires Node 22) and the missing `uni-echarts/global` type definition.
