# 管理端 UI 统一（页面骨架 + 设计令牌）

> 日期：2026-10-22 ｜ 状态：**Batch 0–5 已完成（管理端统一一期收口）**
> 分支：`codex/newbie-price-activity` ｜ 工作目录：仓库根
> 原始请求（用户原文）：「我觉得需要统一一下管理端页面的UI风格，目前有很多页面风格都不统一，你觉得呢」→「需要」→ 按盘点方案分批执行。
> 背景：管理端 119 个业务 `index.vue` 里，页面外壳、筛选区、工具栏、状态标签、金额格式与硬编码色值写法分散；一次性全站换皮风险高，采用「统一令牌 + 公共骨架 + 分批迁移」。

## 一、目标与非目标

**目标**：让同类管理页共享同一套页面骨架与视觉令牌，页面只保留业务内容。

1. 筛选区、工具栏、数据表格、页签、状态标签、金额、空态六类高频元素统一。
2. 详情/配置页统一分区标题（`PageSection`）、表单标签宽度、底部按钮区。
3. 状态与金额走统一出口：`DictTag`（字典驱动）+ `formatMoney`。
4. 迁移过程中不改变业务逻辑、接口路径、权限码与操作流程。

**非目标（明确不做）**：

- 不做全站视觉重构，不全局覆盖 Element Plus 样式（统一限定在 `.hx-*` 作用域）。
- 装修设计器、C 端预览组件（还原真实商城渲染）保留自定义色值。
- 工作台/大屏（Batch 3）允许自定义布局，只统一令牌、状态标签、空态与金额，不强套 CRUD 模板。

## 二、公共基线

### 组件（`apps/web-ele/src/components/page/`）

| 组件 | 用途 | 关键约定 |
|------|------|----------|
| `page-filter-panel.vue` | 筛选卡片 | 标签 96px、常规控件 200px、日期 360px、按钮右对齐；重置即调用 `resetFields()` 并触发查询 |
| `page-toolbar.vue` | 页面工具栏 | 左侧页签/新增/批量操作；右侧总数（`total` + `totalLabel`）/导出/搜索/刷新 |
| `page-section.vue` | 详情/配置分区 | `title` / `description` / `bordered`（默认卡片外壳，抽屉弹窗可 `bordered=false`）；`extra` 放右侧操作 |

### 全局样式（`packages/styles/src/ele/index.css`）

- `.hx-data-table`：表头 44px、悬浮态、操作按钮间距 8px。
- `.hx-money` / `.hx-money--strong`：金额等宽数字（`font-variant-numeric: tabular-nums`）。
- `.hx-form-footer`：弹窗/抽屉底部按钮右对齐、间距 12px（已抵消 Element 相邻按钮 margin-left）。

### 工具

- `formatMoney`（`apps/web-ele/src/utils/util.ts`）：金额统一两位小数。
- `DictTag`：字典值渲染标签；`status` 字典已补 `show_class`（boot 增量 `146status_dict_show_class.sql`、cloud `147status_dict_show_class.sql`，boot 全量 SQL 已重建）。

## 三、四类页面规则

| 页面类型 | 规则 |
|----------|------|
| 标准列表 | `hx-layout-container` 外壳 + `PageFilterPanel`（有筛选字段时）+ `PageToolbar` + `hx-data-table` + `empty-text` + `Pagination` |
| 分栏/树表 | 保留左右分栏与树表，仅替换筛选、工具栏、状态与表格样式 |
| 详情/配置/表单 | 分区用 `PageSection`；弹窗/抽屉表单 `label-width="auto"`，整页表单页内单一固定宽度；footer 用 `.hx-form-footer` + 取消/确定；金额 `formatMoney` + `.hx-money`；硬编码色值改 `var(--el-*)` |
| 工作台/大屏 | 保留自定义布局，统一令牌、状态标签、空态、金额 |

> 覆盖率（2026-10-22 收尾统计）：页面级 `.vue`（>60 行）共 127 个，含表格 74 个，其中 72 个已用 `.hx-data-table`；未用的 2 个为刻意保留自定义设计的工作台页（`order/shared-cart`、`promotion/page-design`）。剩余硬编码色仅存在于显式豁免页（`upms/sys-server` 深色监控大屏、消息中心品牌色、`mall-theme` 主题数据、`goods-category` C 端角标同步、`material` 注释）。

**豁免**：装修设计器、C 端预览组件、定制订单详情卡片布局（只收敛色值与金额格式，不换骨架）。

## 四、批次进度

| 批次 | 状态 | 范围 |
|------|------|------|
| Batch 0 + 试点 | ✅ | 公共组件/表格样式 + 订单列表、商城退单、角色管理、会员等级 |
| Batch 1 | ✅ | 商品、商品类目、会员权益、会员标签、优惠券、秒杀、部门、字典、交易订单、员工账号、配送员、配送任务、船舶档案（13 页）+ `status` 字典 `show_class` |
| Batch 2 | ✅ | 配置/详情/表单页 21 个文件：订单配置（列表 + 弹窗）、支付配置（列表 + 弹窗）、仓库配置、配送范围、分销配置、积分配置、签到配置、储值配置（列表 + 弹窗）、用户明细抽屉、商品发布表单、优惠券/拼团抽屉、秒杀/折扣编辑页、订单详情、订单发货抽屉；新增 `PageSection`、`.hx-money`、`.hx-form-footer` |
| Batch 3 | ✅ | 工作台与大屏 38 个文件（令牌化 + 空态/金额/标签统一）：消息中心 5 页（通知/收件箱/私信/客服工作台/消息卡片，品牌 hero 与深色客服台保留为页面级变量）、共享购物车、商城主题、模板市场、旧版装修编辑器 chrome、装修设计器 3 个 chrome 组件；三个经营看板 26 个文件，图表系列色抽到 `dashboard/shared/chart.ts`（`CHART_PALETTE`/`areaGradient`/`withAlpha`/`readCssVar`）。**豁免**：`page-design/components/**`、`canvas-page-shell.vue`、`page-preview/**`（C 端还原渲染色） |
| Batch 4 | ✅ | 剩余标准列表页补迁移 38 个文件：商品品牌/规格/规格值/评价（含弹窗）、营销领券记录/折扣活动/拼团活动/拼团记录/分销订单/分销用户/分销提现、会员列表/余额记录/积分记录/充值订单/签到记录/三方账号/三方用户（含弹窗）、支付回调/退单、系统操作日志/登录日志/物流公司/素材分组/在线用户/文件存储配置/租户/租户套餐（含弹窗） |
| Batch 5 | ✅ | 剩余特殊页面 12 个文件（保留布局、只统一骨架/令牌/空态/金额）：船舶绑定申请（列表+审核弹窗）、靠港日历（三视图+统计卡+抽屉）、港口报备车辆（筛选+表格+弹窗）、出车单（列表+详情抽屉）、港口配送看板（统计卡+筛选+表格）、履约异常、拣货波次（主表+3 弹窗）、船供活动（筛选+表格+5 弹窗）、字典值子页（列表+弹窗）、素材中心图片库（卡片网格+空态） |
| Batch 5 收尾 | ✅ | 补漏 7 个文件：配送任务详情（6 个 ElCard→PageSection、表格统一、令牌；修复 PageSection 漏 import 导致的"标题不渲染"）、员工登录/操作日志子表（hx-data-table+空态）、配送员新增抽屉/绑定商城账号弹窗（label=auto + footer/ElEmpty + 令牌）、会员标签/角色菜单/员工表单/配送员列表残留色令牌化 |
| Batch 5 之后 | — | 已知剩余非表格页：`upms/menu`（Vben 框架原生 `Page`+`Grid` 标准，不迁 hx-* 体系）、`upms/sys-server`（深色监控大屏，自定义视觉）、`gen/*`（代码生成工具页）——按豁免口径保留 |

## 五、验证

- 静态：`pnpm -F @vben/web-ele run typecheck`、`pnpm exec eslint <改动文件>`、`pnpm test:unit`（Batch 4 收尾全绿；Batch 5 收尾 typecheck/eslint 全绿、scoped 单测 121 例通过，全量单测余 2 例失败来自外部并行会话的 `page-designer/preview-parity.test.ts`，与本需求无关）。外部注：`src/views/user`、`src/views/pay` 下本无测试文件，路径过滤需加 `--passWithNoTests`。
- 实机：本地 `dev:ele`（5777 代理 9999 boot 容器）注入 accessToken 免登录，1600×1000 与 1280×1000 两档截图；检查表格 `clientWidth/scrollWidth`、筛选区/工具栏/分区渲染、弹窗与抽屉、空态文案、控制台错误（0 error）。
- 深色模式：通过顶栏主题开关进入 `html.dark`，确认 `--el-bg-color` 等令牌切换后页面无死色块（硬编码色值已全部改令牌）。

## 六、已知限制与边界

- `/promotion/distribution/config` 在当前本地库未授权给当前 token 的角色（所有分销页 404），该页只做了静态校验与代码走查，未做真实截图；页面代码结构与已验收的积分/签到配置页一致。
- 1280 视口下订单详情 9 列表格内部横向滚动约 132px（1440/1600 完整展示），页面本身无横向溢出；如需 1280 单屏展示可再压缩列宽。
- `pay/config` 支付类型列由硬编码三元改为 `pay_type` 字典驱动，文案由「微信/支付宝/未知」变为「微信支付/支付宝支付/0元支付/货到付款」（字典只有 0–3，无未知兜底）。
- `delivery/area` 状态列保留本地 `statusMap` + `ElTag`：全局 `status` 字典语义是「正常/停用」，与本页「启用/禁用」不一致，强行字典化会改文案含义。
- 三个成员配置列表页（积分/签到/储值）本身没有筛选字段，未引入 `PageFilterPanel`（按「有筛选字段才上」口径）。
- Batch 1/2/3 只改前端展示，未涉及接口、数据库（`status` 字典除外，已在 Batch 1 提供 boot/cloud 双份增量 SQL）。
- Batch 3 刻意保留的品牌/数据色（不随主题切换）：消息中心 hero 渐变与白色 CTA、客服工作台整页深色品牌台、`chat-message-card` 深色卡片、`mall-theme` 主题数据 hex（`navigationColor`/`primaryColor` 等）、`theme.json` 区块背景默认值；上述色值已集中到页面/组件根局部变量，不再散落。
- 图表系列色集中在 `dashboard/shared/chart.ts`（`CHART_PALETTE`/`CHART_SERIES_COLORS`）；两个未显式指定 `itemStyle` 的饼图沿用 ECharts 默认调色板（显式改色会改变渲染结果）。
- `promotion/page-design/form.vue` 是旧版装修入口（路由已 redirect 到 `/page-designer`，无模块 import），只有 typecheck/eslint 保障，无法实机打开截图。
- 消息中心类型图标/标签色来自 `message/inbox/notice-type-meta.ts` 的数据驱动色（8 类业务语义色），本轮未改。
- Batch 4 中 `/platform/logistics-company`、`/platform/tenant`、`/platform/tenant-package` 在当前本地 token 角色下 404（平台级菜单未授权），仅静态校验；分销订单/用户/提现 3 页本地菜单树缺失，由执行者运行时注入临时路由验证 UI。
- `product/appraise`（商品评价）与 `product/goods-specs-value`（规格值内嵌抽屉）本地无独立菜单入口，只做静态与内嵌场景验证。
 - 工作区在 Batch 4 期间存在其它会话的并行改动（`router/guard.ts` 调试日志、`router/routes/core.ts` 的 loader-preview 路由、`views/_core/loader-preview/index.vue` 新文件），不属于本需求，未纳入。
- Batch 5 中 `/delivery/port-board`、`/fulfillment/exception`、`/fulfillment/wave` 三个页面被既有种子脚本 `db/boot/96hide_fulfillment_menus.sql` 逻辑下线（`sys_menu.del_flag=1`），本地无路由；执行者用浏览器运行时注入临时路由（仅内存、不落盘/不改 DB）渲染真实组件验证通过，0 console error。
- `upms/dict-value` 为字典抽屉内子页、平台级菜单未授权，仅静态校验；`upms/material/image-tab` 图片库已实机验证（明暗两色）。
- ship-supply 弹窗 `label-width="auto"` 会让商品名动态 label 参与最大宽度计算，超长商品名（约 >15 字）会压缩内容区，属边缘情况。
- 全量单测当前 2 例失败为外部并行会话在改的 `page-design/components/{limited-activity,seckill,discount}` 与 `page-designer/preview-parity.test.ts` 口径不一致（被改文件 mtime 19:25–19:26），不在本需求写范围。
