# 会员专区与低价捡漏规则化改造 · 开发计划

- 需求来源：飞书文档《海员商城小程序 · 会员专区改造方案》（`https://feishu.doubao.com/docx/BopHd7nzhoeVncxuRPLcLYZO6Fb`）
- 提出时间：2026-11-02
- 影响端：后端（product / promotion / order / user）、管理后台、UniApp C 端
- 当前状态：**P0 / P1 已实施，P2 报表轻量版已实施**（2026-11-03；D1–D6 见 §十，落地清单见 §十二）
- 运营配置说明：[操作手册](操作手册.md)

---

## 一、原始诉求与本次目标

飞书方案的核心不是「再加两个活动类型」，而是把会员价与清仓价**从「运营按活动配置」下沉为「商品级标签 + 规则自动算价」**，并给两个专区稳定的选品口径、价格口径与展示口径。

本次目标：

1. 会员专区：商品可批量打「会员商品」标签并配置会员折扣率/封顶价，专区按标签选品，价格由标签 + 会员身份自动计算，不再依赖运营逐个 SKU 配活动。
2. 低价捡漏专区：商品可批量打「出清商品」标签并配置出清价/折扣 + 失效时间，专区按标签选品，到期自动退出。
3. 价格语义统一：会员价、出清价、等级折扣率、券/积分之间的叠加关系一次性收口，避免「高等级会员买会员价比普通价还贵」和「出清后再叠会员折扣导致击穿」。
4. 管理端可运营：商品列表批量打标/取消、单 SKU 精调、专区设置页；C 端两个专区使用统一选品与展示契约。

**非目标（本次不做，见 §九）**：真实临期批次追踪与自动入池、免费菜/赠品菜、会员券包与运费券、让利统计报表、会员价活动全量数据迁移。

---

## 二、飞书方案摘要（已核对原文）

| 模块 | 方案要点 |
|---|---|
| 会员专区 | 商品级 `is_member_item` + `member_discount_rate`；按会员等级或统一折扣算会员价；专区按标签选品 |
| 低价捡漏 | 商品级 `is_clearance_item` + 清仓规则（固定价/折扣 + 时间分层）；到期自动失效 |
| 价格叠加 | 列出活动价链与「会员价 vs 等级折扣」的关系，要求避免双重优惠 |
| 运营流程 | 商品批量打标 → 定时任务维护价池 / 缓存 → C 端专区与商详展示 |
| 技术要点 | XXL-Job 定时刷新、Redis 缓存、接口与表结构清单、分阶段 P0/P1/P2 |
| 统计 | 让利金额、专区转化等报表（P2） |

原文的 `t_item` 商品标签表、价池表是按独立小程序从零设计的；本项目已有 `goods_spu` / `goods_sku` 与成熟的促销引擎，**不应照搬建表**，应把标签落在既有商品表、把算价接入既有促销链。

---

## 三、现有实现盘点（带代码锚点）

### 3.1 低价捡漏专区（已上线，自动折扣率选品）

| 位置 | 现状 |
|---|---|
| `aryn-product-biz/.../service/impl/GoodsSpuServiceImpl.java` | `apiPage(page, goodsSpu, bargainRate)` 校验 `(1.00, 9.99]`，清空分页排序，按折扣率选品 |
| `aryn-product-biz/src/main/resources/mapper/GoodsSpuMapper.xml` | `selectApiPage` 中 `original_price >= sales_price * bargainRate`，并按 `(原价−售价)/原价 DESC` 排序 |
| `aryn-product-biz/.../controller/app/AppGoodsSpuController.java` | `/app/goodsspu/page` 可选 `bargainRate`，不传时行为与旧版完全一致 |
| `aryn-mall-uniapp/src/components/diy/retail-query.ts` | `BARGAIN_SORT='bargain'`、`DEFAULT_BARGAIN_RATE=1.15`、`normalizeBargainRate` |
| `aryn-mall-uniapp/src/components/diy/retail-data.ts` | 装修商品楼层按 `sort` 取数，`memberBanner` 开启时批量补会员价 |

结论：现有「捡漏」是**纯折扣率派生**，没有显式出清标签、没有出清价、没有失效时间、没有与会员价的比较策略。原价虚高或临时调价会把商品误判进专区。

### 3.2 会员专区（type=10 活动 + 展示链路）

| 位置 | 现状 |
|---|---|
| `MemberPriceActivityServiceImpl.java` | 读进行中 `promotion_activity type=10`，`rules={minLevelId, skus:[{skuId,unitPrice}]}`；`getSkuPrices` / `getSpuPrices`；资格 fail-closed；折扣率 fail-open 到 1 |
| `AppMemberPriceController.java` | `/app/member/prices`，支持 skuIds 优先、spuIds 兜底 |
| `MemberActivityGoodsServiceImpl.java` | **已删除（2026-11-03 会员价活动功能整体删除）**；会员专区改为 `/app/goodsspu/page?memberItem=true&sort=member_price` 按标签选品 |
| `PromotionEngineServiceImpl.java` | 会员价在阶梯价之后、新人价之前落价，`memberPricedSkuIds` 阻断新人价与限时折扣覆盖 |
| `OrderPriceComputeService.java` | `resolvePromotionPricePlan` 合并引擎覆盖；`orderMemberBenefitHandler` 对会员价命中做「会员价 vs 原价×等级折扣率」取优（`resolveMemberPriceTakeBetter`），其它活动价不收等级折扣 |
| `StackActivityTypeEnum.java` | `MEMBER_PRICE` 默认可叠券/积分；`stackPriority`=2，介于秒杀(3)与新人(1)之间 |
| `aryn-mall-uniapp/src/utils/member-price.ts` | 展示契约：`min(memberPrice, round2(currentPrice × userDiscountRate))`，文案「会员专享 ¥x」 |

结论：会员价链路完整且已有取优，但**配置粒度是「活动 + SKU 清单」**，运营要为每个商品进活动；专区选品依赖活动命中，没有商品级标签，也没有「会员专区专用」选品/排序。

### 3.3 已有可复用能力

- 商品批量操作：`aryn-mall-ui/.../views/product/goods-spu/index.vue` 已有 `multipleSelection` 与批量上下架 `goodsShelf({status, spuIds})`，可挂批量打标。
- 促销引擎 `preview → reserve` 两次试算 + `promotion_lock` 在途唯一，已处理「活动详情缺失导致锁丢失」。
- XXL-Job tenant 遍历模板：`SeckillStatusJobHandler` + `db/boot/94seckill_status_job.sql` 幂等注册。
- 管理端会员价页面：`aryn-mall-ui/.../views/promotion/ship-supply/index.vue`（等级门槛 + 逐 SKU 价格），新专区设置页可复用其交互。
- 装修楼层 `goods-tabs` 已支持 `automatic / manual / activity` 三种数据源与 `bargain` 排序。

### 3.4 关键缺口

1. **无商品级标签**：`goods_spu` / `goods_sku` 没有会员、出清相关字段。
2. **无临期/批次数据**：`goods_spu`、`goods_sku` 均无保质期、生产批次、到期日字段；`aryn-mall-uniapp` 也没有临期标签。「按剩余保质期自动入池」目前**无法实现**，只能人工打标或按售价/成本规则。
3. **无免费菜模型**：方案中的免费菜/赠品菜在当前代码中完全不存在，需要独立商品与订单 0 元行支持，不能塞进本次。
4. **会员折扣率是全局等级权益**：`MemberBenefitServiceImpl` 已对**每个会员订单**应用等级折扣率；若再按商品配 `member_discount_rate` 并「叠加」，会双重让利，必须定义为「取优」或「覆盖」。
5. **出清价没有价格位**：现有促销链没有「出清」概念，必须新增活动类型或伪活动，否则下单链无法成交。

---

## 四、推荐方案（核心决策）

### 4.1 数据模型：标签落在商品域，规则落促销域

不新建 `t_item`。在 `goods_spu` 增加专区标签与默认规则，在 `goods_sku` 增加精调覆盖：

**P0 新增字段（goods_spu）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `is_member_item` | tinyint(1) | 会员专区选品标签，默认 0 |
| `member_discount_rate` | decimal(5,4) | 商品级会员折扣率，如 0.9000；空表示用等级折扣率 |
| `member_price_fixed` | decimal(10,2) | 会员固定价（迁移旧 type=10 用）；非空时与按率算价取低，保证旧价不涨 |
| `member_price_floor` | decimal(10,2) | 会员价下限（防击穿），空表示 0.01 |
| `is_clearance_item` | tinyint(1) | 捡漏专区选品标签，默认 0 |
| `clearance_price` | decimal(10,2) | 出清固定价，与 `clearance_discount_rate` 二选一 |
| `clearance_discount_rate` | decimal(5,4) | 出清折扣率（对 `original_price` 或 `sales_price` 口径见 §4.2） |
| `clearance_expire_time` | datetime | 出清截止时间，到期自动退出专区与出清价 |

**P0 新增字段（goods_sku）**：`is_member_item`、`member_discount_rate`、`member_price_fixed`、`member_price_floor`、`is_clearance_item`、`clearance_price`、`clearance_discount_rate`、`clearance_expire_time`（同义覆盖，空则回落到 SPU）。

> 为什么 SPU + SKU 两层：管理端批量打标按 SPU 操作（列表勾选），但价格必须按 SKU 计算（多规格售价不同）；SKU 留空即继承 SPU，避免运营逐个规格维护。

### 4.2 价格语义（本方案最重要的一节）

**统一价链**（数字为 `stackPriority`，越大越优先）：

```
拼团(4) > 秒杀(3) > 会员价 / 出清价(2，同 SKU 取低) > 新人价(1) > 限时折扣(0) > 原价
```

1. **商品会员价** = `round2(salesPrice × rate)`，其中
   `rate = min(商品 member_discount_rate（若有）, 用户等级折扣率)`；
   若 `member_price_fixed` 非空，则与按率算价取低（兼容旧活动固定单价、保证迁移后不涨价）；
   结果不低于 `member_price_floor`，且不高于 `salesPrice`（倒挂直接不生效）。
   > 与现有「会员价 vs 等级折扣取优」逻辑同构：现有 type=10 是固定单价，新商品级是按率算价，最终都落到 `resolveMemberPriceTakeBetter` 同一取优分支。
2. **出清价** = `clearance_price` 优先；否则 `round2(original_price × clearance_discount_rate)`；仍以 `salesPrice` 为上限（出清不应高于现售价，高于则不生效并告警）。
3. **出清 vs 会员**：同 SKU 两者都命中时**取低者**，不叠加。理由：出清价通常已低于任何等级折扣，若会员价更高却强制会员价成交，会出现「高等级会员比普通用户更贵」。
4. **出清 vs 等级折扣**：出清价命中时，等级折扣**不再二次打折**，但仍做一次「出清价 vs 原价×等级折扣率」取优（复用 `resolveMemberPriceTakeBetter`），保证任何身份都不吃亏。
5. **券/积分**：出清价与会员价默认可叠（`StackActivityTypeEnum.MEMBER_PRICE` 口径），活动可显式关闭；最低实付兜底沿用 `promotion.stack-policy.min-payable-*`。
6. **倒挂防护**：任何算出的会员价/出清价 ≥ 当前 `salesPrice` 时不落价、不计入命中，仅打日志（避免「先涨价再打折」把专区做成假优惠）。
7. **到期校验（D6）**：出清价有效期以 SKU 的 `clearance_expire_time` 为准；提交订单时重新校验，已到期则按当前有效价重算并提示，**禁止沿用购物车旧价成交**。

### 4.3 落地方式：系统托管活动（影子活动）+ 标签驱动

引擎已经具备「type=10 会员价 → override + detail + promotion_lock + 取优」的完整能力。P0 **不重写价格链**，而是让商品标签自动同步到一个系统托管活动：

- 每个租户两个系统活动（`activity_code` 固定，如 `SYS_MEMBER_ZONE` / `SYS_CLEARANCE_ZONE`）：
  - 会员专区：复用 `promotion_activity` **type=10**，`rules.skus` 由打标商品实时/定时重算；
  - 出清专区：新增 **type=11（出清价）**，`rules.skus` 含 `{skuId, price, expireTime}`。
- 同步触发：管理端打标/改价保存后**同步触发**一次重算（Dubbo/事件均可，boot 模式同进程直接调用），另加每日兜底 Job 处理到期退出与漏同步。
- 引擎侧只加一条出清分支：在会员价落价之后、新人价之前，按 `skuId` 与已有 override 取低合并；`memberPricedSkuIds` 同时包含出清命中的 SKU。
- 订单侧 `stackPriority`：`CLEARANCE` 与 `MEMBER_PRICE` 同为 2，同 SKU 时以引擎给出的更低单价与对应 `activityId` 为准（合并时已去重，不存在并列）。

**为什么用影子活动而不是「下单时直接读商品字段算价」**：

- 复用已验证的 `preview → reserve` 两阶段、在途唯一锁、活动快照、退款口径，风险最小；
- 商品域不需要知道会员等级与订单规则，避免 product → promotion 反向依赖；
- 管理端「活动配置」与「商品打标」两条入口最终汇聚到同一份 `rules`，C 端展示接口（`/app/member/prices`、`/app/member/activity-goods`）零改动即可复用。**（2026-11-03 更新：活动入口与 `activity-goods` 接口已随功能整体删除，会员价只走商品标签 + `/app/member/prices`）**

**代价与约束（必须写进实现）**：

- 系统活动不可在活动列表被手工编辑/删除；管理端需过滤 `activity_code LIKE 'SYS_%'` 或置灰；
- 同步必须幂等（按 `skuId` 全量覆盖，不追加），且保留 `activityId` 稳定（同一租户同一专区始终一条记录），否则在途锁与快照会错配；
- **手工 type=10 活动按期迁移为商品标签（D3 已确认）**，见 §6 P0-C。迁移完成前两者并存，系统活动优先级最低，并在发布治理中提示「同类型并存」，避免迁移窗口期价格错配。

### 4.4 手工 type=10 活动迁移口径（D3）

现有 type=10 活动是「SKU + 固定单价」，新模型是「折扣率 + 下限」。迁移规则：

1. 读取所有**未结束**（`end_time >= now`）的手工 type=10 活动，取其 `rules.skus`；
2. 按迁移时的当前售价折算：`member_discount_rate = round(unitPrice / salesPrice, 4)`，同时 `member_price_fixed = unitPrice`（**不写 `member_price_floor`**，保持默认下限），写入 SKU 标签（SPU 标签在任一 SKU 命中时置 1）；
3. 迁移后会员价 = `min(按率算价, member_price_fixed)`：旧价**不会上涨**；售价下调时按率算价更低，会员同步受益；售价上调时仍锁定旧固定价，直至运营主动清空 `member_price_fixed`；
4. 迁移脚本输出逐 SKU 对照清单（旧单价 / 折算率 / 新算价），供运营抽检；迁移完成后手工 type=10 活动置为只读（不再参与引擎选品），保留一个版本周期后再下线；
5. 迁移必须可重跑且幂等：同一 `skuId` 重复导入以最后一次为准，不得产生重复标签或覆盖运营手工调整（以 `update_time` 较新者胜）。

### 4.5 出清时间分层（P1，P0 只做固定价/固定折扣）

P1 在 `clearance_rule` 表中配置分层：如「距 `clearance_expire_time` ≤48h 再打 8 折、≤24h 再打 6 折」。算价在同步任务中完成（一次算出当前生效价写入影子活动），避免下单链路做时间比较。若后续改为读时计算，必须保证同一请求内 `preview` 与 `reserve` 使用同一时间基准（`PromotionContextDTO.activityTime` 已具备）。

---

## 五、接口与数据变更

### 5.1 后端接口

| 端 | 方法/路径 | 说明 |
|---|---|---|
| 管理端 | `POST /product/goodsspu/tags/batch` | 批量打/取消会员、出清标签；入参 `{spuIds, memberTag?, clearanceTag?, memberRate?, memberFixedPrice?, clearancePrice?, clearanceRate?, expireTime?}`；返回逐条结果 |
| 管理端 | `GET /product/goodsspu/tags/{spuId}` | 读取 SPU + SKU 标签与规则（表单回显） |
| 管理端 | `PUT /product/goodsspu/{spuId}/tags` | 单商品精调（含 SKU 级覆盖） |
| C 端 | `GET /app/goodsspu/page` | 新增可选参数 `memberItem=true`、`clearanceItem=true`、`sort=member_price\|clearance`；不传时行为不变 |
| C 端 | `GET /app/member/prices` | 不改契约；系统活动同步后可返回商品级会员价 |
| C 端 | `GET /app/clearance/activity-goods` | **新增**出清专区选品接口（D4 已确认），按 `is_clearance_item` + 未到期 + 上架状态取数，返回价格与到期时间 |
| 管理端 | `GET /promotion/zone/settings`、`PUT /promotion/zone/settings` | 专区开关、默认折扣率、专区排序、文案（P1） |

> 所有新增接口：Controller 首段保持微服务域（`/product`、`/promotion`），boot 模式由 `/boot` 前缀统一改写；C 端接口需 `tenant-id`，租户由拦截器注入，禁止从入参取租户。

### 5.2 双模式 SQL

- 新增 `aryn-mall-java/db/boot/100member_clearance_zone.sql`
- 新增 `aryn-mall-java/db/cloud/100member_clearance_zone.sql`（内容一致，按微服务拆分归属）
- 脚本要求：可重复执行（`ADD COLUMN IF NOT EXISTS` 不支持时用存储过程/`information_schema` 判存），禁止 DROP/TRUNCATE；为新字段补默认值与索引：

```sql
-- 选品索引（专区列表）
ALTER TABLE goods_spu ADD INDEX idx_member_item (tenant_id, is_member_item, status, del_flag);
ALTER TABLE goods_spu ADD INDEX idx_clearance_item (tenant_id, is_clearance_item, clearance_expire_time, status, del_flag);
```

- 登记 `db/boot/build-full-sql.mjs` 的 `sections` → `node db/boot/build-full-sql.mjs` → `node db/boot/verify-full-sql.mjs`。
- `goods_spu` / `goods_sku` 已在 `aryn-boot/src/main/resources/application.yml` 的 `tenant.tables` 中；**P1 新增 `clearance_rule`、`goods_price_history` 时必须同步登记**（boot yml + cloud Nacos）。

### 5.3 实体与 DTO

- `GoodsSpu` / `GoodsSku` 增加上述字段；`costPrice` 已做 `@JsonInclude(NON_NULL)` 成本脱敏，新字段中的**出清价属于对外价，可下发；`member_discount_rate` / `member_price_fixed` / `member_price_floor` 不下发到 C 端**（避免暴露底价），仅在管理端与促销域内部使用。
- `PromotionActivity` 新增 `TYPE_CLEARANCE = "11"`；`StackActivityTypeEnum` 新增 `CLEARANCE("clearance", true, true)`。
- 新增 `AppZoneGoodsVO`（可选）：统一专区卡片的价格字段，避免 C 端再分两个接口拼装。

---

## 六、实施拆分与工作量

> 估算是「一名熟悉本仓的工程师」的净开发时间，不含等待评审与真机验证。

### P0-A 会员专区（约 8–9 人日）

| # | 任务 | 关键文件 | 估时 |
|---|---|---|---|
| A1 | DDL 双模式脚本 + 实体字段 + 租户配置核对 | `db/boot/100*.sql`、`db/cloud/100*.sql`、`GoodsSpu/GoodsSku` | 1 |
| A2 | 标签读写接口 + 批量接口 + 权限点 | `GoodsSpuController`(admin)、`GoodsSpuServiceImpl` | 1.5 |
| A3 | 系统会员活动同步服务（幂等全量覆盖 + 租户隔离 + 立即同步 + 每日兜底 Job） | `promotion-biz/.../MallZoneActivitySyncService`、`job/MemberZoneSyncJobHandler` | 2 |
| A4 | 商品列表筛选（`memberItem` / 排序）与现有 bargain 条件合并 | `GoodsSpuMapper.xml`、`AppGoodsSpuController` | 1 |
| A5 | 管理端：商品列表批量打标弹窗 + 表单专区字段 + 活动列表隐藏系统活动 | `views/product/goods-spu/index.vue`、`form.vue`、`views/promotion/ship-supply` | 2 |
| A6 | 单测 + 契约测试 + 真实库联调 | 见 §七 | 1 |

### P0-B 低价捡漏（约 7 人日）

| # | 任务 | 关键文件 | 估时 |
|---|---|---|---|
| B1 | 出清字段 + DDL（与 A1 合并同一脚本） | 同上 | 0.5 |
| B2 | 新增活动类型 type=11 + 引擎出清分支（与会员价取低、阻断新人/折扣） | `PromotionEngineServiceImpl`、`PromotionActivity`、`StackActivityTypeEnum` | 2 |
| B3 | 订单侧：`stackPriority` 与 `orderMemberBenefitHandler` 对 CLEARANCE 的取优分支 | `OrderPriceComputeService`、`PromotionPricePlan` | 1.5 |
| B4 | 出清同步服务（到期自动移除 + 倒挂校验）+ Job + 提交时到期重算（D6） | 同 A3、`OrderPriceComputeService` | 1 |
| B5 | 管理端出清配置（列表批量 + 表单 + 到期时间校验） | 同 A5 | 1.5 |
| B6 | 单测 + 结算/确认页金额一致性 E2E | 见 §七 | 0.5 |

### P0-C 手工 type=10 活动迁移（约 2 人日，D3 已确认纳入）

| # | 任务 | 关键文件 | 估时 |
|---|---|---|---|
| C1 | 迁移脚本/Job：读取未结束手工 type=10 → 折算 rate/floor → 写 SKU/SPU 标签 → 输出对照清单 | `db/boot/100*.sql`（仅数据预览，不直接改数据）或 `promotion-biz` 迁移 Handler | 1 |
| C2 | 手工活动只读化：活动列表标记「已迁移」、禁止新配置、发布治理提示 | `views/promotion/ship-supply/index.vue`、发布校验 | 0.5 |
| C3 | 迁移后回归：抽检 10 个 SKU 的确认页/商详价格与旧活动一致（折算误差 ≤0.01） | 真实库 E2E | 0.5 |

迁移执行顺序：**先上会员专区标签能力并灰度 → 跑迁移预览清单 → 运营确认 → 执行迁移 → 手工活动转只读**。迁移不可与专区上线同一发布窗口执行，避免价格双重生效。

### P1 规则化与治理（约 3–4 人日）

- `clearance_rule` 表（时间分层规则），管理端规则编辑页；
- 价格历史 `goods_price_history`（用于「先涨后降」判定与专区可信度）；
- 专区设置页（开关、默认折扣、文案、排序）；
- 手工 type=10 活动下线（只读一个版本周期后逻辑删除，保留历史订单快照引用）。

### P2 统计与扩展（独立立项）

- 专区让利金额、转化、动销报表；
- 真实临期批次（需要 `goods_batch` 表、入库批次、先进先出与订单批次快照）；
- 免费菜/赠品菜（需要 0 元订单行、库存与成本口径）。

---

## 七、验证方案

### 7.1 后端

```bash
cd aryn-mall-java
mvn clean test -pl aryn-boot -am
```

必须覆盖：

1. 同步服务：重复执行不产生重复 SKU、不新增第二条系统活动；租户 A 的打标不影响租户 B。
2. 引擎：会员价与出清价同 SKU 取低；出清价高于售价时不生效；新人价被出清/会员阻断；券门槛基数只算可叠 SKU。
3. 订单：`resolveMemberPriceTakeBetter` 对 CLEARANCE 生效（高等级会员不高于出清价）；退款金额按取优后单价计算。
4. 展示：`/app/member/prices` 对系统活动返回 `eligible`/`userDiscountRate`；未登录不下发资格。

### 7.2 管理端

```bash
cd aryn-mall-ui
pnpm check:type && pnpm test:unit && pnpm lint
```

- `list-page-contract.test.ts` 会校验列表页交互契约，批量打标弹窗需按既有契约接入；
- boot/cloud 双模式各验证一次批量接口路径（`rewriteBootUrl` 输出）。

### 7.3 C 端

```bash
cd aryn-mall-uniapp
pnpm type-check && pnpm test:unit
```

- `retail-query.ts` 新增筛选参数的单测（`normalizeBargainRate` 同类函数）；
- 专区卡片必须走 `member-price.ts` 的取优口径，禁止页面内自行乘折扣率；
- 小程序主包体积已临界（约 37KB 余量），专区页复用现有 `diy-goods-tabs`，不新增重组件。

### 7.4 真实验收（不可只用单测）

- 真实库造 3 个商品：普通、会员、出清；三个账号：非会员、银卡、钻石；
- 校验「商详展示价 = 确认页 = 提交订单 = 订单详情 = 退款可退金额」五处一致；
- 出清到期后：专区列表不再展示、购物车结算回落原价并提示；
- 会员标签取消后：下一次请求即不再享受会员价（同步延迟需在页面提示范围内）。

---

## 八、风险、兼容与回滚

| 风险 | 说明 | 对策 |
|---|---|---|
| 双重折扣 | 等级折扣率已全局生效，商品级会员率若「叠加」会双重让利 | 严格取优（§4.2），单测锁定 |
| 系统活动与手工活动冲突 | 迁移窗口期同一 SKU 在两条 type=10 中配置不同价 | 系统活动优先级最低；先灰度标签能力再执行迁移，迁移后手工活动转只读 |
| 迁移折算偏差 | `unitPrice / salesPrice` 四舍五入导致按率算价与旧价不一致 | `member_price_fixed` 保留旧单价参与取低，实际成交不高于旧价；预览清单逐 SKU 对照 |
| 迁移后价格联动 | 售价下调后按率算价更低、会员价随动 | 属预期行为（§4.4 第 3 条）；需要长期固定价时保留 `member_price_fixed` 不清空 |
| 影子活动同步失败 | 打标后价格未生效，C 端展示与实际成交不一致 | 保存后同步调用 + 每日兜底 Job + 同步状态字段/日志告警；页面价一律以服务端返回为准 |
| 价格倒挂 | 先涨后降制造假折扣 | 算价时校验「成交价 < 当前售价」，否则不生效 |
| 在途锁错配 | 重建系统活动导致 activityId 变化 | 系统活动按租户唯一且只更新 `rules`，禁止删后重建 |
| 出清到期 | 到期瞬间价格回落引发客诉 | 确认页展示「出清价有效期」；下单侧以提交时快照为准 |
| 多租户 | 新表/新活动漏租户过滤 | 标签同步、Job、查询全部显式 `tenant_id`；新增表登记 tenant.tables |
| 回滚 | 上线后价格异常 | 专区开关（`zone.enabled=false`）关闭同步并清空系统活动 rules；出清类型可通过 `StackActivityTypeEnum` 与引擎分支开关回退 |

**兼容策略**：

- `/app/goodsspu/page` 不传新参数时 SQL 条件与排序完全不变；
- 会员专区如果未打任何标签，`/app/member/prices` 继续按手工 type=10 活动返回（旧数据不失效）；
- 出清活动 type=11 只有在管理端打标后才可能产生，未启用时引擎查询列表不含该类型，零影响。

---

## 九、明确不做 / 需另行立项

1. **真实临期自动入池**：当前无批次、无保质期字段，做「按剩余保质期打折」必须先建 `goods_batch` 与入库流程，属于独立项目（参考 `docs/50-需求文档/2026-10-21-生鲜商城库存必要性调研`）。
2. **免费菜/赠品菜**：需要 0 元订单行、赠品库存与成本分摊，现有买赠（`TYPE_GIFT`）不等价，不能复用。
3. **会员券包/运费券**：`member_benefit` 只到「专属券」类型，发券流程需另做。
4. **让利统计报表**：已做轻量版（见 §12.4）；转化/动销与分渠道拆解待后续。

---

## 十、已确认决策（2026-11-02）

| # | 决策 | 结论 | 落到计划的位置 |
|---|---|---|---|
| D1 | 商品会员折扣率与等级折扣率 | **取优**：`min(商品折扣率, 等级折扣率)`，高等级会员不吃亏 | §4.2 第 1 条 |
| D2 | 出清价与会员价 | **取低**；出清不参与二次等级折扣，但保留「原价 × 等级折扣率」取优 | §4.2 第 3/4 条、B3 |
| D3 | 现有手工 type=10 活动 | **迁移**为商品标签；迁移后旧活动转只读，一个版本周期后下线 | §4.4、§6 P0-C |
| D4 | 出清专区接口 | **新增** `/app/clearance/activity-goods`，不复用会员专区接口 | §5.1 |
| D5 | 免费菜 | **不纳入**本期，需先有赠品/0 元订单模型 | §九 |
| D6 | 到期出清下单 | **不允许**保留原价下单；提交时重新校验并按新价提示 | §8 风险表、B4 |

---

## 十一、建议实施顺序

1. D1–D6 已于 2026-11-02 确认，本文件转入实施；
2. P0-A（会员专区）与 P0-B（出清）并行开发，共用 DDL 与同步服务骨架；
3. 先在 boot 单体跑通全链路（真实库 + 真账号），再验证 cloud 拆分模式；
4. 上线灰度：先开会员专区（不影响未打标商品）→ 跑 P0-C 迁移预览并由运营确认 → 执行迁移并转只读 → 再开出清专区；
5. 完成后在 `docs/90-记录归档/需求记录.md` 登记结果与剩余风险。

总量估算：P0-A 8–9 + P0-B 7 + P0-C 2 ≈ **17–18 人日**；P1 另计 3–4 人日。

---

## 十二、实施落地（2026-11-03）

### 12.1 已落地范围（P0-A / P0-B / P0-C）

| 模块 | 落地内容 |
|---|---|
| 数据模型 | `goods_spu` / `goods_sku` 新增会员标签、会员折扣率/固定价/下限、出清标签/固定价/折扣率/截止时间；`promotion_activity` 新增 `activity_code`；双模式增量脚本 `db/boot/183member_clearance_zone_incremental.sql`、`db/cloud/185member_clearance_zone_incremental.sql` |
| 商品标签 | 管理端 `POST /product/goodsspu/tags/batch`（批量）、`GET /product/goodsspu/tags/{spuId}`、`PUT /product/goodsspu/{spuId}/tags`（SKU 覆盖）；管理端列表批量打标弹窗、商品表单「专区标签」页签 |
| 系统活动 | `MallZoneActivitySyncServiceImpl`：按租户全量重算 `SYS_MEMBER_ZONE`（type=10，最低等级门槛）与 `SYS_CLEARANCE_ZONE`（type=11）；活动 id 稳定、幂等更新；打标后同步触发 + 每日兜底 Job `mallZoneSyncJobHandler`（boot/cloud 均已注册） |
| 引擎 | `PromotionActivity.TYPE_CLEARANCE=11`、`StackActivityTypeEnum.CLEARANCE`；会员价/出清价同 SKU 取低、与阶梯价取低、阻断新人价；`expireTime` 在计算时二次校验 |
| 订单 | 出清价进入叠加策略计划（优先级 2），出清命中不二次打折但保留「原价 × 等级折扣率」取优；限时折扣不再覆盖出清成交价 |
| 专区选品 | `/app/goodsspu/page` 新增 `memberItem` / `clearanceItem` / `sort=member_price|clearance`；新增 `GET /app/clearance/activity-goods`；会员专区活动数据源对系统活动/空活动回落为商品标签选品 |
| 迁移 | `GET /promotion/zone/migration/preview`、`POST /promotion/zone/migration/execute`：旧 type=10 固定单价折算 rate + fixed（下限保持默认），倒挂项标记「需核对」且不迁移，迁移后暂停手工活动并输出逐 SKU 对照清单；管理端活动页提供「会员价迁移」入口与「已迁移」标记。**（2026-11-03 已随会员价活动功能整体删除：接口/Job/入口移除，旧数据直接物理清理，见 §12.6）** |
| C 端 | 装修多 Tab 新增 `member_price` / `clearance` 排序；出清专区按「SPU 规则 + SKU 覆盖」算有效价后过滤/排序，卡片与商详/购物车共用 `/app/clearance/prices` 口径；后端出参继续脱敏会员折扣率/固定价/下限 |

### 12.2 验证记录

- 后端：`mvn test -pl aryn-promotion/aryn-promotion-biz,aryn-product/aryn-product-biz,aryn-order/aryn-order-biz -am` 通过；新增 `MallZoneActivitySyncTest` 3 条、`PromotionEngineTest` 6 条出清/取低/到期/系统优先级用例。
- 管理端：`pnpm check:type` 通过；`pnpm test:unit` 1275 通过 / 3 失败，失败均为存量 `preview-parity` 双端文案差异（公告/导航滚动，与本次无关）。
- 移动端：`pnpm type-check`、`pnpm test:unit` 1538 用例全绿。
- boot 真实库：增量 SQL 可重复执行；批量打标后系统活动即时生成；C 端会员标签/出清标签筛选、会员价接口、出清专区接口、系统活动标签选品、引擎「会员 8.91 vs 出清 8.80 取出清」、出清到期回落会员价、迁移预览 12 条均实测通过（测试数据已清理）。
- 全量 SQL：`node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs` 通过。
- P1 真实库：48h/24h 分层规则命中后出清价 9.00 → 5.40，C 端专区/查价/引擎试算三处一致；专区关闭后系统活动暂停且 C 端隐藏，重新开启恢复；专区标题/公告下发（该三字段已于 2026-11-04 删除，历史记录保留）；价格历史接口返回 tag_save 记录；「先涨后降」提示实测触发；测试数据已清理。
- P1 单测：`ClearanceRuleServiceTest`、`MallZoneActivitySyncTest`（默认折扣/分层/开关关闭）、`MallZoneLegacyCleanupServiceTest`、`GoodsZoneTagServiceImplTest` 全绿；boot 全量测试通过。

### 12.3 P1 规则化与治理（2026-11-03 已实施）

| 模块 | 落地内容 |
|---|---|
| 出清时间分层 | `clearance_rule` 表（距到期 ≤N 小时再打折率）；同步任务按「命中规则取最深一档」把最终价写入系统出清活动，不连乘；管理端「出清折扣规则」页可增删改停用，保存后立即重算 |
| 价格历史 | `goods_price_history` 表：商品编辑的 SKU/SPU 售价与原价、专区规则精调、迁移折算均落历史；`GET /product/goodsspu/{spuId}/price-history` 可查；出清打标时若近 30 天售价上调过，逐条结果返回「先涨后降」提示 |
| 专区设置 | `mall_zone_setting` 表 + 管理端「专区设置」页：会员/出清开关、默认折扣率、排序、标题/副标题/公告（`title/subtitle/notice` 已于 2026-11-04 收敛删除，见下方注）；关闭开关时同步服务清空系统活动 rules 并暂停活动，C 端专区与最终价接口同步隐藏；默认折扣率用于商品未配专属规则时的兜底 |
| 旧活动下线 | 迁移后暂停的手工 type=10 活动保留 30 天，`mallZoneLegacyCleanupJobHandler` 每日逻辑删除到期记录，历史订单快照不受影响 |
| 展示一致性 | 商品域新增远程最终价查询 `RemoteMallZonePriceService`，`/app/clearance/activity-goods` 与 `/app/clearance/prices` 直接使用系统活动分层后的最终价，避免「页面基础价、结算分层价」漂移；会员/出清专区接口下发设置中的标题/公告/排序（文案字段已于 2026-11-04 删除，仅保留排序） |

> **2026-11-04 收敛**：专区设置的 `title/subtitle/notice` 字段已删除——会员侧下发接口已随会员价活动功能删除，出清侧 `/app/clearance/activity-goods` 小程序从未调用，C 端没有任何消费方。`mall_zone_setting` 三列保留未使用（零 DDL、不写增量 SQL）；`MallZoneSettingSnapshotVO` 与 `AppClearanceGoodsVO` 同步删除三个字段。专区展示文案统一由装修 Tab 标题与会员权益条承担；装修器同时把「排序」拆为「排序 + 专区选品」并移除旧「折扣力度优先（低价捡漏）」，历史装修文档仍兼容并给一键迁移。详见 [2026-11-04 专区配置收敛与装修器易用性优化](../2026-11-04-专区配置收敛与装修器易用性优化/README.md)。

P1 新增双模式脚本：`db/boot/185p1_zone_governance_incremental.sql`、`186p1_zone_governance_menu.sql`、`187mall_zone_legacy_cleanup_job.sql`；Cloud 对应 `187` / `188` / `189`（Nacos 租户表）/ `190`（Job）。**（2026-11-03：187/190 已随会员价活动功能删除；会员价调整相关脚本见 §12.6）**

### 12.4 后续（P2）

- **已做（2026-11-03，轻量版）**：专区让利报表。`order_item` 增加 `zone_source/activity_base_price` 快照，`GET /order/statistics/zone` 按专区聚合，经营分析页新增「专区让利」卡片；让利 = 活动前价差 + 会员折扣，不含券/积分。
- **未做**：转化/动销报表、真实临期批次、免费菜/赠品菜（仍按 §九 独立立项）。

### 12.5 跨页面价格一致性（2026-11-03）

用户反馈：会员专区/低价捡漏展示的会员价、捡漏价，同一商品出现在其它页面（首页其它 Tab、分类、搜索、收藏、足迹、推荐位、商家详、购物车、新人楼层）时价格不一致。本次把「专区价」从两个专区页的局部逻辑下沉为**全 C 端商品卡片统一展示口径**。

**后端（商品域）**

- `GET /app/clearance/prices` 扩展 `spuIds` 参数（与既有 `skuIds` 并存、`skuIds` 优先；响应 item 增加可选 `spuId`，旧契约零变化）。按 SPU 返回每个有效 SKU 的出清价：标签/到期/倒挂校验 + 系统活动最终价（含时间分层），前端再按卡片价聚合最低有效价，口径与低价捡漏专区 `clearancePage` 一致。列表卡**禁止**直接使用普通列表的 `clearancePrice` 配置原值。
- 不新增接口路径/白名单/SQL，boot 与 cloud 双模式编译均通过（`mvn -Pcloud compile`）。

**前端共享层（新增）**

- `utils/zone-price.ts`：列表卡片专区价纯函数——出清价与会员价取低、同价会员优先；未达标/未登录保留「会员专享 ¥x」引导；划线口径与商详一致。
- `utils/clearance-price.ts`：新增 SPU 聚合（只统计 SKU 售价 ≤ 卡片价的最低有效出清价）与 SKU 级价表。
- `composables/useZonePrices.ts`：模块级批量取价（`/app/member/prices?spuIds=` + `/app/clearance/prices?spuIds=`），60s 缓存、并发合并、失败静默、登录态切换整体失效；列表先渲染、价格晚一帧补上（revision 驱动 computed 重算）。
- `api/product/spu.ts`：`getClearanceSpuPrices(spuIds)`。

**接入面**

| 面 | 改动 |
|---|---|
| 装修楼层 | `goods-tabs`（全部 Tab，会员专区保留数据层活动补价口径）、`goods-group`、`goods-ranking`、`goods-recommend`、`goods-waterfall`、`goods-scroll`、`goods`、`newbie-floor`（身份价优先，修复「新人价 ¥9.90、结算 ¥11.90」） |
| 通用列表 | `goods-list-panel`（分类/商品列表/搜索/共享选货）、`waterfall-goods`（购物车/商详/订单详情/支付结果推荐位）、收藏、足迹 |
| 弹层 | `goods-detail-sheet`（分类页快捷商详）、`quick-cart-button` 规格弹层、`useQuickCart` 确认弹层（卡片价 = 弹层小计） |
| 交易页 | 购物车：出清价角标/划线优先级修正、补「捡漏价」词条与「xx 前有效」到期提示；商详/购物车既有出清价接入统一到同一口径 |

**验收与验证（2026-11-03）**

- 移动端：`pnpm type-check` 通过；`pnpm test:unit` 147 文件 / 1630 用例全绿；`pnpm build:mp-weixin` 通过。**注**：`pnpm check:mp-package` 当前失败（主包 2084.2KB > 2048KB 硬上限），本次新增主包模块约 20–30KB，叠加前序未提交改造，发布前须先按 `docs/30-端分析/移动端分析.md` 的瘦身候选处理（未修改门禁阈值）。
- 后端：`aryn-product-biz` 161 例通过；`mvn test -pl aryn-boot -am` BUILD SUCCESS；全量 boot 已重新打包部署，`/boot/app/clearance/prices?spuIds=` 实测返回 `{skuId,spuId,salesPrice,clearancePrice,clearanceExpireTime}`，`skuIds` 旧响应无 `spuId` 字段。
- H5 真机尺寸实测（Playwright，截图 `outputs/pricing-consistency/final-*.png`）：
  - 游客：热门 Tab 山竹「会员专享 ¥35.91」、湿巾「捡漏价 ¥7.90/¥9.90」；低价捡漏 Tab 皮蛋「捡漏价 ¥5.90/¥7.90」、奶黄包「¥11.90/¥15.80」；会员专区 Tab 引导价与改造前一致。
  - 金卡会员：热门 Tab/分类列表/商详 山竹「会员价 ¥35.91 日常价 ¥39.90」；新人楼层黄豆芽由新人价 ¥1.00 切换为「会员价 ¥3.15」；购物车三件商品分别显示「会员价 ¥35.91」「捡漏价 ¥5.90/¥7.90 2026-11-06 前有效」「捡漏价 ¥7.90/¥9.90 2026-11-17 前有效」。
- 审计结论：确认页/订单详情/退款金额取服务端结算与快照（`expectedPaymentPrice` 防串改、退款取 `order_item.payment_price`），**金额无需改动**；商详/购物车/列表/弹层与结算同源。

**遗留（P2，下期）**

- 确认页/订单详情的「会员价/捡漏价」标签与出清有效期展示（金额已一致，仅缺文案识别）。
- 商详本体多规格弹层（`vk-data-goods-sku-popup` 直传原始 SPU）——当前专区标签商品均为单规格未触发；共享购物车导入参考价、限时折扣页身份价取优、秒杀场次与会员价并存的列表价口径需产品确认。
- 出清到期为静态展示：页面停留跨过到期时刻需刷新/重进才回落，结算侧有到期重算与金额校验兜底，无金额风险。
- 购物车会员价划线沿用商品原价（¥51.87），列表/商详用当前售价（¥39.90）：成交价一致，划线基准差异属既有设计，未在本期统一。

### 12.6 会员价活动功能整体删除（2026-11-03，P0-C 收口）

**背景**：商品级专区标签已能完整表达会员价（折扣率/固定价/下限 + 专区设置默认折扣 + 成本兜底），手工 type=10 活动与标签重复，运营需要维护两套入口。用户确认（开发阶段，数据均为测试数据）：**彻底删除「发布会员价活动」功能，旧活动不迁移、直接删除**；会员价只通过商品打标维护。

**落地**
- **数据清理**：新增 `db/boot/194member_price_activity_purge_incremental.sql`、`db/cloud/199member_price_activity_purge_incremental.sql`（幂等）：物理删除全部手工会员价活动（type=10 且非 `SYS_*`，系统托管活动保留）、回滚历史迁移写入的 SKU/SPU 标签与价格历史（`change_source='migration'`）、注销已下线的迁移清理 Job（boot `aryn_boot_job` / cloud `aryn_job`）；boot 全量 SQL 已登记并重建校验，双11 演示种子（boot `180`/cloud `182`）不再创建会员价活动。
- **后端删除**：删除 `/app/member/activity-goods` 控制器/服务/VO 与白名单；删除会员价迁移工具（`MallZoneMigrationController/Service`、`MallZoneMigrationItemVO`、`GoodsZoneMigrationItemDTO`、`RemoteGoodsZoneTagService#applyMemberMigration`）；删除迁移后清理 Job/服务（`MallZoneLegacyCleanupJobHandler/Service`）。`ShipActivityController`：活动列表排除 type=10；按 ID 的详情/修改/发布/暂停/删除统一拦截；新建 type=10 直接拒绝并引导商品专区标签。装修发布校验删除 goods-tabs `activity` 数据源模式（保留「链接到活动页」通用链接）；活动发布治理删除会员价规则校验分支。
- **管理端删除**：船供活动页移除「会员价」类型、等级门槛/逐 SKU 会员价表单、规则摘要、试算与会员价特殊分支；移除「会员价迁移」按钮/弹窗/API；装修器多 Tab 商品流删除「按会员价活动自动」数据源与活动下拉，数据源只剩「按分类/排序自动 / 手选商品」，会员专区推荐「按分类/排序自动 + 会员商品专区（按标签）」（2026-11-04 起该排序项拆为「专区选品」，见 §12.3 注）。
- **移动端删除**：删除 `getMemberActivityGoods`/`AppMemberActivityGoodsVO`、`loadGoodsTabPage` 的 activity 分支、商品多 Tab 类型中的 activity 模式/activityId 透传；历史脏配置归一化回落 automatic。
- **装修数据**：首页会员专区数据源此前已切为 `automatic + sort=member_price`，发布版本 v72（v71 及更早版本仍含 activity 配置，仅作历史快照，回滚/发布时按自动 Tab 处理）。

**验证**
- 后端：全 reactor `mvn -pl aryn-boot -am -DskipTests compile` 通过；目标测试 **162/162** 全绿（ShipActivityControllerTest 18、DefaultPageDesignDocumentValidatorTest 45、MemberPriceActivityServiceTest 30、PromotionEngineTest 62、MallZoneActivitySyncTest 7）；boot 镜像重建并重启，启动无错误。
- 真实库：12 个迁移 SKU 标签已回滚、24 条 migration 价格历史删除、2 条手工活动物理删除、迁移清理 Job 注销；系统活动重算为 **18 个标签 SKU**（原有 8 个 + 用户手动打标 10 个）；`/app/member/activity-goods` 已不可访问（401）、`/app/member/prices` 与 `/app/goodsspu/page?memberItem=true&sort=member_price`（total=18）正常。
- 管理端：`pnpm check:type` 通过；`vitest run apps/web-ele/src/views/promotion` **51 文件 / 430 用例全绿**；改动文件 eslint/prettier 通过；Vite HMR 已下发删除后的模块（数据源只剩两项）。
- 移动端：`pnpm type-check` 通过；`pnpm test:unit` **145 文件 / 1610 用例全绿**。
- 全量 SQL：`node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs` 通过（28721 行 / 3250454 字节）。

**边界/遗留**
- 会员价活动的「限时/时间窗」能力随功能删除：会员价由商品标签长期生效，若后续需要限时会员价需另立需求。
- 价格引擎/会员价查价仍读 `promotion_activity type=10`，但只可能是系统托管活动 `SYS_MEMBER_ZONE`（由商品标签同步）；引擎内历史「手工活动优先」注释已收敛，逻辑保留（当前无手工活动，不生效）。
- 旧装修版本历史（v71 及更早）仍含 activity 配置，仅历史快照；新保存/发布不再提供该数据源。
- cloud 侧历史脚本 `db/cloud/165member_activity_goods_gateway_whitelist_nacos_config.sql`/`187` 仍会写入已删除接口的网关白名单（死配置、无安全影响），下次 cloud 环境升级时可顺手清理。
- 运营心智从「活动」改为「商品打标」，操作手册/培训已同步。
- **2026-11-04 收敛**：会员价活动功能删除后，专区设置的 `title/subtitle/notice` 已无任何 C 端消费方（会员侧接口已删、出清侧 `/app/clearance/activity-goods` 小程序未调用），三字段已从接口/VO 删除、数据库列保留未使用（零 DDL）；展示文案统一由装修 Tab 标题与会员权益条承担。详见 §12.3 注与 [2026-11-04 专区配置收敛与装修器易用性优化](../2026-11-04-专区配置收敛与装修器易用性优化/README.md)。

### 12.7 商品列表「专区」查询条件（2026-11-03，运营侧增量）

打标能力上线后，商品列表（商品管理 → 全部商品）缺少按专区复核的入口。本次新增查询条件 `zoneType`：会员专区（`member`）/ 低价捡漏（`clearance`）/ 未加入专区（`none`），空=全部；口径与列表「专区」列一致（只看商品级标签、不看到期时间）；空值不追加 SQL 条件，存量查询逐字不变。无接口路径/权限点/DDL/配置新增，boot 与 cloud 共用；boot 真实库实测 635 件中 member 18 / clearance 5 / none 612。详见 [商品列表增加「专区」查询条件](../2026-11-03-商品列表专区筛选/README.md)，运营操作步骤已补入本需求 [操作手册](操作手册.md) §3.1「按专区筛选商品」。

## 十三、参考

- [低价捡漏与会员专区（已实施）](../2026-10-25-低价捡漏与会员专区/README.md)
- [船供活动治理](../2026-10-13-船供活动治理/README.md)
- [营销优惠叠加规则调研](../2026-10-23-营销优惠叠加规则调研/README.md)
- [生鲜商城库存必要性调研](../2026-10-21-生鲜商城库存必要性调研/README.md)
