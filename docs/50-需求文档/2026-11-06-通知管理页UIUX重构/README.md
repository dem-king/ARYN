# 通知管理页 UI/UX 重构（管理端 · 2026-11-06）

> 范围：`aryn-mall-ui/apps/web-ele/src/views/message/notice/`（消息中心 → 通知管理）。
> 前端单页改造，**无后端、无 API、无 SQL、无菜单/权限码变更**；请求路径、权限码、
> boot/cloud 双模式改写（`parseOpenBoot` / `rewriteBootUrl`）全部保持原样。

## 1. 背景

用户提供页面截图，要求「UI/UX 重新设计，要主流、要现代」。

改造前该页是「深色 hero 大标题 + 朴素表格」：hero 高 152px 但只承载一句文案，
分类列直接渲染后端原始枚举，12 种操作按钮全部平铺在行内，分页落在首屏之外。
先做了**量化基线**（Playwright 离线 harness，36 个场景/主题/宽度组合），实测出 8 条问题：

| 编号 | 问题 | 实测数据 |
| --- | --- | --- |
| P0-1 | 1280 视口下「创建时间」被固定操作列**完全遮挡** | 列宽合计 1260 > 可视 970；操作列左边界 977，创建时间列 1087–1267；`elementFromPoint` 打在文本中心返回操作列单元格，132px 文本被覆盖 **132px = 100%** |
| P0-2 | 行高剧烈跳动 | 12 行行高 `[68,96,119,68,96,96,68,142,68,68,68,68]`，极差 **74px**（1280）/ 51px（1600） |
| P1-3 | 分类列显示后端原始枚举 | 12/12 行是 `PROMOTION / ORDER / REFUND / VESSEL_CALL_CHANGE / DELIVERY_TASK / SYSTEM`；`VESSEL_CALL_CHANGE` 在 120px 列宽里强制换行，是行高跳动主因；详情抽屉同样是 `PROMOTION` |
| P1-4 | 分页在首屏之外 | 1280 `y=1437.7`（视口 1000）、1600 `y=1335.7`；空态反而在首屏内 |
| P1-5 | 操作按钮热区过小 | 查看/撤回/重试/编辑/发布/删除全是 **34×20px**；44 个可点目标中 **42 个**高度 < 24px（WCAG 2.5.8） |
| P1-6 | 浅色主题弱文字对比度不足 | `.notice-summary` 12px `#909399` on `#fff` = **3.08:1**（AA 需 4.5:1）；暗色同色 5.41:1 达标 —— 该色本是为暗色调的 |
| P2-7 | 空态无行动入口 | 仅 `ElEmpty` 默认插图，区块内 CTA 数 = 0；唯一「新建」在 hero 右上，相距约 350px |
| P2-8 | 新建弹窗过高、字段类型错误 | 弹窗 680×765.2px（1000px 视口余量仅 84.8px）；label 宽度 4 种、控件宽度 2 档；**分类是自由文本 `ElInput`**，而后端是固定枚举 |

## 2. 方案

### 2.1 信息架构：从「hero 占位」到「工具页头 + 常驻筛选 + 满高表格」

| 区域 | 改动 |
| --- | --- |
| 页头 | 152px 深色 hero → **紧凑页头卡片（约 74px）**：图标 + 面包屑「消息中心 / 通知编排」 + 标题 + 一行副标题；右侧只放**总数 + 新建**。深色渐变改为页面底色上的极淡 primary 径向渐变，跟随主题与租户主色，暗色下不刺眼 |
| 筛选 | 状态从 `ElSelect` 改 `ElSegmented`（全部/草稿/已发布/已撤回），保留关键词搜索；筛选条 `position: sticky` + 玻璃拟态（`backdrop-filter`，`@supports` 降级为不透明底）；增加「清除筛选」与圆形刷新 |
| 表格 | 满高（`flex: 1 1 0`）、**固定 62px 行高**、去掉 `fixed="right"`；标题单行省略、摘要单行省略、分类/目标端/创建时间都拆成两行小单元 |
| 分页 | 复用 `#/components/pagination/index.vue`（`margin-top: auto`），由 `hx-layout-container` 的标准 flex 规则钉在卡片底部，**首屏可见** |
| 操作 | 6 个平铺 link 按钮 → **「查看」+「更多」下拉**（按 `status` 与权限码动态收敛），危险动作排到分隔线之后并染 danger 色 |
| 新建/编辑 | 765px 弹窗 → **720px 全高右侧抽屉**（`notice-editor.vue`）：分段「内容 / 受众」，分类改 `ElSelect`（6 个真实枚举 + `allow-create`），优先级改 `ElRadioGroup`，正文 `autosize 8–16` 行 |
| 详情 | 720px 抽屉 → **760px 抽屉**（`notice-detail.vue`）：状态头（胶囊 + 受众摘要）→ 概览（送达/已读/隐藏指标卡 + 投递完成度进度条 + 6 条事实 + 正文 + 分发任务表）→ 收件人明细页签；动作按钮按状态 + 权限渲染 |

### 2.2 设计口径单源化：`meta.ts`（30 条单测钉住）

改造前「目标端」有 `targetText()` 翻译而「分类」没有，是同一类字段处理不一致。
这次把**所有展示口径**收敛成一个无 Vue / 无请求依赖的纯函数模块：

- `categoryMeta` / `statusMeta` / `dispatchMeta` / `taskStatusMeta`：枚举 → 中文标签 + 语义色；
  未知枚举回退原文（后端新增分类不会渲染空白），空值回退 `—`；
- `deliveryPercent` / `deliveryText`：`0/0` 不除零、脏数据钳到 0–100、无送达显示 `—`；
- `splitDateTime`：`yyyy-MM-dd HH:mm:ss` 拆成两行固定高度单元，兼容 `T` 分隔、缺秒、脏数据；
- `targetTypesText` / `audienceText` / `audienceSummary`：受众快照 → 「全部会员；指定工作人员 2 名」这类人能读的摘要
  （「不选条件=发给全部」是后端语义，必须显式说「全部」，否则运营会以为漏配）；
- `rowCommands` / `canRetry`：状态 → 可用动作，与后端 `NoticeServiceImpl` 的前置条件对齐；
- `NOTICE_ACTION_PLANS` / `noticeActionPlan`：5 个破坏性/状态动作的**确认文案与成功提示单源化**，
  列表「更多」和详情抽屉共用同一份，避免同一件事在两个入口给出不同承诺。

### 2.3 对比度与可访问性

- **语义色只落在圆点/边框/浅底上**，胶囊文字统一用正文色。Element Plus 的 `--el-color-*` 当文字色
  在浅色主题下只有约 3:1（改造前 3.08:1 就是这个坑）。
- 新增 `--nt-text-muted` 令牌（`notice-ui.css`，按主题分别定义）替代 `--el-text-color-secondary`：
  浅色 `#5f6875` → **5.64:1**，暗色 `#a9adb6` → **7.42:1**（≥4.5:1）。
- 热区：行内操作 59×28 / 28×28、标题按钮 221×24（原 34×20 / 22.8）。
- 标题用真 `<button>`：可 Tab 聚焦、`Enter` 打开详情、`:focus-visible` 有可见描边。
- 进度条带 `role="progressbar"` + `aria-valuenow/min/max`；图标 `aria-hidden`；搜索框与刷新按钮有 `aria-label`。
- `prefers-reduced-motion` 下关闭进度条过渡。

### 2.4 共享样式抽取

`notice-ui.css`（全局、非 scoped）放 `.nt-pill--{tone}` / `.nt-dot--{tone}` / `.nt-progress` / `.nt-num`：
列表页与详情抽屉两个组件若各自 `scoped` 维护同一套规则，一旦漂移就会出现
「列表里已发布是绿点、抽屉里变成灰点」的不一致（做法与既有的 `service/desk-tokens.css` 一致）。

## 3. 验证

### 3.1 静态与单测

```bash
cd aryn-mall-ui
node node_modules/vue-tsc/bin/vue-tsc.js --noEmit --skipLibCheck -p apps/web-ele/tsconfig.json   # 0 error
npx eslint apps/web-ele/src/views/message/notice/                                                  # 0 error
npx stylelint "apps/web-ele/src/views/message/notice/*.{vue,css}"                                  # 0 error
npx vitest run apps/web-ele                                                                        # 1347/1347 passed（120 files）
```

`meta.test.ts` 30 例，含两条回归守卫：
- 「已知 6 个分类枚举必须全部有中文标签」（钉住 P1-3）；
- 「5 个动作文案不得泄漏 `{target}` 占位符、危险动作标记正确、每条确认都是完整说明」。
- `views/__tests__/element-component-imports.test.ts` 在开发过程中抓到一个真实缺陷
  （`notice-detail.vue` 漏 import `ElDrawer`，运行时会渲染成未知标签），已修。

### 3.2 视觉验收（Playwright 离线 harness，不触达真实后端）

- 脚本 `/tmp/notice-qa/capture.mjs`：全量 mock `/api`（绝对 URL 正则，避免误伤 Vite 的 `/src/api/*.ts`）、
  假 token、`routeWebSocket` 关闭 WS、`platformAdmin:false`；暗色走**真实偏好按钮**。
- **18 张标准矩阵（before/after 逐一对照）+ 8 张扩展场景（long 超长文本 ×3、更多菜单 ×2、编辑抽屉 ×2、空态新建 ×1）**，
  全部锚定最终源码 hash `d4330cc2…`，**控制台错误 0 / pageerror 0 / Vue 警告 0**。
- before 基线用 `@vue/compiler-sfc` 把改动前 SFC 快照编译成模块回放（真实渲染的老页面，非手写模拟），
  保真度经行高/列宽/原始枚举逐项对齐校验。

**改动前 8 条问题 8/8 全部解决：**

| 原问题 | 复核实测 |
| --- | --- |
| P0-1 创建时间 100% 遮挡 | 列边界重叠 290px → **0px**；`elementFromPoint` 逐列命中 3 组合 **0/6 被遮挡** |
| P0-2 行高跳动 | 极差 74/51px → **0**（恒定 62px，含 `long` 超长标题场景） |
| P1-3 分类原始枚举 | 12/12 → **0/12**，详情同样变「营销」胶囊 |
| P1-4 分页首屏外 | `y=1437.7` → **928**（1280 与 1600 都是 928） |
| P1-5 热区 34×20 | **59×28 / 28×28**，标题 **221×24**；小目标 42/44 → **0** |
| P1-6 对比度 3.08:1 | **5.64:1（浅）/ 7.42:1（暗）**；`.nt-pill` 五色 11.09–16.36:1 |
| P2-7 空态无 CTA | 0 → **1 个「新建通知」** + 场景化说明 + 有条件时「清除筛选」 |
| P2-8 弹窗过高/分类自由文本 | 改 720×1000 抽屉；分类改 `ElSelect`（编辑态回填「营销」已验证） |

- 横向溢出：1280 与 1600 下 `scrollWidth == clientWidth`（列宽合计 1004 = 可视宽 1004）。

### 3.3 行为流探针（真实点击，非仅截图）

`/tmp/notice-qa/probe-flows.mjs` 断言全通过：

| 流程 | 结果 |
| --- | --- |
| 状态分段筛选 | 请求 `GET /api/boot/admin/notice/page?...&status=DRAFT`，列表收敛到 1 行 |
| 草稿行「更多」 | 菜单项 = 编辑草稿 / 发布 / 删除草稿 |
| 已发布行「更多」 | 菜单项 = 撤回 / 重试分发（`PARTIAL_FAILED` 才出现重试） |
| 撤回确认 + 提交 | 确认文案与 `meta.ts` 单源一致；请求 `DELETE /api/boot/admin/notice/p1/publish`；toast「通知已撤回」 |
| 详情受众摘要 | 「受众：会员按等级/标签筛选；全部工作人员」（由 `audienceSnapshot` 解析） |
| 收件人明细页签 | 1 行（mock 数据），页签切换触发 `/{id}/recipients` |
| 详情 →「编辑」 | 关闭详情、打开编辑器；标题回填「草稿通知」、分类回填「营销」 |
| 键盘可达 | Tab 聚焦标题按钮 + `Enter` 打开详情 |

## 4. 边界 / 遗留

- **P2-2（接受）**：编辑抽屉 `label-position="top"` 下 label 宽度随文案长度变化（6 种），左边缘不严格对齐，
  不影响可用性；若要严格对齐需固定 label 行高或统一文案长度。
- **观察项（合理）**：详情抽屉内容变丰富后 body 出现纵向滚动（942 / 866），抽屉内滚动是正常交互。
- **观察项（已评估）**：`.nt-hint` 仅在草稿详情渲染，本次 detail 动作打开的是已发布通知未采到样本；
  其样式与 `.nt-summary` 同用 `var(--nt-text-muted)` + 12px，按同令牌推定达标。
- 本页**没有**改动共享组件 `PageToolbar` 的 12px 文字对比度（属全局组件，影响面超出本需求）——
  这次页头改为自有 `nt-hero`，因此本页已不再受影响。
- 双模式适配：仅前端展示层改动，`api/message/notice.ts` 的路径首段仍是 `message` 微服务域，
  boot 模式下由 `requestClient` 内的 `rewriteBootUrl` 改写为 `/boot/admin/notice/*`（行为流探针已实测到该 URL）。

## 5. 涉及文件

- 修改：`aryn-mall-ui/apps/web-ele/src/views/message/notice/index.vue`（1348 → 约 980 行，模板/样式重写）
- 新增：
  - `.../notice/meta.ts`（展示口径与动作文案纯函数）
  - `.../notice/meta.test.ts`（30 例）
  - `.../notice/notice-editor.vue`（新建/编辑抽屉）
  - `.../notice/notice-detail.vue`（详情抽屉）
  - `.../notice/notice-ui.css`（共享语义色胶囊/进度条/弱文字令牌）

## 6. 证据

- 改动前基线报告：[`artifacts/notice-redesign-2026-11-06/before-report.md`](../../../artifacts/notice-redesign-2026-11-06/before-report.md)
- 验收报告（67 项断言表 + before/after 对照）：[`artifacts/notice-redesign-2026-11-06/qa-report.md`](../../../artifacts/notice-redesign-2026-11-06/qa-report.md)
- 截图：`artifacts/notice-redesign-2026-11-06/screenshots/`（18 before + 26 after）
- 度量：`before-metrics.json`、`all-metrics.json`、`summary-{before,after}.json`
- 脚本：`/tmp/notice-qa/{capture,legacy-page,probe-overlap-after,probe-flows,probe-hit}.mjs`
