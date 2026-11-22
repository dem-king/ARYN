# 商详信息层补位方案

**设计提案 · 2026-11-20**

商详页现在从「价格条 → 标题卡 → 一句卖点」直接跳到「用户评价」，中间是空白。这不是缺一张券，而是 B2B 改造中连续撤掉两块占位内容后留下的结构性空档：商品信息之后原本依次是领券卡、船供资料卡、商详装修位，前两块已删，第三块对多数商品是空的。

本方案补的是**决策信息层**，不是营销入口：用一张商品参数卡回答「这是什么」，一条交易与履约保障回答「敢不敢下单」，再把已经存在但没露面的阶梯价、买赠门槛显性化。评价的位置不动。

## 一、现状：为什么会空

### 1.1 两块占位内容先后撤掉

| 原来在这 | 现在 | 证据 |
|---|---|---|
| 领券卡 `CouponCard`（取券、领取、券列表） | 整块下线，连取券接口都禁 | [purchase-contract.test.ts#L25](../../../aryn-mall-uniapp/src/sub-pages/product/goods-detail/purchase-contract.test.ts#L25) 断言不出现 `CouponCard`、不调用 `/app/couponinfo/page` |
| 船供资料卡 `ShipProfileCard`（储存条件 / 采购单位 / 箱规 / MOQ / 步长 / 配送说明） | 2026-09-29 随船供资料退出主链路一并删除，三张表 DROP | [purchase-contract.test.ts#L114](../../../aryn-mall-uniapp/src/sub-pages/product/goods-detail/purchase-contract.test.ts#L114)、[99drop_ship_product_profile_incremental.sql](../../../aryn-mall-java/db/boot/99drop_ship_product_profile_incremental.sql) |

两张卡原来正好落在「价格区之后、评价之前」，是这段页面唯一的两个信息块。

### 1.2 装修位是「有装修才渲染」

[goods-detail/index.vue#L1182](../../../aryn-mall-uniapp/src/sub-pages/product/goods-detail/index.vue#L1182) 在商品信息下方、评价上方留了商详装修位（`usePageDecoration('2')`），但只有运营发布过 Schema 时才渲染。这款银鹭花生牛奶没有配置，整段被跳过——页面第二次空白。

装修依赖运营逐商品维护，漏配就是今天的页面。

### 1.3 优惠信息只在价格区零散出现

- 阶梯价表（`b2b-price-strip`）只在商品命中阶梯活动时才渲染，且只列档位、不告诉用户「再买几件能便宜多少」。
- 买赠与满量/满额优惠按**订单商品基价总额**命中（`promotion_activity` 的 5/7 类），商详完全不展示，用户到确认页才看见赠品预览。
- 会员价、协议价、档期特供、出清价各自独立成条，逐条叠加在价格区下方，信息碎、优先级靠用户自己读。

### 1.4 页面当前顺序

`商品图/好评气泡 → 价格条 → 会员条 → 标题+属性标签+卖点 → 规格/配送两行 →（空）→ 用户评价 → 分类回流 → 商品详情图文 → 你可能还会喜欢`

评价出现在图文详情之前，本身是电商常规顺序（小象、盒马同款），**不需要调换**；缺的是它前面那层。

## 二、目标与非目标

**目标**

- 评价之前补齐四类信息：这是什么（参数）、怎么买（箱规/起订/阶梯）、多久到（靠港送达）、能不能开票（对公与票据）。
- 恢复页面节奏：高饱和价格区之后有两个中性白卡承接，再进入评价，避免「橙色价格条直落评价卡」的跳变。
- 未装修商品也有兜底，不再依赖运营逐款配置。

**非目标**

- 不复活领券中心、秒杀、新人价、拼团、限时折扣等已停运玩法（见「营销活动范围（强制）」）。
- 不改价格主价口径、成交校验、MOQ/步长校验。
- 不动评价排序、推荐算法、装修器既有楼层。
- 不把船供资料卡或 `ship-summary` 接口接回来（数据源已删，接回来只有空壳）。

## 三、新页面结构

```text
商品图（好评率气泡）
¥4.90  省¥1.47       收藏 分享           ← 现有价格条
会员专享 ¥4.41  开通即享                  ← 现有会员条
银鹭花生牛奶450ml                         ← 现有标题卡
[常温保存] [植物蛋白] [0乳糖]              ← 现有属性标签
花生奶香一口入魂，甜而不腻…                ← 现有卖点
规格 / 配送 两行                          ← 现有

商品参数【新增】                          ← 填补空档，≤8 行，空行隐藏
品牌 银鹭 · 类目 饮料 · 规格 450ml
包装 1 箱 = 15 瓶 · 起订 1 箱 · 递增 1 箱
保质期 12 个月 · 储存 常温避光 · 条码 …
[ 查看全部参数 ]

交易与履约保障【新增】
整箱起订 · 靠港送达 · 可开发票 · 破损临期包赔
送至 海星号 · 青岛港 3 泊位 · 12/01 08:00–12:00   ← 有船舶上下文才出

商详装修（有 Schema 才渲染）                ← 现有，位置不变

用户评价（5）                              ← 现有
分类回流 → 商品详情图文 → 你可能还会喜欢       ← 现有
```

三条设计原则：

1. **一屏只说一件事**：参数回答「是什么」，保障回答「敢不敢下单」，优惠回答「怎么更省」。三者不混在同一张卡里。
2. **卡片语言只有一套**：新卡复用 `.detail-section` 的竖条标题 + `24rpx` 圆角 + `16rpx/24rpx` 外边距，不引入第四种卡片样式。
3. **没有内容就不渲染**：整卡隐藏，不出空卡、不出「暂无」占位。旧商品升级后页面与今天完全一致。

## 四、模块设计

### 4.1 商品参数卡（P0，纯前端可上线）

位置：标题卡与收货信息之后、装修位之前。视觉上用中性白卡，不抢价格区的色。

| 行 | 示例 | 数据来源 | 现状 |
|---|---|---|---|
| 品牌 | 银鹭 | `goodsSpu.brandName` | 已有 |
| 类目 | 饮料 › 植物蛋白饮料 | `goodsSpu.categoryName` | 已有 |
| 规格 | 450ml | `sku.specsArr` 拼接（口径同购物车 `joinSpecs`） | 已有 |
| 包装 | 1 箱 = 15 瓶 | `salesUnit` + `baseQtyPerSalesUnit` + `baseUnit` | 已下发，C 端未用 |
| 起订/递增 | 1 箱起订，按 1 箱递增 | `minOrderQty` + `stepQty` | 已有 |
| 保质期 / 净含量 / 配料 / 营养成分 / 产地 / 条码 | 12 个月 / 450ml / … | 新增 `params_json` | 缺 |

**为什么阶段 0 不需要后端**：`GoodsSkuMapper.selectBySpuId` 已经把测量域整套字段查回来（`sales_unit`、`base_unit`、`base_qty_per_sales_unit`、`unit_spec_text`、`min_order_qty`、`step_qty`），[GoodsSkuMapper.xml#L17](../../../aryn-mall-java/aryn-product/aryn-product-biz/src/main/resources/mapper/GoodsSkuMapper.xml#L17)，商详接口 `selectApiSpuById` 也带 SKU 集合。这些字段目前只在采购页露过面。

展示规则：

- 最多 8 行，超出折叠为「查看全部参数」，展开不跳页、不新开弹层。
- 空值整行隐藏；一行都没有则整卡不渲染。
- 多规格商品「规格」行显示「3 种规格可选」，点击唤起现有 SKU 弹层（复用 `handleBuyAction`）。
- 箱规文案走 i18n 模板（`product.params.packageValue`，如「1 {salesUnit} = {count} {baseUnit}」），不在模板里拼中文。
- 数据收敛放在纯函数 `utils/goods-params.ts`，组件只负责渲染；空值、脏值、多规格、箱规缺失全部在单测覆盖。

### 4.2 交易与履约保障（P0）

位置：参数卡之后、装修位之前。四项等宽，图标 + 主文案 + 一行副文案。

| 项 | 主文案 | 副文案（有上下文时动态） |
|---|---|---|
| 起订 | 整箱起订 | 起订 1 箱，按 1 箱递增（取当前 SKU 的 `minOrderQty`/`stepQty`） |
| 送达 | 靠港送达 | 送至 海星号 · 青岛港 3 泊位 · 12/01 08:00–12:00（`shipContextStore`） |
| 票据 | 可开发票 | 支持增值税专票，对公结算按协议（租户默认文案） |
| 售后 | 破损临期包赔 | 收货 48 小时内拍照申请补发或退款 |

**数据形状复用装修器的服务承诺**：`service-promise` 楼层用 `{ id, title, description, iconUrl }` 的 items 数组，[createServicePromiseDefaults](../../../aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/components/extension/types.ts#L232)。商详沿用同一形状，首版在前端常量里给租户默认值，后续接装饰器配置即可，不新造第三套结构。图标沿用 `diy-service-promise` 的关键词映射（票/结算→wallet，退/换→refresh，速/达→time），C 端与画布两端保持一致。

交互：点任一项展开 3 行脚注（送达口径、开票类型、赔付规则），不跳页；有船舶上下文时「送达」一项展示具体船名、港口泊位与时间窗，没有上下文只显示「送达港口或船舶」。

口径红线：商详不出现「包邮」。这条已有明确规定并被执行，[goods-purchase.ts#L153](../../../aryn-mall-uniapp/src/utils/goods-purchase.ts#L153)（订单级满额包邮只在结算页计算）。

### 4.3 优惠利益点（P1）

不新增领取入口，只把已有优惠讲清楚。

- **阶梯价**：现有档位表保留，补一行动态引导——`resolveLadderNextTier(ladderPrices, 当前件数)`：「再买 6 件，单价降到 ¥4.35」；已在最高档则显示「已享最高档」。纯前端可算，`ladderPrices` 已经是接口返回的现成数据。
- **买赠 / 满量满额**：门槛按订单商品基价总额命中，商详**只展示门槛与赠品**（「满 ¥100 赠 花生牛奶 450ml ×1」），不计算「还差多少」。差额归购物车与结算，避免同一规则出现第二套口径。
- **定向券**：商详不出现「领取」，最多一句「下单可用企业券」，命中不到则整行隐藏。券的可用性判定继续由结算页的 `coupon-usability` 统一产出。

### 4.4 装修兜底（P1）

- 顶部「详情」标签通过原有锚点进入图文详情。
- 装修为空时依次展示适用的原生信息卡与评价。有装修时在两者之间渲染装修。
- 装修异步加载期间保持现有骨架屏，不提前渲染兜底，避免内容闪动。

### 4.5 评价区（P2，可选）

- 1 至 2 条评价直接显示实际评价，0 条显示现有空态，3 条起显示好评率和评价标签统计。
- 标签使用用户提交的评价标签聚合。「大家问」另行确认内容来源与维护方式。
- 评价下方的分类回流从「去逛逛」改成「同品类凑单 · 整箱更划算」。

## 五、数据与接口改造

### 5.1 商品参数落库（P0-b）

结构：`goods_spu.params_json`，JSON 数组 `[{ "label": "保质期", "value": "12 个月" }]`，最多 12 项，`label` 唯一。

```sql
ALTER TABLE `goods_spu`
  ADD COLUMN `params_json` JSON NULL
  COMMENT '商品参数（label/value 数组，商详参数卡）' AFTER `attr_tags`;
```

- 两种模式使用 `1014goods_spu_params_incremental.sql`。脚本根据 `information_schema` 判断是否新增列，可以重复执行。Cloud 的商品库初始化基线同步包含参数列。
- 登记 `db/boot/build-full-sql.mjs` 的 `sections`，重跑 `node db/boot/build-full-sql.mjs` 生成 `aryn_boot_full.sql`，再跑 `node db/boot/verify-full-sql.mjs` 校验。
- 后端：`GoodsSpu.params` 字段 + `GoodsParamListTypeHandler`（照 `GoodsAttrTagListTypeHandler` 写法），`GoodsSpuMapper.xml` 的 `goodsSpuSql` 与 `selectApiSpuById` 带出 `params_json`。
- 管理端：`goods-spu/form.vue` 增「商品参数」分组（label/value 行编辑，最多 12、标签去重），校验放 `goods-spu-form-validation.ts`。
- 兼容：`params_json` 为空的存量商品，C 端整块不渲染，与改造前一致。

### 5.2 买赠 / 满量提示接口（P1，可选）

`GET /promotion/app/goods-hints/{spuId}`，返回 `{ gifts: [{ activityId, name, thresholdAmount, giftNames }], volumes: [...] }`。

- 范围匹配复用 `PromotionScenarioEvaluator` 的既有口径（全场 / 指定 SKU / 场景 / 船舶 / 靠港 / 港口），不新造判断。
- 带登录态与船舶上下文时按船过滤；未登录只返回全场与指定 SKU 的活动。
- 只读，不参与结算与库存锁定；接口失败整块隐藏，与现有 `state.* = null` 降级方式一致。

## 六、前端改动清单

| 文件 | 改动 |
|---|---|
| `sub-pages/product/goods-detail/index.vue` | 在 `GoodsComment` 之前渲染参数和履约信息，内容变化后重跑 `updateTabListPositions` |
| `components/GoodsParamCard.vue`（新建） | 参数卡渲染、折叠、点击唤起 SKU 弹层 |
| `components/GoodsAssurance.vue`（新建） | 四项保障、脚注展开、船舶上下文文案 |
| `utils/goods-params.ts`（新建） | 纯函数：SPU + 当前 SKU → 参数行数组（去重、截断、空值过滤） |
| `utils/ladder-price.ts` | 增加「下一档还差多少」的纯函数，复用现有 `ladderTiersOf` |
| `locales/product.zh-CN.ts` / `product.en-US.ts` | 新增词条（见下） |
| `purchase-contract.test.ts` 或新增 `param-card.contract.test.ts` | 守住新结构，不复活旧卡片 |

新增词条（zh / en）：

- `product.params.title` 商品参数 / Specifications
- `product.params.brand|category|spec|package|moq|step` 品牌 / 类目 / 规格 / 包装 / 起订 / 递增
- `product.params.packageValue` 1 {salesUnit} = {count} {baseUnit} / 1 {salesUnit} = {count} {baseUnit}
- `product.params.viewAll` 查看全部参数 / View all specs
- `product.params.multiSpec` {count} 种规格可选 / {count} options available
- `product.assurance.title` 交易与履约保障 / Service & fulfillment
- `product.assurance.minOrder|port|invoice|claim` 整箱起订 / 靠港送达 / 可开发票 / 破损临期包赔
- `product.assurance.portValue` 送至 {vessel} · {port} {berth} · {window} / Delivering to {vessel} · {port} {berth} · {window}
- `product.ladderNextTier` 再买 {count} 件，单价降到 ¥{price} / {count} more for ¥{price} each

## 七、验收标准

- 契约：源码中不出现 `ShipProfileCard`、`ship-summary`、`CouponCard`、`/app/couponinfo/page`；参数卡的行必须由 `goods-params` 产出；无数据整卡不渲染；保障条固定四项。
- 单测：`goods-params` 覆盖空值、脏值、多规格、箱规缺失、超过 8 行截断；`ladder-price` 覆盖未达档、正好达档、已最高档。
- 双模式：本期只有 P1 新增请求，路径首段必须是 `/promotion`，boot 模式由 `rewriteBootUrl` 改写，不在页面里判 `VITE_OPEN_BOOT`。
- 真机（iPhone 15 Pro Max 430×932）：最长值不溢出、长品牌名截断、深色模式可读、英文包不换行错位。
- 回归：`pnpm type-check && pnpm test:unit`；商详现有 6 个测试文件保持通过；`purchase-contract` 的价格优先级断言不得改动。
- 性能：参数卡 ≤8 行、无图片；图文详情仍在原位置懒加载，不因新卡提前请求。

## 八、实施顺序与工作量

| 阶段 | 内容 | 依赖 | 可独立发布 |
|---|---|---|---|
| 0 | 参数卡（现有字段）+ 保障条 + 契约/单测 | 无（纯前端） | 是 |
| 1 | `params_json` 落库 + 后台编辑 + 食品参数 | 阶段 0 | 是 |
| 2 | 阶梯「再买 N 件」引导 + 装修兜底 | 阶段 0 | 是 |
| 3 | 买赠/满量提示接口 + 评价标签与降级 | 阶段 2 | 是 |

阶段 0 约半天，能直接消除当前空白；阶段 1 一天（后端字段 + 后台表单 + 双份 SQL + 全量 SQL 重建）；阶段 2 半天到一天；阶段 3 一到两天，接口口径需先与结算对齐再开发。

## 九、风险与不做

- **不接回船供资料卡**：三张表已 DROP，接口已删，接回来没有数据源。
- **不接回领券/秒杀/新人价**：均已停运。商详里的 `seckill-strip`、`newbie-strip` 属历史代码，可另行排期清理，不纳入本期。
- **参数卡不做第二套富文本**：长图文仍归 `description`，参数只放短字段，避免两处重复且拖慢首屏。
- **订单级门槛不在商详算差额**：满量、买赠只展示门槛与赠品，差额交给购物车与结算，防止与结算金额不一致。
- **参数卡与保障条不带价格**：任何价格只出现在价格区，避免「同页两个价」的客诉。
- **锚点会位移**：插卡后「评价」「详情」「推荐」的测量位置变化，必须确认 `updateTabListPositions` 与 `measureDetailSections` 在参数卡渲染后重跑，否则导航 tab 会跳错位置。

## 十、待确认

1. 保障条四项是否按船供口径写死为默认值，还是首版就接到租户级配置？
2. 食品参数（保质期、配料、营养成分）由商品编辑逐款维护，还是走批量导入模板？

## 十一、落地记录（2026-11-21）

评审设计稿后收敛为「承诺行并入商品信息卡」，不再单独挂保障卡：

- **服务承诺走商品级配置**：`goods_spu.service_tags`（code 数组，白名单 `cold_chain / damage_refund / invoice / authentic`，最多 4 项）。未配置时端上回退「正品保障 / 破损包赔 / 可开发票」，旧商品也有承诺行；后台商品表单新增「服务承诺」勾选组。
- **双模式 SQL**：`db/boot/1016goods_spu_service_tags_incremental.sql`、`db/cloud/1016goods_spu_service_tags_incremental.sql`，已登记 `build-full-sql.mjs` 并重建 `aryn_boot_full.sql`。
- **页面顺序**：商品信息（含承诺行）→ 当前靠港安排（船供有上下文时）→ 订单优惠 → 商品参数 → 装修区 → 用户评价。§五 中的顺序与「独立保障条」以本节为准。
- **底栏不提供立即下单**：商详只保留「加入购物车」，SKU 弹层固定 `mode=2`（只显示购物车），采购统一走购物车确认。
- **文案调整**：原 `product.assurance.*` 词条替换为 `product.promise.*`（冷链直发 / 破损包赔 / 支持开票 / 正品保障）。
- **订单优惠进度（本地估算）**：不做「本单已购」（商详没有订单篮子），改为「按当前 N 箱 ¥X 计」，用当前数量 × 当前 SKU 售价算差额；门槛超过当前金额 20 倍时退化成「满 ¥X 起赠」，不画无意义的短进度条。表头「N 档赠礼 ›」可真正展开，不再给假箭头。
- **全场活动不再铺商详（2026-11-22）**：`GoodsPromotionHintsService` 过滤 `scope_type=1` 的全场活动。商详只展示指定商品、购买场景、靠港范围等与当前上下文真正相关的订单优惠；全场满赠仍在购物车/结算按订单金额计算，但不再让每个商品详情页重复同一张「订单优惠」卡。
- **参数治理**：参数卡首屏 6 行、超过才出「查看全部」；类目层级改用 `›`；后台新增「常用食品参数」一键模板（产地/储存/保质期/包装），并拦截把「签收/发货」写进「保质期」。
- **月售 1 件**：按产品要求保持现状，不改展示门槛。
