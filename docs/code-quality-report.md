# 悦航购代码质量检查报告

> 检查日期：2026-05-08 | 项目：aryn-mall (Java + UI + UniApp)

## 总览

| 子项目 | 高 | 中 | 低 | 合计 |
|--------|---|---|---|------|
| aryn-mall-java | 11 | 2 | 1 | 14 |
| aryn-mall-ui | 14 | 17 | 9 | 40 |
| aryn-mall-uniapp | 560+ | 5000+ | 100+ | 7000+（6261 可自动修复） |
| **合计** | **585+** | **5019+** | **110+** | — |

---

## 一、aryn-mall-java（后端）

### 1.1 层级违规（9 项 · 全部高）

| # | 文件 | 行号 | 描述 |
|---|------|------|------|
| 1 | `aryn-common/aryn-common-storage/.../ArynUploadFileHandler.java` | 4 | L1(storage) 导入 L3(upms-api) |
| 2 | `aryn-common/aryn-common-storage/.../AbstractUploadFileHandler.java` | 14 | L1(storage) 导入 L3(upms-api) |
| 3 | `aryn-common/aryn-common-log/.../LogAopAspect.java` | 18 | L1(log) 导入 L2(common-security) |
| 4 | `aryn-common/aryn-common-log/.../ArynSysLogConfig.java` | 6 | L1(log) 导入 L3(upms-api) |
| 5 | `aryn-common/aryn-common-log/.../ArynLogEvent.java` | 4 | L1(log) 导入 L3(upms-api) |
| 6 | `aryn-common/aryn-common-log/.../ArynLogEventListener.java` | 5-7 | L1(log) 导入 L3(upms-api) ×3 |
| 7 | `aryn-common/aryn-common-log/.../ArynLoginLogEvent.java` | 4 | L1(log) 导入 L3(upms-api) |

> **模式**：`aryn-common-log`(L1) 大量反向依赖 `aryn-upms-api`(L3)，建议将日志 DTO 下沉到 common 层或引入接口解耦。

### 1.2 代码质量（5 项）

| # | 类别 | 文件 | 行号 | 描述 | 严重度 |
|---|------|------|------|------|--------|
| 10 | 文件超长 | `aryn-order/aryn-order-biz/.../OrderInfoServiceImpl.java` | — | 941 行，超 500 行限制 | 中 |
| 11 | 禁止输出 | `aryn-pay/aryn-pay-biz/.../PayNotifyRecordServiceImpl.java` | 149 | `e.printStackTrace()` | 高 |
| 12 | 禁止输出 | `aryn-common/aryn-common-core/.../DesensitizationJsonSerializer.java` | 76 | `e.printStackTrace()` | 高 |
| 13 | 禁止输出 | `aryn-boot/.../SaTokenConfigure.java` | 72 | `System.out.println()` | 中 |
| 14 | 硬编码 | `aryn-pay/aryn-pay-biz/.../AliPayConfiguration.java` | 48 | 硬编码支付宝 API URL | 低 |

---

## 二、aryn-mall-ui（管理后台）

### 2.1 ESLint 错误（高优先级）

| # | 文件 | 描述 | 严重度 |
|---|------|------|--------|
| 1 | `components/verifition/Verify/VerifySlide.vue` | 20+ error：`var`、`==`、未使用变量、`console.log`、无效 removeEventListener | 高 |
| 2 | `components/verifition/Verify/VerifyPoints.vue` | 7 error：未使用变量/参数、`==`、`vue/require-default-prop` | 高 |
| 3 | `components/verifition/index.vue` | 15 error：未使用变量/props、`vue/require-default-prop` | 高 |
| 4 | `components/verifition/utils/util.js` | 4 error：`prefer-const` | 中 |
| 5 | `components/sku/index.vue:52` | 应使用 `structuredClone()` | 低 |
| 6 | `utils/util.ts:45` | 禁止 `Array#reduce()` | 低 |
| 7 | `views/gen/gen-table/generate.vue` | 未使用的 props/ref | 中 |
| 8 | `views/product/goods-spu/form.vue:371` | 未使用参数 `id` | 低 |
| 9 | `views/product/goods-spu/index.vue:80,171` | 未使用变量 | 中 |

### 2.2 TypeScript 类型错误（40+ 项）

| # | 文件 | 描述 | 严重度 |
|---|------|------|--------|
| 1 | `src/adapter/component/index.ts` | 16 个 TS7016：element-plus CSS 模块缺类型 | 中 |
| 2 | `src/components/editor/index.vue` | TS7016 + TS2339：`uploadImage` 属性不存在 | 高 |
| 3 | `src/components/sku/sku-table/index.vue` | 隐式 `any`；类型比较不兼容 | 高 |
| 4 | `src/store/auth.ts:42` | TS2345：`LoginParams` 缺少 `randomStr` | 高 |
| 5 | `src/utils/util.ts` | 参数数量不匹配；`Blob.data` 不存在 | 高 |
| 6 | `src/views/dashboard/analytics/index.vue` | `never[]` 类型推断错误 | 高 |
| 7 | `src/views/gen/gen-table/generate.vue` | 属性不存在、未使用变量 | 高 |
| 8 | `src/views/promotion/distribution-config/index.vue:279` | 类型不兼容 | 中 |
| 9 | `src/views/promotion/page-design/` 组件 | `"mini"`/`"inside"` 不可赋值给限定类型 | 中 |
| 10 | `src/views/user/user-info/detail.vue` | 属性不存在于类型 | 中 |
| 11 | `packages/@core/ui-kit/popup-ui/` | `h()` 调用参数类型不匹配 | 中 |
| 12 | `packages/effects/layouts/.../preferences.vue` | `class` 类型不兼容 | 中 |

### 2.3 文件超长（13 项 · 超 500 行）

| # | 文件 | 行数 | 严重度 |
|---|------|------|--------|
| 1 | `packages/effects/layouts/.../slogan.vue` | **4568** | 高 |
| 2 | `packages/effects/common-ui/.../resize.vue` | **1122** | 高 |
| 3 | `apps/web-ele/.../views/promotion/page-design/form.vue` | 897 | 高 |
| 4 | `packages/@core/ui-kit/menu-ui/.../menu.vue` | 872 | 高 |
| 5 | `apps/web-ele/.../views/pay/config/form.vue` | 709 | 高 |
| 6 | `apps/web-ele/.../views/product/goods-spu/form.vue` | 708 | 高 |
| 7 | `apps/web-ele/.../views/order/order-info/info/index.vue` | 708 | 高 |
| 8 | `packages/stores/src/modules/tabbar.ts` | 658 | 中 |
| 9 | `apps/web-ele/.../views/upms/material/image-tab/index.vue` | 625 | 中 |
| 10 | `packages/@core/ui-kit/layout-ui/.../vben-layout.vue` | 616 | 中 |
| 11 | `packages/@core/ui-kit/form-ui/.../form-api.ts` | 596 | 中 |
| 12 | `apps/web-ele/.../views/order/order-info/index.vue` | 535 | 中 |
| 13 | `apps/web-ele/.../api/order/order-statistics.ts` | 510 | 中 |

### 2.4 console.log 残留（4 处业务代码）

| # | 文件 | 行号 | 严重度 |
|---|------|------|--------|
| 1 | `components/verifition/Verify/VerifySlide.vue` | 182 | 中 |
| 2 | `views/upms/sys-server/index.vue` | 72 | 中 |
| 3 | `views/_core/authentication/forget-password.vue` | 33 | 中 |
| 4 | `views/_core/authentication/register.vue` | 86 | 中 |

### 2.5 Prettier 格式化（73 个文件不一致）

可通过 `pnpm prettier --write` 批量修复。

---

## 三、aryn-mall-uniapp（移动端）

### 3.1 ESLint 问题（7224 项 · 6261 可自动修复）

**高优先级（需手动修复，~963 项）**：

| 规则 | 数量 | 描述 |
|------|------|------|
| `block-scoped-var` | 248 | 变量作用域问题 |
| `no-var` | 94 | 应使用 let/const |
| `eqeqeq` | 69 | 应使用 `===` |
| `no-cond-assign` | 58 | 条件中的赋值 |
| `no-sequences` | 52 | 逗号操作符 |
| `no-unused-expressions` | 50 | 无用表达式 |
| `unused-imports/no-unused-vars` | 39 | 未使用变量 |
| `no-use-before-define` | 20 | 变量先使用后定义 |

**可自动修复（6261 项）**：运行 `npx eslint src/ --ext .vue,.ts,.tsx --fix`

| 规则 | 数量 |
|------|------|
| `style/comma-spacing` | 2433 |
| `style/space-infix-ops` | 1688 |
| `style/block-spacing` | 352 |
| `style/semi-spacing` | 307 |
| `style/key-spacing` | 301 |
| `style/keyword-spacing` | 196 |
| `style/space-before-blocks` | 165 |
| `style/object-curly-spacing` | 122 |
| `style/quotes` | 103 |
| `style/semi` | 96 |
| `prefer-const` | 91 |

### 3.2 TypeScript 类型问题

| # | 文件 | 描述 | 严重度 |
|---|------|------|--------|
| 1 | `tsconfig.json` | 引用不存在的类型定义 `uni-echarts/global` | 高 |

### 3.3 文件超长（6 项 · 超 500 行）

| # | 文件 | 行数 | 严重度 |
|---|------|------|--------|
| 1 | `src/components/vk-data-goods-sku-popup/vk-data-goods-sku-popup.vue` | **1373** | 高 |
| 2 | `src/api/globals.d.ts` | 1058 | 中 |
| 3 | `src/auto-imports.d.ts` | 759 | 低（自动生成） |
| 4 | `src/sub-pages/product/goods-detail/index.vue` | 573 | 高 |
| 5 | `src/pages/user/shopping-cart/index.vue` | 561 | 高 |
| 6 | `src/sub-pages/user/distribution/index.vue` | 535 | 高 |

### 3.4 console.log 残留（24 处）

| # | 文件 | 行号 | 严重度 |
|---|------|------|--------|
| 1 | `src/api/core/handlers.ts` | 79 | 中 |
| 2 | `src/api/core/instance.ts` | 51-53 | 中 |
| 3 | `src/components/coupon-card/index.vue` | 58 | 中 |
| 4 | `src/components/ikun-qrcode/ikun-qrcode.vue` | 43 | 中 |
| 5 | `src/pages/user/shopping-cart/index.vue` | 223 | 中 |
| 6 | `src/router/index.ts` | 65,70,81,90,93,124,130,133 | **高**（8 处） |
| 7 | `src/store/authStore.ts` | 69,214,216 | 中 |
| 8 | `src/store/dictStore.ts` | 21,46 | 中 |
| 9 | `src/sub-pages/product/goods-detail/index.vue` | 237,243 | 中 |
| 10 | `src/sub-pages/product/goods-list/index.vue` | 70 | 中 |
| 11 | `src/sub-pages/user/address/form.vue` | 80 | 中 |

### 3.5 组件命名规范（4 项）

| # | 文件 | 描述 | 严重度 |
|---|------|------|--------|
| 1 | `src/components/ikun-qrcode/ikun-qrcode.vue` | 非 PascalCase | 低 |
| 2 | `src/components/region-picker/region-picker.vue` | 非 PascalCase | 低 |
| 3 | `src/components/vk-data-goods-sku-popup/vk-data-goods-sku-popup.vue` | 非 PascalCase | 低 |
| 4 | `src/components/vk-data-input-number-box/vk-data-input-number-box.vue` | 非 PascalCase | 低 |

> 页面组件 kebab-case 是 UniApp 路由约定，不算违规。

### 3.6 分层规则 ✅

`src/api/` 未引用 pages 组件；`src/components/` 未引用 pages。合规。

---

## 四、修复优先级建议

### P0 — 立即修复

| 项目 | 问题 | 原因 |
|------|------|------|
| Java | 2 处 `e.printStackTrace()` | 生产环境泄露堆栈信息 |
| Java | `aryn-common-log` 对 `aryn-upms-api` 的 7 处反向依赖 | 架构层级违规，影响模块独立性 |
| UI | `src/store/auth.ts` 类型错误 | `LoginParams` 缺少 `randomStr`，影响登录功能 |
| UI | verifition 组件 30+ error | 代码质量极差，建议重写或替换 |

### P1 — 尽快修复

| 项目 | 问题 | 原因 |
|------|------|------|
| Java | `OrderInfoServiceImpl.java` 941 行 | 难以维护，需拆分 |
| Java | `System.out.println()` → 改用 `@Slf4j` | 不符合日志规范 |
| UI | `slogan.vue` 4568 行 / `resize.vue` 1122 行 | 严重超限 |
| UniApp | `vk-data-goods-sku-popup.vue` 1373 行 | 严重超限 |
| UniApp | 运行 `eslint --fix` 自动修复 6261 项 | 批量消除风格问题 |
| UniApp | `src/router/index.ts` 8 处 console.log | 调试日志泄露 |

### P2 — 计划修复

| 项目 | 问题 |
|------|------|
| UI | 73 个 Prettier 格式化不一致文件 → `prettier --write` |
| UI | 40+ TypeScript 类型错误 |
| UniApp | ~963 个 ESLint 高优先级错误（no-var, eqeqeq 等） |
| UniApp | 24 处 console.log → 替换为结构化日志 |
| UniApp | 4 个组件非 PascalCase 命名 |
