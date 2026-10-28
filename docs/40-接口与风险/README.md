# 40 接口与风险

本层用于跨端联调和高风险变更前审阅。

- [接口映射](接口映射.md)：管理端/移动端 API 与后端模块覆盖。
- [缺失模块清单](缺失模块清单.md)：已确认缺口、风险级别和处理策略。
- [多租户 SQL 硬约束](#多租户-sql-硬约束)：改 Mapper 前必读。
- [租户上下文线程规则](#租户上下文线程规则)：改 Dubbo 过滤器或 `ArynTenantContextHolder` 前必读。
- [免登白名单与 401 语义](#免登白名单与-401-语义)：新增 C 端公开接口或排查「登录已过期」误报前必读。
- [免登登录端点的凭证自校验](#免登登录端点的凭证自校验)：改登录端点 / 验证码过滤器前必读。
- [C 端接口权限与 403 语义](#c-端接口权限与-403-语义)：C 端页面调接口、排查「登录一直过期」前必读。
- [登录会话策略（全局单点登录）](#登录会话策略全局单点登录)：新增登录入口 / 调整会话逻辑前必读。

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

## 多租户隔离加固（2026-10-28）

需求包见 [2026-10-28-多租户隔离加固](../50-需求文档/2026-10-28-多租户隔离加固/README.md)。以下规则适用于新增接口、实体、缓存与文件能力。

### 平台配置边界

- 租户、套餐、租户菜单、全局菜单的**读/写管理接口**必须同时满足：TOB 会话、会话租户为
  `CommonConstants.PLATFORM_TENANT_ID`、具备对应 `upms:*` 权限。仅靠权限串无法区分平台管理员与
  被套餐授予同名权限的租户管理员。
- 租户菜单保存必须校验目标租户存在、菜单 ID 有效；角色菜单保存必须校验角色归属当前租户，
  且非平台租户只能提交 `sys_tenant_menu` 已授权菜单。**不要**把「当前租户授权菜单」校验退回给前端。
  `SysTenantMenuServiceImpl` 全量替换前还要求租户上下文，避免无上下文误删。
- 在线用户列表/强退必须校验 `upms:onlineuser:get` / `upms:onlineuser:forced`；强退前必须核对
  在线记录的 tenantId 与当前租户一致，不能只凭 token 路径直接 kickout。
- 角色菜单页读取的 `/menu/tenant`、`/menu/role/{roleId}` 需要 `upms:sysrole:update`；
  不要为了「页面上能打开」而移除该权限，否则无权限会话可读取租户菜单结构。
- 平台租户不可停用/删除；`/tenant/register` 与 `/tenantpackage/list` 是公开注册入口，不接平台守卫。

### 写入归属

- `*.api.entity` 的 `tenantId` 由 `TenantIdRequestModule` 在 HTTP 反序列化时移除；新增实体若需要
  客户端显式指定租户，应使用 DTO 并单独校验，不要放开实体字段。
- `MyMetaObjectHandler` 在租户上下文存在时：INSERT 强制覆盖 `tenantId`，UPDATE 清空 `tenantId`
  （默认 `NOT_NULL` 策略下不生成 `tenant_id` 的 SET）。后台任务无上下文时才允许显式租户值。
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
