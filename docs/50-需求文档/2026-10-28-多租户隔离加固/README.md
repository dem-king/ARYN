# 多租户隔离加固

> 日期：2026-10-28
> 范围：`aryn-mall-java` 后端的租户配置权限、写入归属、缓存、会话生命周期与配送凭证访问
> 约束：不新增表/字段，不改动现有业务数据；Boot / Cloud 双模式同时生效

## 一、背景

对当前工作树做只读审计后确认：项目具备会话租户绑定、MyBatis 租户注入、Dubbo 租户传播和启动 schema 校验，属于「有基础的多租户」，但尚未形成完整闭环。已确认的缺口：

1. `SysTenantMenuController` 无权限校验，直接把路径/请求体中的租户 ID 写入上下文，普通有效会话可读写其它租户的授权菜单。
2. 管理端大量接口直接以实体作为 `@RequestBody`，实体中的 `tenantId` 可被客户端指定；租户插件在 INSERT 已带租户列时不覆盖，UPDATE 也只限制原记录归属、不禁止改写归属。
3. 三方账号详情缓存只按 ID 命中，其它租户可跳过数据库租户过滤直接拿到缓存对象。
4. 租户状态、授权有效期与套餐授权未进入认证闭环：停用/到期/删除不会撤销已有 token；角色菜单保存不校验套餐授权范围。
5. 配送凭证与公开商品图共用匿名本地下载链路，租户目录与 UUID 只是命名隔离。
6. `TenantInterceptorBypassAuditTest` 解析不到「注解与方法之间夹 @Select」的绕过方法，且新增旁路未登记，门禁未真正生效。

## 二、修复内容

### 1. 平台级配置权限

- 新增 `PlatformTenantGuard`：租户、套餐、租户菜单、全局菜单写操作必须同时满足 TOB 会话 + 平台租户身份。
- `SysTenantController`、`SysTenantPackageController`、`SysMenuController` 的查询/写操作接入该守卫；`/tenant/register`、`/tenantpackage/list` 保持公开注册用途。
- `SysTenantMenuController` 增加 `upms:systenant:get/edit` 权限校验，校验目标租户存在、菜单 ID 有效，并在操作后恢复原租户上下文。
- `SysRoleMenuServiceImpl` 校验角色属于当前租户、菜单 ID 有效；非平台租户只能提交 `sys_tenant_menu` 已授权的菜单，堵住套餐外提权。
- `SysTenantMenuServiceImpl` 在全量替换授权菜单前强制要求租户上下文，避免无上下文调用误删其它租户授权行。
- 平台租户不可被停用/删除，避免误配锁死运维入口。
- 在线用户列表/强退补 `upms:onlineuser:get` / `upms:onlineuser:forced` 权限；强退前校验该 token 的在线记录属于当前租户，防止跨租户或跨端强退。
- 角色菜单页读取的 `/menu/tenant`、`/menu/role/{roleId}` 补 `upms:sysrole:update`，避免无该权限的会话（含 C 端 token）读取租户菜单结构。

### 2. 请求体与写入归属

- 新增 `TenantIdRequestModule`：`*.api.entity` 的 `tenantId` 在 JSON 反序列化时被移除并标记为 ignorable，客户端无法通过请求体指定归属；显式跨租户 DTO（如 `SysTenantMenuDTO`）不受影响。
- `MyMetaObjectHandler`：
  - INSERT：租户上下文存在时强制覆盖实体 `tenantId`；
  - UPDATE：租户上下文存在时清空实体 `tenantId`，默认字段策略下不会生成 `tenant_id` 的 SET，禁止改挂租户；
  - 无上下文的后台任务保留显式租户值。

### 3. 缓存与异步上下文

- `SocialAccountServiceImpl#getSocialAccountById` 缓存 key 加入当前租户；`appId` 维度缓存保持全局唯一语义，由登录绑定校验负责归属。
- `CatalogVectorRecallService` 异步重建目录向量时快照/恢复租户上下文，避免 `tenant_id = NULL` 清空缓存。

### 4. 租户生命周期认证闭环

- `RemoteTenantService#getAccess` 返回租户状态与授权起止时间；新增 `TenantAccessGuard`，管理端密码/短信/配送登录以及 C 端微信/短信/密码登录均在签发 token 前校验；平台租户始终放行。
- 在线记录覆盖 TOB/TOC：`ArynSaTokenListener` 为商城端也写在线记录并标记 `deviceType`；管理端在线列表只展示 TOB（历史无 `deviceType` 记录按 TOB 兼容）。
- `doRenewTimeout` 同步续期在线记录，避免 token 续期后记录先过期、停用时无法定位会话。
- 新增 `TenantSessionRevoker`：租户停用/删除时在事务提交后扫描该租户在线记录并逐个 `kickoutByTokenValue`；新增 `TenantExpiryJob` 每小时回收授权到期租户的残留会话。
- 修复自助注册：状态由误用的 `YES("1")` 改为 `NORMAL_STATUS("0")`；`packageId` 在插入前赋值；校验套餐存在且启用；未采集密码时生成随机不可用密码（该账号走短信登录）。
- `registerSysTenant` 用 finally 恢复进入前租户上下文，避免操作日志被记到新租户名下。

### 5. 配送凭证私有访问

- 存储策略新增 `uploadPrivateFile` 默认方法；本地存储将配送凭证落到 `{tenantId}/private/` 子目录并返回带 `private/` 的 URL。
- `PrivateFileUrlSigner` 使用共享 Redis 中的 HMAC 密钥为私有 URL 追加 `expires + signature`（24 小时）；下载端点对 `private/` 路径强制校验，公开商品图保持匿名。
- `RemoteMaterialService#signPrivateUrls` 在配送任务凭证读取时签名（不持久化，避免过期快照失效）；素材管理页对 `delivery-evidence` 记录同样签名；素材编辑保留原 URL 与 `bizTag`，避免把 24h 签名快照写回数据库或把私有素材改成公开素材。
- 本地文件解析器允许 `private/<uuid>.<ext>`，仍禁止路径穿越与非法扩展名。

### 6. 门禁修复

- `TenantInterceptorBypassAuditTest` 改为「定位方法声明 + 回看上一个成员边界」解析，能识别注解间夹 `@Select` 的 Mapper 方法；登记 `GoodsRankMapper`、`PayRefundOrderMapper` 已审计旁路。

## 三、验证

| 项 | 命令 | 结果 |
|----|------|------|
| 多租户/登录/签名专项 | `mvn -B test -pl aryn-boot -am '-Dtest=Tenant*Test,MyMetaObjectHandlerTest,TenantIdRequestModuleTest,ArynDubboRequestFilterTest,TenantAccessGuardTest,PrivateFileUrlSignerTest,SysOnlineUserControllerTest,ArynSaTokenListenerTest,TocLoginServiceTest,DeliveryAuthServiceTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过：common-mybatis 10、common-dubbo 6、auth 31、upms 9、boot 22 |
| 后端全量 | `mvn -B test -pl aryn-boot -am` | **BUILD SUCCESS**（56.1s，全部模块 SUCCESS，aryn-boot 69 项通过） |

## 四、边界与后续

- **存量 TOC 会话**：改造前商城端未写在线记录，部署前签发的 TOC token 在停用租户时不会被扫描撤销；会在 token 过期前继续有效。新登录已覆盖。
- **存量配送凭证**：改造前上传的凭证仍位于租户根目录，物理文件未迁移，仍可匿名访问；新上传凭证进入 `private/` 并受签名保护。建议后续做一次存量文件迁移或在业务上重新上传。
- **非本地存储**：S3/OSS 等策略的私有性取决于桶 ACL/私有读策略；本次仅确保本地存储路径与签名链路，不改变对象存储配置。
- **租户级撤销依赖在线记录**：若 Redis 在线记录被清空而 token 仍有效，撤销会漏掉该会话；这是当前“账号停用踢线”同源的边界。
- **免登公开读**：本次闭环的是登录与已登录会话；租户停用/到期后，带 `tenant-id` 的免登公开读接口（如商品目录）仍可能返回该租户公开数据。若需要整租户下线，应在网关/过滤器增加租户状态检查。
- **无 DDL**：本次没有新增表、字段或索引，不需要 Boot/Cloud 增量 SQL。
- **Cloud 滚动发布顺序**：`RemoteTenantService#getAccess`、`RemoteMaterialService#signPrivateUrls` 是新增 Dubbo 契约，
  必须先部署 upms，再部署 auth / order；Boot 聚合包无此顺序问题。
