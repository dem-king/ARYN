# 商城配送员入口第二轮审查修复方案

## 1. 文档目的

本方案针对上一轮配送员入口、商城账号绑定和免重复登录改造后的当前工作树进行第二轮审查，记录仍然存在的缺陷、风险、修复顺序和验收标准。

本轮只输出修复方案，不直接修改业务代码。方案以当前 `HEAD`（`9ae23da9e4cad3354406d4507b13083f39e9cf29`）及工作区中的配送相关改动为基线，遵循项目的 Boot 单体/Cloud 微服务双模式、租户隔离、逻辑删除和 API/Biz 分层要求。

关联设计：[配送员入口与免重复登录设计方案.md](../50-需求文档/2026-09-05-商城配送员入口优化/配送员入口与免重复登录设计方案.md)

## 2. 本轮审查结论

上一轮方案中的主体功能已经落地，以下项目确认已具备：

- 商城个人中心按服务端资格结果显示配送工作台入口。
- 资格状态已区分 `ACTIVE`、`UNBOUND`、`PERMISSION_MISSING`、`ACCOUNT_DISABLED`、`STAFF_INVALID`。
- Web 端增加向导式创建、商城账号绑定、配送资格开通/停用和绑定状态展示。
- 绑定表具备有效员工账号唯一约束，绑定过程使用行锁和数据库约束兜底。
- 删除配送员会解绑记录并撤销配送端会话。
- C 端配送员资料接口已开始使用 VO，移动端请求失败时隐藏入口。
- 移动端已有配送 Token 会先探活，避免每次重复调用 exchange。
- Boot/Cloud 菜单、角色和 Nacos 配置已按租户初始化，Cloud 基线 `md5` 已同步。
- 配送接口已接入 `DeliveryAccessGuard`，并补充了专项测试。

仍需修复的事项按优先级如下：

| 编号 | 优先级 | 类型 | 问题 | 上线要求 |
|---|---|---|---|---|
| R-01 | P1 | 安全/规格 | `DeliveryAccessGuard` 未完整校验租户、staff 主体和绑定关联 | 上线前完成 |
| R-02 | P1 | 安全/规格 | 资格响应暴露 `sysUserId`、`staffId` 等内部 ID | 上线前完成 |
| R-03 | P1 | 安全/规格 | exchange 未显式校验商城用户与员工账号租户一致 | 上线前完成 |
| R-04 | P1 | 一致性 | 删除配送员没有回收 `delivery_staff` 角色 | 上线前完成 |
| R-05 | P1 | 一致性 | Web 旧 `POST /delivery/staff` 可绕过向导创建半成品资格 | 上线前完成 |
| R-06 | P1 | 数据合规 | `sys_user_role` 回收仍使用物理删除 | 上线前完成，或形成评审通过的例外 |
| R-07 | P1 | 事务 | 向导本地事务无法回滚远程 UPMS 角色授予 | 上线前完成补偿或可靠任务 |
| R-08 | P2 | 多实例 | 资格守卫使用单节点内存缓存，Cloud 节点间不能即时失效 | 上线前明确窗口并建议完成 |
| R-09 | P2 | 发布 | `CREATE TABLE IF NOT EXISTS` 不能升级已有旧表结构 | 执行迁移前完成 |

## 3. 统一身份和资格模型

资格判断必须由服务端完成，商城端只依据 `eligible` 控制展示，不能提交或信任 `sysUserId`、`staffId`、手机号。

有效配送资格的完整条件为：

```text
当前商城 TOC 用户属于当前租户
  AND delivery_account_binding.status = '1'
  AND delivery_account_binding.del_flag = '0'
  AND delivery_staff 存在且 del_flag = '0'
  AND delivery_staff.id = binding.delivery_staff_id
  AND delivery_staff.user_id = binding.sys_user_id
  AND sys_user 存在且 del_flag = '0'
  AND sys_user.id = binding.sys_user_id
  AND sys_user.tenant_id = 当前租户
  AND sys_user.status = '0'
  AND sys_user.permissions 包含 delivery:execute 或 *
```

状态建议保持以下含义：

| 状态 | `eligible` | 说明 |
|---|---:|---|
| `ACTIVE` | `true` | 可显示入口并允许 exchange |
| `UNBOUND` | `false` | 没有有效商城账号绑定 |
| `PERMISSION_MISSING` | `false` | 有绑定，但员工没有配送执行权限 |
| `ACCOUNT_DISABLED` | `false` | 员工账号停用或不可见，可按产品决定是否显示灰色提示 |
| `STAFF_INVALID` | `false` | 配送员资料不存在、已删除或与绑定主体不一致 |

资格查询响应对商城端只保留：

```json
{
  "eligible": true,
  "status": "ACTIVE",
  "staffName": "张三",
  "pendingTaskCount": 3
}
```

内部 ID 可以在认证服务内部对象或 Dubbo DTO 中使用，但不得进入 C 端资格响应；exchange 的返回也只给配送端确实需要的最小资料。

## 4. P1 修复任务

### 4.1 R-01：补齐配送访问守卫的主体和租户校验

**现状位置**

- `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/security/DeliveryAccessGuard.java:58-69`
- `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/security/DeliveryAccessGuard.java:101-115`

`requireCurrentStaff()` 通过当前 TOB token 的 `userId` 查 staff，`checkQualification()` 只查询 `SysUser` 状态和权限。当前没有显式确认：

1. 当前 `SysUser.tenantId` 是否等于当前认证租户。
2. `staff.userId` 是否等于当前 token 的主体。
3. staff 是否属于当前租户。
4. staff 与有效 `delivery_account_binding` 是否仍然一致。

这会使“员工账号仍有角色但配送资料已换绑/租户上下文异常”的状态依赖底层拦截器和缓存，守卫本身没有形成完整的授权闭环。

**修复方式**

1. 从 `SecurityUtils.requireUser(DeviceTypeEnum.TOB)` 同时取得 `userId` 和 `tenantId`。
2. 增加一个返回内部资格结论的方法，例如 `loadCurrentQualification(userId, tenantId)`，统一查询 staff、binding 和 sys_user。
3. 对 staff、binding、sys_user 做显式租户比较；任一租户为空或不一致都按无资格处理并记录审计日志。
4. 强制比较 `staff.userId == binding.sysUserId`，并要求 binding 的 `deliveryStaffId` 与查询到的 staff 一致。
5. 配送端工作台仍可保留“独立密码登录”作为兜底，但通过商城绑定换取的身份必须经过绑定校验。若产品允许无商城绑定的独立配送员工作台，应将该路径明确区分为密码登录路径，不能让 guard 误把商城绑定缺失当成可跳过所有 staff 校验。
6. 对写操作使用更严格策略：不使用本地正向资格缓存，或只缓存短 TTL 的版本号；读取类接口可以使用短 TTL。

**验收**

- 当前 token 的租户与 staff 租户不一致时，所有配送接口返回 403。
- staff 被逻辑删除、换绑其他员工账号或 binding 被解绑后，旧 TOB token 不能访问配送任务。
- 普通 TOC token 不能访问任何配送端接口。

### 4.2 R-02：移除资格响应中的内部 ID

**现状位置**

- `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/DeliveryAuthService.java:119-149`
- `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/DeliveryAuthService.java:184-206`

`DeliveryEligibilityVO` 当前返回 `sysUserId` 和 `staffId`。需求明确规定商城端不接收这些 ID，也不能将其作为授权依据。除此之外，`exchange()` 依赖响应对象取回内部 ID，容易让内部 DTO 和外部 VO 混用。

**修复方式**

1. 从 `DeliveryEligibilityVO` 删除 `sysUserId`、`staffId` 两个字段及其 Swagger 描述。
2. 增加内部 `DeliveryQualification` 或直接保留 `DeliveryEligibilityDTO`，在 `evaluateInternal()` 中保存 ID。
3. `eligibility()` 只将内部结果映射为外部字段：`eligible`、`status`、`staffName`、`pendingTaskCount`。
4. `exchange()` 重新调用内部判定方法取得 `sysUserId` 和 `staffId`，不要从外部 VO 反向读取。
5. 对旧客户端采取向后兼容时，不能继续返回真实 ID；如必须兼容字段，返回空值并在版本升级后删除，推荐直接更新客户端类型和接口测试。

**验收**

- OpenAPI/接口测试响应中不出现 `sysUserId`、`staffId`。
- 日志可以记录脱敏后的内部关联信息，但不得记录商城 Token 或配送 Token 明文。
- 客户端删除对这两个字段的依赖。

### 4.3 R-03：显式校验 exchange 的租户一致性

**现状位置**

- `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/DeliveryAuthService.java:68-101`
- `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/DeliveryAuthService.java:165-175`

当前 `loadSysUser()` 根据 binding 返回的 `sysUserId` 查询员工账号，`evaluate()` 只校验账号状态和权限。虽然 MyBatis 租户拦截器可能过滤了部分查询，但认证服务没有显式比较商城 TOC 用户租户与 sys_user 租户，不能把隐式拦截当作认证契约。

**修复方式**

1. `evaluate()` 接收 `mallUserId` 和 `tocTenantId`。
2. 从当前 TOC token 取得 `tenantId`，禁止使用客户端参数覆盖。
3. 查询 `SysUser` 后执行：

```java
if (sysUser == null || !Objects.equals(tocTenantId, sysUser.getTenantId())) {
    return ACCOUNT_DISABLED; // 对外不泄露跨租户细节
}
```

4. 同时要求订单域返回的 binding tenant 与当前租户一致；订单域与 UPMS 域任一侧无法确认租户都拒绝换取。
5. 为 Boot 和 Cloud 分别增加同租户、跨租户、租户为空三组测试。

**验收**

- 跨租户绑定记录不能显示入口。
- 跨租户 exchange 不签发 TOB token。
- 失败响应不泄露“目标租户存在”“员工账号存在”等内部信息。

### 4.4 R-04：删除配送员时回收配送角色

**现状位置**

- `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryStaffServiceImpl.java:355-375`

当前删除流程会解绑 binding、逻辑删除 staff 并撤销配送会话，但没有调用 `changeRoleByCode(userId, "delivery_staff", false)`。结果是员工账号可能继续持有 `delivery_staff` 角色，独立密码登录仍可能被判定为有配送权限，重新创建 staff 时也会继承旧资格。

**修复方式**

1. 删除前读取 staff 的 `userId`，并在本地事务中先标记 staff 删除、解绑有效 binding、撤销配送会话。
2. 调用 UPMS 角色回收接口，确保该员工不再具有 `delivery:execute`。
3. 远程回收失败时不能静默返回成功：
   - 推荐写入“配送资格回收待处理” outbox/任务表，重试直到成功；
   - 最低要求是删除接口返回失败并产生高优先级告警，同时保留可重试操作。
4. 角色回收必须具备幂等性；角色本来不存在时视为成功。
5. 删除确认弹窗应明确提示“同时停用该员工的配送资格”，避免管理员误解为只删除展示资料。

**验收**

- 删除成功后，`delivery_staff` 角色在 UPMS 中不再有效。
- 独立配送员密码登录不能以旧角色进入工作台。
- 删除重试不会重复报错，也不会恢复已删除 staff。

### 4.5 R-05：收口 Web 旧新增接口

**现状位置**

- Controller：`aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/admin/DeliveryStaffController.java:122-133`
- 前端 API：`aryn-mall-ui/apps/web-ele/src/api/delivery/staff.ts:57-61`
- 旧表单：`aryn-mall-ui/apps/web-ele/src/views/delivery/staff/staff-form.vue:181-209`

页面已经增加 `onboard` 向导，但旧 `POST /delivery/staff` 仍然直接调用 `deliveryStaffService.save(staff)`。它可以创建只有 staff 资料、没有绑定、没有角色授予的半成品；同时旧表单仍暴露“新增配送员”路径，管理员可能绕过新向导。

**修复方式**

推荐方案：

1. 删除前端旧的 `createDeliveryStaff()` 调用和旧表单的新增分支，新增统一跳转到 `staff-onboard.vue`。
2. 后端保留旧接口一个版本周期，但标记 `@Deprecated`，并让它返回明确错误：`请使用配送员向导创建`。
3. 下一版本删除旧 `POST /delivery/staff`，同步删除无业务调用的 API 类型和测试。

兼容方案：

1. 旧接口改为内部委托 `onboard()`，请求 DTO 必须明确 `userId`，并要求未绑定时返回“创建成功但未绑定”的状态。
2. 旧接口不得允许直接提交任意 `staffPhone`、`mallUserId` 作为授权依据。
3. 统一写审计日志，区分“向导创建”和“兼容接口创建”。

**验收**

- Web 新增入口只有一个，创建后页面能看到绑定状态和资格状态。
- 任意旧接口调用都不会产生无法解释的 staff 半成品。
- E2E 覆盖创建、绑定、开通、解绑、删除完整链路。

### 4.6 R-06：处理 `sys_user_role` 的逻辑删除要求

**现状位置**

- 实体：`aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/entity/SysUserRole.java:21-41`
- 回收：`aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/dubbo/RemoteSysUserServiceImpl.java:103-135`

项目规则要求删除使用逻辑删除，但 `SysUserRole` 当前没有 `delFlag` 字段，回收角色仍调用 `sysUserRoleMapper.delete(...)` 物理删除。

**修复方式**

优先方案：

1. 给 `sys_user_role` 增加 `del_flag`、`update_time`、`update_by` 字段，并在 `SysUserRole` 增加 `@TableLogic` 映射。
2. 角色查询、权限计算、重复授予查询统一过滤有效记录。
3. 回收角色改为逻辑更新 `del_flag = '1'`。
4. 再次授予角色时优先恢复同一条已删除关联，避免无限新增历史行。
5. 对 `(tenant_id, user_id, role_id, active)` 建立有效关联唯一约束，具体生成列方案先检查现存重复数据。

如果由于上游表结构兼容性无法改造 `sys_user_role`，必须在架构评审中记录例外，并新增独立的配送资格关系表保存授予/回收状态；不能继续无说明地物理删除。

**验收**

- 回收角色后历史关系仍可审计。
- 权限计算不会把 `del_flag = '1'` 的关系算作有效。
- 重复开通/停用具备幂等性。

### 4.7 R-07：补齐跨服务事务和补偿

**现状位置**

- `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryStaffServiceImpl.java:251-312`

`onboard()` 的 `@Transactional` 只能回滚订单域本地数据库。步骤 1、2 创建 staff 和 binding 后，步骤 3 通过 Dubbo 授予 UPMS 角色；本地事务无法回滚远程调用。如果远程成功后本地提交失败，就会留下角色已授予但 staff/binding 不存在的孤儿权限。

**修复方式**

推荐使用可靠消息/补偿任务：

1. 本地事务创建 staff、binding，并写入 `delivery_qualification_operation` outbox，状态为 `PENDING_GRANT`。
2. 本地事务提交后由任务或消息消费者调用 UPMS 授权。
3. 授权成功更新为 `GRANTED`；失败按指数退避重试并记录最后错误。
4. 对管理员返回“处理中”或“开通失败待重试”，不能伪造已开通。
5. 删除或回滚流程使用相同 outbox 机制执行角色回收。

最低改造方案：

1. 保留同步 Dubbo 调用，但在远程成功后本地保存失败的 catch/补偿路径中再次调用撤销角色。
2. 撤销失败写入告警和可重试记录。
3. 为远程调用增加幂等键：`tenantId + sysUserId + roleCode + operation`。

**验收**

- 注入“授权成功、本地提交失败”场景后，最终不会留下孤儿角色。
- Dubbo 超时重试不会重复创建角色关系。
- 管理端能查看待处理/失败的资格操作。

## 5. P2 修复任务

### 5.1 R-08：把资格缓存改为跨实例可感知

**现状位置**

- `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/security/DeliveryAccessGuard.java:44-53`
- `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/security/DeliveryAccessGuard.java:90-99`

当前 `ConcurrentHashMap` 是单节点缓存。Cloud 部署多个订单服务实例时，管理端在 A 节点撤销资格，只能清理 A 节点；B 节点可能继续使用最多 60 秒的正向缓存。

**修复方式**

按安全敏感程度选择：

1. 推荐将资格版本号/撤销时间放入 Redis，缓存 key 至少包含 `tenantId:sysUserId`。
2. 管理端回收资格、解绑、删除时递增版本号或发布权限变更事件，各节点收到后删除本地缓存。
3. 配送写操作每次读取 Redis 版本或数据库当前资格，不使用本地正向缓存。
4. 若暂时保留本地缓存，TTL 应配置化并明确 60 秒是产品可接受的最大失效窗口；文档和监控必须标记该限制。
5. 缓存异常按 fail-closed 处理，禁止因 Redis 故障继续放行配送写操作。

**验收**

- 两个 Cloud 实例之间执行解绑/停用后，另一实例的旧 token 在约定窗口内被拒绝。
- 监控能统计资格缓存命中、失效和远程校验失败。

### 5.2 R-09：补充存量绑定表结构迁移

**现状位置**

- `aryn-mall-java/db/boot/29delivery_account_binding.sql`
- `aryn-mall-java/db/cloud/29delivery_account_binding.sql`

脚本在 `CREATE TABLE IF NOT EXISTS` 中声明 `active_sys_user_id` 生成列和唯一索引。若目标环境已有上一版本的 `delivery_account_binding`，`CREATE TABLE IF NOT EXISTS` 不会增加缺失字段和索引，脚本看似成功但约束并未生效。

**修复方式**

1. 新增编号递增的 boot/cloud 增量脚本，例如 `30delivery_account_binding_schema_upgrade.sql`，两份内容语义一致。
2. 执行前查询重复有效绑定：

```sql
SELECT tenant_id, sys_user_id, COUNT(*) AS cnt
FROM delivery_account_binding
WHERE status = '1' AND del_flag = '0'
GROUP BY tenant_id, sys_user_id
HAVING COUNT(*) > 1;
```

3. 对重复数据先由管理员确认保留记录，另一条只能通过业务解绑/逻辑删除处理，禁止脚本自动删除存量业务数据。
4. 在确认无重复后再执行 `ALTER TABLE ... ADD COLUMN` 和 `ADD UNIQUE KEY`；脚本应使用 `information_schema` 判断字段/索引是否存在，保证可重复执行。
5. Boot 脚本加入 `build-full-sql.mjs` 的 `sections`，重新生成并校验 `aryn_boot_full.sql`。
6. Cloud 同步订单库、UPMS 库和 Nacos 所需变更，并核对基线 `md5`。

**验收**

- 新环境从完整 SQL 初始化后具备生成列和唯一索引。
- 旧环境执行增量脚本后结构一致，重复执行不报错。
- boot/cloud `SHOW CREATE TABLE` 结果满足同一业务约束。

## 6. Web 端配套修改

Web 端本轮不需要重新设计页面，但需要收口数据和状态：

1. `staff-onboard.vue` 作为唯一新增入口，提交后展示 `BOUND/UNBOUND` 和 `GRANTED/NOT_GRANTED` 结果。
2. 旧 `staff-form.vue` 只保留资料编辑，不允许新增；已绑定 staff 禁止修改 `userId`。
3. 删除按钮二次确认中说明会同时回收配送资格和配送会话。
4. 资格停用、解绑、删除后刷新列表和顶部统计，失败时保留页面状态并提示可重试。
5. `managerPage` 的权限摘要只能用于展示，不能替代服务端授权。
6. 继续使用服务域路径和 `rewriteBootUrl`，不得在页面中拼接 `/boot` 或读取 `VITE_OPEN_BOOT` 做分支。
7. 增加 API 类型测试，确认资格字段不包含内部 ID；增加 E2E 测试覆盖“向导创建 -> 绑定 -> 开通 -> C 端入口 -> 解绑/停用 -> 入口消失”。

## 7. UniApp 端配套修改

1. 资格查询只发送商城 TOC token，不能发送 `staffId`、手机号或员工账号。
2. 资格请求失败、返回 `eligible=false` 或返回 `STAFF_INVALID` 时清理展示状态；不得因为本地旧 `deliveryToken` 继续显示入口。
3. 点击入口使用互斥 loading，避免并发 exchange；exchange 仅在本地 token 探活失败后调用。
4. exchange 返回 401 时刷新商城登录态并最多重试一次；403 时清理配送 token 并回到个人中心；网络错误留在当前页面。
5. 商城退出同时清理配送 token；退出配送工作台只清理配送 token。
6. Boot/Cloud 两种环境都检查资格、exchange、工作台请求的最终 URL。

## 8. 测试计划

### 8.1 Java 单元和集成测试

新增或补充以下场景：

- `DeliveryAuthServiceTest`：同租户 ACTIVE、未绑定、权限缺失、账号停用、staff 无效、跨租户、内部 ID 不出参。
- `DeliveryAccessGuardTest`：TOC token、租户不一致、staff.userId 不一致、解绑、删除、角色回收后的旧 token。
- `DeliveryStaffServiceImplTest`：删除回收角色、解绑幂等、向导角色授予失败、远程成功后本地失败补偿。
- `RemoteSysUserServiceImplTest`：角色授予/回收幂等、逻辑删除记录不参与权限计算。
- `DeliveryAccountBindingServiceImplTest`：同商城用户/同员工并发绑定、租户隔离、历史解绑记录复用。

最小命令：

```bash
cd aryn-mall-java
mvn -pl aryn-auth,aryn-order/aryn-order-biz,aryn-upms/aryn-upms-biz -am \
  -Dtest='DeliveryAuthServiceTest,DeliveryAccessGuardTest,DeliveryStaffServiceImplTest,RemoteSysUserServiceImplTest,DeliveryAccountBindingServiceImplTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### 8.2 前端验证

```bash
cd aryn-mall-ui
pnpm check:type
pnpm test:unit
pnpm lint

cd ../aryn-mall-uniapp
pnpm type-check
pnpm test:unit -- --run
```

至少分别在 `VITE_OPEN_BOOT=true` 和 `VITE_OPEN_BOOT=false` 下核对：

- `GET /auth/delivery/eligibility`
- `POST /auth/delivery/exchange`
- 配送工作台所有 `/order/...` 或配送服务域请求

### 8.3 SQL 和整体验证

```bash
cd aryn-mall-java
node db/boot/build-full-sql.mjs
node db/boot/verify-full-sql.mjs
mvn clean compile -pl aryn-boot -am
git diff --check
```

当前已知环境阻断：`mvn clean compile -pl aryn-boot -am` 曾因 `SysTenantPackage.java` 对 `JsonArrayStringTypeHandler` 的模块依赖/编译解析失败而中断；源码文件在 common-mybatis 模块中存在，但当前构建没有成功解析到它。该问题需要先确认是否属于本轮改动；在未解决前，不能宣称 Boot 全量编译通过。

## 9. 上线顺序

1. 先发布兼容代码：认证服务能识别新状态，配送守卫能处理新旧数据，Web 仍可读取旧数据。
2. 执行 boot/cloud 绑定表结构增量迁移，先检查重复数据，再加生成列和唯一索引。
3. 执行 UPMS `sys_user_role` 逻辑删除改造或经评审确认的替代方案。
4. 发布向导收口、删除回收角色、租户显式校验和补偿任务。
5. 发布 UniApp 新客户端，确认旧客户端在 `eligible=false` 时安全降级。
6. 观察资格查询失败率、exchange 拒绝率、守卫 401/403、角色补偿积压和跨实例缓存失效延迟。

## 10. 回滚策略

- 前端回滚只回退入口展示和向导页面版本，不删除已产生的 binding 或审计数据。
- 后端回滚前先停止新的角色授予/删除操作，保留兼容查询接口；禁止回滚脚本删除字段、索引或历史绑定。
- 补偿任务支持暂停和继续，失败记录保留，恢复后按幂等键重试。
- 如果唯一索引上线前发现重复数据，停止索引变更，先人工处理重复有效绑定，再重新执行迁移。
- 发现跨租户异常时立即关闭 exchange 开关，并保留独立配送密码登录作为运维兜底；密码登录同样必须受有效角色和租户校验保护。

## 11. 完成定义

满足以下条件后，才可将本次改造标记为完成：

1. R-01 至 R-07 已有代码、测试和审计日志证据；R-08、R-09 已完成或有明确的上线前例外评审。
2. 资格响应不返回内部 ID，exchange 显式校验租户并签发独立 TOB token。
3. 删除、解绑、停用资格后，所有配送接口都拒绝旧身份，且角色回收最终成功。
4. Web 端不存在可绕过向导创建半成品的默认入口。
5. Boot/Cloud SQL、Nacos、API 路径和权限语义一致。
6. Java 专项测试、UI/UniApp 测试、SQL 校验和 Boot 编译均有成功记录；若 Boot 编译仍被无关工作树错误阻断，必须在发布记录中明确阻断原因和责任边界。
