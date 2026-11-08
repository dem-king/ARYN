# 商品卡营销标签（新人首单价 / 秒杀中 / 社证统计 / 近14天最低价）

> 2026-11-08 · 需求来源：对标小象超市商品卡「新人首单价 / 近14天最低价 / 秒杀价」与盒马「月N+人已下单 / 月回头客N+人 / X%人回购」。
> 本文档为详细设计（未实施），分三期落地：P1 角标透出 → P2 社证统计 → P3 价格快照。

## 需求口径（对标拆解）

| 标签 | 平台 | 本质 | 个性化 |
|---|---|---|---|
| 新人首单价 | 小象 | 资格定价：命中新人资格才下发新人价+角标，结算侧二次校验+防重锁 | 是（user-scoped） |
| 秒杀价 | 小象 | 场次化限时限量活动价，角标+划线原价 | 否（全局） |
| 近14天最低价 | 小象 | 价格历史快照 + `当前价 ≤ MIN(近N天快照价)` 判定，合规自证（折减基准价须可回溯） | 否（全局） |
| 月N+人已下单 | 盒马 | 近30天支付过去重买家数，T+1 离线聚合 | 否（全局） |
| 月回头客N+人 | 盒马 | 近30天购买≥2次的去重用户数 | 否（全局） |
| X%人回购 | 小象 | 回头客 / 下单人数 | 否（全局） |

共性工程原则（对标结论）：**一切数字预计算/冗余，列表页零逐 SKU 聚合；一切数字有门槛+向下取整，宁可不显示不出难看数字；个性化标签与全局标签分开处理；价格声明必须可回溯举证。**

## 总体方案：独立批量标签接口（不内嵌列表 VO）

C 端商品列表共 4 个接口、两种出参形态（`/page`、`/recommend`、`/list/{ids}` 直出 `GoodsSpu` 实体；`/search`、`/ship/page` 出 `GoodsCatalogSummaryVO`）。标签下发**不走列表接口**，新增一个聚合批量端点（**入参为 spuIds**——装修楼层/推荐流的卡片项只有 SPU 维度，SKU 级解析收敛在服务端）：

```
GET /product/app/goodsspu/card-tags?spuIds=a,b,c（spuIds 上限 200，SPU 级聚合：同 SPU 多 SKU 取最低可享价）
→ { items: [ { spuId, newbiePrice, seckillPrice } ] }
（P2/P3 在 VO 上扩 socialProofText / priceTagText 字段）
```

**理由**：
1. 前端已有异步补价管线（`useZonePrices.ts` 的 `cardPriceOf`，出清/会员价就是列表后批量补的），新人价/秒杀价天然同构，三张商品卡（diy-goods / waterfall-goods / goods-list-panel）的角标渲染已收敛到 `badgeKind` 单枚举，扩枚举即复用；
2. 四个列表接口与两种 VO **零改动**；
3. 门槛/取整/优先级口径在服务端单点收口，前端零展示逻辑；
4. 新人价是 user-scoped，独立端点不污染列表响应的缓存语义；
5. 服务端装配：product-biz 经 Dubbo 拉 promotion（新人+秒杀）与 order（社证），内部查价格快照。

**免登口径**：分类页免登可看，`card-tags` 挂 `/app/` 免登白名单（前端 skipToken + 后端 Sa-Token 白名单 + `FRONTEND_PUBLIC_PATHS` 契约登记）；服务端尝试读当前登录用户，**未登录=潜在新人=下发新人价**（与免登楼层 `current()` 口径一致）。

**降级契约**：任何一路标签数据失败（Dubbo 异常/超时）该路置空，商品字段完整返回——商品卡永远不因营销标签挂掉（照抄 `GoodsRankServiceImpl` 的 try-catch 降级模式）。

## 关键决策

| 决策点 | 结论 | 理由 |
|---|---|---|
| 下发通道 | 独立批量端点 `card-tags` | 复用既有异步补价管线；列表 4 接口 2 形态零侵入；user-scoped 不进列表响应 |
| 新人价跨模块出口 | promotion-api 新增 `RemoteCardMarketingService`（含批量新人价+批量秒杀） | `getSkuPrices` 已批量（≤200 SKU）但无远程出口；新人+秒杀同在 promotion-biz，聚合后 product 只引一个远程接口 |
| 秒杀批量 | `ISeckillActivityService` 增批量方法（seckill_goods JOIN seckill_session，进行中） | 现有 `getGoodsSeckillInfo` 仅单 SKU；状态由 `seckillStatusJobHandler` 物化，查询即索引点查 |
| 社证统计归属 | **order 域**（新表+Job 在 order-biz），product 经 `RemoteGoodsSalesStatService` 读 | 数据在 order_item，跨库不能 JOIN；照抄 `RemoteGoodsSalesRankService` 模式 |
| 价格快照归属 | **product 域**（新表+Job 在 product-biz） | 标价/出清价在商品域；秒杀价远程批量拉取，失败降级仅快照本地价 |
| 快照价口径 | **非个性化到手价 = MIN(标价, 出清生效价, 秒杀价)**；会员价/新人价不计入 | 会员/新人价 user-scoped 无法全局快照；口径一旦对外展示即合规证据，表 comment 与本文档双重固化 |
| 近14天最低价判定 | 页级批量 GROUP BY 快照表，不做冗余标记列 | `(tenant_id, sku_id, stat_date)` 唯一索引下每 SKU 仅 14 行，20 SKU 页一次聚合 <300 行，免第二个 Job 与迁移 |
| 打标门槛 | 窗口内出现过 ≥2 个不同价（发生过变价）且 当前价 < originalPrice | 防常平价商品天天挂「最低价」，只在真降价/活动价时出标 |
| 社证文案 | 服务端下发成文（`socialProofText`），前端零拼装 | 取整/门槛口径单点；ARYN 无国际化诉求 |
| 社证统计表（落地修正） | **`stat_daily_spu_sales`，按 SPU 维度聚合**（设计初稿为 `order_sku_sales_stat` SKU 维度） | `stat_` 前缀对齐 `stat_daily_user_order` 家族；卡面只有 SPU 维度，SKU 级诉求出现前不加维度 |
| 社证聚合口径（落地修正） | **`order_info.status IN ('2','3','4')`，不加 `pay_status='1'`**（设计初稿多写了 pay_status） | COD 单 pay_status=0 但已过支付环节，热销榜口径注释明确「含货到付款」；加 pay_status 会剔掉 COD 且与热销榜漂移 |
| 社证 Job 租户方案（落地修正） | **逐租户设置上下文再聚合**（设计初稿为 @InterceptorIgnore 全租户分组） | 与 order-biz 既有 `OrderDailyStatJobHandler` 同款惯例；租户由拦截器与 SQL 显式条件双重保证 |
| Dubbo 注入 | 一律 `@DubboReference` 非 final 字段注入 | 构造器注入远程接口曾致 boot 启动失败（GoodsRankServiceImpl:55 注释在案） |
| DTO 序列化 | 新增远程 DTO 全部 `implements Serializable` | Dubbo strict 模式硬约束 |
| 聚合 SQL 租户 | Job 线程无租户上下文：聚合 SQL 挂 `@InterceptorIgnore(tenantLine)` + 显式 tenant_id 分组 | 既定模式（JSON_TABLE 事故同款），防租户拦截器解析注入 |
| 秒杀 MVP 边界 | 只做角标+价格展示，不做「抢」按钮改造 | 秒杀购买链路仍走秒杀楼层/商详；结算侧 `getPriceContext` 兜底本就存在，卡价仅展示 |
| card-tags 入参 | **spuIds**（设计初稿为 skuIds，落地时修正） | 装修楼层/推荐流的卡片项是 GoodsSpu 行、只有 spuId；SKU 级解析由 promotion 内部经 `RemoteGoodsSkuService.listBySpuIds`（新增精简列查询）完成 |
| 远程出口形状 | 单方法 `batchCardTags`（设计初稿为新人/秒杀两方法） | 新人+秒杀同在 promotion-biz，单方法一次 RPC 拿全，两路各自独立降级 |
| 卡价优先级 | **秒杀（硬互斥）> 会员/出清（取低、同价会员）> 新人价**（设计初稿顺序有误，按结算引擎口径修正） | 卡价必须等于结算到手价（zone-price.ts 头注释既有铁律），新人价是引擎优先级最低的身份价 |
| 装修预览边界 | **不镜像**卡面营销角标（设计初稿要求镜像，落地时按仓库既有边界修正） | 预览画布的基础商卡本就不渲染会员/捡漏等运行时角标（只有 newbie-floor 等营销组件有静态预览态）；新人/秒杀同为运行时状态，边界一致 |
| P3 判定「当前价」口径（落地修正） | **当日快照价**（非字面 sku.sales_price）：快照价口径已是 MIN(标价,出清,秒杀)，判定即「当日非个性化到手价 ≤ 窗口最低价」 | 按字面标价判定会让出清/秒杀降价当天永不打标，与快照口径自相矛盾；当日行缺失（Job 未跑/当天不在售）不出标，宁可不显示 |
| P3 SPU 级打标（落地修正） | **主价驱动 SKU** 判定：当日快照价最低的 SKU 自身满足三条件才出标；窗口最低价聚合含当天不在售 SKU 的历史行 | 展示价与声明严格对应（另一 SKU 达标但非主价时不出标）；「近N天最低」声明对全窗口成立（历史低价来自已下架变体也阻断） |
| P3 文案与开关（落地补充） | 文案动态拼 `近{window-days}天最低价`（默认 14，改窗口声明不失真）；新增 `goods-card.price-tag.enabled` 开关（默认 true） | 快照表空窗自然灰度（首日只有 1 个价、kinds=1 不出标）；开关供运维彻底关断放量 |
| P3 秒杀批量出口（落地修正） | `RemoteCardMarketingService#batchActiveSeckill(tenantId, skuIds)`（SKU 级 `GoodsSkuSeckillDTO`），内部经新增 `ISeckillActivityService#getActiveSeckillGoodsBySkuIds` | 快照按 SKU 逐行落库不能复用 SPU 聚合的 batchCardTags；「进行中」判定抽 private activeSessionIds 与既有口径共用 |
| P3 出清价复用（落地修正） | 直接复用现成的 `IGoodsZoneTagService#clearancePrices(skuIds)`（200 SKU 分批），未抽新方法 | 该方法本就是专区同源口径（SKU 覆盖+系统活动最终价+倒挂过滤+专区开关），复用即同源 |
| P3 DDL 补时间列（落地修正） | goods_sku_price_daily 增 create_time/update_time（对齐 stat_daily_spu_sales 家族） | 纯统计/快照表家族统一带审计列，问题排查需要 |
| P3 product-biz 接 xxl-job（落地补充） | product-biz 原无 xxl 接入：补 pom `aryn-common-job` + `@ArynEnableXxlJob` + Nacos `xxl.job.port=9011`（order 9009/promotion 9010 不冲突）；cloud Job 种子沿用 job_group=1 惯例 | 快照 Job 落 product-biz（设计文档未覆盖此前提），boot 模式经聚合天然生效 |

## 分期范围

| 期 | 内容 | DDL | 依赖 |
|---|---|---|---|
| P1 | 新人首单价 + 秒杀中 上卡（promotion 批量远程接口 + card-tags 端点 + 前端三卡扩枚举） | 无（实现时 EXPLAIN 确认 seckill_goods.sku_id 索引，缺则并入 P2 增量） | 无 |
| P2 | 社证统计（order 域统计表 + T+1 Job + 远程接口 + 文案格式化） | 1 表 + 2 份 Job 种子 | P1 的 card-tags 通道 |
| P3 | 每日价格快照 + 近14天最低价（product 域快照表 + Job + 页级判定）✅ 2026-11-08 已落地 | boot 216/217 + cloud 225/226/227 | 无（展示走 P1 通道） |

## 详细设计

### P1 新人首单价 + 秒杀中（✅ 2026-11-08 已落地）

**promotion-api**（新增）：
- `dto/GoodsCardTagDTO`（spuId / newbiePrice / seckillPrice，Serializable）
- `remote/RemoteCardMarketingService#batchCardTags(tenantId, userId, spuIds)` 单聚合方法

**promotion-biz**：
- `dubbo/RemoteCardMarketingServiceImpl`（`@DubboService`）：租户上下文快照替换显式入参再还原（RemoteBuyerProfileServiceImpl 同款）；新人价委托 `INewbieActivityService.getSkuPrices`（先经 `RemoteGoodsSkuService.listBySpuIds` 解析 SPU→SKU，SKU 超 200 分批），`eligible=false` 整体不下发、null（未登录）按可享下发；秒杀委托新增的 `ISeckillActivityService.getActiveSeckillGoodsBySpuIds`。两路独立 try-catch 降级，外层兜底。
- `SeckillActivityServiceImpl#getActiveSeckillGoodsBySpuIds`：进行中**按时间判定**（活动未暂停未结束 + 场次已开始未结束），与 `getActiveSessions` 同口径、不依赖 Job 落库 status（任务停摆不空窗）。

**product-api / product-biz**：
- `RemoteGoodsSkuService#listBySpuIds`（id/spuId 精简列）
- `vo/GoodsCardTagVO`（最窄白名单出参）
- `IGoodsCardTagService` / `GoodsCardTagServiceImpl`：入参去重截断 200、`@DubboReference` 非 final 字段注入、远程异常降级空列表、`SecurityUtils.getUser()` 可选取当前用户
- `AppGoodsSpuController#cardTags`（`GET /app/goodsspu/card-tags?spuIds=`）

**免登白名单**：boot `secure.ignore.urls` 已有 `/app/goodsspu/**` 域级通配天然覆盖，零配置变更；`PublicEndpointWhitelistContractTest.FRONTEND_PUBLIC_PATHS` 登记路径；cloud 网关 `/product/app/**` 已覆盖（契约测试既有断言）。

**结算一致性**：无需新兜底——下单链 `PromotionEngineServiceImpl.applyBestNewbiePrice` 与 `RemoteSeckillService.getPriceContext` 本就按资格/场次取价，卡价只是展示，不存在「卡价≠结算价」新风险。

**P1 验证记录（2026-11-08）**：
- 后端：`mvn compile -pl aryn-boot -am` 通过；`RemoteCardMarketingServiceImplTest` 9/9、`GoodsCardTagServiceImplTest` 5/5、`AppGoodsSpuControllerTest` 4/4、`PublicEndpointWhitelistContractTest` 5/5 全绿
- 前端：`pnpm type-check` 通过；`pnpm test:unit` 1773/1773 全绿（156 文件）；改动文件 eslint 零告警
- 双模式路径：`rewriteBootUrl('/product/app/goodsspu/card-tags', true)` → `/boot/app/goodsspu/card-tags`；`false` → 原样走 `/product` 网关域（白名单已覆盖）
- P1 零 DDL、零增量 SQL

### P2 社证统计（✅ 2026-11-08 已落地）

**新表 `stat_daily_spu_sales`（order 域，boot 214 / cloud 222 建表）**：`(tenant_id, spu_id, stat_date)` 唯一，按 **SPU 维度**聚合（卡面只有 SPU 维度）：`buyer_cnt`（近30天去重买家）、`repeat_buyer_cnt`（不同订单数≥2 的去重买家）、`repurchase_rate`。纯统计表无逻辑删除，历史暂不清理。

**聚合 Job**：order-biz `job/SpuSalesStatJobHandler`，`@XxlJob("spuSalesStatJobHandler")`，每日 02:30（boot 215 / cloud 223 种子注册，`trigger_status=1`）：
- 口径与热销榜**完全同源**（`OrderItemMapper#selectGoodsSalesRank`）：`order_item JOIN order_info`，`order_info.status IN ('2','3','4')`（已过支付环节，**含货到付款，不加 pay_status**）、双方 `del_flag='0'`、窗口按 `order_info.create_time` 近 30 天；回购判定 = 同一 (用户,SPU) 的 `COUNT(DISTINCT order_id) ≥ 2`（同订单多件不算回购）；
- **逐租户设置上下文再聚合**（`OrderDailyStatJobHandler` 同款，租户拦截器 + SQL 显式条件双重保证）；单租户失败不阻断；
- 幂等：自定义 mapper `INSERT ... SELECT ... ON DUPLICATE KEY UPDATE`（撞 uk 走覆盖，id 用 `REPLACE(UUID(),'-','')`，同 `DailyUserOrderStatMapper` 家族风格）；参数 days 可回填 1-90 天（首次部署传 14 预铺底数）。

**远程接口**（order-api）：`RemoteGoodsSalesStatService#latestStats(tenantId, spuIds)` → `GoodsSpuSalesStatDTO{ spuId, buyerCnt, repeatBuyerCnt, repurchaseRate }`（Serializable）；order-biz `RemoteGoodsSalesStatServiceImpl` fail-closed（租户缺失空列表）+ 异常降级。

**文案格式化**：product-biz `support/SocialProofFormatter`（纯函数，单测钉死）：
- `formatCount`：`n≥1万 → floor(n/1万)+"万+"`；`n≥1000 → floor(n/1000)+"000+"`；`n<门槛 → null`（向下取整不可夸大）；
- 单卡单条优先级：**回购率（≥30% 且买家≥门槛）→ 月回头客N+人 → 月N+人已下单**；
- 门槛 `@Value("${goods-card.social-proof.min-buyer-cnt:1000}")` / `min-repurchase-rate:30`——默认值内联，yml 可覆盖（boot/cloud 同键），零配置强制项。

**card-tags 集成**：`GoodsCardTagServiceImpl` 两路独立降级合并（promotion 价签 + order 社证，`GoodsCardTagVO` 扩 `socialProofText`），**只命中社证的 SPU 也产出标签行**；前端 `ListZonePrice` 透传 `socialProofText`，三卡在价格区上方渲染灰字行（盒马式），前端零拼装。

**P2 验证记录（2026-11-08）**：
- 后端：`mvn compile -pl aryn-boot -am` 通过；`SocialProofFormatterTest` 6/6、`GoodsCardTagServiceImplTest` 8/8、`RemoteGoodsSalesStatServiceImplTest` 3/3、`SpuSalesStatServiceImplTest` 5/5 全绿
- SQL：`build-full-sql.mjs` 登记 214/215 后重新生成 `aryn_boot_full.sql`（31295 行），`verify-full-sql.mjs` 通过；boot yml `hx.tenant.tables` + cloud Nacos（224 号锚点脚本）双白名单登记
- 前端：`pnpm type-check` 通过；`pnpm test:unit` 1776/1776 全绿；改动文件 eslint 零告警
- 部署顺序：执行增量 SQL（214/215 + 222/223/224）→ 手动触发一次 Job（参数 14 回填）→ 社证文案次日可见

### P3 价格快照 + 近14天最低价（✅ 2026-11-08 已落地）

> 落地实现以上方「关键决策」表的 P3 落地修正为准（当前价=当日快照价、主价驱动 SKU 打标、
> 动态文案+enabled 开关、product-biz 新接 xxl-job 等）；本节数字小节保留设计原稿供对照。

**新表 `goods_sku_price_daily`（product 域）**：
```sql
CREATE TABLE IF NOT EXISTS goods_sku_price_daily (
  id         VARCHAR(32)   NOT NULL COMMENT '主键',
  tenant_id  VARCHAR(32)   NOT NULL COMMENT '租户',
  spu_id     VARCHAR(32)   NOT NULL COMMENT 'SPU',
  sku_id     VARCHAR(32)   NOT NULL COMMENT 'SKU',
  stat_date  DATE          NOT NULL COMMENT '快照日',
  sale_price DECIMAL(10,2) NOT NULL COMMENT '非个性化到手价=MIN(标价,出清生效价,秒杀价)；会员/新人价不计入（user-scoped）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_sku_date (tenant_id, sku_id, stat_date),
  KEY idx_tenant_date (tenant_id, stat_date)
) COMMENT='SKU每日价格快照：近N天最低价判定底表，口径即合规证据禁止随意变更';
```

**快照 Job**：product-biz `job/SkuPriceDailySnapshotJobHandler`，`@XxlJob("skuPriceDailySnapshotJobHandler")`，每日 01:30：
- 对全部在售 SKU 写当日行：价 = `MIN(sku.sales_price, 出清生效价, 秒杀价)`；出清价复用 `GoodsZoneTagServiceImpl` clearancePage 同源口径（必要时抽单 SKU 方法）；秒杀价走 `RemoteCardMarketingService.batchActiveSeckill`，**远程失败降级仅用本地价**（表 comment 记口径）；
- 幂等同 P2（ON DUPLICATE KEY UPDATE）。

**窗口判定**（`GoodsCardTagServiceImpl` 内，页级一次）：
```sql
SELECT sku_id, MIN(sale_price) AS min_price, COUNT(DISTINCT sale_price) AS price_kinds
FROM goods_sku_price_daily
WHERE tenant_id=? AND sku_id IN(...) AND stat_date >= DATE_SUB(CURDATE(), INTERVAL 14 DAY)
GROUP BY sku_id
```
打标条件（全部满足才下发 `priceTagText="近14天最低价"`）：
1. `当前 sales_price <= min_price`；
2. `price_kinds >= 2`（窗口内确实变过价）；
3. `sales_price < original_price`（有划线空间才有意义）。

窗口天数配置 `goods-card.price-tag.window-days=14`；首日上线快照表为空，自然不出标，随 Job 逐日填充（灰度友好）。

## 前端设计（aryn-mall-uniapp）

- `src/api/product/spu.ts`：新增 `getCardTags(skuIds)`（alova，`/product/app/goodsspu/card-tags`；免登 skipToken 登记）；
- `src/utils/zone-price.ts`：`ZonePriceBadgeKind` 扩 `'newbie' | 'seckill'`；`ListZonePrice` 扩 `socialProofText?: string`、`priceTagText?: string`；
- 补价管线 `useZonePrices.ts`：列表装载后与出清/会员补价并行批量拉 `card-tags`，命中新人价/秒杀价参与 `mainPrice` 取**最低可享价**，`badgeKind` 取价格来源；优先级 `seckill > newbie > clearance > member`（现有 clearance/member 相对顺序不动，只在其前插入）；
- 三张卡角标渲染（模板均挂在既有 `badgeKind` 分支上）：
  - `diy-goods/index.vue`（:154 price-badge / :165 price-original）：badgeKind 样式分支加 `newbie`（描边红「新人首单价」）、`seckill`（实底红「秒杀价」）；
  - `goods-list-panel/index.vue`（:382 goods-price-badge）：同上，`goods-price-badge--newbie/--seckill`；
  - `waterfall-goods/index.vue`（:262 zone-badge）：同上；
  - 新增两行展示位：划线价上方灰字社证行（`socialProofText`）、主价旁描边小标（`priceTagText`，降价折线图标+「近14天最低价」，照小象样式）；仅 `card-tags` 下发的文本直接渲染，前端不做门槛/取整；
- **管理端装修预览同步**：page-designer 预览画布的 diy 卡须按 1rpx=0.5px 逐值镜像新角标（`preview-parity.test.ts` 扩断言），预览端 badgeKind 枚举与文案同源；
- 契约测试同步：`zone-price-contract.test.ts`（badgeKind 文案映射）、`diy-zone-price.contract.test.ts`、`diy-goods` 角标分支、新增 `card-tags` 管线契约（降级置空时三卡不渲染新元素）；`original-price-contract.test.ts` 划线口径不动（新人/秒杀价 < 原价时现有 strike 机制自然生效）。

## SQL（双模式，编号自当前最大顺延：boot 214+ / cloud 222+）

| 期 | 模式 | 脚本 | 内容 |
|---|---|---|---|
| P2 | boot | `db/boot/214spu_sales_stat_incremental.sql` | 建表（IF NOT EXISTS 可重复执行） |
| P2 | boot | `db/boot/215spu_sales_stat_job.sql` | `spuSalesStatJobHandler` 种子（207 号模式：NOT EXISTS 判重，cron `0 30 2 * * ?`，trigger_status=1） |
| P2 | boot | `application.yml` `hx.tenant.tables` + `build-full-sql.mjs` sections 登记 | 白名单 + 重新生成 `aryn_boot_full.sql`（`node db/boot/build-full-sql.mjs` + `verify-full-sql.mjs`，均已执行通过） |
| P2 | cloud | `db/cloud/222spu_sales_stat_incremental.sql` | 同内容建表（aryn_order 库） |
| P2 | cloud | `db/cloud/223spu_sales_stat_job.sql` | Job 种子（aryn_job 库） |
| P2 | cloud | `db/cloud/224spu_sales_stat_nacos_tenant_tables.sql` | Nacos `aryn-order-biz-dev.yml` 租户表白名单 REPLACE 锚点（221 号模式，锚点=stat_daily_user_order） |
| P3 | boot | `db/boot/216goods_sku_price_daily_incremental.sql` + `db/boot/217sku_price_daily_snapshot_job.sql` | 建表 + `skuPriceDailySnapshotJobHandler` 种子（cron `0 30 1 * * ?`，job_group=1 惯例） |
| P3 | boot | `application.yml` 白名单 + full 重生成 | `hx.tenant.tables` 登记后 `build-full-sql.mjs` sections 登记并重新生成 `aryn_boot_full.sql`（31390 行，`verify-full-sql.mjs` 通过） |
| P3 | cloud | `db/cloud/225goods_sku_price_daily_incremental.sql` + `db/cloud/226sku_price_daily_snapshot_job.sql` + `db/cloud/227sku_price_daily_nacos_tenant_tables.sql` | 同 P2 模式（aryn_product 建表 / aryn_job 注册 / aryn-product-biz-dev.yml 租户白名单 + xxl.port=9011，锚点 goods_category_badge 与 product 独有 logging 行）；Nacos 基线 `3aryn_nacos.sql` record 8 已同步改内容+md5（TenantConfigurationConsistencyTest 钉死） |
| P1 | — | 无 DDL | 仅在 EXPLAIN 发现 seckill_goods 缺 sku_id 索引时，并入后续增量补索引 |

禁止 DROP/TRUNCATE；两份增量内容除库上下文（USE）外与 boot 逐字一致。

## 测试与验证方案

**后端单测**：
- promotion：`RemoteCardMarketingServiceTest`（批量命中/未登录口径/tenantId 缺失 fail-closed/秒杀仅进行中）；
- order：`SkuSalesStatJobHandlerTest`（口径 SQL 渲染断言须先 getSqlSegment（wrapper 惰性绑定）、幂等重跑、@InterceptorIgnore 在位）；聚合 SQL EXPLAIN 留档；
- product：`GoodsCardTagServiceTest`（四路装配、每路独立降级、skuIds>200 截断、P3 打标三条件边界）、`SocialProofFormatterTest`（999→null、1000→"1000+"、4321→"4000+"、61234→"6万+"、rate 29→null/30→"30%人回购"）；
- boot 白名单审计：`TenantInterceptorBypassAuditTest` / `TenantConfigurationConsistencyTest` 按既有模式扩新表；
- 免登契约：`PublicEndpointWhitelistContractTest` 登记 `card-tags`，uniapp 侧 skipToken 清单同步。

**回归基线**：`mvn clean test -pl aryn-boot -am`（对照基线失败清单判新失败）；uniapp `pnpm type-check && pnpm test:unit`；动预览则 UI 仓 `pnpm check:type && pnpm test:unit && pnpm lint`。

**双模式路径**：`rewriteBootUrl('/product/app/goodsspu/card-tags')` → boot 模式 `/boot/app/goodsspu/card-tags`、cloud 模式原样走网关 `/product`，两端各验一次（`VITE_OPEN_BOOT=true/false`）。

**验收演示口径**：造一个新用户（未下单）与一个老用户同页对比——新用户卡见「新人首单价 ¥X」+划线原价，老用户同卡无此角标；秒杀场次内的 SKU 出「秒杀价」角标；跑一次 Job 后热门 SKU 出「月N+人已下单 / X%人回购」；对快照表中出现过降价的 SKU 出「近14天最低价」。

## 风险与边界

1. **聚合大表扫描**：order_item 增长后 30 天窗口聚合成本上升——驱动表用 order_info（create_time 近30天）再 JOIN 明细，EXPLAIN 确认走索引；必要时 214 号增量补 `(tenant_id, create_time)` 索引；
2. **合规红线**：`priceTagText` 一旦对外展示，快照口径即价格合规证据——口径变更须走需求评审，禁止 Job 顺手改；
3. **秒杀/新人价上卡的展示-结算一致性**：结算链路本就按资格/场次取价，无新增风险；但**普通卡加购不拦截秒杀 SKU**（限购校验在秒杀购买链路），MVP 文档标注，产品如需「抢」按钮再立项；
4. **分期上线顺序不可倒置**：P3 快照 Job 先跑（无展示）→ 积累满 14 天窗口后 `price-tag` 开关再放量，避免上线首日全站挂「最低价」；
5. **商详页不在本期**：仅商品卡（列表/楼层/瀑布流），商详角标后续复用同一 `card-tags` 单 SKU 调用。

## 标签视觉规范（2026-11-08 视觉增强版 v2，取代 v1 素色方案）

> v1 定稿后用户反馈「太素、没有进来要买的欲望」，同日升级为 v2。信息架构不变
> （社证行在价格行上方、价格声明在主价旁、身份价 badge 在价格行），只升级标签视觉 treatment。
> 令牌单点收口在 `aryn-mall-uniapp/src/styles/goods-tag.scss`（mixin 只承载身份：底色/字色/字重/形状，
> 字号/内边距/图标尺寸由各卡按 rpx 或 px 体系落值），改令牌即全站同步。

**三族标签**（四张主卡 `goods-list-panel`/`waterfall-goods`/`diy-goods`/`diy-goods-tabs` 统一）：

| 标签 | v2 视觉 | 要点 |
|---|---|---|
| 社证（月N+人已下单/回头客/回购率） | 浅桃底胶囊 `#fff6ee→#ffe9d8` + 暖橙字 `#d43e00` + 数字加粗 | `splitSocialProofText` 把服务端成文拆「前缀+数字+后缀」，数字段 700（纯展示装饰，门槛/取整口径仍在服务端）；图标按类型配 `socialProofIcon` 随 chip 色 |
| 近N天最低价 | 浅红实底贴纸 `#fff0ec→#ffdfd9` + `#e02612` 600 字重 + 降价触底折线图标 | 同族浅描边 `rgba(224,38,18,.16)` 保住 v1 度量（行高不变） |
| 新人首单价 | 红橙渐变实底 `#ff2237→#ff7a00` 白字 700 + 白色星芒图标 | 专享钩子；文案即「新人首单价」（`labels.newbiePriceBadge`，en=First-order price）；**固定红橙系不随主题**（v1「新人跟主题纯色」决策被 v2 取代，与「标签固定品牌红」原则对齐） |

**标签行口径**（2026-11-08 用户确认）：新人首单价与社证**同一行并排**（`flex nowrap`，空间不足裁切社证文案，不折行、不把新人 chip 挤下价格行）；价格行只留秒杀/会员/捡漏身份价 + 主价 + 近N天最低价声明 + 划线。四张主卡的标签行类名：`goods-tag-row`（列表卡）/ `tag-row`（推荐流）/ `goods-tabs__tag-row`（Tab 流）/ `goods-info-chip-row`（装修 floor，px 体系）。

**身份价角标（价格行）**：会员价=浅金描边、秒杀价=主题红渐变；**捡漏价自 2026-11-08 统一为固定红橙渐变实底白字**（`@include goods-tag-clearance`，`#fa2c19→#ff6b45`，不随主题）。此前同一枚「捡漏价」在首页 Tab 是纯红文字、个人中心/分类/收藏/足迹是固定渐变实底、装修楼层是主题红实底，三种画法并存；本轮把 12 个卡面（`diy-goods-tabs` 及 goods/waterfall/group/ranking/recommend/scroll、`goods-list-panel`、`waterfall-goods`、`goods-detail-sheet`、收藏、足迹）统一接入同一 mixin，各卡只保留自己的字号/内边距/圆角。商详价格条的白描边胶囊、购物车的软底胶囊属不同容器语境，不在此口径内；新人楼层/新人页的图上角标是通用楼层角标（含会员/出清/新人），维持原样。契约见 `components/diy/bargain-price-contract.test.ts#renders every clearance badge through the shared identity token`。

**延续 v1 的硬约束**：三族标签固定红橙系不随多租户主题（主题橙下橙框被否的教训）；「跟随主题」只适用于价格数字与秒杀角标。社证 chip `inline-flex + align-self:flex-start` 防父级拉伸铺满；`nowrap + ellipsis` 兜底裁切。

**次级卡面对齐**（修一类而非一个）：
- 收藏/足迹/快捷商详此前缺 `--newbie`/`--seckill` 背景分支（白字白底隐形）→ 补齐同一套令牌；
- 五个装修楼层（group/ranking/recommend/waterfall/scroll）角标映射由 member/clearance 二元扩为按 `badgeKind` 枚举派生，四色语义与主卡一致。

**v2 落地修掉的两个存量缺陷**：
1. `diy-goods` 的 chip 行规则原嵌套在 `.goods-info-price` 内，而模板标签行在价格块**外** → 后代选择器永远不命中，样式裸奔（v1「素」的根因之一）。规则上移到 `.goods-box-info` 层并加位置硬约束注释；
2. `diy-goods` 价格行加购按钮绝对定位右上，价格/贴纸/划线价会滑到按钮底下 → `.goods-info-price` 预留 `padding-right: 26px` 按钮列。

**踩坑记录（v1 历史，保留供对照）**：首版 chip 用主题色变量 + 600 字重 + 大 padding，在橙色主题下渲染成粗橙框（用户否）；二版修正为固定红系 + 细字重后通过；v2 在固定红系前提下升级为实底贴纸/胶囊增强购买欲。教训：**参考品的标签色是固定的品牌红，不随主题换肤**——「跟随主题」只适用于价格数字。

**v2 验证记录（2026-11-08）**：
- `pnpm type-check` 通过；`pnpm test:unit` 157 文件 / 1794 全绿（含 `splitSocialProofText` 三用例：成文形态/脏数据/三段拼接恒等）；改动文件 eslint 零告警
- H5 真渲染截图四卡面（分类双列卡 / 首页 Tab 流 / 注入装修 goods 楼层 / 商详推荐流）：社证胶囊+数字加粗、最低价贴纸、新人渐变星芒、秒杀渐变全部命中；`scrollWidth == innerWidth` 无横向溢出；控制台仅存量 diy-tabnav wxs H5 报错（本次未动该组件）
- `pnpm build:mp-weixin` 通过；产物 wxss 抽查：chip 选择器挂在 `.goods-box-info` 层、渐变与 `padding-right:26px` 落位；折叠屏 px 段随构建编译，待开发者工具复核

## ⚠️ 重大引擎语义变更：身份价取低（2026-11-08，用户确认）

**原语义**：会员/出清价锁定的 SKU，新人价无条件跳过（`PromotionEngineServiceImpl#applyBestNewbiePrice`）——全店会员价把 ¥1 新人钩子全面压制，新人价在 demo 几乎永不生效（用户看到「新人首单价不显示」的根因）。

**新语义（用户拍板「身份价取低」）**：新人价与已锁定身份价（会员/出清/保留的阶梯价）的**实际生效价**比较，**严格更低才覆盖**，同价身份价保留。实现要点：

1. `applyBestIdentityPrice`/`mergeIdentityOverrides` 返回 `Map<skuId, 实际生效价>`（原 `Set<skuId>`；阶梯价保留时记阶梯价）
2. `mergeNewbieOverrides` 删除 contains 跳过，靠既有「existing 单价 ≤ 新人价则保留原覆盖」取低；**被压制不置 matched**——新人价没生效就不产 detail、不写 `promotion_lock`（防白耗一次性新人资格）；重放场景（基价已=新人价）仍置 matched 保 detail 写锁
3. **让利防重复计费**：新人 detail 只记「相对替换前生效价」的增量（`(existing.unitPrice - 新人价) × qty`），否则替换身份价覆盖时与会员/出清明细重复计算总让利（`newbieSaving` 同口径取 `min(基价, 身份价)` 为基准）
4. 卡面 `resolveListZonePrice` 同口径：新人价严格低于身份价才覆盖；被压制不下发（卡价=结算到手价铁律不变）
5. 测试：`PromotionEngineTest` 4 个旧语义用例改写（新人更低覆盖会员/新人更高会员保留/层层取低/出清被覆盖），fixture 为 sku-1 qty=60 base=28.5、sku-2 qty=5 base=12（算让利别再算错）

**演示/部署状态（2026-11-08 凌晨）**：
- P1+P2 已部署 boot（compose，md5 双验证）+ Pages（`deploy-pages.sh --only h5`，隧道 `exceed-wholesale-lil-colour.trycloudflare.com`）
- **boot `application.yml` 末尾有演示低门槛段**（`goods-card.social-proof.min-buyer-cnt:1 / min-repurchase-rate:1`，环境变量可覆盖）——生产/正式演示前调回 1000/30 或删段
- demo 库近 30 天仅 6 买家，stat 表 38 行由**临时 cron 每分钟**触发回填（xxl-sso 挡 API 触发，改 `schedule_conf='0 * * * * ?'`+重置 `trigger_next_time` 的手法），**已恢复每日 02:30**
- 标签视觉 v1 四轮对稿定稿后，同日按用户「增强购买欲」反馈升级 v2（见上文「标签视觉规范 v2」）：热销胶囊社证+数字加粗、红底最低价贴纸、红橙渐变星芒新人价；布局口径延续 v1（标签行独立价格上方、与商品名左对齐、nowrap+ellipsis 兜底）

**P3 前置状态**：SQL 编号顺延至 boot 216+/cloud 225+（214/215/222/223/224 已占）；`GoodsCardTagVO` 已有 `newbiePrice/seckillPrice/socialProofText`，P3 扩 `priceTagText`；card-tags 已是三路装配结构（promotion 价签 + order 社证），P3 的价格快照判定在 product 域内部直接加第三路。

**P3 落地清单（2026-11-08）**：
- promotion-api / promotion-biz：`GoodsSkuSeckillDTO`（skuId+seckillPrice，Serializable）、`RemoteCardMarketingService#batchActiveSeckill`（租户快照替换+还原、同 SKU 多场次取最低、异常降级空列表）、`ISeckillActivityService#getActiveSeckillGoodsBySkuIds`（activeSessionIds 抽取共用）
- product-api：entity `GoodsSkuPriceDaily`、`GoodsCardTagVO` 扩 `priceTagText`
- product-biz：`GoodsSkuPriceDailyMapper`（批量 upsert + 页级窗口聚合两条 XML SQL，显式 tenant_id 与拦截器双保证）、`support/SkuPriceWindowStat`、`IGoodsSkuPriceDailyService`/`Impl`（MIN 三价合成、出清 200/秒杀 200 分批独立降级、upsert 500 分片传拷贝）、`job/SkuPriceDailySnapshotJobHandler`（逐租户上下文、单租户失败不阻断）、`GoodsCardTagServiceImpl` 第三路 `fillPriceTag`（主价驱动 SKU 三条件判定 + windowMin 全窗口校验，`goods-card.price-tag.window-days/enabled` 配置）、pom 补 `aryn-common-job` + `@ArynEnableXxlJob`
- 前端：`GoodsCardTagItem`/`CardTagCacheEntry`/`ListZonePrice` 全链扩 `priceTagText`（`sanitizeProofText` 防御透传）；四张卡（diy-goods px 体系 / diy-goods-tabs 三价格变体 / goods-list-panel / waterfall-goods 含折叠屏 px 段）价格行主价旁描边小标（`1px solid rgba(250,44,25,0.35)` + `#fa2c19` 细字重 + 降价折线 base64 SVG 图标，固定红系不随主题）；装修预览不镜像（决策表既有边界）

**P3 验证记录（2026-11-08）**：
- 后端：`mvn clean compile -pl aryn-boot -am` 通过；`GoodsCardTagServiceImplTest` 19/19（8 旧用例适配新构造器 + 11 个 P3 三条件边界：常平价/涨价回弹/无划线空间/无当日快照/非主价达标/同价任一达标/不在售历史行阻断/窗口文案/开关/独立降级）、`GoodsSkuPriceDailyServiceImplTest` 7/7（MIN 合成/双降级/倒挂过滤/分片/脏行）、`SkuPriceDailySnapshotJobHandlerTest` 3/3（逐租户/失败不阻断/上下文还原）、`RemoteCardMarketingServiceImplTest` 12/12（+4 个 batchActiveSeckill）、`TenantConfigurationConsistencyTest`+`TenantInterceptorBypassAuditTest` 全绿（新表双白名单登记，无新增旁路）
- SQL：`build-full-sql.mjs` 登记 216/217 后重新生成 `aryn_boot_full.sql`（31390 行），`verify-full-sql.mjs` 通过
- 前端：`pnpm type-check` 通过；`pnpm test:unit` 全绿（含 P3 透传/降级用例 + 四卡渲染契约 `zone-price-contract.test.ts` 新 describe）；改动文件 eslint 零告警
- 双模式路径：P3 无新端点（card-tags 路径 P1 已验：boot → `/boot/app/goodsspu/card-tags`、cloud → `/product/...` 网关域白名单覆盖）
- 部署顺序（风险 4）：执行 216/217（boot）或 225/226/227（cloud）→ Job 首跑写当日快照 → 表空窗期自然无标 → 14 天窗口逐日积累后自然放量；紧急关断用 `goods-card.price-tag.enabled=false`
