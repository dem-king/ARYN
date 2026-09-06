# 商城配送员入口修复实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复商城配送员入口、账号绑定、配送资格和配送 Token 的一致性问题，使 Web 配置、商城入口和移动端工作台在 Boot/Cloud 双模式下遵循同一套授权规则。

**Architecture:** 保留商城 TOC Token 与配送员 TOB Token 的身份隔离。服务端以 `delivery_account_binding` 为唯一绑定事实来源，资格查询和身份换取同时校验绑定、员工账号、配送员资料、租户、状态和 `delivery:execute` 权限；配送接口再通过统一访问守卫动态校验资格，确保停用、解绑和删除在短时间内失效。Web 端只通过向导和绑定接口修改关系，禁止旧编辑接口直接修改 `userId`。

**Tech Stack:** Spring Boot 3、Dubbo、Sa-Token、MyBatis-Plus、MySQL、Nacos、Vue 3/TypeScript、UniApp、Pinia、Alova、JUnit 5。

---

## 1. 依据与范围

本计划基于以下文档和当前工作树审查结果：

- 需求：[配送员入口与免重复登录设计方案.md](../50-需求文档/2026-09-05-商城配送员入口优化/配送员入口与免重复登录设计方案.md)
- 代码基线：当前工作树相对于 `HEAD` 的全部改动
- 项目约束：[AGENTS.md](../../AGENTS.md)、[Java开发规范.md](../10-开发指南/Java开发规范.md)

本次修复包含 Java 后端、管理后台、UniApp、Boot/Cloud SQL、Nacos 配置和测试；不修改无关的商城装修、促销或消息业务。

## 2. 修复目标与验收口径

### 2.1 用户可见行为

| 状态 | 个人中心入口 | 点击行为 |
|---|---|---|
| 未登录 | 不显示 | 无 |
| 已登录且无有效绑定 | 不显示 | 无 |
| 已绑定但未授予 `delivery:execute` | 不显示 | 无 |
| 已绑定、账号正常、资料可用、权限正常 | 显示“配送工作台” | 复用有效 Token，否则换取一次 |
| 已绑定但账号停用 | 可显示灰色提示 | 不换取 Token，提示联系管理员 |
| 已解绑、资料删除或绑定失效 | 不显示 | 清理本地配送状态 |
| 资格接口超时/失败 | 不显示 | 留在个人中心 |

### 2.2 后端安全口径

1. 客户端不能提交 `sysUserId`、`deliveryStaffId` 或手机号作为授权依据。
2. 资格接口只能从商城 TOC Token 获取当前商城用户和租户。
3. 身份换取必须校验绑定记录、员工账号、配送员资料、逻辑删除标记、配送员状态、租户一致性和 `delivery:execute`。
4. 配送接口每次请求都要能感知资格回收；不能只依赖换取时写入 Token 的旧权限集合。
5. 绑定关系在数据库层和事务层都保证一个商城用户、一个员工账号最多一个有效绑定。

### 2.3 双模式口径

- API 源路径首段必须是服务域，Boot 由 `rewriteBootUrl` 改写，Cloud 由网关路由。
- Boot 和 Cloud 各有可重复执行的增量 SQL，内容和权限语义一致。
- Cloud Nacos 的基线 INSERT、增量 UPDATE 和 `md5` 必须同时正确。

## 3. 问题清单与优先级

| 编号 | 优先级 | 问题 | 修复结果 |
|---|---|---|---|
| F-01 | P1 | 向导文案称“创建并开通”，后端没有授予配送角色 | 向导一次性完成创建、绑定和资格开通，或明确显示为“仅创建” |
| F-02 | P1 | 失效绑定、删除资料仍可能被标记为已绑定并换取 Token | 绑定、资料、员工账号三方必须一致，任何一方失效都拒绝 |
| F-03 | P1 | 旧编辑接口可直接修改 `userId`，不更新绑定 | 禁止修改已绑定配送员的 `userId`，改用解绑后重新绑定流程 |
| F-04 | P1 | 解绑/停用后旧配送 Token 在 TTL 内仍可调用配送接口 | 增加统一配送访问守卫，动态校验当前资格并主动撤销会话 |
| F-05 | P1 | 角色/菜单 SQL 固定默认租户 | 角色和菜单按租户初始化，不能写死租户 ID |
| F-06 | P1 | Cloud Nacos 基线没有包含新增配送表 | 更新基线 INSERT，并重新计算 `md5` |
| F-07 | P1 | Cloud 增量只改 `content` 不改 `md5` | 增量脚本同步 `md5`、`gmt_modified` |
| F-08 | P1 | 资格状态把“未开通权限”和“账号停用”都返回 `DISABLED` | 增加可区分的状态，移动端只对停用状态显示灰卡 |
| F-09 | P2 | 资格请求失败仍保留本地 Token 入口 | 请求失败清理展示状态并隐藏入口，Token 不能作为授权依据 |
| F-10 | P2 | 资格正常时已有 Token 仍重复 exchange | 先检查本地 Token 的有效性，再决定是否换取 |
| F-11 | P2 | 绑定只靠先查后写，并发时可能重复绑定同一员工 | 增加数据库有效绑定唯一约束、行锁和冲突重试 |
| F-12 | P2 | C 端 Controller 返回 Entity，登录接口使用裸参数和通用异常 | 增加 VO、Request DTO 和统一业务异常 |

## 4. 目标状态模型

### 4.1 绑定状态

`delivery_account_binding.status` 继续使用：

- `1`：有效绑定
- `0`：已解绑，保留历史记录

有效资格必须同时满足：

```text
binding.status = 1
binding.del_flag = 0
delivery_staff.id = binding.delivery_staff_id
delivery_staff.del_flag = 0
delivery_staff.user_id = binding.sys_user_id
sys_user.id = binding.sys_user_id
sys_user.tenant_id = 当前租户
sys_user.status = 0
sys_user.permissions 包含 delivery:execute 或 *
```

### 4.2 资格状态

后端 DTO 使用以下状态，避免把不同原因混成一个值：

- `ACTIVE`：可展示入口并允许换取
- `UNBOUND`：没有有效绑定，不展示
- `PERMISSION_MISSING`：有绑定但未授予配送权限，不展示
- `ACCOUNT_DISABLED`：员工账号停用，可展示灰色提示
- `STAFF_INVALID`：配送资料不存在、删除或状态不可用，不展示

如果为了兼容旧客户端必须继续返回 `DISABLED`，则同时增加 `reason` 字段；新客户端按 `reason` 判断，旧客户端仍按 `eligible=false` 处理。

## 5. 实施顺序

按以下顺序执行，每个任务完成后都要运行对应的最小验证。任务之间建议拆成独立中文提交，避免把 SQL、认证和前端行为混在一个提交中。

### Task 1：补齐失败测试和状态契约

**Files:**

- Modify: `aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/dto/DeliveryEligibilityDTO.java`
- Modify: `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/DeliveryAuthService.java`
- Create/Modify tests: `aryn-mall-java/aryn-auth/src/test/java/com/aryn/cloud/auth/service/DeliveryAuthServiceTest.java`
- Create/Modify tests: `aryn-mall-java/aryn-order/aryn-order-biz/src/test/java/com/aryn/cloud/order/dubbo/RemoteDeliveryAccountServiceImplTest.java`

**Step 1: 写失败测试**

覆盖以下输入：

1. binding 找不到 staff，结果为 `STAFF_INVALID`、`eligible=false`。
2. staff 的 `userId` 与 binding 不一致，结果为 `STAFF_INVALID`。
3. staff 被逻辑删除或状态不可用，结果为 `STAFF_INVALID`。
4. 员工账号正常但没有 `delivery:execute`，结果为 `PERMISSION_MISSING`。
5. 员工账号停用，结果为 `ACCOUNT_DISABLED`。

**Step 2: 运行测试确认失败**

```bash
cd aryn-mall-java
mvn -pl aryn-auth,aryn-order/aryn-order-biz -am \
  -Dtest='DeliveryAuthServiceTest,RemoteDeliveryAccountServiceImplTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

**Step 3: 定义最小 DTO 变更**

在 `DeliveryEligibilityDTO` 中增加 `reason` 或明确状态枚举，禁止使用多个布尔值表达同一状态。

**Step 4: 重新运行测试**

预期新增测试全部通过。

### Task 2：修复资格查询和身份换取校验

**Files:**

- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/dubbo/RemoteDeliveryAccountServiceImpl.java`
- Modify: `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/DeliveryAuthService.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/IDeliveryStaffService.java`

**Implementation:**

1. `getEligibilityByMallUser()` 查到 binding 后，必须查 staff，并验证 `staff.userId == binding.sysUserId`。
2. staff 不存在、`del_flag != 0`、状态不可用时，返回 `STAFF_INVALID`，不能设置 `bound=true`。
3. 通过 Dubbo 获取 sys_user 后，验证 `sysUser.tenantId` 与当前租户一致；无法确认租户时拒绝。
4. `exchange()` 不重复实现一套校验，统一调用 `DeliveryAuthService` 的资格判定方法。
5. 换取成功返回 `DeliveryStaffInfoVO`，只返回 `id` 和 `staffName`，不返回完整 Entity。
6. 记录拒绝原因和绑定 ID 的审计日志，禁止记录明文 Token。

**Acceptance:**

- 删除 staff 后资格接口返回 `STAFF_INVALID`，exchange 返回业务错误，不签发 Token。
- 修改 staff.userId 后，旧 binding 不再通过校验。
- 租户不一致时不能读取或换取其他租户配送身份。

### Task 3：统一配送接口访问守卫和 Token 回收

**Files:**

- Create: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/security/DeliveryAccessGuard.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppDeliveryStaffController.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppDeliveryTaskController.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppDeliveryTripController.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppOrderDeliveryController.java`
- Modify: `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/DeliveryAuthService.java`

**Implementation:**

1. `DeliveryAccessGuard.requireCurrentStaff()` 从当前 TOB Token 读取 `sysUserId`，重新查询当前 staff、binding/资格和权限。
2. 所有 `/app/delivery/**` 入口在获取 staffId 前调用该守卫；不能只校验 `DeviceTypeEnum.TOB`。
3. 守卫发现权限回收、解绑、删除或租户不一致时，返回 401/403，并触发配送 Token 清理。
4. 停用/解绑/删除管理操作完成后，调用统一的 `revokeDeliverySessions(sysUserId)`：
   - 优先使用 Sa-Token 的配送设备会话踢出能力；
   - 同时写入配送会话版本或 Redis denylist，防止 Cloud 节点缓存旧会话；
   - 不影响商城 TOC 会话。
5. 若项目当前无法按设备踢出，则至少让守卫每次请求动态校验，最长失效窗口由缓存 TTL 控制且不得超过 60 秒。
6. `AppDeliveryStaffController.me()` 改为返回 `DeliveryStaffInfoVO`，不返回 Entity。

**Tests:**

- 权限回收后旧 Token 调用 `/app/delivery/task/page` 返回 403。
- 解绑后旧 Token 调用任务详情、送达、异常上报、出车单接口全部拒绝。
- 普通 TOC Token 访问配送接口返回 401/403。

### Task 4：修复绑定事务、并发和删除语义

**Files:**

- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryStaffServiceImpl.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryAccountBindingServiceImpl.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/admin/DeliveryStaffController.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/mapper/DeliveryAccountBindingMapper.java`

**Implementation:**

1. 绑定前在事务中对当前租户下的商城用户和员工有效绑定记录加行锁，查询使用 `FOR UPDATE`。
2. 增加数据库有效绑定唯一约束。推荐新增生成列：

```sql
ALTER TABLE delivery_account_binding
  ADD COLUMN active_sys_user_id varchar(32)
    GENERATED ALWAYS AS (
      CASE WHEN status = '1' AND del_flag = '0' THEN sys_user_id ELSE NULL END
    ) STORED,
  ADD UNIQUE KEY uk_delivery_binding_active_sys_user
    (tenant_id, active_sys_user_id);
```

执行前先统计重复数据；若存在重复，必须由管理员确认保留记录后再迁移，不能自动删除存量数据。

3. 绑定冲突捕获唯一键异常，返回“员工账号已绑定其他商城用户”，不能返回 500。
4. `unbindMallUser()` 只将 status 改为 `0`，保留审计字段，并立即触发配送会话撤销。
5. 删除配送员时先执行解绑和会话撤销，再逻辑删除 staff；禁止留下有效 binding。
6. 删除和解绑操作写入操作人、时间和原因。

**Tests:**

- 两个并发绑定请求只能有一个成功。
- 解绑后同一商城用户可以绑定新的配送员。
- 删除绑定配送员后资格立即失效且历史绑定仍可审计。

### Task 5：关闭旧编辑链路，避免修改 `userId`

**Files:**

- Modify: `aryn-mall-ui/apps/web-ele/src/views/delivery/staff/index.vue`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/delivery/staff/staff-form.vue`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/admin/DeliveryStaffController.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/IDeliveryStaffService.java`

**Implementation:**

1. 已绑定配送员的列表操作只允许编辑姓名、手机号、车辆信息、接单状态，不允许编辑关联员工账号。
2. `staff-form.vue` 在编辑模式禁用员工账号选择器；后端更新接口忽略或拒绝 `userId` 变化。
3. 若确需更换员工账号，流程必须是：停用资格 -> 解绑商城账号 -> 更换员工账号 -> 重新绑定 -> 重新开通资格。
4. 删除旧的直接 `updateById` 入口，改为 Service 方法执行字段白名单更新和一致性校验。
5. `DeliveryStaffController` 只接收 DTO 并封装结果，业务校验下沉 Service。

**Acceptance:**

- 已绑定行在 Web 编辑弹窗中看不到可编辑的员工账号选择器。
- 恶意提交不同 `userId` 返回 400/业务错误，不改变数据库。
- 更换账号后必须产生新的绑定和权限审计记录。

### Task 6：修复 Web 向导，真正完成开通闭环

**Files:**

- Modify: `aryn-mall-ui/apps/web-ele/src/views/delivery/staff/staff-onboard.vue`
- Modify: `aryn-mall-ui/apps/web-ele/src/api/delivery/staff.ts`
- Modify: `aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/dto/DeliveryOnboardDTO.java`
- Create: `aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/vo/DeliveryStaffOnboardVO.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryStaffServiceImpl.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/admin/DeliveryStaffController.java`

**Recommended interaction:**

1. 向导摘要明确展示三个动作：创建配送资料、绑定商城账号、开通配送资格。
2. “立即开通配送资格”作为明确的复选项，默认勾选；未勾选时按钮显示“仅创建配送员”。
3. 勾选时后端在同一业务编排中授予 `delivery_staff` 角色；不要让前端再调用第二个接口猜测是否成功。
4. 成功响应返回 `DeliveryStaffOnboardVO`，包括 staffId、bindingStatus、qualificationStatus，不返回 Entity。
5. Dubbo 角色授予失败时执行补偿：撤销已创建 binding、逻辑删除新 staff，记录失败原因；重复提交必须幂等。
6. 成功提示根据实际状态生成，禁止固定显示“创建并开通成功”。

**Tests:**

- 默认勾选时创建后资格接口返回 `ACTIVE`。
- 不勾选时创建成功但资格接口返回 `PERMISSION_MISSING`，页面不显示入口。
- 角色服务失败时不留下半成品 staff/binding。
- 重复点击只生成一条有效 staff 和一条有效 binding。

### Task 7：修复移动端入口状态和 Token 使用顺序

**Files:**

- Modify: `aryn-mall-uniapp/src/pages/user/user-center/index.vue`
- Modify: `aryn-mall-uniapp/src/api/delivery.ts`
- Modify: `aryn-mall-uniapp/src/api/core/handlers.ts`
- Modify: `aryn-mall-uniapp/src/store/authStore.ts`

**Implementation:**

1. `getDeliveryEligibility()` 成功前不渲染入口。
2. 请求失败时清理 `deliveryEligibility`、`hasDeliveryToken` 的展示状态，不能仅因本地存在 Token 就显示或直达。
3. 仅当服务端返回 `ACTIVE` 时显示入口；`ACCOUNT_DISABLED` 才显示灰色提示；`PERMISSION_MISSING`、`STAFF_INVALID`、`UNBOUND` 均隐藏。
4. 点击时若本地 Token 存在，先调用一个轻量配送身份探活接口；探活成功直接进入，401/403 清理 Token 后再尝试一次 exchange。
5. exchange 请求增加 loading 和幂等锁，成功后保存 Token 与最小 staff 信息。
6. 商城退出时清理配送 Token；退出配送工作台只清理配送 Token，不清理商城登录态。
7. 资格接口仍使用商城 Token，配送接口仍使用独立 `deliveryToken`；保留 `rewriteBootUrl` 路径规则。

**Tests:**

- 未登录、无资格、请求失败时入口都不存在。
- 已有有效 Token 时不重复 exchange。
- Token 失效后清理本地数据并进入配送登录或个人中心。
- `VITE_OPEN_BOOT=true/false` 下请求路径分别正确。

### Task 8：统一 DTO/VO、异常和依赖注入规范

**Files:**

- Create: `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/dto/DeliveryLoginRequest.java`
- Modify: `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/controller/TobTokenController.java`
- Modify: `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/LoginService.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppDeliveryStaffController.java`
- Modify: `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/admin/DeliveryStaffController.java`

**Implementation:**

1. 配送登录使用 `@RequestBody @Valid DeliveryLoginRequest`，手机号和密码使用长度、格式校验。
2. 账号不存在、密码错误、账号停用、权限缺失统一抛 `ArynBusinessException`，由全局异常处理器返回统一结构。
3. `@DubboReference` 使用 `private final` 构造器注入；不要在 `@RequiredArgsConstructor` 类中保留非 final 字段注入。
4. C 端 `/me` 返回 VO，不返回 Entity。
5. Controller 只做参数接收和结果封装，资格、绑定、订单归属校验全部放 Service。

### Task 9：多租户角色、菜单和 SQL 迁移

**Files:**

- Create: `aryn-mall-java/db/boot/30delivery_entry_fix.sql`
- Create: `aryn-mall-java/db/cloud/30delivery_entry_fix.sql`
- Modify: `aryn-mall-java/db/boot/build-full-sql.mjs`
- Regenerate: `aryn-mall-java/db/boot/aryn_boot_full.sql`
- Modify: `aryn-mall-java/db/cloud/3aryn_nacos.sql`
- Modify: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/service/impl/SysTenantServiceImpl.java`（若采用租户创建时 provisioning）

**SQL 规则:**

1. 新脚本可重复执行，不使用 `DROP`、`TRUNCATE`，不按 ID 前缀物理删除存量权限数据。
2. 角色和菜单采用“平台定义 + 租户初始化”策略：新租户创建时复制 `delivery_staff` 角色及其菜单；存量租户执行一次幂等补齐。
3. 不在 SQL 中写死业务租户 ID。若必须保留默认租户兼容数据，应从租户表查询并按租户循环处理，不能复制固定 ID。
4. 角色、角色菜单、租户菜单的唯一键使用业务编码或稳定 ID，重复执行使用 `INSERT ... ON DUPLICATE KEY UPDATE`，只恢复缺失关系。
5. `sys_user_role` 撤权不能物理删除。若现有表无 `del_flag`，先增加可审计状态字段或建立配送资格关系表，完成模型迁移后再切换代码。
6. Cloud Nacos 基线 `aryn-order-biz-dev.yml` 的 INSERT 直接包含完整配送表清单：

```yaml
hx:
  tenant:
    tables:
      - order_info
      - order_item
      - order_delivery
      - order_delivery_logistics
      - order_config
      - order_refund
      - shopping_cart
      - delivery_staff
      - delivery_trip
      - delivery_task
      - delivery_task_item
      - delivery_warehouse_config
      - delivery_task_log
      - delivery_evidence
      - delivery_area
      - delivery_account_binding
```

7. 修改 Nacos `content` 时同时执行：

```sql
UPDATE config_info
SET content = :new_content,
    md5 = MD5(:new_content),
    gmt_modified = NOW()
WHERE data_id = 'aryn-order-biz-dev.yml'
  AND group_id = 'DEFAULT_GROUP';
```

8. 重新运行 `node db/boot/build-full-sql.mjs`，检查 `aryn_boot_full.sql` 中包含新脚本且顺序正确。

**Migration preflight:**

```sql
SELECT tenant_id, sys_user_id, COUNT(*) AS active_count
FROM aryn_order.delivery_account_binding
WHERE status = '1' AND del_flag = '0'
GROUP BY tenant_id, sys_user_id
HAVING COUNT(*) > 1;
```

结果非空时暂停唯一索引迁移，先由管理员处理冲突并保留审计记录。

### Task 10：补齐 Controller、Service、前端和双模式验收测试

**Backend tests:**

- `DeliveryAuthServiceTest`：状态矩阵、租户、权限、staff 一致性。
- `DeliveryAccountBindingServiceImplTest`：绑定、解绑、并发冲突、幂等。
- `DeliveryStaffServiceImplTest`：向导成功、角色失败补偿、删除联动解绑。
- `DeliveryAccessGuardTest`：旧 Token 在权限回收后拒绝。
- `AppDeliveryStaffControllerTest`：返回 VO，不含 Entity 敏感字段。
- `DeliveryLoginControllerTest`：DTO 校验和统一异常。

**UniApp tests:**

- 入口显示矩阵：未登录、ACTIVE、UNBOUND、PERMISSION_MISSING、ACCOUNT_DISABLED、请求失败。
- 本地 Token 探活、失效清理和重复点击锁。
- `rewriteBootUrl` 在 Boot/Cloud 两种模式下的输出。

**Commands:**

```bash
cd aryn-mall-java
mvn clean test -pl aryn-boot -am

cd ../aryn-mall-ui
pnpm check:type
pnpm test:unit
pnpm lint

cd ../aryn-mall-uniapp
pnpm type-check
pnpm test:unit -- --run

cd ../aryn-mall-java/db/boot
node verify-full-sql.mjs
```

**双模式静态检查:**

```bash
cd aryn-mall-uniapp
VITE_OPEN_BOOT=true pnpm type-check
VITE_OPEN_BOOT=false pnpm type-check

cd ../aryn-mall-ui
VITE_OPEN_BOOT=true pnpm check:type
VITE_OPEN_BOOT=false pnpm check:type
```

## 6. 推荐的提交拆分

1. `修复配送资格状态与绑定一致性校验`
2. `增加配送接口动态权限守卫和会话撤销`
3. `修复配送员绑定并发约束与删除联动`
4. `关闭旧编辑入口并完善配送员向导`
5. `修复商城配送入口状态和 Token 复用`
6. `统一配送认证 DTO VO 与异常处理`
7. `补齐配送多租户 SQL 和 Cloud Nacos 基线`
8. `补充配送入口双模式验收测试`

每个提交完成后执行对应测试；合并前再执行一次全量构建和 SQL 校验。

## 7. 上线与回滚

### 7.1 上线顺序

1. 先执行数据预检查，确认不存在重复有效绑定。
2. 发布兼容后端：先支持新状态字段和旧客户端响应格式。
3. 执行 Boot/Cloud SQL 增量脚本，更新 Nacos 基线和 `md5`。
4. 发布后端动态访问守卫，观察权限拒绝、绑定冲突、exchange 失败日志。
5. 发布 Web 向导和移动端入口。
6. 使用至少两个租户验证创建、绑定、开通、解绑、停用、删除和重新绑定。

### 7.2 回滚策略

- 前端可回滚到旧版本，但后端仍保留动态访问守卫，不能因回滚前端而放宽权限。
- 新增字段和索引只做向前兼容，不在回滚时删除。
- 若 Nacos 配置异常，使用备份的完整 `content` 和对应 `md5` 恢复，并重新发布配置。
- 若发现绑定数据冲突，暂停写入绑定接口，导出冲突记录后人工处理。

## 8. 完成定义

只有满足以下条件才可标记完成：

- 向导的“创建并开通”与实际权限状态一致。
- 任何失效 binding、staff、sys_user 都不能展示入口或换取 Token。
- 解绑、停用、删除后的旧配送 Token 在约定窗口内被拒绝。
- 已绑定配送员不能通过旧编辑弹窗修改 `userId`。
- 绑定并发测试和唯一约束测试通过。
- Boot/Cloud 两份 SQL 可重复执行，Nacos `content` 与 `md5` 一致。
- `TenantConfigurationConsistencyTest`、Java 编译、UI/UniApp 类型检查和单元测试全部通过。
- 至少两个租户完成端到端验收，且日志中没有明文 Token。
