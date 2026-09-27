# 40 接口与风险

本层用于跨端联调和高风险变更前审阅。

- [接口映射](接口映射.md)：管理端/移动端 API 与后端模块覆盖。
- [缺失模块清单](缺失模块清单.md)：已确认缺口、风险级别和处理策略。
- [多租户 SQL 硬约束](#多租户-sql-硬约束)：改 Mapper 前必读。
- [租户上下文线程规则](#租户上下文线程规则)：改 Dubbo 过滤器或 `ArynTenantContextHolder` 前必读。
- [免登白名单与 401 语义](#免登白名单与-401-语义)：新增 C 端公开接口或排查「登录已过期」误报前必读。
- [C 端接口权限与 403 语义](#c-端接口权限与-403-语义)：C 端页面调接口、排查「登录一直过期」前必读。

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
