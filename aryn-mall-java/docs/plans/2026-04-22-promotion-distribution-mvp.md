# Promotion Distribution MVP Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `aryn-promotion` 内实现分销最小可用闭环（管理端+用户端接口、归因结算、SQL与权限、编译通过）。

**Architecture:** 采用方案A，全部分销模型与服务落在 promotion api/biz。通过独立 `distribution_order` 表承载归因订单，结算服务写入订单与佣金流水，并驱动提现审核后的余额变更。接口分 admin 与 app 两套 Controller，使用 Sa-Token 权限注解与现有 MyBatis-Plus 规范。

**Tech Stack:** Spring Boot, MyBatis-Plus, Sa-Token, MyBatis XML, Lombok, Maven

---

### Task 1: 分销 API 模型定义

**Files:**
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionConfig.java`
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionUser.java`
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionOrder.java`
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionCommissionFlow.java`
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/DistributionWithdraw.java`
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/dto/DistributionWithdrawAuditDTO.java`
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/dto/DistributionSettleDTO.java`
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/dto/DistributionWithdrawApplyDTO.java`
- Create: `aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/vo/DistributionCenterVO.java`

**Step 1:** 新增实体字段并对齐公共字段（租户/审计/逻辑删除）。
**Step 2:** 新增审核、结算、提现申请 DTO 与分销中心 VO。

### Task 2: 持久层与服务层

**Files:**
- Create: `aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/mapper/Distribution*.java` (5个mapper)
- Create: `aryn-promotion/aryn-promotion-biz/src/main/resources/mapper/Distribution*.xml` (5个xml)
- Create: `aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/service/I*.java` (分销服务接口)
- Create: `aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/service/impl/*.java` (服务实现)

**Step 1:** 定义分页查询 Mapper XML（admin/app）。
**Step 2:** 实现提现申请、审核、结算写入逻辑。
**Step 3:** 增加基础状态检查与余额更新事务。

### Task 3: 管理端与用户端接口

**Files:**
- Create: `.../controller/admin/DistributionConfigController.java`
- Create: `.../controller/admin/DistributionUserController.java`
- Create: `.../controller/admin/DistributionOrderController.java`
- Create: `.../controller/admin/DistributionWithdrawController.java`
- Create: `.../controller/app/AppDistributionController.java`

**Step 1:** 管理端 CRUD 与审核接口，补齐 `@SaCheckPermission`。
**Step 2:** 用户端分销中心、佣金记录、提现申请、进度查询。
**Step 3:** 增加结算触发入口（MVP 内部可调用）。

### Task 4: SQL 与权限

**Files:**
- Modify: `db/cloud/9aryn_promotion.sql`
- Modify: `db/cloud/2aryn_upms.sql`

**Step 1:** 新增5张分销表 DDL。
**Step 2:** 新增管理端权限菜单 SQL（page/get/add/edit/del/audit）。

### Task 5: 验证与收尾

**Files:**
- Verify: `aryn-promotion` 模块编译

**Step 1:** 运行 Maven 编译并修复报错。
**Step 2:** 执行诊断检查（近期修改文件）。
**Step 3:** 汇总变更文件与剩余风险。
