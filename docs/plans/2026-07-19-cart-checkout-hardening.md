# Cart And Checkout Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复购物车和下单链路中的越权、库存超卖、优惠券重复占用、重复下单、状态竞态和异步一致性问题。

**Architecture:** 保持现有三端 URL 和模块边界，使用用户归属条件、数据库条件更新和唯一键建立并发边界。订单创建由 Seata 全局事务协调订单、库存和优惠券，购物车在事务内按 SKU 清理；延迟取消消息在事务提交后发送，现有超时扫描作为投递失败兜底。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、Seata、Dubbo、MySQL 8、Vue 3/UniApp、JUnit 5、Mockito

**Status:** 已完成。后端全量测试和 SQL 静态校验通过；真实 MySQL 8 存量迁移及 RocketMQ/Seata 多进程联调保留为上线前环境验收项。

---

### Task 1: 固化请求契约和用户归属（已完成）

**Files:**
- Modify: `aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/dto/*.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/*.java`
- Modify: `aryn-mall-java/aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/controller/app/AppUserAddressController.java`

1. 为结算、下单、预支付和购物车写请求增加 Bean Validation。
2. 所有 C 端按 ID/订单号操作增加当前 `userId` 条件。
3. 地址远程查询改为同时接收地址 ID 和用户 ID。
4. 添加越权和非法数量契约测试，先确认旧实现失败，再实现通过。

### Task 2: 加固购物车并发和服务端快照（已完成）

**Files:**
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/ShoppingCartServiceImpl.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/IShoppingCartService.java`
- Test: `aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/service/impl/ShoppingCartServiceImplTest.java`

1. 新增和换规格时从商品服务读取上架 SKU，不信任客户端价格和商品快照。
2. 修改、删除都按 `id + userId` 更新。
3. 下单后按 SKU 清理，避免删除同 SPU 的其他规格。
4. 使用数据库唯一键兜底并发添加，冲突时合并数量。

### Task 3: 原子库存、优惠券和下单幂等（已完成）

**Files:**
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/service/impl/GoodsSkuServiceImpl.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/service/impl/GoodsSpuServiceImpl.java`
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/remote/RemoteCouponUserService.java`
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/service/impl/CouponUserServiceImpl.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java`

1. 库存扣减条件改为 `stock >= quantity`，服务端拒绝非正数量并聚合重复 SKU/SPU。
2. 优惠券占用改为 `id + userId + status=待使用 + 未过期` 的单条条件更新，并绑定订单 ID。
3. 取消订单只释放绑定到该订单的占用券。
4. 新增 `requestId`，先查已有订单并由唯一键阻止并发重复订单。

### Task 4: 修复订单状态机和事务后动作（已完成）

**Files:**
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/event/listener/ArynOrderCreateAfterEventListener.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/job/OrderJobHandler.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/listener/OrderCancelListener.java`

1. 取消、收货使用状态条件更新，避免与支付并发覆盖。
2. 仅允许用户删除本人未支付且已取消订单，并在同一事务删除订单项。
3. 收货时持久化订单项完成状态，完成消息在提交后发送。
4. 创建后监听器只负责提交后发送延迟取消消息，租户从订单快照读取。
5. 自动收货查询待收货，自动评价查询已完成，并在 `finally` 清理租户上下文。

### Task 5: 数据库、移动端和知识库同步（已完成）

**Files:**
- Create: `aryn-mall-java/db/boot/16order_checkout_hardening.sql`
- Create: `aryn-mall-java/db/cloud/16order_checkout_hardening.sql`
- Create: `aryn-mall-java/db/boot/17order_appraise_hardening.sql`
- Create: `aryn-mall-java/db/cloud/17order_appraise_hardening.sql`
- Create: `aryn-mall-java/db/boot/18product_pay_consumer_idempotency.sql`
- Create: `aryn-mall-java/db/cloud/18product_pay_consumer_idempotency.sql`
- Modify: `aryn-mall-java/db/boot/2aryn_boot.sql`
- Modify: `aryn-mall-java/db/cloud/7aryn_order.sql`
- Modify: `aryn-mall-java/db/boot/aryn_boot_full.sql`
- Modify: `aryn-mall-uniapp/src/sub-pages/order/order-confirm/index.vue`
- Modify: `docs/20-业务与数据/数据模型.md`
- Modify: `docs/40-接口与风险/缺失模块清单.md`
- Modify: `docs/90-记录归档/需求记录.md`

1. 增加订单幂等号、有效购物车 SKU 生成列和唯一索引；迁移前逻辑删除存量重复购物车。
2. 移动端确认页生成稳定 `requestId`，成功离页后自然失效。
3. 更新稳定规则和需求记录。
4. 运行订单、商品、促销、用户和 Boot 测试，再运行 UniApp 类型检查。
5. 评价身份字段由服务端订单项覆盖，同一订单项唯一；支付成功消息按租户和订单持久化消费幂等。
