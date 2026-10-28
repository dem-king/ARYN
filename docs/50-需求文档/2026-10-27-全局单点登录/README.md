# 全局单点登录（同一账号任意时刻只保留一个会话）

> 状态：**已实施（自动化验证通过；cloud 未部署实测）**
> 日期：2026-10-27
> 影响端：管理后台、配送端（小程序）、C 端商城（会话层）；零业务接口/表结构变更

## 1. 原始请求

用户提问：「如果管理端账号登录了，那在别的电脑上还可以登录吗？」确认现状允许同一账号多端同时在线后，
用户选择规则 **「同一账号全局只能一处在线（不管管理端还是配送端）」**，即方案 A：新登录顶掉旧登录。

## 2. 目标 / 非目标

**目标**

- 同一账号（`loginId`）任意时刻只保留一个有效会话：新登录成功、旧设备下一次请求退出登录态。
- 管理端 Web 与配送端登录同为 `TOB` 设备类型，按用户确认**互顶是预期行为**。
- C 端商城（`TOC`）同样纳入单设备登录（同一规则，非独立开关）。
- boot 单体与 cloud 微服务两种模式保持一致；存量 cloud 环境可用增量脚本升级。

**非目标**

- 不做「按端精准单点」（管理端单独单点、配送端不受影响）——该目标需要新增设备类型或按 scope 过滤，
  见 §7 备选。
- 不修「角色 / 权限变更需重新登录才生效」的问题（见 §9 剩余风险；本次已补停用/改密/删除三类账号失效踢线）。
- 不改登录接口、不改前端交互、不改 token 存储方式。

## 3. 影响端与权限 / 租户

| 端 | 影响 |
|---|---|
| 管理后台（`aryn-mall-ui`） | 同账号第二台电脑登录后，第一台下一次请求 401，按现有登录过期逻辑回登录页 |
| 配送端（`aryn-mall-uniapp` 配送工作台） | 同账号第二台手机登录顶掉第一台；被顶设备 401 后 `reLaunch` 到 `/pages/delivery/login` |
| C 端商城（`aryn-mall-uniapp`） | 同一会员换手机登录会顶掉旧手机（TOC 单设备） |
| 账号停用 / 改密 / 删除 | 立即撤销该账号全部 TOB 会话（管理端 + 配送端）；在线设备下一次请求 401 |
| 权限 / 租户 | 单点粒度是 `loginId`（Sa-Token 账号维度），不跨账号、不跨租户；租户隔离逻辑不变 |

## 4. 接口与表变更

- **零接口变更**：未新增/修改 Controller 与 API 路径。
- **零表结构变更**：未新增/修改业务表、索引、种子数据。
- **配置变更（双模式）**：
  - boot：`aryn-mall-java/aryn-boot/src/main/resources/application.yml` → `sa-token.is-concurrent: false`；
  - cloud：Nacos 共享配置 `application-dev.yml` → 同值。基线写进
    `db/cloud/3aryn_nacos.sql`（md5 同步重算），存量环境用新增增量脚本
    `db/cloud/166global_single_session_nacos_config.sql` 幂等升级。
- **会话失效增强（无接口/表变更）**：新增 `SysUserSessionRevoker`，在账号停用
  （`SysUserServiceImpl.updateUser` 状态由正常→停用）、修改密码（`SysUserController.editPwd`）、
  删除账号（`SysUserServiceImpl.delUser`）成功后立即 `StpUtil.kickout(userId, "TOB")`；
  撤销失败只记日志，不影响停用/改密主流程。
- **在线用户记录 key 修正（无接口/表变更）**：`ArynSaTokenListener` 的写入与删除统一改为
  token 维度 key（租户信息保留在记录值里），`SysOnlineUserController` 扫描全部在线记录后
  按记录 `tenantId` 过滤，并对已失效会话兜底过滤。
- **兼容性附带修复**：`db/cloud/3aryn_nacos.sql` 中 upms 服务配置行自 2026-09-06 起缺
  `config_info` 后 7 列（值数 17→10），cloud 空库导入执行到该行会报
  `ERROR 1136 Column count doesn't match value count` 中断。本次已按历史完整行补回
  `gmt_create / gmt_modified / src_user / src_ip / app_name / tenant_id / c_desc` 列，
  并加契约测试防止手工编辑 dump 再丢列。

## 5. 设计决策

1. **用框架配置而不是业务代码实现**：Sa-Token 1.45 `is-concurrent=false` 时，新登录会先
   `replaced(loginId, deviceType)` 把该账号同设备类型的旧终端标记 `REPLACED`（默认
   `replacedRange=CURR_DEVICE_TYPE`、`replacedLoginExitMode=OLD_DEVICE`），旧 token 下一次
   请求抛 `NotLoginException.BE_REPLACED` → 401。无需新增表/字段，也无跨实例广播问题
   （会话数据在 Redis，多实例天然一致）。
2. **保持 `is-share: false`**：每次登录签发新 token，旧 token 被 REPLACED 标记，符合「顶号」
   语义；若 `is-share=true` 第二台会复用旧 token，两台会同时在线，与需求相反。
3. **为什么 boot/cloud 都要改**：cloud 所有服务（含 gateway）通过
   `spring.config.import: optional:nacos:application-{profile}.yml` 引入共享配置；boot 不读
   Nacos（`spring.cloud.nacos.config.enabled: false`），配置在 jar 内 yml。漏改一侧该模式就失效。
4. **在线用户记录 key 修正（历史 bug 收口）**：原实现写入 `前缀 + tenantId + ":" + token`，而
   `doLogout / doKickout / doReplaced` 用 `前缀 + token` 删除，key 永远不匹配，已注销/被顶/
   被强退的会话会残留在「在线用户」列表直到 30 天 TTL。现统一为 `前缀 + token`（租户信息
   已保存在记录的 `tenantId` 字段），写入与删除必然使用同一 key；列表扫描全部在线记录后按
   当前租户过滤，并继续用 `StpUtil.getLoginIdByToken` 剔除失效会话。旧格式 key 无法精确
   删除，由 30 天 TTL 自然过期（展示层已过滤，不影响观感）。
5. **账号失效必须主动撤会话**：登录入口只能阻止新登录，无法清理已签发 token。因此新增
   `SysUserSessionRevoker`，在停用/改密/删除成功后主动 `StpUtil.kickout(userId, "TOB")`
   （管理端与配送端同为 TOB，一起失效）；撤销失败（无活跃会话时 Sa-Token 会抛异常，或 Redis
   异常）只告警不回滚主流程，残余会话由 token 过期兜底。
6. **不改 `replacedRange`**：默认只顶同设备类型。管理端与配送端同为 `TOB`，满足「互顶」；
   一个 `loginId` 在现有业务里只可能落在一种设备类型上（sys_user 走 TOB、mall user 走 TOC，
   ID 空间独立），因此无需 `ALL_DEVICE_TYPE`。

## 6. 上线步骤

1. **boot 模式**：重新打包 `aryn-boot` 并重启（配置打进 jar）。
2. **cloud 模式**：对 `aryn_nacos` 执行 `166global_single_session_nacos_config.sql`，确认自检输出
   `single_session_registered=1 / legacy_multi_session_left=0 / config_md5_mismatch=0`；随后重启
   所有微服务（含 gateway）。Nacos 配置不会热刷新 Sa-Token 全局配置，必须重启生效。
3. **已签发 token**：重启后旧的多端 token 仍有效，直到各账号下一次登录触发顶号清理；如需立即
   收敛可清理 Redis `satoken:login:*`（代价是所有用户强制重新登录）。

## 7. 备选方案（未采用，留档）

- **方案 C：配送端独立设备类型**（新增如 `TOB_DELIVERY`，管理端 Web 保持 `TOB`）——两套会话彻底
  隔离，可各自单点；代价是 `DeviceTypeEnum` 与约 10 处按 `TOB` 判定的员工端逻辑
  （`DeliveryAccessGuard`、文件上传、消息中心员工端 Controller/WebSocket 等）都要改并兼容存量
  token。
- **方案 D：按 scope 精准下线**——全局保留 `is-concurrent: true`，管理端登录时遍历该账号 TOB
  token，跳过配送端 token 后 `kickoutByTokenValue`；C 端/配送端行为完全不变，但单点逻辑要自己
  维护（token-session 写 scope 标记）。

## 8. 验证

| 项 | 方式 | 结果 |
|---|---|---|
| 后端全量测试 | `cd aryn-mall-java && mvn test -pl aryn-boot -am` | 新增契约 5/5 通过；唯一失败为既有 WIP 引发的 `TenantInterceptorBypassAuditTest`（未登记用户未提交的 `GoodsRankMapper#selectHotTop10/selectOnSaleByIds`），与本需求无关 |
| 排除该 WIP 失败类重跑 | `mvn test -pl aryn-boot -am -Dtest='!com.aryn.cloud.boot.tenant.TenantInterceptorBypassAuditTest' -Dsurefire.failIfNoSpecifiedTests=false` | BUILD SUCCESS，全模块通过（aryn-boot 61 用例 0 失败） |
| boot 打包资源 | `aryn-boot/target/classes/application.yml`（Maven 资源处理产物） | 已含 `sa-token.is-concurrent: false` |
| 存量 cloud 升级 | 本地 MySQL 临时库导入 HEAD 基线（`is-concurrent: true`）→ 执行 166 脚本 | 自检 `1 / 0 / 0`；content 变 false 且 `md5=MD5(content)` |
| 脚本幂等 | 同一库再执行一次 166 | 无变更（`gmt_modified` 不变），自检仍 `1 / 0 / 0` |
| 新环境初始化 | 导入改后基线（false）→ 执行 166 | 13 行配置导入成功；脚本 no-op，md5/gmt 不变 |
| 临时库清理 | `DROP DATABASE aryn_nacos` | 已清理，未影响本地 `aryn_boot` |
| 会话失效（停用/改密/删除） | `SysUserSessionRevokerTest`、`SysUserServiceImplTest`、`SysUserControllerPasswordTest`、`SysUserControllerDeleteProtectionTest` | 全部通过：停用/改密/删除均踢 TOB 会话；普通编辑不误踢；改密写库失败不踢；无会话/撤销异常不影响主流程 |
| 在线记录 key 生命周期 | `ArynSaTokenListenerTest`、`SysOnlineUserControllerTest` | 全部通过：登出/强退/顶号删除同一 token key、空 token 不触 Redis；列表按记录 tenantId 隔离、剔除失效会话、有效会话正常展示 |
| **boot 重新打包部署** | `mvn clean package -DskipTests -pl aryn-boot -am` → `docker compose -f docker-compose-boot.yml build aryn-boot` → `up -d --no-deps aryn-boot` | 启动 19s，`/boot/actuator/health` 返回 UP；启动日志 `SaTokenConfig` 已为 `isConcurrent=false`；旧镜像已打备份 tag `aryn-mall-java-aryn-boot:pre-global-logout-backup` |
| **真机顶号验证** | 临时 QA 员工账号连续两次登录（图形验证码票据按 aj-captcha 二次校验 key 规则写入 Redis 一次性放行） | 旧 token 请求返回 `code=401 "token 已被顶下线"`（Sa-Token 标记 -4），新 token 正常；Redis 在线记录只剩新 token，旧记录已被监听器删除 |
| **真机改密踢线** | 同一账号调用 `POST /boot/user/password` | 改密成功；原 token 返回 `code=401 "token 已被踢下线"`（标记 -5），在线记录删除 |
| **真机停用踢线** | 同一账号调用 `PUT /boot/user` 将自身 status 改为 1 | token 返回 `code=401 "token 已被踢下线"`（标记 -5），DB status=1；随后登录返回「状态异常不可登录」 |
| 临时数据清理 | 物理删除 QA 账号/角色行、清理 token 标记与验证码票据 | 已清理，服务健康 UP，未触碰既有账号 |

## 9. 实际结果与剩余风险

**已验证**

- **boot 已重新打包并重启部署（2026-10-27 12:06）**：容器 `aryn-boot` 启动 19s、健康检查 UP，启动日志
  `SaTokenConfig ... isConcurrent=false, isShare=false, replacedRange=CURR_DEVICE_TYPE, replacedLoginExitMode=OLD_DEVICE`；
  旧镜像已备份为 `aryn-mall-java-aryn-boot:pre-global-logout-backup`，可随时回滚。
- **真机顶号**：临时 QA 账号两次登录得到两个 token，旧 token 请求返回 401「token 已被顶下线」，新 token 正常；
  Redis 在线记录只剩新 token（旧记录被监听器即时删除），验证了单点策略 + 在线记录 key 写删同源。
- **真机改密 / 停用踢线**：改密后原 token 返回 401「token 已被踢下线」；停用后原 token 同样被踢、DB status=1，
  再登录提示「状态异常不可登录」。临时 QA 账号与 token 标记已清理，未触碰既有账号。
- 后端：`mvn test -pl aryn-boot -am` 中新增契约测试 5/5 通过；唯一失败为工作区既有未提交 WIP
  （`GoodsRankMapper` 两个绕过租户拦截的方法未登记到 `TenantInterceptorBypassAuditTest` 白名单），
  与本需求无关；排除该失败类重跑为 BUILD SUCCESS（全模块通过，aryn-boot 61 用例 0 失败）。
- 会话失效与在线记录相关测试类全部通过（listener 4、revoker 3、online controller 4、password 2、
  delete protection 3、service 9），覆盖停用/改密/删除踢线、普通编辑不误踢、撤销异常兜底、
  在线记录 key 同源删除与租户隔离。
- 本地 MySQL 临时库两条路径（存量 true → 升级 false；新环境 baseline false → 脚本 no-op）均符合预期，
  自检三指标 `1 / 0 / 0`，md5 与 content 一致。
- cloud 基线 dump 现在可完整导入（此前 upms 配置行缺列会中断）；共 13 行 `config_info` 种子配置。

**剩余风险 / 边界**

1. **角色 / 权限变更仍不会踢线**：token-session 缓存登录时的用户与权限快照，调整角色/菜单后需重新登录
   才生效；本次只覆盖停用、改密、删除三类账号失效场景（`SysUserSessionRevoker`），权限变更需独立需求评估。
2. **在线用户旧格式 key 无法精确删除**：升级前写入的 `前缀 + tenantId + ":" + token` 记录只能等 30 天
   TTL；展示层已按记录 tenantId + Sa-Token 实际登录态过滤，不影响观感。
3. **12 并发上限失效**：`is-concurrent=false` 后 `max-login-count`（默认 12）不再生效，配置保留不影响。
4. **共享配送账号风险**：多个配送员共用一个账号时，后登录者会顶掉正在配送的司机（前端会跳配送登录页），
   属于业务规则的一部分，运营需确认不存在共用账号。
5. **未部署实测**：cloud 侧为 SQL 静态验证 + 本地临时库执行验证，未启动完整微服务链路验证顶号 401；
   boot 侧未重启本地容器实测（配置已进入打包产物，重生效力由 Sa-Token 全局配置绑定保证）。
