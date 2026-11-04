# 2026-11-03 平台租户合并与平台管理员收敛（数据 + 代码 + 种子）

## 需求

**第一步（平台租户合并）**：管理端【平台管理 > 租户管理】里长期存在两个租户：

- 「系统租户」`1881232176465358849`：平台租户（`tenant_type=1`），只挂 `system` 管理员、角色、部门、
  存储配置，没有任何业务数据；
- 「悦航购」`1590229800633634816`：普通租户（`tenant_type=2`），承载全部业务数据与 `admin` 账号。

用户要求：**把悦航购变成系统租户（平台租户），把原来的系统租户物理删除**；删除时保留 `system`
管理员账号并迁入悦航购，同时修改初始化种子 SQL，让新环境初始化后只有一个平台租户。

**第二步（平台管理员收敛）**：合并后悦航购既是平台租户又是业务租户，`admin` 仍应是**租户管理员**
（只管理本租户业务），**只有 `system` 是唯一平台管理员**（可切换租户、维护平台配置、看到平台管理菜单）。

## 背景与根因

- 平台租户身份此前写死在代码常量里：后端 `CommonConstants.PLATFORM_TENANT_ID`、前端
  `ARYN_PLATFORM_TENANT_ID`，租户切换、平台配置守卫、菜单过滤、登录放行全部按固定 ID 判断。
  只改数据不改代码，悦航购即使标记成平台租户也拿不到平台管理菜单、过不了平台接口守卫；
  改常量又会把新环境种子（平台租户仍是系统租户）与其它部署全部带偏。
- 2026-10-29 已引入 `sys_tenant.tenant_type`（1 平台 / 2 普通）并把授权期判断改为数据驱动，
  但「当前会话是否平台租户」仍按 ID 判断，属于同一类技术债。
- 种子基线 `2aryn_boot.sql` / `2aryn_upms.sql` 固定写入两条租户，平台租户是纯运维占位；
  业务租户悦航购反而只能以普通租户身份使用。
- 第一步按「租户类型」判定平台身份后，平台租户里的**所有账号**都会被当作平台管理员——
  `admin`（业务租户管理员）也能看到平台管理菜单、切换租户，与「只有 system 是平台管理员」冲突。
  因此第二步把平台管理员收敛为**用户级身份**。

## 实施

### 一、平台身份数据驱动（后端）

| 文件 | 内容 |
|------|------|
| `ArynUser.java`（common-security） | 会话快照新增 `tenantType`（租户类型）与 `platformAdmin`（平台管理员） |
| `SysUser.java`（upms-api） | 新增非表字段 `tenantType`、`platformAdmin`；`SysUserServiceImpl#findUserInfo` 按租户类型 + `sys_user.platform_admin` 计算 |
| `SysUserVO.java`（upms-api） | 用户信息响应新增 `tenantType`、`platformAdmin`，前端据此判断平台管理员 |
| `SysUserMapper.java` | 新增 `selectPlatformAdminFlag`（用户级标记列不映射实体，避免被通用接口改写） |
| `PlatformAdminSupport.java`（upms，新增） | 当前会话是否平台管理员的统一判定 |
| `PlatformTenantGuard.java`（upms） | 平台配置守卫改用会话 `platformAdmin`（用户级），不再按租户类型/ID |
| `ArynTenantContextFilter.java`（common-mybatis） | 平台管理员可切换租户的判定改用会话 `platformAdmin` |
| `SysMenuServiceImpl.java`（upms） | 平台管理员在本租户上下文下不过滤菜单；租户管理员按租户套餐过滤；`/menu/tenant` 仅平台管理员返回全部菜单 |
| `SysRoleMenuServiceImpl.java`（upms） | 角色菜单授权：平台管理员不受套餐限制；`ROLE_PLATFORM_ADMIN` 角色仅平台管理员可维护 |
| `SysUserServiceImpl.java`（upms） | 平台管理员账号仅平台管理员可修改/停用/删除；`ROLE_PLATFORM_ADMIN` 角色仅平台管理员可授予 |
| `SysRoleController.java`（upms） | `ROLE_PLATFORM_ADMIN` 角色仅平台管理员可新增/修改/删除 |
| `SysTenantServiceImpl.java`（upms） | `syncRoleMenus` 不裁剪平台管理员角色 |
| `TenantAccessDTO.java`、`RemoteTenantServiceImpl.java`、`TenantAccessGuard.java`（auth） | 登录侧平台租户始终放行，按 `tenant_type` 判定（租户可用性仍是租户级） |
| `CommonConstants.java` | 删除 `PLATFORM_TENANT_ID`；新增 `ROLE_PLATFORM_ADMIN_CODE/NAME` |

### 二、平台身份数据驱动（前端）

| 文件 | 内容 |
|------|------|
| `packages/types/src/user.ts`、`@vben-core/typings/basic.d.ts` | `UserInfo` 增加 `tenantType`、`platformAdmin` |
| `layouts/basic.vue` | 平台管理员判定改为 `userInfo.platformAdmin === true`；租户切换下拉、品牌、切换引导同步 |
| `layouts/tenant-brand.ts`、`tenant-brand.test.ts` | 移除平台租户 ID 判断，仅按「当前生效租户」决定品牌 |
| `packages/constants/src/core.ts` | 删除 `ARYN_PLATFORM_TENANT_ID` |

### 三、种子与迁移 SQL（双模式）

| 文件 | 内容 |
|------|------|
| `db/boot/196platform_tenant_consolidation.sql`（新增） | 悦航购置 `tenant_type=1`、`auth_end_time=NULL`；`system` 迁入悦航购（部门→悦航购根部门）；悦航购无启用存储配置时继承平台租户默认本机配置；物理删除系统租户在 `sys_user/sys_role/sys_user_role/sys_role_menu/sys_dept/sys_tenant_menu/sys_storage_config/sys_login_log/sys_log/sys_material*/sys_user_wechat_binding/goods_specs*` 的数据与租户行；兜底自检残留 |
| `db/cloud/201platform_tenant_consolidation.sql`（新增） | 同一逻辑，目标库 `aryn_upms`（cloud 基线无 `sys_user_wechat_binding`，已剔除；商品域按 `aryn_product` 跨库清理） |
| `db/boot/197platform_admin_user.sql`（新增） | `sys_user` 增加 `platform_admin` 列；唯一平台管理员 `system` 置 1、其余置 0；平台租户下创建 `ROLE_PLATFORM_ADMIN` 角色并授予全部可见菜单；`system` 只挂该角色；删除 196 曾给 `ROLE_ADMIN` 补授的套餐外菜单（还原租户管理员） |
| `db/cloud/202platform_admin_user.sql`（新增） | 同一逻辑，目标库 `aryn_upms` |
| `db/boot/build-full-sql.mjs`、`aryn_boot_full.sql` | 登记 196/197 并重新生成全量 SQL |
| `db/boot/verify-full-sql.mjs` | 增加来源段与关键 token 校验 |
| `db/boot/25seckill_discount.sql` | 顺手修复既有阻塞：`seckill_order` 建表引用了不存在的 `order_item_id` 列索引，导致全量 SQL 空库初始化直接失败（与本次需求无关，但阻塞新环境验证） |

脚本特性：**幂等**；196/201 仅当目标租户「悦航购」存在时才执行任何写入/删除，避免在非种子环境误删
平台租户；无 DROP TABLE / TRUNCATE；含物理删除，执行前必须备份。

## 验证

| 项 | 方式 | 结果 |
|----|------|------|
| 后端单测 | `mvn test -pl aryn-auth,aryn-common/aryn-common-mybatis,aryn-upms/aryn-upms-biz -am`（定向用例） | 全部通过 |
| 全量构建 | `mvn package -pl aryn-boot -am` | BUILD SUCCESS |
| 全量 SQL | `node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs` | 静态校验通过 |
| 空库初始化 | 用 `aryn_boot_full.sql` 初始化全新库（库名临时替换） | 初始化成功；仅 1 个平台租户「悦航购」；`system` 为唯一平台管理员且挂 `ROLE_PLATFORM_ADMIN`（全部菜单）；`admin` 为租户管理员（套餐菜单）；旧租户 0 残留 |
| 迁移脚本 | 在 `aryn_boot`、`aryn_upms` 副本上执行 196+197 / 201+202（含重复执行） | 结果符合预期、二次执行无变更、残留自检为空 |
| 数据核对 | 本机 `aryn_boot` | `sys_tenant` 仅剩悦航购（`tenant_type=1 / auth_end_time=NULL`）；`sys_user.platform_admin` 仅 `system=1`；`system` 只挂 `ROLE_PLATFORM_ADMIN`（410 菜单）；`admin` 挂 `ROLE_ADMIN`（359 套餐菜单、0 套餐外菜单）；系统租户各表 0 行 |
| 接口 | 分别以 `admin`、`system` 新会话调用 | `admin`：`platformAdmin=false`、`/boot/tenant/page` 403、菜单树无「平台管理」；`system`：`platformAdmin=true`、`/boot/tenant/page` 200、菜单树含「平台管理」 |
| 页面（Playwright，1600×1000） | 分别注入两种会话打开 `#/platform/tenant` | `admin` 无平台管理菜单/租户切换、访问平台页被拦；`system` 侧栏含平台管理、租户列表仅「悦航购」且「长期有效」、无「系统租户」；控制台 0 error |

## 边界与部署注意

- **平台管理员唯一判据**是 `sys_user.platform_admin='1'` 且所属租户为平台租户；租户管理员即使位于
  平台租户也不是平台管理员。新增平台级菜单/权限授权不要再写死租户 ID。
- 平台管理员专属角色 `ROLE_PLATFORM_ADMIN` 持有全部菜单，不参与租户套餐；该角色与平台管理员账号
  都受服务端保护，租户管理员不能修改/删除/授予。
- 存量会话快照没有 `platformAdmin`：升级后平台管理员需重新登录（租户切换在重新登录前不生效）；
  被合并租户的旧会话应清理（本机开发环境直接清空 sa-token 会话）。
- 迁移脚本会删除「系统租户」全部数据（含登录/操作日志）；若某环境在系统租户下产生过业务数据，
  脚本末尾的残留自检会列出表名，需人工确认后再清理。
- 迁移后唯一平台管理员为 `system`，业务租户管理员为 `admin`（初始密码均为种子密码，登录后应改密）。
- 生产环境执行顺序：备份 → 执行增量 SQL（196→197 / 201→202）→ 发布后端（否则实体查询
  `platform_admin` 报未知列）→ 通知平台管理员重新登录。
