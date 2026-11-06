# P2-A 会员/订单上下文契约（订单域远程接口 + message 域聚合）

> 版本：v1.1（2026-11-06；v1 订单域契约 + 补 message 域最终接口/实现记录）
> 用途：锁定客服工作台「会员卡 + 最近订单」所需订单域 Dubbo 远程契约，以及 message 域聚合接口的设计输入；供后端 task-9/task-10 与前端 task-12 使用。
> 关联：[P2-A-接口契约.md](P2-A-接口契约.md)（会话/消息主契约）、[方案.md](方案.md) §5.2（上下文面板优先级）
> 本需求定性：**无数据库变更**（不新增表/列/索引/菜单/权限；订单域只读现有 `order_info`）。

---

## 1. 背景与目标

客服工作台右侧「上下文面板」的会员/订单卡片需要跨域数据：

- **会员**：id / 昵称 / 头像——来自 message 域会话参与者快照（`ConversationVO.customerName/customerAvatar`），**不新增用户域接口**；
- **最近订单**：会员在本租户的最近 N 条订单——订单域新增只读 Dubbo 远程接口（本文）；
- message 域新增聚合接口 `GET /message/staff/conversation/{id}/context` 一次返回上述数据（设计输入见 §4）。

非目标（本批次不做）：订单明细/物流轨迹/售后单、跨域写操作、权限点、订单搜索/分页。

---

## 2. 订单域远程契约（`aryn-order-api`）

### 2.1 接口

```java
package com.aryn.cloud.order.api.remote;

public interface RemoteOrderContextService {
	/**
	 * 查询会员最近订单（按下单时间倒序）。
	 * @param tenantId 租户 ID（必填，空返回空列表）
	 * @param customerId 会员 ID（必填，空返回空列表）
	 * @param limit 返回条数：≤0 归一化为 3，>10 截断为 10（1..10）
	 * @return 订单上下文列表；无数据/查询异常时返回空列表（降级，不抛错）
	 */
	List<OrderContextVO> queryRecentOrders(String tenantId, String customerId, int limit);
}
```

### 2.2 返回字段（`order.api.vo.OrderContextVO`，`Serializable`）

| 字段 | 类型 | 可空 | 语义 |
|---|---|---|---|
| `orderId` | string | 否 | 订单 ID（`order_info.id`） |
| `orderNo` | string | 是 | 订单单号 |
| `status` | string | 是 | 订单状态码：`1` 待付款 / `2` 待发货 / `3` 待收货 / `4` 已完成 / `5` 退款中 / `11` 已取消 |
| `statusName` | string | 是 | 状态中文名（`OrderStatusEnum` 映射）；未知状态码为 null，前端回退展示 `status` |
| `payStatus` | string | 是 | 支付状态码：`0` 未支付 / `1` 已支付 |
| `payAmount` | decimal | 是 | 支付金额：优先 `actualPayPrice`（实收），为空回退 `paymentPrice`（应付）；均空为 null |
| `createTime` | datetime（`yyyy-MM-dd HH:mm:ss`） | 是 | 下单时间 |
| `productSummary` | string | 是 | 商品摘要；**本批次固定 null**（见 §2.4），前端可用订单号/金额/状态展示 |
| `deliveryWay` | string | 是 | 配送方式码：`1` 普通快递 / `2` 上门自提（前端自行映射文案） |

### 2.3 查询规则（实现口径）

1. 条件：`tenant_id = tenantId AND user_id = customerId AND del_flag = '0'`（只读、会员本人域数据，无权限项）；
2. 排序：`create_time DESC`（最近下单优先）；
3. 条数：`limit` 归一化 `1..10`，`≤0` → 默认 `3`，`>10` → `10`；实现用 `LIMIT n` 且不查总数；
4. 租户上下文：实现内把线程租户快照替换为入参 `tenantId` 并在 finally 还原（沿用 order 域既有 Dubbo 实现约定），保证 MyBatis 租户拦截器口径与入参一致；
5. 异常/空数据：返回 `List.of()` 并发 `warn` 日志，不向调用方抛错（fail-soft）。

### 2.4 商品摘要降级说明

订单明细（`order_item`）查询会引入额外跨表查询与并行会话中的 Mapper 限制；本批次 `productSummary` **固定返回 null**，前端用订单号/金额/状态展示即可。后续若产品需要「首件商品名+件数」，在订单域单独立项补齐（复用 OrderItemMapper BaseMapper 批量查询，不改既有 XML）。

### 2.5 异常与降级

| 场景 | 表现 |
|---|---|
| `tenantId`/`customerId` 为空 | 返回 `[]`，不查库 |
| 无订单 | 返回 `[]` |
| DB/查询异常 | 返回 `[]` + `warn` 日志（调用方接口仍 200） |
| 订单域服务不可用/超时 | Dubbo 异常由调用方（message 域）捕获降级为 `[]` |

---

## 3. message 域聚合接口设计输入（task-10）

`GET /message/staff/conversation/{id}/context`（权限与会话详情一致：会话参与者/主管，沿用 detail 鉴权）：

```json
{
  "member": { "id": "会员ID", "name": "会员昵称", "avatar": "头像URL" },
  "orders": [ { "orderId": "...", "orderNo": "...", "status": "2", "statusName": "待发货", "payStatus": "1", "payAmount": 88.80, "createTime": "2026-11-06 10:30:00", "productSummary": null, "deliveryWay": "1" } ],
  "conversation": { "id": "...", "status": "ACTIVE", "queueCode": "DEFAULT", "createTime": "...", "waitingSince": null },
  "contextPayload": "原会话 contextPayload（可选）"
}
```

规则：

1. `member` 来自会话参与者快照（`customerName/customerAvatar`），缺失回退 `customerId`/null，不新增用户域接口；
2. `orders` 调 `RemoteOrderContextService.queryRecentOrders(tenantId, customerId, 3)`，仅 `CUSTOMER_SERVICE` 会话查询；`STAFF_DIRECT` 不查、返回 `[]`；
3. 订单域异常/超时 → `orders=[]` + `warn`，接口仍 200（不阻塞上下文面板）；
4. 会话不存在/越权 → 沿用现有 `ArynBusinessException` 中文错误（与 `GET /staff/conversation/{id}` 一致）；
5. 无 DB 变更；`aryn-message-biz` 新增 `aryn-order-api` 依赖（只依赖 api，不 import order-biz），`@DubboReference` 双模式可用（cloud Dubbo / boot 同 JVM）。

---

## 4. 双模式与部署

- cloud：message 服务 `@DubboReference RemoteOrderContextService` 调 order 服务；租户经 `ArynDubboRequestFilter` attachment 透传（order 实现还会用显式入参兜底）。
- boot：order-biz 与 message-biz 同一 JVM，`@DubboService`/`@DubboReference` injvm 调用；无需新增配置。
- 不新增网关路由/Nacos 配置；前端路径以 `/message` 开头，经 `requestClient` 的 `parseOpenBoot` + `rewriteBootUrl`（cloud 原样 / boot 改 `/boot/...`）。

---

## 5. 无 DB 变更声明

- 不新增/修改表、列、索引、菜单、权限、种子数据；不新增 `db/boot/**` 与 `db/cloud/**` 增量 SQL；
- 仅新增 3 个 Java 文件（order-api 接口 + VO、order-biz Dubbo 实现）与 1 个测试文件；message 域 task-10 新增聚合接口及 `aryn-order-api` pom 依赖。

---

## 6. 测试清单

| 层 | 测试 |
|---|---|
| 订单域（已实现） | `RemoteOrderContextServiceImplTest`：过滤条件（tenant_id/user_id/del_flag）、`ORDER BY create_time DESC`、limit 归一化（0/-5→3、5→5、11/100→10）、字段映射（中文状态名/实收优先/配送方式）、未知状态 statusName=null、空/异常降级为空列表、租户上下文还原、空入参不查库 |
| message 域（task-10） | message 域新增测试：会话参与者/主管鉴权、会员快照回退、CUSTOMER_SERVICE 查订单/STAFF_DIRECT 不查、订单异常降级空数组、无会话/越权 |
| 手工 | 客服工作台打开会话 → 信息面板会员卡/最近 3 单正确；无订单/订单域不可用时面板不报错；双模式路径与 Dubbo 调用可用 |

---

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| v1 | 2026-11-06 | 初稿：订单域远程契约 + message 聚合接口设计输入；确认无 DB 变更 | be-p2a |
| v1.1 | 2026-11-06 | 补 message 域最终接口契约（§8）与实现/验证记录（§9）；order 域接口与 message 聚合接口均已落盘 | be-p2a |
---

## 8. message 域最终接口契约（task-10 已实现，2026-11-06）

### 8.1 接口

| 项 | 值 |
|---|---|
| 方法/路径 | `GET /message/staff/conversation/{id}/context` |
| Controller | `StaffConversationController#context`（@RequestMapping("/staff/conversation")，不新增权限注解） |
| 权限 | 与会话详情同口径：会话 ACTIVE 参与者；**非参与者但持 `message:service:supervisor` 时走只读降级**（`selectForTenant`，不要求参与者） |
| 请求参数 | 路径参数 `id`（会话 ID） |
| 响应 | `Result<ConversationContextVO>` |
| 双模式 | 前端 `GET /message/staff/conversation/{id}/context`：cloud 原样；boot → `/boot/staff/conversation/{id}/context`（requestClient 统一改写） |

### 8.2 响应结构（最终字段）

```json
{
  "member": { "id": "会员ID", "name": "会员昵称或会员ID", "avatar": "头像URL|null" },
  "orders": [
    {
      "orderId": "19580...", "orderNo": "NO20261106001", "status": "2", "statusName": "待发货",
      "payStatus": "1", "payAmount": 88.80, "createTime": "2026-11-06 10:30:00",
      "productSummary": null, "deliveryWay": "1"
    }
  ],
  "conversation": { "id": "...", "conversationType": "CUSTOMER_SERVICE", "status": "ACTIVE", "queueCode": "DEFAULT", "createTime": "...", "waitingSince": null, "contextPayload": "..." },
  "contextPayload": "{\"orderId\":\"19582...\"}"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `member` | object/null | 仅 `CUSTOMER_SERVICE` 会话返回；`id`=会话 `customerId`，`name`=参与者昵称快照（缺失回退 `id`），`avatar` 可空；`STAFF_DIRECT` 为 null |
| `orders` | array | 最近订单，最多 3 条，订单域 orderByDesc(create_time)；**`STAFF_DIRECT` / 无会员 / 订单域降级时为空数组**（不返回 null） |
| `orders[].*` | - | 与订单域 `OrderContextVO` 一一对应（message-api 用 `OrderContextItemVO` 镜像，biz 层映射）；`productSummary` 本批次为 null |
| `conversation` | object | 复用既有 `ConversationVO`（含 `createTime/waitingSince/contextPayload` 等；主管降级视角不计算未读字段） |
| `contextPayload` | string/null | 与 `conversation.contextPayload` 同值，方便前端直接取用 |

### 8.3 规则与降级

1. 会话读取：先按参与者鉴权（`ConversationService#get`）；捕获「会话不存在或无权访问」时，若调用者持主管权限则用 `MessageConversationMapper#selectForTenant`（新增按 ID 只读查询，列与主管列表同构）降级；否则原样抛 `ArynBusinessException`（`code=1` 中文）；
2. 订单查询：仅 `CUSTOMER_SERVICE` 且有 `customerId` 时调 `@DubboReference RemoteOrderContextService.queryRecentOrders(tenantId, customerId, 3)`；`STAFF_DIRECT` 不调用；
3. 订单域异常/超时：catch `RuntimeException` → `warn` 日志 + `orders=[]`，接口仍 200，会员卡/会话信息不受影响；
4. 订单域返回 null：按空数组返回；
5. 无 DB 变更；message-biz 仅新增 `aryn-order-api` 依赖（不 import order-biz，双模式 Dubbo injvm/远程均可用）。

### 8.4 错误与降级

| 场景 | 表现 |
|---|---|
| 非参与者且非主管 | HTTP 200 + `code=1`「会话不存在或无权访问」（与会话详情一致） |
| 主管降级后会话仍不存在 | 同上 |
| 订单域不可用/超时 | HTTP 200 + `orders=[]`（warn），其余正常 |
| 未登录/会话失效 | 401（既有链路） |

---

## 9. 实现与验证记录（2026-11-06）

- 订单域（task-9）：新增 3 个 Java 文件 + 1 个测试（详见 §2）；`RemoteOrderContextServiceImplTest` 7/7 通过；`mvn -q -pl aryn-order/aryn-order-api,aryn-order/aryn-order-biz -am -DskipTests compile` 通过。
- message 域（task-10）：新增 `ConversationContextVO/MemberContextVO/OrderContextItemVO`（message-api）、`ConversationContextService/Impl`（message-biz）、`MessageConversationMapper#selectForTenant` + XML、Controller `GET /{id}/context`；message-biz pom 增加 `aryn-order-api`；新增测试 `ConversationContextServiceTest` 8/8 通过；message 域全量测试通过；`aryn-boot -am` 聚合编译通过。
- 前端（task-12）按本节字段联调；路径经 requestClient 改写，禁止硬编码 `/boot`。
