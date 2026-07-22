# 账户菜单与头像展示调整实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 删除管理后台账户菜单中的三个外部入口，并在没有用户上传头像时隐藏所有相关头像、以用户名保留账户菜单入口。

**Architecture:** 基础布局只向下传递规范化后的真实用户头像地址，不再提供默认头像链接。共享账户菜单和认证/锁屏组件在组件边界根据 `avatar` 是否存在决定是否渲染头像；全局 `VbenAvatar` 保持不变，避免扩大影响范围。

**Tech Stack:** Vue 3、Composition API、`<script setup lang="ts">`、Vitest、Vue TypeScript。

---

### Task 1: 建立账户菜单与头像展示契约测试

**Files:**
- Create: `aryn-mall-ui/apps/web-ele/src/layouts/account-menu-avatar.test.ts`
- Test: `aryn-mall-ui/apps/web-ele/src/layouts/account-menu-avatar.test.ts`

**Step 1: Write the failing test**

新增源码契约测试，分别验证基础布局、账户下拉组件和三个认证/锁屏界面：

```ts
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const root = process.cwd();
const readSource = (path: string) =>
  readFileSync(resolve(root, path), 'utf8');

const basicLayoutSource = readSource('apps/web-ele/src/layouts/basic.vue');
const userDropdownSource = readSource(
  'packages/effects/layouts/src/widgets/user-dropdown/user-dropdown.vue',
);

describe('account menu and avatar contracts', () => {
  it('removes external account menu entries and default avatar URL fallback', () => {
    expect(basicLayoutSource).not.toContain('ARYN_DOC_URL');
    expect(basicLayoutSource).not.toContain('ARYN_GITEE_URL');
    expect(basicLayoutSource).not.toContain('const menus');
    expect(basicLayoutSource).not.toContain(':menus');
    expect(basicLayoutSource).not.toContain('preferences.app.defaultAvatar');
    expect(basicLayoutSource).toContain(
      'userStore.userInfo?.avatar?.trim() || undefined',
    );
  });

  it('uses the username as the dropdown trigger when the avatar is absent', () => {
    expect(userDropdownSource).toContain('<VbenAvatar v-if="avatar"');
    expect(userDropdownSource).toContain('<span v-else');
    expect(userDropdownSource).toContain('{{ text }}');
  });

  it('does not render avatars in account-related dialogs when absent', () => {
    const paths = [
      'packages/effects/layouts/src/widgets/lock-screen/lock-screen.vue',
      'packages/effects/layouts/src/widgets/lock-screen/lock-screen-modal.vue',
      'packages/effects/common-ui/src/ui/authentication/login-expired-modal.vue',
    ];

    for (const path of paths) {
      expect(readSource(path)).toMatch(/<VbenAvatar\s+v-if="avatar"/);
    }
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd aryn-mall-ui && pnpm exec vitest run --dom apps/web-ele/src/layouts/account-menu-avatar.test.ts`

Expected: FAIL；现有代码仍包含外部菜单和默认头像链接，且相关头像未使用 `v-if="avatar"`。

**Step 3: Commit the failing test**

```bash
git add aryn-mall-ui/apps/web-ele/src/layouts/account-menu-avatar.test.ts
git commit -m "test: 增加账户菜单头像展示契约"
```

### Task 2: 移除外部菜单与默认头像链接

**Files:**
- Modify: `aryn-mall-ui/apps/web-ele/src/layouts/basic.vue`
- Modify: `aryn-mall-ui/packages/effects/layouts/src/widgets/user-dropdown/user-dropdown.vue`
- Test: `aryn-mall-ui/apps/web-ele/src/layouts/account-menu-avatar.test.ts`

**Step 1: Remove the three external menu entries**

从基础布局删除 `ARYN_DOC_URL`、`ARYN_GITEE_URL`、三个菜单图标、`openWindow`、`menus` 计算属性和模板上的 `:menus`。

**Step 2: Restrict avatar data to the uploaded address**

将头像计算值改为纯派生值：

```ts
const avatar = computed(() => {
  return userStore.userInfo?.avatar?.trim() || undefined;
});
```

**Step 3: Preserve the account entry without an avatar**

账户菜单触发区使用互斥模板：

```vue
<VbenAvatar
  v-if="avatar"
  :alt="text"
  :src="avatar"
  class="size-8"
  dot
/>
<span v-else class="max-w-32 truncate px-2 text-sm font-medium">
  {{ text }}
</span>
```

下拉头部头像同样增加 `v-if="avatar"`，文本容器仅在头像存在时增加左边距。

**Step 4: Run the focused test**

Run: `cd aryn-mall-ui && pnpm exec vitest run --dom apps/web-ele/src/layouts/account-menu-avatar.test.ts`

Expected: 前两个测试 PASS，认证/锁屏头像测试仍 FAIL。

**Step 5: Commit**

```bash
git add aryn-mall-ui/apps/web-ele/src/layouts/basic.vue aryn-mall-ui/packages/effects/layouts/src/widgets/user-dropdown/user-dropdown.vue
git commit -m "feat: 精简账户菜单并移除默认头像链接"
```

### Task 3: 隐藏认证与锁屏界面的空头像

**Files:**
- Modify: `aryn-mall-ui/packages/effects/layouts/src/widgets/lock-screen/lock-screen.vue`
- Modify: `aryn-mall-ui/packages/effects/layouts/src/widgets/lock-screen/lock-screen-modal.vue`
- Modify: `aryn-mall-ui/packages/effects/common-ui/src/ui/authentication/login-expired-modal.vue`
- Test: `aryn-mall-ui/apps/web-ele/src/layouts/account-menu-avatar.test.ts`

**Step 1: Add conditional rendering**

三个组件的头像节点均使用以下形式：

```vue
<VbenAvatar v-if="avatar" :src="avatar" class="..." />
```

保持表单、用户名、按钮和既有布局行为不变。

**Step 2: Run the focused test**

Run: `cd aryn-mall-ui && pnpm exec vitest run --dom apps/web-ele/src/layouts/account-menu-avatar.test.ts`

Expected: PASS，3 tests passed。

**Step 3: Commit**

```bash
git add aryn-mall-ui/packages/effects/layouts/src/widgets/lock-screen/lock-screen.vue aryn-mall-ui/packages/effects/layouts/src/widgets/lock-screen/lock-screen-modal.vue aryn-mall-ui/packages/effects/common-ui/src/ui/authentication/login-expired-modal.vue
git commit -m "fix: 无头像时隐藏认证与锁屏头像"
```

### Task 4: 完整验证与收尾

**Files:**
- Verify: `aryn-mall-ui/apps/web-ele/src/layouts/account-menu-avatar.test.ts`
- Verify: all modified Vue files

**Step 1: Run the focused unit test**

Run: `cd aryn-mall-ui && pnpm exec vitest run --dom apps/web-ele/src/layouts/account-menu-avatar.test.ts`

Expected: PASS，3 tests passed。

**Step 2: Run type checking**

Run: `cd aryn-mall-ui && pnpm check:type`

Expected: PASS；所有 workspace 类型检查通过。

**Step 3: Run lint for the changed files**

Run: `cd aryn-mall-ui && pnpm exec eslint apps/web-ele/src/layouts/basic.vue apps/web-ele/src/layouts/account-menu-avatar.test.ts packages/effects/layouts/src/widgets/user-dropdown/user-dropdown.vue packages/effects/layouts/src/widgets/lock-screen/lock-screen.vue packages/effects/layouts/src/widgets/lock-screen/lock-screen-modal.vue packages/effects/common-ui/src/ui/authentication/login-expired-modal.vue`

Expected: PASS，无 ESLint 错误。

**Step 4: Inspect the final diff**

Run: `git diff --check && git status --short`

Expected: 本任务文件无空白错误；用户原有未提交改动保持不变。

**Step 5: Record completion**

如需提交额外的格式调整，仅暂存本任务文件并使用中文提交信息，禁止包含用户已有改动。
