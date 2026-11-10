# 新人首件混价（首件新人价、超出件恢复售价）

> 2026-11-09 · 需求来源：用户「新人首件新人价后续加购恢复售价」；本轮对 2026-11-08 已落地版本做代码审查后修复完善。
> 旧规格（2026-09-27 新人专享定价调研）「每人限购 1 件、超量整单拦截」被本规格取代；资格口径（本租户已支付订单数=0、退款不恢复）、身份价取低（2026-11-08）、卡价=结算到手价等不变量继续生效。

## 一、规格

1. 新人资格用户每 SKU 首 `limitPerUser` 件（发布校验固定 1，引擎归一兜底）按新人价成交；**超出件按「替换前生效价」正常成交，不再整单拦截**。
2. 替换前生效价 = 该 SKU 被新人价取低替换前的覆盖价：有会员价/出清价时保留身份价（会员常态权益不剥夺），无身份价时即销售基价。
3. 秒杀仍硬限购：`capQuantity=true` 时加购预填与弹层封顶；新人价混价 `capQuantity` 缺省 false 不封顶。
4. 展示-结算同源：购物车/加购弹层混价小计 = 首件新人价 + 超出件×替换前生效价；超限提示「超过 N 件，按 ¥X/件算」的 X 同口径。

## 二、关键决策

- **超出件保留身份价**（审查发现会员×新人重叠时原实现按基价成交剥夺会员权益、且引擎让利明细与真实成交不符）。保留后引擎记账自洽：会员明细按全量（含首件的基价→会员部分）+ 新人明细增量（会员→新人×优惠件数）= 实际成交让利（fixture：510+10=520=1710−(10+59×20)）。
- **C 端拆行展示分两种口径**：确认页无逐行操作 → `mergeNewbieSplitRows` 合并单行 + 「首件享新人价，超出件单独计价」提示，件数按数量求和；订单详情/列表/评价的退款/评价按行 id 提交 → `annotateNewbieSplitRows` 保留逐行、仅打「首件享新人价/超出件单独计价」标注，避免误认重复加购也不孤儿化售后入口。管理端按两行对账（不改）。
- **超出行权益口径**：券/积分/整船按普通商品参与；会员折扣行级——超出行保留身份价（成交价<基价）时互斥（权益已落价，不重复让利），回落基价时按普通商品吃等级折扣；优惠行始终互斥。

## 三、后端改动

- `PromotionCalculationVO.UnitPriceOverride` 增 `remainderUnitPrice`（超出件成交单价，additive，双模式序列化兼容；空回落 `originalPrice`）。
- `PromotionEngineServiceImpl#mergeNewbieOverrides` 填 `remainderUnitPrice=替换前生效价`；`applyBestWholeDiscount` 门槛超出件按 remainder 累计（与订单超出行成交价同口径）。
- `OrderPriceComputeService#applyLadderOverrides` 超出行按 remainder 落价；`mergeStackPolicyHits` 新人价部分覆盖携带基价（供会员折扣识别身份保留）；`orderMemberBenefitHandler` 行级三态（见决策）；行级判别收敛共享谓词 `rowCarriesPrice`、`rowStackable`。
- 删除 `assertNewbieQuantityLimit`/`newbiePriceSkuIds`（旧拦截口径下线）。

## 四、前端改动

- `utils/newbie-price.ts`：`newbieLineSubtotal`/`newbieOverLimitHint` 增身份感知 `overUnitPrice` 参；共享公式 `mixedLineSubtotal` 导出（弹层复用，消除双实现）；`trimPrice` 导出。
- `utils/quick-cart.ts` `QuickAddPromotion.originalPrice`（超出件基准，身份价优先）；`useNewbieGoodsActions` 新人分支透传 `skuZonePrice`；购物车 `identityOverUnitOf`（出清胜出取出清、否则会员）传入小计与提示。
- 秒杀预填封顶恢复：`useQuickCart` `finalQuantity`、`useCartDestination` initialPayload 仅 `capQuantity=true` 封顶。
- `utils/order-item-merge.ts`：合并（确认页）+ 标注（详情/列表/评价）双口径，共享 `qualifiedSplitGroups`；注释与实现一致。
- 确认页件数改 `displayTotalQuantity`（数量求和）；详情/列表/评价接标注并渲染提示；locales 中英成对（checkout.goods.newbieFirstUnitHint/newbieRemainderHint，newbieSplitHint 文案改「超出件单独计价」）。

## 五、契约与测试

- 修复基线红契约 `diy-zone-price.contract.test.ts`：0872dbb2 把楼层专区价逻辑下沉 `useNewbieGoodsActions` 后断言对象过期，改扫 composable 接线（`useZonePrices`/`void zone.loadForSpuIds`/`resolveListZonePrice`/`zone.skuZonePriceOf`），行为未丢。
- 新增/更新：引擎 remainder 与整船门槛身份价用例；订单拆行保留身份价、会员折扣行级三态用例；`mixedLineSubtotal`/身份感知小计用例；`annotateNewbieSplitRows` 用例；购物车契约签名同步。
- 验证：Java `OrderPriceComputeServiceTest` 77/77、`PromotionEngineTest` 65/65；uniapp `type-check` 0 error、单测 163 文件 / 1857 用例全绿。

## 六、边界与后续

- 超出行按普通商品叠券/积分：若运营对身份活动单独配置「不可叠券」，被替换后的超出行仍按普通商品可叠（罕见配置，已知边界）。
- 退款/评价按行提交，拆行两行各自可退/可评；C 端标注解释价差。
- 无 DB 改动（VO additive、无新表/字段）；无新增 API 路径，双模式不受影响。