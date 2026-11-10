# 40 接口与风险

本层用于跨端联调和高风险变更前审阅。

- [接口映射](接口映射.md)：管理端/移动端 API 与后端模块覆盖。
- [缺失模块清单](缺失模块清单.md)：已确认缺口、风险级别和处理策略。
- [多租户 SQL 硬约束](#多租户-sql-硬约束)：改 Mapper 前必读。
- [Mapper 方法名不得与 BaseMapper 重名](#mapper-方法名不得与-basemapper-内置方法重名)：新增/改名 Mapper 自定义方法前必读。
- [租户上下文线程规则](#租户上下文线程规则)：改 Dubbo 过滤器或 `ArynTenantContextHolder` 前必读。
- [免登白名单与 401 语义](#免登白名单与-401-语义)：新增 C 端公开接口或排查「登录已过期」误报前必读。
- [免登登录端点的凭证自校验](#免登登录端点的凭证自校验)：改登录端点 / 验证码过滤器前必读。
- [C 端接口权限与 403 语义](#c-端接口权限与-403-语义)：C 端页面调接口、排查「登录一直过期」前必读。
- [登录会话策略（全局单点登录）](#登录会话策略全局单点登录)：新增登录入口 / 调整会话逻辑前必读。
- [登录凭证与会话加固](#登录凭证与会话加固2026-10-28)：改登录/退出、限流、密码策略或用户响应前必读。

接口状态来自静态路径与 Controller 对照，不代表真实服务、权限和数据库联调通过。

## 多租户 SQL 硬约束

**凡参与 FROM/JOIN 的租户表（`hx.tenant.tables` 白名单内的表），必须在 SQL 中声明表别名。**

原因：MyBatis-Plus `TenantLineInnerInterceptor` 会向每条 JOIN 的 ON 子句追加租户条件，
其 `getAliasColumn()` 在表**无别名时只输出裸列名** `tenant_id`（源码注释即写着「该起别名就要起别名」）。
JOIN 作用域内只要有两张表带 `tenant_id`，MySQL 就会报：

```
ERROR 1052 (23000): Column 'tenant_id' in on clause is ambiguous
```

**反直觉点**：语句中只要出现 `INNER JOIN`，MP 的 `processJoins` 会清空 `mainTables`，
导致 **WHERE 子句不再被追加租户条件**。因此不能靠「WHERE 看起来正常」来排除租户拦截器的嫌疑，
必须直接检查 ON 子句里的 `tenant_id` 是否带表前缀。

**守门测试**：`aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantJoinAliasAuditTest#allTenantTablesInJoinScopesUseExplicitAliases`
会扫描全仓 Mapper 并报出 `文件#statement:表名` 形式的违规。

**踩坑记录**：2026-09-19 `ShipGoodsProfileMapper.xml` 的 `goods_spu`（FROM）与 `goods_sku`（LEFT JOIN）
未起别名，导致船供采购页报上述 1052 错误。该测试当时已存在且逻辑正确，但因
`mvn -o test -pl aryn-boot`（离线、无 `-am`）解析不到 `aryn-vessel-biz` 构件而**从未真正执行**——
守门测试必须纳入常规验证命令才有意义。

## Mapper 方法名不得与 BaseMapper 内置方法重名

**MyBatis 语句 id = `命名空间 + "." + 方法名`**，MyBatis-Plus 的内置语句也按同一 id 注入。
Mapper 接口里只要声明一个与 `BaseMapper` 内置方法（`selectCount`、`selectById`、`selectList`、`selectPage` 等）同名的自定义方法，
XML/注解语句会先以该 id 注册，MP 的 `AbstractMethod#hasMappedStatement` 检测到同名语句已存在便**跳过内置注入**。
后果：内置方法被自定义 SQL 顶掉，而两者参数绑定名不同（自定义通常是 `@Param("query")`；内置是 `@Param("ew")` + 位置参数 `param1`），
任何走内置方法的调用（`IService#count()`、`page()`、`getById()` 等）都会抛：

```
org.apache.ibatis.binding.BindingException: Parameter 'query' not found. Available parameters are [ew, param1]
```

**规则**：自定义 Mapper 方法必须换不重名的名字（如 `countUserByQuery`），不得叫 `selectCount` / `selectById` 等。
**守门测试**：`aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/MapperStatementBindingAuditTest#customMapperMethodsMustNotShadowBaseMapperMethods`
（BaseMapper 方法清单按当前 MP 版本反射获取，避免手写清单随版本漂移）。

**踩坑记录（2026-10-28）**：`SysUserMapper#selectCount(@Param("query") SysUser)` 配 XML `<select id="selectCount">`
覆盖了内置 `selectCount(Wrapper)`。该自定义方法只服务「手机号唯一性校验」，其自身调用一直正常，问题潜伏到
新增的租户概览接口（`TenantOverviewService#getOverview` → `sysUserService.count()`）才暴露：
租户管理页点「详情」必现 500。原审计测试把名为 BaseMapper 内置方法的方法直接跳过，等于把该冲突白名单化了，
本次同时修正该盲点。

## 免登白名单与 401 语义

**C 端「免登」不是前端单方面声明**：api 层写 `skipToken: true` 只表示前端不发送
`satoken` 头；后端仍会按 `secure.ignore.urls`（boot）/ 网关白名单（cloud）决定放行。
前端声明的公开接口若未登记进后端白名单，服务端返回**业务码 401**
（HTTP 状态仍是 200），而前端过去把 401 一律当作登录态失效 → 清 token 跳登录页。

**踩坑记录**：2026-09-24 小程序登录成功后点首页金刚区分类，立即提示「登录已过期」
跳回登录页。根因是金刚区落地页（`sub-pages/product/goods-list`）会以免登方式请求
商品品牌列表 `/product/app/goodsbrand/list`，该接口漏登 boot 白名单。同类漏配还有
秒杀场次与折扣活动读接口（金刚区「限时秒杀」「限时折扣」入口）。已补齐 boot 白名单
并新增 cloud 增量脚本 `db/cloud/88public_goods_read_nacos_config.sql`。

**修法（两层，缺一不可）**：
1. **配置层**：公开读接口登记进后端白名单。boot 改
   `aryn-boot/src/main/resources/application.yml` 的 `secure.ignore.urls`；
   cloud 改 `db/cloud/3aryn_nacos.sql` 网关内容并补增量脚本（两份都要）。
2. **代码层**：`instance.ts` 的 `beforeRequest` 把 `skipToken` 透传为
   `meta.publicRequest`，`handlers.ts` 据此判定 —— 免登请求收到 401/403 时
   只提示错误，**不清登录态、不跳登录页**。判定必须基于请求侧标记，
   因为服务端响应无法区分「token 过期」与「该接口未开通公开访问」。

**匹配语义**：Spring 环境下 Sa-Token 经 `SaPathMatcherHolder` 使用
`AntPathMatcher`，`/a/b/**` 同时匹配 `/a/b` 与 `/a/b/...`（已实测），
无需再单列裸路径。注意它**不是** `SaFoxUtil.vagueMatch`（后者 `**` 不匹配裸路径）。

**守门测试**：`aryn-boot/.../security/PublicEndpointWhitelistContractTest`
把「前端 skipToken 接口清单」与「boot/cloud 白名单」绑成契约，并反查需登录接口
未被过宽白名单顺带放开；前端 `src/api/core/public-request.test.ts` 直接驱动
`handleAlovaResponse`，断言免登 401 不清登录态、带凭证 401 仍照旧清态跳登录。

## 免登登录端点的凭证自校验

**`/token/**`、`/toc-token/**` 在免登白名单内，只是让登录请求能进 Controller，不是免认证承诺**：
凭证校验必须由登录端点自己完成（密码 / 图形验证码 / 短信验证码）。新增或改名登录端点时，
务必同步验证码过滤器匹配的路径常量，否则等于「知道账号即可登录」。

**踩坑记录（P0，2026-10-21 修复并重新部署）**：管理端短信登录真实路径是
`POST /token/sms/login`（`TobTokenController.smsLogin`），而 `SmsValidateCodeFilter` 只匹配
`/token/phone/login`、`/toc-token/sms/login`、`/tenant/register`；`LoginService.smsLogin`
又只按手机号查用户、从不读 `code`。结果：`POST /token/sms/login?phone=<管理员手机号>`
**无需任何凭证即签发管理端 TOB token**（实测返回 `loginDeviceType=TOB`，攻击者可完全接管后台）。

修复内容：

1. `SecurityConstants.TOB_SMS_TOKEN` 常量纳入过滤器匹配；验证码在 Redis
   `sms_code_<phone>`（5 分钟 TTL）比对，校验通过即删除。
2. 顺带修掉历史 bug：原实现 `redisTemplate.delete(key)` 删的是 **code 值本身**而不是
   `sms_code_<phone>`，验证码实际未消费、5 分钟内可重放；改为删缓存 key。
3. 过滤器在 DispatcherServlet 之前执行，抛异常会返回裸 500（绕过全局异常处理）→
   改为按项目出参约定写 `Result.fail(msg)` JSON（HTTP 200 + 业务码），前端可提示
   「验证码为空 / 验证码不合法」。
4. 管理端 `mobileLoginApi` 改为表单提交：原实现发 JSON body，后端
   `smsLogin(String phone)` 与过滤器 `getParameter("code")` 都读不到参数。该 UI 入口
   因此一直不可用，但 **API 层的绕过仍可被直接利用**（本次实测可签发 token）。

**守门测试**：`aryn-auth/.../filter/SmsValidateCodeFilterTest`（缺码/错码/过期码拒绝、
正确码放行且按 `sms_code_<phone>` 消费、C 端路径同防、密码登录路径不受影响）；
管理端 `login-security.test.ts` 锁住 `mobileLoginApi` 必须使用
`application/x-www-form-urlencoded`。

**短信发送端点频控（2026-10-21 晚补齐）**：`GET /sms/{type}/{mobile}` 免登可调，已加 Redis 三级频控
（同手机号 60 秒冷却 / 同手机号 5 次每小时 / 同 IP 20 次每小时，只作用于真正发短信的
`login`/`update`；验证码在 5 分钟有效期内时直接返回成功、不重发也不消耗配额）。
实现见 `SysSmsServiceImpl#assertSendAllowed`，IP 解析见 `SysSmsController#resolveClientIp`，
守门测试 `SysSmsServiceImplTest` / `SysSmsControllerTest`。

**三项加固（2026-10-21 晚第二轮，均已部署验证）**：

1. **验证码校验失败计数**：`SmsCodeVerifier`（common-security 自动装配）用一段 Lua 原子完成
   「比对 + 一次性消费 + 失败计数 + 超限作废」，同一条验证码累计错误 5 次即删除并提示
   「验证码错误次数过多，请重新获取验证码」。管理端/C 端短信登录过滤器与小程序换绑手机号
   共用该组件，不再各写一份 Lua（旧的换绑脚本只做校验+删除、没有失败计数）。
2. **发送前图形验证码**：`type=login` 必须携带 `captchaVerification`（管理端 `Verify` 滑动拼图 →
   `/code/check` 生成的一次性票据），后端校验共享 Redis 中的
   `RUNNING:CAPTCHA:second-<captchaVerification>`（aj-captcha 1.4.0 内部 key 格式，
   由 `CaptchaSecondVerificationKeyContractTest` 反射比对库常量守住），命中即删除。
   管理端 `code-login.vue` 点「获取验证码」先拉起滑块，验证成功且短信发送成功后才启动倒计时。
   `type=update`（小程序换绑手机号）是登录态流程，改为**必须已登录**（未登录返回业务码 401），
   避免给 C 端强行引入滑块 SDK。
3. **IP 频控防伪造**：新增 `sms.rate-limit.trust-forwarded-headers` 配置。boot 单体直连置 `false`
   （只用 `remoteAddr`，伪造 `X-Forwarded-For` 无效）；cloud 在 upms 的 nacos 配置里置 `true`
   （取 XFF **最后一跳**，即网关追加的真实客户端 IP）。另加全局小时上限（Redis 共享，
   默认 300 次/小时）作为换号 + 伪造 IP 的最终成本兜底。

**仍未做 / 已知边界**：① 全局上限是固定常量（不是按租户/环境可配），如需按环境调参可升为配置项；
② 图形验证码目前只覆盖管理端 `type=login`，C 端 `update` 用登录态 + 频控替代；
③ 短信发送仍依赖阿里云模板/签名配置（当前 dev/prod yml 为占位值，真实发送需注入有效 AK/模板）。

## C 端接口权限与 403 语义

**C 端 token 没有 permissions，任何带 `@SaCheckPermission` 的接口对 C 端必然 403。**
C 端登录（`TocLoginService` 的 `maPhoneLogin` / `smsLogin` / `passwordLogin`）构造
`ArynUser` 时只设 `userId`/`openId`/`tenantId`/`username`，**从不 `setPermissions`**；
只有管理端 `LoginService` 与配送端 `DeliveryAuthService` 才灌权限。
因此 C 端页面**不得直连管理端接口**，必须走 `/app/` 下的 C 端接口
（无权限注解，靠全局过滤器做登录校验，用户身份由
`SecurityUtils.getUser().getUserId()` 从登录态取）。

**401 与 403 必须分开处理**（前端 `handlers.ts`）：

| 状态 | 含义 | 正确处理 |
|------|------|----------|
| 401 | 未登录 / 登录态失效 | 清 token、提示「登录已过期」、跳登录页 |
| 403 | **已登录**，但缺该接口所需权限 | 只提示无权限；**禁止**清 token、**禁止**跳登录页 |
| 免登请求的 401/403 | 该接口未开通公开访问 | 只提示；不清登录态（见上一节） |

**踩坑记录**：2026-09-24 小程序签到页报「登录已过期」并清掉 token。真因不是登录态：
签到页调的是管理端 `/signinrecord/page`、`/signinconfig/page`
（`@SaCheckPermission("user:signinrecord:page")` 等），C 端 token 权限为空 → 403；
而 `isUnauthorizedResponse` 把 403 与 401 一视同仁，403 走进
`handleAuthenticationExpired` 清 token 跳登录页。

**识别特征**：用户**重新登录也治不好**，进页面仍被踢出 —— 因为重新登录不会凭空
获得管理端权限。这与真正的 token 过期（重登即愈）是两类问题，不要按登录态排查。

**同批受影响接口**：签到配置/记录、积分记录、余额记录、充值配置共 5 处
（积分/余额/充值的 C 端页面是同一个错误模式）。其中充值配置后端**早已存在**
正确的 `/app/recharge/config/list`，但前端一直没走它。

**顺带修掉的两处隐患**：
1. 管理端 `/pointsrecord/user/page` 等把 `userId` 当查询参数，能查任意用户。
   C 端接口改为只认登录态，用户只能看自己的记录。
2. C 端 VO 不得回 `userId`/`nickname` 等内部字段 —— 新增 `App*VO` 承载展示字段，
   不复用管理端 VO。

**排序参数陷阱**：`PageArgumentResolver` 会把 `asc`/`desc` 查询参数
**前置**到 mapper 自身的 `ORDER BY` 之前。C 端页面若沿用管理端习惯传
`desc: 'create_time'`，会覆盖后端排序意图（例如签到奖励档位被翻成降序）。
排序应由后端 SQL 决定，C 端不传 `desc`。

**守门测试**：
- 后端 `aryn-boot/.../security/AppControllerPermissionContractTest`
  扫描 `controller/app` 包，出现 `@SaCheckPermission`/`@SaCheckRole` 即失败。
- 前端 `src/api/c-end-endpoint-contract.test.ts` 锁定 C 端 api 层不出现管理端路径、
  不出现 `userId` 入参。
- 前端 `src/api/core/forbidden-response.test.ts` 直接驱动 `handleAlovaResponse`，
  断言 403 不清登录态、401 仍照旧清态跳登录。

## 租户上下文线程规则

`ArynTenantContextHolder` 是 ThreadLocal，**凡是在请求线程上执行的代码，都不得在返回时把它的值抹成 null**。

**要害在 Dubbo 过滤器**：`ArynDubboRequestFilter` 同时挂在 PROVIDER 和 CONSUMER 两侧。
BOOT 模式下协议是 `injvm`，服务提供方与调用方**共用同一个 HTTP 请求线程**；
若提供方分支执行完直接 `removeTenantId()`，抹掉的是调用方线程自己的租户。

**踩坑记录**：2026-09-24 C 端「加入购物车」报
`Column 'tenant_id' cannot be null`。`ShoppingCartServiceImpl.saveShoppingCart` 先经
`requireSaleSku` 走 Dubbo 查 SKU，紧接着在同一线程上执行 `shopping_cart` 插入，
租户已被提供方分支清空，插入语句被注入 `tenant_id = NULL`。
日志里表现为紧邻的两条 SQL 一条带租户、下一条为 `AND tenant_id = NULL`，可作为识别特征。

**修法**（不要退回无条件清空）：提供方分支进入前先快照调用方租户，`finally` 里**还原**快照；
快照为 null 时才 `remove`。真实 RPC 场景提供方线程原本无租户，还原等价于清空，
仍能避免线程复用导致的租户串号；injvm 场景则保住调用方上下文。

**守门测试**：`aryn-common-dubbo/.../ArynDubboRequestFilterTest`
的 `restoresCallerTenantAfterProviderInvocation` / `restoresCallerTenantAfterFailedProviderInvocation`
锁定还原语义，`clearsTenantAfter*ProviderInvocation` 锁定「提供方线程本无租户」的清空语义。

## 登录会话策略（全局单点登录）

**当前策略（2026-10-27 起）**：同一账号任意时刻只保留一个有效会话，新登录顶掉旧登录。
配置为 Sa-Token `is-concurrent: false`，boot 在 `aryn-boot/src/main/resources/application.yml`，
cloud 在 Nacos 共享配置 `application-dev.yml`（基线 `db/cloud/3aryn_nacos.sql`，存量环境用
`db/cloud/166global_single_session_nacos_config.sql` 升级）。需求包见
[2026-10-27-全局单点登录](../50-需求文档/2026-10-27-全局单点登录/README.md)。

- **顶替范围**：同账号 + 同设备类型（Sa-Token 默认 `replacedRange=CURR_DEVICE_TYPE`）。管理端 Web
  与配送端登录同为 `TOB`，会互顶；C 端商城为 `TOC`，同一会员换设备登录也会互顶。
- **旧设备表现**：下一次请求收到 401（`NotLoginException.BE_REPLACED`）。管理端沿用登录过期逻辑
  回登录页；小程序配送端 `reLaunch` 到 `/pages/delivery/login`。
- **账号失效即下线**：停用账号、修改密码、删除账号成功后，由 `SysUserSessionRevoker` 立即
  `StpUtil.kickout(userId, "TOB")` 撤销该账号全部会话（管理端 + 配送端）；撤销失败只告警不阻断
  主流程。**角色 / 权限变更仍不会踢线**（token-session 缓存登录时的权限快照，需重新登录生效）。
- **在线用户记录**：Redis key 已统一为 token 维度（`satoken:login:online_user:{token}`，租户存在
  记录值里），写入与注销/强退/顶号删除使用同一 key；列表扫描全部记录后按当前租户过滤并剔除
  失效会话，旧格式残留 key 由 30 天 TTL 自愈。
- **不要按登录入口覆盖该配置**：给单个端点传 `SaLoginParameter.setIsConcurrent(true)` 会绕过
  全局单点策略；新增登录设备类型时同步评估顶替范围，否则会变成「按设备类型单点」。
- **守门测试**：`aryn-boot/.../security/SingleSessionPolicyContractTest` 把 boot yml、cloud Nacos
  基线（含 md5）与存量增量脚本绑成契约，并校验 `config_info` 种子行保持 17 列；
  `ArynSaTokenListenerTest` / `SysOnlineUserControllerTest` 锁在线记录 key 生命周期与租户隔离，
  `SysUserSessionRevokerTest` / `SysUserServiceImplTest` / `SysUserControllerPasswordTest` 锁停用/改密/删除踢线。

## 登录凭证与会话加固（2026-10-28）

需求包见 [2026-10-28-管理端登录安全加固](../50-需求文档/2026-10-28-管理端登录安全加固/README.md)。以下规则适用于登录入口、退出链路、用户响应、密码与日志配置。

### 凭证日志与 hash 边界

- **`sa-token.is-log` 必须保持 `false`**（boot `aryn-boot/src/main/resources/application.yml`、cloud Nacos
  共享配置 `application-dev.yml`，基线 `db/cloud/3aryn_nacos.sql`，存量环境 `db/cloud/170login_hardening_nacos_config.sql`）。
  `is-log: true` 会在登录/续期事件打印**完整 token**，能读日志即可重放会话；`is-print: false` 关不掉这些日志。
  审计日志由 `ArynSaTokenListener` 与登录日志表承担，二者不得记录 token / 密码。
- **密码 hash 不得出现在 HTTP 响应**：用户信息类 HTTP 响应必须使用 `SysUserVO`
  （`aryn-upms-api/.../api/vo/SysUserVO.java`，无 `password`、手机号脱敏）；新增字段加到 VO，
  不要直接返回 `SysUser` 实体。`/user/page` 走 `IPage<SysUserVO>`。
- **密码 hash 不得进入会话与日志**：登录/配送换 token 前必须 `hxUser.setPassword(null)`
  （hash 会随 `ArynUser` 写入 Redis token-session）；Mapper 列片段分 `adminPageSql`（页面查询，不含
  `password`）与 `authUserSql`（认证查询才含 `password`），页面查询不得再查密码列；
  `com.aryn.cloud.upms.mapper` / `com.aryn.cloud.user.mapper` 日志级别保持 INFO（DEBUG 会打印改密 SQL 的 hash）。
- **历史日志的泄露面**：改造前 `is-log: true` 写入过完整 token、DEBUG mapper 日志可能含 hash，
  发布后需按合规清理/轮转历史日志。

### 会话有效期

- **TOB（管理端 Web + 配送端）绝对有效期 12h**：`security.login.tob-timeout-seconds=43200`
  （`aryn-auth/.../config/LoginProtectionProperties.java`），由 `LoginService.login/smsLogin/deliveryLogin`
  与 `DeliveryAuthService.exchange` 通过 `SaLoginParameter` 覆盖；C 端 TOC 仍用全局 `sa-token.timeout`（30 天）。
- **未启用空闲超时（2h）**：按登录维度的空闲超时依赖全局 `dynamic-active-timeout=true`，开启后存量 token
  会被判冻结、发布即强制全端重登；留待维护窗口评估。存量已签发 TOB token 仍按旧 30 天有效期。
- 覆盖会话参数的 `SecurityUtils.loginByDevice(user, deviceType, SaLoginParameter)` **只能用来改超时**；
  `isConcurrent`/`isShare` 继续以全局单点配置为准（见上一节）。

### 登录失败限流与错误提示

- 密码登录（`/token/login`、`/token/delivery-login`）必须经 `LoginAttemptGuard`：账号维度默认
  **5 次 / 900s**、IP 维度 **30 次 / 900s**（`security.login.max-account-failures/account-lock-seconds/
  max-ip-failures/ip-lock-seconds`），Redis Lua 原子计数、每次失败滑动续期；登录成功**只清账号维度**，
  不清 IP 维度。key 前缀见 `CacheConstants.LOGIN_FAIL_ACCOUNT_KEY` / `LOGIN_FAIL_IP_KEY`。
- **统一错误提示、不区分账号是否存在**：账号不存在 / 密码错误 / 空密码 / 非法 hash 一律返回
  「账号或密码错误」；锁定中返回「登录失败次数过多，请 N 分钟后再试」；失败日志只记录脱敏账号。
- 顺序必须是 **先查锁定 → 再查用户 → 再校验密码**，失败才计数；用户名归一化（trim + 小写）后计数。
- Redis 不可用时限流 fail-closed（按锁定返回），不得降级放行；生产必须保持 `security.login.enabled=true`。
- 新增复用员工账号密码的登录入口时，必须接入同一个 `LoginAttemptGuard` 并复用统一文案，不要另写计数。

### 密码策略

- 新建员工密码与改密（`SysUserController.add` / `editPwd`）必须经
  `aryn-common-security/.../util/PasswordPolicy.java`：非空、8~64、至少 1 字母 + 1 数字、15 条弱口令黑名单
  （大小写不敏感）；改密顺序为「验旧密码 → 校验新密码强度 → 验两次一致」，旧密码为空直接拒绝。
- 前端 `apps/web-ele/src/utils/password-policy.ts` 是**逐字镜像**，只做表单提示；规则/文案改动必须两处同步。
- 编辑用户时详情不再返回 hash：前端编辑态必须清空密码字段并放行密码校验，否则弹窗无法提交。

### 退出链路

- 前端 `logoutApi` 必须走统一 `requestClient`（注入 `satoken` + `rewriteBootUrl`，boot 下
  `/auth/token/logout → /boot/token/logout`）；**禁止**再用裸 `baseRequestClient`——不带 token 的退出
  不会撤销服务端会话，会出现「界面已退出、旧 token 仍有效」。`refreshTokenApi` 仍用 `baseRequestClient`
  （刷新请求不应携带已过期 token）。
- 后端 `TobTokenController#logout` 保持 `@DeleteMapping`：Sa-Token logout 对无 token / 失效 token 幂等，
  但 Redis 等基础设施异常必须上抛，不得吞掉后假装退出成功。
- 前端 store 的 logout 必须兜底清理本地态；告警只记录 `msg/message`，**禁止**打印异常对象
  （`error.config.headers.satoken` 可直接重放、登录请求 `config.data` 含明文密码）。

### 免登登录端点

- `TobTokenController` 的 `login` / `smsLogin` 已限定 **POST**；新增登录端点只接受 POST，
  不得用 `@RequestMapping` 放开 GET（密码会进入 URL 与访问日志）。
- 登录端点都在免登白名单内，「免登」只代表能进 Controller，凭证校验（密码/图形验证码/短信验证码）必须由
  端点自行完成，见前文「免登登录端点的凭证自校验」一节。

### 部署与传输

- 生产 HTTPS 必须到真实入口核实：仓库原 Nginx 配置只监听 80，`aryn-ui-https.conf.example` 只是模板，
  **不构成生产已启用 TLS 的证据**；核实清单见 `aryn-mall-ui/scripts/deploy/README.md`。
- 入口代理必须透传 `X-Forwarded-For` / `X-Forwarded-Proto`；`/api` 的 CORS 头由 Nginx 统一输出
  （已剥离上游 CORS 头），不要在后端再叠加第二个 `Access-Control-Allow-Origin`。

**守门测试**：`aryn-boot/.../security/LoginSecurityConfigContractTest`（boot yml + cloud 基线 content/md5 +
增量脚本锚点，6 条）、`LoginAttemptGuardTest`、`LoginServiceSecurityTest`、`PasswordPolicyTest`、
`SysUserControllerPasswordTest`（VO 无 password 字段）、`TobTokenControllerTest`（POST-only）；
前端 `apps/web-ele/src/views/_core/authentication/login-security.test.ts` 锁退出必须走 `requestClient`、
不出现 `console.error(error)`。

## 会话失效 401 契约（2026-10-28）

需求包见 [2026-10-28-被顶下线弹窗提示](../50-需求文档/2026-10-28-被顶下线弹窗提示/README.md)，
实现契约见同目录 [一期实现契约](../50-需求文档/2026-10-28-被顶下线弹窗提示/一期实现契约.md)。

**背景**：Sa-Token 的 7 种未登录场景值（-1 未带 token / -2 无效 / -3 过期 / -4 被顶下线 / -5 被踢下线 /
-6 冻结 / -7 前缀错）历史上只被揉进中文 `msg`，且三个出口不一致（boot/cloud 过滤器 `code=401`，
MVC 兜底 `code=1`）。前端只判 `code===401`，导致被顶下线时管理端静默跳登录页、移动端 0.5s toast 后 `reLaunch`，
用户看不到"为什么下线"，还会把带 token 的 `msg` 弹到界面上。

**现契约**（HTTP 状态仍为 200，沿用 `body.code` 约定）：

```json
{"code":401,"msg":"账号已在其他设备登录","data":{"reason":"BE_REPLACED","invalidAt":"2026-10-28T21:32:07+08:00",
 "device":{"os":"Windows 10","browser":"Chrome 120","location":"上海市","loginTime":"2026-10-28T21:32:05+08:00"}}}
```

- `reason` 取值（wire 值 = 后端 `SessionInvalidReason` 枚举名，**不得改名**）：
  `NOT_TOKEN` / `TOKEN_INVALID` / `TOKEN_EXPIRED` / `BE_REPLACED` / `KICK_OUT` / `TOKEN_FREEZE` / `NO_PREFIX`。
- `invalidAt` / `device` 可缺失；`device` 仅在「被顶下线 + 能取到新会话在线记录」时返回，只含
  `os/browser/location/loginTime`，**不含 IP、token、loginId、tenantId**（`SessionInvalidPayload` 白名单过滤）。
- `msg` 是脱敏固定文案，**不再回显 token**（旧实现 `NotLoginException` 的 message 会拼完整 token）。
- **响应体必须是合法 JSON**：boot/cloud 过滤器不能用 `SaResult` 承载嵌套 `data`（`SaResult#toString()` 对嵌套 Map 只做
  `String.valueOf`，会输出 `{reason=NOT_TOKEN}` 非法 JSON，浏览器 Axios 无法解析）；统一走
  `SessionInvalidResponse.toJson(reason, data)` 预序列化字符串（2026-10-29 boot 部署实测修复）。
- 写入：登录链路 `SecurityUtils.loginByDevice`（登录前收集旧 token → 登录成功后写
  `satoken:login:invalid-reason:{token}`，value 为同上 JSON，TTL 7 天；失败只 warn，不影响登录）。
- 读取：boot 过滤器 / cloud 网关过滤器 / MVC 兜底三个出口同源（网关不能依赖 servlet 版 `common-security`，
  按同一 key/字段直接读 Redis）；Redis 异常或记录缺失只降级为「只有 reason」，不得改变 401 语义。
- 前端消费：管理端 `loginExpiredMode: 'modal'` + 会话失效弹窗（原地重登、保留页面现场）；
  移动端 `sessionStore` + `SessionInvalidDialog`（用户确认后才清态/跳登录页，并回跳原页）。
  **免登请求 401 与 403 仍然只提示、不清登录态、不弹窗**。
- **游客态 401 不属于会话失效（2026-10-29 修复）**：移动端 `beforeRequest` 给请求打 `meta.hadToken`
  （本次是否真的带了 token），响应层 `isGuestRequest`（`hadToken === false`）命中时只 toast「请先登录」，
  不置失效态、不弹窗、不冻结请求；带 token 的 401 仍按上面的弹窗链路。对齐需求包 README §1 第 5 行
  「未登录/伪造 → 直接去登录页，不弹被顶」与 §7.4。受限页不得依赖路由守卫（只拦 `router.push`，
  裸 `uni.navigateTo` 会绕过），须自行做游客态/登录引导（如常购清单页）。
- **入口级登录守卫统一到 `composables/useRequireLogin.ts`（2026-10-29 第二轮）**：`ensureLoggedIn()`
  未登录时写 `redirectPath`（当前页完整路径含 query）→ 打开登录页 → 返回 false；已登录直接放行；
  已在登录页/连点幂等。已接入：装修领券（receive/ladder）与领券中心、消息中心（搜索栏铃铛未登录不渲染、
  notice 游客态、chat/notice-detail 深链跳登录）、商详多规格加购/收藏/客服、共享车分享卡片直达
  （回跳保留 `id`/`token`，登录后自动 join）、拼团开团/参团。守门测试：移动端
  `src/require-login-contract.test.ts`（源码契约）+ `src/composables/useRequireLogin.test.ts`（行为 6 例）。
- 新增 `data` 为纯追加字段，旧客户端无感；**不要把 HTTP 状态改成 401**（三端拦截器与既有测试都建立在
  「HTTP 200 + 业务码 401」上）。

**守门测试**：`aryn-boot/.../security/SessionInvalidContractTest`（枚举/wire 值/脱敏文案/设备字段白名单/
三个出口同源/gateway 不依赖 common-security/Redis key 与 TTL/契约文档冻结值/`subReason` 全链路）、
`aryn-boot/.../common/security/support/SessionInvalidSupportTest`（读写/降级/敏感字段/TTL/踢人原因写入）、
移动端 `src/api/core/guest-request.test.ts`（游客 401 不弹会话失效窗、只提示登录；带 token 401 仍弹窗；
`handleAlovaError` 游客短路）。

### 细分原因 `subReason`（二期，2026-10-28）

同为 `reason=KICK_OUT`，"管理员强退 / 账号停用 / 改密 / 删除 / 租户停用 / 配送资格撤销 / 会员禁用"
对用户的含义和下一步动作完全不同，因此在 `data` 里增加可空 `subReason`：

| wire 值 | 含义 | 写入点 |
|---|---|---|
| `ADMIN_KICK` | 管理员强制下线 | `SysOnlineUserController#kickoutByToken` |
| `ACCOUNT_DISABLED` | 员工账号停用 | `SysUserServiceImpl#updateUser` |
| `PASSWORD_CHANGED` | 密码修改 | `SysUserController#editPwd` |
| `ACCOUNT_DELETED` | 账号删除 | `SysUserServiceImpl#delUser`、`UserInfoServiceImpl#deleteUser` |
| `TENANT_DISABLED` | 租户停用/到期/套餐变更/删除/平台强制下线 | `TenantSessionRevoker#revokeTenant` |
| `DELIVERY_REVOKED` | 配送资格撤销 | `DeliveryAccessGuard#revokeDeliverySessions` |
| `MEMBER_DISABLED` | C 端会员禁用 | `UserInfoServiceImpl#updateStatus` |

- **只在 `reason=KICK_OUT` 时输出**；其它原因即使传入也会被 `SessionInvalidPayload` 忽略。
- 写入必须在 kickout **之前**（`SessionInvalidSupport.kickoutWithReason/kickoutTokenWithReason` 已封装"枚举 token → 写原因 → kickout"）：kickout 之后 token→loginId 映射立即消失，无法再按 token 反查。
- 前端未知值按"无 subReason"降级；`可重新登录=false` 的原因（停用/删除/租户停用/会员禁用）不渲染「重新登录」按钮，避免用户白试。
- 冻结契约见 [二期实现契约](../50-需求文档/2026-10-28-被顶下线弹窗提示/二期实现契约.md)。

### 会话失效主动推送与登录设备（三期，2026-10-29）

- **WS 事件**：被顶/被踢时服务端向旧设备已建立的 WebSocket 推送
  `{"eventType":"SESSION_INVALID","tenantId","recipientType":"SYS_USER|MALL_USER","recipientId","reason","subReason?","invalidAt?","device?"}`。
  通道复用 `message:push:events`；发布方在 `common-security`（不依赖 message-api），
  通道/事件类型/接收者类型的常量统一在 `common-core` 的 `SessionInvalidPushConstants`，
  `MessagePushEvent` 增加可空字段承载；前端收到后复用同一 `sessionStore.invalidate`（幂等）。
  推送失败只 warn，仍由下一次 HTTP 401 兜底。
- **登录设备接口**：`GET /auth/session/devices` 返回当前账号活跃会话（token 只以 SHA-256 前 12 位
  形式出现，带 `current` 标记）；`DELETE /auth/session/devices/{id}` 只能下线自己的**非当前**会话，
  当前设备必须走退出登录。
- **我的登录记录**：`GET /upms/sysloginlog/my` 只返回当前账号 + 当前租户记录，单页上限 20，
  **不得**加 `upms:sysloginlog:page` 权限点（否则普通员工无法在「登录设备」弹窗查看自己的记录）。
- 守门测试：`SessionInvalidContractTest`（推送契约/接口安全边界/契约文档冻结值）、
  `SessionInvalidSupportTest`（推送字段白名单）、`SessionDeviceControllerTest`、
  `SysLoginLogControllerMyTest`、`MessageRedisPushListenerTest`（跨实例投递/本实例跳过/坏 JSON）。

## 多租户隔离加固（2026-10-28）

需求包见 [2026-10-28-多租户隔离加固](../50-需求文档/2026-10-28-多租户隔离加固/README.md)。以下规则适用于新增接口、实体、缓存与文件能力。

### 平台配置边界

- 租户、套餐、租户菜单、全局菜单的**读/写管理接口**必须同时满足：TOB 会话、会话为
  **平台管理员**、具备对应 `upms:*` 权限。仅靠权限串无法区分平台管理员与被套餐授予同名权限的租户管理员。
  **平台管理员是用户级身份**：`sys_user.platform_admin = '1'` 且所属租户为平台租户
  （`sys_tenant.tenant_type = '1'`），登录时计算后写入会话快照 `ArynUser.platformAdmin`；
  守卫用 `PlatformTenantGuard`/`PlatformAdminSupport` 判定，**禁止**再硬编码平台租户 ID
  （原 `CommonConstants.PLATFORM_TENANT_ID` 已删除）。平台租户里的租户管理员不是平台管理员：
  不显示平台管理菜单、不能切换租户、不能操作平台配置。
- 租户菜单保存必须校验目标租户存在、菜单 ID 有效；角色菜单保存必须校验角色归属当前租户，
  且非平台租户只能提交 `sys_tenant_menu` 已授权菜单。**不要**把「当前租户授权菜单」校验退回给前端。
  `SysTenantMenuServiceImpl` 全量替换前还要求租户上下文，避免无上下文误删。
- 在线用户列表/强退必须校验 `upms:onlineuser:get` / `upms:onlineuser:forced`；强退前必须核对
  在线记录的 tenantId 与当前租户一致，不能只凭 token 路径直接 kickout。
- 角色菜单页读取的 `/menu/tenant`、`/menu/role/{roleId}` 需要 `upms:sysrole:update`；
  不要为了「页面上能打开」而移除该权限，否则无权限会话可读取租户菜单结构。
- 平台租户不可停用/删除、不可设置授权结束时间或续期；`/tenant/register` 与 `/tenantpackage/list` 是公开注册入口，不接平台守卫。
- 租户是否长期有效由数据决定：`sys_tenant.tenant_type = '1'`（平台租户）且 `auth_end_time IS NULL`；
  普通租户按授权起止时间管控。前端不得再按租户 ID 硬编码「长期有效」，列表/详情统一以
  `authEndTime` 为空作为长期有效判据，操作按钮以 `tenantType` 判据。「当前会话是否平台管理员」
  取会话 `ArynUser.platformAdmin`（用户级标记），前端取用户信息接口返回的 `platformAdmin`；
  存量会话没有该字段时按非平台管理员处理，重新登录即可刷新。
- 平台管理员账号是**系统级身份、不属于任何租户员工视图**：存储上仍绑定平台租户（RBAC/登录
  上下文依赖），但所有租户侧「查询用户/员工账号」入口都必须排除 `platform_admin='1'`：
  `SysUserMapper` 的员工分页、消息受众、客服坐席资格、员工搜索（`searchNormalStaff`）、
  租户员工数（`countTenantStaff`）SQL；`RemoteSysUserServiceImpl` 的按 ID/批量/搜索查询
  （登录认证用的 `getUserInfo(username/phone)` 除外）；销售员/配送员等下游业务视图通过
  `RemoteSysUserService#getPlatformAdminUserIds` 按 ID 排除。新增租户侧员工查询时沿用同一口径。
- 平台管理员专属角色 `ROLE_PLATFORM_ADMIN`（平台租户下「平台管理员」）是**系统内置角色**：
  不出现在角色管理列表/详情，角色新增/修改/删除、角色菜单保存一律拒绝（对平台管理员会话同样
  拒绝），角色编辑也不能把普通角色改名为该保留编码；该角色只允许数据库初始化/迁移脚本维护。
  平台管理员账号的权限与菜单树由「全部可见菜单」动态推导
  （`SysUserServiceImpl#findUserInfo`、`SysMenuServiceImpl#getLoginUserMenuTree`），
  不依赖可编辑的 `sys_role_menu` 数据。租户管理员也不能修改/停用/删除平台管理员账号；
  平台管理员账号 `system` 不在员工账号列表/详情中暴露（`SysUserMapper#adminPage` 过滤、
  按 ID 读取走 `ISysUserService#getVisibleUserById`），员工手机号改绑入口同样不可达。
  `syncRoleMenus` 不裁剪该角色。
- 登录日志、操作日志与在线用户也不向普通角色账号暴露平台管理员：`PlatformAdminVisibilitySupport`
  返回当前租户平台管理员用户名集合，`SysLoginLogController` 的列表/统计、`SysLogController`
  的列表/详情、`SysOnlineUserController` 的列表与强退按该集合适用；当前会话是平台管理员时
  不过滤（保留审计能力）。登录日志/操作日志/在线记录没有 user_id，按用户名过滤
  （`NOT IN` 需显式放行 NULL）；平台管理员账号不允许经员工账号接口改名，故历史记录可稳定过滤。
- 平台租户必须有自己的启用 `sys_storage_config`（种子：boot `181` / cloud `183`，默认本机
  `/data/aryn/uploads`）：个人信息头像、租户 Logo 等平台级上传按登录用户自己的租户查存储配置，
  不能依赖业务租户的配置。`domain` 为空只返回站内相对 URL，跨源管理端需补公开访问域名。
  2026-11-03 起平台租户由「悦航购」担任（boot `196` / cloud `201` 合并脚本），悦航购自身已有启用
  存储配置时不再继承平台租户配置；新增平台级种子/授权脚本按 `tenant_type='1'` 或目标租户管理员
  角色推导，**不要**写死租户 ID。

### 写入归属

- `*.api.entity` 的 `tenantId` 由 `TenantIdRequestModule` 在 HTTP 反序列化时移除；新增实体若需要
  客户端显式指定租户，应使用 DTO 并单独校验，不要放开实体字段。
- `MyMetaObjectHandler` 在租户上下文存在时：INSERT 强制覆盖 `tenantId`，UPDATE 把实体 `tenantId`
  改写为当前租户（`SET` 与租户插件 `WHERE` 同归属，改挂无效）。**UPDATE 不能把 `tenantId` 置空**：
  MyBatis-Plus 先按实体非空字段生成 `SET tenant_id = #{et.tenantId}`，之后才执行 `updateFill`，
  置空会让参数绑定 NULL，命中 `tenant_id NOT NULL`（2026-10-29 微信登录 500 的根因，回归测试
  `TenantUpdateOwnershipSqlTest`）。后台任务无上下文时才允许显式租户值。
- 手写 SQL / `@InterceptorIgnore` 旁路仍必须显式过滤租户；新增旁路必须登记到
  `TenantInterceptorBypassAuditTest.ALLOWED_BYPASSES`。

### 缓存与异步

- `@Cacheable` key 默认必须带租户维度；仅当业务键全局唯一（如 `social_account.app_id`）且调用方
  有权威归属校验时才可按全局键缓存。
- 异步线程、`CompletableFuture`、自建线程池不会自动传递 `ArynTenantContextHolder`；必须在任务内
  快照/恢复租户，或使用显式传入的租户 ID。新增异步入口时按 `CatalogVectorRecallService` 的写法处理。

### 租户生命周期

- 登录（TOB 密码/短信/配送、TOC 微信/短信/密码）必须先经 `TenantAccessGuard` 校验租户状态与授权
  起止时间；平台租户豁免。
- 租户停用/删除在事务提交后由 `TenantSessionRevoker` 扫描在线记录并撤销全部会话；授权到期由
  `TenantExpiryJob` 每小时回收。在线记录覆盖 TOB/TOC（`SysUserOnline.deviceType`），`doRenewTimeout`
  同步续期；管理端在线列表只展示 TOB。
- 存量改造前签发的 TOC 会话没有在线记录，无法被租户级撤销；新登录已覆盖。

### 私有文件

- 配送凭证等私有材料必须走 `uploadPrivateFile`；本地存储落在 `{tenantId}/private/`，下载端点对
  `private/` 路径强制校验 `PrivateFileUrlSigner` 的 `expires + signature`。公开商品图继续匿名。
- 签名只在下发时生成、不持久化；素材管理页与配送任务读取侧都已接入。新增读取侧时调用
  `RemoteMaterialService#signPrivateUrls`，不要把签名 URL 写回数据库；素材编辑必须保留私有记录的
  原 URL 与 `bizTag`。
- 存量私有文件仍在租户根目录，未自动迁移；对象存储的私有性依赖桶 ACL，本次未改。
- 免登公开读（商品目录等）不在本次会话闭环内；租户停用后如需整租户下线，应在网关/过滤器增加状态检查。

## 手机号修改（2026-11-02）

需求包见 [2026-11-02-员工账号与个人信息手机号修改](../50-需求文档/2026-11-02-员工账号与个人信息手机号修改/README.md)。

- **两个入口、两套安全口径**：管理员入口 `PUT /upms/user/{id}/phone` 走独立权限
  `upms:sysuser:phone:edit`（与查看权限 `upms:sysuser:phone` 分开，按「重置密码」同敏感度授权，
  boot `182` / cloud `184` 种子）；本人入口 `PUT /upms/profile/phone` 无额外权限，但必须校验
  当前密码，不能只凭登录态换绑（2026-11-10 起不再要求新号短信验证码，请求体只有 `phone` + `password`）。
- **手机号全局唯一**：`sys_user` 的 `uk_sys_user_phone`（仅 `del_flag='0'`）跨租户生效；应用层用
  `ISysUserService#getCount` 预检并捕获唯一键冲突。不要改成租户内唯一——`selectUserByPhone` 是
  免租户旁路，短信登录按全局唯一解析账号。
- **脱敏值永不回写**：列表/详情 `SysUserVO.phone` 已脱敏；员工编辑接口继续丢弃 `phone`，换绑只能走
  独立接口并接收完整 11 位号码，禁止放开通用编辑提交。
- **配送员资料同步**：两个入口在本地更新成功后调用
  `RemoteDeliveryAccountService#syncStaffPhone(sysUserId, phone)` 更新未删除 `delivery_staff.staff_phone`；
  best-effort，失败只 warn。配送登录读 `sys_user.phone`（即时生效），`staff_phone` 只用于配送工作台展示/搜索。
- **不强制下线**：手机号不是当前 token 的校验因子，改号不撤销会话；如需强制重登，需新增
  `PHONE_CHANGED` 会话失效子原因并同步前后端契约。

## IP 归属地数据源与许可（2026-11-04）

需求包见 [2026-11-04-IP归属地离线化](../50-需求文档/2026-11-04-IP归属地离线化/README.md)。

- **默认实现改为本地离线库**：`IpUtils.getWhoisAddress` 是通过 `IpLocationResolver` SPI 注册的
  统一入口，默认是第三方 HTTP（`HttpIpLocationResolver`），引入 `aryn-common-ip` 后由自动配置注册
  ip2region 实现（classpath 内置 11MB v4 xdb，外部 `data-dir` 优先）。新增调用点时只调用
  `IpUtils.getWhoisAddress`，**不要**自行 new HTTP 客户端或再依赖第三方接口。
- **数据许可红线**：`pconline` whois 接口只自称「免费」、无商用授权与 SLA（实测并发即 503）；
  ip2region **代码** Apache-2.0/MIT 可商用，但**自带数据**未给商用许可承诺——对外售卖前必须替换为
  已购商用离线数据（ip2region.net ¥499/年起，每月 ≥1 更新）。数据文件更新走
  `aryn.ip.refresh.source-url` 指向自有分发，不要长期依赖 GitHub master。
- **热更新**：XXL-JOB `ipRegionDataRefreshJobHandler`（boot `200` / cloud `203` 注册脚本）每日 03:30
  按 ETag 检查，下载后必须过 体积/`verifyFromFile`/样本 IP 三重校验才原子替换，失败自动回滚并保留旧数据；
  多实例靠 `{data-dir}/.ip2region_v4.lock` 文件锁串行化。**不要**手工直接覆盖 xdb 文件。
- **数据目录必须持久化**：boot 已在 `docker-compose-boot.yml` 把 `/root/.aryn/ip2region` 挂到命名卷
  `boot-ipdata`，容器重建/发版不会丢失热更新数据；新增部署形态（更换编排/多副本）时同样要给
  `data-dir` 挂持久卷，否则每次重建都会回退到镜像内置数据（功能不变，但数据新鲜度回退）。
  启动时按 `header.createdAt` 取「卷内文件 vs 镜像内置」较新者，发版换新镜像后自动纠偏。
- **降级边界**：离线库不可用/未命中且 `aryn.ip.fallback-http=true` 时才会访问第三方 HTTP（1.5s 超时），
  两者都失败返回「未知」；归属地只影响展示，任何异常不得影响登录/业务主流程（`IpUtils` 已兜底）。
- **cloud 多服务**：xdb 是本地文件。需要所有实例热更新时，要么给各服务挂同一 `data-dir` 共享卷，
  要么在各执行器分组分别注册刷新任务；否则非执行器服务随镜像发版更新。
