# 商超化商品详情：单规格免选规格与发货行语义修复

> 日期：2026-09-27
> 状态：**已实施并验证**（未提交，工作区有并行未提交改动交织，见 §5）
> 关联：[2026-09-22 移动端UI交互优化（对标小象超市）](../2026-09-22-移动端UI交互优化方案（对标小象超市）/方案.md) §3.2「商品详情首屏重构」、[2026-09-21 移动端商品快捷加购](../2026-09-21-移动端商品快捷加购/README.md)

## 1. 问题

对标小象超市/盒马鲜生：商超商品下单不需要手动选规格。核实（boot 库）：

| 项 | 数据 |
|---|---|
| 在架 SPU | 273 |
| 单规格（`enable_specs='0'`） | **269（98.5%）** |
| 多规格 | 4（全部是 3C 演示商品，同一分类） |
| 船供商品（有 `ship_sku_profile`） | 28，**全部单规格**；25 个 MOQ>1 或步长>1 |
| 运费 | 272/273 包邮 |

而详情页对单规格商品：

1. 规格行恒显示「选择规格」——旧代码用 `selectArr !== '默认'` 过滤弹层回传的
   占位值，用户选完也看不见反馈；
2. 「加入购物车」必须过一次弹层（弹层里只有一个自动选中的「默认」）；
3. 弹层数量固定从 1 起，MOQ/步长迟到共享购物车提交才被拦（「加购成功、结算被拦」）；
4. 发货行对已绑定船舶的用户显示「包邮 + 配送至：某某小区」——内部配送
   （`delivery_way=4`）不走收货地址，与 `ShipProfileCard` 的「公司司机按靠港
   计划送达港口/船舶」自相矛盾。

## 2. 方案与口径

判断收敛在新增纯函数 `utils/goods-purchase.ts`（详情页与 goods-detail-sheet 共用）：

- `resolvePurchaseDecision`：**明确 `enableSpecs === '0'` 才免选**。与后端
  `QuickCartServiceImpl.SINGLE_SPEC`、`initGoodsSpecs` 同口径；字段缺失/异常值
  fail-safe 回落「需选规格」，绝不盲加。
- `resolveSpecRow`：多规格维持「选择规格/已选组合」；单规格展示真实规格值，
  **无规格值时整行隐藏**（本库单规格 SKU 的 specs_arr 基本为空，规格信息在
  商品名与船供箱规里）。点击单规格行只调数量——弹层组件对单规格本就不渲染
  规格选择区（`isManyCom`）。
- `resolveDeliveryRow`：判据与结算页一致（`shipContextStore.hasVesselContext`）。
  内部配送展示「配送至 船舶 + 港口/泊位/时间窗」且不可点（船舶/靠港在购物车
  与工作台切换）；普通场景维持 运费/包邮 + 收货地址可点。标签「发货」→「配送」。
- 数量规则不在此重复实现：统一 `utils/quick-cart` 的 `resolveQuickAddQuantity`
  （列表页快捷加购、详情页直加共用同一份 MOQ/步长算法）。

关键约束：**结算页不能改数量**（`ShopOrderItem` 的 buyQuantity 只读），因此
「跳过弹层直接购买」必须先解决数量选择。当前工作区并行改动已移除「立即购买」
按钮，弹层仅服务多规格，该矛盾自然消解；若未来恢复立即购买，需先给结算页
加数量编辑。

## 3. 改动清单（纯前端，无 DB/后端改动）

| 文件 | 内容 |
|---|---|
| `utils/goods-purchase.ts` + `.test.ts` | 新增：购买决策/规格行/发货行纯函数 + 21 例单测 |
| `sub-pages/product/goods-detail/index.vue` | 单规格加购免弹层（`useQuickCart.quickAdd`，异常兜底退弹层）；规格结构初始化收敛到 `initGoodsSpecs`（顺带消除 goodsSkus 为空时 `goodsSkus[0]` 越界崩溃）；`onCloseSkuPopup` 加 `Array.isArray` 守卫 |
| `sub-pages/product/goods-detail/components/GoodsInfo.vue` | 规格行/发货行改由视图模型渲染 |
| `sub-pages/product/goods-detail/components/ShipProfileCard.vue` | 改用 `useShipProfile` 共享缓存，不再自发请求 |
| `components/goods-detail-sheet/index.vue` | 分类页底部面板同口径：单规格直加、规格行一致 |
| `composables/useShipProfile.ts` | 新增：`ship-summary` 模块级缓存，卡片与购买链路共用一次请求 |
| `sub-pages/product/goods-detail/purchase-contract.test.ts` | 新增：8 例源码契约守门 |

## 4. 验证

- `pnpm test:unit` **413 用例全绿**；`pnpm type-check` 通过；
  `npx uni build -p mp-weixin` Build complete。
- 缺陷注入反向验证（均已还原）：免选判据改成 `!== '1'`、规格行回退旧三元链、
  直加路径移除、占位规格注入先于原始规格值读取——分别被单测/契约测试捕获。
- 改动文件 ESLint 0 error（`index.vue` 内遗留 2 个 error 来自并行秒杀半成品，
  见下）。

## 5. 与并行改动的边界

本次开工前 `goods-detail/index.vue` 已存在他人未提交改动：秒杀功能半成品
（`loadSeckillInfo` 已定义**未接线**、`state.seckillInfo` 无消费、`@buy-now`
绑定与 `buyNow` 函数已移除、GoodsFooter 只剩「加入购物车」）。本次未触碰这些
行；该文件遗留的 `loadSeckillInfo is defined but never used`、`res is defined
but never used` 两个 lint error 属该半成品，非本次引入。**提交时注意**：整文件
stage 会把秒杀半成品一并带上。
