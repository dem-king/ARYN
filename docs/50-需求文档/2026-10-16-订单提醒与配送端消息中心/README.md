# 订单事件工作人员提醒与配送端消息中心

> 日期：2026-10-16 ｜ 状态：**已实施**（三端验证通过）
> 起因：运营反馈「商城新订单、退单等等，目前有没有接入站内信？感觉没有任何通知，
> 就算工作人员坐在电脑前开着系统，不专门点击刷新，根本不知道有新订单、取消订单、退单」

## 一、问题定位

基础设施是齐的（`message_notice` 站内信 + MQ 命令模式 + `/message/ws/staff` WebSocket
实时推送 + Redis 多实例广播），管理端铃铛也一直活着——**但只对「有人给某个工作人员
发了站内信」或「有客服新会话」这两个条件亮起**。订单类事件此前只通知买家：

| 场景 | 改造前 | 改造后 |
|------|--------|--------|
| 新订单提交 | 仅买家（`OrderCreatedMessageNotifier`） | **租户 ROLE_ADMIN**（2026-10-20 起取消买家自推送） |
| 取消订单（买家/超时/管理端） | 无任何消息 | **租户 ROLE_ADMIN**，三种来源各自标题 |
| 退款申请 | 无任何消息 | **租户 ROLE_ADMIN**（退单管理跳转） |
| 退款完成 | 仅买家 | 不变（买家侧已有） |
| COD 收款提醒 | 买家 + ROLE_ADMIN | 不变（既有样板） |
| 配送派单 | 司机 | 不变 |

管理端没有任何轮询/桌面通知/提示音，订单与退单列表只在打开时拉一次 —— 因此工作人员
不刷新就完全静默。配送端小程序更是连站内信入口都没有，发给司机的消息「发了收不到」。

## 二、后端改动

### 新增共享通知服务

`OrderStaffNotifyService` / `OrderStaffNotifyServiceImpl`（aryn-order-biz）：
收件人解析复用 COD 收款提醒的口径（`RemoteMessageStaffService.queryRecipientsByRoleCode`
+ `ROLE_ADMIN_CODE`，逐人下发 `MessageSendCommand`），幂等靠站内信
`(source_type, source_key)` 唯一键，`eventId` 含业务主键。跳转统一指向订单/退单管理列表。

**时序约定**：本服务立即发送、不感知事务，调用时机由调用方负责（与
`OrderPaySuccessNotifier` 同约定）。事务内调用点用 `TransactionalMqUtils.sendAfterCommit`
包一层；反过来在 AFTER_COMMIT 监听器里调用时**不能**再包——此时事务同步仍处于激活态，
新注册的回调不会被执行，通知会被静默丢弃。

### 三个接线点

- **新订单**：`OrderCreatedMessageNotifier` 新增 `notifyStaff` 监听器（同为 AFTER_COMMIT），
  COD 单文案强调「无需等待支付，请及时安排配送」，在线单强调「关注支付情况」。
- **取消订单**：`cancelOrder` 拆出 `doCancelOrder`，新签名
  `cancelOrder(orderInfo, CancelSource)` 在提交后发通知。四个调用方显式传来源：
  `cancelUserOrder`→BUYER、`OrderCancelListener`（MQ 延迟消息）→TIMEOUT、
  管理端 Controller→ADMIN、超时扫描 Job→TIMEOUT。
- **退款申请**：挂在 **C 端 Controller**（`AppOrderRefundController.saveRefund`）
  而不是服务层 `saveRefund`——拼团成团失败的整单自动退款也复用 `saveRefund`，
  放服务层会让自动退款误报「待审核」。只有买家在本入口提交且状态为
  `PENDING_REVIEW` 时才通知。

### 踩到的两个坑

1. **租户取值**：租户拦截器只把 `tenant_id` 注入 SQL、**不回填实体**，创建链路的
   `orderInfo.getTenantId()` 常为空（`MessageSendCommand.tenantId` 是 `@NotBlank`）。
   已加 `resolveTenant`：实体值优先，为空回落 `ArynTenantContextHolder`（与
   `OrderPriceComputeService`、订单取消既有写法同口径）。
2. **退款单号口径**：`OrderRefund` 只存 `orderId`，通知里要展示订单号需回查
   `OrderInfoMapper`；回查失败退回订单 ID 展示，不吞掉整条通知。

## 三、管理端：声音 + 语音播报 + 桌面通知

新增 `apps/web-ele/src/composables/use-message-alert.ts`（纯逻辑可测 + 副作用隔离）：

- **上升沿判定**：首次快照只用于定基线——登录时拉到的历史未读不是「新消息」；
  只报未读增加的部分，已读导致下降不提醒。
- **音频自动解锁（关键修法）**：浏览器 autoplay 策略要求音频先在用户手势里解锁。
  早期实现只在「切换开关」时解锁，于是刚打开后台、**什么都没点过的管理员
  第一条新订单是静默的**——这正是运营反馈的「感觉必须点了才有声音」。
  现改为 `armAudioUnlock()` 在布局启动时挂上首次任意手势（click/keydown/pointerdown/
  touchstart，capture 阶段）监听，解锁后立即卸载；登出时 `detachAudioUnlock()`。
- **语音播报（预生成音频优先 + TTS 兜底）**：定稿话术用预生成 wav（`public/static/voice/`，
  统一外卖接单音风格，`speak()` 优先播放；共 12 个文件）——
  新订单「您有一笔新订单，请及时处理」（多条合并不分笔数，统一「您有新的订单，请及时处理」；
  **不播金额**，金额看铃铛/桌面通知即可）、取消订单按来源各一份
  （买家取消订单 / 订单超时自动取消 / 管理端取消订单）、退款申请「有新的退款申请待审核」、
  COD「有货到付款订单待收款」、会话「有新的客服消息，请及时回复」、
  船舶绑定按来源（有新的船员绑定申请 / 有新的业务员认领船舶申请，多条合并
  「有新的船舶绑定申请待审核」，不念数量）、开关试播「语音播报已开启」。
  无现成音频的动态标题（如系统通知）回退浏览器 TTS。
  **4 秒节流**：连单高峰只更新铃铛不逐条朗读（播报前先 `cancel()`，避免排队念过期信息）。
  **音色选优**（TTS 兜底路径）：按可观测特征给中文音色打分（zh-CN、神经/在线、晓晓/婷婷等高质感
  音色名加分），并预热 `voiceschanged` 音色列表，避免首次播报落到默认音色。
- **提示音**：WebAudio 合成两声短促上行音（880/1180Hz），不引二进制音频资源。
- **桌面通知**：`Notification` API，`tag` 固定避免糊满通知中心，点击回到页面。
- **开关**：铃铛弹层「新消息提醒」分组，声音 / 语音播报 / 桌面通知三者独立，
  落 localStorage（缺省开启，显式存 `'0'` 才关闭）；**打开桌面通知时才申请权限**
  （浏览器要求用户手势），权限被拒就把开关落回关闭，避免「开着但永远不会响」。
  语音开关打开时试播「语音播报已开启」，让用户确认本机有可用音色。
- 文案：会话优先（有人在等回复比一条通知更急），标题取铃铛列表里对应类型的第一条。

铃铛组件在 `packages/effects/layouts`（共享包），因此开关通过 props 传入、
事件向上抛，未传则不渲染（不污染通用组件默认外观）。

> 声音/语音**不是**点击铃铛才响：新订单到达时由 WebSocket 推送 + 未读上升沿直接触发，
> 与铃铛是否展开无关。点击铃铛只是查看列表，不参与提醒触发。

## 四、配送端小程序：消息中心

关键发现：**配送 token 在服务端就是 TOB 的 SYS_USER**（`DeliveryAuthService.exchange`
用商城登录态换取员工身份登录），所以司机端可以直接复用工作人员消息通道，
不需要新接口。

- `src/api/delivery-message.ts`：`/message/staff/notice*` 四个接口，全部带
  `authScope: 'delivery'`（否则请求层会拿商城 token 去调，对不上身份）。
- `src/store/deliveryMessageStore.ts`：独立于商城端 `messageStore`（两者 token 不同，
  混用会互相覆盖未读数），连 `/message/ws/staff`，心跳/PONG 超时/断线重连与商城端同口径。
- `src/pages/delivery/message.vue`：消息列表 + 全部已读；点击只标记已读**不跳管理端路由**
  （`jumpPayload` 里的 `/order/**` 是管理端页面，司机点过去只会 403）。
- 入口：工作台快捷入口 + 个人中心菜单，均带未读角标；退出登录先断消息通道再清 token。

### 顺手修掉的跨端缺陷

响应侧 `isDeliveryRequest` 只按 **URL** 判断「这是不是配送请求」，而请求侧在
`beforeRequest` 里已经删掉了 `authScope` 头。结果：新接口 `/message/staff/**` 的 401
会被当成商城请求 → **错清商城登录态并把司机踢到商城登录页**。修法：
请求侧把 `deliveryRequest` 写进 `meta`，响应侧优先读 `meta.deliveryRequest`。

## 五、验证

- **后端**：`mvn clean compile -pl aryn-boot -am` 通过；订单模块 442 条测试全绿
  （新增 `OrderStaffNotifyServiceImplTest` 14 例：收件人口径、租户回落、双通道文案、
  三种取消来源、退款跳转、收件人查询失败/发送失败/null 实体降级）。
- **管理端**：`vue-tsc` 通过；vitest 653 条全绿（新增 19 例提醒逻辑 + 5 条「自动响」契约）；
  eslint + prettier 干净。
- **小程序**：`vue-tsc` 通过；vitest 677 条全绿（新增 7 条配送端契约测试）；
  eslint 干净；`pages.json` 已重新生成并验证生成幂等。
- **已知无关失败**：`aryn-boot` 全量测试有 2 条既有失败
  （`TenantConfigurationConsistencyTest` 缺 `vessel_port_vehicle`、
  `TenantInterceptorBypassAuditTest` 方法清单漂移），涉及文件 mtime 均在 09-23~10-11，
  与本次改动无交集，属工作树中其它未提交改造留下的存量漂移。
- **未做**：无数据库结构变更（纯代码接线，`message_notice`/`message_recipient` 既有表
  足够承载）；声音与语音播报未在真实浏览器做听觉验收（音色可用性取决于操作系统，
  Chrome 首次 `getVoices()` 可能为空但播报仍会走默认音色）。

## 六、后续扩展（2026-10-18）：船舶绑定申请提交也提醒运营

同一套链路随后被复用到「船舶绑定申请提交」场景：船员/业务员在小程序提交申请后，
管理端铃铛 + 语音提醒运营。收件人改为**按审核权限 `vessel:bindapply:audit` 筛**
（而不是沿用订单场景的 `ROLE_ADMIN` 角色码），无人持权限时回落租户管理员；
同时在 upms 受众查询上泛化出 `StaffMessageAudienceRequest.permissions` 能力。
设计细节、改动清单与验证见
[船舶绑定申请待审核提醒](../2026-10-16-船舶绑定申请审核提醒/README.md)。

## 七、2026-10-18 二次修复：语音播报同样受浏览器手势限制

第一轮修好「已读后基线不同步」与提示音补播后，运营再次反馈：新订单只进铃铛、**没有语音**。

真实 Chrome 155 实测结论（`--remote-debugging-port` + 独立 profile，与前端同源）：

- 页面**尚未获得用户手势**时，`speechSynthesis.speak()` 不抛异常，只异步派发
  `SpeechSynthesisErrorEvent.error = "not-allowed"`，声音被静默丢弃；
  一次可信点击后 `navigator.userActivation.hasBeenActive = true`，同样的 `speak()` 立即 `start/end`。
- 即第三节的「语音与 AudioContext 的自动播放状态无关」是错误假设：
  **Chrome 的 Web Speech 与音频自动播放同受用户激活限制**。
- 命中时序：管理端页面整页重载后、运营还没点过页面就来了新订单 → 语音被
  `not-allowed` 丢弃；提示音有补播队列，语音没有，于是表现为「只有站内信、没有语音」。

修法（`use-message-alert.ts`，与提示音同口径）：

- `speak()` 在页面未获得用户激活时先 `queueSpeechReplay()` 排队；已激活才真正 `speak`。
- 接住 `utterance.addEventListener('error')` 的 `not-allowed` 兜底补播一次
  （`userActivation` 取不到的旧浏览器需要），`blocked` 标记避免无限重试。
- `unlockAudio()` 首次手势解锁时同时补播音频与语音；只补最近一条、60 秒 TTL、
  关掉语音开关则不补；登出清理两份队列。
- 边界：无手势时浏览器禁止发声是硬限制，无法绕过；正常 SPA 登录路径本就有手势，
  只有整页重载后尚未点击的窗口会命中「排队 + 首次手势补播」。

验证：真实 Chrome 端到端（动态导入真实模块）——新文档无手势时 speak 被排队、
真实点击后 `speak-called/start` 补播成功；已激活后立即播报；对照旧逻辑直接
`speechSynthesis.speak()` 得到 `error=not-allowed`。`pnpm test:unit` 88 文件 670 条全绿、
`pnpm check:type` 通过、eslint/prettier 干净。详见
[需求记录](../../90-记录归档/需求记录.md)「2026-10-18 管理端语音播报无声（第二轮）」。
