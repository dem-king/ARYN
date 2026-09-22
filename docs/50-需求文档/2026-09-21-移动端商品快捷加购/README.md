# 移动端商品快捷加购

> 日期：2026-09-21
> 状态：**已实施并验证**
> 关联：[船供化交互调研与竞品对标](../../2026-09-19-船供化交互调研与竞品对标/README.md)、[共享采购与商品统一](../../2026-09-20-共享采购与商品统一/README.md)

---

## 1. 原始请求

> 移动端商品增加一个快速加入购物车的功能，可以快速将商品加入购物车。

## 2. 目标与非目标

### 目标

1. 商品**列表/推荐位**上的商品卡片可以直接加入购物车，不必先进详情页再唤起规格弹层。
2. 单规格商品一键完成；多规格商品自动唤起规格弹层（不能让用户对着一个不知道规格的商品加购）。
3. 加购数量满足船供包装资料的数量规则（MOQ / 步长），避免「加购成功、结算才被拦」。
4. 装修组件（`goods` / `goods-group` / `goods-waterfall`）上的购买按钮从**纯装饰**变为**可点击加购**，同时保留运营在后台选的按钮样式。
5. 前后端双模式（boot / cloud）路径均正确。

### 非目标

- 不改造购物车页与结算页的既有交互。
- 不引入新表、不新增数据库字段。
- 不改变商品详情页的加购/立即购买行为。

## 3. 影响的端与文件

| 端 | 文件 | 说明 |
|---|---|---|
| 后端 | `aryn-product-api/.../vo/QuickCartInfoVO.java` | 新增：加购方式（direct/choose/unavailable）+ 数量规则 |
| 后端 | `aryn-product-api/.../vo/QuickCartSkuVO.java` | 新增：多规格可选 SKU（只下发购买决策字段） |
| 后端 | `aryn-product-biz/.../service/IQuickCartService.java` + `impl/QuickCartServiceImpl.java` | 新增：按需查询加购可行性 |
| 后端 | `aryn-product-biz/.../controller/app/AppGoodsSpuController.java` | 新增 `GET /app/goodsspu/quick-cart/{id}` |
| 移动端 | `src/utils/quick-cart.ts` | 纯规则：加购方式归一化、默认数量计算 |
| 移动端 | `src/composables/useQuickCart.ts` | 登录拦截 → 查询 → 加购/唤起弹层 |
| 移动端 | `src/components/quick-cart-button/index.vue` | 可复用按钮（含内部 SKU 弹层，懒挂载、支持插槽换皮） |
| 移动端 | `src/sub-pages/product/goods-list/index.vue` | 商品列表接入 |
| 移动端 | `src/components/diy/diy-goods\|diy-goods-group\|diy-goods-waterfall/` | 装修组件接入 |

## 4. 接口

### `GET /product/app/goodsspu/quick-cart/{id}`

列表接口不返回 SKU 明细，加购按钮**点击时**才按需查询一次。

| 字段 | 说明 |
|---|---|
| `mode` | `direct` 直接加购 / `choose` 需选规格 / `unavailable` 不可加购 |
| `skuId`、`salesPrice`、`stock`、`picUrl`、`specsInfo` | `mode=direct` 时返回 |
| `moq`、`stepQty`、`purchaseUnit` | 来自 `ship_sku_profile`，未维护该资料时为 `null`（按 1 处理） |
| `goodsSkus[]` | `mode=choose` 时返回可选 SKU（含 `specsArr` 用于渲染规格区） |
| `reason` | `mode=unavailable` 时的原因文案 |

**不写浏览足迹**：商品详情接口 `selectApiSpuById` 之外的路径都会写 `goods_footprint`，
而快捷加购只是「加购意图」，不能污染用户足迹与热搜榜。因此这里复用详情查询的
SQL 但**不调用** `getApiSpuById`，改直连 `goodsSpuMapper.selectApiSpuById`。

### 数量规则（与后端结算口径一致）

`SharedCartServiceImpl.validateQuantityRules` 在共享购物车提交时校验
`数量 ≥ MOQ` 且 `数量 % stepQty == 0`。前端取「满足该规则的最小值」：
`ceil(moq / stepQty) * stepQty`；库存不足该值时返回 0 并提示。

## 5. 交互

| 场景 | 表现 |
|---|---|
| 未登录 | 直接跳登录页（不先发一次必然 401 的请求） |
| 单规格 + 有货 | 按合法数量直接加购，Toast「已加入购物车」，购物车角标刷新 |
| 多规格 | 弹出规格弹层，选完规格后加购 |
| 售罄 / 下架 | Toast 具体原因（下架商品后端返回 `null`，前端按请求失败静默处理） |
| 点击加购按钮 | 不触发卡片跳转详情（`@tap.stop`） |

## 5.1 规格弹层的懒挂载

商品列表可能一次渲染几十张卡片。若每张卡片都挂一份 SKU 弹层，小程序节点数会随商品数
线性增长。因此弹层用 `v-if="skuMounted"` 懒挂载，**只有真正点开多规格商品时**才创建，
并由 `nextTick` 确保新建后能正常打开（否则首次点击不弹）。

## 6. 双模式适配

- 前端源码路径首段为微服务域 `product`，由 `rewriteBootUrl` 在 boot 模式下改写为 `/boot/app/goodsspu/quick-cart/{id}`。
- Controller 只声明 `/app/goodsspu`，boot 的 `/boot` 前缀来自 `context-path`，未硬编码。
- 未新增表/字段，无需 SQL 增量脚本。

## 7. 顺带修复：船供种子 SKU 状态写反（阻断级）

排查中发现 **`62ship_supply_seed_acceptance.sql` 把船供 SKU 的 `status` 写成了 `'1'`**。

`goods_sku.status` 语义是 **`0`=启用、`1`=停用**（管理端 `sku-table` 的
`ElSwitch` 为 `active-value="0"` / `inactive-text="停用"`），而加购与结算链路
（`GoodsSkuMapper.selectListByIds` / `selectSkuByIds`）只取 `status='0'`。

**后果**：全部 27 个船供验收商品都不可购买——加购直接返回 `60008 库存不足`。
这是本次快捷加购功能的**前置阻断**（否则船供商品一键加购必然失败），因此一并修复：

- boot / cloud 两份 `62ship_supply_seed_acceptance.sql` 同步改为 `'0'`
- 重新生成 `aryn_boot_full.sql` 并通过 `verify-full-sql.mjs`
- 新增契约测试固化该字段语义，防止回退

**存量库修复**（脚本仅对新建库生效，已导入的库需手工执行一次）：

```sql
UPDATE `goods_sku` SET `status` = '0' WHERE `id` LIKE '965%' AND `status` = '1';
```

## 8. 验收

| # | 场景 | 预期 | 结果 |
|---|---|---|---|
| 1 | 单规格商品（无船供资料） | 一键加购 1 件，购物车角标 +1 | ✅ H5/接口实测 |
| 2 | 单规格商品（MOQ 60 / 步长 12） | 一键加购 60 件（合法值），不报错 | ✅ 接口实测 |
| 3 | 多规格商品 | 返回 `choose` + 可选 SKU，弹层可渲染规格 | ✅ 接口实测 |
| 4 | 售罄/下架商品 | 不误报「已加入购物车」 | ✅ 单测覆盖 |
| 5 | 未登录点击加购 | 跳登录页 | ✅ 单测覆盖 |
| 6 | 点击加购不跳详情 | `@tap.stop` | ✅ 契约测试（反向验证） |
| 7 | boot 模式路径 | `/boot/app/goodsspu/quick-cart/{id}` | ✅ 单测覆盖 |
| 8 | cloud 模式路径 | `/product/app/goodsspu/quick-cart/{id}` | ✅ 接口实测 |
| 9 | 列表页节点数 | 弹层懒挂载，不随商品数线性增长 | ✅ 构建产物确认 |
