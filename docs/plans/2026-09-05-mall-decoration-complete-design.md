# 商城装修完整能力建设方案

> **For Codex:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task, `test-driven-development` for behavior changes, and verification-before-completion at every phase gate.

**Goal:** 将现有商城装修升级为面向多租户电商 SaaS 的统一装修平台，达到主流竞品的编辑、模板、数据组件、预览、发布治理和多端交付水平，并在易用性、可观测性和安全性上形成差异化。

**Architecture:** 保留 `page_design` 作为页面身份与草稿入口，以统一的 schema v2/v3 文档作为管理端和 UniApp 的契约；新增主题、组件能力、发布计划、审批和实验等平台对象。管理端负责编辑体验，后端负责校验、版本、权限、发布和租户隔离，移动端只消费已发布快照，不直接解释管理端临时状态。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、Redis/Redisson、Sa-Token、Vue 3、TypeScript、Element Plus、Vitest、Playwright、UniApp、Alova、Maven、pnpm、MySQL。

**范围约束:** 不创建 Git worktree；所有后端跨业务调用继续走 `*-api`/Dubbo；所有数据库变更同时提供 Boot/Cloud 增量 SQL；所有前端请求继续通过 `parseOpenBoot`/`rewriteBootUrl` 适配双模式。

---

## 1. 产品目标和用户角色

### 1.1 目标

装修平台的核心任务不是“拖几个组件”，而是让运营人员在不写代码的情况下完成：选主题、搭页面、绑定商品和营销、预览多终端效果、审核并发布、观察效果、快速回滚。

### 1.2 角色

| 角色 | 能力 |
|---|---|
| 店铺运营 | 创建/编辑草稿、使用模板、预览、提交发布 |
| 店铺管理员 | 发布、下线、回滚、管理店铺模板和主题 |
| 平台运营 | 管理系统模板、组件包、全局主题、审批规则 |
| 审核人 | 查看变更 diff、批准/拒绝发布 |
| 数据/研发 | 查看渲染错误、性能和转化数据，不直接改业务内容 |

### 1.3 成功标准

- 新用户 10 分钟内完成“套模板、改商品、预览、发布”。
- 所有页面的草稿、预览、发布和回滚都能定位到租户、操作者和版本。
- 管理端预览、H5、微信小程序使用同一份页面契约；未知组件降级而不白屏。
- 发布失败可以解释原因并给出修复入口，而不是只返回通用错误。
- 页面性能、组件请求失败和转化事件可以按页面版本查询。

## 2. 产品信息架构

统一入口命名为“商城装修”，下分五个工作区：

1. **页面**：首页、活动页、商品专题页、会员页、空白页；支持搜索、标签、状态、渠道和更新时间筛选。
2. **模板**：系统模板、行业模板、租户模板；支持收藏、预览、复制、版本和适用终端。
3. **主题**：品牌色板、字体、圆角、间距、按钮、商品卡片、导航栏和底部导航。
4. **发布中心**：草稿、待审核、已发布、定时发布、灰度发布、历史版本和回滚。
5. **数据看板**：页面访问、组件曝光、点击、加购、领券、转化、渲染失败和性能。

旧 `home-decoration` 路由只保留兼容跳转，首页编辑器最终进入统一 `page-designer/:id`。迁移期间显示“已迁移到新版装修”的一次性提示，不再维护两套保存逻辑。

## 3. 编辑器设计

### 3.1 桌面布局

- 左栏 240px：组件库、模板、素材、最近使用。
- 中栏自适应：375/414/768 宽度预览，可切换 H5、微信小程序和桌面展示框。
- 右栏 360px：属性、数据、样式、交互、校验五个标签页。
- 顶部工具栏：返回、页面选择、撤销/重做、预览、保存状态、提交审核、发布。
- 底部固定状态条：当前版本、最后保存时间、错误数、警告数、页面体积和接口数。

### 3.2 易用性规则

- 首次进入提供三条路径：套模板、从空白页开始、复制已有页面。
- 组件支持搜索、分类、收藏、最近使用和“推荐组合”。
- 添加组件后自动生成合法默认值；空配置不可保存。
- 所有业务选择器使用结构化选择，不让运营填写商品 ID、活动 ID 或内部路径。
- 属性修改支持即时预览、撤销/重做、批量编辑和复制样式。
- 组件配置错误显示在组件卡片和右栏；点击错误直接定位字段。
- 页面大纲支持拖拽排序、锁定、隐藏、复制、批量删除和键盘操作。
- 预览支持二维码、短链、指定终端、指定会员身份和指定时间；预览令牌默认 30 分钟过期。

### 3.3 页面结构能力

在现有平铺组件基础上增加有限嵌套：页面 -> 区块 -> 组件，最多 3 层。区块提供背景、栅格、间距、横向滚动、吸顶和条件显示。禁止任意深层嵌套，避免移动端性能和编辑复杂度失控。

## 4. 组件体系和数据协议

### 4.1 组件分类

基础：页面标题、公告、搜索、留白、富文本、图片、轮播、分割线。  
导航：分类导航、标签导航、营销入口、底部导航。  
商品：手选商品、商品分组、商品排行、商品瀑布流、商品横滑、组合购。  
营销：优惠券、秒杀、拼团、折扣、限时活动、倒计时、会员权益。  
店铺：店铺信息、服务承诺、客服、门店/自提点、评价摘要。  
内容：图文卡片、视频、直播入口、文章/种草内容。

首期新增应优先完成：商品瀑布流、优惠券组合、会员权益、服务承诺、底部导航、视频/直播入口；组件数量达到 25~30 个后暂停扩张，先完善协议和治理。

### 4.2 统一组件元数据

```ts
interface ComponentDefinition {
  type: string;
  version: number;
  label: string;
  category: string;
  terminals: Array<'admin' | 'h5' | 'weapp'>;
  dataSources: string[];
  permissions: string[];
  createDefaultProps: () => Record<string, unknown>;
  validate: (props: Record<string, unknown>) => ValidationIssue[];
  migrate?: (oldProps: unknown) => Record<string, unknown>;
  performanceBudget?: { requestCount: number; imageBytes: number };
}
```

组件的 `props` 只存配置，不存商品名称、价格、库存等业务快照。业务数据通过 `dataSource` 描述，渲染时按当前租户和已发布版本读取。组件必须声明 loading、empty、error、invalid、forbidden 五种状态及降级策略。

### 4.3 数据源协议

统一支持 `manual`、`category`、`tag`、`search`、`ranking`、`activity`、`coupon`、`shop` 七类数据源；每类有 `limit`、`sort`、`filters`、`cacheTtl` 和 `fallback`。管理端只保存引用和筛选条件，后端在发布时检查引用是否存在、是否属于当前租户、活动是否在有效期内。

## 5. Schema 与版本模型

页面文档升级为 schema v3，兼容 v1/v2：

```ts
interface DecorationDocumentV3 {
  schemaVersion: 3;
  page: PageSettings;
  themeRef?: string;
  sections: DecorationSection[];
  terminalOverrides?: Record<string, Partial<PageSettings>>;
  analytics?: { enabled: boolean; campaignId?: string };
}
```

发布快照必须是完整、自包含、不可变的文档，包含 schema 版本、主题快照、组件版本、数据源配置、校验结果、发布人和发布时间。草稿允许引用最新主题，发布时固化主题 token，防止修改主题后历史版本漂移。

需要新增或扩展的表：

- `page_design_theme`：租户主题及系统主题引用。
- `page_design_release`：发布申请、审批、定时和灰度规则。
- `page_design_release_target`：灰度租户、渠道和终端目标。
- `page_design_audit_log`：保存、发布、下线、回滚、模板应用等审计事件。
- `page_design_metric_daily`：页面/版本/组件的聚合指标。
- `page_design_asset_ref`：页面引用的图片、视频、素材和可用性状态。

所有表包含 `tenant_id`、审计字段和 `del_flag`；Boot/Cloud 各自提供编号递增、可重复执行的增量 SQL，并补齐 full SQL。

## 6. 后端 API 设计

沿用 `/promotion/pagedesign` 作为微服务域，新增接口：

| 能力 | 方法与路径 | 说明 |
|---|---|---|
| 主题 | `GET/POST/PUT /pagedesign/themes` | 系统/租户主题 CRUD |
| 发布检查 | `POST /pagedesign/{id}/validate` | 返回错误、警告、引用和性能预算 |
| 发布申请 | `POST /pagedesign/{id}/release` | 立即、定时、灰度三种策略 |
| 审核 | `POST /pagedesign/releases/{id}/approve` | 审批通过或拒绝 |
| 发布状态 | `GET /pagedesign/releases/{id}` | 状态、目标、失败原因 |
| 差异 | `GET /pagedesign/{id}/versions/{a}/diff/{b}` | 结构化组件级 diff |
| 素材引用 | `GET /pagedesign/{id}/assets` | 检查失效、外链和大小 |
| 运行指标 | `GET /pagedesign/{id}/metrics` | 页面/组件/版本聚合指标 |
| 发布内容 | `GET /app/pagedesign` | 只返回当前租户已发布快照 |

发布流程：草稿保存 -> 服务端校验 -> 创建 release -> 审批（可配置跳过） -> 生成不可变版本 -> 更新线上指针 -> 清理缓存 -> 记录审计 -> 发出发布事件。所有状态变更使用事务；缓存只在提交成功后失效。

## 7. 发布治理和安全

- 权限拆分为查看、编辑草稿、管理模板、提交审核、发布、下线、回滚、管理主题、查看指标。
- 发布前阻断：schema 错误、未知组件、失效链接、跨租户引用、缺少必要数据源、超性能预算、图片/视频不可访问。
- 警告项可配置为阻断或允许，例如活动将在 24 小时内结束、页面图片过大。
- 发布支持立即、定时、按租户灰度、按终端灰度；灰度期间保留稳定版本作为 fallback。
- 回滚只允许回到已发布快照，不允许直接把草稿覆盖线上版本。
- 预览 token 绑定租户、页面、草稿修订号、终端和过期时间，禁止跨租户使用。
- 公开读取接口只读已发布版本；匿名预览必须使用一次性或短时 token。
- 所有操作写审计日志，记录操作者、IP、租户、前后版本和结果。

## 8. 运营和数据闭环

统一事件：`page_view`、`component_exposure`、`component_click`、`goods_detail`、`add_cart`、`coupon_receive`、`activity_enter`、`order_paid`、`render_error`。事件必须带 `tenantId`、`pageId`、`versionId`、`componentId`、`componentType`、`terminal` 和 `traceId`。

看板首期提供：页面访问趋势、组件点击率、商品/优惠券转化、渲染错误 Top、接口耗时、页面 JSON 大小、图片字节数和首屏耗时。数据只做聚合查询，不在装修请求链路中执行重查询。

## 9. 分期交付

### Phase 0：收口基线，1 周

- 确认新旧入口迁移策略和权限矩阵。
- 固定 Node/pnpm/UniApp 构建环境，记录当前基线失败。
- 建立 Playwright 与临时 MySQL 骨架。
- 输出 17 个组件的契约清单和端到端 fixture。

验收：能在本地创建页面、保存草稿、读取 fixture；测试环境能执行 Boot/Cloud 增量 SQL。

### Phase 1：可靠发布，2~3 周

- 完成服务端 validate、结构化错误、发布申请、审计和 diff。
- 完成 E2E：新建、添加、排序、复制、保存、预览、发布、App 读取、回滚、跨租户拒绝。
- 完成旧首页入口跳转和 v1/v2/v3 迁移测试。

验收：发布主链路通过；线上指针、缓存、版本和审计一致。

### Phase 2：易用编辑器，3~4 周

- 完成模板中心、主题编辑器、素材引用检查、批量操作、快捷键和错误定位。
- 引入页面/区块/组件三级结构和有限嵌套。
- 增加 H5/微信预览、二维码、指定身份和终端切换。

验收：新用户按模板完成首个页面不超过 10 分钟；50 个组件页面滚动、排序和撤销操作无明显卡顿。

### Phase 3：营销与运营组件，4~6 周

- 新增商品瀑布流、优惠券组合、会员权益、服务承诺、底部导航、视频/直播入口。
- 统一数据源协议、失效提示、缓存策略和 fallback。
- 完成管理端、H5、微信 fixture 一致性测试。

验收：组件数据状态齐全；活动失效、商品下架和接口失败都不会白屏。

### Phase 4：治理与增长，3~4 周

- 定时/灰度发布、审批流、发布报告、性能预算和指标看板。
- 接入页面/组件事件和错误监控。
- 主题/组件包按租户开关，支持行业模板。

验收：可以按租户、渠道、终端发布；指标能定位到页面版本和组件。

## 10. 代码落点和测试策略

### 管理端

- 扩展 `views/promotion/page-designer/schema`：v3 类型、迁移器、校验器、diff。
- 扩展 `registry/component-registry.ts`：元数据、终端、数据源和性能预算。
- 新增 `components/theme-editor.vue`、`release-dialog.vue`、`validation-panel.vue`、`asset-check.vue`、`version-diff.vue`。
- 扩展 `composables/use-page-designer.ts`：发布检查、主题快照、终端覆盖和 release 状态。
- API 全部使用 typed transport 类型，并覆盖 Boot/Cloud URL 重写测试。

### 后端

- 在 `aryn-promotion-api` 增加 DTO/VO/API 接口，禁止管理端直接依赖 biz 实现。
- 在 `aryn-promotion-biz` 增加主题、发布、审计、指标服务和 Controller。
- 事务、租户、权限和缓存测试放在 service/controller 层；跨模块数据走已有 API/Dubbo。
- 增量 SQL 同时放 `db/boot` 和 `db/cloud`，更新 `build-full-sql.mjs` 并执行完整 SQL 校验。

### UniApp

- 扩展 `components/diy/schema`、`registry.ts` 和 `retail-normalizers.ts`。
- 页面只消费已发布文档；终端覆盖由 schema 适配器完成。
- 为每个组件提供 data/empty/error/invalid/forbidden fixture，加入 H5/微信构建检查。

### 必须覆盖的测试

- schema v1/v2 -> v3 迁移和未知组件降级。
- 草稿乐观锁、并发保存、发布指针、回滚、定时发布和灰度 fallback。
- 租户隔离、权限矩阵、预览 token 过期和跨租户拒绝。
- 17 个既有组件 + 新组件的 registry 完整性和端一致性。
- Playwright 主链路和页面性能预算。
- Boot/Cloud API 路径及数据库 SQL 幂等执行。

## 11. 最终验收门槛

- `mvn test -pl aryn-boot -am`、管理端 typecheck/unit/lint/build、UniApp type-check/H5/微信构建达到约定基线。
- E2E 主链路通过率 100%；跨租户拒绝 100%。
- 17 个现有组件和新增组件在管理端、H5、微信三端 fixture 结构一致，未知组件不白屏。
- 发布前错误必须为 0；警告、性能预算、素材失效和数据源失效可解释。
- 任何线上版本均可从审计日志定位到租户、操作者、release、version 和主题快照。
- 首页和微页面只剩一套编辑、保存、预览、发布实现；旧入口仅做跳转。

## 12. 实施顺序

先做 Phase 0/1，证明可靠发布，再做 Phase 2 易用性，随后扩充营销组件，最后建设治理和增长能力。不要先堆组件数量；组件协议、版本迁移、错误降级和跨端 fixture 是后续扩展的硬基础。
