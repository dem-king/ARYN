# 2026-11-05 平台管理员角色不可见与不可维护

## 需求

用户截图反馈：以平台租户内的租户管理员 `admin` 登录管理端「系统设置 → 角色管理」，仍能看到
「平台管理员」角色，并且行内出现「修改 / 删除」入口。用户认为：

1. 平台管理员是系统最大权限身份，任何角色都不应能修改/删除该角色或改写其权限；
2. 平台管理员的 `system` 账号不应被任何角色账号看到，不应显式出现在系统页面。

## 结论（设计口径）

用户的判断成立：**平台/系统身份不应作为租户 RBAC 数据暴露在页面上**。但不必删除
`ROLE_PLATFORM_ADMIN` 数据行——它是权限下发的技术载体，删除会破坏 `sys_user_role`、
`sys_role_menu` 和迁移脚本。正确做法是把该角色收敛为「系统内置角色」：

- **不可见**：角色分页/列表/详情、指定角色菜单读取都不返回 `ROLE_PLATFORM_ADMIN`；
- **不可维护**：新增（含同编码）、修改、删除、保存角色菜单一律拒绝，且对平台管理员会话同样
  拒绝（原实现只拦租户管理员，平台管理员仍可改，等于「最大权限角色可被削权」）；
- **权限不落可变数据**：平台管理员的权限与登录菜单树改为由「全部可见菜单」动态推导，
  不再依赖可被编辑的 `sys_role_menu`，因此「没有人能改它的权限」是代码层不变量，而不是靠 UI 隐藏；
- **账号不可见**：`system`（`sys_user.platform_admin='1'`）从员工账号列表过滤，按 ID 读取详情/
  完整手机号/改绑手机号也一律按「用户不存在」处理，堵住「改手机号 → 短信登录接管」的越权路径；
- 数据行保留、角色菜单行保留（可被 SQL 维护），仅作为内部实现，不再有可操作入口。

## 实施

| 层 | 文件 | 内容 |
|---|---|---|
| 公共常量 | `CommonConstants` | 新增 `isSystemBuiltinRole(roleCode)`（当前仅 `ROLE_PLATFORM_ADMIN`） |
| 角色 API | `SysRoleController` | 分页 SQL 排除内置角色；`/list` 排除；`/{id}` 返回「角色不存在」；新增/修改/删除对内置角色一律拒绝并移除 `PlatformAdminSupport` 依赖 |
| 角色菜单 | `SysRoleMenuServiceImpl` | `saveRoleMenu` 对内置角色无条件拒绝（原先平台管理员可改） |
| 菜单 API | `SysMenuController` | `/menu/role/{roleId}` 对内置角色返回「角色不存在」，不再暴露其授权数据 |
| 权限推导 | `SysUserServiceImpl#findUserInfo` | 平台管理员的 `permissions` = `sys_menu` 全部非空权限串（新 mapper 方法 `selectAllPermissions`） |
| 菜单树 | `SysMenuServiceImpl#getLoginUserMenuTree` | 平台管理员在本租户上下文下取全部菜单（新 mapper 方法 `listAllMenus`），不再取角色菜单交集 |
| 员工账号 | `SysUserMapper.xml#adminPage`、`ISysUserService#getVisibleUserById`、`SysUserController` | 列表过滤 `platform_admin='1'`；详情/手机号/改绑手机号统一走可见性查询 |
| 前端兜底 | `views/upms/role/index.vue` | `isProtectedRole` 纳入 `ROLE_PLATFORM_ADMIN`，旧数据/旧缓存下也不出现修改删除按钮 |

不变项：`ROLE_ADMIN`（超级管理员）仍是租户可分配角色（可改权限、不可改码/删除，前端保护保持）；
`delivery_staff` 保护逻辑不变；`syncRoleMenus` 不裁剪平台管理员角色；无库表结构变更，无需增量 SQL。

## 二期：历史登录日志与在线用户不显示平台管理员

用户追加要求：**其他用户查看历史登录日志、在线用户时，不能看到平台管理员**。

登录日志（`sys_login_log`）与在线记录（`SysUserOnline` Redis 记录）都只保存用户名，
因此新增 `PlatformAdminVisibilitySupport`：当前会话是平台管理员时返回空集合（保留完整审计），
否则返回当前租户内 `sys_user.platform_admin='1'` 的用户名集合，旁路视图按该集合过滤。

| 层 | 文件 | 内容 |
|---|---|---|
| 可见性组件 | `PlatformAdminVisibilitySupport`（新增） | `hiddenPlatformAdminUsernames()`：平台管理员会话返回空；否则按 `sys_user.platform_admin='1'` 查用户名集合 |
| 数据访问 | `SysUserMapper#selectPlatformAdminUsernames` | 租户拦截器限定当前租户；平台管理员账号只存在于平台租户 |
| 登录日志 | `SysLoginLogController` | `/page` 与 `/statistics` 对非平台管理员会话追加 `user_name NOT IN (平台管理员用户名)`；`/my` 自服务口径不变 |
| 在线用户 | `SysOnlineUserController` | 列表按隐藏用户名过滤；`/onlineuser/{token}` 强退对平台管理员会话按「不存在」拒绝（防止 token 泄露后被强退） |

边界：过滤按**用户名**进行（表结构无 user_id）；平台管理员账号不允许经员工账号接口改名，
因此有效账号的历史记录可被稳定过滤。若未来给登录日志/在线记录补充 `user_id`，可改为按 ID 过滤。

## 三期：操作日志（sys_log）不显示平台管理员

用户确认操作日志同样需要隐藏。

| 层 | 文件 | 内容 |
|---|---|---|
| 操作日志 | `SysLogController` | `/page` 追加 `(user_name IS NULL OR user_name NOT IN (...)) AND (create_by IS NULL OR create_by NOT IN (...))`；`/{id}` 命中平台管理员记录返回「日志不存在」；平台管理员会话不过滤 |

`sys_log` 的用户名落在 `user_name`（异步落库时 `create_by` 可能为空），两个字段都做 null-safe
过滤，避免 `NOT IN` 的三值逻辑误伤存量记录；按 ID 直查同样返回「日志不存在」，避免泄露操作内容。

## 四期：所有"查询用户 / 查询员工账号"入口都不返回平台管理员

用户口径：**平台管理员账号不属于任何一个租户，是整个系统平台的管理员**；系统中所有查询用户、
查询员工账号的地方都不应该能查到他。

平台管理员在存储上仍绑定平台租户（`sys_user.tenant_id` 非空，RBAC/菜单/登录租户上下文依赖它），
这是内部实现；对外一律按「系统级身份」处理：租户侧员工查询、员工选择器、员工统计全部排除。

| 层 | 文件 | 内容 |
|---|---|---|
| 员工查询 SQL | `SysUserMapper.xml` | `adminPage`（一期已过滤）、`selectMessageRecipients`（消息受众/客服候选）、`countCustomerServiceStaff`（客服坐席资格）、`searchNormalStaff`（新增，配送员开户等员工搜索）、`countTenantStaff`（新增，租户员工数）全部 `platform_admin = '0'` |
| 跨模块过滤 | `SysUserMapper.xml#selectPlatformAdminIds` + `RemoteSysUserService#getPlatformAdminUserIds` | 向销售/配送等下游模块提供当前租户平台管理员 ID 集合，用于业务视图排除 |
| 远程用户查询 | `RemoteSysUserServiceImpl` | `getUserById` 命中平台管理员返回 null；`getUserInfoByIds` 剔除平台管理员；`searchNormalUsers` 改走 `searchNormalStaff`（排除）；登录用的 `getUserInfo(username/phone)` 保持可用（否则平台管理员无法登录） |
| 租户概览 | `TenantOverviewService` + `ISysUserService#countVisibleStaff` | 「管理端账号」数排除平台管理员 |
| 销售员 | `SalesStaffServiceImpl` | 分页/详情/在职下拉/历史名称回溯排除平台管理员绑定的销售员；编辑、离职、状态变更同样拒绝；新增/编辑经 `requireSysUser` 已被远程查询拦截 |
| 配送员 | `DeliveryStaffServiceImpl` | 配送员管理分页按员工账号排除；向导开户的员工搜索走 `searchNormalUsers`（已排除），`onboard` 经 `getUserById` 被拦截 |
| 消息域 | `RemoteMessageStaffServiceImpl`（SQL 层） | 员工受众、客服坐席候选、客服坐席资格校验均排除平台管理员 |

边界：
- 平台管理员账号的**历史业务数据**（例如其被误登记为销售员后产生的订单/结算/绑定）不会自动删除，
  代码只保证「用户/员工查询」不再返回它；如需清理需单独确认（涉及业务/财务记录）。
- 平台管理员仍可正常登录；登录认证接口 `getUserInfo(username/phone)` 不能过滤，否则最高权限账号无法登录。

## 验证

- 后端定向测试（`aryn-upms-biz`）：
  - `SysRoleControllerProtectionTest`：内置角色 add/edit/del/getById 全拒绝；
  - `SysRoleMenuServiceImplTest`：内置角色角色菜单保存拒绝；
  - `SysUserServiceImplTest`：平台管理员账号不可见、权限来自全部菜单而非角色授权；
  - `SysUserControllerPhoneTest/PhoneUpdateTest/PasswordTest`：详情与手机号入口走可见性查询；
  - `SysLoginLogControllerPageTest`：登录日志列表/统计按平台管理员用户名过滤，平台管理员会话不过滤；
  - `SysLogControllerPageTest`：操作日志列表/详情隐藏平台管理员记录，平台管理员会话仍可见；
  - `SysOnlineUserControllerTest`：在线列表隐藏平台管理员会话，平台管理员会话仍可见；
  - `RemoteSysUserServiceImplTest`：远程用户查询（按 ID/批量/员工搜索）排除平台管理员；
  - `PlatformAdminUserQueryContractTest`：员工查询 SQL 契约（列表/受众/坐席/搜索/统计均含排除条件）；
  - `SalesStaffServiceImplTest`、`DeliveryStaffServiceImplTest`：销售员/配送员业务视图过滤回归。
- 本地重新打包重启后真实接口验证：`user/page` 与 `onboard-sys-user-search` 仅返回 3 个租户员工、
  `keyword=system` 返回 0 条；`sales/staff/page` 隐藏平台管理员绑定行（total 由 2 → 1），
  其详情返回「销售员不存在」；`sysloginlog`/`syslog` 列表分别按 117−7、61−16 过滤后返回；
  在线列表与客服坐席候选均不含 system。
- 编译：`mvn -pl aryn-upms/aryn-upms-biz -am test-compile`。
- 文档：`docs/40-接口与风险/README.md` 平台管理员条目同步更新。

## 边界/遗留

- 二期已覆盖历史登录日志与在线用户两处旁路视图；若还有其它面向租户的审计视图（如操作日志
  `sys_log`、仪表盘统计）需要同样隐藏，按 `PlatformAdminVisibilitySupport#hiddenPlatformAdminUsernames`
  的同一口径接入即可，本次未主动扩散。
- 平台管理员账号的用户名被按用户名过滤；当前系统没有改名入口（员工账号接口对平台管理员账号
  不可见、平台管理员角色也不可维护），若未来开放改名，登录日志/在线记录需改按 `user_id` 过滤。
- 新增菜单仍按既有 SQL 约定推导授权给平台管理员角色（`sys_role_menu` 行），但平台管理员
  实际权限与菜单树已不读取该数据，数据只作为 SQL 维护/审计载体，不再影响线上权限。
