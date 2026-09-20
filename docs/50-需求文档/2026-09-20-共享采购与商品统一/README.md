# 悦航购 · 商品统一与共享采购改造方案

> 日期：2026-09-20
> 状态：**设计已确认，进入实施**（决策记录见 §10）
> 关联：[船供化交互调研与竞品对标](../../2026-09-19-船供化交互调研与竞品对标/README.md)、[船舶自助绑定实施进度](../../2026-09-19-船供化交互调研与竞品对标/船舶自助绑定-实施进度.md)

---

## 1. 背景

船供化一~三期把「船供商品」做成了与「个人商品」并列的一套目录：商品级 `sale_scope` 可见性闸门 + 管理端独立「船供目录」视图 + C 端独立船供采购页 + 独立的船供资料 Tab。

实际业务与之不符，有两个事实：

1. **船供不是商品分类，是采购场景。**
   一次靠港采购由一名海员代表全船发起，金额 1 万~十几万，涉及几十上百个 SKU；船上其他人的需求靠微信群收集。商品应该统一管理、统一展示，「船供」只体现在**下单时的船舶/靠港/内部配送/MOQ/共享购物车**。

2. **靠港时间只有海员知道。**
   公司无法与船舶公司对接 ETA/ETD。当前 `vessel_call` 由运营在管理端录入，等于让运营编造数据。正确方向是：**海员在下单时申报靠港信息，运营侧接收后再做排产决策**。

---

## 2. 已确认决策

| # | 决策 | 现状 |
|---|---|---|
| D1 | **商品统一**：不再区分船供/个人商品，管理端与 C 端均不区分展示 | 待改造 |
| D2 | **保留采购场景**：选船 + 选靠港 + 内部配送 + 采购单位/MOQ/步长 + 共享购物车 | 已就绪 |
| D3 | **靠港由海员申报**，运营接收后管理（修正、填配送时间窗、排波次） | 待改造 |
| D4 | **共享购物车不限发起角色**，任何船员都能发起 | ✅ 现状已满足 |
| D5 | **重复发起拦截**：同船已有「收集中」购物车 → 拦截并返回 cartId，前端直达该购物车 | 待改造 |
| D6 | **有效期 24 小时**，自创建时刻起算，服务端计算 | 部分（字段有，计算在前端） |
| D7 | **过期由 Job 关闭**：定时扫描置为「已关闭」 | 待新建 |
| D8 | **只有发起人能提交订单** | ✅ 现状已满足（`confirmerUserId` = 发起人） |
| D9 | **签收后归档**：订单签收 → 购物车置「已完成」，船员可见「本次采购已送达」 | 待改造 |
| D10 | **加入即绑定船舶**：分享卡片 → 登录/自动注册 → 绑定该船 → 加入购物车 | 待改造 |

---

## 3. 目标流程

```
发起人（任一船员）
  选船 → 申报/选择靠港 → 创建共享购物车（自动 24h 有效）
  → 转发到微信群
        │
        ├─ 群里船员点开卡片
        │     ├─ 有账号 → 微信一键登录
        │     └─ 无账号 → 一键登录即自动注册
        │     → 自动绑定该船（获得船员身份）
        │     → 自动加入共享购物车
        │     → 选自己要的商品，提交明细
        │
  发起人审核明细 → 调整核定数量 → 统一提交 → 生成整船订单
        │
  内部配送到船 → 司机签收 → 购物车自动置「已完成」
        │
  运营侧（管理端）全程可见：船舶、靠港（海员申报）、订单、履约波次
```

---

## 4. 商品统一

### 4.1 原则

`ship_goods_profile` / `ship_sku_profile` 的字段**全部保留**，但**降级为「商品的可选扩展属性」**，不再作为可见性闸门、不再构成第二套目录。

未填写船供资料的商品，在船供采购场景下按默认值处理：`采购单位 = 基本单位`、`MOQ = 1`、`步长 = 1`。

### 4.2 后端改动

| 文件 | 改动 |
|---|---|
| `aryn-product-biz/.../mapper/GoodsSpuMapper.xml` | 移除 `AND (ship_profile.id IS NULL OR ship_profile.sale_scope != '2')` 及其 LEFT JOIN（若无其他用途） |
| `aryn-product-biz/.../mapper/ShipGoodsProfileMapper.xml` | 船供目录查询移除 `sale_scope IN ('2','3')`；个人摘要移除 `IN ('1','3')` |
| `aryn-product-api/.../enums/SaleScopeEnum.java` | 标注 `@Deprecated`，保留枚举避免历史数据反序列化失败，**不再参与任何过滤** |
| `aryn-product-biz/.../service/impl/ShipProductProfileServiceImpl.java` | 发布校验移除「船供可见时必须有 IMPA/ISSA/内部编码」的强制项，改为**可选提示** |
| `ProductExcelConverter` / `ProductImportServiceImpl` | 导入模板中的 `saleScope` 列标记为忽略（保留兼容旧模板，不报错） |

### 4.3 管理端改动

| 文件 | 改动 |
|---|---|
| `views/product/goods-spu/index.vue` | **移除**「船供目录 / 返回普通列表」视图切换、船供专用列表与查询条件；保留单一商品列表。导出功能保留，改为导出统一商品目录 |
| `views/product/goods-spu/form.vue` | 「船供资料」Tab 重命名为「规格与编码」，并入商品主表单的语义（不再是"另一个类目"）。IMPA/ISSA/内部编码/英文名/储存条件/箱规/采购单位/MOQ/步长 均为可选字段 |
| `views/product/goods-spu/ship-product-validation.ts` | 校验规则从"必填"改为"填了才校验"（如 MOQ 必须 ≥1、步长为步长整数倍） |
| `views/product/goods-spu/components/PublishChecklist.vue` | 从"发布阻断"改为"资料完善度提示" |

### 4.4 移动端改动

| 文件 | 改动 |
|---|---|
| `sub-pages/product/ship-supply/index.vue` | **改为统一商品选货页**：商品数据源与普通列表一致；只在携带 `sharedCartId` 时进入「共享购物车选货模式」。页面标题不再是「船供采购」 |
| `components/ship-workbench/index.vue` | 入口语义调整：「船供采购」→ 进入统一商品目录并携带船舶上下文；不再指向独立类目 |
| `sub-pages/product/goods-detail/components/ShipProfileCard.vue` | 从「船供资料」卡片改为「规格与编码」展示，任何有资料的商品均可展示，无资料不渲染 |

> **保留**：船舶/靠港上下文、`purchase_scene`、`delivery_way=4`、共享购物车、履约波次、船供营销活动。

---

## 5. 靠港信息由海员申报（D3）

### 5.1 模型调整

`vessel_call` 拆分为两个权责域：

| 字段 | 填报方 | 性质 |
|---|---|---|
| `port_code` / `port_name` / `berth` / `eta` / `etd` | **海员** | 客观事实 |
| `delivery_window_start` / `delivery_window_end` | **运营** | 排产决策（保持可空，运营后填） |

新增字段区分来源：

```sql
ALTER TABLE `vessel_call`
  ADD COLUMN `source` char(2) NOT NULL DEFAULT '1'
    COMMENT '来源：1运营维护 2海员申报' AFTER `status`;
```

### 5.2 接口

- **新增** `POST /vessel/app/vessel-calls/declare` — 海员申报靠港（港口、泊位选填、ETA、ETD）
- **新增** 管理端 `GET /vessel/admin/calls/pending` — 待处理的申报靠港列表（`source='2'`）
- **复用** `PUT /vessel/admin/calls/{id}` — 运营修正海员申报的靠港（走现有 `vessel_call_change_log` + 站内信通知链路）

### 5.3 去重规则

同一艘船已有未完成（`status IN ('1','2')`）且 **ETA 距今在 ±72 小时内** 的靠港计划时，不新建，直接复用并要求用户确认。

> ✅ **已确认（2026-09-20）**：采用 ±72 小时窗口。

---

## 6. 共享购物车：分享与加入（D10，核心）

### 6.1 现状缺口

后端 12 个接口齐全，C 端页面已建，但分享链路断裂：

- `inviteSharedCartMember(cartId, memberUserId)` **要求发起人先知道对方的商城用户 ID** —— 微信群里的人还没进系统，不可能知道。
- 详情页的「邀请成员」表单让用户手填用户 ID，实际不可用。
- **无任何 `onShareAppMessage`** 实现，无法转发到微信群。

### 6.2 方案

**新增分享令牌机制**，卡片即通行证：

```sql
ALTER TABLE `shared_cart`
  ADD COLUMN `share_token` varchar(64) DEFAULT NULL
    COMMENT '分享令牌（转发卡片携带，随购物车过期失效）' AFTER `version`;
```

**新增接口**：

| 接口 | 说明 |
|---|---|
| `POST /order/app/shared-cart/{id}/share` | 发起人生成/刷新 `share_token`，返回带 token 的分享路径 |
| `POST /order/app/shared-cart/join` | 凭 token 加入：校验 token → 校验购物车未过期 → 若用户未绑定该船则自动绑定 `vessel_member` → 写入 `shared_cart_member` → 返回 cartId |

**前端**：共享购物车详情页实现 `onShareAppMessage`，卡片 path：
`/sub-pages/order/shared-cart/detail?id={cartId}&token={shareToken}`

用户点开：
1. 未登录 → 跳登录页（微信一键登录，**登录即注册**，无需单独注册页）
2. 登录后带 token 调 `join` → 自动绑定船舶 + 加入购物车
3. 直达购物车详情，可开始加购

### 6.3 安全边界

| 风险 | 处置 |
|---|---|
| 卡片被转发到外部群，陌生人加入 | token 随购物车 **24h 过期**；运营/发起人可在成员管理中移除；已加入的成员会留下 `vessel_member` 记录，可被移除 |
| 非本船人员加购混入整船订单 | **`addItem` 增加船舶成员校验**（当前只校验购物车成员）。复用 `vessel_member` + `RemoteVesselService.isVesselMember` |
| 冒用他人身份 | 依赖微信登录态；`shared_cart_item.user_id` 与 `vessel_member.user_id` 均为登录态写入，不接受客户端传入 |

---

## 7. 生命周期与自动化（D5 / D6 / D7 / D9）

### 7.1 创建时自动计算有效期

`SharedCartServiceImpl.create` 改为**服务端计算** `expiresAt = now + 24h`，忽略前端传值（前端不再计算，避免客户端时钟不可信）。

### 7.2 重复发起拦截（D5）

同一 `tenant_id + vessel_id` 已存在 `status = '2'`（收集中）的购物车时，**拒绝创建**并返回该购物车的 `cartId`，前端捕获后直接跳转。

**并发安全**：应用层查询无法防止两个请求同时通过校验，用 MySQL 生成列 + 唯一索引兜底：

```sql
ALTER TABLE `shared_cart`
  ADD COLUMN `active_flag` tinyint GENERATED ALWAYS AS
    (CASE WHEN `status` = '2' THEN 1 ELSE NULL END) STORED
    COMMENT '进行中标记：仅收集中为1，其余为NULL以允许多行';

ALTER TABLE `shared_cart`
  ADD UNIQUE KEY `uk_shared_cart_active` (`tenant_id`, `vessel_id`, `active_flag`);
```

> 唯一索引允许多个 NULL，因此「已提交 / 已关闭 / 已完成」不互相冲突，只有「收集中」互斥。

**错误传递**：`ArynBusinessException` 只带 `code` + `msg`，无法携带 `cartId`。但 `Result` 已有 `fail(String msg, T data)` 重载，因此 **`create` 接口改为不抛异常**，直接返回 `Result.fail("该船已有进行中的采购，请加入", existingCartId)`，前端从 `data` 取 `cartId` 并跳转。这样无需改动公共异常类。

### 7.3 过期 Job（D7）

新增 `SharedCartExpireJob`，参照 `DeliveryQualificationRetryJob` 的写法（`@Scheduled` + 多租户遍历，避免依赖 XXL-JOB 手工注册）：

- 扫描 `status IN ('2','3') AND expires_at < now()` → 置为 `status = '5'`（已关闭）
- 多实例并发由条件更新（`WHERE status = '2' AND expires_at < now()`）保证幂等

### 7.4 签收归档（D9）

新增状态：`6 已完成`。

触发点：订单签收时反向查找 `shared_cart.submit_order_id = orderId`，将购物车置为「已完成」并写 `completed_time`。

- 主路径：`OrderInfoServiceImpl.receiveUserOrder`（已有 `deliveryTaskService.signOnReceive` 联动，同一位置追加）
- 兜底：`DeliveryTaskServiceImpl.signOnReceive` 内同步处理，失败仅记日志（fail-open，不阻断签收）

```sql
ALTER TABLE `shared_cart`
  ADD COLUMN `completed_time` datetime DEFAULT NULL
    COMMENT '送达归档时间' AFTER `submitted_time`;
```

**C 端展示**：购物车列表/详情的「已完成」状态文案为「本次采购已送达」。需同步更新前端状态映射单一来源 `aryn-mall-uniapp/src/utils/shared-cart.ts`：

| 常量 | 值 |
|---|---|
| `CART_STATUS_COMPLETED` | `'6'` |
| `CART_STATUS_LABEL['6']` | `已完成（本次采购已送达）` |
| `CART_STATUS_THEME['6']` | 复用「已提交」的绿色系 |

> 该文件注释中「后端取值：1草稿 2收集中 3待确认 4已提交 5已关闭」需同步为 6 值。页面禁止自行三元兜底，一律经 `cartStatusLabel()`。

---

## 7.5 订单明细按成员拆分与标签溯源（决策 D11）

### 7.5.1 需求

配送时要给每个船员的商品**贴标签区分归属**。因此订单必须能回答：「这条明细是谁要的、多少数量、贴谁的名字」。

**决策（2026-09-20）**：采用**方案 A —— 按人拆行**。谁加购的商品就按这个人生成订单明细，不做 SKU 合并。

### 7.5.2 现状缺口（三处必须连带改造）

#### 缺口 1：`generateOrderItems` 按 SKU 遍历 + `findFirst`，拆行后会被静默丢弃

`OrderPriceComputeService.generateOrderItems` 外层遍历的是 `goodsSkuList`（去重后的 SKU），再用 `skuReqList.stream().filter(skuId).findFirst()` 取第一条请求：

```java
return goodsSkuList.stream().map(sku -> {
    CreateOrderSkuReqDTO placeOrderSku = skuReqList.stream()
        .filter(tree -> tree.getSkuId().equals(sku.getId()))
        .toList()
        .get(0);   // ← 同一 SKU 的其余请求行被丢弃
```

**后果**：若不改造，方案 A 拆行后同 SKU 只保留第一人的明细，其余人的商品**直接消失**，但金额可能已按全部数量计算 → 少发货、对不上账。

**改法**：外层改为遍历 `skuReqList`，内层匹配 `goodsSkuList` 中的 SKU，保证「一个请求行 → 一条订单明细」。

#### 缺口 2：`orderStockHandler` 的库存校验在拆行后必然误报

```java
List<GoodsSku> skuList = goodsSkuList.stream()
    .filter(goodsSku -> goodsSku.getStock() >= orderItemEntityList.stream()
        .filter(skuReq -> skuReq.getSkuId().equals(goodsSku.getId()))
        .findFirst()          // ← 只看第一条明细的数量
        .get()
        .getBuyQuantity())
    .toList();
if (CollUtil.isEmpty(skuList) || skuList.size() < orderItemEntityList.size()) {
    throw new ArynBusinessException(...ERROR_60008...);   // ← 拆行后必然成立
}
```

**两处错误**：
1. 库存比对只取 `findFirst()` 的单个数量，未按 SKU 求和
2. `skuList.size() < orderItemEntityList.size()` 在拆行后必然为真（3 人买 1 个 SKU → 1 < 3），**直接抛「库存不足」导致下单失败**

**改法**：库存校验改为**按 SKU 分组求和**后与库存比对；移除行数比较。

> 注：`GoodsSkuService.reduceStock` 已支持重复 SKU 的聚合（见 `GoodsSkuServiceImplTest#reduceStockAggregatesDuplicateSkuAndRequiresEnoughStock`），扣减侧无需改动。

#### 缺口 3：阶梯价按明细行数量判档，拆行后永不触发

营销上下文直接把明细映射为 `skuItems`（每行一条），而阶梯价用 `item.getQuantity() >= minQty` 判档：

```java
for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
    PromotionCalculationVO.UnitPriceOverride override = resolveLadder(ladders, item);
```

**后果**：整船满 20 件降价 5% 的活动，拆行后每行只有 2 件，**活动永不触发**——整船采购的核心优惠失效。

**改法**：`buildPromotionContext` 内**先按 SKU 聚合数量**再构造 `skuItems`（对外语义 = 整船该 SKU 的总量），算出的改价再分摊回各行。

### 7.5.3 顺带修复：`contributor_user_id` 溢出（P0）

`order_item.contributor_user_id` 为 `varchar(32)`，而合并逻辑把多个用户 ID 用逗号拼接：

```java
skuReq.setContributorUserId(skuReq.getContributorUserId() + "," + item.getUserId());
```

雪花用户 ID 为 20 位 → **两人同购即 41 字符，超出字段长度**，MySQL 非严格模式下静默截断、严格模式下报错。

**方案 A 拆行后每条明细只对应一个贡献者（20 字符），该缺陷自然消除**，无需扩容。

### 7.5.4 新增字段：姓名快照

标签要打印**中文姓名**，不能是用户 ID。`order_item` 与 `shared_cart_member` 增加姓名快照：

```sql
ALTER TABLE `shared_cart_member`
  ADD COLUMN `display_name` varchar(64) DEFAULT NULL COMMENT '成员展示姓名（默认取商城收货人姓名，可自行修改）';

ALTER TABLE `order_item`
  ADD COLUMN `contributor_name` varchar(64) DEFAULT NULL COMMENT '共享购物车贡献者姓名快照（标签打印用）';
```

**姓名取值优先级**：
1. `shared_cart_member.display_name`（成员自行确认/修改的姓名）
2. 该用户商城默认收货地址的 `recipientName`
3. 商城昵称 `nickname`
4. 兜底 `用户{ID后6位}`

**取值实现**：订单域已注入 `RemoteMallUserService`（`OrderInfoServiceImpl` 使用中），且 `order-api` 已依赖 `aryn-user-api`，可直接复用 `getUserByIds`；收货人姓名经 `RemoteUserAddressService` 获取（需新增按用户查询默认地址的方法，或由前端在成员加入时回填 `display_name`，**推荐后者**：一次填写、下单时直接读快照，避免下单链路依赖地址服务）。

### 7.5.5 拣货与标签

`fulfillment_pick_item` 对 `order_item_id` 有唯一约束（`uk_fulfillment_pick_item (tenant_id, wave_id, order_item_id)`），拆行后**天然一明细一拣货记录**，恰好满足「一行一个标签」。

标签所需字段链：`fulfillment_pick_item.order_item_id` → `order_item.contributor_name` + `spu_name` + `specs_info` + `buy_quantity`。

> 标签打印本身（PDF/小票模板）本次不实现，先保证数据可用；需要时在履约波次页导出。

### 7.5.6 验收补充

| # | 场景 | 预期 |
|---|---|---|
| 15 | 3 人各买同一 SKU | 生成 3 条订单明细，数量与贡献者各自对应 |
| 16 | 库存仅够总和 | 校验通过（按 SKU 求和比对），下单成功 |
| 17 | 库存不足总和 | 正确抛「库存不足」 |
| 18 | 整船满 20 件触发阶梯价 | 拆行后仍按整船总量触发，优惠金额与改造前一致 |
| 19 | 标签数据 | 每条明细可取到 SPU 名、规格、数量、贡献者姓名 |
| 20 | 两人同购不再溢出 | `contributor_user_id` 单值，无截断/报错 |

---

## 7.6 实施进度（2026-09-20）

### 已完成

| 项 | 文件 | 说明 |
|---|---|---|
| 缺口 1 修复 | `OrderPriceComputeService.generateOrderItems` | 改为以**请求行**为粒度生成明细，同 SKU 多位成员各自成行 |
| 缺口 2 修复 | `OrderPriceComputeService.orderStockHandler` | 库存校验改为**按 SKU 汇总数量**比对，移除「SKU 去重数 < 明细行数」的错误判定 |
| 缺口 3 修复 | `OrderPriceComputeService.buildPromotionContext` | 营销上下文**按 SKU 聚合数量**后再算阶梯价，保证整船档位不失效 |
| 按人拆行 | `SharedCartServiceImpl.splitByMember` | 替换原 `aggregateBySku`；每条明细一个 `CreateOrderSkuReqDTO` |
| 姓名兜底链 | `SharedCartServiceImpl.resolveContributorName` | 成员填写姓名 → 商城昵称 → `用户{ID后6位}` |
| 姓名设置接口 | `PUT /order/app/shared-cart/{id}/members/me/name` | `SharedCartServiceImpl.updateMemberDisplayName` |
| 字段 | `OrderItemEntity.contributorName`、`CreateOrderSkuReqDTO.contributorName`、`SharedCartMember.displayName` | 快照字段，非外键 |
| 订单查询补列 | `OrderItemMapper.xml` | 补 select 船供与贡献者相关列（原先完全未查询，管理端看不到归属） |
| 小程序 | `shared-cart/detail.vue` | 成员姓名展示与补填入口；修正「自动合并为一条明细」的过时文案 |
| 小程序 | `order-detail/index.vue` | 明细展示「为 XX 购买」 |
| 管理端 | `order/order-info/info/index.vue` | 明细表新增「订购人」列与条件渲染的「成员备注」列 |
| SQL | `db/boot/65…sql` + `db/cloud/65…sql` | 双模式增量脚本，幂等，仅加列不新增表 |
| 全量 SQL | `build-full-sql.mjs` + `aryn_boot_full.sql` | 已登记并重新生成，`verify-full-sql.mjs` 通过 |

### 验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| 编译 | `mvn -o compile -pl aryn-order/aryn-order-biz -am` | 通过 |
| 订单域测试 | `mvn -o test -pl aryn-order/aryn-order-biz -am` | 130 用例全绿 |
| 船舶域测试 | `mvn -o test -pl aryn-vessel/aryn-vessel-biz -am` | 55 用例全绿 |
| 营销域测试 | `mvn -o test -pl aryn-promotion/aryn-promotion-biz -am` | 167 用例全绿 |
| boot 编译 | `mvn -o clean compile -pl aryn-boot -am` | 通过 |
| 小程序测试 | `npx vitest run` | 84 用例全绿 |
| 管理端单测 | `pnpm test:unit` | 472 用例全绿 |
| 产品域测试 | `mvn -o test -pl aryn-product/aryn-product-biz -am` | 52 用例全绿 |
| 船舶域测试 | `mvn -o test -pl aryn-vessel/aryn-vessel-biz -am` | 58 用例全绿 |
| 管理端类型 | `pnpm check:type` | 通过 |
| 小程序类型 | `pnpm type-check` | 通过 |
| 全量 SQL 校验 | `node db/boot/verify-full-sql.mjs` | 通过（7954 行） |

### 新增测试

- `generateOneOrderItemPerRequestRowForSameSku`：同 SKU 两条请求行 → 两条明细，贡献者姓名各自正确
- `stockCheckAggregatesQuantityAcrossRows`：6 + 4 = 10 = 库存 → 通过（历史实现必然抛错）
- `stockCheckRejectsWhenAggregatedQuantityExceedsStock`：6 + 6 > 5 → 正确拒绝
- `SharedCartServiceTest#confirmAggregatesAndCreatesOrder`：断言更新为拆行语义（3 条明细，非 2 条）

### 生命周期治理实施（§7 剩余部分，2026-09-20）

| 项 | 文件 | 说明 |
|---|---|---|
| 重复发起拦截 | `SharedCartServiceImpl.create` | 同租户同船舶已有「收集中」购物车时**复用该车**并置 `adoptedExisting=true`，前端提示「该船已在进行中，已为你打开」 |
| 并发兜底 | `db/*/66…sql` | 生成列 `active_flag` + 唯一索引 `uk_shared_cart_active(tenant_id, vessel_id, active_flag)`；建索引前先把存量重复的收集中购物车置为已关闭 |
| 24 小时有效期 | `SharedCartServiceImpl.create` | 改为**服务端计算** `now + 24h`，忽略客户端传值（客户端时钟不可信）；前端移除 48 小时/不限期选项 |
| 过期 Job | `SharedCartExpireJob` | 每 5 分钟按租户扫描，条件更新置为「已关闭」，多实例幂等 |
| 送达归档 | `OrderInfoServiceImpl.receiveOrder` | 订单签收后调 `archiveOnOrderSigned` 置「已完成」+ `completed_time`，fail-open 不阻断签收 |
| 新增状态 | `SharedCart.STATUS_COMPLETED = "6"` | 前端 `utils/shared-cart.ts` 同步：文案「本次采购已送达」，并纳入 `isCartReadonly` |
| 循环依赖处理 | `OrderInfoServiceImpl` | 注入 `ISharedCartService` 时用 `@Lazy` 断环（参照 `DeliveryAccessGuard` 既有做法） |

### 分享入群自助加入实施（§6，2026-09-20）

| 项 | 文件 | 说明 |
|---|---|---|
| 分享令牌 | `db/*/66…sql` | `shared_cart.share_token` + 唯一索引；随购物车 24 小时过期失效 |
| 生成令牌 | `POST /order/app/shared-cart/{id}/share` | 仅发起人可调用，重复调用复用同一令牌 |
| 凭令牌加入 | `POST /order/app/shared-cart/join?token=` | 校验令牌与有效期 → 幂等写成员行 → **自动补建船舶成员关系**；只回传 cartId，不回显实体 |
| 自动绑船 | `RemoteVesselService.bindMemberByShare` | Dubbo 接口，vessel 域实现；失败时 fail-open（不阻断加入，加购/下单环节仍有成员校验兜底） |
| 小程序转发 | `shared-cart/detail.vue` `onShareAppMessage` | 卡片 path 携带 `id` + `token`；发起人进入页面时**预取令牌**（微信要求转发时同步可得，无法在回调里异步请求） |
| 点击加入 | 同上 `onLoad` | 带 token 时自动 `join`，成功后直达详情；失败展示「分享链接已失效」并给出可执行出路 |
| 流转入口 | 同页成员区 | 发起人可见「邀请成员 / 分享到群」 |

**安全边界**：`SharedCartVO` 不含 `share_token`，令牌只经发起人专属接口下发；加入时校验购物车有效期与可编辑状态；已提交/已关闭/已完成的购物车拒绝新成员。

### 商品统一实施（§4，2026-09-20）

| 项 | 文件 | 说明 |
|---|---|---|
| 零售列表解禁 | `GoodsSpuMapper.xml` | 移除 `sale_scope != '2'` 过滤与 LEFT JOIN（决策：`sale_scope='2'` 历史商品**立即对所有用户可见**） |
| 统一商品池 | `ShipGoodsProfileMapper.xml` | 船供目录与个人摘要的 `INNER JOIN ship_goods_profile` 改 **LEFT JOIN**，无船供资料的商品同样可选；SKU 改为经 `goods_sku` 关联 |
| 去除范围过滤 | 同上 | 移除 `sale_scope IN ('2','3')` / `IN ('1','3')` 两处硬过滤 |
| 发布校验松绑 | `ShipProductProfileServiceImpl.validateProfile` | 从「船供可见必须有编码/采购单位/MOQ」改为**填了才校验取值合法性**；缺省按采购单位=基本单位、MOQ=1、步长=1 |
| 管理端去分裂 | `views/product/goods-spu/index.vue` | 移除「船供目录 / 返回普通列表」视图切换与船供专用查询、表格、分页；导出按钮保留为「导出商品目录」 |
| 移动端去分裂 | `components/ship-workbench/index.vue` | 移除「船供采购 / 个人购买」双入口，改为统一「去选购」；不再预设 `purchaseScene`（由结算时配送方式决定） |

> `ship-supply/index.vue` 页面**保留**，但仅服务于共享购物车选货模式（携带 `sharedCartId`）。

### 靠港信息由海员申报实施（§5，2026-09-20）

| 项 | 文件 | 说明 |
|---|---|---|
| 来源标记 | `db/*/67…sql` | `vessel_call.source`（1运营 / 2海员申报）、`declared_by`、来源索引 |
| 申报接口 | `POST /vessel/app/calls/declare` | 校验船舶成员关系 → **同船 ETA ±72 小时内复用**已有计划 → 落库并标记来源 |
| 权责拆分 | `VesselServiceImpl.declareCall` | 海员只填港口/泊位/ETA/ETD；**配送时间窗强制置空**，由运营排产时补填 |
| 运营接收 | `GET /vessel/admin/calls/declared` | 待处理申报列表；管理端靠港表新增「来源」列，海员申报带 `ElTag` 标识 |
| C 端入口 | `components/ship-context-picker/index.vue` | 无可用靠港计划时给出「申报本次靠港」按钮与表单（此前只有一句无法执行的提示） |

### 已知未覆盖

| 项 | 说明 |
|---|---|
| **端到端未验证** | 上述均为编译/单测/类型检查。订单拆行影响下单主链路（价格、库存、优惠分摊），**上线前必须真实走一遍下单流程** |
| 标签打印模板 | 数据链已就绪（拣货明细 → 订单明细 `contributor_name`），PDF/小票模板本次未实现 |
| `TenantConfigurationConsistencyTest` 为红 | **既有问题**，2026-09-19 已登记于 `docs/README.md`：`CLOUD_SCHEMAS` 未登记 `aryn-vessel-biz-dev.yml → aryn_vessel`。本次改动不新增表，与该失败无关 |

---

## 8. 双模式适配与 SQL

按 AGENTS.md 强制要求，全部 DB 改动**双份可重复执行增量脚本**：

- Boot：`aryn-mall-java/db/boot/65shared_cart_collaboration_incremental.sql`
- Cloud：`aryn-mall-java/db/cloud/65shared_cart_collaboration_incremental.sql`
- 登记进 `db/boot/build-full-sql.mjs` 的 `sections`，重新生成 `aryn_boot_full.sql` 并跑 `verify-full-sql.mjs` 校验
- 新表/新字段同步登记租户白名单（boot `application*.yml` + cloud 各 biz `application*.yml` + Nacos `3aryn_nacos.sql`）

**接口路径**：新增接口首段必须是微服务域（`/order/...`、`/vessel/...`），禁止硬编码 `/boot`；前端统一走 `parseOpenBoot` / `rewriteBootUrl`。

**Dubbo**：订单域校验船舶成员需经 `RemoteVesselService`，禁止 biz 间直接 import。

---

## 9. 验收清单

| # | 场景 | 预期 |
|---|---|---|
| 1 | 管理端商品列表 | 只有一套列表，无「船供目录」切换 |
| 2 | 未填船供资料的商品 | 船供采购场景下可下单，MOQ=1、步长=1 |
| 3 | C 端商品列表/搜索 | 不再排除任何商品，统一展示 |
| 4 | 海员申报靠港 | 申报成功，管理端「待处理申报」可见 |
| 5 | 同船重复发起 | 拦截并跳转到已有购物车 |
| 6 | 并发创建 | 数据库唯一索引兜底，仅一条成功 |
| 7 | 分享到微信群 | 卡片可转发，点击进入详情 |
| 8 | 无账号船员加入 | 一键登录即注册 → 自动绑定船舶 → 进入购物车 |
| 9 | 非本船人员尝试加购 | 被拒（船舶成员校验） |
| 10 | 24 小时过期 | Job 置为已关闭；用户操作提示「收集已截止」 |
| 11 | 发起人提交 | 生成整船订单；非发起人无提交权限 |
| 12 | 司机签收 | 购物车自动置「已完成」，船员看到「本次采购已送达」 |
| 13 | 双模式 | boot（`/boot` 前缀）与 cloud（`/order`、`/vessel` 首段）路径均正确 |
| 14 | 回归 | 普通零售下单、地址配送、优惠券、购物车行为不变 |

---

## 10. 决策记录

| # | 事项 | 决策 | 日期 |
|---|---|---|---|
| 1 | 靠港去重窗口 | **±72 小时**（同船 + 未完成状态 + ETA 在窗口内即复用，不新建） | 2026-09-20 |
| 2 | `sale_scope='2'` 历史商品 | **统一后立刻对所有用户可见**，不做运营二次确认 | 2026-09-20 |
| 3 | 加入共享购物车后的身份 | **成为正式船员**（写入 `vessel_member`，非临时参与） | 2026-09-20 |
| 5 | 订单明细粒度 | **按人拆行**（方案 A）：谁加购就按谁生成明细，不做 SKU 合并，用于配送贴标签 | 2026-09-20 |
| 6 | 标签姓名来源 | 商城填写的**收货人姓名**（`UserAddress.recipientName`），有兜底链 | 2026-09-20 |
| 4 | 支付方式（对公转账 / 账期） | **本次不覆盖**，单笔 1 万~十几万的在支付能力需单独评估 | 2026-09-20 |

### 待办事项

| # | 事项 | 状态 |
|---|---|---|
| 1 | 实施顺序：商品统一（§4）→ 靠港申报（§5）→ 分享加入（§6）→ 生命周期（§7） | 进行中 |
| 2 | 支付能力评估（对公转账 / 账期 / 大额支付限额） | 未开始 |
