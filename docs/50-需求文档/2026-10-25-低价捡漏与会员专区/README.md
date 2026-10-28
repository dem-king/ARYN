# 低价捡漏与会员专区：多Tab商品流运营方案与开发需求

> 日期：2026-10-25 ｜ 状态：**已实施 F1/F2/F3**（2026-10-25 落地，实现与验证记录见 `docs/90-记录归档/需求记录.md`；P0/F4 为纯运营项未执行；落地偏差见 §14）
> 范围：product / promotion / user 三域 + 管理后台（装修器）+ C 端小程序；预计**零 DDL**（个别前置检查项见 §10）
> 关联：会员权益体系（158 号，`member_benefit`）、新人专享价（`promotion_activity` type=9）、装修多Tab商品流（`goods-tabs`）

---

## 0. 结论先行

1. **竞品没有任何一家用「价格从低到高」裸排序做低价区**：淘宝天天特卖是招商制活动池（活动价须低于近 15 天最低标价），拼多多限时秒杀是限时限量先到先得，淘宝/京东/抖音的低价专区货盘来源就是清仓/尾货/临期。共性 = **小货盘 + 限时限量 + 频繁换血**。
2. **会员专区的主流形态是「会员价 + 专属权益感知」，不是「仅会员可见」**（只有山姆/Costco 付费会员店模式做会员墙，不适用本系统多租户 B2B 场景）。京东 PLUS = SKU 级专享价 + PLUS 角标 + 等级梯度。
3. 据此分期：**P0 零开发**（手选货盘 + 限时折扣 + 等级专属券，运营配置即可上线）→ **P1 两个小开发**（F1 自动捡漏规则、F2 会员权益透出）→ **P2 会员价活动类型**（F3，贴 type=9 新人价骨架）与 **F4 会员日**（纯运营）。
4. 当前装修器里「低价捡漏=价格从低到高、会员专区=新品优先」是两个最弱配置：前者让 3.5 元豆芽这类常青低价品永久霸屏（捡漏感消失、无周转价值），后者新品与会员利益无关。P0 上线即替换。

---

## 1. 背景与问题定义

装修器多Tab商品流（`goods-tabs`）已支持最多 6 个 Tab、每 Tab `automatic`（分类+排序）/`manual`（手选 SPU ≤30）两种数据源。现网首页已配「猜你喜欢 / 低价捡漏 / 会员专区」三 Tab，但只有排序与分类两种语义，运营回答不了三个问题：

- **P1**：低价捡漏区「什么商品能进、按什么逻辑轮换」——裸价格升序既无货盘门槛也无变化节奏；
- **P2**：会员专区「会员到底多得了什么」——系统已有会员等级与五类权益，但专区没有任何权益透出，沦为普通货架；
- **P3**：低价感缺少「限时/限量」氛围，与竞品的场次、限量、每日换新机制存在心智差距。

本需求同时给出**运营配置方案（P0）**与**补齐能力缺口的开发需求（F1/F2/F3/F4）**，P0 上线即可验证交互与转化，开发按数据表现排期。

---

## 2. 竞品调研结论（2026-10-25）

### 2.1 低价捡漏类频道

| 平台 | 形态 | 关键机制 | 证据强度 |
|------|------|----------|----------|
| 淘宝天天特卖/天天特价 | 招商制活动池 | 活动价须**低于近 15 天最低标价**（低价=相对自身历史）；符合营销活动规则+类目资质才可报名 | 官方规则中心（B） |
| 淘抢购 | 招商制限时活动 | 开店 90 天以上、品牌售卖证明/发票等准入 | 官方规则页（B） |
| 淘宝/京东库存专区、抖音快手直播底价 | 清仓/尾货专区 | 货盘=**高库存、滞销、尾货、临期**；专区明示清仓心智管理预期 | 权威媒体（C） |
| 拼多多限时秒杀 | 限时限量先到先得 | 报名须报活动库存且全程校验不低于申报量、价格须全网最低、缺货按单赔付 | 官方规则转载（B） |
| 前置仓生鲜（叮咚/小象等） | 每日特价+爆款引流 | 高频品类每日换新拉日活；低客单刚需单品（蛋/纸/水）做价格锚 | 媒体+通用打法（C/D） |

提炼的共性打法：**货盘是圈出来的不是排出来的**（清仓/尾货/深折扣单品进池）；**必须有变化与稀缺**（限时场次、限量限购、每周/每日换血）；**少量低客单刚需品压价格感知**；**划线价对比是标配**（捡漏感来自可见价差）。

### 2.2 会员专区

| 平台 | 形态 | 关键机制 | 证据强度 |
|------|------|----------|----------|
| 京东 PLUS 会员专享价 | **SKU 级专享价**，商品带 PLUS 标识、列表可筛 | 针对高复购标品/高客单耐用品设梯度（加量不加价、多件多折）；专享价可与跨店满减叠加；PLUS DAY 每月固定日翻新 | 官方+代运营（B/C） |
| 88VIP | 大牌专享价 + 全域 95 折 | 通用折扣打底，少量专享价锚点商品 | 媒体（C） |
| 山姆/盒马X/Costco | 付费会员店 | 差异化/自有品牌（Member's Mark 700+ SKU、盒马MAX >20%）；会员费筛选人群 | 媒体/研究（C） |

共性：**主流是价格权益而非会员墙**；选品两极 = 高复购刚需（日常花得少）+ 高客单改善型（好东西有专享）；**按等级分档**形成升级动力；**会员日**给专区固定的翻新理由。

### 2.3 对悦航购的映射

- 低价捡漏 = **人工/规则圈定的货盘 + 可见价差 + 限时折扣氛围 + 每周换血**；裸价格升序仅可作低客单类目的兜底排序。
- 会员专区 = **权益显性化（F2）+ 事实会员价（P0 用等级专属券、P2 用会员价活动 F3）**；不做会员墙。
- 本系统特有约束：多租户、船员/岸上企业/业务员均可下单、COD 占比高、货盘含食品与工业安全用品（见首页现状）——选品 SOP 按「船供刚需 + 周转压力」双轴组织（§5.2）。

---

## 3. 现状盘点与差距（代码锚点，2026-10-25 核对）

### 3.1 已具备

| 能力 | 现状 | 锚点 |
|------|------|------|
| 多Tab商品流 | 最多 6 Tab；Tab=标题(≤10字)+`automatic`(分类+排序)/`manual`(手选 SPU ≤30，按配置顺序展示)；每页加载数量、每行 2/3 列、吸顶、展示划线价开关 | 管理端 `page-design/components/extension/extension-settings.vue:258-382`；C端 `components/diy/diy-goods-tabs/index.vue`、`retail-data.ts:84-111` |
| 商品列表接口 | `GET /product/app/goodsspu/page`，支持分类筛选 + `asc/desc` 排序参数；手选走 `GET /product/app/goodsspu/list/{ids}`（免登） | `AppGoodsSpuController.java:46-50`、`GoodsSpuServiceImpl.apiPage:294-306` |
| 排序枚举 | 仅 3 个：`sales`销量 / `create_time`新品 / `sales_price`价格从低到高 | `retail-query.ts:1-26` |
| 划线价 | 卡片在 `originalPrice > salesPrice` 时显示；**存量商品大量未填原价（为 0/空不显示）** | `components/diy/price-display.ts:10-19` |
| 限时折扣 | 打折/减价/固定价，scope 全场/指定商品，活动级+SKU 级限量 | `DiscountActivity.java`、`DiscountGoods.java` |
| 秒杀 | 场次+每人限购+限量；会场页、活动入口条（`diy-activity-strip`）可用 | `pages/promotion/seckill.vue` |
| 会员等级 | `member_level`（累计消费/积分定级，sortOrder 越大越高）；权益五类：1折扣率/2免邮/3专属券/4积分倍率/5抵扣上限；等级变更自动发等级专属券 | `MemberLevel.java`、`MemberLevelServiceImpl.java:150-222`、`MemberBenefitServiceImpl.java:214-227` |
| 券适用范围 | 券模板支持指定商品关联（`coupon_goods`） | `CouponGoodsMapper.java` |
| C端会员信息 | `/mall-user/app/member/current-info`（levelId/levelName/tags，装修条件渲染已在用） | `api/user/member.ts:24` |

### 3.2 缺口（→ 对应开发项）

| 缺口 | 影响 | 开发项 |
|------|------|--------|
| 列表无「折扣率/有划线价」筛选与排序 | 捡漏货盘无法自动圈品 | F1 |
| C端商品流无会员权益透出 | 专区无会员心智 | F2 |
| 无会员价活动类型（全后端无「会员价/仅会员」实现） | 会员让利只能靠全域折扣率或券 | F3 |
| 无临期/保质期字段、无商品标签体系、无独立商品分组表 | 货盘圈选只能人工 | 本期不做，见 §13 |

### 3.3 附带发现（不在本期范围，另行立项）

C 端 diy 组件注册表**未注册秒杀/折扣楼层组件**（`components/diy/registry.ts` 仅见 `newbie-floor`），这类装修 type 会落入 `unknown-component` 空渲染；限时氛围本期统一走「活动入口条 + 会场页」实现，不依赖楼层组件。

---

## 4. 需求总览与分期

| 项 | 内容 | 成本 | 依赖 |
|----|------|------|------|
| **P0** | 四 Tab 配置 + 两个手选货盘 + 限时折扣活动 + 等级专属券 + 划线价补全 | 运营 0.5~1 人日，**0 开发** | 券模板管理端具备指定商品范围配置入口（实现前检查） |
| **F1** | 低价捡漏自动规则（折扣率圈品+排序） | ≈2 人日 | P0 划线价补全（数据质量前提） |
| **F2** | 会员权益条透出（装修可配） | ≈1.5 人日 | 无 |
| **F3** | 会员价活动类型 type=10 | ≈5~5.5 人日 | F2 上线后心智承接更顺 |
| **F4** | 会员日运营节奏 | 纯运营 SOP | F2/F3 任一已上线 |

---

## 5. P0 配置期（零开发）

### 5.1 Tab 结构与装修配置

四 Tab 封顶（上限 6 个，Tab 越多单 Tab 点击越稀释）：

> **2026-10-25 增补（数据源自动化，见 §14）**：多Tab商品流的数据源新增第三种模式 **`activity`（按会员价活动自动）**——「会员专区」Tab 推荐配置为 `{"mode":"activity","activityId":""}`（activityId 留空=自动取当前进行中的会员价活动），专区商品**自动等于会员价活动里配置的商品**、顺序随活动 SKU 配置顺序，活动加减商品即同步，**不再需要手选 targetIds**。手选（`manual`）保留用于「活动商品 + 引流/新品」混合货盘。

| # | Tab | 数据源 | 配置 |
|---|-----|--------|------|
| 1 | 猜你喜欢 | automatic · `sales` | 保持现状（全站销量榜） |
| 2 | 低价捡漏 | **manual 手选 ≤30** | 深折扣品置顶，价格锚垫底 |
| 3 | 会员专区 | **manual 手选 ≤30** | 刚需+改善型两极，见 §5.3 |
| 4 | 新品上架 | automatic · `create_time` | 零成本，采购新品固定出口 |

组件级：每页加载数量 **10**、每行 **2 列**、Tab 吸顶开、显示价格与划线价开；手选 Tab 的「每页加载数量」须 ≥ 手选商品数（手选只展示前 N）。

装修 JSON 样例（手选 targetIds 为占位，按实际 SPU ID 填写）：

```json
{
  "columns": 2, "count": 10, "stickyTabs": true,
  "showPrice": true, "showOriginalPrice": true,
  "tabs": [
    { "id": "recommend", "title": "猜你喜欢",
      "dataSource": { "mode": "automatic", "sort": "sales", "categoryId": "", "targetIds": [] } },
    { "id": "bargain", "title": "低价捡漏",
      "dataSource": { "mode": "manual", "sort": "sales_price", "categoryId": "", "targetIds": ["<spuId-1>", "<spuId-2>"] } },
    { "id": "member", "title": "会员专区",
      "dataSource": { "mode": "manual", "sort": "sales_price", "categoryId": "", "targetIds": ["<spuId-3>", "<spuId-4>"] } },
    { "id": "new", "title": "新品上架",
      "dataSource": { "mode": "automatic", "sort": "create_time", "categoryId": "", "targetIds": [] } }
  ]
}
```

### 5.2 低价捡漏选品 SOP

**四类货盘配比（≤30 个）：**

1. **深折扣品（主力，约 60%）**：`原价 ≥ 售价 × 1.15`（保证卡片显示划线价）且毛利为正——捡漏感来源；
2. **高库存整箱/大规格品（约 20%）**：推动库存周转，清仓区的经营目的；
3. **低客单价格锚（约 10%）**：饮用水、纸品、米面等 1~5 元档刚需，压住「这里真的便宜」的感知；
4. **慢动销/临季品（约 10%）**：人工判断。

**选品硬标准 checklist**：有划线价且价差 ≥15% ｜ 库存 ≥50 ｜ 上架在售 ｜ 毛利 ≥0（成本价运营侧可见）。

**节奏**：每周换血 1/3（竞品共识：捡漏区的价值在「变化」）；手选顺序即展示顺序，引流品置顶。

### 5.3 会员专区配置

- **货盘两极**：高复购刚需（米面粮油、饮用水、纸品——日常花得少）+ 高客单改善型（整箱饮料、零食、锅具——好东西有专享）；
- **事实会员价 = 商品池 + 等级专属券**：为该池商品建**指定商品适用范围的优惠券**，通过会员权益（benefitType=3 专属券）绑定等级自动发放；等级变更自动发券链路已有（`MemberLevelServiceImpl.recalculateLevel`）；
- **文案**：楼层标题或 Tab 名点明「会员专享 · 领券下单」，把券利益前置（F2 上线前先用静态文案，上线后接动态权益条）。

**前置检查项（上线前完成）**：管理端券模板是否已具备「指定商品」适用范围的配置入口；若缺，本期先以全店通用等级券过渡，并在 §10 记录补齐项。

### 5.4 限时氛围

- 为捡漏池商品建**限时折扣活动（scope=指定商品）**，时间窗与换血周期对齐（建议 7 天）；
- 首页楼层面板加**活动入口条**（`diy-activity-strip`）跳折扣/秒杀会场——「捡漏」必须有时限，否则心智立不住。

### 5.5 P0 验收标准

- [ ] 四 Tab 点击/分页正常；手选 Tab 顺序=配置顺序；切回 Tab 回放缓存不重复请求；
- [ ] 捡漏池商品卡片显示划线价（`original > sales`）；
- [ ] 对应等级会员登录可见并可领等级券，券下单仅对池内商品生效；
- [ ] 限时折扣活动时间窗内卡片价/下单价正确，结束后回原价；
- [ ] boot / cloud 双模式发布后 C 端渲染一致。

---

## 6. F1 自动捡漏规则（P1 开发①）

### 6.1 需求

给 `goods-tabs` 的 automatic 数据源新增排序语义 **`bargain`（折扣力度优先）**：圈品 = `原价 ≥ 售价 × bargainRate`，排序 = 折扣率降序（同折扣率按售价升序、SPU id 降序兜底稳定分页）。

### 6.2 接口契约

`GET /product/app/goodsspu/page` 新增可选参数 **`bargainRate`**（BigDecimal，允许区间 (1.00, 9.99]，缺省按 1.15）：

- 携带时追加过滤：`spu.original_price IS NOT NULL AND spu.original_price > 0 AND spu.original_price >= spu.sales_price * #{bargainRate}`；
- 固定排序：`((spu.original_price - spu.sales_price) / spu.original_price) DESC, spu.sales_price ASC, spu.id DESC`；此时忽略 `asc/desc` 参数（防列名注入面，仅绑定值）；
- 不携带时行为完全不变（存量零影响）。

### 6.3 改动清单

| 端 | 文件 | 改动 |
|----|------|------|
| 后端 | `GoodsSpuMapper.xml` `selectApiPage` | 追加 `bargainRate` 条件分支与固定 ORDER BY |
| 后端 | `AppGoodsSpuController.page` / `GoodsSpuServiceImpl.apiPage` | 透传并校验 `bargainRate` |
| 后端 | `DefaultPageDesignDocumentValidator#validateGoodsTabs` | sort 白名单加 `bargain`；校验 `bargainRate` 区间 |
| 管理端 | `extension-settings.vue`、`extension/types.ts` | 排序下拉加「折扣力度优先」；选中时显示阈值输入（默认 1.15）；`GoodsTabDataSource` 加 `bargainRate?: number` |
| C端 | `retail-query.ts` | SORT_MAP 加 `bargain` → 请求参数 `{ bargainRate }`，不发 asc/desc；`retail-types.ts` 同步类型 |

### 6.4 边界与风控

- **数据质量是前提**：存量商品大量未填原价，F1 上线前必须完成 P0 划线价补全，否则自动池漏品严重；
- `bargainRate` 仅作为 SQL 绑定值，不进 ORDER BY 拼接，无注入面；
- 划线价展示口径沿用 `shouldShowOriginalPrice`（`original > sales`），阈值 ≥1.01 保证圈出的商品都能显示划线价；
- goods-tabs 自动 Tab 无 cacheTtl 缓存（仅 waterfall/recommend 有），无缓存一致性问题。

### 6.5 验收标准

- [ ] 造数三商品：A 折扣 30%、B 折扣 20%、C 无原价 → Tab 按 A、B 排列，C 不出现；
- [ ] 卡片显示划线价；翻页去重正常（跨页 `mergePagedItems`）；
- [ ] 管理端可配置阈值并发布成功；后端校验拒绝非法区间；
- [ ] `mvn test -pl aryn-boot -am` 全绿（含新增 apiPage 参数用例）。

---

## 7. F2 会员权益透出（P1 开发②）

### 7.1 需求

goods-tabs 每 Tab 新增**会员权益条**配置（默认关）：开启后 Tab 商品流顶部渲染一条权益横幅，三态展示：

| 用户态 | 文案 | 动作 |
|--------|------|------|
| 会员（有等级） | 「您已是 {levelName} 会员，本区享 {折扣率} 折 / 会员专属权益」 | 无 |
| 登录但无等级 | guest 文案：「入会享会员价与专属券」 | 跳会员中心页 |
| 未登录 | guest 文案 | 跳登录 |

### 7.2 接口

纯消费既有接口，后端零改动：`/mall-user/app/member/current-info`（等级）、`/mall-user/app/member/benefits`（权益 VO，benefitType=1 折扣率）。

### 7.3 改动清单

| 端 | 文件 | 改动 |
|----|------|------|
| 管理端 | `extension-settings.vue`、`extension/types.ts` | Tab 级「会员权益条」开关 + 会员/游客两段文案输入（默认模板可留空走内置文案）；`GoodsTabConfig` 加 `memberBanner?: { enabled, memberCopy?, guestCopy? }` |
| 后端 | `DefaultPageDesignDocumentValidator#validateGoodsTabs` | `memberBanner.enabled=true` 时文案长度校验（≤30 字） |
| C端 | `diy-goods-tabs/index.vue` | 横幅渲染组件（Tab 头下方）；会员态并行请求 current-info + benefits；**接口失败/未配置不渲染，不阻塞商品流**； guests 跳转用现有会员中心路由 |

### 7.4 边界

- 横幅仅做权益**透出**，不做购买拦截；
- C 端直连管理端接口必 403 的教训：只消费 `/app/member/*` 既有 C 端接口，不新增管理端依赖；
- 未登录请求 current-info 走可选登录契约（401 不得清 token，遵守 `optional-login-contract`）。

### 7.5 验收标准

- [ ] 会员/无等级/未登录/接口异常四态渲染正确；
- [ ] 开关关闭的 Tab 完全无横幅；
- [ ] 管理端配置发布→C 端生效，双模式一致；
- [ ] uniapp `type-check` + 相关单测绿。

---

## 8. F3 会员价活动类型 type=10（P2 开发）

### 8.1 数据模型（预计零 DDL）

复用 `promotion_activity`，新增 `TYPE_MEMBER_PRICE = "10"`；规则走受控 JSON（同阶梯价/新人价先例）：

```json
{
  "minLevelId": "<member_level.id>",
  "skus": [ { "skuId": "<sku>", "unitPrice": 9.90 } ]
}
```

- v1 语义：`minLevelId` 对应等级及其以上（按 `member_level.sortOrder` 比较）可享 SKU 会员价；
- 预留 `skus[].levelPrices: [{levelId, unitPrice}]` 等级分档扩展位（本期不实现，解析层容错忽略未知字段）；
- `scopeType=2` 指定 SKU（`scopeValue` 与 `rules.skus` 一致性校验）；无每人限购 → **不需要 PromotionLock 锁定链路**；
- `couponStackable` / `pointsStackable` 默认 1（会员价是常态权益非止血促销），管理端可关；
- `totalBudget` 建议留空（不限），若运营设预算则沿用现有「额度不足按原价成交」语义。

### 8.2 取价链与叠加

`OrderPriceComputeService` 优先级链调整为：**拼团 > 秒杀 > 会员价 > 新人价 > 限时折扣 > 原价**（会员价是身份权益，优先于一次性拉新的新人价与常态折扣；秒杀/拼团属稀缺场景仍压会员价）。

- 会员价商品不再叠加会员折扣率权益：活动价 SKU `memberDiscountPrice=0` 的既有规则天然覆盖（`orderMemberBenefitHandler:881-906`）。
  - **2026-10-25 修订（见 §15.4）**：改为「**取优**」——会员价商品成交价 = `min(会员价, 原售价 × 该等级会员折扣率)`。原因：会员价是统一身份价、会员折扣率是分档身份折扣，纯互斥会让高等级会员在专区反而更贵（反例见 §15）。新人价/秒杀/拼团/限时折扣仍维持「互斥不叠」，取优只针对会员价（type=10），且是两种优惠取一而非叠加让利；C 端展示同步取优（`/app/member/prices` 下发 `userDiscountRate`）。
- 价格倒挂回退：`unitPrice ≥ salesPrice` 的 SKU 不出会员价（同新人价 `applyPromotionPricePlan:162-164` 先例）。

### 8.3 资格判定

- 登录用户经 Dubbo 查 `UserInfo.memberLevelId` → `member_level.sortOrder ≥ minLevelId 的 sortOrder`（**同租户内比较，显式锁定租户上下文，fail-closed**——沿用新人价资格「显式换租户上下文防 fail-open」约定与 boot injvm 租户快照还原前提）；
- 无等级 / 不达标 = 无会员价资格，按原价可购。

### 8.4 C 端价格聚合

- 新增 `GET /promotion/app/member/prices?skuIds=...`（**可选登录**，同 `/app/newbie/prices` 模式：未登录返回空集合不算错误），返回 `{ skuId, memberPrice, minLevelName }`；
- 商品卡/商详：达标 → 主价切会员价 + 角标「会员价」（口径收敛进 `price-display.ts` / `discount-price.ts` 同族）；未达标 → 原价展示 + 引导文案「{minLevelName}专享 ¥x」（透出引导，运营可配是否透出价格）；
- 列表接入点与 `discount/prices`、`newbie/prices` 相同（`retail-normalizers` / 商品卡二次聚合），不进入 `/app/goodsspu/page` 返回体。

### 8.5 管理端

- 营销活动管理页新增「会员价」类型表单：选品（指定 SKU）+ SKU 会员价 + 等级门槛下拉（`member_level` 启用项）+ 时间窗 + 叠加开关 + 备注；
- 发布治理复用 `ActivityPublishGovernanceService`（`skuPrices[{skuId,unitPrice}]` 校验形态已有，新增 type 分支与 minLevelId 存在性校验）；
- 权限：复用现有营销活动权限段，实现时确认是否需要独立按钮权限（若需则按规范补菜单 SQL）。

### 8.6 风控边界

- 券/积分叠加开关默认开，但**券模板可另设排除**；让利失控的兜底 = 活动预算字段 + 叠加开关；
- 资格查询走 Dubbo 的接口定义放 user-api，DTO implement Serializable（strict 模式硬约束）；
- 与秒杀/拼团同 SKU 并发活动时，取价链固定优先级，无需新增互斥表。

### 8.7 验收标准

- [ ] 达标会员：列表/商详/下单三处价格一致为会员价；秒杀进行中 SKU 取秒杀价；
- [ ] 未达标/未登录：原价可购，无会员价角标（或仅引导文案）；
- [ ] 会员价 ≥ 售价的 SKU 回退原价；叠加开关关闭后券不可用于该活动商品；
- [ ] 活动暂停/结束后价格即时回退；`PromotionEngineTest` 补会员价用例（资格/倒挂/优先级/叠加）；
- [ ] 双模式：boot injvm 与 cloud Dubbo 下资格判定与租户隔离正确（含错租户 curl 验证）。

---

## 9. F4 会员日（P2 纯运营）

- 每月固定日（建议与发薪日后对齐，如每月 10 号）：会员专区换血 + 临时调高 `member_benefit` 积分倍率（benefitType=4，已有）+ 定向发券；
- 零开发：运营 SOP + 日历提醒；若要自动化提醒另行立项（可挂现有站内信/OrderStaffNotify 模式）。

---

## 10. 双模式适配与 SQL

- **预计零 DDL**：F1 仅查询分支、F2 纯前端、F3 复用 `promotion_activity`；
- 若实现过程中出现以下任一情况，按数据库规范补增量 SQL（boot `db/boot/` 与 cloud `db/cloud/` 各一份、可重复执行、无 DROP/TRUNCATE，并登记 `build-full-sql.mjs` 重建 `aryn_boot_full.sql`）：
  1. F3 管理端新增独立按钮/菜单权限；
  2. F3 资格判定需要新增跨域冗余字段或索引；
  3. P0 检查发现券模板「指定商品范围」缺管理端配置入口而需要补字段；
- 前端双模式：C 端新增请求路径首段为有效微服务域（`/mall-user/...`、`/mall-promotion/...` 同既有文件），boot 改写由 `parseOpenBoot`/`rewriteBootUrl` 统一处理，禁止硬编码；后端改动均在 biz 内部，boot/cloud 同一份逻辑；
- 验证命令：`mvn clean compile -pl aryn-boot -am`（无 biz 间直接 import）、`pnpm check:type && pnpm test:unit && pnpm lint`（web-ele）、`pnpm type-check`（uniapp）。

---

## 11. 测试与验证要点

| 层 | 用例 |
|----|------|
| 后端 F1 | apiPage `bargainRate` 过滤与固定排序（含 null 原价、原价=售价边界）；validator sort 白名单与区间 |
| 后端 F3 | 规则 JSON 解析容错（未知字段忽略）；资格 sortOrder 比较（同租户）；倒挂回退；优先级链（秒杀>会员价>新人价）；叠加开关；预算耗尽按原价 |
| C端 | `retail-query` SORT_MAP 单测；`diy-goods-tabs` 横幅四态渲染；`price-display` 会员价角标与划线价并存口径 |
| 管理端 | `extension/types.ts` 校验（bargain 阈值、memberBanner 文案长度）；设置面板快照 |
| 回归 | 划线价展示、新人价、秒杀/折扣会场回归不受取价链调整影响（F3 落地时全量 `mvn test -pl aryn-boot -am`，对照基线失败清单判断新失败） |

---

## 12. 工作量与排期建议

| 项 | 后端 | C端 | 管理端 | 测试联调 | 合计 |
|----|------|-----|--------|----------|------|
| P0 运营配置 | — | — | — | — | 0.5~1 人日 |
| F1 | 0.5 | 0.5 | 0.5 | 0.5 | ≈2 人日 |
| F2 | 0 | 1 | 0.5 | 0.5 | ≈1.5 人日（后端含校验微调） |
| F3 | 2~2.5 | 1 | 1 | 1 | ≈5~5.5 人日 |

建议顺序：P0 立即 → F2（心智先行）→ F1（等划线价数据补全）→ F3（视 P0/P1 的专区转化与券核销数据决定投入）→ F4 常态化。

---

## 13. 风险与不做的事

**风险：**

1. **划线价数据质量**是 F1 的硬前提（存量大量未填）——P0 逐品补全；量大时可评估批量导入工具（超出本期范围）；
2. **让利叠加失控**（会员价 × 券 × 积分）——叠加开关 + 预算字段双兜底，上线首月盯毛利；
3. **手选货盘的运营持续成本**（每周换血）——用 Tab 点击率/动销率复盘，数据不好再上 F1 自动化，避免为自动化而自动化；
4. **多租户资格判定** fail-open——沿用人人价资格的显式租户上下文与 fail-closed 约定。

**本期不做：**

- 「仅会员可见/可买」会员墙（B2B 多租户场景伤转化，竞品主流也不做）；
- 临期/保质期字段体系与保质期临期自动圈品；
- 独立商品分组表（若活动池需求增长，作为通用基建另行立项，届时 goods-tabs/goods-group/秒杀等组件可统一引用）；
- 秒杀/折扣楼层组件注册（§3.3 附带发现，另行立项）；
- 会员价等级分档 `levelPrices`（数据结构已预留）。

---

## 14. 落地记录（2026-10-25 实施）

本次落地 **F1 + F2 + F3**（P0/F4 为运营项，未执行），**零 DDL、零菜单 SQL**。详细改动/验证/边界见 `docs/90-记录归档/需求记录.md` 的 2026-10-25 条目；此处只记录与本文原设计的偏差，便于后续评审。

| 项 | 原设计 | 实际落地 | 说明 |
|----|--------|----------|------|
| P0 前置检查（§5.3） | 需确认券模板是否有「指定商品」配置入口 | **已具备**（`views/promotion/coupon-info/form.vue` 的 `useRange === '2'` 商品选择器） | 无需补字段/补菜单，故无 SQL |
| F1 排序语义（§6） | `sort='bargain'` + `bargainRate` | 一致 | 另新增 `GoodsSpuMapperXmlTest` 锁「不携带时 SQL 逐字不变 + count SQL 兼容」 |
| F1 管理端（§6.3） | 只改 `extension-settings.vue` / `extension/types.ts` | 额外改画布预览 `retail-preview/retail-data.ts` | 否则设计器预览取数口径与 C 端不一致 |
| F1 C 端（§6.3） | `retail-query.ts` / `retail-types.ts` | 另修 `diy-goods-tabs` 的 `normalizeTabs` 会剥掉 `bargainRate` 的既有缺陷 | 不修则发布后阈值丢失 |
| F2（§7） | 会员横幅消费 `current-info` + `benefits` | 一致；文案占位符定为 `{levelName}` / `{discount}` | 管理端 placeholder 与 C 端同 token |
| F3 资格（§8.3） | 经 Dubbo 查 `UserInfo.memberLevelId` → `member_level.sortOrder` | 新增 `RemoteMemberLevelService`（user-api + user-biz `@DubboService`），promotion 域 `@DubboReference` 非 final 字段消费 | 显式租户上下文 + fail-closed；未按 `member_level.status` 过滤（与用户域既有口径一致） |
| F3 取价链（§8.2） | 拼团 > 秒杀 > 会员价 > 新人价 > 限时折扣 > 原价 | 一致 | 会员价与新人价同 SKU 冲突时会员价锁定、新人价跳过；限时折扣对 type=9/10 的 SKU 均跳过 |
| F3 会员价叠加（§8.2 修订，见 §15.4） | 会员价商品不叠会员折扣率（`memberDiscountPrice=0`，纯互斥） | **改为「取优」**：会员价商品成交价 = `min(会员价, 原售价 × 该等级会员折扣率)`；新人价/秒杀/拼团/限时折扣仍互斥 | 纯互斥会让高等级会员在专区反而更贵（售价59/会员价9折53.10/钻石8.5折50.15 的反例）。取优是「两种身份优惠取一」而非叠加让利；缺 `originalUnitPrice` 时回落旧行为 |
| F3 展示价口径（新增） | 原设计未定义展示取优 | `/app/member/prices` 增加 `userDiscountRate`，C 端展示价同样取 `min(memberPrice, originalPrice × userDiscountRate)` | 保证「页面价 = 结算价」；`userDiscountRate`：①(0,1]、1=无折扣权益、null=未登录；查询失败/脏值回落 1（展示侧 fail-open，订单侧才是权威）。缺字段时 C 端静默退化为会员价，**建议前后端同版本发布** |
| F2 会员横幅文案（§7.1 修订，见 §15.4） | 「您已是 {levelName} 会员，本区享 {discount} 折」 | **改为确定性文案**：「您已是 {levelName}，本区享会员专享价」，默认不写折数；两态（有无折扣率权益）合并为一句；`{discount}`/`{折扣}` 占位符保留给运营自定义文案 | `{discount}` 来自「会员折扣率权益」（全场身份折扣），与本区「会员价活动」是两套体系，写进默认文案会与实价不符（竞品一律用 PLUS价/X会员价/88VIP 95折 这类确定性标签） |
| F3 C 端接口（§8.4） | `GET /promotion/app/member/prices` | `GET /app/member/prices`（promotion-biz 控制器，cloud 实际路径 `/promotion/app/member/prices`、boot 为 `/boot/app/member/prices`） | 与 `AppNewbieController` 同款：控制器不硬编码微服务前缀 |
| F3 C 端接口未登录口径（§8.4「未登录返回空集合」） | 未登录返回空集合 | 未登录返回活动配置价 + `eligible=null` | 否则无法渲染「{minLevelName}专享 ¥x」引导；与 `/app/newbie/prices` 同口径，未登录不下发任何用户维度数据 |
| F3 列表聚合（§8.4/§8.7） | 列表接入点与 `discount/prices`、`newbie/prices` 相同（`retail-normalizers` / 商品卡二次聚合） | **已实现（2026-10-25 追加）**：`GET /app/member/prices` 增加可选 `spuIds`（`skuIds` 优先），C 端只在 `memberBanner.enabled` 的 Tab 做列表二次聚合 | 与本文假设的差异：列表出参是 SPU 级（`selectApiPage` 用 `resultType` 不带 `goodsSkus`）且新人价/限时折扣**本来也没有**列表聚合，故不能"复用既有接入点"，改为按 SPU 批量补价：后端用活动已配置 SKU + `getBySkuIds` 回填 `spuId`/SKU 现价后按请求 SPU 过滤（**不新增 Dubbo 接口、不改 `/page` 出参**），同 SPU 多规格逐条下发、**后端不挑选**；C 端按"卡片展示价对应的 SKU"（`abs(originalPrice − 卡片价) ≤ 0.005`，同价多规格取最低会员价）精确匹配：达标切主价 + 「会员价」角标 + 划线取卡片价；未达标/未登录给「{等级}专享 ¥X」引导；规格不匹配或倒挂**完全不动**（防「会员价 > 卡片价」误导）。门控复用 `memberBanner.enabled`（横幅承诺"本区享会员价"，卡片必须有会员价，同开同关，不新增第三个开关） |
| F3 会员专区数据源（§5.1/§5.3 补充，2026-10-25 追加） | 「会员专区」= 手选商品（SPU ≤30），运营需把活动里配置的 SKU 对应商品再手动选一遍，活动变货要重选重发 | **新增数据源模式 `activity`（按会员价活动自动）**：装修器选「按会员价活动自动」+ 活动下拉（留空=自动取当前进行中会员价活动）；C 端自动拉活动商品、**顺序随活动 SKU 配置顺序**、活动加减商品自动同步；手选保留用于「活动商品 + 引流/新品」混合货盘 | 新 C 端接口 `GET /app/member/activity-goods`（免登；`activityId` 空=取已发布且时间窗内的活动，有值=指定活动不校验窗口以便提前装修；rules.skus→SKU→`getBySkuIds` 回填 spuId→**按 SPU 去重保序**，上限 SKU100/SPU50，异常降级 null）；cloud 网关登记 `db/cloud/165member_activity_goods_gateway_whitelist_nacos_config.sql`（两条白名单 + 精确路由，禁止 `/boot/app/member/**` 通配）；发布校验：activity 模式不校验 sort/targetIds、计 2 个请求预算、activityId 给值时校验存在且 `activityType=10` |
| F3 双模式（§10） | 预计零 DDL；前端首段用微服务域 | 后端零表结构；新增 `db/cloud/164member_price_gateway_whitelist_nacos_config.sql`（Nacos 网关白名单 + boot 兼容精确路由） | 依据 127/131 先例：免登接口不上网关白名单会 401 → 前端清 token 跳登录。boot 侧由 `application.yml` 承担，故 boot 无 DB 脚本；路由必须精确 `/boot/app/member/prices`，写通配会抢走 user 域 `/boot/app/member/*` |

**未做（沿用 §13 口径）**：会员墙、临期/保质期字段、独立商品分组表、秒杀/折扣楼层组件注册。`levelPrices` 等级分档价按 §15.4 决策**不做**（主流无此形态；等级差异改由会员折扣率/专属券/免邮/积分倍率/会员日表达）。

**取优的边界（2026-10-25 追加实现）**：① 取优只针对「会员价 vs 会员折扣率」，会员价 × 券/积分仍按活动叠加开关（默认开）可叠，与京东/88VIP「会员价可与券叠加」一致；② 取优按明细行独立比较（共享购物车同 SKU 拆行时逐行取优），与既有逐行会员折扣口径一致，理论仅 0.01 级舍入差；③ 取优命中明细的 `salesPrice/totalPrice` 会回原价、优惠体现在会员优惠行，确认页展示为「原价 + 会员优惠」，与商详「会员价」主价在同额下口径不同（实付一致）；④ 宣传口径上会员价仍应低于该门槛及以上等级的最优会员折扣率——取优是兜底，不是允许倒挂定价。

**列表聚合的边界（2026-10-25 追加实现）**：① 会员价必须与后端**同版本发布**——旧接口不识别 `spuIds`，C 端会静默降级为原价展示（不报错、不影响列表）；② 会员价配在比卡片展示规格更贵的规格上时不展示（避免"会员价 > 卡片价"），多规格商品若只把会员价配在贵规格，卡片看不到；③ 列表快捷加购弹层仍按 quick-cart 接口售价，未接会员价（与既有新人价/折扣的列表口子一致，未扩大范围）；④ 会员专区 Tab 每页多 1 个串行请求（先拿列表才知道 spuIds），失败不影响渲染；⑤ 管理端画布预览无登录态，不展示会员价。

---

## 15. 竞品对照与设计依据（2026-10-25 补充调研）

F3 上线后暴露两个口径问题：① 会员价是「统一身份价」，而权益横幅写「本区享 X 折」（X 来自 `member_benefit` 折扣率权益），两套体系混在一句话里必然与实价不符；② 会员价与会员折扣率互斥时，高等级会员在专区反而更贵（例：售价 59，会员价 9 折 53.10，钻石 8.5 折 50.15）。为此补充调研主流平台做法，作为本次收敛设计依据。

### 15.1 平台对照

| 平台 | 会员类型 | 会员专区形态 | 会员价如何表达 | 是否按等级分档定价 | 会员价与券/其它折扣 |
|---|---|---|---|---|---|
| 盒马鲜生 | 付费（X 黄金 258/年、X 钻石 658/年；88VIP 可 88 元购） | 无独立货架：会员日 +「X 会员价」标签 + 免邮/免费菜 | 「X 会员价」标签；会员日 88 折 | **否**。黄金=每周自选 1 天 88 折、钻石=天天 88 折+副卡 → 分档体现在**折扣可用频次** | 会员日 88 折明确**排除「已有会员价的商品」**；2023-2024 曾推「线下专享价」不可与会员折扣叠加 → 客诉 → 2024-04 取消、线上线下同价 |
| 小象超市（美团） | 付费年卡（美团黑钻可免费领 90 天） | 无会员价货架：会员中心=权益聚合 | 未见公开的会员价角标体系 | 否 | 核心权益=**免运费券包**（每月 6 张 3 元）+ 满额免邮；2025-26 多次调整配送费门槛 |
| 京东 PLUS | 付费（单档） | 有 PLUS 会员频道 + PLUS 价商品集合 | 「PLUS 专享价」「PLUS 折上折」标签 | 否 | 专享价**可与品类券叠加**（家电满 5000-800 + PLUS 95 折）；每月固定 PLUS DAY |
| 山姆 | 付费会员店（260 / 680 卓越） | 整店即会员价，无额外专区 | 会员价=标价 | 否。两档差异=2% 回馈金、运费券、极速达、茅台抽签**资格** | — |
| 天猫 88VIP | 付费（淘气值≥1000 为 88 元/年，否则原价） | 大牌专享价 + 95 折 | 「88VIP 95 折」「专享价」 | 否。分档只体现在**开通价格** | 95 折**可与津贴、红包、优惠券叠加** |

### 15.2 三个共性结论

1. **会员价是「身份两档价」，不是「等级多档价」**：没有平台做「同一件商品银卡 19.9 / 金卡 18.9 / 钻石 17.9」；等级差异一律落在**权益**上（券、免邮、回馈金、积分、折扣可用频次/范围、会员日）。
2. **用「确定性标签」表达会员价**（PLUS 价 / X 会员价 / 88VIP 95 折），不在列表横幅上宣称「您享 X 折」再让实价对不上。
3. **固定会员日是最普遍的低成本抓手**（盒马会员日、京东 PLUS DAY），对应本需求 F4。

### 15.3 反例教训

- **盒马「线下专享价」翻车（2023-2024）**：专享价不可与 X 会员折扣叠加，个别商品专享价反而比会员价更便宜 → 大量会员不满 → 2024-04 取消线下专享价、全国线上线下同价。启示：两套价格体系互斥时，必须保证会员专享价**永远不高于其它渠道价**。
- **盒马会员日 88 折的排除规则**：明确「已有会员价的商品除外」——同为「互斥+排除」，但其前提是全场只有一档折扣力度（88 折）；本系统有 5 档会员折扣率，照抄互斥必然出「高等级更贵」的反例，必须补取优兜底。

### 15.4 据此收敛的设计决策（已实现）

| 决策 | 内容 | 理由 |
|---|---|---|
| 不做 `levelPrices` 等级价矩阵 | 会员价保持「一个门槛 + 一个价」 | 主流无此形态；管理端录入成本（SKU × 5 档）不可维护 |
| **结算取优** | 会员价商品成交价 = `min(会员价, 原售价 × 该等级会员折扣率)`；新人价/秒杀/拼团/限时折扣仍互斥不叠 | 保证高等级永不吃亏；与京东/88VIP「会员价可与券叠加」同类思路（取优而非叠加，不击穿） |
| **展示取优** | `/app/member/prices` 增加 `userDiscountRate`；C 端展示价同样取 `min` | 页面价 = 结算价，避免第三次价格口径漂移 |
| **文案确定性** | 会员横幅改为「您已是 {levelName}，本区享会员专享价」，默认不写折数 | 与竞品一致；折数由会员折扣率权益决定，与本区会员价无关 |
| 等级差异化走权益 | 等级专属券（`benefitType=3`，链路已有）/ 免邮 / 积分倍率 / 会员日（F4） | 零开发、与主流一致；替代等级价矩阵 |

### 15.5 证据与来源（沿用附A 强度口径）

- **C+** 盒马 X 会员回归、线下专享价取消、会员日 88 折、黄金/钻石分档：[解放日报·上观新闻原稿（网易转载）](https://www.163.com/dy/article/J0KTI3C4055040N3.html)
- **C** 88VIP 95 折可与津贴/红包/优惠券叠加：[快科技](https://news.mydrivers.com/1/601/601515.htm)
- **C** 山姆普通卡/卓越卡差异（2% 回馈金、运费券、抽签资格）：[东莞本地宝](http://dg.bendibao.com/dglvyou/20241017/ly174448.shtm)
- **C** 盒马会员日「已有会员价商品除外」、京东 PLUS 专享价/折上折与品类券叠加、小象超市会员=免运费券+满额免邮：第三方攻略站/媒体转述（什么值得买、php.cn、钛媒体等），**未直连各平台官方规则页**，正式配置前建议以官方规则复核（本次未使用 D 级证据）。

---

## 16. 会员介绍弹层与引导文案统一（2026-10-25 追加）

> 起因：C 端首页「会员专区」权益条右侧「了解会员」点击后直接整页跳会员中心，用户只是想先了解会员就被打断浏览；商品卡引导文案「新客会员专享 ¥368」把入门等级名拼进营销句，对「登录未入会 / 未登录」用户读起来像「新客才专属」，且等级名是租户可配数据、写死会串租户。

### 16.1 交互调整（会员介绍弹层）

- 「了解会员」（权益条游客态动作）点击 → **底部滑出会员介绍弹层**，不再整页跳会员中心；未登录态的「去登录」动作保持不变（仍去登录页）。
- 弹层内容（自上而下）：身份区（悦航购会员 + 副标题「入会享会员价与专属券」+ 当前状态行）→ 会员专享权益 4 宫格（会员专享价 / 等级专属券 / 积分加速 / 等级特权）→ 会员成长 3 步（登录账号 → 累计消费 → 自动评级）→ 会员专享价说明（与卡片「会员专享 ¥x」标签闭环）→ 主 CTA「查看会员中心」+ 次级「先逛逛」+ 右上角关闭。
- 弹层 CTA 才跳 `/sub-pages/user/member/index`（会员中心承载等级权益、积分、储值等完整信息）。
- 弹层**不新增接口请求**：复用权益条已查到的会员 profile（等级名/会员态）；会员接口失败时按游客态介绍。
- 层级与安全区：`wd-popup` bottom + `z-index: 1100`（压住 H5 原生 tabBar 998、高于加购确认弹层 1020）+ safe-area-inset-bottom。
- 文案约束：不写死等级名 / 等级档数 / 门槛金额 / 折数（`member_level` 是租户可配数据）；不承诺「付费开通」（本系统等级按累计消费自动评定，无开通动作）。

### 16.2 引导文案统一

- `utils/member-price.ts` 的 `memberPriceGuideText` 由「{minLevelName}专享 ¥x」改为「**会员专享 ¥x**」，列表卡片、商品详情、购物车共用同一函数，三端口径同步；`minLevelName` 仍保留在展示对象里供角标/埋点。
- 理由：会员价活动最低门槛档=入会即得，对未达标/未登录用户拼等级名既像把入门权益说成新客专属，也把租户可配等级名当固定文案；高等级门槛对未达标用户只是噪声（会员中心「等级权益」页有完整等级表）。与权益条「本区享会员专享价」统一「会员」口径。

### 16.3 改动清单

| 端 | 文件 | 改动 |
|----|------|------|
| C 端 | `src/components/member-intro-sheet/index.vue` | 新增会员介绍底部弹层（wd-popup bottom / z-index 1100 / 安全区 / 关闭热区 88rpx） |
| C 端 | `src/components/member-intro-sheet/member-intro.ts` | 弹层内容与会员态文案口径纯函数（不写死租户等级数据） |
| C 端 | `src/components/diy/diy-goods-tabs/index.vue` | 「了解会员」改为唤起弹层，弹层 CTA 再跳会员中心 |
| C 端 | `src/utils/member-price.ts` | 引导文案统一「会员专享 ¥x」（列表/商详/购物车同源） |
| 测试 | `src/components/member-intro-sheet/*.test.ts`、`src/utils/member-price.test.ts` 等 | 新增弹层内容/契约测试，更新引导文案断言 |

### 16.4 验证与边界

- `pnpm type-check` exit 0；`pnpm test:unit` 106 files / 1132 tests 全绿（新增弹层内容 11 例 + 契约 10 例）。
- H5 390×844（Playwright mock 装修页）验收：文案迁移、弹层展开/关闭/重开、CTA 跳转、z-index 与底部安全区、无 console error，证据见 `outputs/member-intro-sheet/`。
- **零后端 / 零 DB / 零接口路径改动**，boot/cloud 双模式无差异；弹层不发新请求。
- 当前入口只在权益条游客态（登录未入会）触发；未登录仍去登录页，登录后回到首页再点「了解会员」即可看到弹层。
- 会员态弹层能力已预留（`isMember` / `levelName` props），后续若从会员专区会员态入口打开无需改组件。

## 附A. 竞品来源

- 淘宝帮助中心 · 天天特卖活动价格规则：https://helpcenter.taobao.com
- 淘抢购招商规则：https://www.taobao.com/market/tqg/business_rules.php
- CBNData · 电商试水低价模式，「库存特卖」迎来春天：http://www.cbndata.com/information/276300
- 知乎 · 电商滞销品清库存策略（清仓专区打法）：https://zhuanlan.zhihu.com/p/356820399
- 知乎 · 拼多多平台活动规则（限时秒杀）：https://zhuanlan.zhihu.com/p/356044194
- 一点百科 · 拼多多限时秒杀活动规则：https://www.yidian51.com/baike/275948.html
- transcosmos · 京东PLUS会员专享价的设置与权益透出：https://www.transcosmos-cn.com/tags/1177.html
- 小多AI · 京东PLUS会员专享优惠机制：https://insight.xiaoduoai.com/e-commerce-information/jingdong-information/double-eleven-does-jd-plus-member-have-special-discount-how-much-discount-for-plus-members.html
- 知乎 · 山姆会员店选品逻辑：https://zhuanlan.zhihu.com/p/379258803
- CBNData · 山姆与盒马X会员店对比分析：https://www.cbndata.com/information/292461

> 证据强度口径沿用 10-23/10-24 调研约定：B=官方规则第三方完整转载，C=权威媒体转述，D=经验帖；D 级不作为配置细节依据。

## 附B. 关键代码锚点速查

| 主题 | 锚点 |
|------|------|
| 多Tab组件（管理端） | `aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/components/extension/extension-settings.vue:258-382`、`extension/types.ts:250-327` |
| 多Tab组件（C端） | `aryn-mall-uniapp/src/components/diy/diy-goods-tabs/index.vue`、`retail-query.ts:1-26`、`retail-data.ts:84-111` |
| 装修文档校验 | `aryn-promotion-biz/.../validation/DefaultPageDesignDocumentValidator#validateGoodsTabs:563-616` |
| 商品列表 | `AppGoodsSpuController.java:46-50` → `GoodsSpuServiceImpl.apiPage:294-306` → `GoodsSpuMapper.xml:116-146` |
| 取价链 | `OrderPriceComputeService.java:92`（优先级）、`:140-168`（落价）、`:881-906`（会员折扣不叠活动价） |
| 新人价先例 | `PromotionActivity.TYPE_NEWBIE_PRICE`（rules JSON：`{"registerWithinDays":30,"limitPerUser":1,"skus":[{skuId,unitPrice}]}`）、`AppNewbieController`、`utils/newbie-price.ts` |
| 会员等级与权益 | `MemberLevel.java`、`MemberLevelServiceImpl.recalculateLevel:150-222`、`MemberBenefitServiceImpl:214-227`、`api/user/member.ts:24` |
| 券适用商品 | `CouponGoodsMapper.java`（`coupon_goods`） |
