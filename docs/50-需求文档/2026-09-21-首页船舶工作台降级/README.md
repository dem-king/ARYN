# 首页船舶工作台降级为单行状态条

> 日期：2026-09-21
> 状态：已实现
> 关联：[船供化交互调研与竞品对标](../../2026-09-19-船供化交互调研与竞品对标/README.md)、[共享采购与商品统一](../../2026-09-20-共享采购与商品统一/README.md)

---

## 1. 问题

首页 `ShipWorkbench` 是装修页上方的一张完整卡片（船舶名 + 切换 + 靠港 + 「去选购 / 常购」两个大按钮）。初版即占据首屏第一权重，存在四个问题：

| # | 问题 | 依据 |
|---|---|---|
| 1 | **四层白卡堆叠，无视觉节奏** | 工作台 → 轮播 → 宫格 → banner 全是 `bg-white + rounded`，底色又是浅灰；商品被推到 60% 视口以下 |
| 2 | **主次颠倒** | 可点的「切换 >」是 24rpx 蓝字；不可点的「已选靠港」是绿色胶囊——最显眼的元素反而不可交互 |
| 3 | **机器时间戳** | 直接渲染 `2026-09-22 00:15:33`，秒级精度只有阅读成本 |
| 4 | **入口三重重叠** | 卡内「去选购」+ 宫格「全部商品」+ 底部 TabBar「分类」指向同一处 |

更根本的是**对多数访客是负资产**：`loadWorkbench()` 在未登录时也 `visible = true` 并渲染 `guest` 态，新用户第一次打开小程序第一眼看到的是与船舶无关的内容。这与自家调研报告 §7.3「首页第一屏不应出现任何警告性文案」的方向相反。

## 2. 决策

采纳「方案 B：按关系分层显示」，同时以方案 A 的**单行状态条**作为视觉载体。分两层落地：

### 2.1 视觉：卡片 → 单行状态条（88rpx）

```
⚠ 悦航1号 · 上海港 3号泊位 · 明天 00:15       常购
```

- 左侧主热区点击弹出既有的 `ShipContextPicker`（查看/切换船舶与靠港计划）
- 「常购」保留为右侧轻量文字入口，仅 `ready` 态提供
- 「去选购」**移除**：底部 TabBar「分类」已是同一入口
- 时间由 `formatCallTime` 压缩为 `今天 HH:mm / 明天 HH:mm / MM-dd HH:mm`（跨年补年份）

### 2.2 可见性：分层闸门

| 用户状态 | 首页表现 |
|---|---|
| 有船有靠港（ready） | 显示状态条 + 常购入口 |
| 有船无靠港（noCall） | 显示状态条（文案「暂无靠港计划，点击申报」） |
| 已登录无船（noVessel） | **不显示**，入口收进「我的」 |
| 未登录（guest） | **不显示** |
| `business_mode = 2` 纯零售 | **不显示** |
| 船舶服务异常（error） | **不显示**（首屏不出现告警文案） |

## 3. 装修组件化（2026-09-21 追加）

方案 B 落地后暴露一个遗留问题：该模块仍是**代码里的固定插槽**（`diy-page` 的 `below-navbar`），
运营既不能调整位置也不能隐藏。本次彻底改为装修组件。

### 3.1 三端同步登记

| 端 | 改动 |
|---|---|
| 移动端 | 新增 `components/diy/diy-ship-workbench/index.vue`，登记进 `diy/registry.ts` 与 `diy/index.vue` 静态渲染分支；**删除**旧 `components/ship-workbench/` 与 `below-navbar` 插槽 |
| 服务端 | `PageDesignComponentTypes` 新增 `SHIP_WORKBENCH` 常量与 `SINGLETON_TYPES`（同页唯一）；`DefaultPageDesignDocumentValidator` 新增重复组件发布阻断 |
| 管理端 | 新增 `page-design/components/ship-workbench/{types,index,setting}.vue`；注册进 `componentRegistry`；组件库自动出现「船舶工作台」 |

### 3.2 关键约束

- **同页唯一**：该组件展示「当前用户此刻的船舶」，放多个只会互相矛盾 → `SINGLETON_TYPES` 发布阻断
- **通用样式真正生效**：组件透传 `commonStyle`（与其他零售组件一致），否则后台的设置项就是在骗运营
- **可见性仍由组件自身判断**：租户 `business_mode` / 登录态 / 船舶关系三重闸门不变，
  运营可以决定「放不放、放哪、是否显示常购」，但决定不了「谁能看到」

### 3.3 存量数据迁移（69 号双模式增量）

**这是本次最容易漏掉的一环**：C 端读取的是**已发布版本快照**
（`page_design.published_version_id` → `page_design_version`），不是草稿 `page_design.page_content`。
只迁移草稿的话，改造上线后所有存量租户首页的船舶工作台会**直接消失**。

因此 `69ship_workbench_component_incremental.sql` 同时迁移两处，并兼容两种历史结构：

| 结构 | 路径 | 组件字段 |
|---|---|---|
| v3 | `sections[0].components` | `props` |
| v2 | 根级 `components` | `formData`（读取侧 `migratePageContent` 自动迁移） |

插入位置为组件数组**首位**，与改造前「导航栏下方、所有 DIY 组件之上」的视觉位置一致。
必须用 `JSON_ARRAY_INSERT(..., '$[0]', ...)`；`JSON_INSERT` 在路径已存在时会**静默不生效**。

## 4. 实现

### 3.1 让 `business_mode` 真正生效（此前是死字段）

`sys_tenant.business_mode` 由 `45ship_supply_menu_permission.sql` 引入，但**全仓无任何代码读取**（调研报告 P1-4）。本次补齐整条链路：

| 层 | 改动 |
|---|---|
| 实体 | `SysTenant.businessMode` |
| VO | `SysTenantShopVO.businessMode`（写入租户公开信息白名单） |
| Controller | `AppTenantController` 透出该字段 |
| C 端 store | 新增 `tenantCapabilityStore`（/shop-info 的客户端缓存，不持久化，登出重置） |
| 管理端 | 租户表单新增「业务模式」单选、列表新增列、`68` 号增量补 `business_mode` 字典 |

### 3.2 关键文件

- `aryn-mall-uniapp/src/components/ship-workbench/index.vue`（重写为状态条）
- `aryn-mall-uniapp/src/utils/vessel-call-time.ts`（时间格式化，手工解析避免 iOS `new Date` 陷阱）
- `aryn-mall-uniapp/src/store/tenantCapabilityStore.ts`（新增）
- `aryn-mall-uniapp/src/store/shipContextStore.ts`（新增 `clearVesselCall`：有船无靠港时必须清掉上一靠港的港口/泊位/时间窗）
- `aryn-mall-uniapp/src/pages/user/user-center/index.vue`（新增「我的船舶」入口，承接未绑定用户）

## 5. 验证

- 小程序：`pnpm type-check`、`test:unit`（**96 用例**，新增 7 个时间格式化 + 4 个可见性契约）、`pnpm build:mp-weixin` 通过
- 后端：`mvn clean compile -pl aryn-boot -am` 通过；`aryn-upms-biz` 测试 **58 用例 0 失败**（含 `AppTenantControllerTest` 对 `businessMode` 的断言）
- Web：`pnpm check:type`、`pnpm lint` 通过
- SQL：`68` 号增量在 boot/cloud 成对新增，`build-full-sql.mjs` 已登记并重新生成 `aryn_boot_full.sql`（`verify-full-sql.mjs` 通过）
- **真机/H5 实测**：启动 H5 dev server 逐状态验证——未登录首页**完全不含船舶概念**；`business_mode=1` 有船有靠港渲染单行状态条、无船与纯零售均不渲染；「我的」页在综合模式下显示「我的船舶」（已绑定显示船名）
- **SQL 实跑**：对运行中的 MySQL 8.0.46 用真实 `aryn_upms` schema 副本执行 68 号脚本两次，无报错、无重复行、中文标签 UTF-8 正确
- **装修组件化实测**：H5 实机以「含 ship-workbench 组件的装修页」驱动——登录且已绑定船舶时状态条按配置位置渲染、`showFrequent` 生效；未登录与 `business_mode=2` 均不渲染
- **Full SQL 端到端**：空库执行 `aryn_boot_full.sql` 全程 **exit 0、零报错**；种子首页首位组件为 `ship-workbench`
- **69 号增量实跑**：v2/v3 两种结构、草稿与已发布版本共 4 条自检全部为 0；连跑两次幂等；非首页页面未被触碰

## 6. 顺带修复：全量 SQL 的既有顺序缺陷

验证 69 号增量时发现：`build-full-sql.mjs` 中 **62 号（船供验收种子）排在 63 号（船舶自助绑定）之前**，
而 62 号的种子要写 `vessel_bind_apply` / `vessel_invite_code`——这两张表由 63 号创建。
结果是空库执行全量脚本会在 62 号处报 `Table 'aryn_boot.vessel_bind_apply' doesn't exist` 并**中断**，
其后所有增量（含 64~69）静默不执行。

该缺陷在本次改动前即存在（`HEAD` 中顺序相同），非本次引入，但会直接掩盖 69 号迁移的效果，
因此一并修正为 **63 号（DDL）先于 62 号（种子）**。修正后空库全量初始化 exit 0、零报错。

## 7. 遗留

- 首页状态条依赖 `shop-info` 返回 `business_mode`；**需后端重新部署**后该闸门才在联调环境生效（容器内目前仍是旧 jar，实测时通过接口拦截验证）
- 装修组件形态下，运营可以自由排序与删除；若误删，用户将看不到船舶入口——服务端只对「同页重复」阻断，不对「首页缺失」阻断
- 钉住旧布局的 `code-review-contract.test.ts` 断言已按本次决策更新：插槽契约改为「必须以装修组件渲染」，并新增 4 条方案 B 可见性契约（已反向验证：破坏闸门时用例失败）
