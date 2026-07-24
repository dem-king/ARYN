# 支付管理安全加固执行计划

**目标：** 修复支付查询越权、第三方回调伪造/重放、支付配置证书上传越权与路径穿越，并建立支付状态机的自动化回归测试。

**架构：** 保持现有 pay-api/pay-biz 分层，不新增跨 biz 直接依赖。C 端支付单查询以 TOC 会话用户和租户为边界；第三方回调先验签并校验商户、金额、状态，再用数据库条件更新完成持久化幂等，重复成功回调直接返回渠道成功响应。证书上传复用支付配置编辑权限，并在固定证书目录内生成安全文件名。

**技术栈：** Spring Boot 3、Sa-Token、MyBatis-Plus、JUnit 5、Mockito、MySQL 8。

---

### 任务 1：锁定支付单查询归属

**文件：**
- 修改：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/controller/app/AppPayOrderController.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java`
- 修改：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/handler/impl/*PayHandler.java`
- 测试：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/test/java/com/aryn/cloud/pay/controller/app/AppPayOrderControllerTest.java`

**步骤：**
1. 写失败测试，证明 TOB 会话和非订单用户不能查询支付单。
2. C 端入口强制 TOC 身份，并按 `out_trade_no + user_id` 查询。
3. 下单 RPC 和各支付处理器持久化可信 `user_id`。
4. 运行支付 Controller 测试，预期全部通过。

### 任务 2：加固第三方支付回调

**文件：**
- 修改：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/service/impl/PayNotifyRecordServiceImpl.java`
- 修改：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/mapper/PayTradeOrderMapper.java`
- 修改：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/resources/mapper/PayTradeOrderMapper.xml`
- 修改：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/controller/PayNotifyRecordController.java`
- 测试：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/test/java/com/aryn/cloud/pay/service/impl/PayNotifyRecordServiceImplTest.java`

**步骤：**
1. 写失败测试覆盖支付宝验签 `false`、金额/应用不一致和重复回调。
2. 严格检查验签布尔值、应用 ID、商户号、订单金额和交易状态。
3. 用 `pay_status = 0` 条件更新实现原子幂等；重复成功通知返回成功且不再发 MQ。
4. 回调处理加入本地事务，Controller 在 `finally` 清理租户上下文。
5. 运行回调服务测试，预期全部通过。

### 任务 3：限制支付证书上传

**文件：**
- 修改：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/controller/PayConfigController.java`
- 测试：`aryn-mall-java/aryn-pay/aryn-pay-biz/src/test/java/com/aryn/cloud/pay/controller/PayConfigControllerTest.java`

**步骤：**
1. 写失败测试覆盖缺少权限、路径穿越文件名、非法扩展名和超大文件。
2. 上传端点增加 `pay:payconfig:edit` 权限。
3. 仅允许有限证书扩展名和大小，在规范化后的 `cert-dir` 下生成随机文件名。
4. 运行 Controller 测试，预期全部通过。

### 任务 4：补齐数据库幂等约束

**文件：**
- 新增：`aryn-mall-java/db/boot/24payment_security_hardening.sql`
- 新增：`aryn-mall-java/db/cloud/24payment_security_hardening.sql`
- 修改：`aryn-mall-java/db/boot/2aryn_boot.sql`
- 修改：`aryn-mall-java/db/cloud/6aryn_pay.sql`

**步骤：**
1. 增加支付单、退款单和成功通知的租户内唯一约束迁移。
2. 同步 Boot/Cloud 基线结构，迁移前只报告重复数据，不自动删除资金记录。
3. 静态核对两种部署模式的索引定义一致。

### 任务 5：验证并沉淀结论

**文件：**
- 修改：`docs/40-接口与风险/缺失模块清单.md`
- 修改：`docs/90-记录归档/需求记录.md`

**步骤：**
1. 运行支付模块定向测试与编译。
2. 运行 `mvn test -pl aryn-boot -am`，如遇无关既有失败则单独记录。
3. 回写支付回调、归属和迁移约束，完成后将本计划移入归档目录。
