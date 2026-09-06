# 员工账号与配送员管理重构

本需求用于解决通用用户角色、配送员资料、配送资格和商城账号绑定职责混淆的问题。

- [员工账号与配送员管理重构方案](员工账号与配送员管理重构方案.md)

核心结论：系统设置中的“用户管理”实际承担员工账号管理；配送员资料和配送资格由“配送员管理”负责；商城账号绑定是配送工作台免重复登录的可选能力。

一句话规则：用户管理创建“人和登录账号”，配送员管理创建“配送身份和资格”，商城账号绑定决定是否能从商城个人中心进入配送工作台。

## 落地范围（2026-09-06 已实施）

- 后端 UPMS：`delivery_staff` 列为受保护角色（`CommonConstants.PROTECTED_DELIVERY_ROLE_CODE`），用户新增拒绝直接授予、编辑剔除请求中的受保护角色并合并保留已有配送资格；删除员工账号前经订单域远程契约 `RemoteDeliveryAccountService.hasActiveDeliveryStaff` 检查配送关联（fail-closed）；`/upms/sysrole/list` 补充返回 `roleCode`；员工列表增加配送资格只读摘要。
- 管理后台：员工账号表单过滤受保护角色并以“已具备配送资格”只读标签展示；页面文案统一“员工账号/登录用户名/员工昵称/后台角色”；列表增加配送资格列与“返回配送员管理”入口；配送员向导修正“员工管理”文案、增加“去创建员工账号”入口、步骤与提交按钮文案对齐方案（创建并开通配送资格 / 仅创建配送资料）。
- 数据库：`37employee_role_scope.sql`（boot/cloud 双份）——幂等补齐每租户“普通员工”（ROLE_STAFF）角色、菜单“用户管理”更名“员工账号”、输出重复角色编码与“有配送角色无配送资料”预检查、条件补齐 `(tenant_id, role_code)` 唯一约束；已登记 `build-full-sql.mjs` 并重建 `aryn_boot_full.sql`。
- 遗留缺口修复：`db/cloud/3aryn_nacos.sql` 的 aryn-order-biz-dev.yml 租户白名单补登 `delivery_qualification_operation` 并重算 md5（上轮 34 号脚本的遗留，导致 `TenantConfigurationConsistencyTest` 存量失败）。

