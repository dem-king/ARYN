# 接龙报货改造方案

> 2026-11-14 · 基于 `ship-chandler` 当前工作树调研。结论：**不是入口问题，是单据模型问题**。
> 当前把「一次性清单 → 订单」建模成了「24 小时多人协作购物车」，导致建车、过期、权限、
> 二次核定、重复粘贴翻倍、单位静默错配六个问题同时存在。建议把接龙迁到「清单直下单」
> 链路，共享购物车只保留真正的多人协作场景。

## 0. 一句话结论

| 维度 | 现状 | 改造后 |
|---|---|---|
| 单据 | 接龙寄生在共享车，一车一单，24h 过期 | 一次粘贴 = 一个直下单批次，不挂车、不过期 |
| 权限 | 只有确认人（`requireConfirmer`）能贴 | 登录用户即可贴，批次归操作人 |
| 复核 | 报告页核对 + 提交弹窗二次核定 | 报告页一次核对，确认即进结算 |
| 重复粘贴 | 数量累加，重贴一次就翻倍 | 默认作废上一份待确认批次，不累加 |
| 单位 | 「3 斤」当「3 件」用，匹配成功也不提示 | 单位校验 + 换算建议，最终由人确认 |
| 入口 | 详情页 11px 小徽标，无车时可能没有入口 | 导入向导 / 补给卡 / 购物车 三处显式入口 |

## 1. 调研结论：四个结构性缺陷

### 1.1 单据模型错配：接龙被迫寄生于共享购物车

证据链：

- 接龙预览端点只有一个：`POST /app/shared-cart/{id}/chain-import/preview`，
  且服务层连续做了四道「车」的校验：
  `requireCart` → `requireEditable` → `requireNotExpired` → `requireConfirmer`
  （`SharedCartServiceImpl#previewChainImport`）。
- 共享车收集窗口写死 24 小时（`COLLECT_WINDOW_HOURS = 24L`，
  `SharedCartServiceImpl` 顶部常量），创建时 `expiresAt = now + 24h`。
- C 端入口只在车详情页的明细卡里，且条件为 `collecting && viewerCanConfirm`
  （`sub-pages/order/shared-cart/detail.vue` 明细卡头部）。
- 粘贴页本身不建车：`chain-import.vue` 从 URL 拿 `cartId`，没有车就无从进入。

后果：

1. **新的一轮必须先建车**：选船 + 靠港（或采购主体 + 收货地址），再进详情，才能贴；
2. **跨天收集会过期**：接龙常常提前几天在群里滚，车 24h 一到就关闭，前一天的明细作废；
3. **只有确认人能贴**：真正在群里收接龙的人如果不是确认人，连入口都看不到；
4. **多一次核定**：报告页已经逐行核对过，提交时还要在弹窗里再核一遍数量
   （`detail.vue` 的 `confirmState.approved`），同一批数据核两次。

### 1.2 重复粘贴 = 数量翻倍

- 确认并入时，已存在的活动明细**累加数量**（`mergeIntoCartItems` →
  `accumulateOnActive`，`SharedCartServiceImpl` 2400+ 行）；
- 接龙预览把原文 sha256 写进了 `file_sha256`（`job.setFileSha256(sha256(text))`），
  但**全仓没有任何地方读取它做重复判断**；
- 没有「替换上一份」「以本次为准」的选项，只有「并入」。

现实场景：群里接龙改了一版（某人改数量、加人、删项），工作人员把新版整段再贴一次。
系统会把旧版数量和新版数量相加，用户只能在共享车里逐行手删。这是当前最容易造成错单的一条。

### 1.3 单位口径静默错配

- 解析器 `ChainOrderTextParser` 会解析出 `unit`（斤/公斤/箱/瓶…），
  但 `previewChainImport` 只用 `item.goodsName()` 和 `item.quantity()`，
  **单位被丢弃**：`classify(match, null, String.valueOf(quantity))`；
- `ReplenishImportClassifier` 只校验数量是否正整数、MOQ、步长、库存、规格文本，
  **不校验单位**；
- 商品域 SKU 没有「销售单位」字段，包装规格写在商品名里
  （例：`218spu_name_spec_backfill_incremental.sql` 把「国产香蕉」改成
  「国产香蕉 约1kg/把」）。

后果：接龙写「香蕉 3 斤」，命中「国产香蕉 约1kg/把」后按 **3 把**（约 3kg）下单；
写「猪五花 2 斤」，命中「猪五花肉片 300g/盒」后按 **2 盒**（600g）下单。
报告显示「已匹配」，没有任何提示。生鲜类几乎每条都要人自己算，算错就是错单。

### 1.4 入口与核对体验

- 有车时：首页补给卡 → 详情 → 明细卡右上角「粘贴接龙」，文字 `22rpx`（约 11px），
  和「导入 Excel」挤在一起；明细为空时反而是两个大按钮——**车越满，入口越小**。
- 无车时：首页补给卡只在有进行中车时渲染；无车占位卡要求 `emptyStrategy=placeholder`，
  而种子数据（`78home_replenish_card_entry.sql`）默认是 `hide`；
  另一条路是购物车 tab 的「共享购物车」胶囊，再建车。
- 报告页是「人名 / 品名 / 数量」的平铺表格，**每行一个归属人输入框**；
  同一人的连续行要重复确认人名，和微信群接龙「一人一段」的结构相反。
  `chain-import.vue` 已到 1946 行，继续在这个页面上叠功能，维护成本会失控。

## 2. 根因

共享购物车的三个隐含假设，接龙一个都不成立：

| 共享车的假设 | 接龙的真实情况 |
|---|---|
| 多人各自加购，需要成员与邀请 | 一个人在群里收齐，代所有人报 |
| 先收集 24h，再核定，再提交 | 粘贴即核对，核对完就下单 |
| 明细归属必须是成员账号 | 接龙里的人大多没有账号，只需要姓名标签 |

所以问题不是「入口不够快」，而是**把一个一次性清单任务塞进了协作容器**。
继续在共享车上打补丁（加入口、加去重、加单位提示）只能缓解，无法消除 24h、权限、二次核定。

## 3. 方案对比

| 方案 | 做法 | 工作量 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|---|
| A. 修补共享车 | 加入口、加「替换上一份」、加单位提示 | 3-5 人日 | 改动最小，现有链路不动 | 24h、确认人权限、二次核定仍在；重复粘贴只能治标 | 可做过渡，不做终态 |
| B. 新建「接龙单」领域对象 | 新表、新状态机、新端点、新页面 | 3-4 人周 | 语义最纯，未来可扩展分享/多人编辑 | 与现有导入任务、报告、确认、恢复大量重复；过度设计 | 不推荐 |
| **C. 接龙下沉到清单直下单** | 复用 `replenish_direct_import`，加来源/人名/单位，去车 | **7-9 人日** | 复用现成解析/报告/确认/结算链路；无车、无过期、无权限门槛 | 需要改确认聚合与结算透传；与共享车并存两套入口 | **推荐** |

为什么 C 不是 B：`replenish_direct_import` 本身已经是「一次上传/粘贴 = 一个批次」的对象，
有状态机、解析行、确认回执、恢复端点，只是没有配送上下文与人名。再加一张「接龙单」表，
等于把同一套东西抄一遍。等接龙真的需要群内多人编辑/分享时，再把批次升级成独立单据也不迟。

行业对照也一致：同类接龙工具的核心是「接龙直接承接成订单/对账」，而不是先建一个协作购物车
（有赞「从发起接龙到订单承接」、群接龙「订单管理/批量发货」、TuanFlow「贴清单自动算每人金额」）。
参考：[有赞微信群接龙](https://www.youzan.com/chanpin/weixingqunjelongxiaochengxu)、
[群接龙帮助手册](http://help.qunjielong.com/archives/)、[TuanFlow](https://tuanflow.cc/)。

## 4. 推荐方案 C：详细设计

### 4.1 领域模型

把 `replenish_direct_import` 从「Excel 导入任务」升级为「清单批次」：

| 表 | 新增字段 | 说明 |
|---|---|---|
| `replenish_direct_import` | `source_type` | EXCEL / CHAIN，存量默认 EXCEL |
| | `biz_scene` | 2 船供 / 3 岸上企业（与 `purchaseScene` 同口径） |
| | `vessel_id` / `vessel_call_id` | 船供批次的分组与展示上下文 |
| | `user_address_id` | 岸上批次的配送目标 |
| | `buyer_org_id` / `buyer_org_name` | 岸上批次的采购主体快照 |
| | `title` | 批次展示名，默认「{日期} {靠港/地址} 接龙」 |
| | `superseded_by` | 被哪一份新批次替代（空=未替代） |
| | `cancel_reason` | 替代/取消原因，报告页可读 |
| `replenish_direct_import_row` | `person_name` | 接龙人名原文；EXCEL 行为空 |
| | `raw_unit` | 接龙单位原文（斤/箱/瓶…） |
| | `matched_unit` | 命中 SKU 的包装单位（把/盒/箱…），解析不出为空 |
| | `unit_factor` | 换算系数（1 斤=0.5kg；按包装重量折算），仅建议值 |
| | `unit_status` | OK / CONVERTIBLE / MISMATCH / UNKNOWN |

状态机沿用：1 待确认 / 2 已确认 / 3 已取消。
「被替代」不新增状态，走 3 + `cancel_reason`，避免前端到处判断第四种状态。

批次分组键（用于「上一份待确认批次」查询）：

- 船供：`tenant_id + operator_user_id + vessel_call_id`
- 岸上：`tenant_id + operator_user_id + user_address_id`

这样同一船同一靠港、同一操作人只有一条「当前批次」；新粘贴能准确找到上一份。

### 4.2 端点

新增：

```
POST /app/replenish-import/chain/preview
body: {
  text, scene, vesselId?, vesselCallId?, userAddressId?, buyerOrgId?, buyerOrgName?,
  replaceImportId?   // 前端明确选择「替换上一份」时传
}
→ ReplenishDirectImportVO
  + previousPendingImportId / previousPendingSummary
  + unitWarningCount
```

复用不变：

- `GET /app/replenish-import/imports/{id}` 取回报告
- `GET /app/replenish-import/pending` / `latest` 恢复
- `POST /app/replenish-import/imports/{id}/confirm` 确认
- `POST /app/replenish-import/imports/{id}/cancel`（新增，取消/替代用）

改造：

- `ReplenishDirectImportConfirmDTO.RowAction` 增加 `personName`（归属人修正）、
  `unitMode`（`AS_IS` / `CONVERT`）。
- 确认聚合键从 `skuId` 改为 `personName + skuId`；输出项增加 `contributorName`。
- 库存校验分两层：先按 `person + sku` 校验 MOQ/步长，再按 `sku` 汇总校验总库存
  （否则两个人各 4 件、库存 8 件时逐人通过、整单超卖）。
- 确认后若 `replaceImportId` 存在，把旧批次置 3 + `cancel_reason='被新版本接龙替代'`。

Excel 端点保持不变，`source_type=EXCEL` 走原来的「无 personName」分支。

### 4.3 单位规则（P0 先做「不静默」，P1 再做「精确换算」）

P0：新增纯函数 `ReplenishUnitRules`，三步：

1. **解析 SKU 包装**：从商品名/规格中提取「重量 + 包装单位」，例如
   `约1kg/把`、`500g/袋`、`300g/盒`、`10枚/盒`、`1L/瓶`。
2. **归一化**：斤→0.5kg、两→0.05kg、g→0.001kg、ml→0.001L；包装单位记为把/盒/袋/箱…
3. **判定**：
   - 接龙单位与包装单位同类且可换算 → `CONVERTIBLE`，给出建议数量
     （如 3 斤 ≈ 1.5 把，按步长向上取 2 把），报告页显示「≈ 2 把」；
   - 同类但除不尽/低于 MOQ → `CONVERTIBLE` + 提示人工确认；
   - 不同类（斤 vs 盒、个 vs 箱）→ `MISMATCH`，
     报告行显示「接龙 3 斤 / 商品按盒卖（300g/盒），请确认」；
   - 包装解析不出 → `UNKNOWN`，显示原文单位 + 商品规格，要求人工点一下。

**任何情况下都不自动改数量**：换算只给建议，最终以人点选/确认的值为准。
这样既消除「静默按件数下单」，又不需要先把商品主数据补全。

P1：给 `goods_sku` 增加 `sales_unit`（销售单位）、`unit_weight_kg`（单件净重）、
`unit_spec_text`（展示规格），做一次目录回填；`ReplenishImportMatchVO` 下发这三个字段，
换算就从「猜商品名」变成「读结构化字段」。这一步是数据治理，和接龙改造解耦。

### 4.4 重复粘贴 / 批次替换

产品规则：

1. 预览时按分组键查「待确认」批次；有则在报告页顶部显示：
   「检测到 11-14 10:20 还有一份待确认的接龙（3 人 / 18 项）」；
2. 默认按钮 **「替换上一份」**：确认新批次后，旧批次置取消；
3. 次按钮 **「作为新批次」**：两份都保留，P1 提供「合并两份待确认批次一起结算」；
4. 已确认（状态 2）的批次不自动作废，只提示：
   「该靠港 10:20 已确认过一份接龙，本次将作为补单」；
5. 永远不再往已有订单/购物车里静默累加。

这样「接龙 2.0」重贴一次，结果是替换而不是翻倍；要补单也看得见。

### 4.5 C 端流程与入口

目标流程（船供，5 步内到结算）：

```
首页/购物车 → 接龙报货
  → 配送目标（默认当前船+靠港，可切换）
  → 粘贴（读剪贴板）
  → 报告（按人分组，异常优先）
  → 确认 → 结算（createWay=2 + handoff）→ 下单
```

入口（三处，按场景出现，不做全量用户）：

1. **「导入清单下单」向导**（首选）：step 1 增加「上传 Excel / 粘贴接龙」两个输入方式。
   接龙模式复用向导已有的配送目标，选完直接进粘贴页；`directImportOrderStore`
   的 handoff 原样复用，结算页不需要新逻辑。
2. **首页补给卡**：有进行中共享车时，保留一个次级动作「粘贴接龙」（走车路径，兼容）；
   无车时占位卡 CTA 改为「粘贴接龙 / 导入清单」，并把默认 `emptyStrategy` 从 `hide`
   调整为 `placeholder`（仅船供租户/已登录，不污染零售首页）。
3. **购物车 tab 船舶面板**：在「共享购物车」胶囊旁加「接龙报货」胶囊；
   岸上客户在同一位置显示「粘贴清单下单」。

不在首页金刚区/悬浮球做全局图标：受众窄，对零售用户是噪声。

### 4.5.1 一个容易忽略的坑：handoff 只有 24h

`directImportOrderStore` 的交接有效期是 24 小时（`DIRECT_IMPORT_HANDOFF_TTL_MS`），
而接龙经常跨 2-3 天收集。如果第 1 天粘贴、第 3 天确认，结算页会判定交接过期，
船供/岸上配送上下文丢失，订单退回普通下单口径。

改造要求：批次把配送目标落到服务端（4.1 的 `vessel_id/vessel_call_id/user_address_id`），
报告页恢复时用批次里的目标重新 `begin()` 一次 handoff（新的 `handoffId`、新的 `createdAt`），
或者把直下单交接的 TTL 延长到 7 天。两条至少做一条，并在恢复路径加测试。

### 4.6 报告页重构：按人分组，而不是按商品平铺

这是「核对累」的直接解法：

```
┌ 批次头：11-14 · 远洋xx轮 · 28号靠港      3 人 / 18 项 / 2 项待处理 ┐
│ [上一份待确认：10:20 · 18项]  [替换] [作为新批次]                  │
├ 任化东（9 项 · 全部匹配）                              [展开]      │
│   红富士冰糖心 4斤 → 烟台红富士苹果 4个装 约1.2kg      ✓           │
│   香蕉 3斤 → 国产香蕉 约1kg/把    ≈2把  [按2把] [改数量]  ⚠单位     │
├ 水手长（5 项 · 2 项待处理）                            [展开]      │
│   盐汽水 4包 → 未匹配   [搜商品] [跳过]                             │
└ 吸底：确认并去结算（16 项）                                          ┘
```

规则：

- 人名在组头编辑一次，不再每行一个输入框；
- 匹配成功的行默认收起为一行摘要，只展开异常/单位/候选；
- 「仅看待处理」保留，默认开；
- 候选沿用现有横排 chips + AI 推荐一键采用；
- 页面按「人」分组，与微信群接龙原文结构一致，核对时能一眼发现「谁被串行」。

实现上建议把 `chain-import.vue` 的报告区抽成 `ChainReport` 组件，
车版和直下单版共用；先抽组件再改，避免继续在 1946 行文件里叠。

### 4.7 结算与订单归属

现有链路已经具备承载归属的字段，缺的是前端透传：

- `CreateOrderSkuReqDTO` 已有 `contributorUserId` / `contributorName`；
- `OrderPriceComputeService` 下单时已把 `contributorName` 写进 `order_item`；
- 但 C 端结算页 `buildSkuReqList()` 只取 `spuId/skuId/quantity/specsInfo/picUrl`，
  **把 contributor 丢了**；
- 且 `mergeDuplicateSkuItems()` 按 `skuId` 合并，两个人同 SKU 会被并成一行。

必须一起改：

1. 确认回执的 `items` 增加 `contributorName`；
2. `goodsStore.setGoodsList` 写入该字段；
3. `mergeDuplicateSkuItems` 的合并键改为 `skuId + contributorName`；
4. `buildSkuReqList` 透传 `contributorName`；
5. 结算/订单详情已有的「为谁购买」展示直接生效。

这几步不改服务端下单模型，只补前端漏掉的归属链。

### 4.8 数据与双模式

- boot：`122chain_direct_import_incremental.sql`
- cloud：`123chain_direct_import_incremental.sql`
- 两张表都是既有表加列，**不新增表**，不触发 cloud 租户表白名单变更；
- 幂等写法沿用既有增量脚本（如 121）的 `information_schema` 自检 + `PREPARE`；
- 登记进 `db/boot/build-full-sql.mjs` 的 `sections`，重新生成 `aryn_boot_full.sql`，
  再跑 `verify-full-sql.mjs`；
- 存量 CHAIN 任务留在 `shared_cart_import`，不迁移；老车路径保留一个版本。

### 4.9 灰度与回滚

- 开关：`aryn.chain-direct.enabled`（boot / order-biz 两份 yml + cloud Nacos）。
- 灰度：先一个船供租户、一条靠港；对比「粘贴→确认」耗时、人工改动行数、单位告警数。
- 回滚：开关关掉即回老车路径；已产生的直下单批次仍可只读/继续确认，不丢数据。
- 观测：批次总数、每批人数/项数、替换率、单位告警率、确认后取消率、订单退款率。

## 5. 分阶段排期

### 5.0 如果只想先止血（2-3 人日，方案 A 的最小补丁）

不换模型，只堵最危险的三个洞：

1. **重复粘贴检测**：预览时用 `file_sha256` + 分组键查同操作人同靠港的近期批次，
   命中就弹「这份接龙已经贴过，继续会累加」，默认取消；保留「仍要追加」。
   （只能防呆，不能替换，因为车明细没有记录来源批次。）
2. **单位告警**：报告行并排显示「接龙单位 / 商品规格」，斤 vs 盒/把/箱标黄，
   不自动改数量。
3. **入口**：补给卡加次级动作，seed 的 `emptyStrategy` 改 `placeholder`。

这个补丁适合「一周内先让现场不难用」，但 24h、确认人权限、二次核定仍在，
不建议作为终态。

### 5.1 P0：可用的直下单接龙（7-9 人日）

| 天 | 内容 |
|---|---|
| D1 | DDL 双模式 + entity/VO/DTO + `source_type/person_name` |
| D2 | `chain/preview`：解析（AI→规则回退）+ 匹配（fuzzy/向量复用）+ 落行 |
| D3 | 单位规则 `ReplenishUnitRules` + 报告行字段 + 单测 |
| D4 | confirm：按人聚合、双层库存校验、contributor 回执、批次替代 |
| D5 | 向导双输入 + 粘贴页 + 报告按人分组（抽 `ChainReport`） |
| D6 | 结算透传（merge key + buildSkuReqList）+ 三处入口 |
| D7 | 契约测试、i18n、双模式 SQL、全量编译/测试 |
| D8-9 | 真机（iPhone 15 Pro Max）走 7 行真实接龙 + 灰度 |

### P1：精确单位与批次管理（3-5 人日）

- `goods_sku` 增加销售单位/净重/规格文本，目录回填；
- 批次列表页（同一靠港的待确认/已确认/已替代）；
- 「合并两份待确认批次一起结算」；
- 管理端只读批次列表（可选）。

### P2：与共享车协作打通（2-3 人日）

- 「把接龙批次并入共享车」（复用 `mergeIntoCartItems`），仅给「船员自己也在加购」的少数场景；
- 按船花名册/成员姓名词典自动归属（把现在的字符串匹配升级）。

## 6. 验收清单

功能：

1. 船供用户、无共享车，从首页进「接龙报货」，粘贴 7 行真实接龙 → 报告 25 项人名/品名/数量正确；
   确认 → 结算 → 订单明细按人拆行，`contributorName` 正确。
2. 两个人报同一 SKU → 订单两行，不被合并；配送标签能按人分。
3. 同一接龙改版重贴 → 默认替换上一份，数量不翻倍；选择「作为新批次」则生成补单提示。
4. 「香蕉 3 斤」命中「约1kg/把」→ 显示单位告警与建议，人确认后才下单。
5. 岸上企业按收货地址走同一流程，`way=3`；纯零售租户不出现入口。
6. 共享车老路径（开关关）行为逐字节不变。

技术：

- `mvn clean test -pl aryn-boot -am` 通过；
- uniapp `pnpm type-check && pnpm test:unit` 通过；
- `navigation-contract` / `require-login-contract` / shared-cart 契约不回归；
- boot/cloud 增量 SQL 均存在且可重复执行，`aryn_boot_full.sql` 重新生成并 verify；
- 无硬编码 `/boot`，cloud 首段为 `/mall-order`；
- 中英文案齐全，真机截图留档。

## 7. 风险与明确不做

风险：

- **单位解析覆盖率**：商品名格式不统一时 `UNKNOWN` 会较多；用「必须人工点一下」兜底，
  不允许静默通过。
- **AI 解析波动**：保留规则解析回退，报告标 `parserSource`；不把 AI 结果当唯一事实。
- **归属链断裂**：`mergeDuplicateSkuItems` 不改，前面所有按人拆行都会在结算页被合并——
  这是 P0 的硬验收项。
- **两套入口并存**：一个版本内允许车版与直下单版并存，开关控制；下个版本再决定车版入口去留。

明确不做：

- 不做群接龙小程序、拼团、在线收款；
- 不做接龙模板/商品库管理；
- 本期不做管理端接龙运营台（P1 只做只读列表）；
- 不删除共享购物车，它仍是船员自助加购的协作容器。

## 8. 待确认

1. 你说的「不实用」，最痛的是哪一条：建车/入口、重复粘贴翻倍、单位错配、核对太累？
2. 目标用户是否只船供？岸上企业客户是否也要开放「粘贴清单」？
3. 是否接受「接龙默认直下单，共享车只做协作」的定位？
4. P0 是否按 7-9 人日排？如果要先上最小补丁，我可以把 A 方案的 3 个改动拆出来单独排期。
