# 出清折扣规则页 UI/UX 优化（管理端 · 2026-11-04）

> 范围：`aryn-mall-ui/apps/web-ele/src/views/promotion/clearance-rule/`（营销管理 → 出清折扣规则）。
> 前端单页改造，无后端、无 API、无 SQL 变更；请求路径与权限码保持不变。

## 1. 背景

原页面是「一行灰色说明 + 新增按钮 + 朴素表格」：说明/按钮/表格零间距、没有页面层级；
折扣以 `再打折率 0.8000` 呈现，运营需要心算；「更长时间档折扣更深导致短档永不生效」
这类业务事实在页面上不可见；状态只能进弹窗改；空态只有一行灰字；弹窗无快捷档位、
无内联校验、无结果预览。

用户提供页面截图，要求优化该页 UI/UX。

## 2. 方案

| 层 | 内容 |
|---|---|
| 信息架构 | 新页头卡片：标题「出清时间阶梯」+ 机制说明（出清价 = 基础出清价 × 命中档折扣率；剩余 ≤ N 小时命中；多档取最深一档、不连乘）+ 启用条数/最深折扣标签；下方「当前生效价格阶梯」chips 与「快速试算」 |
| 生效阶梯 | 新增纯逻辑 `logic.ts` 的 `buildLadder`：按启用规则计算「剩余时长区间 → 实际折扣」，并 `mergeAdjacentBands` 合并相邻同率段（例：48h 8.0 折 / 24h 8.5 折 / 6h 7.0 折 → `>48 原价`、`6–48 时段 8.0 折`、`≤6 时段 7.0 折`），直接暴露冗余档 |
| 快速试算 | 基础出清价（默认 ¥100）+ 剩余小时 → 折后价与「命中 N 条、取最深 X 折」提示，tooltip 给出最深档规则名 |
| 表格 | 名称（备注副行、两行截断+title）、生效时间 chip、折扣力度「8.0 折 + 基础出清价 × 80%」、行内 ElSwitch 启停（PUT 后重拉列表，失败回滚并以服务端为准）、无效档「不改变价格」告警 tag（tooltip 解释被哪一档覆盖）、最近修改时间、修改/删除；停用行背景与文字弱化；空态改为图标 + 说明 + CTA |
| 弹窗 | 640px 分组表单：规则名称、剩余时长（InputNumber + 72/48/24/12/6 快捷预设）、折扣力度（按「折」输入 + 9/8.5/8/7/6/5 快捷预设，副行显示「基础出清价 × %」）、启用、展示顺序（说明仅影响列表展示）、备注；`ElForm rules` 内联校验；「已有同时间档规则」「该规则保存后不会改变出清价」即时提示；「配置后生效阶梯」实时预览并高亮本档；提交 loading |
| 精度兼容 | 折输入精度为 0.1 折，编辑存量 3–4 位小数折扣率时：未改动折扣力度则按原始 `discountRate` 原值回写（避免静默改价），并提示「原折扣率 85.55%，不改折扣力度时按原值保存」；覆盖 ≥9.96 折被输入框上限钳制的边界 |
| 工程 | 新增 `logic.ts`（18 个纯函数，无 Vue/Element Plus 依赖）+ `logic.test.ts`（54 例）；新增 `components/LadderChips.vue`（页头与弹窗共用）；`index.vue` 负责状态与编排 |
| 可访问性/主题 | 语义标签在 light 主题下改为「正文色文字 + 语义色边框/底色/图标」（≥4.5:1），暗色单独适配；行内开关/刷新/试算输入/弹窗数字输入补 aria-label；阶梯 chip 可 Tab 聚焦 + focus-visible + tooltip；图标 aria-hidden；长文本 title 兜底 |

**不变项**：`api/promotion/clearance-rule.ts`（`/promotion/clearance-rule` 契约与 boot 模式改写）、
权限码 `promotion:shipactivity:save`、菜单 `keepAlive`、后端实体与算价口径均未改动。

## 3. 验证

- `cd aryn-mall-ui && pnpm exec vitest run --dom apps/web-ele/src/views/promotion/clearance-rule/logic.test.ts`
  → **54 passed**。
- `pnpm exec eslint apps/web-ele/src/views/promotion/clearance-rule/` → 0 error；`prettier --check` → 全过。
- `pnpm -F @vben/web-ele run typecheck`：本次 4 个文件 0 报错（工作树同时有用户并行的
  `zone-goods` 改动，其未使用 import 会让全量 typecheck 失败，与本页无关）。
- Playwright 离线 mock harness（不触达真实后端）：35 张矩阵截图（empty/single/normal/redundant/many ×
  light/dark × 1280/1600）+ 18 张交互/边界探针；控制台 0 错误、暗色走真实主题按钮且白块扫描 0、
  1280/1600 无横向溢出、弹窗无双重滚动。
- 专项：编辑 `discountRate=0.8555`/`0.9999` 规则不改折扣保存，PUT payload 仍为原值；
  行内启停成功序列 `GET list → PUT → GET list`，失败回滚；停用行 light `rgb(244,244,245)` /
  dark `rgb(46,48,51)`；对比度实测（light）：告警 tag 11.88、试算 tag 4.95、最深 tag 4.59；
  dark：16.36 / 14.9 / 13.48；长名称两行截断后行高 129px → 83px（1280）。

## 4. 边界/遗留

- 工具栏「共 N 条规则」的 12px 文字对比度 3.08:1 来自共享组件 `PageToolbar`，本页未改（属全局组件）。
- 剩余未处理、已评估为「接受/后续」：`locateBand` 复用、表单校验单源化、仅测试使用的导出收敛、
  `.is-muted` 冗余清理、`sameHoursRule` 在停用草稿时仍提示、CSS 圆角/字号 token 化。
- QA 明细报告与截图：`/tmp/clearance-qa/qa-report.md`、`/tmp/clearance-qa/baseline-report.md`；
  截图副本：`artifacts/clearance-rule-redesign/`（before / after-light / after-dark / empty / create /
  edit / redundant / precision）。

## 5. 涉及文件

- 修改：`aryn-mall-ui/apps/web-ele/src/views/promotion/clearance-rule/index.vue`
- 新增：`.../clearance-rule/logic.ts`、`.../clearance-rule/logic.test.ts`、
  `.../clearance-rule/components/LadderChips.vue`
