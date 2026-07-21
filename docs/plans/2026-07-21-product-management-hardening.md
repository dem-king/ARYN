# Product Management Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复商品管理的租户写入、退款幂等、价格汇总、库存并发、SKU 归属、权限和多规格校验问题。

**Architecture:** 保持现有 Controller 路径和三端契约，强化商品服务写入边界；库存使用 SKU 版本与 SPU 差值原子更新；退款使用数据库唯一键持久化幂等。前端仅增加可单测的 SKU 校验工具，最终约束由后端负责。

**Tech Stack:** Spring Boot 3、MyBatis-Plus 3.5.15、MySQL 8、RocketMQ、JUnit 5、Mockito、Vue 3、TypeScript、Vitest。

---

### Task 1: 商品写入与价格校验回归测试

**Files:**
- Create: `aryn-mall-java/aryn-product/aryn-product-biz/src/test/java/com/aryn/cloud/product/service/impl/GoodsSpuServiceImplTest.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/service/impl/GoodsSpuServiceImpl.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/resources/mapper/GoodsSkuMapper.xml`

1. 为正确的原价/成本价汇总、空 SKU 拒绝、SKU 归属校验、版本冲突和服务端字段清理编写失败测试。
2. 运行 `mvn -pl aryn-product/aryn-product-biz -Dtest=GoodsSpuServiceImplTest test`，确认因现有缺陷失败。
3. 实现允许字段复制、SKU 校验和正确价格汇总。
4. 再次运行相同测试，确认通过。

### Task 2: 库存并发与 SKU 版本

**Files:**
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/test/java/com/aryn/cloud/product/service/impl/GoodsSkuServiceImplTest.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/service/impl/GoodsSkuServiceImpl.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/service/impl/GoodsSpuServiceImpl.java`

1. 增加库存扣减和恢复必须递增版本的失败测试。
2. 运行聚焦测试确认 RED。
3. 修改原子库存 SQL，并让商品编辑通过版本校验和库存差值更新 SPU。
4. 运行商品服务测试确认 GREEN。

### Task 3: 退款消息幂等

**Files:**
- Create: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/mapper/ProductRefundStockRecordMapper.java`
- Create: `aryn-mall-java/aryn-product/aryn-product-biz/src/test/java/com/aryn/cloud/product/listener/ProductRefundListenerTest.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/listener/ProductRefundListener.java`

1. 编写重复退款只恢复一次、非法消息拒绝、上下文最终清理的失败测试。
2. 运行监听器测试确认 RED。
3. 实现消费记录插入、事务和 `finally` 清理。
4. 运行监听器及商品模块测试确认 GREEN。

### Task 4: 权限修复

**Files:**
- Create: `aryn-mall-java/aryn-product/aryn-product-biz/src/test/java/com/aryn/cloud/product/controller/admin/ProductAdminPermissionTest.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/controller/admin/GoodsSpecsController.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/controller/admin/GoodsSpecsValueController.java`
- Modify: `aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/controller/admin/GoodsSpuController.java`

1. 用反射测试声明各方法所需权限并确认 RED。
2. 补齐或修正权限注解。
3. 运行权限测试确认 GREEN。

### Task 5: 前端多规格校验

**Files:**
- Create: `aryn-mall-ui/apps/web-ele/src/views/product/goods-spu/goods-spu-form-validation.ts`
- Create: `aryn-mall-ui/apps/web-ele/src/views/product/goods-spu/goods-spu-form-validation.test.ts`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/product/goods-spu/form.vue`

1. 编写空 SKU、非法字段、重复规格组合和合法数据测试。
2. 运行聚焦 Vitest 确认 RED。
3. 实现纯校验函数并接入提交逻辑。
4. 运行聚焦测试和 `pnpm check:type` 确认 GREEN。

### Task 6: Boot/Cloud SQL 与租户配置

**Files:**
- Create: `aryn-mall-java/db/cloud/19product_refund_stock_idempotency.sql`
- Create: `aryn-mall-java/db/boot/19product_refund_stock_idempotency.sql`
- Modify: `aryn-mall-java/db/cloud/8aryn_product.sql`
- Modify: `aryn-mall-java/db/boot/2aryn_boot.sql`
- Modify: `aryn-mall-java/db/boot/aryn_boot_full.sql`
- Modify: `aryn-mall-java/db/cloud/3aryn_nacos.sql`
- Modify: `aryn-mall-java/aryn-boot/src/main/resources/application.yml`

1. 添加退款消费记录表与唯一索引。
2. 将新表加入 Boot/Cloud 租户表配置。
3. 使用项目 SQL 生成脚本重建完整 Boot SQL。
4. 静态检查三份 schema 和两份增量 SQL 一致。

### Task 7: 全量验证与审查

1. 运行 `mvn test -pl aryn-product/aryn-product-biz`。
2. 在不覆盖无关改动的前提下尝试 `mvn test -pl aryn-product/aryn-product-biz -am`，如仍被无关模块阻断则明确记录。
3. 运行 `pnpm check:type` 和商品校验单元测试。
4. 检查 `git diff --check`、相关 diff 和最终工作区状态。
5. 不执行提交或推送。
