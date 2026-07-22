# 登录验证码错位修复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复登录页滑块验证码的拼图块与滑块按钮脱离弹窗、相对页面错位的问题。

**Architecture:** 保留验证码包装组件的 scoped 样式隔离，仅将需要作用于动态子组件 DOM 的选择器改为 `:deep(...)`。这样父组件弹窗样式继续局部生效，同时子组件内部的定位容器和绝对定位元素重新建立正确的包含块。

**Tech Stack:** Vue 3、Vue SFC scoped CSS、Vitest、TypeScript

---

### Task 1: 添加样式穿透回归测试

**Files:**
- Create: `aryn-mall-ui/apps/web-ele/src/components/verifition/verification-style.test.ts`
- Test: `aryn-mall-ui/apps/web-ele/src/components/verifition/verification-style.test.ts`

**Step 1: Write the failing test**

读取 `index.vue` 源码，断言包装层仍使用 `<style scoped>`，并断言图片定位容器、滑轨定位容器及拼图块选择器使用 `:deep(...)`。

**Step 2: Run test to verify it fails**

Run: `pnpm exec vitest run --dom apps/web-ele/src/components/verifition/verification-style.test.ts`

Expected: FAIL，因为当前 `.verify-img-panel`、`.verify-bar-area` 和拼图块选择器没有穿透动态子组件。

### Task 2: 修复验证码子组件样式作用域

**Files:**
- Modify: `aryn-mall-ui/apps/web-ele/src/components/verifition/index.vue:208`

**Step 1: Write minimal implementation**

保留弹窗、遮罩等包装层的普通 scoped 选择器；将验证码子组件内部使用的选择器改为 `:deep(...)`，重点恢复：

- `.verify-img-panel { position: relative; }`
- `.verify-bar-area { position: relative; }`
- `.verify-move-block { position: absolute; }`
- `.verify-sub-block { position: absolute; }`

**Step 2: Run test to verify it passes**

Run: `pnpm exec vitest run --dom apps/web-ele/src/components/verifition/verification-style.test.ts`

Expected: PASS。

### Task 3: 验证前端质量与页面表现

**Files:**
- Verify: `aryn-mall-ui/apps/web-ele/src/components/verifition/index.vue`

**Step 1: Run focused lint/type checks**

Run: `pnpm prettier --check apps/web-ele/src/components/verifition/index.vue apps/web-ele/src/components/verifition/verification-style.test.ts`

Run: `pnpm -F @vben/web-ele run typecheck`

Expected: PASS。

**Step 2: Run related unit tests**

Run: `pnpm exec vitest run --dom apps/web-ele/src/components/verifition/verification-style.test.ts apps/web-ele/src/components/code-review-contract.test.ts`

Expected: PASS。

**Step 3: Visual verification**

启动管理后台并打开登录页，确认拼图块覆盖在验证码图片内、滑块按钮位于滑轨左端，拖动时两者水平同步且不脱离弹窗。
