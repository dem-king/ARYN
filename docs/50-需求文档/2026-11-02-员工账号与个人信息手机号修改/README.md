# 员工账号与个人信息手机号修改

> 日期：2026-11-02
> 范围：管理端「系统设置-员工账号」修改员工手机号 + 管理端「个人信息」自助换绑手机号
> 约束：零表结构变更（仅菜单权限种子数据）；Boot / Cloud 双模式同时生效；配送员资料联系电话自动同步

## 一、背景

用户反馈：

1. 管理端「员工账号」编辑弹窗中手机号只能查看、不能修改（截图：字段 disabled + 「查看」按钮）；
2. 个人信息页手机号同样只读，提示「如需变更请联系企业管理员」，但企业管理员实际也改不了。

代码核对结论：

- 前端 `views/upms/user/form.vue` 编辑态对手机号输入框设置了 `:disabled="!!state.form.id"`；
- 后端 `SysUserController#edit` 显式 `sysUser.setPhone(null)`，编辑请求中的手机号一律丢弃；
- 列表/详情响应中 `SysUserVO.phone` 经 `@Desensitization` 脱敏，直接放开输入框会把 `176****2321` 写库；
- `sys_user` 已有全局唯一键 `uk_sys_user_phone`（仅对 `del_flag='0'` 生效，跨租户），手机号同时是管理端
  短信登录、配送员登录的凭据，属于账号接管级敏感字段；
- C 端小程序早已支持换绑手机号（`/app/userinfo/update/phone` + `/sms/update/{mobile}` + `SmsCodeVerifier`），
  管理端缺的是入口、权限与后端接口。

## 二、目标与非目标

**目标**

- 管理员（具备新权限）可在员工账号编辑弹窗内为员工换绑手机号；
- 员工本人可在「个人信息」页自助换绑手机号（当前密码 + 新手机号短信验证码）；
- 两个入口修改成功后都自动同步该员工未删除的 `delivery_staff.staff_phone`；
- 手机号全局唯一、脱敏值永不回写、操作留审计日志。

**非目标**

- 不修改用户名/登录账号；不修改 C 端商城用户手机号流程；
- 不强制目标员工下线（现有 token 不依赖手机号，管理员仍可用在线用户/停用处理）；
- 不要求旧手机号验证码（旧号丢失是常见场景，管理员入口承担兜底）；
- 不引入新表、字段或索引。

## 三、权限模型

| 能力 | 权限 | 说明 |
|---|---|---|
| 查看员工完整手机号 | `upms:sysuser:phone` | 既有能力，本次不变 |
| 修改员工手机号 | `upms:sysuser:phone:edit` | 新增独立按钮权限，不能与「查看」合并 |
| 重置员工密码 | `upms:sysuser:password` | 既有能力，风险等级与改手机号相同 |
| 本人换绑手机号 | 无额外权限 | TOB 登录态 + 当前密码 + 新号短信验证码 |

- 改手机号等价于可把号码换成自己的再用短信登录接管账号，因此按「重置密码」同敏感度管理：
  种子脚本向已拥有 `upms:sysuser:password` 授权的角色推导补授新按钮，不向普通员工角色放开。
- 查看完整号码与修改号码分开授权：只有查看权限的账号不能换绑。
- 菜单在登录时快照，执行种子后相关管理员需重新登录才出现按钮；接口侧权限即时生效。

## 四、接口契约

### 1. 管理员修改员工手机号

```
PUT /upms/user/{id}/phone
Permissions: upms:sysuser:phone:edit
Body: { "phone": "13800138000" }
Response: Result<Boolean>
```

校验顺序：

1. 手机号 trim 后非空，且匹配 `^1[3-9]\d{9}$`；
2. 目标用户存在（`getById`，租户拦截器限定当前租户，平台管理员切换租户后只能改该租户员工）；
3. 新号与当前号相同 → 拒绝；
4. 跨租户唯一：`ISysUserService#getCount` 按 phone 统计 `del_flag='0'` 用户，>0 → 「该手机号已被使用」；
5. 只更新 `id + phone`，捕获唯一键冲突兜底同样返回「该手机号已被使用」；
6. 成功后 best-effort 调用配送同步 RPC（失败只 warn，不回滚手机号更新）；
7. `@SysLog("修改员工手机号")` 记录操作；日志不记录完整手机号。

### 2. 本人自助换绑

```
PUT /upms/profile/phone
Body: { "phone": "13800138000", "code": "1234", "password": "当前密码" }
Response: Result<Boolean>
```

- 强制使用登录用户自己的租户上下文（前端固定 `isSwitchTenant:false`；平台管理员切换租户也不影响）；
- 校验顺序：手机号格式 → 当前用户 → 当前密码 → 同号 → 跨租户唯一 → 短信验证码
  （`SmsCodeVerifier` 原子校验 + 一次性消费 + 5 次错误作废）→ 更新 → 刷新会话用户快照 phone → 配送同步；
- 验证码发送复用既有 `GET /upms/sms/update/{mobile}`（登录态 + 手机号/IP/全局三级频控），无需图形验证码；
- 错误文案：`当前密码错误` / `验证码不合法` / `验证码错误次数过多，请重新获取验证码` /
  `该手机号已被使用` / `新手机号与当前手机号相同` / `手机号格式不正确`。

### 3. 配送员资料同步

`RemoteDeliveryAccountService#syncStaffPhone(sysUserId, phone)`（Dubbo）：

- 租户隔离由调用链上下文保证；
- 无未删除 `delivery_staff` 资料返回 false；号码相同直接返回 true；不同则只更新 `staff_phone`；
- 调用方 best-effort，同步失败记 warn，不阻断手机号修改（配送登录使用 `sys_user.phone`，已即时生效；
  `staff_phone` 是配送工作台展示/搜索用的资料快照）。

## 五、前端交互

- **员工账号**：编辑弹窗手机号行保留脱敏展示 + 「查看」，新增「修改」按钮（`v-access:code="'upms:sysuser:phone:edit'"`）；
  子弹窗输入新手机号 + 确认新手机号（前端一致性校验），提示「旧号将无法登录、配送联系电话自动同步」；
  成功后刷新详情与列表。通用编辑提交仍不带 phone，后端继续丢弃 phone 作为纵深防御。
- **个人信息**：「账号安全」区新增手机号行 + 「修改」按钮；弹窗包含新手机号、短信验证码（60 秒倒计时，
  复用 `sendCode('update', phone)`）、当前密码；成功后刷新用户信息。
- 手机号输入统一 `^1[3-9]\d{9}$`，错误由请求层统一提示。

## 六、数据与权限种子

- boot `db/boot/182sysuser_phone_edit_permission.sql` / cloud `db/cloud/184sysuser_phone_edit_permission.sql`：
  - `sys_menu` 新增按钮 `2110000000000000207`「修改员工手机号」`upms:sysuser:phone:edit`，
    父级「员工账号」`1491752531735490561`，sort 10，`app_base`；
  - 向已拥有同父 `upms:sysuser:password` 按钮授权的角色补授；
  - 向已开通同父 `upms:sysuser:password` 按钮的租户补 `sys_tenant_menu`；
  - 固定 ID + `INSERT IGNORE` 幂等，可重复执行。
- boot 全量脚本已登记并重建；`verify-full-sql.mjs` 增加关键 token/来源段校验。

## 七、验证清单

- [x] 后端定向单测：upms **35/35**（`SysUserProfileControllerTest` 20、`SysUserControllerPhoneUpdateTest` 11、
  `SysUserControllerPhoneTest` 4）；order **13/13**（含 `syncStaffPhone` 5 例）；全量 `mvn test -pl aryn-boot -am` 通过
- [x] SQL 契约测试：`SysUserPhonePermissionSqlContractTest` 通过；boot/cloud 脚本除 USE 外一致、独立权限、
  按 `upms:sysuser:password` 推导授权、全量脚本已含来源段；`node db/boot/verify-full-sql.mjs` 通过
- [x] 前端 `pnpm -F @vben/web-ele run typecheck` exit 0；3 个改动文件 eslint / prettier 全通过；
  `boot-url` 契约测试 3 passed
- [x] 运行态：boot 库执行种子（菜单 1 / 角色授权 2 / 租户授权 1，重复执行数量不变）；重建 `aryn-boot`
  容器后健康检查 200；管理员改 shy 手机号 `13200009999→13200008888` 成功，
  `sys_user.phone` 与 `delivery_staff.staff_phone` 同步为 `13200008888`
- [x] 运行态：自助换绑经 Vite dev 代理 `PUT /api/boot/profile/phone` 成功（system `17640212321→17640219999`），
  DB 与会话快照 phone 同步；手机号归属 count 旧号 0 / 新号 1；Redis 注入 `sms_code_<phone>` 模拟短信
- [x] 负向：无 `upms:sysuser:phone:edit` 的 shy 令牌返回 403；脱敏值 `132****9999` 返回「手机号格式不正确」；
  重复号返回「该手机号已被使用」；自助换绑错误密码/错误验证码均被拒
- [x] 测试数据已回滚（system/shy 手机号、配送员 `staff_phone`、会话快照 phone、测试验证码 key），
  权限种子与会话权限保留

## 八、边界与后续

- 自助换绑依赖真实短信服务；本地开发可用 Redis 写入 `sms_code_<手机号>` 模拟验证码（仅联调）。
- 管理员改号不需要员工短信验证码（管理员不掌握员工新号）；兜底场景是员工旧号丢失。
- 改号不强制目标员工下线；如需强制重登，应新增 `PHONE_CHANGED` 会话失效子原因并扩展前后端契约，另行评估。
- 目标员工若在线，其 Redis 会话快照中的 phone 仍是旧值，直到重新登录或下一次刷新用户信息；
  不影响登录鉴权（token 不依赖手机号）。
- 配送同步是跨服务 best-effort：极端情况下 `sys_user.phone` 已更新而 `staff_phone` 未同步，可在
  配送员管理中手工修正；未引入分布式事务。
- 若账号用户名历史上等于旧手机号（注册租户时自动生成），换号不会同步改用户名，需单独走改账号流程。
