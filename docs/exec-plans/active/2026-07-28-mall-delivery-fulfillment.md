# 商城配送履约实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. 本仓库禁止创建、使用或切换 Git worktree，必须在当前工作目录执行，并保护所有既有未提交改动。

**目标：** 实现商城配送第三种履约方式，使管理员可指定配送员，配送员在独立小程序完成配货、送达或异常上报，客户确认或超时后完成订单。

**架构：** 配送任务作为 `aryn-order` 内的独立子域，和第三方快递 `order_delivery` 分表、分状态机。UPMS 提供配送员、员工微信绑定和受控素材能力；消息域保存员工通知事实并扩展微信订阅通道；客户商城、管理后台和独立配送 UniApp 通过各自的 TOC/TOB 接口访问。

**技术栈：** Spring Boot 3、MyBatis-Plus、Dubbo、RocketMQ、Redis、XXL-JOB、Sa-Token、MySQL 8、Vue 3、Vite、TypeScript、Element Plus、UniApp、Alova、Pinia、Vitest、微信小程序。

---

## 执行前约束

- 当前分支为 `dev`，远端为 `origin`；不得创建 worktree。
- 每个任务开始前运行 `git status --short --branch`，确认用户改动未变化。
- 当前已知无关路径不得暂存：

```text
aryn-mall-java/aryn-gateway/pom.xml
.codex-start/
aryn-mall-java/aryn-boot/aryn/
aryn-mall-uniapp/pnpm-lock.local-backup-20260722.yaml
docs/代码审查报告-2026-07-21.md
```

- 修改实体、认证、菜单和公共存储前先运行 Graphify 影响检查；若工具不可用，在需求包的核对结论中记录搜索路径、替代证据和未覆盖风险。
- 所有提交标题和正文使用中文；每次提交前运行 `git diff --cached --name-only` 并逐项核对。
- 实施采用 TDD：先写失败测试，确认失败原因正确，再写最小实现。

### 任务 1：建立配送 SQL 契约和基础表

**文件：**

- 创建：`aryn-mall-java/db/boot/21mall_delivery.sql`
- 创建：`aryn-mall-java/db/cloud/21order_delivery_task.sql`
- 创建：`aryn-mall-java/db/cloud/21delivery_staff_wechat.sql`
- 创建：`aryn-mall-java/db/cloud/21message_channel_task.sql`
- 创建：`aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/order/MallDeliverySqlContractTest.java`
- 修改：`aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantConfigurationConsistencyTest.java`

**步骤 1：写失败的 SQL 契约测试**

测试必须断言以下表和关键索引同时出现在 Boot/Cloud SQL：

```text
order_delivery_task
order_delivery_task_item
order_delivery_evidence
order_delivery_task_log
order_delivery_area
sys_user_wechat_binding
message_channel_task
uk_delivery_task_order (tenant_id, order_id)
uk_delivery_task_no (tenant_id, task_no)
uk_delivery_task_item (tenant_id, task_id, order_item_id)
```

同时断言每张业务表包含 `tenant_id`、`del_flag`，任务表包含 `attempt_no`、`version`、`delivered_at`。

**步骤 2：运行测试确认失败**

```powershell
cd aryn-mall-java
mvn -pl aryn-boot -am "-Dtest=MallDeliverySqlContractTest,TenantConfigurationConsistencyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期：因 SQL 文件和租户表登记尚不存在而失败。

**步骤 3：创建最小增量 SQL**

- `order_delivery_task.status` 使用字符串状态，不复用快递状态字典。
- 一张订单永久只有一个任务，唯一索引不依赖逻辑删除条件。
- 日志表增加 `request_id`，并建立 `tenant_id + task_id + action + request_id` 唯一键用于命令幂等。
- 凭证表只保存 `material_id` 和 `binding_status`，不保存客户端 URL。
- `sys_material` 增加可空的 `object_key`、`business_type`、`business_id`、`binding_status`、`reservation_id`、`reservation_expire_time`、`bound_time`，兼容历史素材。
- `message_channel_task` 建立来源幂等键和待重试索引。

**步骤 4：登记租户表并重新运行测试**

预期：SQL 契约和租户一致性测试通过；若 Nacos 配置测试同时要求 Cloud 表登记，同步更新对应配置源而不是绕过测试。

**步骤 5：提交**

```powershell
git add aryn-mall-java/db/boot/21mall_delivery.sql aryn-mall-java/db/cloud/21order_delivery_task.sql aryn-mall-java/db/cloud/21delivery_staff_wechat.sql aryn-mall-java/db/cloud/21message_channel_task.sql aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/order/MallDeliverySqlContractTest.java aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantConfigurationConsistencyTest.java
git diff --cached --name-only
git commit -m "新增商城配送数据模型"
```

### 任务 2：实现配送领域类型和状态转换策略

**文件：**

- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/enums/DeliveryTaskStatusEnum.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/enums/DeliveryTaskActionEnum.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/enums/DeliveryEvidenceTypeEnum.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/entity/OrderDeliveryTask.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/entity/OrderDeliveryTaskItem.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/entity/OrderDeliveryEvidence.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/entity/OrderDeliveryTaskLog.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/entity/OrderDeliveryArea.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/service/DeliveryTaskTransitionPolicy.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/delivery/service/DeliveryTaskTransitionPolicyTest.java`

**步骤 1：写状态机失败测试**

覆盖合法路径：

```java
assertThat(policy.canTransit(WAITING_ASSIGNMENT, ASSIGNED)).isTrue();
assertThat(policy.canTransit(ASSIGNED, PICKING)).isTrue();
assertThat(policy.canTransit(PICKING, DELIVERING)).isTrue();
assertThat(policy.canTransit(DELIVERING, DELIVERED)).isTrue();
assertThat(policy.canTransit(DELIVERING, RETURN_PENDING)).isTrue();
assertThat(policy.canTransit(RETURN_PENDING, CLOSED)).isTrue();
```

覆盖非法路径：`DELIVERED -> ASSIGNED`、`CLOSED -> PICKING`、`WAITING_ASSIGNMENT -> DELIVERED`。

**步骤 2：运行单测确认失败**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskTransitionPolicyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：实现枚举、实体和纯状态策略**

状态枚举固定为：

```java
WAITING_ASSIGNMENT, ASSIGNED, PICKING, DELIVERING,
DELIVERED, EXCEPTION, RETURN_PENDING, CLOSED
```

策略类只回答状态转换，不查询数据库、不发送消息，便于后续服务复用。

**步骤 4：运行测试并提交**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskTransitionPolicyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-order
git diff --cached --name-only
git commit -m "建立商城配送状态机"
```

### 任务 3：实现配送范围与结算校验

**文件：**

- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/mapper/OrderDeliveryAreaMapper.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/resources/mapper/OrderDeliveryAreaMapper.xml`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/service/DeliveryAreaService.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/controller/admin/DeliveryAreaAdminController.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/controller/app/AppDeliveryAvailabilityController.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/delivery/service/DeliveryAreaServiceTest.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/constant/MallOrderConstants.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/dto/CreateOrderDTO.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/dto/SettlementOrderDTO.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/entity/OrderInfo.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/service/impl/CheckoutRequestValidationTest.java`

**步骤 1：写失败测试**

- DTO 接受 `1/2/3`，拒绝其他值。
- 无配送范围配置时 `3` 不可用。
- 区县规则优先于城市规则，禁用规则不匹配。
- `deliveryWay = 3` 必须读取本人地址、复制收货快照并调用现有运费计算。
- 创建订单会重新校验，不能只依赖 availability 接口。

**步骤 2：运行测试确认失败**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryAreaServiceTest,CheckoutRequestValidationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：提取共享地址和运费逻辑**

将“加载本人地址、写入收货快照、计算运费”提取为一个私有/包内服务，供快递和商城配送共用：

```java
if (DELIVERY_WAY_1.equals(deliveryWay) || DELIVERY_WAY_3.equals(deliveryWay)) {
    UserAddress address = requireOwnedAddress(addressId, userId);
    if (DELIVERY_WAY_3.equals(deliveryWay)) {
        deliveryAreaService.requireAvailable(address);
    }
    applyRecipientSnapshot(order, address);
    orderPriceComputeService.orderFreightHandler(...);
}
```

不要复制两份结算和创建逻辑。

**步骤 4：运行测试并提交**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryAreaServiceTest,CheckoutRequestValidationTest,OrderPriceComputeServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-order
git diff --cached --name-only
git commit -m "支持商城配送范围与结算"
```

### 任务 4：支付成功幂等创建配送任务

**文件：**

- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/mapper/OrderDeliveryTaskMapper.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/mapper/OrderDeliveryTaskItemMapper.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/resources/mapper/OrderDeliveryTaskMapper.xml`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/service/DeliveryTaskCreationService.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/delivery/service/DeliveryTaskCreationServiceTest.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/event/listener/ArynOrderPayEventListener.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/listener/ArynOrderListenerReliabilityTest.java`

**步骤 1：写失败测试**

- 商城配送支付成功创建 `WAITING_ASSIGNMENT` 任务和每个订单项的一条配货明细。
- 快递和自提不创建配送任务。
- 相同 `tenant_id + order_id` 重试读取已存在任务，不重复插入明细。
- 任务或明细插入失败导致支付事件事务回滚。

**步骤 2：运行测试确认失败**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskCreationServiceTest,ArynOrderListenerReliabilityTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：实现同库事务内创建**

在支付监听器成功更新订单和订单项后、注册事务后 MQ 通知前调用创建服务。通过数据库唯一键处理并发重复，而不是先 `count` 再插入。

**步骤 4：运行测试并提交**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskCreationServiceTest,ArynOrderListenerReliabilityTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-order
git diff --cached --name-only
git commit -m "支付后生成商城配送任务"
```

### 任务 5：提供配送员资格和候选查询

**文件：**

- 创建：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/dto/DeliveryStaffQuery.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/vo/DeliveryStaffVO.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/vo/DeliveryStaffPageVO.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/remote/RemoteDeliveryStaffService.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/dubbo/RemoteDeliveryStaffServiceImpl.java`
- 修改：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/mapper/SysUserMapper.java`
- 修改：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/resources/mapper/SysUserMapper.xml`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/dubbo/RemoteDeliveryStaffServiceImplTest.java`

**步骤 1：写失败测试**

- 只返回当前租户、未删除、状态正常且通过角色菜单拥有 `order:delivery:execute` 的员工。
- 查询携带不匹配租户时拒绝。
- 候选 VO 不返回密码、完整权限集合或无关个人信息。
- `isEligible` 对空 ID、停用账号和跨租户均返回 false/抛出租户错误。

**步骤 2：运行测试确认失败**

```powershell
mvn -pl aryn-upms/aryn-upms-biz -am "-Dtest=RemoteDeliveryStaffServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：实现权限驱动查询**

不要硬编码角色主键；通过角色拥有的权限标识判断配送资格。查询中的每张租户表都使用别名限定 `tenant_id`，避免拦截器改写产生歧义。

**步骤 4：运行测试并提交**

```powershell
mvn -pl aryn-upms/aryn-upms-biz -am "-Dtest=RemoteDeliveryStaffServiceImplTest,RemoteMessageStaffServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-upms
git diff --cached --name-only
git commit -m "提供商城配送员资格查询"
```

### 任务 6：实现管理端派单、改派和异常处理接口

**文件：**

- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/dto/DeliveryAssignRequest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/dto/DeliveryReassignRequest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/dto/DeliveryCloseRequest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/dto/DeliveryReturnRequest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/vo/DeliveryTaskAdminVO.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/mapper/OrderDeliveryTaskLogMapper.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/service/DeliveryTaskAdminService.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/controller/admin/DeliveryTaskAdminController.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/delivery/service/DeliveryTaskAdminServiceTest.java`

**步骤 1：写失败测试**

- 派单前调用远程资格校验。
- 首次派单只允许 `WAITING_ASSIGNMENT`。
- 改派只允许 `ASSIGNED/PICKING/EXCEPTION`，原因必填，`attempt_no + 1`。
- 已送达、待退回、已关闭不能改派。
- 相同版本并发请求只有一条更新成功。
- 每次动作写入追加日志，日志失败使状态事务回滚。

**步骤 2：运行测试确认失败**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskAdminServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：实现条件更新和权限**

Controller 使用 `@SaCheckPermission` 区分 page/get/assign/reassign/exception/return。Service 把当前管理员 ID/名称从 TOB 安全上下文写入日志，不接受客户端操作人字段。

**步骤 4：运行测试并提交**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskAdminServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-order
git diff --cached --name-only
git commit -m "实现商城配送派单管理"
```

### 任务 7：实现配送员配货、取货、送达和异常接口

**文件：**

- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/dto/DeliveryItemCheckRequest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/dto/DeliveryPickupRequest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/dto/DeliveryCompleteRequest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/dto/DeliveryExceptionRequest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/delivery/vo/DeliveryTaskStaffVO.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/service/DeliveryTaskStaffService.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/controller/staff/DeliveryTaskStaffController.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/delivery/service/DeliveryTaskStaffServiceTest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/delivery/controller/DeliveryStaffIdentityContractTest.java`

**步骤 1：写失败测试**

- Controller 显式调用 `SecurityUtils.requireUser(DeviceTypeEnum.TOB)`。
- 列表和详情始终约束当前租户和 `assignee_id`。
- 改派后原配送员所有详情和写操作立即失败。
- 未全部勾选不能取货。
- 取货事务把任务置为 `DELIVERING`、订单置为 `WAITING_FOR_RECEIPT`、订单项置为 `SHIPPED`。
- 送达要求 1 至 6 个素材 ID；异常说明必填、图片可选。
- 相同 `requestId` 重试返回原结果，不重复写日志或凭证。

**步骤 2：运行测试确认失败**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskStaffServiceTest,DeliveryStaffIdentityContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：实现员工接口**

所有写入先校验状态策略，再使用 `tenant_id + assignee_id + status + version` 条件更新。隐私字段仅在当前本人进行中的详情 VO 返回；列表 VO 对电话和详细地址做最小化。

**步骤 4：运行测试并提交**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskStaffServiceTest,DeliveryStaffIdentityContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-order
git diff --cached --name-only
git commit -m "实现配送员履约接口"
```

### 任务 8：建立配送凭证安全上传和短期访问

**前置技能：** 实施前使用 Graphify 检查 `aryn-common-storage`、`SysUploadController` 和 `SysMaterial` 的调用方。

**文件：**

- 创建：`aryn-mall-java/aryn-common/aryn-common-storage/src/main/java/com/aryn/cloud/common/storage/entity/StoredObject.java`
- 修改：`aryn-mall-java/aryn-common/aryn-common-storage/src/main/java/com/aryn/cloud/common/storage/handler/ArynUploadFileHandler.java`
- 修改：`aryn-mall-java/aryn-common/aryn-common-storage/src/main/java/com/aryn/cloud/common/storage/handler/impl/S3UploadFileHandler.java`
- 修改：`aryn-mall-java/aryn-common/aryn-common-storage/src/main/java/com/aryn/cloud/common/storage/handler/impl/LocalUploadFileHandler.java`
- 修改：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/entity/SysMaterial.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/remote/RemoteMaterialAccessService.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/vo/MaterialAccessVO.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/controller/StaffDeliveryEvidenceController.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/dubbo/RemoteMaterialAccessServiceImpl.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/job/TemporaryDeliveryMaterialCleanupJob.java`
- 修改：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/support/UploadFileValidator.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/support/DeliveryEvidenceUploadTest.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/dubbo/RemoteMaterialAccessServiceImplTest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/job/DeliveryEvidenceBindingRecoveryJob.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/delivery/job/DeliveryEvidenceBindingRecoveryJobTest.java`

**步骤 1：写失败测试**

- 配送上传只接受真实 JPEG/PNG，拒绝扩展名伪装、GIF/BMP/WebP 和超过 10 MB。
- 上传接口要求 TOB 且拥有 `order:delivery:execute`。
- 返回 `materialId`，素材保存 `object_key`、业务类型、当前员工和租户。
- 订单提交时只能绑定本人未占用素材；跨租户、跨员工、重复绑定被拒绝。
- 同一素材并发预占时只有一个 `reservationId` 成功；同一预占重试幂等。
- 临时访问描述有明确过期时间，不返回存储密钥。

**步骤 2：运行测试确认失败**

```powershell
mvn -pl aryn-upms/aryn-upms-biz -am "-Dtest=DeliveryEvidenceUploadTest,RemoteMaterialAccessServiceImplTest,UploadFileValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryEvidenceBindingRecoveryJobTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：保持旧上传 API 兼容**

为存储处理器增加返回对象键的新方法，旧 `uploadFile(...) -> String` 保持兼容并委托新方法。不要一次性修改所有历史调用方。配送专用接口使用新方法并持久化对象键。

**步骤 4：接入订单凭证绑定**

修改 `DeliveryTaskStaffService`，按以下顺序处理跨库一致性：

1. 以 `taskId + requestId` 为 `reservationId`，通过 Dubbo 原子预占本人素材。
2. 订单本地事务写入 `binding_status = PENDING` 的凭证并更新任务状态。
3. 本地事务提交后幂等确认 UPMS 素材绑定，再把凭证更新为 `BOUND`。
4. 确认失败时保留 `PENDING` 事实，由订单域恢复任务重试；本地事务回滚且客户端不再重试的预占由 UPMS 超时清理。

不得使用“先查询素材未占用，再分别更新”的竞态实现，也不得用分布式事务掩盖失败。

**步骤 5：运行测试并提交**

```powershell
mvn -pl aryn-upms/aryn-upms-biz -am "-Dtest=DeliveryEvidenceUploadTest,RemoteMaterialAccessServiceImplTest,UploadFileValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskStaffServiceTest,DeliveryEvidenceBindingRecoveryJobTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-common/aryn-common-storage aryn-mall-java/aryn-upms aryn-mall-java/aryn-order
git diff --cached --name-only
git commit -m "加固商城配送凭证访问"
```

### 任务 9：联动退款退回和自动确认收货

**文件：**

- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderRefundServiceImpl.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/listener/ArynRefundListener.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/job/OrderJobHandler.java`
- 修改：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/delivery/service/DeliveryRefundBoundaryTest.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/job/OrderReceiverJobDeliveryTest.java`

**步骤 1：写失败测试**

- 取货前全部退款完成关闭任务。
- 取货后提交任一退款申请时，整张任务条件更新为 `RETURN_PENDING`，并阻止并发提交送达。
- 取货后审批退款时，任务未完成退回确认则拒绝调用支付退款。
- 管理员确认退回后允许退款，重复回调不重复关闭任务。
- 快递订单自动确认继续使用 `deliver_time`。
- 商城配送只有 `DELIVERED` 且 `delivered_at` 超时才自动确认。
- 配送中时间再长也不能被自动确认。

**步骤 2：运行测试确认失败**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryRefundBoundaryTest,OrderReceiverJobDeliveryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：实现退款门禁和分支查询**

在 `OrderRefundServiceImpl.saveRefund` 中完成取货后任务的 `RETURN_PENDING` 条件更新，在 `refund` 的服务端放款路径校验管理员已经确认退回；两处都不能只依赖 Controller。自动确认查询按配送方式拆分，商城配送使用任务 `delivered_at`，不要给 `order_info` 再增加语义重复字段。

**步骤 4：运行测试并提交**

```powershell
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryRefundBoundaryTest,OrderReceiverJobDeliveryTest,ArynOrderListenerReliabilityTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-order
git diff --cached --name-only
git commit -m "联动配送退回与订单完成"
```

### 任务 10：扩展员工站内通知和微信订阅通道

**文件：**

- 修改：`aryn-mall-java/aryn-message/aryn-message-api/src/main/java/com/aryn/cloud/message/api/dto/MessageSendCommand.java`
- 创建：`aryn-mall-java/aryn-message/aryn-message-api/src/main/java/com/aryn/cloud/message/api/entity/MessageChannelTask.java`
- 创建：`aryn-mall-java/aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/service/MessageChannelService.java`
- 创建：`aryn-mall-java/aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/service/impl/WechatSubscribeChannelService.java`
- 创建：`aryn-mall-java/aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/job/MessageChannelRecoveryJob.java`
- 修改：`aryn-mall-java/aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/service/impl/MessageCommandServiceImpl.java`
- 创建：`aryn-mall-java/aryn-message/aryn-message-biz/src/test/java/com/aryn/cloud/message/service/WechatSubscribeChannelServiceTest.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/entity/SysUserWechatBinding.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/remote/RemoteStaffWechatBindingService.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/controller/StaffWechatBindingController.java`
- 创建：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/dubbo/RemoteStaffWechatBindingServiceImpl.java`
- 创建：`aryn-mall-java/aryn-user/aryn-user-api/src/main/java/com/aryn/cloud/user/api/remote/RemoteMiniAppGateway.java`
- 创建：`aryn-mall-java/aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/dubbo/RemoteMiniAppGatewayImpl.java`
- 创建：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/service/DeliveryAssignmentNotifier.java`

**步骤 1：写失败测试**

- 派单事件 ID 使用 `delivery-assigned:{taskId}:{attemptNo}`，同一尝试只生成一条员工通知。
- 站内通知事务提交后才建立微信通道任务。
- 未绑定/未授权是终止状态，不无限重试；网络、5xx、限流是可重试状态。
- 重试按退避时间执行，同一通道幂等键不重复创建任务。
- jsCode 交换只返回 openid，不创建 TOC 会员。
- 绑定接口要求当前 TOB 员工且配送权限有效。

**步骤 2：运行测试确认失败**

```powershell
mvn -pl aryn-message/aryn-message-biz -am "-Dtest=WechatSubscribeChannelServiceTest,MessageSendCommandListenerTest,MessagePushServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：实现通道扩展**

`MessageSendCommand.channels` 缺省为仅 `IN_APP`，保证现有调用方兼容。模板参数使用服务端白名单构造，不允许订单域传任意微信字段或跳转 URL。真实 `appSecret` 只在用户域读取。

**步骤 4：接入派单事务后通知**

`DeliveryTaskAdminService` 成功提交派单/改派后调用 notifier 发送 RocketMQ 命令。MQ 发送失败记录结构化错误和可恢复事实，不回滚已提交派单。

**步骤 5：运行测试并提交**

```powershell
mvn -pl aryn-message/aryn-message-biz -am "-Dtest=WechatSubscribeChannelServiceTest,MessageSendCommandListenerTest,MessagePushServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl aryn-order/aryn-order-biz -am "-Dtest=DeliveryTaskAdminServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add aryn-mall-java/aryn-message aryn-mall-java/aryn-upms aryn-mall-java/aryn-user aryn-mall-java/aryn-order
git diff --cached --name-only
git commit -m "接入配送任务多通道提醒"
```

### 任务 11：实现管理后台配送页面

**前置技能：** 使用 `vue-best-practices` 和 `ui-ux-pro-max`，保持 Vue 3 `<script setup lang="ts">` 与现有 Element Plus 规范。

**文件：**

- 创建：`aryn-mall-ui/apps/web-ele/src/api/order/delivery-task.ts`
- 创建：`aryn-mall-ui/apps/web-ele/src/api/order/delivery-area.ts`
- 创建：`aryn-mall-ui/apps/web-ele/src/views/order/delivery-task/index.vue`
- 创建：`aryn-mall-ui/apps/web-ele/src/views/order/delivery-task/detail.vue`
- 创建：`aryn-mall-ui/apps/web-ele/src/views/order/delivery-task/components/AssignDialog.vue`
- 创建：`aryn-mall-ui/apps/web-ele/src/views/order/delivery-task/components/ExceptionDialog.vue`
- 创建：`aryn-mall-ui/apps/web-ele/src/views/order/delivery-area/index.vue`
- 创建：`aryn-mall-ui/apps/web-ele/src/views/order/delivery-task/delivery-task.spec.ts`
- 修改：`aryn-mall-ui/apps/web-ele/src/api/order/order-info.ts`
- 修改：`aryn-mall-ui/apps/web-ele/src/views/order/order-info/index.vue`
- 修改：`aryn-mall-ui/apps/web-ele/src/views/order/order-info/info/index.vue`

**步骤 1：写失败测试**

- 商城配送待发货订单显示“派单”，不显示快递发货弹窗。
- 快递、自提既有按钮条件不变。
- 派单/改派请求携带版本和原因，成功后刷新任务。
- 页面操作受对应权限码控制。
- 已送达凭证使用短期地址并支持过期刷新。

**步骤 2：运行测试确认失败**

```powershell
cd aryn-mall-ui
pnpm.cmd test:unit -- apps/web-ele/src/views/order/delivery-task/delivery-task.spec.ts
```

**步骤 3：实现 API、列表、详情和对话框**

任务状态使用分段控件；派单和改派使用同一个窄职责对话框，通过 props 明确模式。不要把页面区段包成层层嵌套卡片。

**步骤 4：运行聚焦验证并提交**

```powershell
pnpm.cmd test:unit -- apps/web-ele/src/views/order/delivery-task/delivery-task.spec.ts
pnpm.cmd check:type
git add aryn-mall-ui/apps/web-ele/src/api/order aryn-mall-ui/apps/web-ele/src/views/order
git diff --cached --name-only
git commit -m "新增商城配送管理页面"
```

### 任务 12：接入客户商城配送选择和进度

**前置技能：** 使用 `vue-best-practices`。

**文件：**

- 创建：`aryn-mall-uniapp/src/api/order/mallDelivery.ts`
- 创建：`aryn-mall-uniapp/src/sub-pages/order/components/delivery-progress/index.vue`
- 创建：`aryn-mall-uniapp/src/sub-pages/order/mall-delivery.spec.ts`
- 修改：`aryn-mall-uniapp/src/sub-pages/order/order-confirm/components/ShopOrderItem.vue`
- 修改：`aryn-mall-uniapp/src/sub-pages/order/order-confirm/index.vue`
- 修改：`aryn-mall-uniapp/src/sub-pages/order/order-detail/index.vue`
- 修改：`aryn-mall-uniapp/src/sub-pages/order/order-list/index.vue`
- 修改：`aryn-mall-uniapp/src/api/order/orderInfo.ts`

**步骤 1：写失败测试**

- 地址在范围内时出现商城配送，不可用时显示原因且不能提交。
- 地址或配送方式变化后重新结算。
- `deliveryWay = 3` 显示现有运费。
- 客户进度过滤内部日志、完整配送员电话和内部异常说明。
- 快递和自提展示回归不变。

**步骤 2：运行测试确认失败**

```powershell
cd aryn-mall-uniapp
pnpm.cmd test:unit -- src/sub-pages/order/mall-delivery.spec.ts
```

**步骤 3：实现最小客户流程**

客户端 availability 只改善交互，最终创建失败必须展示服务端错误。凭证图片只保存 `evidenceId`，每次进入详情请求短期访问地址。

**步骤 4：运行验证并提交**

```powershell
pnpm.cmd test:unit -- src/sub-pages/order/mall-delivery.spec.ts
pnpm.cmd type-check
git add aryn-mall-uniapp/src/api/order aryn-mall-uniapp/src/sub-pages/order
git diff --cached --name-only
git commit -m "客户商城接入商城配送"
```

### 任务 13：搭建独立配送 UniApp 和 TOB 身份边界

**前置技能：** 使用 `vue-best-practices` 和 `ui-ux-pro-max`。

**文件：**

- 创建：`aryn-mall-delivery-uniapp/package.json`
- 创建：`aryn-mall-delivery-uniapp/pnpm-lock.yaml`
- 创建：`aryn-mall-delivery-uniapp/tsconfig.json`
- 创建：`aryn-mall-delivery-uniapp/vite.config.ts`
- 创建：`aryn-mall-delivery-uniapp/vitest.config.ts`
- 创建：`aryn-mall-delivery-uniapp/src/manifest.json`
- 创建：`aryn-mall-delivery-uniapp/src/pages.json`
- 创建：`aryn-mall-delivery-uniapp/src/App.vue`
- 创建：`aryn-mall-delivery-uniapp/src/main.ts`
- 创建：`aryn-mall-delivery-uniapp/src/api/core/instance.ts`
- 创建：`aryn-mall-delivery-uniapp/src/api/auth.ts`
- 创建：`aryn-mall-delivery-uniapp/src/store/auth.ts`
- 创建：`aryn-mall-delivery-uniapp/src/store/auth.spec.ts`
- 创建：`aryn-mall-delivery-uniapp/src/pages/login/index.vue`
- 创建：`aryn-mall-delivery-uniapp/.env.example`

**步骤 1：建立最小项目并写失败认证测试**

- Token 使用独立存储键，不能读取客户商城 Token。
- 请求头发送 `satoken`、`tenant-id` 和配送小程序 `app-id`。
- 登录后验证 TOB 员工资料和配送权限，无权限立即退出。
- AppID 和 API 地址从环境配置读取，不提交真实密钥。

**步骤 2：安装依赖并确认测试先失败**

```powershell
cd aryn-mall-delivery-uniapp
pnpm.cmd install
pnpm.cmd test:unit -- src/store/auth.spec.ts
```

**步骤 3：实现最小登录和请求层**

复用客户 UniApp 的技术版本和请求模式，但不复制 TOC 登录、会员 Store、购物车或营销页面。第一屏直接进入配送工作台或登录页，不创建营销首页。

**步骤 4：运行验证并提交**

```powershell
pnpm.cmd test:unit -- src/store/auth.spec.ts
pnpm.cmd type-check
git add aryn-mall-delivery-uniapp
git diff --cached --name-only
git commit -m "搭建独立配送员小程序"
```

### 任务 14：实现配送小程序完整工作流

**前置技能：** 使用 `vue-best-practices` 和 `ui-ux-pro-max`；完成后按 `requesting-code-review` 审查移动工作流。

**文件：**

- 创建：`aryn-mall-delivery-uniapp/src/api/delivery.ts`
- 创建：`aryn-mall-delivery-uniapp/src/api/message.ts`
- 创建：`aryn-mall-delivery-uniapp/src/api/upload.ts`
- 创建：`aryn-mall-delivery-uniapp/src/store/delivery.ts`
- 创建：`aryn-mall-delivery-uniapp/src/store/message.ts`
- 创建：`aryn-mall-delivery-uniapp/src/pages/workbench/index.vue`
- 创建：`aryn-mall-delivery-uniapp/src/pages/tasks/index.vue`
- 创建：`aryn-mall-delivery-uniapp/src/pages/tasks/detail.vue`
- 创建：`aryn-mall-delivery-uniapp/src/pages/tasks/picking.vue`
- 创建：`aryn-mall-delivery-uniapp/src/pages/tasks/delivered.vue`
- 创建：`aryn-mall-delivery-uniapp/src/pages/tasks/exception.vue`
- 创建：`aryn-mall-delivery-uniapp/src/pages/message/index.vue`
- 创建：`aryn-mall-delivery-uniapp/src/composables/useStaffWebSocket.ts`
- 创建：`aryn-mall-delivery-uniapp/src/composables/useWechatSubscription.ts`
- 创建：`aryn-mall-delivery-uniapp/src/pages/tasks/delivery-workflow.spec.ts`

**步骤 1：写失败测试**

- 未全部勾选时不能发出取货请求。
- 送达图片少于 1 张或多于 6 张不能提交。
- 上传成功、任务提交失败时保留素材 ID 草稿并可重试。
- WebSocket 事件只触发 HTTP 补拉，不直接覆盖任务详情。
- 改派后的 403/状态冲突清理本地隐私详情并返回列表。
- 微信拒绝订阅不阻塞任务操作。

**步骤 2：运行测试确认失败**

```powershell
pnpm.cmd test:unit -- src/pages/tasks/delivery-workflow.spec.ts
```

**步骤 3：实现工作台和履约页面**

- 使用固定底部操作区和稳定图片网格，防止动态内容导致按钮跳动。
- 拨号和导航使用 UniApp/微信系统 API，不自行实现地图。
- 每个状态写操作生成 UUID `requestId`，重试复用同一个 ID。
- 页面离开前不清除已上传但未提交的草稿。

**步骤 4：运行验证并提交**

```powershell
pnpm.cmd test:unit -- src/pages/tasks/delivery-workflow.spec.ts
pnpm.cmd type-check
pnpm.cmd build:mp-weixin
git add aryn-mall-delivery-uniapp
git diff --cached --name-only
git commit -m "实现配送员履约工作台"
```

### 任务 15：补齐菜单、字典和全量 SQL

**文件：**

- 创建：`aryn-mall-java/db/boot/21mall_delivery_menu.sql`
- 创建：`aryn-mall-java/db/cloud/21mall_delivery_menu.sql`
- 修改：`aryn-mall-java/db/boot/build-full-sql.mjs`
- 修改：`aryn-mall-java/db/boot/verify-full-sql.mjs`
- 修改：`aryn-mall-java/db/boot/aryn_boot_full.sql`
- 创建：`aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/order/MallDeliveryMenuSqlContractTest.java`
- 修改：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/menu/MenuSeedContractTest.java`

**步骤 1：写失败菜单测试**

断言配送方式字典值 `3`、配送任务/配送范围页面、全部按钮权限和 `order:delivery:execute` 存在，并与套餐菜单/存量租户授权策略一致。

**步骤 2：运行测试确认失败**

```powershell
cd aryn-mall-java
mvn -pl aryn-boot -am "-Dtest=MallDeliveryMenuSqlContractTest,MenuSeedContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**步骤 3：新增菜单 SQL 并更新全量 SQL 构建器**

将 `21mall_delivery.sql` 和 `21mall_delivery_menu.sql` 加入来源清单，更新关键令牌和建表总数。不要直接手改 `aryn_boot_full.sql`，必须由构建器生成。

**步骤 4：生成并校验**

```powershell
node db/boot/build-full-sql.mjs
node db/boot/verify-full-sql.mjs
mvn -pl aryn-boot -am "-Dtest=MallDeliverySqlContractTest,MallDeliveryMenuSqlContractTest,TenantConfigurationConsistencyTest,MenuSeedContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期：全量 SQL 来源、表数、DROP、菜单和租户测试全部通过。

**步骤 5：提交**

```powershell
git add aryn-mall-java/db/boot/21mall_delivery_menu.sql aryn-mall-java/db/cloud/21mall_delivery_menu.sql aryn-mall-java/db/boot/build-full-sql.mjs aryn-mall-java/db/boot/verify-full-sql.mjs aryn-mall-java/db/boot/aryn_boot_full.sql aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/order/MallDeliveryMenuSqlContractTest.java aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/menu/MenuSeedContractTest.java
git diff --cached --name-only
git commit -m "补齐商城配送菜单与全量SQL"
```

### 任务 16：执行完整回归、更新需求记录并交付

**前置技能：** 使用 `verification-before-completion` 和 `requesting-code-review`。

**文件：**

- 修改：`docs/50-需求文档/2026-07-28-商城配送履约/README.md`
- 修改：`docs/50-需求文档/2026-07-28-商城配送履约/实施拆分与验收清单.md`
- 修改：`docs/50-需求文档/2026-07-28-商城配送履约/当前代码与测试环境核对结论.md`
- 修改：`docs/20-业务与数据/功能到代码索引.md`
- 修改：`docs/20-业务与数据/业务流程.md`
- 修改：`docs/20-业务与数据/数据模型.md`
- 修改：`docs/40-接口与风险/接口映射.md`
- 修改：`docs/40-接口与风险/缺失模块清单.md`
- 修改：`docs/90-记录归档/需求记录.md`
- 修改：`AGENTS.md`

**步骤 1：后端全量验证**

```powershell
cd aryn-mall-java
mvn test -pl aryn-boot -am
node db/boot/build-full-sql.mjs
node db/boot/verify-full-sql.mjs
```

预期：Reactor 全部通过，全量 SQL 静态校验通过。任何失败先修复并重跑，不把历史通过当作本次证据。

**步骤 2：管理端验证**

```powershell
cd ..\aryn-mall-ui
pnpm.cmd check:type
pnpm.cmd test:unit
pnpm.cmd lint
```

**步骤 3：两个 UniApp 验证**

```powershell
cd ..\aryn-mall-uniapp
pnpm.cmd type-check
pnpm.cmd test:unit
pnpm.cmd build:mp-weixin

cd ..\aryn-mall-delivery-uniapp
pnpm.cmd type-check
pnpm.cmd test:unit
pnpm.cmd build:mp-weixin
```

**步骤 4：运行态聚焦验收**

- Boot 模式启动后验证结算范围、支付任务、派单、配货、送达、客户确认。
- Cloud 模式验证 Gateway 路由、Dubbo、RocketMQ、Redis/WebSocket 和 XXL-JOB。
- 使用真实私有对象存储验证图片上传和短期访问。
- 使用真实配送小程序 AppID 验证 openid 绑定和订阅消息。
- 将无法在本地执行的项目明确登记为集成环境阻塞，不声明通过。

**步骤 5：代码审查与文档回写**

- 审查状态机、租户、TOB/TOC、图片、退款和消息失败路径。
- 将实际测试数量、命令结果、集成环境结果和剩余风险写回需求包。
- 更新功能索引、业务流程、数据模型、接口映射、缺失模块和需求记录。
- 更新 `AGENTS.md` 项目表，加入独立配送 UniApp 及其验证命令。

**步骤 6：最终工作区检查和文档提交**

```powershell
cd ..
git diff --check
git status --short --branch
git diff --cached --name-only
```

确认未暂存已知无关路径后提交：

```powershell
git add AGENTS.md docs/20-业务与数据 docs/40-接口与风险 docs/50-需求文档/2026-07-28-商城配送履约 docs/90-记录归档/需求记录.md
git diff --cached --name-only
git commit -m "同步商城配送履约文档"
```

是否推送 `origin/dev` 由用户明确授权后执行；推送后必须重新检查 `HEAD...origin/dev` 和工作区状态。
