# ARYN 站内信 / 通知中心 — 可行性分析报告

**分析日期**：2026-07-05
**分析对象**：`ARYN站内信技术方案_2026-07-05.md`（QClaw 生成）
**分析方法**：方案逐条对照悦航购当前代码库核查（文件路径 + 行号佐证）
**结论**：**方案整体可行，但存在 6 处必须修正的约定不符 + 2 块需额外建设，不可直接照搬代码生成。修正后预估 2 人周。**

---

## 一、总体结论

| 维度 | 评估 |
|------|------|
| 架构方向 | ✅ 合理。事件驱动 + 模板渲染 + Redis 计数 + WS 推送，是电商站内信的标准范式 |
| 模块拆分 | ✅ 符合项目 `*-api` / `*-biz` 惯例（参考 aryn-order） |
| MQ Topic 复用 | ⚠️ 5 个监听器中 4 个 Topic 已存在可用，1 个（发货）Topic 缺失 |
| 代码可落地性 | ❌ **不能直接生成**。DB 字段命名、类型、工具类、前端 API 路径等多处与项目实际不符 |
| 工时估算 | ⚠️ 方案估 1.5 人周偏乐观。含 WebSocket 基建 + 发货事件补链 + 联调，实际 **2 人周** |

---

## 二、可行性核查矩阵

### 2.1 已具备、可直接复用 ✅

| 依赖项 | 方案假设 | 实际情况 | 佐证 |
|--------|----------|----------|------|
| RocketMQ Topic（支付成功） | `ORDER_PAY_SUCCESS_NOTIFY_TOPIC` | ✅ 存在 | `aryn-common-core/.../RocketMqConstants.java:19` |
| RocketMQ Topic（订单完成） | `ORDER_COMPLETE_NOTIFY_TOPIC` | ✅ 存在 | 同上 :25 |
| RocketMQ Topic（退款成功） | `ORDER_REFUND_SUCCESS_NOTIFY_TOPIC` | ✅ 存在 | 同上 :22 |
| RocketMQ Topic（订单取消） | `ORDER_CANCEL_TOPIC` | ✅ 存在 | 同上 :13 |
| RocketMQ Topic（退款回调） | `PAY_REFUND_NOTIFY_TOPIC` | ✅ 存在 | 同上 :28 |
| 事件类 `OrderPaySuccessEvent` | 含 userId/orderNo/orderId/金额 | ✅ 存在，字段齐全 | `aryn-common-core/.../entity/OrderPaySuccessEvent.java:21-41` |
| 事件类 `OrderCompleteEvent` | 含 userId/orderNo/orderId | ✅ 存在 | `.../entity/OrderCompleteEvent.java:23-36` |
| 事件类 `OrderRefundSuccessEvent` | 含 refundAmount/refundReason | ✅ 存在 | `.../entity/OrderRefundSuccessEvent.java:13-61` |
| `SecurityUtils.getUserId()` | 返回用户ID | ✅ 存在，返回 `String` | `aryn-common-security/.../util/SecurityUtils.java:58-64` |
| `@SaCheckPermission` | 管理端鉴权 | ✅ 项目 40+ 控制器在用 | `product-biz/.../admin/GoodsSpuController.java:41` |
| `Result<T>` 工具类 | 统一返回 | ✅ 存在，成功码=0 | `aryn-common-core/.../util/Result.java` |
| `ArynBusinessException` | 业务异常 | ✅ 存在 | `aryn-common-security/.../handler/ArynBusinessException.java` |
| Listener 写法惯例 | `@RocketMQMessageListener` + 常量引用 | ✅ 有规范范本 | `product-biz/.../listener/ProductPayEventListener.java:18-23` |
| 异步线程池 | 需新建 `notifyAsyncExecutor` | ✅ **已有全局** `hxAsyncExecutor` | `aryn-common-core/.../config/AsyncConfig.java:31-50` |
| 管理后台 `requestClient` | `#/api/request` | ✅ 路径正确 | `web-ele/src/api/request.ts:121` |

### 2.2 必须修正的约定不符 ❌（6 处）

| # | 方案写法 | 项目实际 | 影响 | 佐证 |
|---|----------|----------|------|------|
| 1 | 逻辑删除字段 `deleted` (Integer) | **`delFlag`** (String) | 全部 3 张表 + 实体类 | 50+ 实体均用 `delFlag`，如 `OrderInfo.java:100-103`、`SysUser.java:75-78` |
| 2 | `tenant_id` 为 `bigint`，Java 用 `Long` | **`tenantId` 为 `String`** | 全部 3 张表 + 实体类 + MQ 消费 | `OrderInfo.java:111`、事件类 `OrderPaySuccessEvent.java:35` 等 |
| 3 | `PageResult<T>` 分页封装 | **不存在**，项目用 MyBatis-Plus `Page<T>`/`IPage<T>` | 所有分页接口签名 + Service 实现 | `IPointsConfigService` 等均用 `IPage` |
| 4 | 实体主键 `id` 用 `IdType.ASSIGN_ID` (String) | 需核实，项目主键多用 `ASSIGN_ID` 但要确认 `@TableId` 写法 | 实体类 | 需参照 `OrderInfo` 主键注解 |
| 5 | C 端 WS 鉴权用 `StpUtil.getLoginIdByToken(token)` | 可行，但需确认 Sa-Token token 传递方式 | WS Handler | `SecurityUtils` 基于 `StpUtil`，可行 |
| 6 | UniApp `userStore.token` | **错误**，token 在 `useAuthStore` | WS 连接 + 所有需要 token 处 | `authStore.ts:42-53`，`userStore.ts:34-66` 无 token 字段 |

### 2.3 需额外建设 ⚠️（2 块）

#### A. WebSocket 基建 — 从零开始（最大工作量增量）

方案假设 WS 可直接用，但项目当前：
- **无任何 WebSocket 依赖**（pom 无 `spring-boot-starter-websocket`）
- **网关不支持 WS 路由**：`aryn-gateway` 是 Reactive WebFlux 网关，`SaTokenConfigure.java:40-70` 的 `SaReactorFilter` 拦截 `/**`，WS 升级握手会被拦截
- **多实例路由未实现**：方案代码里多实例 Pub/Sub 部分是注释，单实例可用，多实例需补 Redis Pub/Sub 广播

**需新增工作**：
1. notify-biz 引入 `spring-boot-starter-websocket`
2. 网关新增 WS 路由配置（`lb:ws://aryn-notify/ws/notify`）+ 白名单放行 `/ws/**`
3. 若部署多实例，实现 Redis Pub/Sub 跨节点推送（方案已留接口，需补全）
4. 单体模式（aryn-boot）下 WS 端口与 HTTP 共用，需验证 Sa-Token 过滤器对 WS 的放行

**替代方案**：若 C 端实时性要求不高，可一期先用「短轮询未读数」+ MQ 触发，WS 推到二期。可省 ~1.5d。

#### B. 「订单已发货」事件链路缺失

方案 `ORDER_SHIPPED` 模板 + 监听器假设存在发货 Topic，但：
- `RocketMqConstants` 中 **无发货相关 Topic**
- 发货动作是 `OrderInfoServiceImpl` 里的同步 DB 更新，**不发任何事件**
- 方案模板里用的 `${logisticsCompany}` / `${logisticsNo}` 字段在现有事件类中也不存在

**需新增工作**：
1. 在 `RocketMqConstants` 新增 `ORDER_SHIPPED_NOTIFY_TOPIC`
2. 在 `aryn-order-biz` 发货 Service 方法末尾 `rocketMQTemplate.syncSend` 发布 `OrderShippedEvent`（含物流公司、运单号）—— **这要改 aryn-order 模块**
3. 新建 `OrderShippedEvent` 事件类放 `aryn-common-core`

⚠️ 这违反了"notify 模块零侵入"的初衷，但无替代方案。需与 order 模块负责人协调。

---

## 三、事件类字段适配清单

方案模板里的变量，需逐一映射到真实事件字段：

| 模板编码 | 方案用变量 | 真实事件字段 | 处理 |
|----------|-----------|--------------|------|
| `ORDER_PAY_SUCCESS` | `${amount}` | `paymentPrice`（非 amount） | 改 Listener 里 params 的 key |
| `ORDER_PAY_SUCCESS` | `${orderId}` `${orderNo}` | 均存在 | ✅ |
| `ORDER_SHIPPED` | `${logisticsCompany}` `${logisticsNo}` | **无此事件** | 需新建事件 + Topic（见 2.3-B） |
| `ORDER_COMPLETE` | `${orderNo}` `${orderId}` | 均存在 | ✅ |
| `REFUND_SUCCESS` | `${amount}` | `refundAmount`（非 amount） | 改 key |
| `REFUND_SUCCESS` | `${orderId}` `${orderNo}` | 有 orderId，**无 orderNo** | 需 Listener 内查订单补 orderNo，或扩展事件类 |
| `REFUND_REJECT` | `${reason}` | `refuseReason`（非 reason） | 改 key |
| `ORDER_CANCEL` | 用 `OrderConsumerDTO`（非 Event） | 字段：orderId、tenantId | Listener 类型改为 `RocketMQListener<OrderConsumerDTO>` |

---

## 四、修正后的工时估算

| 步骤 | 方案原估 | 修正估 | 说明 |
|:---:|:---:|:---:|------|
| 建库建表 + 预置模板 | 0.5d | 0.5d | 改 delFlag/tenantId 类型 |
| aryn-notify-api 模块 | 0.5d | 0.5d | |
| aryn-notify-biz 骨架 + pom | 0.5d | 0.5d | |
| Service 层实现 | 1.5d | 1.5d | 改用 `Page<T>`，复用 `hxAsyncExecutor` |
| RocketMQ 监听器（5个） | 1d | 1.5d | 字段映射修正 + 发货事件新建 |
| WebSocket 推送 | 1d | **2.5d** | 含网关 WS 路由 + 多实例 Pub/Sub |
| 管理端 Controller | 1d | 1d | |
| C 端 Controller | 0.5d | 0.5d | |
| 管理后台前端（3页） | 2d | 1.5d | 用 `createCrudApi` 工厂，比手写快 |
| C 端 UniApp | 2d | 2d | 修 `useAuthStore` + alova API 写法 |
| aryn-boot 适配 | 0.5d | 0.5d | |
| aryn-order 发货事件改造 | — | **0.5d** | 新增项 |
| 联调测试 | 1d | 1.5d | WS 链路联调复杂 |
| **合计** | **~12d** | **~14.5d ≈ 2 人周** | |

---

## 五、建议实施路径

### 阶段一：基础站内信（无 WS，可独立交付）— ~1 人周
1. 建表（修正字段类型）+ 预置模板
2. aryn-notify-api / -biz 模块搭建
3. Service 层（发送/查询/已读/删除）+ Redis 未读计数
4. 4 个 RocketMQ 监听器（支付/完成/退款/取消）—— 字段映射修正
5. C 端 + 管理端 Controller
6. 管理后台前端 3 页
7. C 端 UniApp 消息列表页 + 轮询未读数 + TabBar Badge

### 阶段二：实时推送 + 发货通知 — ~1 人周
8. aryn-order 发货事件改造（新增 Topic + Event + 发布点）
9. WebSocket 全链路（网关路由 + 业务 Handler + 多实例 Pub/Sub）
10. UniApp WS 客户端 + 心跳重连
11. 全量联调

**建议先做阶段一**，理由：① 风险最低、价值已可交付；② WS 涉及网关改造，需评估运维影响；③ 发货事件要动 order 模块，需协调。

---

## 六、待确认决策点

1. **WebSocket 是否一期必做？** 若可接受 30s 轮询，一期省 ~2.5d。
2. **发货通知是否本期做？** 若做，需改动 aryn-order-biz，确认是否允许跨模块改动。
3. **群发目标「指定会员等级/标签」**：方案 `target_type=3/4` 需要查 user 模块的等级/标签数据，跨模块调用走 Dubbo。需确认 `aryn-user-api` 是否已暴露会员等级查询接口。
4. **多实例部署**：当前是否多实例？若单实例，WS 多节点路由可后置。

---

**报告结束**。确认上述决策点后，可按阶段一生成全部后端 + 前端代码。
