# 购物车同款商品重复与结算合并

> 修复「购物车出现重复商品 → 去结算报错『订单商品不能包含重复SKU』、确认页空白 ¥0.00」。
> 日期：2026-10-25。范围：`aryn-mall-java/aryn-order` + `aryn-mall-uniapp`（零 DDL、零新增接口、boot/cloud 同源）。

## 背景

用户贴三张截图：

1. 购物车里有 **两行完全相同的「老李卤豆干小包装500g」**（单价、折扣、数量都相同），上方还有「此前靠港计划（福建港）加购，结算将按常规方式配送」「当前船舶：悦航7号（暂无进行中的靠港计划）」两条分组说明；
2. 点击「去结算」后，订单确认页只剩一条红色 toast「**订单商品不能包含重复SKU**」，页面空白、合计 ¥0.00，无法下单。

用户诉求：购物车里出现重复商品，去结算却因「商品重复」失败。

## 根因（真实环境复现）

`shopping_cart` 的防串船唯一键是 `uk_shopping_cart_active_row(tenant_id, user_id, active_row_key)`，而
`active_row_key = CONCAT(sku_id, ':', IFNULL(vessel_call_id, ''))`（58 号增量脚本，生成列）——
**同一 SKU 在不同靠港分组各占一行是设计内行为**（分组头、切换/转入靠港都依赖它）。

本次数据（用户 `2103140502970413057`，SKU `10740000000000000355`）：

| 购物车行 ID | 数量 | vessel_call_id | 说明 |
|---|---|---|---|
| 2113674401893740546 | 1 | 2112528239246413825 | 福建港靠港行（该靠港 ETD=2026-10-24 21:00，`status=3` 已结束） |
| 2113993306163228673 | 1 | NULL | 靠港结束后客户端靠港快照清空，2026-10-24 21:57 再次加购落入「无归属」组 |

两行最终都按常规方式配送，但没有在提交边界合并：

- 确认页 `order-confirm` 的 `initData` 把 `createGoodsList` 原样映射成 `state.orderParams.skuReqList`，两行同 SKU 一起提交；
- 后端 `OrderInfoServiceImpl.createOrder` / `settlementOrder` 原有一段「重复 SKU 直接拒绝」的硬校验（`skuIds.size() != skuReqList.size()` 抛 `订单商品不能包含重复SKU`，2026-07-19 购物车加固引入）。

真实接口复现（boot 容器）：

```
POST http://localhost:9999/boot/app/orderinfo/settlement   （两行 SKU 10740000000000000355）
→ {"code":1,"msg":"订单商品不能包含重复SKU","data":null}
```

同根因的**潜在缺陷**：共享购物车 `SharedCartServiceImpl.splitByMember` 刻意「按成员拆行、不做 SKU 合并」（同一 SKU 每位成员各一行，是仓库按人分拣、打印配送姓名标签的依据），多成员买同款时同样会撞这段硬校验。

## 方案

三层，缺一即复发：

1. **后端归一化（权威兜底，旧客户端也生效）**：新增 `OrderSkuReqNormalizer`，`createOrder`/`settlementOrder` 在生成 `skuIds` 前归一化：
   - 无人货归属（`contributorUserId`/`contributorName` 均为空）的同 SKU 多行 → **数量相加合并到首次出现的行**；
   - 带贡献者信息的行 → 视为共享购物车按成员拆行，**整组原样保留**（贴标签归属不能丢）；
   - 删除两处「重复 SKU 直接拒绝」抛错；查价仍按去重后的 `skuIds`。
2. **购物车加购并入滞留行（源头止血）**：`saveShoppingCart` 在当前无靠港上下文（`vesselCallId` 空）时，若同用户同 SKU 同船舶存在挂在**已结束靠港**的滞留行，数量并入该行而不新建「无归属」行；靠港仍可下单（`callOrderable=true`）不并入（防串船口径不放宽）；靠港快照远程失败时按无法判定处理、维持旧行为（宁可多一行也不误并进行中的靠港）。
3. **C 端**：
   - `order-confirm` 构造 `skuReqList` 前用 `mergeDuplicateSkuItems` 按 SKU 合并数量（提交边界只合并数量）；**防串船校验仍用原始 `createGoodsList` 逐行比对 `vesselCallId`**（合并后的行没有归属信息，不能拿来判归属）；
   - 购物车页保留按靠港分行的展示（归属信息不能丢），对重复 SKU 行补一句「同款商品结算时合并数量」，避免用户误以为重复加购。

## 改动

后端（`aryn-mall-java/aryn-order/`）：

- 新增 `aryn-order-biz/.../support/OrderSkuReqNormalizer.java`：归一化规则 + 中文原因注释（为什么合并、为什么共享车拆行不能合并）。
- `OrderInfoServiceImpl`：`createOrder` / `settlementOrder` 两入口接入归一化，删除「订单商品不能包含重复SKU」硬校验。
- `ShoppingCartServiceImpl`：`saveShoppingCart` 增加 `findStrandedRowOfSameVessel` 滞留行并入（仅同船、仅已结束靠港、快照失败降级新建）。
- 测试：新增 `OrderSkuReqNormalizerTest`（8 例）；`ShoppingCartServiceImplTest` 增加 4 例（并入滞留行 / 进行中靠港不并入 / 快照异常回落新建 / 无 vesselId 不查）。

C 端（`aryn-mall-uniapp/src/`）：

- 新增 `utils/order-sku-merge.ts`（纯函数，按 `skuId` 合并数量、保序、浅拷贝不改入参、空 skuId 不合并）+ `order-sku-merge.test.ts`（7 例）。
- `sub-pages/order/order-confirm/index.vue`：`initData` 用合并后的列表构造 `skuReqList`；`toPay` 防串船校验保持原始列表。
- `pages/user/shopping-cart/index.vue`：新增 `duplicateSkuIds` computed 与「同款商品结算时合并数量」提示（`.merge-tip`）。
- 新增 `sub-pages/order/order-confirm-contract.test.ts`：守门「前端提交边界合并 + 后端两入口归一化 + 防串船仍用原始行 + 购物车提示」四件事。

## 验证

1. **后端单测**：`mvn -q -pl aryn-order/aryn-order-biz -am test -Dtest='OrderSkuReqNormalizerTest,ShoppingCartServiceImplTest,ShoppingCartVesselMergeTest,SharedCartServiceTest,CheckoutRequestValidationTest' -Dsurefire.failIfNoSpecifiedTests=false` → 99 tests / 0 failures；order-biz 全模块 **533 tests / 0 failures**。
2. **C 端**：`pnpm test:unit` → 94 files / 977 tests 全绿；`pnpm type-check` exit 0。
3. **真实环境回归**：重新打包并重建 `aryn-boot` 容器后，用修复前的同一请求（两行 SKU `10740000000000000355`）重放 `POST /boot/app/orderinfo/settlement` → `code=0`，返回明细中该 SKU 仅一行、`buyQuantity=2`（原为 `code=1` + 报错）。旧版小程序客户端无需更新即可恢复结算。
4. **全量 `mvn test -pl aryn-boot -am`**：`aryn-order-biz` 533 全绿；唯一失败 `TenantInterceptorBypassAuditTest` 与本次改动无关——工作区中并行进行中的搜索页功能新增了未提交的 `GoodsRankMapper.selectHotTop10` / `selectOnSaleByIds` 两个显式租户旁路，审计白名单尚未同步（本次未触碰该文件）。

## 边界

- 零表结构/字段/种子变更，无需 boot/cloud 增量 SQL；归一化在 order-biz 内部，boot/cloud 同一份代码。
- 购物车仍按靠港分行展示（防串船归属信息保留），只在结算/下单边界按 SKU 合并数量。
- 同一 SKU 若跨「仍在进行中的不同靠港」仍是两行，按既有规则需先切换船舶或「转入本次靠港」（结算前会被拦截），本需求不放宽防串船。
- 加购并入滞留行只对「同船 + 已结束靠港」生效；靠港快照远程失败时维持原「新建一行」行为。
- 共享购物车按成员拆行、订单明细按人分拣贴标签的语义未改；多成员买同款现在可正常下单（后端口径恢复）。
- 本需求未提交 git，工作区包含其他并行任务的未提交改动。
