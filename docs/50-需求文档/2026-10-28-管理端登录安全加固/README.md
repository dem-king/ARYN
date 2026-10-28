# 管理端登录安全加固

> 日期：2026-10-28
> 范围：管理端 Web（`aryn-mall-ui/apps/web-ele`）登录/退出链路、后端 TOB（管理端 + 配送端）认证与账号安全、部署模板
> 约束：无表结构变更（cloud 仅 Nacos 配置增量）；Boot / Cloud 双模式同时生效；不改 token 持久化介质

## 一、背景

用户提供管理端登录截图并质疑「登录接口明文传密码」，只读审查结论见仓库根目录
`登录代码安全审查_2026-10-28_17-53.md`：

- **DevTools 能看到密码本身不是漏洞**：HTTPS 下浏览器同样能看到加密前的表单值；用前端固定 AES/MD5
  掩盖密码不是正确方向，应核实传输、日志与会话保护。
- 但审查确认了 6 项真实问题，按优先级：
  1. 用户接口返回密码 hash（高）：`/user/info`、`/user/{id}` 直接返回 `SysUser` 实体，列表 SQL 也查 `password`；
  2. Sa-Token 完整 token 写入日志（高）：boot 与 cloud 基线均 `is-log: true`，登录/续期事件会打印完整 token，
     能读日志即可重放会话；默认有效期 30 天且无空闲超时；
  3. 退出登录不保证服务端会话撤销（高）：前端 `logoutApi` 用裸 `baseRequestClient`，不带 `satoken`、
     不做 boot 路径改写，服务端无法撤销，出现「界面已退出、旧 token 仍有效」；
  4. 密码登录缺少失败次数限制（中）：网页入口有滑块，但配送端登录复用同一套员工账号密码、没有同等保护；
  5. 新建/修改密码没有服务端强度约束（中）：空串/极短/弱口令可直接落库；
  6. 登录接口未限制 POST（中）：`@RequestMapping` 允许 GET 查询参数提交凭证，密码可能进入 URL 与访问日志。
- 部署侧另有两项关联问题：仓库 Nginx 模板只监听 80（无法据仓库认定生产已启用 TLS）、
  `/api` 代理重复输出冲突的 `Access-Control-Allow-Origin`；且 `Dockerfile` COPY 了不存在的
  `aryn-ui-pro.conf`，说明现有模板未必等于线上实际配置。

**非目标**：不引入前端固定密钥加密密码（传输安全由 HTTPS 承担）；不迁移浏览器 token 存储介质；
不改 C 端（TOC）登录会话策略；不新增表/字段。

## 二、修复内容

### 1. 密码 hash 不再出现在 HTTP 响应与日志（高）

- 新增 `aryn-mall-java/aryn-upms/aryn-upms-api/.../api/vo/SysUserVO.java`：不含 `password`，`phone`
  走 `@Desensitization(MobilePhoneDesensitization)`，保留 `roles`/`permissions` 等展示字段。
- `SysUserController`：`/user/info`、`/user/{id}` 返回 `SysUserVO.from(entity)`；`/user/page` 经
  `ISysUserService#adminPage` / `SysUserMapper#adminPage` 改为 `IPage<SysUserVO>`。
- `SysUserMapper.xml` 拆列片段：`adminPageSql`（不含 `password`）用于页面查询；新增 `authUserSql`
  （= adminPageSql + `password`）仅供 `selectUserByName`/`selectUserByPhone` 等认证链路使用。
- `LoginService` / `DeliveryAuthService` 在签发 token 前 `hxUser.setPassword(null)`，
  hash 不再随 `ArynUser` 写入 Redis token-session；`SecurityUtils.loginByDevice` 新增可带
  `SaLoginParameter` 的重载（仅供覆盖会话时长，禁止覆盖 `isConcurrent`/`isShare`）。
- 日志侧：boot `application.yml` 与 cloud Nacos 的 `com.aryn.cloud.upms.mapper`、
  `com.aryn.cloud.user.mapper` 由 DEBUG 降为 INFO——新增/改密 SQL 参数含 BCrypt hash。

### 2. Sa-Token 令牌日志关闭 + TOB 会话缩短（高）

- boot `aryn-boot/src/main/resources/application.yml`：`sa-token.is-log: false`；cloud 共享配置
  `application-dev.yml` 同步（基线 `db/cloud/3aryn_nacos.sql`，存量环境升级脚本
  `db/cloud/170login_hardening_nacos_config.sql`）。审计日志仍由 `ArynSaTokenListener` 与登录日志表承担，
  两者不记录凭证。
- 管理端（TOB，含管理端 Web 与配送端）会话改为 **绝对有效期 12 小时**：
  `aryn-auth/.../config/LoginProtectionProperties.java`（`security.login.tob-timeout-seconds=43200`）
  生成 `SaLoginParameter.setTimeout(...)`；`LoginService.login` / `smsLogin` / `deliveryLogin`
  与 `DeliveryAuthService.exchange` 四处生效。C 端 TOC 仍沿用全局 `sa-token.timeout`（30 天）。
- **未启用空闲超时（2h）**：Sa-Token 按登录维度空闲超时依赖全局 `dynamic-active-timeout=true`，
  开启后存量 token（无最后活跃时间记录）会被判定为已冻结，等于发布即强制全端重新登录；
  C 端商城不适合承担该影响，留作后续维护窗口再评估（见「边界与后续」）。

### 3. 退出登录链路（高）

- 前端 `apps/web-ele/src/api/core/auth.ts`：`logoutApi` 由裸 `baseRequestClient` 改走统一
  `requestClient`（自动注入 `satoken`、执行 `rewriteBootUrl`、带 `Accept-Language`），
  保留 `headers: { isSwitchTenant: false }`；`refreshTokenApi` 不动（刷新请求不应带已过期 token）。
- 前端 `apps/web-ele/src/store/auth.ts`：服务端退出失败不再静默，改为脱敏告警后仍清理本地态；
  新增重入标记 `serverLogoutPending`，避免「退出 401 → 拦截器再调 logout」的无限递归。
- 后端 `TobTokenController#logout`：保持 `@DeleteMapping`；补注释明确「Sa-Token 1.45 logout 对无 token /
  已失效 token 幂等，但 Redis 等基础设施异常必须上抛」，不吞异常假装退出成功。
- 契约：前端 `login-security.test.ts` 断言 `logoutApi` 必须用 `requestClient.delete('/auth/token/logout'`、
  不得出现 `baseRequestClient`，`refreshTokenApi` 仍用 `baseRequestClient.post`；后端 `TobTokenControllerTest`
  锁 `logout` 保持 DELETE 且 `password`/`sms` 登录只接受 POST。

> 说明：「退出后旧 token 失效」由 Sa-Token `logout()` 按请求头 token 撤销会话的语义保证，
> 本次验证覆盖到路径改写与请求头链路（见「验证」），**未做真实接口联调**，见边界。

### 4. 密码登录失败限流与统一错误提示（中）

- 新增 `aryn-auth/.../service/LoginAttemptGuard.java`：`StringRedisTemplate` 执行 Redis Lua 原子计数。
  - 账号维度默认 **5 次 / 900 秒**，IP 维度默认 **30 次 / 900 秒**；每次失败滑动续期（持续尝试持续锁定）；
  - 登录成功后**只清账号维度**，不清 IP 维度（防用少量有效账号反复重置 IP 限制）；
  - **不区分账号是否存在**：不存在的账号同样计数；账号不存在 / 密码错误 / 空密码 / hash 非法
    统一返回「账号或密码错误」，锁定中返回「登录失败次数过多，请 N 分钟后再试」；
  - Redis 异常按 fail-closed 处理（Redis 与 Sa-Token 会话存储同源，此时登录本就不可用），
    错误日志只记录脱敏账号；
  - key 前缀新增 `CacheConstants.LOGIN_FAIL_ACCOUNT_KEY`（`login:fail:account:`）与
    `LOGIN_FAIL_IP_KEY`（`login:fail:ip:`）。
- `LoginService.login` / `deliveryLogin` 接入：**先查锁定 → 查用户 → 校验密码**，失败才计数；
  `passwordMatches` 对空密码、非法 hash 按不匹配处理（不再 500）；`LoginService` 由
  `RuntimeException` 改为业务异常 `ArynBusinessException`，原「用户不存在」提示已消除。
- 配置：boot `security.login.*`（见下表）；cloud 在 Nacos `aryn-auth-dev.yml`。

### 5. 密码强度策略（中）

- 新增 `aryn-common-security/.../util/PasswordPolicy.java`（服务端权威）：
  非空、长度 8~64、至少 1 个字母 + 1 个数字、15 条常见弱口令黑名单（大小写不敏感），返回中文错误文案；
  不强制特殊字符（避免催生 `Password@1` 式伪强密码）。
- 接入 `SysUserController.add`（新增员工）与 `editPwd`（改密）：改密顺序为
  **验旧密码 → 校验新密码强度 → 验两次一致**；旧密码为空时直接拒绝。
- 前端新增 `apps/web-ele/src/utils/password-policy.ts`（+ `password-policy.test.ts` 8 条用例），
  规则、弱口令表、错误文案与后端逐字一致，仅用于表单提前提示。
- 表单接入：`views/upms/user/form.vue` 新增态必填 + 强度校验，**编辑态放行并清空密码**
  （详情接口已不再返回 hash，避免编辑弹窗被密码规则卡死）；`views/upms/user/info/resetpwd.vue`
  新密码走同一策略。

### 6. 登录接口限定 POST（中）

- `TobTokenController`：`login`、`smsLogin` 由 `@RequestMapping` 改 `@PostMapping`；
  `logout` 保持 `@DeleteMapping`。前端原本就走 POST，此改动只封住「GET 查询参数提交凭证」的服务端口子。

### 7. 前端敏感日志脱敏

- `views/_core/authentication/login.vue`、`code-login.vue`：移除 `console.error(error)`
  （axios error 的 `config.data` 含明文密码/手机号与验证码），改为只输出
  `response.data.msg ?? message` 的脱敏提示；`code-login.vue` 短信失败改 `reject` 脱敏 `Error`。
- `register.vue`、`forget-password.vue`：移除打印完整表单的 `console.log`（含明文密码 / 邮箱），
  两页当前均为未接接口的占位页，保留说明注释。

### 8. 部署配置加固（`aryn-mall-ui/scripts/deploy/`）

- `Dockerfile`：`COPY ./aryn-ui-pro.conf` → `COPY ./aryn-ui.conf`（原文件名不存在，镜像构建必失败）。
- `aryn-ui.conf` 重写为 80 端口安全基线：`server_tokens off`、`client_max_body_size 100m`
  （对齐后端 `multipart.max-request-size: 100MB`）、gzip 保留、安全响应头
  （`X-Content-Type-Options` / `X-Frame-Options` / `Referrer-Policy` / `Permissions-Policy`）、
  `/api` CORS 收敛为单组「回显 Origin + 允许凭证 + `Vary: Origin`」、OPTIONS 预检 204、
  `proxy_hide_header` 剥离上游（`SaTokenConfigure`）可能输出的 CORS 头保证全链路只有一组 ACAO、
  静态资源 30 天缓存 + `index.html` no-cache、SPA 回退、`/actuator` 403；代理语义不变
  （`proxy_pass http://aryn-boot:9999/;`，boot 路径改写仍由前端完成，Nginx 不拼 `/boot`）。
- 新增 `aryn-ui-https.conf.example`：80 server（ACME `/.well-known/acme-challenge/` 放行 + 其余 301）
  与 443 TLS server 模板（证书路径占位、TLS1.2/1.3、安全套件、会话缓存、HSTS `max-age=31536000;
  includeSubDomains` + preload 谨慎说明、同样的安全头与 `/api` 反代）。
- 新增 `README.md`：80/443 两种部署形态、证书目录只读挂载（必须挂目录——Let's Encrypt
  `live/*.pem` 是指向 `archive/` 的软链）、HTTPS 启用与续期步骤、自检命令，并写明
  **「仓库模板不证明生产已启用 TLS，必须核实真实入口」** 的核实清单。
- 新增 `docker-compose.https.example.yml`（80/443 + `/etc/letsencrypt:/etc/nginx/certs:ro`）；
  现有 `docker-compose.yml` 未改动，默认仍只映射 `80:80`。

## 三、双模式配置与增量 SQL

### boot（`aryn-mall-java/aryn-boot/src/main/resources/application.yml`）

| 配置项 | 值 | 说明 |
|---|---|---|
| `sa-token.is-log` | `false` | 关闭完整 token 日志 |
| `security.login.enabled` | `true` | 限流开关，生产必须保持 true |
| `security.login.max-account-failures` / `account-lock-seconds` | `5` / `900` | 账号维度 5 次 / 15 分钟 |
| `security.login.max-ip-failures` / `ip-lock-seconds` | `30` / `900` | IP 维度 30 次 / 15 分钟 |
| `security.login.tob-timeout-seconds` | `43200` | TOB 绝对有效期 12h（TOC 仍 30 天） |
| `logging.level.com.aryn.cloud.upms.mapper` / `com.aryn.cloud.user.mapper` | `INFO` | 密码相关 SQL 不得 DEBUG |

### cloud（Nacos，基线 `aryn-mall-java/db/cloud/3aryn_nacos.sql` + 增量 `170login_hardening_nacos_config.sql`）

| data_id | 变更 | 基线 md5（已重算） |
|---|---|---|
| `application-dev.yml` | `sa-token.is-log: true → false` | `2513f24cc171bd9db07ebfd044785041` |
| `aryn-auth-dev.yml` | 追加 `security.login.*`（限流 + TOB 12h） | `d7f3c702310d04bf0ceaf6a69f1570ee` |
| `aryn-upms-biz-dev.yml` | `com.aryn.cloud.upms.mapper: debug → info` | `b5927b1f4f48c707462989c1231258e5` |
| `aryn-user-biz-dev.yml` | `com.aryn.cloud.user.mapper: debug → info` | `5ea4e3830c49a1184e2f9a55cbd6e65d` |

- 增量脚本**幂等**：先 `SET @var` 读 content，锚点匹配（`is-log: true` / `*.mapper: debug` / 缺
  `tob-timeout-seconds`）才 `REPLACE`/追加，随后 `md5 = MD5(content)`、`gmt_modified = NOW()` 并带
  `content <> @var OR md5 <> MD5(@var)` 条件；结尾自检列 `token_log_disabled` / `legacy_token_log_left` /
  `upms_mapper_info` / `user_mapper_info` / `login_guard_registered` / `config_md5_mismatch`。
- 执行：`mysql -u root -p aryn_nacos < 170login_hardening_nacos_config.sql`；仅配置、无 DDL，**无表结构变更**。
- 契约测试 `aryn-boot/.../security/LoginSecurityConfigContractTest`（6 条）把 boot yml、cloud 基线的
  content 与 md5、增量脚本锚点绑在一起，防止只改一处。
- 生效：Sa-Token 与 logging 为启动期读取；`security.login` 虽有 `@RefreshScope`（Nacos 刷新即可），
  但建议执行后滚动重启 `aryn-auth` / `aryn-upms-biz` / `aryn-user-biz` 一次到位；boot 模式需重新打包重启。

## 四、验证

| 项 | 命令 | 结果 |
|----|------|------|
| 后端全量 | `cd aryn-mall-java && mvn -B test -pl aryn-boot -am` | **BUILD SUCCESS**（exit 0，全部模块 SUCCESS；aryn-boot 模块 75 项测试通过，Failures/Errors 均 0） |
| 后端模块 | `mvn -B test -pl aryn-auth,aryn-upms/aryn-upms-biz -am` | **BUILD SUCCESS**（aryn-auth、aryn-upms-biz 均 SUCCESS） |
| 安全契约 | `mvn -B test -pl aryn-boot -am '-Dtest=LoginSecurityConfigContractTest,SingleSessionPolicyContractTest' -Dsurefire.failIfNoSpecifiedTests=false` | `LoginSecurityConfigContractTest` 6/6、`SingleSessionPolicyContractTest` 5/5 通过 |
| 新增/相关单测 | 随上述命令执行：`LoginAttemptGuardTest`(9)、`LoginServiceSecurityTest`(7)、`PasswordPolicyTest`(6)、`SysUserControllerPasswordTest`(7)、`TobTokenControllerTest`(3) | 全部通过（含 VO 无 password 字段的 Jackson 断言、弱密码拒绝、POST-only 反射断言、账号不存在/密码错误同文案、锁定先于查库、成功清 hash 且应用 12h） |
| 前端类型检查 | `cd aryn-mall-ui && pnpm -F @vben/web-ele typecheck` | exit 0 |
| 前端单测全量 | `pnpm exec vitest run apps/web-ele/src` | 89 files / 711 tests 全部通过 |
| 前端 lint/格式 | 改动文件 `pnpm exec eslint` / `prettier --check` | eslint exit 0、prettier 通过 |
| 双模式退出路径 | 实跑 `rewriteBootUrl('/auth/token/logout')` | cloud 保持 `/auth/token/logout`；boot 为 `/boot/token/logout`（与 Nginx `/api` 去掉前缀后的 `/boot/token/logout` 对齐） |
| 部署配置语法 | docker `nginx:stable-alpine` 对 `aryn-ui.conf` 与 `aryn-ui-https.conf.example` 执行 `nginx -t` | 均通过（443 模板用自签名证书占位；`listen ... http2` 有 deprecation 警告，属预期） |
| 部署镜像构建 | 修复后 `Dockerfile` + 模拟 `dist` 执行 `docker build` | 成功；镜像内 `/etc/nginx/conf.d` 仅 `aryn-ui.conf` + 既有 `mjs.conf`，示例/README 未进镜像 |
| 部署运行时 | 容器 `curl` 实测 | 安全头齐全、`Server: nginx` 无版本、OPTIONS 预检 204 且 ACAO 仅 1 个、`/actuator` 403、静态 hash 资源 30 天缓存、SPA 路由 200；模拟「自身输出 ACAO 的上游」做反代 E2E，出口仍仅 1 个 ACAO |
| 部署 HTTPS 模板 | 自签名证书 + 容器实测 | ACME 校验路径 200、其余路径 301、HTTP/2 + HSTS、TLS1.3/TLS1.2 握手正常、TLS1.1 按配置禁用、预检 204 单 ACAO、`/actuator` 403 |

说明：以上为本地真实执行结果；**未做真实接口联调**（未请求真实登录/退出接口），
**cloud 未部署实测**（配置契约测试覆盖基线内容与 md5，真实 Nacos 环境需按增量脚本升级并重启）。

## 五、边界与后续

- **HTTPS 真实入口需人工核实**：仓库原部署配置只有 `listen 80`、没有 443/证书挂载，本次新增的
  `aryn-ui-https.conf.example` 只是模板，**不能作为生产已启用 TLS 的证据**。必须核实：域名真实入口
  （是否经 CDN / SLB / Ingress 终止 TLS）、80 是否 301 到 HTTPS、443 证书链是否有效、
  若网关终止 TLS 则「网关 → nginx 回源」段是否可信内网、入口是否透传 `X-Forwarded-For` / `X-Forwarded-Proto`。
  核实清单见 `aryn-mall-ui/scripts/deploy/README.md`。
- **未启用空闲超时（原计划的 2h）**：只缩短绝对有效期 12h。原因见上；影响是活跃会话仍可连续使用
  12 小时，且存量已签发 TOB token 不受影响（仍按旧 30 天有效期），需等其自然过期或强制重登。
- **存量 token-session 中的旧 hash**：改造前登录的会话快照可能存在 Redis token-session 里（服务端存储，
  风险低于日志/HTTP 响应）；新登录已清空。可在维护窗口清理或等待过期。
- **token 仍在浏览器 localStorage（SecureLS 加密，密钥随前端下发）**：不抗 XSS；本次只缩小泄露后的
  利用窗口（TOB 12h）并关闭日志泄露面，持久化介质未改。
- **历史日志需清查**：发布前 `is-log: true` 已把完整 token 打进日志，需按合规要求清理/轮转历史日志
  （尤其接入日志采集的平台）；同理历史 DEBUG 的 mapper 日志可能含 BCrypt hash。
- **种子账号同 hash 建议改密**：`db/boot/2aryn_boot.sql`、`db/cloud/2aryn_upms.sql` 等种子账号复用同一
  BCrypt hash，未核实明文与线上是否已改，建议上线前强制改密（本次密码策略对改密生效）。
- **cloud 配置需重启生效**：`sa-token.is-log`、`logging.level.*` 为启动期读取；`security.login` 支持
  `@RefreshScope`，仍建议滚动重启 auth / upms / user，避免新旧实例行为不一致。
- **限流依赖 Redis 且 fail-closed**：Redis 不可用时限流检查按锁定处理（提示稍后再试），登录本身此时
  也不可用；生产必须保持 `security.login.enabled=true`，`account-lock-seconds` 等参数调整后需重启或等
  RefreshScope 刷新。
- **本轮未覆盖**：C 端 `TocLoginService` 的密码登录限流（C 端密码登录仍无限流）；租户自助注册的密码强度
  校验（租户管理员密码）；`refreshTokenApi` 的 boot 路径改写（既有问题，`enableRefreshToken` 默认
  `false`，未在本次修复）；登录初始密码强制修改门禁。
- **未联调**：退出后旧 token 失效、限流锁定与 12h 过期均为代码/契约测试级验证，建议在测试环境用真实
  `POST /token/login`、`DELETE /token/logout` 复验一次（退出后旧 token 请求应返回 401，锁定后再试应提示
  「登录失败次数过多」）。
