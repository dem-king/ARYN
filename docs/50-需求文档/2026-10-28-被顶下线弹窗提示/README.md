# 被顶下线 / 登录态失效弹窗提示（管理端 + 移动端）设计方案

> 状态：**一期 + 二期 + 三期 + 四期已实施（自动化验证通过；cloud 未部署实测、小程序未真机目检）**
> 日期：2026-10-28 ~ 2026-10-29（一期/二期：10-28；三期：10-29）
> 实现契约：[一期](一期实现契约.md)、[二期](二期实现契约.md)、[三期](三期实现契约.md)、[四期](四期实现契约-设备信息含IP与移动端地点.md)（三端冻结字段/枚举/文案/行为矩阵；四期为 2026-11-01 的 IP/地点增量）
> 实施记录：一期 §14、二期 §15、三期 §16
> 影响端：管理端 Web（`aryn-mall-ui/apps/web-ele`）、移动端（`aryn-mall-uniapp`：C 端商城 + 配送工作台）、后端（boot 过滤器 / cloud 网关过滤器 / `common-core` / `common-security` / `aryn-auth` 监听器 / 各 biz 踢人调用点）
> 前置需求：[全局单点登录](../2026-10-27-全局单点登录/README.md)（已实施）、[管理端登录安全加固](../2026-10-28-管理端登录安全加固/README.md)（已实施）
> 硬约束：boot / cloud 双模式一致；不改 token 存储介质；**零表结构变更、零 DDL**；对旧客户端向后兼容；不得破坏既有 6 组会话/安全契约测试

---

## 0. 结论先行

1. **现状是"静默退出"，不是"没有提示"**。管理端 `loginExpiredMode` 默认 `'page'`，被顶下线后拦截器直接 `logout()` 跳登录页，Vben 自带的重登弹窗（`AuthenticationLoginExpiredModal`）实际是**死代码**；移动端更短——0.5 秒 toast + 1 秒后 `reLaunch` 清空页面栈回登录页。
2. **服务端其实已经知道原因，但只写在中文 `msg` 里，且前端全部丢弃**。Sa-Token 1.45 会把被顶下线标成 `-4`、被踢下线标成 `-5`、自然过期标成 `-3`，boot/cloud 的过滤器把异常统一成 `HTTP 200 + body.code=401 + 中文 msg`，但前端只判断 `code===401`，不读 `msg`、不读 `data`。所以**不能靠前端解析文案，必须把"为什么失效"升级成机器可读契约**。
3. **推荐方案**：后端在 401 响应里新增 `data.reason`（被顶下线时再加 `data.device` 新设备信息），不动 `code`、不动 HTTP 状态、`msg` 保留；前端两端统一实现"**会话失效状态机 + 单例阻塞弹窗**"——
   - 管理端：切 `loginExpiredMode: 'modal'`，用 Vben 内置重登弹窗承载原因文案，**页内原地重新登录**，页面现场不丢；
   - 移动端：新增弹窗组件替换 "toast + 自动跳转"，**用户确认后**才清态跳登录页，并写入 `redirectPath` 让重登后回到原页。
4. **两端都要做，且互不影响**：`replacedRange=CURR_DEVICE_TYPE`，管理端（员工账号 `sys_user`，device=`TOB`）只会被管理端/配送端登录顶掉；移动端 C 端（商城会员，device=`TOC`）只会被另一台手机顶掉。所以"管理端还是移动端"的答案是：**两端各做一套表现层，共用同一份后端契约与文案规范**。
5. **分期建议（已按决策重排）**：**一期约 6.5 人日**交付本次诉求——原因契约 + 被顶下线的**新设备信息** + 管理端/移动端弹窗（无"稍后"、C 端也弹、重登回跳）；二期补细分"被踢"原因、多标签页与 WS 治理；主动推送与登录设备管理页不排期。
6. **5 项决策已确认（2026-10-28）**：① 管理端与配送端**保持互顶**；② 弹窗**不提供"稍后处理"**（只有「重新登录 / 退出」）；③ **要展示新设备信息**（浏览器/系统/地点/登录时间）→ 由原 P2 **提前到一期**（§5.6）；④ **不做**"新设备二次确认顶号"；⑤ 移动端 **C 端同样弹窗**。原"待确认"清单已全部关闭，详见 §13。

---

## 1. 术语与用户可感知分类

先统一口径，避免把"被顶下线"和"登录过期"混为一谈——两者对用户的含义完全不同（前者可能是别人在用你的账号，后者只是久了没操作）。

| # | 情况 | Sa-Token 标记 | 触发来源 | 现状返回 | 用户应看到 |
|---|---|---|---|---|---|
| 1 | 自然过期 | `TOKEN_TIMEOUT`(-3) | TOB 12h / TOC 30 天绝对有效期 | code=401，msg `token 已过期` | "登录已过期" + 重新登录 |
| 2 | **被顶下线** | `BE_REPLACED`(-4) | 同账号在另一处登录（`is-concurrent=false` 的 `replaced()`） | code=401，msg `token 已被顶下线` | "账号已在其他设备登录" + 重新登录（**本次核心**） |
| 3 | **被踢下线** | `KICK_OUT`(-5) | 管理员强退、账号停用、改密、删除账号、会员禁用、租户停用/到期/删除、配送资格撤销（见 §5.4） | code=401，msg `token 已被踢下线` | 按子原因给具体文案（二期） |
| 4 | 主动退出 | `INVALID_TOKEN`(-2) | 用户点退出 → `StpUtil.logout()` 删除映射 | code=401，msg `token 无效` | 不弹窗（用户自己点的） |
| 5 | 未登录/伪造 | `NOT_TOKEN`(-1)/`INVALID_TOKEN`(-2) | 未带 token、token 被篡改 | code=401 | 直接去登录页，不弹"被顶" |
| 6 | 权限不足 | —— | `NotPermissionException`/`NotRoleException` | **HTTP 403 + code 403** | 只提示无权限，**绝不清登录态**（已由现有测试守住） |
| 7 | 业务 401（易混） | —— | `ArynBusinessException(401, ...)`，如 `DeliveryAccessGuard` 配送登录失效 | code=401 | 按"配送登录失效"处理，不是账号被顶 |

两个容易踩的边界：

- **主动退出不是 KICK_OUT**：`StpUtil.logout()` 是删除 token→id 映射，旧 token 再请求得到 `INVALID_TOKEN`(-2)。所以"用户自己退出"和"被系统踢"在协议层是可区分的，但**"被别人踢"和"服务端重启/Redis 清理导致映射丢失"同样是 -2**，这一点只能靠 §5.4 的原因注记补齐。
- **租户会话撤销依赖在线记录**：`TenantSessionRevoker` 是按 Redis 在线表逐个 `kickoutByTokenValue`，若账号当时没有在线记录（历史脏 key、Redis 清空），就踢不到，token 会存活到过期。这属于既有边界，不在本需求范围，但会影响"被踢"提示的到达率。

---

## 2. 现状实测：三端到底发生了什么

### 2.1 服务端：有"原因"，但没有"契约"

真实响应形态（boot 与 cloud 一致，均已由仓库文档与真机记录确认）：

```
HTTP/1.1 200 OK
Content-Type: text/plain;charset=utf-8

{"code":401,"msg":"token 已被顶下线：<完整token>","data":null}
```

- 401 来自 Sa-Token 过滤器的 `setError`，不是 HTTP 状态码：boot 在 `aryn-mall-java/aryn-boot/src/main/java/com/aryn/cloud/config/SaTokenConfigure.java:40-58`，cloud 在 `aryn-mall-java/aryn-gateway/src/main/java/com/aryn/cloud/gateway/config/SaTokenConfigure.java:52-60`，都是 `SaResult.error(e.getMessage()).setCode(401)`；Sa-Token 1.45 的 `writeResult` 只写 body 不写 status，所以 HTTP 一直是 200。
- **7 种未登录场景值没有映射成不同 code**，只有中文 `msg` 不同：`aryn-mall-java/aryn-common/aryn-common-security/src/main/java/com/aryn/cloud/common/security/handler/ArynGlobalExceptionHandlerResolver.java:113-136`。且 `TOKEN_FREEZE`(-6)/`NO_PREFIX`(-7) 落进 else「当前会话未登录」。
- **两条路径不一致**：过滤器分支 `code=401`；MVC 兜底 `@ExceptionHandler` 走 `Result.fail(msg)` → `code=1`（`CommonConstants.FAIL`），且没有 `@ResponseStatus`。后者会让前端"看着像登录态还活着"（只弹一条错误字、不清 token、不跳转），是"假登录"的隐患。
- **`msg` 里可能拼了完整 token**：Sa-Token `NotLoginException.newInstance` 会把引发异常的 token 拼进 `message`（是否拼接取决于抛出路径，仓库里同时存在 `token已被顶下线` 与 `token 已被顶下线：xxx` 两种文案）。管理端 `request.ts` 的错误提示直接用 `responseData.msg` 弹 `ElMessage` —— 等于**把可用凭证显示在用户屏幕上、并可能进前端日志**。这与 2026-10-28「登录安全加固」刚关掉的服务端 token 日志是同一类风险的另一个口子。
- **无机器可读原因**：四种情况 body `code` 完全相同、HTTP 相同、无区分 header。前端目前**零 msg 判断**（管理端与移动端都没有）。

### 2.2 管理端：弹窗写好了，但永远不会弹

关键链路（Vben 架构）：

1. `packages/effects/request/src/request-client/preset-interceptors.ts:61-72`：重新认证的唯一判据是 **响应体 `data.code === 401`**（不看 HTTP status、不看 msg）。
2. `apps/web-ele/src/api/request.ts:35-48` `doReAuthenticate()`：先 `setAccessToken(null)`；只有当 `preferences.app.loginExpiredMode === 'modal' && accessStore.isAccessChecked` 才 `setLoginExpired(true)`，否则 `await authStore.logout()`。
3. `packages/@core/preferences/src/config.ts:28`：`loginExpiredMode` 默认 **`'page'`**；`apps/web-ele/src/preferences.ts:8-13` 只覆盖了 `app.name` —— 所以**永远走 `logout()`**，`apps/web-ele/src/layouts/basic.vue:206-213` 挂的登录弹窗是死代码。
4. `apps/web-ele/src/store/auth.ts:156-189` `logout()`：`resetAllStores()` → `router.replace('/auth/login?redirect=...')`，页面现场直接销毁。

即使只把开关改成 `'modal'`，还有 3 个坑：

- **刷新后首次 401 仍会直接登出**：`accessStore.isAccessChecked` 不在持久化白名单里（`packages/stores/src/modules/access.ts:102-120`），而路由守卫 `router/guard.ts:95` 的 `fetchUserInfo()` 发生在 `:107-109` 的 `setIsAccessChecked(true)` 之前。
- **弹窗里没有一句解释**：`packages/effects/common-ui/src/ui/authentication/login-expired-modal.vue` 把 `title/sub-title` 通过 `<Slot>` 透传给 `LoginForm`，但 app 的 LoginForm 不接这些 props → 弹窗里显示的是登录页营销标题（文案 key `authentication.loginAgainTitle` 存在但没用上）。
- **弹窗只在 BasicLayout 内**：登录页、独立页（如大屏/打印页）没有挂载点。

另外还有几处会放大问题：

- 弹窗 `closable=false / close-on-press-escape=false / footer=false`，唯一出口是重登成功——**用户没法"我先退出"**。
- **多标签页不同步**：无 `storage` 事件监听、无会话广播（`BroadcastChannel` 仅用于装修表单同步）。A 标签被顶后，B 标签仍显示已登录，直到各自请求各自弹/跳。
- **WS 死 token 重连**：`apps/web-ele/src/store/message.ts:340-390` 固定 3 秒无限重连，不判登录态；`refreshNotifications()`（`:201-220`）无 catch，401 会变成 unhandled rejection。
- **心跳 401 会被静默掉但认证拦截器已经登出**：`request.ts:117-122` 只跳过 toast，挡不住 `authenticateResponseInterceptor`——注释意图与实际行为冲突。
- **dev 下登出可能中断**：`packages/stores/src/setup.ts:51-60` 的 `resetAllStores()` 会遍历所有 store 调 `$reset()`，而 `apps/web-ele/src/store/agent-presence.ts` 是 setup store 未实现 `$reset` → Pinia 3 在 dev 会抛错，导致后续 `setLoginExpired(false)` 与 `router.replace` 被跳过（生产为 noop，但会影响本地联调与 e2e）。

被顶下线的完整时序（当前）：

```
另一台电脑登录 → 旧 token 在 Redis 被标记 -4
旧浏览器下一次请求 → HTTP 200 + {"code":401,"msg":"token 已被顶下线：xxx"}
→ defaultResponseInterceptor 因 code≠0 抛错
→ authenticateResponseInterceptor 命中 data.code===401
→ doReAuthenticate: 清内存 token → loginExpiredMode='page' → authStore.logout()
→ logout 带空 token 再调 /auth/token/logout → 又 401 → 二次 doReAuthenticate（serverLogoutPending 挡住递归）
→ resetAllStores() + router.replace('/auth/login?redirect=...')
→ 原错误继续上抛 → ElMessage.error("token 已被顶下线：xxx")
```

**用户看到的就是：页面白屏闪跳登录页 + 一条红色 toast（还带着自己的 token 串），没有任何"你的账号在别处登录了"的解释，也无法选择。**

### 2.3 移动端：0.5 秒 toast + 1 秒闪跳

`aryn-mall-uniapp/src/api/core/handlers.ts`：

```ts
// :171-177
if (isUnauthorizedResponse(statusCode, resp)) {
  const deliveryRequest = isDeliveryRequest(method)
  handleAuthenticationExpired(globalToast, deliveryRequest)   // 清 auth + emit + toast + 1s 后 reLaunch
  throw new ApiError(translate(...'common.loginExpired'), statusCode, data)  // 后端 msg 在这里被丢弃
}
```

- `:66-82`：`expireAuthentication()` 先 `removeStorageSync('auth')` + `$emit('auth-expired')` → `main.ts` 清 Pinia（含用户信息、购物车角标、主题），然后 `globalToast.error({ duration: 500 })`。
- `:16-29`：`setTimeout(1000)` 后 `uni.reLaunch('/pages/login/index')`（配送端跳 `/pages/delivery/login`）——**不等用户确认，直接清页面栈**。
- **不写 `redirectPath`**：`redirectToLogin` 没有记录当前页面，而登录页 `src/pages/login/index.vue` 的 `loginSuccess()` 其实**已支持** `redirectPath` 回跳 —— 这是"只差一行写入"的便宜修复。
- 配送端更生硬：`src/api/upms/file.ts:74,109` 上传前 preflight / 上传返回 401 时**直接 `reLaunch('/pages/delivery/login')`，无 toast、无节流**。
- 并发处理尚可：首个 401 同步置 `isRedirectingToLogin=true`，后续早退；但该标志在 `reLaunch` 的 `complete` 里立刻复位，没有"已提示"的会话级标记。
- **有 WebSocket 但不是下线通知通道**：`src/store/messageStore.ts:96-128` 只做 PING/PONG + 未读刷新；`connect()` 只判 token 非空不验有效性 → 被顶后会拿死 token **每 3 秒无限重连**。
- 已有可复用的全局弹窗能力：`src/composables/useGlobalMessage.ts:38-47`（`wd-message-box`，挂根组件 `src/App.ku.vue:85-90`，更新提示 `src/App.vue:32-44` 就是先例）。项目明确要求页面里不要裸调 `useToast()/useMessage()`。

### 2.4 双模式与既有契约（决定了方案边界）

- boot 过滤器与 cloud 网关过滤器**同构**，但 **gateway 不依赖 `common-security`**（只有 `aryn-common-core`，因为 common-security 是 servlet 体系，gateway 是 WebFlux）→ **共享的契约常量/枚举只能放 `aryn-common-core`**，读写实现两侧各自落地。
- **没有 refresh token 端点**（全仓无 `/auth/token/refresh`；前端 `refreshTokenApi` 打过去是 404，且 `enableRefreshToken` 默认 `false`）→ 方案不依赖静默续期，"过期/被顶"一律走弹窗重登。
- 自然过期**没有服务端回调**（不触发 `doKickout/doReplaced/doLogout`），只在请求时由映射判定 → 被动感知是唯一低成本通道。
- 不可违背的既有契约：
  - `SingleSessionPolicyContractTest`：`is-concurrent: false`、`is-share: false`，profile yml 不得覆盖；
  - `LoginSecurityConfigContractTest`：`is-log: false`、TOB 12h；
  - `ArynSaTokenListenerTest:41-49`：`doKickout`/`doReplaced` 只做在线记录的 delete（**改动监听器行为必须同步扩测试，不能为过旧断言放弃原因记录**）；
  - `WebSocketSecurityContractTest:18-37`：WS 握手身份只能来自 `SecurityUtils.requireUser`，不得读 query 取 token；
  - 移动端 `forbidden-response.test.ts` / `public-request.test.ts`：**带凭证 401 必须清态**、免登 401 不得清态 —— "弹窗"改造后"清态"可以保留/延后，但语义不能被改坏。

---

## 3. 主流做法调研

### 3.1 产品策略：三选一，主流是"强制单点 + 旧端弹窗告知"

| 策略 | 做法 | 典型产品 | 优点 | 缺点 |
|---|---|---|---|---|
| **A. 强制单点 + 旧端阻塞弹窗**（推荐，也是现状策略的 UX 补齐） | 新登录直接顶掉旧会话，旧端在下一次操作时弹窗说明原因并引导重登 | 微信/QQ/钉钉/企业微信桌面端、Steam、绝大多数国内 B 端 SaaS 后台 | 安全与审计最强、实现最简单（Sa-Token 配置即得） | 用户可能被"莫名下线"；两端来回顶会产生拉锯 |
| B. 新端二次确认后顶号 | 新设备登录时提示"该账号已在其他设备登录，是否强制下线原设备？" | 部分网银/企业后台（常见文案"您已通过其他浏览器登录，请退出后再登陆或者强制下线"） | 避免误顶，尊重在线的使用者 | 多一步交互；登录接口要带"确认顶号"参数；**Sa-Token 1.45 原生支持**（`replacedLoginExitMode=NEW_DEVICE`） |
| C. 不互踢 + 登录设备管理 | 允许多端并存，提供"登录设备"列表供用户自行下线 | Google、GitHub、飞书、Slack | 体验最好 | 不满足"管理端账号审计/防共用"诉求；需新表/新页面 |

结论：**保持 A（与 2026-10-27 已确认的产品决策一致），但把"旧端静默退出"升级为"旧端阻塞弹窗"**。B 按决策④**不做**（见 §13），C 明确不做。

### 3.2 检测通道：被动感知是后台系统的主流

| 通道 | 机制 | 时效 | 成本 | 适用 |
|---|---|---|---|---|
| **被动（下一次请求）**（一期采用） | 旧端下次带旧 token 请求 → 401 + reason | 用户下一次操作时，通常可接受 | 极低，无新增链路 | 管理后台、低频操作型系统 |
| 主动长连接推送 | 服务端在 `doReplaced` 时向旧连接推事件 | 秒级 | 高：需要 token→连接映射、跨实例广播、断线补推 | IM/游戏/云桌面 |
| 心跳/轮询探活 | 客户端定时探会话状态 | 秒~分钟 | 中：额外 QPS + 无效 token 时的请求风暴 | 挂机类长驻页面 |

本项目已有 `/ws/staff`(TOB)、`/ws/app`(TOC) 与 Redis 跨实例事件通道（`MessagePushEvent` / `message:push:events`），但 eventType 只有会话消息类，且"被顶"事件从 auth biz 到 message biz 要跨模块走 Dubbo/Redis。**结论：一期用被动感知 + 失效即断连；主动推送列"视需要"不排期**（主流后台系统也基本只做被动）。

### 3.3 提示形态：页内阻塞弹窗 > 全屏新页面 > toast > 静默跳转

| 形态 | 用户理解成本 | 现场保留 | 可操作性 | 备注 |
|---|---|---|---|---|
| **页内阻塞弹窗（管理端）** | 低 | **保留**（DOM/表单还在） | 可直接原地重登 | Vben 官方就提供 `loginExpiredMode: 'modal'`；UX 社区建议对"被登出"用不可忽略的 modal 阻止继续操作 |
| **全屏弹窗/新页面（移动端）** | 低 | 部分（靠 redirectPath 回跳） | 需跳登录页 | 小程序页面栈被清后无法保留现场，弹窗必须**在跳转前**显示 |
| Toast | 高（一闪而过） | 无 | 无 | 现在的移动端就是这种，等于没提示 |
| 直接跳转 | 最高 | 无 | 无 | 现在的管理端；用户不知道发生了什么 |

参考：Vben 文档明确"登录超时会跳转到登录页**或者打开登录弹窗**，在应用目录 `preferences.ts` 配置"（`https://doc.vben.pro/guide/in-depth/features.html`）；UX StackExchange 对 SSO 被登出设备建议"用覆盖全屏的 modal 阻止用户继续操作"（`ux.stackexchange.com/questions/102722`）；有开源项目专门把"跳转登录页"改成"页内 session-expired modal"（`goerp#1130`）；国内实践也把"响应拦截器识别会话失效 → 弹窗 + 多窗口监听"当作标准做法（掘金《登录失效时显示弹窗提示 + 多窗口监听》）。

### 3.4 文案对标

| 产品/场景 | 典型文案 |
|---|---|
| 微信 | "你的微信账号在另一台设备登录" |
| QQ | "你的账号于 xx 时间在另一台设备登录，如非本人操作请及时修改密码" |
| 钉钉/企业微信 | "账号在其它设备登录，你已被迫下线" + 重新登录 |
| 管理后台（国内实践） | "您已通过其他浏览器登录，请退出后再登录或者强制下线" |
| 云控制台（阿里云/腾讯云） | 会话失效直接回登录页（无原因弹窗，属反例） |

**本方案推荐（管理端）**：

> **账号已在其他设备登录**
> 当前账号于 21:32 在另一台设备登录，你已被迫下线。重新登录会使那台设备下线。
> ［重新登录］ ［退出登录］

**（移动端）**：同标题，正文精简为"当前账号已在其他设备登录，请重新登录后继续使用。"，按钮「重新登录」/「退出」。

### 3.5 调研结论

1. "被顶下线"必须**解释原因**并给**唯一明确的下一步**，否则用户会当成系统故障（尤其管理端在填单/审批中途）。
2. **原因必须由服务端给**。前端解析中文文案是最脆弱的做法（文案一改就崩、i18n 一上更崩），且本项目后端文案已有 `token已被顶下线` 和 `token 已被顶下线：xxx` 两种写法并存。
3. **管理端优先"页内弹窗 + 原地重登"**（保留现场是后台系统的关键价值），**移动端优先"弹窗说明 + 确认后跳转 + 回跳原页"**。
4. **降级路径必须明确**：拿不到原因（旧后端/滚动发布中）时用通用"登录已过期"文案，而不是不弹。
5. **不要用主动推送解决一期需求**：后台系统被顶的感知延迟天然等于"下一次操作"，投入产出比低。

---

## 4. 方案总览

### 4.1 设计原则

1. **原因权威在服务端**：新增 `data.reason`（+ 二期的 `subReason`、一期的 `device`），前端不解析 `msg`。
2. **弹窗而非跳转，阻塞而非 toast**：会话失效是"身份状态变化"，不是普通错误。
3. **单例 + 幂等**：一次失效只弹一次；并发 401 不得叠弹/叠 toast。
4. **不丢现场**：管理端原地重登；移动端 `redirectPath` 回跳。
5. **失效即冻结**：一旦判定失效，停发业务请求、断开 WS、禁止自动重连，避免 401 风暴与死 token 空转。
6. **向后兼容**：`code`/`msg` 语义不变，只新增字段；旧客户端行为完全不变，支持任意顺序滚动发布。
7. **双模式同源**：boot 与 cloud 用同一份契约常量与同一套文案，契约测试锁死。

### 4.2 分层与数据流

```
┌─────────────────────────── 后端（boot + cloud 同源契约）───────────────────────────┐
│ Sa-Token 场景值(-1..-7)          doReplaced(loginId)          主动踢人调用点(6 处)     │
│        │                              │                             │                │
│        ▼                              ▼                             ▼                │
│   SessionInvalidReason  ←──  SessionInvalidRegistry（一期：被顶设备信息 / 二期：踢人原因）│
│        │                                                                             │
│        ▼                                                                             │
│   SessionInvalidPayload（aryn-common-core：常量 + 枚举 + 组装 ToMap）                  │
│        │                       boot: SaServletFilter.setError                        │
│        │                       cloud: gateway SaReactorFilter.setError               │
│        │                       兜底: ArynGlobalExceptionHandlerResolver               │
│        ▼                                                                             │
│  HTTP 200 + { code:401, msg:"<脱敏文案>", data:{ reason, subReason?, invalidAt } }    │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                   │
        ┌──────────────────────────┴───────────────────────────┐
        ▼                                                      ▼
┌── 管理端 web-ele ─────────────────┐            ┌── 移动端 uniapp ──────────────────┐
│ request.ts 新增 reason 分流拦截器  │            │ handlers.ts 401 分支解析 reason   │
│        ↓                          │            │        ↓                          │
│ session store（单例状态机）        │            │ session store（单例状态机）        │
│        ↓                          │            │        ↓                          │
│ SessionInvalidModal（含 LoginForm）│            │ SessionInvalidDialog（wd-popup）  │
│ 原地重登 → 关闭 → 保留当前路由      │            │ 确认 → clearAuthData + reLaunch   │
│ BroadcastChannel 多标签同步         │            │ redirectPath → 重登回原页          │
└───────────────────────────────────┘            └───────────────────────────────────┘
```

### 4.3 会话失效状态机（两端共用语义）

```
                 ┌──────────────────────────────────────────────┐
                 │              VALID（正常）                     │
                 └──────┬───────────────────────────┬───────────┘
      收到 401(reason)  │                           │ 用户主动退出
                        ▼                           ▼
                 ┌──────────────┐             ┌──────────────┐
                 │ INVALIDATED  │             │  LOGGED_OUT  │
                 │ 弹窗已展示     │             │ 清态→登录页   │
                 └──┬────────┬──┘             └──────────────┘
      点「重新登录」  │        │ 点「退出」
                    ▼        └──────────────────────► LOGGED_OUT
              ┌───────────────┐
              │ REAUTHENTICAT-│  重登成功
              │ ING（弹窗内表单）│──────────────► VALID（关闭弹窗，保留当前页面）
              └───────────────┘
```

规则：

- **进入 INVALIDATED 的动作**：记录 `{reason, subReason, invalidAt, detectedAt}` → 清内存 token → 冻结请求（后续请求直接本地 reject，不发网络）→ 弹窗。
- **幂等**：`INVALIDATED` 状态下再次收到 401 一律静默 reject；不重复弹、不重复清、不重复 toast。
- **退出动作**：清本地持久化 + 跳登录页（管理端）/ `reLaunch` 登录页（移动端）。
- **重登成功**：清空失效状态、恢复请求、重连 WS；管理端留在当前路由并提示"已重新登录，请重试刚才的操作"。

### 4.4 401 分流矩阵（实现时的唯一真值表）

| 收到 | 条件 | 清本地态 | 弹窗 | 文案 | 后续 |
|---|---|---|---|---|---|
| 401 | `reason=BE_REPLACED` | 是（内存） | **是** | 账号已在其他设备登录 | 重登/退出 |
| 401 | `reason=KICK_OUT` + `subReason=ACCOUNT_DISABLED` | 是 | 是 | 账号已被停用，请联系管理员 | 退出 |
| 401 | `reason=KICK_OUT` + `subReason=PASSWORD_CHANGED` | 是 | 是 | 密码已修改，请重新登录 | 重登 |
| 401 | `reason=KICK_OUT` + `subReason=ACCOUNT_DELETED` | 是 | 是 | 账号已被删除 | 退出 |
| 401 | `reason=KICK_OUT` + `subReason=TENANT_DISABLED` | 是 | 是 | 所属租户已停用/到期 | 退出 |
| 401 | `reason=KICK_OUT` + `subReason=ADMIN_KICK` | 是 | 是 | 已被管理员强制下线 | 重登 |
| 401 | `reason=KICK_OUT` + 无 `subReason` | 是 | 是 | 登录状态已失效，请重新登录 | 重登 |
| 401 | `reason=TOKEN_EXPIRED` | 是 | 是 | 登录已过期 | 重登 |
| 401 | `reason=NOT_TOKEN`/`TOKEN_INVALID` | 是 | 是（`isAccessChecked` 之后）/ 否则直接去登录页 | 登录状态已失效 | 重登 |
| 401 | **无 `reason`**（旧后端 / 业务 401） | 是 | 是，通用文案 | 登录已过期，请重新登录 | 重登 |
| 401 | 请求是免登接口（`skipToken`/`publicRequest`） | **否** | 否 | 只 toast 服务端 msg | —— |
| 401 | 已处于 `INVALIDATED` | 否 | 否 | 静默 | —— |
| 401 | 用户主动退出流程中（`serverLogoutPending`） | 否 | 否 | 静默 | —— |
| 403 | —— | **否** | 否 | 权限不足 | 停留 |

> 移动端现有 `isPublicRequest` 与 `forbidden-response.test.ts` 已固定"免登 401 不清态""403 不清态"两条，方案必须继续满足。

---

## 5. 后端设计

### 5.1 响应契约（新增字段，不改既有语义）

保持现状形态（`HTTP 200` + `body.code=401`），只新增 `data`：

```json
{
  "code": 401,
  "msg": "账号已在其他设备登录",
  "data": {
    "reason": "BE_REPLACED",
    "subReason": null,
    "invalidAt": "2026-10-28T21:32:07+08:00"
  }
}
```

- **为什么不动 HTTP 状态码**：现有三端拦截器（管理端判 body `code===401`、移动端判 `statusCode===401 || code===401`）、Nginx/监控告警、以及 `public-request` 的既有测试都建立在"HTTP 200 + 业务码 401"上。改成 HTTP 401 属于协议级变更，收益（更 RESTful）远小于兼容风险。若后续要改，可在本契约上加 `X-Session-Invalid: 1` 响应头做过渡，不在一期范围。
- **为什么保留 `msg`**：旧客户端靠它做 toast；且作为人类可读兜底。但 `msg` 必须**脱敏**（去掉 token，见 §5.5）。
- **`data` 为对象而非字符串**：`Result<T>` 的 data 本来就是泛型（`Result.fail(int code, String msg, T data)` 已存在），`SaResult` 也有 `setData`，两侧都支持；旧客户端对 `data` 无感（管理端只取 `data.msg`，移动端成功分支才读 data）。
- **新增字段都可空**：拿不到就省略，前端按 §4.4 降级。

字段定义：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `reason` | enum string | 一期必填 | `NOT_TOKEN` / `TOKEN_INVALID` / `TOKEN_EXPIRED` / `BE_REPLACED` / `KICK_OUT` / `TOKEN_FREEZE` / `NO_PREFIX` |
| `subReason` | enum string | 二期 可空 | `ADMIN_KICK` / `ACCOUNT_DISABLED` / `PASSWORD_CHANGED` / `ACCOUNT_DELETED` / `TENANT_DISABLED` / `DELIVERY_REVOKED` / `MEMBER_DISABLED` |
| `invalidAt` | ISO8601 | 可空 | 失效发生时间（被顶/被踢时写；自然过期取 token 时间） |
| `device` | object | **一期** 可空 | `{ os, browser, location, loginTime }`；仅 `BE_REPLACED` 且有新会话信息时返回，取不到则省略（弹窗隐藏设备行）。规则见 §5.6 |

### 5.2 reason 映射（一期；映射本身零存储）

| Sa-Token 场景值 | 现行 msg（过滤器） | 新 `reason` | 新 `msg`（脱敏统一文案） |
|---|---|---|---|
| `NOT_TOKEN`(-1) | 未能读取到有效 token | `NOT_TOKEN` | 登录状态已失效，请重新登录 |
| `INVALID_TOKEN`(-2) | token 无效：xxx | `TOKEN_INVALID` | 登录状态已失效，请重新登录 |
| `TOKEN_TIMEOUT`(-3) | token 已过期：xxx | `TOKEN_EXPIRED` | 登录已过期，请重新登录 |
| `BE_REPLACED`(-4) | token 已被顶下线：xxx | `BE_REPLACED` | 账号已在其他设备登录 |
| `KICK_OUT`(-5) | token 已被踢下线：xxx | `KICK_OUT` | 登录状态已失效，请重新登录 |
| `TOKEN_FREEZE`(-6) | token 已被冻结 | `TOKEN_FREEZE` | 登录状态已冻结，请重新登录 |
| `NO_PREFIX`(-7) | 未按照指定前缀提交 token | `NO_PREFIX` | 登录状态已失效，请重新登录 |
| 其它 | 当前会话未登录 | `TOKEN_INVALID` | 登录状态已失效，请重新登录 |

> 注意：这里**只做"场景值 → 枚举"映射，不解析文本、不新增存储**。这是一期能在一周内落地且零风险的关键。

### 5.3 一期代码落点（原因契约）

| 文件 | 改动 | 说明 |
|---|---|---|
| `aryn-mall-java/aryn-common/aryn-common-core/src/main/java/com/aryn/cloud/common/core/enums/SessionInvalidReason.java`（新增） | 枚举 + `fromNotLoginType(String)` | L0 纯类型；boot 与 gateway 都能依赖（**gateway 只依赖 common-core**，不能依赖 common-security） |
| `aryn-mall-java/aryn-common/aryn-common-core/src/main/java/com/aryn/cloud/common/core/util/SessionInvalidPayload.java`（新增） | `Map<String,Object> of(reason, subReason, invalidAt)` + 常量 | 与枚举同处，保证两端字段名一致 |
| `aryn-mall-java/aryn-boot/src/main/java/com/aryn/cloud/config/SaTokenConfigure.java:54-57` | `setError` 中判断 `e instanceof NotLoginException` → 组装 `SaResult.error(msg).setCode(401).setData(payload)`；其它异常维持原样 | boot 主出口 |
| `aryn-mall-java/aryn-gateway/src/main/java/com/aryn/cloud/gateway/config/SaTokenConfigure.java:58-60` | 同上（反应式） | cloud 主出口 |
| `aryn-mall-java/aryn-common/aryn-common-security/.../handler/ArynGlobalExceptionHandlerResolver.java:113-136` | 改为 `Result.fail(401, msg, payload)` | 消灭 `code=1` 假登录；保留 `@ExceptionHandler` 兜底 |
| 测试：`aryn-boot/src/test/java/.../security/SessionInvalidContractTest.java`（新增） | ① 三个出口都调用公共 payload；② `reason` 与场景值映射全表；③ 响应 `msg` **不含 token**；④ 404/403 分支不受影响 | 仿 `SingleSessionPolicyContractTest` 的"双模式同值"契约风格 |
| `aryn-mall-java/aryn-common/aryn-common-security/.../util/SecurityUtils.java:45-56` + 新增 `SessionInvalidRegistry` | 登录前捕获旧 token 列表、登录后写 `BE_REPLACED` 失效记录（**含新设备信息**） | 一期设备信息，见 §5.6；`try/catch` 隔离，失败不影响登录 |

**为什么不把映射写进 `ArynSaTokenListener`**：filter 抛异常发生在 Servlet 过滤器链，异常类型信息已经在手上（`NotLoginException.getType()`），无需额外存取；且 `ArynSaTokenListenerTest:41-49` 已固化"侦听器只 delete"的契约，一期不动它可零测试冲突。

### 5.4 二期：细分"被踢"原因（Redis 原因注记，无表变更）

一期只能告诉用户"被踢下线了"，但业务上至少有 6 种截然不同的"被踢"，文案必须区分（"账号被停用"和"管理员把你踢了"是完全不同的处理路径）。

**机制**：新增 `SessionInvalidRegistry`（`aryn-common-security/.../support/`，`StringRedisTemplate`），同时承载二期"踢人原因"与一期"被顶下线的新设备信息"（§5.6）：

- key：`satoken:login:invalid-reason:{tokenValue}` → value JSON `{reason, subReason, invalidAt, operator?}`；TTL 建议 1 天（只需覆盖"用户回到旧端看到一次提示"的窗口）。
- 写入时机：**踢人前**写（在 `StpUtil.kickout*` 之前），因为 kickout 一执行 token 映射就没了。
- 读取时机：401 组装修复时按请求里的原始 token 读；读不到就只给一期的基础 reason（**降级不能影响 401 返回**，用 try/catch + warn 日志，绝不因 Redis 异常改变响应）。
- 清理：不主动删（TTL 自然过期）；下一个同 token 不可能复现（token 唯一）。

| 调用点 | 业务动作 | `subReason` |
|---|---|---|
| `aryn-upms-biz/.../support/SysUserSessionRevoker.java:27-39` | 员工停用 / 改密 / 删除（调用方 `SysUserController`、`SysUserServiceImpl`） | `ACCOUNT_DISABLED` / `PASSWORD_CHANGED` / `ACCOUNT_DELETED`（需要调用方把语义传进来，当前只有日志 `reason` 字符串） |
| `aryn-upms-biz/.../controller/SysOnlineUserController.java:110-123` | 管理员在"在线用户"页强退 | `ADMIN_KICK`（+ `operator`） |
| `aryn-upms-biz/.../support/TenantSessionRevoker.java:35-56` | 租户停用 / 到期 / 套餐变更 / 删除 | `TENANT_DISABLED` |
| `aryn-user-biz/.../service/impl/UserInfoServiceImpl.java:107-141` | C 端会员禁用 / 删除 | `MEMBER_DISABLED` |
| `aryn-order-biz/.../security/DeliveryAccessGuard.java:133-146` | 删除配送员 / 解绑商城账号 / 停用配送资格 | `DELIVERY_REVOKED` |
| `aryn-auth/.../listener/ArynSaTokenListener.java:114-146`（`doReplaced`） | 被顶下线 | **不需要改**：被顶的失效记录（含新设备信息）由一期在登录链路写入，见 §5.6 |

实现注意：

- `StpUtil.kickout(userId, device)` 是**批量**（该账号该端所有 token），所以封装成 `SessionInvalidRegistry.markAndKickout(userId, device, reason, subReason)`：先 `getTokenValueListByLoginId` 拿 token 列表 → 逐个写 reason → 再 kickout。封装点收敛在 `common-security`，避免 6 个调用点各写一遍 Redis。
- 监听器（`doReplaced`/`doKickout`）保持"只 delete 在线记录"的既有职责，**原因注记由调用方（`SessionInvalidRegistry`）负责**——这样 `ArynSaTokenListenerTest` 不受影响；"被顶"的设备信息也走登录链路（§5.6）而非监听器。
- 跨 biz 约束：`SessionInvalidRegistry` 放 `common-security`（L2），`upms-biz`/`user-biz`/`order-biz`（L4）与 `auth`（L5）均可依赖，符合分层规则；不新增任何 `biz → biz` import。

### 5.5 顺带必须修的安全问题（S0，建议与一期同批）

1. **401 响应体回显完整 token**：`NotLoginException.newInstance` 把 token 拼进 `message`，过滤器和兜底都直接 `e.getMessage()`。改为按 §5.2 输出固定文案，token 一律不进响应体。
2. **管理端把 `msg` 弹成 `ElMessage`**：改契约后 `msg` 已脱敏；同时管理端在会话失效分支不应再弹错误 toast（与弹窗重复）。
3. **兜底 handler 的 `code=1`**：统一为 401 + payload，避免"假登录"。
4. （记录，不改）`/validate-token` 不在白名单、`/toc-token/tenant-session` 把无 token/过期/被顶/被踢归并成同一句话——两者都不能当"会话探针"用，一期不依赖它们。

### 5.6 新设备信息（决策③，一期交付）

被顶下线的弹窗只说"在另一台设备登录"信息量不够；主流做法（微信/QQ/钉钉）都会带登录设备与时间，让用户能判断"是不是我"。决策③要求展示，故从原 P2 提前到一期。

**数据从哪来（全仓已有，无需新表、无需探针接口）**

- `ArynSaTokenListener.doLogin` 已在每次登录时解析 UA，把 `SysUserOnline` 写进 Redis：`satoken:login:online_user:{token}`，值含 `browser / os / ipAddr / location / loginTime / deviceType`（`aryn-auth/.../ArynSaTokenListener.java:77-96`、`CacheConstants.SYS_ONLINE_KEY`）。
- UA/IP 解析已有现成实现：`UserAgentUtil` + `JakartaServletUtil.getClientIP` + `IpUtils.getWhoisAddress`（同一监听器内已在使用），抽成 `LoginDeviceInfoResolver`（`common-security`）供监听器与登录流程共用。

**为什么"登录时写"而不是"401 时查"**

- `doReplaced` 回调发生在**新 token 创建之前**（Sa-Token `distUsableToken` 先 `replaced()` 再签发），此刻查不到"新设备"的会话；等 401 再按 `loginId` 反查新 token 要多一次读取，还得处理"会话又变了"的竞态。
- 登录请求本身就带着新设备的 UA/IP。**在登录链路把新设备信息写进旧 token 的失效记录**，401 时一次 `GET` 即可返回完整原因；同时覆盖"顶号后旧端很久才请求"的窗口。

**时序**

```
新设备登录请求（带 UA/IP）
→ SecurityUtils.loginByDevice(新用户, device)
   ├─ ① 登录前：prevTokens = StpUtil.getTokenValueListByLoginId(loginId, device)   // Sa-Token 1.45 已核实可用
   ├─ ② StpUtil.login(...)                     // 旧 token 在此被标记 -4
   └─ ③ 尽力而为：为 prevTokens 逐个写 satoken:login:invalid-reason:{oldToken}
          = { reason:"BE_REPLACED", invalidAt, device:{os,browser,location,loginTime} }
旧设备下一次请求 → 401 → 读 invalid-reason:{oldToken} → 直接带出 device → 弹窗展示
```

**响应片段**

```json
{
  "code": 401,
  "msg": "账号已在其他设备登录",
  "data": {
    "reason": "BE_REPLACED",
    "invalidAt": "2026-10-28T21:32:07+08:00",
    "device": {
      "os": "Windows 10",
      "browser": "Chrome 120",
      "location": "上海市",
      "loginTime": "2026-10-28T21:32:05+08:00"
    }
  }
}
```

**展示与脱敏规则（硬要求）**

| 项 | 规则 |
|---|---|
| `browser` / `os` | 原样展示（各截断到 20 字），解析失败则省略对应片段 |
| `location` | 只到城市/地区；`IpUtils` 返回空则省略；**不返回、不展示原始 IP** |
| `loginTime` | ISO8601 返回；前端按 `HH:mm` 展示，跨天显示 `MM-dd HH:mm` |
| 不得出现 | `token` / `loginId` / `tenantId` / 手机号 / 完整 IP |
| `device` 为空或不完整 | 弹窗**整行不渲染**，其余文案照常（降级不能变成"没有提示"） |

**实现约束**

- 写失效记录必须 **try/catch + warn 日志**，任何异常都不能影响登录主流程（登录是核心链路）。
- 只在 `BE_REPLACED` 时带设备信息；`KICK_OUT`（被踢）没有"新设备"概念，只带 `subReason`（二期）。
- 写入位置：`SecurityUtils.loginByDevice`（`common-security`，全仓唯一登录收口，5 个调用点自动覆盖）→ 经 `SpringUtils.getBean(SessionInvalidRegistry.class)` 调用（沿用仓库既有 `SpringUtils` 静态取 Bean 模式；`SecurityUtils` 是 `@UtilityClass`，改成实例注入会牵动 5 处调用与静态 mock 测试）。`prevTokens` 为空（首次登录）直接跳过，零 Redis 读。
- 双模式：boot 与 cloud 的 `loginByDevice` 是同一份代码，天然一致；`device` 只在两个 `setError` 出口读出，字段名由 `common-core` 常量统一。
- 测试：
  - `SessionInvalidRegistryTest`：写/读/降级/TTL；`prevTokens` 为空不写；
  - 登录链路测试：登录前捕获 + 登录后写入 + 写失败不影响登录；
  - 契约测试：`device` 字段名两端一致，响应不含 `token`/`loginId`/`tenantId`/`ipAddr`。

### 5.7 双模式一致性与契约测试

- boot 与 cloud 的差异只在"过滤器实现类"（servlet vs reactor），**契约常量、枚举、payload 组装、文案必须同源**（全部来自 `common-core`）。
- 新增 `SessionInvalidContractTest`（boot 模块）：
  - 断言 `aryn-boot/.../SaTokenConfigure.setError` 与 `aryn-gateway/.../SaTokenConfigure.setError` 源码都引用 `SessionInvalidPayload`（文本契约，和 `SingleSessionPolicyContractTest` 同风格）；
  - 断言 7 种场景值 → reason 全表映射；
  - 断言脱敏后的 `msg` 不含 `token` 子串；
  - 断言 `Result.fail(401, ...)` 分支保留 data。
- cloud 侧不新增 Nacos 配置、不新增 SQL → 无需 `db/boot`、`db/cloud` 增量脚本，也无需重新生成 `aryn_boot_full.sql`（**本需求零 DDL**）。

### 5.8 明确的非目标（本期不做）

- 不改单点策略（`is-concurrent` / `replacedLoginExitMode` / `replacedRange` 全部不动）。
- 不新增 refresh token 端点、不做静默续期。
- 不做"按端精准单点"（管理端与配送端仍互顶，见 2026-10-27 决策与本文 §13 已确认决策）。
- 不改 token 存储介质（管理端 SecureLS/localStorage、移动端 storage 均不动）。
- 不做会话表/审计表结构变更。

---

## 6. 管理端设计（`aryn-mall-ui/apps/web-ele`）

### 6.1 改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `apps/web-ele/src/preferences.ts:8-13` | 增加 `app.loginExpiredMode: 'modal'`（注意：改配置后需清缓存，Vben 文档已注明） |
| 2 | `apps/web-ele/src/api/request.ts:35-48` | `doReAuthenticate` 改为：写 session store（原因）→ `setLoginExpired(true)`；去掉对 `isAccessChecked` 的硬依赖（刷新后首请求也要弹）；仅在"当前就在登录页/无布局上下文"时降级为 `logout()` |
| 3 | `apps/web-ele/src/api/request.ts`（新增拦截器） | 新增 `sessionInvalidResponseInterceptor`，插入在 `authenticateResponseInterceptor` 之前：从 `error.response.data.data` 读取 reason/subReason，写入 session store；对 `sessionInvalid` 状态下的后续 401 直接 reject（不重复触发） |
| 4 | `apps/web-ele/src/store/session.ts`（新增） | `{ invalid, reason, subReason, invalidAt, detectedAt }` + `invalidate()` / `reset()` / `getCopy()`；纯内存（刷新即丢，见 §6.5） |
| 5 | `apps/web-ele/src/layouts/session-invalid-modal.vue`（新增，app 级） | 基于 `useVbenModal`：图标 + 标题 + 原因说明 + `LoginForm`；`closable=false`、`close-on-press-escape=false`、`footer=false`；z-index 复用现有 `calcZIndex()` 思路；**不改公共包** `packages/effects/common-ui`（避免影响其他 app） |
| 6 | `apps/web-ele/src/layouts/basic.vue:206-213` | 用新组件替换 `AuthenticationLoginExpiredModal`（或在其外层包一层，按 reason 渲染文案） |
| 7 | `apps/web-ele/src/store/auth.ts:79-85` | 重登成功后：`sessionStore.reset()` + 关闭弹窗 + 保留当前路由 + `ElMessage.success('已重新登录')` |
| 8 | `apps/web-ele/src/store/auth.ts:156-189` | `logout()`：若 `sessionStore.invalid` 为真，**跳过服务端 `logoutApi()`**（带着已失效 token 打接口只会再吃一个 401），直接本地清理 + 跳登录页 |
| 9 | `apps/web-ele/src/store/session.ts`（多标签） | `BroadcastChannel('aryn-session')` + `window.addEventListener('storage')` 兜底：`session-invalid` 广播让其它标签页同步弹窗；`session-restored` 广播让其它标签页 `location.reload()`（持久化 token 已更新） |
| 10 | `apps/web-ele/src/store/message.ts:340-390` | WS 重连前判断 `sessionStore.invalid`；失效时主动 `disconnect()`；`refreshNotifications()` 补 catch |
| 11 | `apps/web-ele/src/store/agent-presence.ts` | 补 `$reset()`（或不在 `resetAllStores` 遍历范围），修 dev 下 logout 中断（既有 bug，顺带修） |
| 12 | `apps/web-ele/src/api/request.ts:117-122` | 会话失效响应不再走 `ElMessage.error`（避免"弹窗 + 红 toast + 带 token 文案"三连） |

### 6.2 弹窗交互规格

```
┌──────────────────────────────────────────────────────┐
│                      ⚠（图标）                        │
│              账号已在其他设备登录                       │
│                                                      │
│   当前账号于 21:32 在另一台设备登录，你已被迫下线。      │
│   登录设备：Chrome · Windows 10｜地点：上海市           │
│   重新登录会使那台设备下线。                            │
│                                                      │
│   ┌────────────────────────────────────────────────┐  │
│   │  账号    [ 已填充/可编辑 ]                       │  │
│   │  密码    [                ]                     │  │
│   │  租户    [ 已填充 ]                             │  │
│   │            [      重新登录      ]               │  │
│   └────────────────────────────────────────────────┘  │
│                                                      │
│                    退出登录                            │
└──────────────────────────────────────────────────────┘
```

- **尺寸/层级**：沿用现有弹窗样式（`sm:w-[600px]`、圆角、阴影），`z-index = 页面最大值 + 1`（复用 `login-expired-modal.vue` 的 `calcZIndex` 思路）。
- **不可关闭**：Esc / 点遮罩 / 右上角关闭全部禁用；唯一出口是"重登成功"或"退出登录"。
- **自动聚焦**：打开后焦点落在密码框；Tab 顺序可控；`aria-modal`/`role=dialog`（Vben Modal 已带）。
- **登录表单复用**：直接复用 app 的 `LoginForm`（含租户选择、验证码），**不新写登录逻辑**；`account` 可预填（若当前登录信息可读），减少重登成本。
- **设备信息行**：仅当响应带 `data.device` 时渲染（`浏览器 · 系统` + `地点` + `时间`），缺失则整行不渲染；**不展示 IP**；字段与脱敏规则见 §5.6、文案见 §8.1。
- **重登成功**：弹窗关闭 → 页面保留 → `ElMessage.success` 提示"已重新登录，请重试刚才的操作"（因为刚才失败的请求不会自动重放；如要自动重放，见 §9"视需要"未排期）。
- **退出登录**：`sessionStore.reset()` → `authStore.logout()` → 登录页。
- **页面在提交中被顶**：弹窗出现时提交请求已被拦截/失败，浮层提示"刚才的操作未提交，请重新提交"（不做自动重放，避免重复扣款/重复审批）。

### 6.3 被顶下线的目标时序（改造后）

```
另一台电脑登录 → 旧 token 标记 -4
旧端用户点"保存" → 请求头带旧 satoken
→ 服务端返回 200 {code:401, msg:"账号已在其他设备登录", data:{reason:"BE_REPLACED", invalidAt}}
→ sessionInvalidResponseInterceptor: sessionStore.invalidate(reason,...) + 冻结请求
→ authenticateResponseInterceptor: doReAuthenticate → setLoginExpired(true)（不再 logout）
→ SessionInvalidModal 打开（页面保持原样，表单数据仍在）
→ 用户输入密码 → 登录成功（新 token 落 access store）
→ 关闭弹窗 + ElMessage.success + 广播 session-restored
→ 用户重新点"保存"→ 成功
```

### 6.4 多标签页

- 任一标签页检测到失效 → 广播 `{type:'session-invalid', reason, at}`：
  - 其它标签页：打开同一个弹窗（不各弹各的 toast）；
- 任一路径重登成功 → 广播 `{type:'session-restored', at}`：
  - 其它标签页：`location.reload()`（最简单可靠；token 已持久化，reload 后自动恢复；不会丢表单以外的状态，与"会话已恢复"语义匹配）。
- 降级：`BroadcastChannel` 不支持时（旧 Safari）用 `localStorage` 事件；两者都失败时退化为"各标签页各自弹"，不影响正确性。

### 6.5 边界与已知取舍

| 场景 | 行为 |
|---|---|
| 弹窗期间刷新页面 | 失效状态不持久化 → 路由守卫拿不到 token → 直接去登录页（可接受；**不要**把弹窗状态持久化，否则会出现"永远弹不掉的弹窗"） |
| 当前在登录页/非 BasicLayout 页面 | 无弹窗挂载点 → 直接 `logout()` 跳登录页（保持现状） |
| 锁屏（`LockScreen`）状态下被顶 | 一期：解锁后由下一次请求触发弹窗；二期可选：解锁时先弹失效提示 |
| 退出流程中收到 401 | 由 `serverLogoutPending` + `sessionStore.invalid` 双重短路，既不弹窗也不递归 |
| 心跳/轮询接口 401 | 走同一分流（只弹一次）；或显式在心跳处跳过（二选一，建议统一走分流） |
| 提示需要"较真"场景（合规审计） | 弹窗正文可加"如非本人操作，请立即联系管理员"（二期文案） |

### 6.6 测试

- 新增 `apps/web-ele/src/api/request-session-invalid.test.ts`：mock 四种响应形态（有 reason / 无 reason / 403 / 免登）→ 断言 store 状态、是否弹窗、是否调 logout、是否 toast。
- 新增 `apps/web-ele/src/store/session.test.ts`：幂等（连续 invalidate 只一次）、并发 401、reset 后恢复。
- 新增 `apps/web-ele/src/layouts/session-invalid-modal.test.ts`：8 种 reason 的标题/正文映射（文案表驱动）。
- 多标签：mock `BroadcastChannel`，断言广播与 reload 决策。
- 回归：`views/_core/authentication/login-security.test.ts`、`packages/effects/request/.../request-client.test.ts` 必须保持绿。
- **修复现有"空断言"**：管理端目前 `doReAuthenticate`/`loginExpiredMode` 零测试覆盖，本需求是补上的最佳时机。

---

## 7. 移动端设计（`aryn-mall-uniapp`）

### 7.1 改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/api/core/handlers.ts:171-177` | 401 分支改为：解析 `resp.data?.reason`/`subReason`（兼容 `resp.data` 为对象或字符串）→ `sessionStore.invalidate()`；**不再 toast + setTimeout + reLaunch** |
| 2 | `src/store/sessionStore.ts`（新增） | 单例状态机（同 §4.3）；`invalidate()` / `confirmRelogin()` / `exit()`；含 `isInvalid` 供请求层与 WS 层短路 |
| 3 | `src/components/SessionInvalidDialog.vue`（新增） | 基于 `wd-popup`/`wd-message-box` 的自定义弹窗，挂 `src/App.ku.vue:85-90` 根组件；含图标、标题、原因文案、可选"查看详情"、主按钮「重新登录」、次按钮「退出」 |
| 4 | `src/api/core/instance.ts:30,45-50`（beforeRequest） | `sessionStore.isInvalid` 为真时直接 `throw new ApiError(..., 'session-invalid')`，**不发网络**，避免 401 风暴（页面轮询/倒计时/商品列表会持续发请求） |
| 5 | `src/api/core/handlers.ts:16-29` | `redirectToLogin` 前写 `Local.set('redirectPath', getCurrentPath())`（工具 `src/utils/current-page.ts` 已有；登录页 `src/pages/login/index.vue` 的 `loginSuccess()` 已支持回跳） |
| 6 | `src/store/messageStore.ts:69-128`、`src/store/deliveryMessageStore.ts` | `connect()` 增加 `!sessionStore.isInvalid` 判断；失效时 `disconnect()`；`onClose` 重连前同样判断 |
| 7 | `src/api/upms/file.ts:74,109` | 配送端 401 统一收敛到 sessionStore（保留配送专用文案与目标页），去掉无提示的裸 `reLaunch` |
| 8 | `src/api/core/tenant-preflight.ts:77-110` / `instance.ts:66-79` | 租户 preflight 的 401 分支目前只 `$emit('auth-expired')` 不清态不提示——收敛到统一处理（既有空洞，顺带修） |
| 9 | `src/App.vue`（onShow） | 冷启动/回前台：若 `sessionStore.isInvalid`（仅内存标记，冷启动不保留）→ 走首个请求触发；二期可选：`onShow` 时若本地有"失效待提示"标记则直接弹窗 |
| 10 | 文案 `src/locales/zh-CN.ts` / 英文包 | 新增 `sessionInvalid.*` 词条（项目已支持中英双语，**必须两语言同时补**，见 2026-10-26 英文切换需求） |

### 7.2 弹窗交互规格

| 形态 | 能力 | 结论 |
|---|---|---|
| `uni.showModal`（小程序原生） | 纯文本、无 HTML、按钮文案 ≤4 汉字、随页面销毁 | **不用**：`reLaunch` 会把未关闭的 modal 一起干掉，且无法承载原因/设备信息 |
| `useGlobalMessage`（`wd-message-box`，已挂在根组件） | Vue 组件、三端一致、可自定义、跳转前不会被销毁 | 可用；但需要"标题+多行正文+双按钮+图标"的定制样式，建议直接写 `SessionInvalidDialog` 组件 |
| `wd-popup` 自研弹窗 | 完全可控 | **推荐**：与项目现有 UI 规范一致，可复用 `useGlobalMessage` 的挂载思路 |

弹窗内容（移动端）：

```
┌────────────────────────────────┐
│            ⚠                   │
│     账号已在其他设备登录          │
│                                │
│  当前账号已在其他设备登录，      │
│  请重新登录后继续使用。          │
│  设备：iPhone · 微信｜21:32      │
│                                │
│      [    重新登录    ]         │
│      [     退出       ]         │
└────────────────────────────────┘
```

- **不自动跳转**：用户点「重新登录」才 `clearAuthData()` → `redirectPath` → `reLaunch` 登录页；点「退出」→ 清态 → `reLaunch` 首页/登录页（不写 redirectPath）。
- **弹窗期间不清 storage**：避免页面数据瞬间清空（用户信息/购物车角标/主题消失会让人以为"数据没了"）；但请求已被 `sessionStore.isInvalid` 短路，不会再打服务端。
- **小程序后台切回**：`App.vue` 的 `onShow` 检查内存状态；若进程被回收导致状态丢失，则下一次请求重新触发（可接受）。
- **设备信息行**：`data.device` 存在时渲染（`系统 · 浏览器/微信` + 时间），缺失整行隐藏；小程序 UA 通常只能解析到「微信 · Android/iOS」，属预期，不展示 IP。
- **配送端**：同一组件、不同文案（"配送账号已在其他设备登录"）与目标页（`/pages/delivery/login`）。
- **H5/App**：组件在三端一致，无需 `#ifdef` 分支。

### 7.3 被顶下线的目标时序（改造后）

```
旧手机用户点"提交订单" → 请求带旧 token
→ 200 {code:401, data:{reason:"BE_REPLACED"}}
→ handlers.ts 解析 reason → sessionStore.invalidate()
→ 写 redirectPath=/pages/cart/index（示例）
→ SessionInvalidDialog 弹出（页面不动，仍在购物车）
→ 用户点「重新登录」→ clearAuthData() → reLaunch('/pages/login/index')
→ 登录成功 → loginSuccess() 读 redirectPath → 回 /pages/cart/index
（若用户点「退出」→ 清态 → reLaunch 首页）
```

### 7.4 边界

| 场景 | 行为 |
|---|---|
| 并发 401（首页多个楼层层层发请求） | `sessionStore.isInvalid` 同步置位 → 后续直接短路，只弹一次 |
| 免登接口 401（白名单漏配的历史坑） | 维持现有 `isPublicRequest` 分支：只 toast，不清态、不弹窗（**不能回归**） |
| 403 权限不足 | 维持现有：只提示，不清态、不弹窗 |
| 支付中/提交中被顶 | 弹窗说明"该操作未完成"，不自动重放（防重复下单/支付） |
| 弱网/离线 | 不进入失效流程（只有明确 401 才触发） |
| 冷启动即失效 | 首个请求触发弹窗；二期可选首屏探针 |
| 未登录用户进受限页 | 路由守卫已拦截（`router/index.ts:95`），不弹"被顶"文案；裸 `uni.navigateTo` 绕过守卫时由请求层兜底：`meta.hadToken=false` 的 401 只提示「请先登录」、不进会话失效（2026-10-29 修复），受限页自身展示游客态/登录引导 |

### 7.5 测试

- 扩展 `src/api/core/handlers.test.ts`：401+reason → 置状态、**不 reLaunch**、不 toast 旧文案；401 无 reason → 降级文案 + 置状态；免登 401 不变；403 不变。
- 新增 `src/store/session.test.ts`：幂等、并发、confirm/exit 分支。
- 新增 `src/components/SessionInvalidDialog.test.ts`：8 种 reason 文案表（中英）。
- **修现有空断言**：`forbidden-response.test.ts:66,98`、`public-request.test.ts:63` 的 `expect(reLaunch).not.toHaveBeenCalled()` 因 1s 定时器不推进而恒真——改为 fake timers，并把"跳转目标页/延时/去重/redirectPath"纳入断言。
- 回归命令：`cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit`。

---

## 8. 文案与 i18n 规范

### 8.1 文案表（两端共用口径）

| reason / subReason | 标题 | 正文（管理端） | 正文（移动端） | 主按钮 | 次按钮 |
|---|---|---|---|---|---|
| `BE_REPLACED` | 账号已在其他设备登录 | 当前账号于 {time} 在另一台设备登录，你已被迫下线。{deviceLine}重新登录会使那台设备下线。 | 当前账号已在其他设备登录，请重新登录后继续使用。{deviceLine} | 重新登录 | 退出 |
| `KICK_OUT`+`ACCOUNT_DISABLED` | 账号已被停用 | 该账号已被停用，无法继续使用。如有疑问请联系管理员。 | 该账号已被停用，请联系管理员。 | 返回登录 | —— |
| `KICK_OUT`+`PASSWORD_CHANGED` | 密码已修改 | 密码已修改，请使用新密码重新登录。 | 密码已修改，请使用新密码重新登录。 | 重新登录 | 退出 |
| `KICK_OUT`+`ACCOUNT_DELETED` | 账号已被删除 | 该账号已被删除，无法继续使用。 | 该账号已被删除。 | 返回登录 | —— |
| `KICK_OUT`+`TENANT_DISABLED` | 所属租户已停用 | 你所属的租户已被停用或已到期，请联系平台管理员。 | 账号所属店铺已停用。 | 退出 | —— |
| `KICK_OUT`+`ADMIN_KICK` | 已被管理员下线 | 管理员已强制将你下线。如非本人预期，请联系管理员。 | 管理员已将你下线。 | 重新登录 | 退出 |
| `KICK_OUT`+`DELIVERY_REVOKED` | 配送资格已变更 | —— | 你的配送资格已变更，请重新登录配送端。 | 重新登录 | 退出 |
| `KICK_OUT`+`MEMBER_DISABLED` | 账号已被禁用 | —— | 该账号已被禁用，请联系客服。 | 返回登录 | —— |
| `TOKEN_EXPIRED` | 登录已过期 | 登录状态已过期，请重新登录。 | 登录已过期，请重新登录。 | 重新登录 | 退出 |
| `TOKEN_INVALID`/`NOT_TOKEN`/`NO_PREFIX` | 登录状态已失效 | 登录状态已失效，请重新登录。 | 登录状态已失效，请重新登录。 | 重新登录 | 退出 |
| 无 reason（降级） | 登录状态已失效 | 登录状态已失效，请重新登录。 | 登录状态已失效，请重新登录。 | 重新登录 | 退出 |

> `{deviceLine}` 仅在 `data.device` 存在时渲染：管理端 `登录设备：{browser} · {os}｜地点：{location}｜时间：{HH:mm}`，移动端 `设备：{os} · {browser}｜{HH:mm}`；字段缺失则整行隐藏，**不显示"未知设备"**。

### 8.2 i18n key 约定

- 管理端（`aryn-mall-ui`）：新增命名空间 `sessionInvalid.*`（`title` / `content` / `relogin` / `exit` / `reason.<REASON>`），文案目录沿用 `packages/locales/src/langs/zh-CN/` 结构；**公共包只加纯文案，不加逻辑**。
- 移动端（`aryn-mall-uniapp`）：`src/locales/zh-CN.ts` + 英文包同步新增 `sessionInvalid.*`（项目已要求中英双语，见 2026-10-26 需求）。
- 服务端 `msg` 保持中文固定文案（旧客户端兜底展示；i18n 由前端 key 决定，不依赖服务端多语言）。

### 8.3 文案红线

- 不出现 "token"、"401"、"BE_REPLACED" 等实现术语（现有 `msg` 里的 `token 已被顶下线` 用户看不懂）。
- **不把 token 显示出来**（当前 `ElMessage` 会显示完整 token）。
- 被顶文案必须说明"另一台设备登录"（这是用户的真实疑问），而不是笼统的"登录失效"。

---

## 9. 分期、工作量与依赖

### 9.1 分期（按 §13 已确认决策重排）

| 阶段 | 范围 | 粗估 |
|---|---|---|
| **一期（本轮交付范围，已实施）** | ① 后端 reason 契约（枚举 + payload + boot/gateway/兜底三出口 + `msg` 脱敏 + 契约测试）；② **被顶下线的新设备信息**（登录时写失效记录 + 401 带 `device` + 脱敏，§5.6）；③ 管理端 `modal` 模式 + 原因/设备文案 + 原地重登 + 幂等；④ 移动端 401 分流 + 弹窗（**无"稍后"**）+ C 端/配送端 + `redirectPath` 回跳；⑤ 两端单测 | 后端 2.5d / 管理端 2d / 移动端 2d = **约 6.5 人日** |
| **二期（已实施）** | 细分"被踢"原因（`subReason` + 7 个调用点，§5.4）；管理端多标签页同步；WS 断连/重连治理；配送端上传 401 收敛；心跳/锁屏边界；`agent-presence.$reset` | 后端 1.5d / 前端 2d = **约 3.5 人日** |
| **不做（已明确）** | "新设备二次确认顶号"（决策④）；"稍后处理"按钮（决策②）；管理端/配送端解耦（决策①：保持现状互顶）；多端共存 | —— |
| **三期（已实施，2026-10-29）** | WS 主动推送 `SESSION_INVALID`（被顶/被踢秒级弹窗）；登录设备管理页（活跃会话 + 我的最近登录记录 + 自助下线非当前会话） | 后端 2d / 管理端 2d / 移动端 1.5d = **约 5.5 人日** |

### 9.2 上线顺序

1. 后端契约（向后兼容，旧前端无感）→ 2. 移动端 → 3. 管理端。任一步回滚都不影响其它端。
2. 管理端发布后注意：`loginExpiredMode` 存在用户偏好/缓存，**需验证线上生效**（Vben 文档提示改配置后清缓存），必要时在 `preferences.ts` 覆盖并清一次本地存储。

### 9.3 验证命令（实施后）

```bash
# 后端
cd aryn-mall-java && mvn -B test -pl aryn-boot -am
mvn -B test -pl aryn-auth,aryn-upms/aryn-upms-biz,aryn-user/aryn-user-biz,aryn-order/aryn-order-biz -am

# 管理端
cd aryn-mall-ui && pnpm check:type && pnpm test:unit && pnpm lint

# 移动端
cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit
```

---

## 10. 验收清单

> 说明：一期自动化覆盖见 §14.3（后端契约/单测、管理端单测+浏览器 e2e、移动端单测）；
> 标注「cloud / 真机」的项尚未在部署环境人工验收，上线前按 10.3 矩阵补齐。

### 10.1 功能验收

- [ ] 管理端：A 电脑登录后 B 电脑登录 → A 下一步操作**不跳转**，弹出"账号已在其他设备登录"，页面表单数据仍在。
- [ ] 管理端：弹窗内重登成功 → 关闭弹窗、停留原路由、新 token 生效、原操作可重试成功。
- [ ] 管理端：弹窗内点"退出登录" → 清态回登录页。
- [ ] 管理端：并发 3 个请求同时 401 → 只弹 1 次、只 1 条提示、无 401 风暴。
- [ ] 管理端：刷新页面后首个请求就是 401（`isAccessChecked=false`）→ 仍走弹窗（不再直接登出）。
- [ ] 管理端：多标签页 → 所有标签同步弹窗；任一标签重登后其它标签恢复。
- [ ] 移动端：A 手机登录后 B 手机登录 → A 在下一次操作时弹窗（**不是** 0.5s toast + 闪跳）。
- [ ] 移动端：点「重新登录」→ 登录成功回到**原页面**（`redirectPath` 生效）。
- [ ] 移动端：点「退出」→ 清态回首页/登录页。
- [ ] 移动端：配送端被顶 → 弹配送专用文案，登录后回配送工作台。
- [ ] 账号被停用/改密（二期）→ 弹对应文案，而不是笼统"被踢下线"。
- [ ] 自然过期（TOB 12h / TOC 30 天）→ 弹"登录已过期"。
- [ ] 被顶弹窗展示**新设备信息**（`浏览器 · 系统｜地点｜时间`）；信息缺失时整行隐藏、弹窗与重登流程不受影响。
- [ ] 弹窗只有「重新登录」「退出」两个出口，**没有"稍后处理"**（决策②）。
- [ ] 移动端 C 端与配送端均弹窗（决策⑤）。

### 10.2 兼容与安全验收

- [ ] 旧版移动端/管理端（未升级）对新增 `data` 完全无感，行为与现在一致。
- [ ] 滚动发布：后端先上 / 前端先上，两种顺序都不出现白屏或静默卡死。
- [ ] 被顶下线的 401 响应体**不含 token**；管理端不再把 token 弹到界面上。
- [ ] 403 权限不足仍然只提示、不清态（回归测试守住）。
- [ ] 免登接口 401 仍然只 toast、不清态（回归测试守住）。

### 10.3 双模式验收矩阵

| # | 模式 | 端 | 场景 | 期望 |
|---|---|---|---|---|
| 1 | boot | 管理端 | 同账号两浏览器登录 | 弹窗 + 原地重登 |
| 2 | cloud | 管理端 | 同上 | 同上（网关出口） |
| 3 | boot | C 端小程序 | 同账号两手机 | 弹窗 + 回跳原页 |
| 4 | cloud | C 端小程序 | 同上 | 同上（网关出口） |
| 5 | boot | 配送端 | 管理端登录顶掉配送端 | 弹配送文案 |
| 6 | cloud | 配送端 | 同上 | 同上 |
| 7 | boot/cloud | 管理端 | 停用/改密/删除账号 | 对应文案（二期） |
| 8 | boot/cloud | 管理端 | 管理员在"在线用户"强退 | "已被管理员下线"（二期） |

---

## 11. 风险与回滚

| 风险 | 影响 | 缓解 |
|---|---|---|
| **互顶拉锯**：A 重登把 B 顶掉，B 再重登把 A 顶掉 | 用户感知"一直在被踢" | 文案明示"重新登录会使那台设备下线"；"新设备确认顶号"按决策④不做，若后续改决策再评估 |
| 前端先上线、后端未上线 | 拿不到 reason，走通用文案 | §4.4 降级行保证不回归；不需要顺序强约束 |
| 管理端 `loginExpiredMode` 未生效（用户偏好缓存） | 仍走 page 模式 | 发布后清缓存 + 验证；或在 `preferences.ts` 强制覆盖 |
| 刷新页面丢现场 | 弹窗状态不持久化 | 接受；若强需求可二期用 `sessionStorage` 标记 + 登录页提示条 |
| 测试契约冲突（`ArynSaTokenListenerTest` 只许 delete） | 改监听器会红 | 一期/二期均不改监听器行为（设备信息与原因注记都在调用方写入），契约保持 |
| `resetAllStores` dev 抛错 | 本地登出中断 | 顺手修 `agent-presence.$reset` |
| 多标签广播兼容性 | 旧浏览器退化 | `BroadcastChannel` → `storage` → 各自弹窗 三级降级 |
| cloud 未部署实测 | 网关出口行为未验证 | 契约测试锁源码；上线前在 cloud 环境真机验证 #2/#4/#6 三条 |
| 存量已失效 token 无 reason 注记 | 升级后被踢用户仍只有通用文案 | 只影响升级瞬间存量会话，下一次登录自然恢复；可接受 |
| 新设备信息取不到（UA 缺失、IP 库离线、失效记录过期/被清） | 弹窗少一行设备信息 | 设备行是增强项：`data.device` 可空、前端整行隐藏；不因缺信息改变弹窗/跳转行为 |
| 登录链路多一次 Redis 写入 | 登录耗时 +~1ms、Redis 依赖面略增 | 仅"存在旧 token"时写；try/catch 隔离且失败只 warn，**绝不影响登录**；写入在登录成功之后 |

**回滚**：

- 管理端：`preferences.ts` 一行改回 `'page'`（或回滚版本）即恢复现状；
- 移动端：回滚 `handlers.ts` 与新增组件（后端 `data` 字段会被忽略）；
- 后端：`data` 字段是纯新增，回滚过滤器改动即恢复原 `msg` 行为；**无 DB 迁移、无需回滚 SQL**。

---

## 12. 备选方案与取舍

| 备选 | 为什么没选 |
|---|---|
| 只把管理端切 `loginExpiredMode: 'modal'` | 弹窗里没有原因文案、无"被顶"语义、移动端依旧 toast+闪跳；且仍卡 `isAccessChecked` |
| 前端解析 `msg` 文案（`msg.includes('顶下线')`） | 脆弱（文案/空格/标点/token 拼接任一变化即失效）、无法 i18n、无法承载"停用/改密"等细分原因 |
| 把 HTTP 状态改成 401 | 三端拦截器判据、监控告警、既有测试都建立在 HTTP 200 上，协议级变更风险 >> 收益 |
| 用 WS 主动推送替代被动感知 | 后台系统收益低（用户没有操作时也不需要提示）；WS 现有通道只服务消息中心，跨 biz 还要加事件类型 |
| 全局 toast 替代弹窗 | 与会话失效的严重性不匹配；toast 一闪而过，用户读不完原因 |
| 通过 `/validate-token`、`/toc-token/tenant-session` 做探针 | 前者被过滤器拦成 401、后者把四种原因归并成一句话，都拿不到原因 |
| 设备信息用独立探针接口（401 后二次请求） | 多一次请求 + 一个需白名单的接口面；设备信息在登录时已确定，随失效记录落 Redis，401 一次读取即可内联返回，无需探针 |
| 多端共存（不互踢） | 与 2026-10-27 已确认的产品与审计诉求冲突 |

---

## 13. 已确认决策（2026-10-28）

| # | 决策点 | 结论 | 对本方案的影响 |
|---|---|---|---|
| 1 | 管理端与配送端是否继续互顶 | **保持现状（互顶）** | 不做"按 scope 精准下线"；文案用通用"账号已在其他设备登录"，配送端同文案、仅目标页不同 |
| 2 | 弹窗是否提供"稍后处理" | **不提供** | 只有「重新登录」「退出」两个出口；移动端不保留只读态 |
| 3 | 是否展示新设备信息 | **要**（浏览器/系统/地点/登录时间） | 由原 P2 **提前到一期**：新增登录侧失效记录写入、401 携带 `device`、脱敏规则与验收项，见 §5.6 |
| 4 | 是否做"新设备二次确认顶号" | **不做** | 不改 `replacedLoginExitMode`；互顶拉锯靠文案提示缓解 |
| 5 | 移动端 C 端是否也弹窗 | **要** | C 端与配送端共用弹窗组件，仅文案/目标页不同 |

> 原"待确认"清单已全部关闭。若后续要改 ①/④，需要单独立项并重新评估 2026-10-27「全局单点登录」的产品决策。

---

## 14. 一期实施记录（2026-10-28）

> 范围：本文 §9.1 的「一期」——reason 契约 + 被顶下线的**新设备信息** + 管理端/移动端阻塞弹窗（无「稍后」、C 端也弹、重登回跳）。
> 决策依据：§13（①互顶保持现状 ②无稍后 ③要设备信息 ④不做新端确认 ⑤C 端也弹）。

### 14.1 交付文件

**后端（`aryn-mall-java/`）**

| 文件 | 改动 |
|---|---|
| `aryn-common/aryn-common-core/.../enums/SessionInvalidReason.java` | 新增：7 种场景值 → 冻结 wire 值 + 脱敏文案 + `fromSaTokenType/fromWire` |
| `aryn-common/aryn-common-core/.../util/SessionInvalidPayload.java` | 新增：`data` 组装，设备字段白名单 + 长度截断 + 空值省略 |
| `aryn-common/aryn-common-core/.../constant/CacheConstants.java` | 新增 `SESSION_INVALID_KEY = satoken:login:invalid-reason:` |
| `aryn-common/aryn-common-security/.../support/SessionInvalidSupport.java` | 新增：被顶下线原因/新设备信息的读写（登录链路写、401 出口读），Redis/Bean 异常只 warn 降级 |
| `aryn-common/aryn-common-security/.../util/SecurityUtils.java` | `loginByDevice`：登录前收集旧 token → 登录成功后写失效记录（旁路，失败不影响登录） |
| `aryn-common/aryn-common-security/.../handler/ArynGlobalExceptionHandlerResolver.java` | MVC 兜底改为 `Result.fail(401, 脱敏文案, data)`，消灭 `code=1` 假登录 |
| `aryn-boot/.../config/SaTokenConfigure.java`、`aryn-gateway/.../config/SaTokenConfigure.java` | `setError` 按场景值下发 `reason`（+ 被顶下线设备信息）；网关按同一契约直接读 Redis（不依赖 servlet 版 common-security） |
| `aryn-boot/src/test/.../security/SessionInvalidContractTest.java`（新增 10 例） | 枚举/wire/文案脱敏/设备白名单/三个出口同源/网关依赖约束/Redis key+TTL/契约文档冻结值 + **SaResult 实际序列化**（嵌套 data 不丢层） |
| `aryn-boot/src/test/.../security/support/SessionInvalidSupportTest.java`（新增 9 例） | 写入/读取/降级/TTL/敏感字段/幂等 |

**管理端（`aryn-mall-ui/`，新增 9 / 修改 10）**

- 新增：`store/session.ts`（失效状态机，纯内存）、`api/session-invalid.ts`（reason 解析/免登分流/重认证决策）、`layouts/session-invalid-modal.vue` + `session-invalid-content.ts`（阻塞弹窗 + 契约文案）、`packages/locales/src/langs/{zh-CN,en-US}/sessionInvalid.json`，及 3 个测试文件（95 例）。
- 修改：`preferences.ts`（`loginExpiredMode:'modal'`）、`main.ts`（老用户偏好缓存迁移，保证升级后不吃旧 `page`）、`api/request.ts`（reason 拦截器置于 authenticate 之前 + 会话失效不弹红 toast + `doReAuthenticate` 去掉 `isAccessChecked` 依赖）、`store/auth.ts`（原地重登收尾 / 失效时跳过服务端登出）、`layouts/basic.vue` + `app.vue`（布局内弹窗 + 刷新首 401 的根级兜底实例）、`store/agent-presence.ts`（补 `$reset`）、`packages/preferences/src/index.ts`（**根因修复**，见 14.3）、`views/_core/authentication/login.vue`（可选 `submitText`，默认行为不变）、`store/index.ts`。

**移动端（`aryn-mall-uniapp/`，新增 8 / 修改 20）**

- 新增：`store/sessionStore.ts`（失效状态机 + 回跳路径决策）、`utils/session-invalid.ts`（契约解析/设备行/时间/双语文案组装）、`components/SessionInvalidDialog.vue`（wd-popup 阻塞弹窗，挂 `App.ku.vue`）、`locales/modules/sessionInvalid.{zh-CN,en-US}.ts`，及 4 个测试文件（46 例）。
- 修改：`api/core/handlers.ts`（401 → 只置失效态，删除 500ms toast + 1s `reLaunch`）、`api/core/instance.ts`（失效时 beforeRequest 本地短路 + 租户 preflight 401 收敛）、`main.ts`（商城/配送分流的单一清态入口）、`store/persist.ts`（session 不持久化）、`store/messageStore.ts`/`deliveryMessageStore.ts`（失效断连 + 禁止重连）、`App.ku.vue`（弹窗挂载 + WS 断连）、`api/upms/file.ts`（配送覆盖裸 `reLaunch`）、`pages/delivery/login.vue`（配送 redirectPath 回跳）、`utils/current-page.ts`（`getCurrentFullPath` 带 query 回跳）、`utils/overlay.ts`（弹窗 z-index）及对应测试。

### 14.2 与设计稿的差异（均已论证，不违背契约）

1. **`SessionInvalidRegistry`（Spring Bean）→ `SessionInvalidSupport`（静态工具 + `SpringUtils.getBean`）**：common-security 的 Bean 在 boot 走组件扫描、在 cloud 各服务走 `AutoConfiguration.imports`；若再按类型注入，boot 会出现同名类型重复注册/歧义风险。改为无状态静态工具，沿用仓库既有的 `SpringUtils` 取 Bean 模式（同 `WxMiniAppConfiguration`）。
2. **设备信息改为读取「在线记录」而不是重新解析 UA**：`ArynSaTokenListener.doLogin` 已把 browser/os/location/loginTime 写进 `satoken:login:online_user:{token}`，直接复用可避免二次解析、尤其避免二次调用 `IpUtils.getWhoisAddress`（那是**外部 HTTP 调用**）。设备行缺失即隐藏。
3. **管理端根因修复 `packages/preferences/src/index.ts`**：`defineOverridesPreferences()` 原本整体替换 `preferences.app`，会静默丢掉 app 级 `loginExpiredMode:'modal'`（实测仍走 page → 整条链路失效）。改为默认值 + 展开调用方覆盖。
4. **`app.vue` 增加根级兜底弹窗实例**：刷新页面后首个请求 401 时，路由守卫里的 `fetchUserInfo` 会中断初始导航、BasicLayout 永不挂载（会白屏）；根实例仅在「路由未解析」时渲染，与布局内实例互斥。
5. **移动端「清态延后」并演进既有断言**：401 检测只进入失效态（不清 storage / 不 emit / 不 toast / 不跳转），清态与跳转发生在用户点「重新登录/退出」；`forbidden-response.test.ts` / `public-request.test.ts` 的「带凭证 401」用例演进为两阶段断言（检测→失效态；动作→清态+redirectPath+reLaunch），**免登 401 / 403 的断言原样保留**。

### 14.3 验证结果（本机真实执行）

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `cd aryn-mall-java && mvn -B test -pl aryn-boot -am` | **BUILD SUCCESS**（全模块通过；新增 19 例：契约 10 + 支撑 9） |
| 网关编译 | `mvn -B compile -pl aryn-gateway -am -DskipTests` | **BUILD SUCCESS** |
| 管理端类型 | `cd aryn-mall-ui && pnpm check:type` | **exit 0** |
| 管理端单测 | `pnpm exec vitest run apps/web-ele/src` | **94 files / 828 tests 全过**（新增 95 例；既有 `login-security.test.ts` 未改断言） |
| 管理端 lint | `pnpm exec eslint <改动文件>` | **exit 0** |
| 移动端类型 | `cd aryn-mall-uniapp && pnpm type-check` | **exit 0** |
| 移动端单测 | `pnpm test:unit` | **121 files / 1264 tests 全过**（基线 118/1218，新增 46 例，无既有失败） |
| 管理端浏览器端到端（teammate 执行） | Playwright + mock 后端 | **17/17 断言**：布局内被顶弹窗（文案/设备行/无「稍后」）、不跳转、无红 toast、原地重登停留原路由、过期换文案、退出回登录页；刷新后首 401 根级兜底弹窗 + 重登整页恢复 |
| 全仓管理端测试（teammate 执行） | `pnpm exec vitest run --dom` | 126 files / 1111 tests 全过 |

### 14.4 未覆盖 / 剩余风险

- **cloud 未部署实测**：网关出口只做了源码契约测试（读同一 Redis key/字段）；上线前需在 cloud 环境按 §10.3 矩阵真机验证 #2/#4/#6/#8（管理端、C 端、配送端经网关的 401）。
- **小程序未真机目检**：`pnpm type-check` + 1264 项单测通过，但弹窗层级/`wd-popup` 在微信真机上的表现需目检；H5/App 未单独验证。
- **旧 dev server 需重启**：新增 `sessionInvalid.json` 后 Vite 的 `import.meta.glob` 不会热更新新文件，浏览器验收前需重启 `pnpm dev:ele`（生产 build 不受影响）。
- **存量已失效 token 无记录**：升级瞬间已存在的被顶/被踢会话拿不到 reason，只走通用文案；下一次登录自然恢复。
- **登录链路依赖外部 IP 库（既有问题）**：设备信息复用在线记录，未新增调用；但 `IpUtils.getWhoisAddress` 本身仍是登录链路上的外部 HTTP 调用（无超时），建议单独排期治理。
- **二期已补**：细分「被踢」原因（`subReason` + 7 个调用点）、多标签页同步、WS 断连/重连治理、配送端上传 401 收敛、锁屏边界、`agent-presence.$reset`（见 §15）。
- **仍未排期**：WS 主动推送（秒级感知）、登录设备管理页（复用 `SysUserOnline`）。

### 14.5 上线顺序与回滚

1. 后端（纯新增 `data` 字段，旧前端无感）→ 2. 移动端 → 3. 管理端；任一步可独立回滚。
2. 管理端回滚：`preferences.ts` 改回 `'page'` 并清一次本地偏好缓存（或回滚版本）；移动端回滚 `handlers.ts` 与新增组件；后端回滚 `setError`/兜底 handler 即恢复旧 `msg` 行为。**无 DDL、无配置变更、无需回滚 SQL。**

---

## 15. 二期实施记录（2026-10-28）

> 范围：本文 §9.1 的「二期」——`subReason` 细分 + 管理端多标签页/WS/锁屏边界 + 配送端收敛。契约见 [二期实现契约.md](二期实现契约.md)。

### 15.1 交付文件

**后端（`aryn-mall-java/`）**

| 文件 | 改动 |
|---|---|
| `aryn-common-core/.../enums/SessionInvalidSubReason.java`（新增） | 7 个细分原因 wire 值 + 日志 label + `fromWire`（未知返回 null） |
| `aryn-common-core/.../util/SessionInvalidPayload.java` | 新增 `of(reason, subReason, invalidAt, device)`；**仅 `KICK_OUT` 输出 subReason**，其它原因忽略 |
| `aryn-common-security/.../support/SessionInvalidSupport.java` | 新增 `markKickoutTokens / kickoutWithReason / kickoutTokenWithReason`（先写原因再 kickout）；读取时解析 subReason |
| `aryn-gateway/.../config/SaTokenConfigure.java` | 网关出口解析同一 Redis 记录的 `subReason` 并下发 |
| 踢人调用点（7 处） | `SysUserSessionRevoker`（停用/改密/删除）、`SysOnlineUserController`（管理员强退）、`TenantSessionRevoker`（租户停用/到期/删除/强制下线）、`SysUserController`/`SysUserServiceImpl`（传语义）、`UserInfoServiceImpl`（会员禁用/删除）、`DeliveryAccessGuard`（配送资格撤销） |
| 测试 | `SessionInvalidContractTest`（+2 例：subReason wire/作用域/序列化 + 7 个调用点全链路）、`SessionInvalidSupportTest`（+5 例：写入/读取/未知值丢弃/kickout 委托）、`SysUserSessionRevokerTest`/`SysUserControllerPasswordTest`/`SysUserServiceImplTest` 签名演进 |

**管理端（`aryn-mall-ui/`，新增 3 / 修改 9）**

- 新增：`store/session-sync.ts`（多标签同步）、`store/session-sync.test.ts`、`store/message-session-invalid.test.ts`、`layouts/session-invalid-subreason.test.ts`。
- 修改：`store/session.ts`（subReason 清洗 + 门禁 + 锁屏解锁）、`api/session-invalid.ts`（解析 subReason、首次失效广播）、`layouts/session-invalid-content.ts`（reason+subReason 文案、`canRelogin`、单按钮）、`layouts/session-invalid-modal.vue`（双/单按钮分支）、`store/auth.ts`（reset 前广播 restored）、`store/message.ts`（WS 守卫 + refresh 兜底）、`app.vue`（启动/停止同步监听）、`packages/locales/.../sessionInvalid.json`（纯文案增量）。

**移动端（`aryn-mall-uniapp/`，修改 7 + 测试 4）**

- `utils/session-invalid.ts`（subReason 解析/文案表/按钮计划）、`store/sessionStore.ts`（subReason + `exitToLogin`）、`components/SessionInvalidDialog.vue`（单按钮变体）、`locales/modules/sessionInvalid.*.ts`（+8 组 key）、`api/core/handlers.ts`、`api/core/instance.ts`（subReason 全链路透传）及 4 个测试文件。

### 15.2 与契约的落地说明

1. **只有 TOB 相关的 5 个子原因在管理端展示**（`ADMIN_KICK`/`PASSWORD_CHANGED`/`ACCOUNT_DISABLED`/`ACCOUNT_DELETED`/`TENANT_DISABLED`）；`MEMBER_DISABLED`/`DELIVERY_REVOKED` 属移动端专用，管理端按未知值降级为通用「登录状态已失效」（不报错）。
2. **单按钮动作**：管理端复用 `authStore.logout(false)`（清态回登录页、不带 redirect 回跳）；移动端新增 `exitToLogin()`（商城→`/pages/login/index`、配送→`/pages/delivery/login`，不写 `redirectPath`）；两按钮场景的「退出」保持一期语义。
3. **多标签页**：`BroadcastChannel('aryn-session')` 优先、`localStorage`（`aryn-session-event`）兜底；`tabId` 去重；invalid → 其它标签落状态并弹窗、restored → 其它标签整页 reload（token 已持久化，刷新即恢复）。
4. **WS 治理落点**：在 `store/message.ts` 内部（`connect`/`onClose` 重连入口判 invalid、`watch(invalid)` 主动 `disconnect`、`refreshNotifications` 整体 try/catch），不依赖页面存活。
5. **锁屏边界**：放在 `sessionStore.invalidate()` 首次状态迁移里（`isLockScreen` → 先 `unlockScreen()` 再返回弹窗），同时覆盖「本地 401」与「多标签广播」两条入口。

### 15.3 验证结果（本机真实执行）

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `cd aryn-mall-java && mvn -B test -pl aryn-boot -am` | **BUILD SUCCESS**（全模块通过；契约 12 例、支撑 14 例全绿） |
| 网关编译 | `mvn -B compile -pl aryn-gateway -am -DskipTests` | **BUILD SUCCESS** |
| 受影响定向测试 | revoker 3 / password 7 / user-service 9 / tenant-service 3 / delivery-guard 15 / online-user 5 | 全部通过 |
| 管理端类型 | `cd aryn-mall-ui && pnpm check:type` | **exit 0** |
| 管理端单测 | `pnpm exec vitest run apps/web-ele/src` | **97 files / 901 tests 全过**（二期 +47 例） |
| 管理端 lint | `pnpm exec eslint <改动文件>` | **exit 0** |
| 移动端类型 | `cd aryn-mall-uniapp && pnpm type-check` | **exit 0** |
| 移动端单测 | `pnpm test:unit` | **121 files / 1295 tests 全过**（一期基线 1264 → 二期 +31） |
| 管理端浏览器 e2e（teammate） | Playwright + mock 后端 | **16/16**：单按钮「返回登录」/「退出」且无表单无回跳、双标签失效同步弹窗 + 重登后另一标签整页恢复、锁屏先解锁再弹窗 |

### 15.4 未覆盖 / 剩余风险

- **cloud 未部署实测**：`subReason` 在网关出口只做源码契约测试（读取同一 Redis 记录的 `subReason` 字段）；上线前与一期一起在 cloud 环境按 §10.3 矩阵补验。
- **小程序未真机目检**：单按钮变体/中英文案需真机确认；H5/App 未单独验证。
- **仍未排期**：WS 主动推送（秒级感知）、登录设备管理页。
- **旧 dev server 需重启**：新增 locale key 不会被 Vite `import.meta.glob` 热更新（生产 build 不受影响）。
- 后端 7 个踢人调用点里，`TenantSessionRevoker` 依赖在线记录才能定位 token（既有边界：无在线记录时踢不到，也就没有 subReason），残余会话由 token 过期兜底。

---

## 16. 三期实施记录（2026-10-29）

> 范围：WS 主动推送（被顶/被踢**秒级**弹窗，不等下一次 HTTP 401）+ 登录设备管理页（管理端弹窗 / 移动端 sub-page）。
> 契约见 [三期实现契约.md](三期实现契约.md)。

### 16.1 交付文件

**后端（`aryn-mall-java/`）**

| 文件 | 改动 |
|---|---|
| `common-core/.../constant/SessionInvalidPushConstants.java`（新增） | 推送通道（复用 `message:push:events`）、事件类型 `SESSION_INVALID`、接收者类型 `SYS_USER`/`MALL_USER`；发布方与 message-biz 引用同一常量 |
| `common-security/.../support/SessionInvalidSupport.java` | 写失效记录后发布推送事件（`publishSessionInvalid`）：被顶下线带新设备信息、被踢下线带 `subReason`；踢人前按 token 解析 loginId/tenantId/deviceType 做路由；事件不含 token/loginId 原始值，失败只 warn |
| `common-security/.../util/SecurityUtils.java` | `loginByDevice` 传入 loginId/tenantId/deviceType 供推送路由 |
| `message-api/.../dto/push/MessagePushEvent.java` | 新增可空字段 `reason/subReason/invalidAt/device`（向后兼容） |
| `message-biz/.../MessagePushServiceImpl.java` | 通道常量引用 common-core，保证两端不漂移 |
| `auth/.../controller/SessionDeviceController.java` + `vo/SessionDeviceVO.java`（新增） | `GET /session/devices` 当前账号活跃会话（token 只以 SHA-256 前 12 位出现、当前标记、按时间排序）；`DELETE /session/devices/{id}` 仅能下线自己的**非当前**会话 |
| `upms-biz/.../controller/SysLoginLogController.java` | 新增 `GET /sysloginlog/my`：当前账号 + 当前租户的最近登录记录（单页上限 20，不需要租户级日志权限） |
| 测试 | `SessionInvalidContractTest`（+3 例：推送契约/设备与登录记录接口安全边界）、`SessionInvalidSupportTest`（+3 例：推送字段/白名单/无路由跳过）、`SessionDeviceControllerTest`（4 例）、`SysLoginLogControllerMyTest`（3 例）、`MessageRedisPushListenerTest`（3 例：跨实例投递/本实例跳过/坏 JSON 兜底） |

**管理端（`aryn-mall-ui/`，新增 5 / 修改 6）**

- 新增：`api/core/session-devices.ts`（三接口，走统一 requestClient/boot 改写）、`layouts/login-devices.ts`（数据整形 + 可注入 controller）、`layouts/login-devices-modal.vue`、两个测试文件。
- 修改：`store/message.ts`（解析 `SESSION_INVALID` → `sessionStore.invalidate` + `setLoginExpired(true)` → `disconnect()` → return，不再刷新未读）、`layouts/basic.vue`（用户下拉新增「登录设备」入口 + `useVbenModal` 挂载）、`api/core/index.ts`、`layouts/account-menu-avatar.test.ts`（断言演进：只允许该业务菜单入口）、app 级 locale `page.loginDevices.*`。

**移动端（`aryn-mall-uniapp/`，新增 8 / 修改 6）**

- 新增：`api/session-devices.ts`、`composables/useUserLogout.ts`、`sub-pages/user/login-devices/`（页面 + 纯函数 + composable）、`locales/modules/loginDevices.*.ts`、2 个测试文件；`pages.json` 由 uni-pages 生成器按新页面重新生成（仅 +11 行）。
- 修改：`store/messageStore.ts` / `store/deliveryMessageStore.ts`（推送消费：失效态 + disconnect + return，不刷新未读）、`utils/session-invalid.ts`（事件解析）、`sub-pages/user/user-setting/index.vue`（「登录设备」入口 + 复用 `useUserLogout`）、locale 注册。

### 16.2 关键设计说明

1. **推送路由**：复用 message-biz 既有 `tenantId:identityType:identityId` WS 会话键；发布方（auth/upms/user/order 的踢人链路）在 common-security，**不依赖** `aryn-message-api`，只按 common-core 常量写 JSON 到 Redis；message-biz 的 `MessagePushEvent` 增加可空字段后原样投递。发布事件 `originInstanceId` 用随机值，保证所有 message 实例都尝试投递（只有持有该连接的实例真正发送）。
2. **尽力而为**：推送失败不影响登录/踢人；WS 未连、事件丢失时仍由一期/二期的 HTTP 401 链路兜底（前端 store 幂等，重复事件无副作用）。
3. **登录设备的现实边界**：当前 `is-concurrent=false`（同设备类型单点），列表通常只有「当前设备」一条；接口按多会话设计，仅渲染非当前会话的「下线」入口，当前设备提示走退出登录。管理端额外展示当前账号最近 10 条登录记录。
4. **隐私**：设备 `id` 为 token SHA-256 前 12 位（不可反推、不可重放）；接口/推送均不返回 token、loginId、完整 IP；登录记录不返回 `ipAddr`。

### 16.3 验证结果（本机真实执行）

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `cd aryn-mall-java && mvn -B test -pl aryn-boot -am` | **BUILD SUCCESS**（含三期新增：契约 14、支撑 16、设备接口 4、我的登录记录 3） |
| 网关编译 | `mvn -B compile -pl aryn-gateway -am -DskipTests` | **BUILD SUCCESS** |
| WS 投递单测 | `mvn -B test -pl aryn-message/aryn-message-biz -am -Dtest=MessageRedisPushListenerTest` | **3/3 通过**（跨实例事件完整投递、本实例跳过、坏 JSON 兜底） |
| 管理端类型 | `cd aryn-mall-ui && pnpm check:type` | **exit 0** |
| 管理端单测 | `pnpm exec vitest run apps/web-ele/src` | **99 files / 970 tests 全过**（三期 +69 例） |
| 管理端 lint | `pnpm exec eslint <改动文件>` | **exit 0** |
| 移动端类型 | `cd aryn-mall-uniapp && pnpm type-check` | **exit 0** |
| 移动端单测 | `pnpm test:unit` | **123 files / 1324 tests 全过**（三期 +29 例） |
| 管理端浏览器 e2e（teammate） | Playwright（注入假 WebSocket + mock 后端） | **14/14**：收到推送即弹窗且不刷新未读、主动断连；登录设备弹窗列表/当前标记/下线确认→DELETE→刷新/单点说明/错误重试 |

### 16.4 未覆盖 / 剩余风险

- **cloud 未部署实测**：redis 频道跨实例投递只做了单测（listener 层）；上线前需在 cloud 环境验证「auth/upms 踢人 → message-biz 实例 → 旧设备 WS 秒级弹窗」全链路，以及 boot 模式同 JVM 路径。
- **小程序/H5 真机未目检**：WS 推送直达时的弹窗时序（页面在前台/后台）、登录设备页 UI 需真机确认；微信小程序后台切回时 WS 可能已断开，仍由 HTTP 401 兜底。
- **单点策略下的能力边界**：登录设备列表通常只有当前设备，自助下线能力当前基本不可触发（为未来放开多端预留）；登录记录仅管理端（C 端无登录日志表数据）。
- **旧 dev server 需重启**：新增模块/页面/locale 靠 HMR 不一定齐（生产 build 不受影响）。
- 后端踢人链路的推送是旁路：Redis 不可用或 message-biz 未部署时只 warn，不阻塞踢人。

### 16.5 boot 重新打包部署与实测（2026-10-29）

按用户要求重新打包并部署 boot 单体，**部署验证时发现并修复了一个真实缺陷**：

- **缺陷**：Sa-Token 的 `SaServletFilter`（cloud 网关 `SaReactorFilter` 同理）用
  `String.valueOf(errorHandler.run(e))` 写响应，而 `SaResult#toString()` 对 `data` 只做
  `String.valueOf(Object)` —— 嵌套 Map 会输出 `{reason=NOT_TOKEN}`（键无引号，**非法 JSON**）。
  浏览器 Axios 无法 parse，导致真实环境 HTTP 401 拿不到 `reason`（此前自动化测试只覆盖了
  mock 响应对象，未覆盖 Sa-Token 实际写响应路径）。
- **修复**：新增 `common-core` 的 `SessionInvalidResponse.toJson(reason, data)` 预序列化 JSON 字符串，
  boot 过滤器与 cloud 网关过滤器都返回该字符串（`String.valueOf` 对 String 原样输出），并显式设置
  `Content-Type: application/json;charset=UTF-8`；契约测试改为直接校验该 JSON 字符串可被解析且
  `data.reason`/`data.device` 存在，并断言过滤器源码必须引用 `SessionInvalidResponse.toJson`。
- **部署**：`mvn -B clean package -DskipTests -pl aryn-boot -am` → `docker compose -f docker-compose-boot.yml build aryn-boot` →
  `up -d --no-deps aryn-boot`；旧镜像已备份为 `aryn-mall-java-aryn-boot:pre-session-invalid-backup`；
  容器内 `/app/app.jar` 与本地产物 md5 一致（`a0a284bc…`）。
- **实测结果**：
  - `/boot/actuator/health` → `{"status":"UP"}`（启动约 20s）；
  - `GET /boot/session/devices`（无 token）→ `{"code":401,"msg":"登录状态已失效，请重新登录","data":{"reason":"NOT_TOKEN"}}`，`Content-Type: application/json;charset=UTF-8`，**合法 JSON**；
  - 携带伪造 token → `data.reason=TOKEN_INVALID`（场景值 -2 映射生效）；
  - Redis `PUBSUB NUMSUB message:push:events` = **2**（WS 推送订阅者在线，三期推送链路可用）；
  - 启动日志无 ERROR/Exception（仅既有的 MyBatis Mapper 重复注册 WARN）。
- **回滚**：`docker tag aryn-mall-java-aryn-boot:pre-session-invalid-backup aryn-mall-java-aryn-boot:latest && docker compose -f docker-compose-boot.yml up -d --no-deps aryn-boot`。
- **cloud 注意**：网关过滤器同样改为 `SessionInvalidResponse.toJson`（已编译通过），cloud 环境重新发布网关 + 各服务后需同样验证一次 401 body 为合法 JSON。

**前端重新构建发布（2026-10-29，紧随后端部署）**

- 管理端 + H5（移动端 C 端）走仓库一键脚本 `./dev-tools/deploy-pages.sh`（重建 → 生成 `_worker.js` → wrangler 发布 Cloudflare Pages）：
  - H5：<https://aryn-h5.pages.dev>（deployment `e3ac2d2a.aryn-h5.pages.dev`）
  - 管理端：<https://aryn-admin.pages.dev>（deployment `9f29c1eb.aryn-admin.pages.dev`）
  - 后端代理目标：`https://wealth-groups-ivory-monitoring.trycloudflare.com`（`/api/boot/actuator/health` 实测 200/UP）
  - 产物校验：管理端上传产物含 `sessionInvalid-*.js` / `message-*.js`（`SESSION_INVALID`），H5 主 bundle 含 `SESSION_INVALID` 与「登录设备」；线上 chunk 可访问。
- 微信小程序：`cd aryn-mall-uniapp && pnpm build:mp-weixin` **构建成功**，产物 `dist/build/mp-weixin/`（5.9M，含 `utils/session-invalid.js`、`sub-pages/user/login-devices/` 且已在 `app.json` 注册）。仓库无 `miniprogram-ci`/上传私钥，需用**微信开发者工具导入该目录并手动上传/设体验版**。
- **演示环境限制**：Cloudflare Pages Functions 不支持 WebSocket → 在 Pages 上运行时三期「秒级推送」不可用，仍由 HTTP 401 弹窗兜底（本次已实测 401 body 合法 JSON）；只有直连后端（真机小程序/正式部署）才走 WS 推送。
- **小程序真机注意**：仓库 `.env.production` 的 `VITE_API_BASE_URL=http://localhost:9999` 仅适合本机开发者工具；真机/体验版需改成公网 HTTPS（如后端隧道或正式域名）后重新 `build:mp-weixin`（本次未改该配置）。

---

## 附录 A：证据索引（本次调研核实点）

后端：

- `aryn-mall-java/pom.xml:49` — Sa-Token 1.45.0
- `aryn-mall-java/aryn-boot/src/main/resources/application.yml:31-47` — `timeout: 2592000`、`active-timeout: -1`、`is-concurrent: false`、`is-share: false`、`is-log: false`
- `aryn-mall-java/aryn-boot/src/main/java/com/aryn/cloud/config/SaTokenConfigure.java:40-58` — `SaServletFilter.setError` → `SaResult` code=401
- `aryn-mall-java/aryn-gateway/src/main/java/com/aryn/cloud/gateway/config/SaTokenConfigure.java:52-60` — cloud 同构
- `aryn-mall-java/aryn-common/aryn-common-security/src/main/java/com/aryn/cloud/common/security/handler/ArynGlobalExceptionHandlerResolver.java:113-136` — 兜底 handler（code=1）
- `aryn-mall-java/aryn-common/aryn-common-core/src/main/java/com/aryn/cloud/common/core/util/Result.java:57-79` — `fail` 系列
- `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/listener/ArynSaTokenListener.java:114-146` — `doLogout/doKickout/doReplaced` 仅删在线记录
- `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/listener/ArynSaTokenListener.java:77-96` — `doLogin` 写 `SysUserOnline`（browser/os/ipAddr/location/loginTime），设备信息数据源
- `aryn-mall-java/aryn-common/aryn-common-security/src/main/java/com/aryn/cloud/common/security/util/SecurityUtils.java:45-56` — `loginByDevice`：全仓唯一登录收口（5 个调用点）
- `aryn-mall-java/aryn-common/aryn-common-core/src/main/java/com/aryn/cloud/common/core/util/SpringUtils.java:45` — 静态取 Bean（`@UtilityClass` 场景沿用）
- Sa-Token 1.45.0 源码核实：`SaResult#setData`、`StpLogic#getTokenValueListByLoginId(loginId, deviceType)` 可用；`SaTokenListener#doReplaced` 只有 `(loginType, loginId, tokenValue)` **无 device 参数** → 设备信息必须在登录链路写入
- `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/support/SysUserSessionRevoker.java:27-39` — 员工停用/改密/删除
- `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/controller/SysOnlineUserController.java:110-123` — 管理员强退
- `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/support/TenantSessionRevoker.java:35-56` — 租户级撤销
- `aryn-mall-java/aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/service/impl/UserInfoServiceImpl.java:107-141` — C 端会员禁用/删除
- `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/security/DeliveryAccessGuard.java:96-146` — 配送会话撤销与业务 401
- `aryn-mall-java/aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/websocket/MessageWebSocketConfig.java` — `/ws/staff`、`/ws/app`
- `aryn-mall-java/aryn-message/aryn-message-biz/src/test/java/com/aryn/cloud/message/websocket/WebSocketSecurityContractTest.java:18-37` — WS 握手契约
- `aryn-mall-java/aryn-auth/src/test/java/com/aryn/cloud/auth/listener/ArynSaTokenListenerTest.java:41-49` — 监听器"只 delete"契约
- `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/security/SingleSessionPolicyContractTest.java:48-58` — 双模式单点契约

管理端：

- `aryn-mall-ui/packages/effects/request/src/request-client/preset-interceptors.ts:61-72` — 重新认证判据 `data.code===401`
- `aryn-mall-ui/apps/web-ele/src/api/request.ts:35-48` — `doReAuthenticate`
- `aryn-mall-ui/packages/@core/preferences/src/config.ts:24,28` — `enableRefreshToken=false`、`loginExpiredMode='page'`
- `aryn-mall-ui/apps/web-ele/src/preferences.ts:8-13` — 仅覆盖 `app.name`
- `aryn-mall-ui/apps/web-ele/src/layouts/basic.vue:206-213` — 登录过期弹窗挂载点
- `aryn-mall-ui/packages/effects/common-ui/src/ui/authentication/login-expired-modal.vue` — 现有弹窗（不可关、无文案）
- `aryn-mall-ui/apps/web-ele/src/store/auth.ts:156-189` — `logout()`
- `aryn-mall-ui/packages/stores/src/modules/access.ts:102-120` — 持久化白名单（不含 `isAccessChecked`）
- `aryn-mall-ui/apps/web-ele/src/router/guard.ts:66-109` — 守卫与 `isAccessChecked` 时序
- `aryn-mall-ui/apps/web-ele/src/store/message.ts:340-390` — WS 无鉴权重连
- `aryn-mall-ui/packages/stores/src/setup.ts:51-60` — `resetAllStores`

移动端：

- `aryn-mall-uniapp/src/api/core/handlers.ts:16-34,66-82,128-142,171-177` — 401 处理与 toast/reLaunch
- `aryn-mall-uniapp/src/api/core/instance.ts:30,45-50,113-133` — 取 token、挂 `satoken`、responded 钩子
- `aryn-mall-uniapp/src/store/authStore.ts:21-32,65-92` — 登录态与 `clearAuthData()`
- `aryn-mall-uniapp/src/store/messageStore.ts:13-17,96-128` — 心跳与重连
- `aryn-mall-uniapp/src/api/upms/file.ts:74,109` — 配送上传裸 reLaunch
- `aryn-mall-uniapp/src/composables/useGlobalMessage.ts:38-47`、`src/components/GlobalMessage.vue`、`src/App.ku.vue:85-90` — 现有全局弹窗能力
- `aryn-mall-uniapp/src/utils/current-page.ts` — `getCurrentPath()`
- `aryn-mall-uniapp/src/pages/login/index.vue:92-110` — `redirectPath` 回跳已支持
- `aryn-mall-uniapp/src/api/core/forbidden-response.test.ts:73-86`、`public-request.test.ts` — 401/403 既有契约

## 附录 B：调研来源

- Sa-Token 官方文档《踢人下线》 `https://sa-token.com/use/kick.html`
- Sa-Token 官方文档《未登录场景值详解》 `https://sa-token.com/fun/not-login-scene.html`（明确"未登录、被顶下线、被踢下线等场景需要不同方式处理"）
- Sa-Token 官方文档《同端互斥登录》 `https://sa-token.com/up/mutex-login.html`（同类型设备单点、不同类型设备可同时在线）
- Sa-Token 源码 `NotLoginException`（-1~-7 场景值常量） `https://github.com/dromara/Sa-Token`，本地核实 1.45.0 `StpLogic#distUsableToken`
- Vben Admin 文档《常用功能》 `https://doc.vben.pro/guide/in-depth/features.html`（`loginExpiredMode: 'page' | 'modal'`）
- UX StackExchange《Single sign-on App》 `https://ux.stackexchange.com/questions/102722/single-sign-on-app`（被登出设备用全屏 modal 阻止继续操作）
- UX StackExchange《Where to show session has expired》 `https://ux.stackexchange.com/questions/117972/where-to-show-session-has-expired`
- 开源实践：`goerp#1130` "Show a session-expired modal over the page instead of redirecting to login" `https://github.com/djangbahevans/goerp/pull/1130`
- 掘金《实现登录失效时显示弹窗提示 + 多窗口监听数据变化》 `https://juejin.cn/post/7386958406136086578`
- CSDN《同一账号如何实现多端登录强制下线的功能》 `https://blog.csdn.net/2301_79727522/article/details/144166997`（"您已通过其他浏览器登录，请退出后再登录或者强制下线"）
- 人人都是产品经理《经验分享｜To B 产品的登录安全》 `https://www.woshipm.com/pd/638833.html`
