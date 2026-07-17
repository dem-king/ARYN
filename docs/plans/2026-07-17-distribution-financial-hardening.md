# 分销资金链路加固 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复分销佣金、退款、提现、租户隔离和前端契约中的全部已审查问题，使资金变化可幂等、可追踪并符合不含运费的佣金口径。

**Architecture:** 支付事件只创建待结算佣金订单，到期任务或受控手工操作再释放佣金；退款通过独立记录表按退款单幂等回退一、二级佣金。提现采用加密账号和线下打款流水确认，前端只消费明确 VO。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、RocketMQ、MySQL 8、JUnit 5/Mockito、Vue 3、TypeScript、Vitest、UniApp。

---

### Task 1: 固化数据库与领域契约

**Files:**
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionUser.java`
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionOrder.java`
- Modify: `aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionWithdraw.java`
- Create: `aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionRefundRecord.java`
- Modify: `aryn-mall-java/db/boot/2aryn_boot.sql`
- Modify: `aryn-mall-java/db/cloud/9aryn_promotion.sql`
- Create: `aryn-mall-java/db/boot/13distribution_financial_hardening.sql`
- Create: `aryn-mall-java/db/cloud/13distribution_financial_hardening.sql`
- Test: `aryn-mall-java/aryn-promotion/aryn-promotion-biz/src/test/java/com/aryn/cloud/promotion/persistence/DistributionPersistenceContractTest.java`

1. 写 SQL 契约失败测试，断言三套 schema 含新增字段、退款表及租户级唯一键。
2. 运行 `mvn -pl aryn-promotion/aryn-promotion-biz -am -Dtest=DistributionPersistenceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认因字段缺失失败。
3. 更新实体、Boot/Cloud 初始化 SQL 和存量迁移 SQL，并同步 `distribution_init.sql`。
4. 重跑测试至通过，再运行 Boot SQL 生成/校验脚本。

### Task 2: 建立待结算佣金状态机

**Files:**
- Modify: `.../api/dto/DistributionSettleDTO.java`
- Modify: `.../service/IDistributionSettlementService.java`
- Modify: `.../service/impl/DistributionSettlementServiceImpl.java`
- Modify: `.../job/DistributionSettleJobHandler.java`
- Modify: `.../handler/DistributionPayHandler.java`
- Test: `.../service/impl/DistributionSettlementServiceImplTest.java`

1. 写失败测试：支付金额 110、运费 10 时佣金基数为 100；支付只增加待结算余额；到期后才增加可提现余额；欠款优先抵扣。
2. 运行聚焦测试确认旧实现立即结算导致失败。
3. 最小实现待结算订单、`settleAt` 和到期释放，定时任务读取每笔计划时间。
4. 重跑测试并清理重复金额更新逻辑。

### Task 3: 修复退款幂等和二级回退

**Files:**
- Modify: `aryn-mall-java/aryn-common/aryn-common-core/src/main/java/com/aryn/cloud/common/core/entity/OrderRefundSuccessEvent.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/event/listener/ArynOrderRefundEventListener.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/listener/ArynRefundListener.java`
- Modify: `.../handler/DistributionRefundHandler.java`
- Modify: `.../service/impl/DistributionSettlementServiceImpl.java`
- Test: `.../service/impl/DistributionRefundServiceTest.java`

1. 写失败测试：同一退款号重复处理一次、一级二级同时回退、部分退款累计不超原佣金、已提现差额进入欠款。
2. 运行测试确认旧实现重复扣减且遗漏二级佣金。
3. 扩展退款事件携带退款号和不含运费的退款基数，写退款记录后回退两层订单。
4. 重跑退款与结算测试。

### Task 4: 恢复 MQ 重试和租户上下文边界

**Files:**
- Modify: `.../listener/PromotionPayListener.java`
- Modify: `.../listener/PromotionRefundListener.java`
- Modify: `.../handler/DistributionPayHandler.java`
- Modify: `.../handler/DistributionRefundHandler.java`
- Modify: `.../job/DistributionSettleJobHandler.java`
- Test: `.../listener/PromotionListenerReliabilityTest.java`

1. 写失败测试：处理器异常必须向 RocketMQ 抛出，且成功或失败后租户上下文都为空。
2. 运行测试确认异常被吞且上下文残留。
3. 移除分销处理器吞异常逻辑，监听器使用 `try/finally` 清理并在处理失败时抛出。
4. 重跑可靠性测试。

### Task 5: 加固配置与手工结算

**Files:**
- Modify: `.../api/entity/DistributionConfig.java`
- Modify: `.../service/IDistributionConfigService.java`
- Modify: `.../service/impl/DistributionConfigServiceImpl.java`
- Modify: `.../controller/admin/DistributionConfigController.java`
- Modify: `.../controller/admin/DistributionOrderController.java`
- Test: `.../service/impl/DistributionConfigServiceImplTest.java`

1. 写失败测试：佣金比例越界、两级合计超过 1、非法周期被拒绝；新增启用配置会禁用旧配置；手工结算只能按分销订单 ID 释放待结算佣金。
2. 运行测试确认旧服务缺少约束。
3. 实现服务端校验、单启用事务和安全手工结算接口。
4. 重跑测试。

### Task 6: 完成线下打款确认和账号保护

**Files:**
- Modify: `.../api/dto/DistributionWithdrawAuditDTO.java`
- Create: `.../api/vo/DistributionWithdrawVO.java`
- Create: `.../service/WithdrawAccountCipher.java`
- Modify: `.../service/impl/DistributionWithdrawServiceImpl.java`
- Modify: `.../controller/admin/DistributionWithdrawController.java`
- Modify: `.../mapper/DistributionWithdrawMapper.xml`
- Test: `.../service/impl/DistributionWithdrawServiceImplTest.java`
- Test: `.../service/WithdrawAccountCipherTest.java`

1. 写失败测试：新申请只保存密文、列表只返回脱敏账号、确认通过必须有唯一打款流水号、拒绝会解冻余额。
2. 运行测试确认明文和无流水审批行为失败。
3. 实现 AES-256-GCM、提现 VO、线下打款确认和流水唯一约束。
4. 重跑测试；测试使用临时密钥，不提交真实密钥。

### Task 7: 防止删除资金用户并修复分享注册契约

**Files:**
- Modify: `.../service/IDistributionUserService.java`
- Modify: `.../service/impl/DistributionUserServiceImpl.java`
- Modify: `.../controller/admin/DistributionUserController.java`
- Modify: `.../controller/app/AppDistributionController.java`
- Test: `.../service/impl/DistributionUserServiceImplTest.java`

1. 写失败测试：有余额、待结算、欠款或待审核提现时禁止删除；逻辑删除用户可重新注册；已有但无邀请人的用户可首次绑定。
2. 运行测试确认无保护删除和绑定空操作。
3. 实现受控删除、有效记录唯一性和首次邀请关系绑定。
4. 重跑测试。

### Task 8: 修复管理端和 UniApp 契约

**Files:**
- Modify: `aryn-mall-ui/apps/web-ele/src/api/promotion/distribution-*.ts`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/promotion/distribution-config/index.vue`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/promotion/distribution-order/index.vue`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/promotion/distribution-withdraw/index.vue`
- Modify: `aryn-mall-uniapp/src/api/distribution/entity.ts`
- Create: `aryn-mall-uniapp/src/sub-pages/user/distribution/presentation.ts`
- Modify: `aryn-mall-uniapp/src/sub-pages/user/distribution/index.vue`
- Test: `aryn-mall-ui/apps/web-ele/src/api/promotion/distribution-contract.test.ts`

1. 写失败测试固定接口载荷、状态、正负金额、邀请人数和提现分页契约。
2. 运行 Vitest 确认旧契约失败。
3. 添加明确 TypeScript 类型，管理端补齐字段；UniApp 显式加载两个列表并使用纯展示函数。
4. 运行聚焦 Vitest、管理端类型检查和 UniApp 类型检查。

### Task 9: 全量验证与知识回写

**Files:**
- Modify: `docs/20-业务与数据/数据模型.md`
- Modify: `docs/20-业务与数据/业务流程.md`
- Modify: `docs/90-记录归档/需求记录.md`

1. 运行 promotion 全部测试与 Boot 租户一致性测试。
2. 运行 `mvn clean test -pl aryn-boot -am`；区分基线失败与本次失败。
3. 运行 `pnpm check:type && pnpm test:unit && pnpm lint`（管理端）和 `pnpm type-check`（UniApp）。
4. 运行 `git diff --check`，复核没有覆盖用户已有修改。
5. 回写数据模型、业务流程和需求记录；不提交、不推送，除非用户另行要求。
