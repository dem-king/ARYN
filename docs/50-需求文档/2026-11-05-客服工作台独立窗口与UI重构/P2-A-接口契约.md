# P2-A 接口契约（批次一：列表筛选/waitingSince/roster/队列统计；批次二：引用/撤回/图片发送）

> 版本：**v2**（2026-11-06；批次一 v1 已实现并验收，批次二为本次追加）
> 作者：be-p2a（客服消息域）
> 状态：批次一**已实现并验收**；批次二**契约锁定，后端按此实现**。前端 task-4 以 §0–§13（v1）为准，批次二前端以 §14–§21 为准；实现如与本文不一致须先改本文并通知对方。
> 关联文档：[方案.md](方案.md) §6.2、[续作交接.md](续作交接.md) §2.2/§6、[实施记录.md](实施记录.md)
> 本需求定性：**纯接口能力增强，全程无数据库变更**（批次一、批次二均不新增/修改表、列、索引、菜单、种子数据、权限点）。
> 文档结构：**§0–§13 为批次一（v1，已实现，内容保持不变）；§14–§21 为批次二（v2 新增）**。

---

## 0. 能力总览与结论

| # | 能力 | 接口 | 权限 | 数据库变更 |
|---|---|---|---|---|
| A | 会话列表增强（keyword / statuses / assignedStaffId / queueCode / unreadOnly） | `GET /message/staff/conversation`<br>`GET /message/admin/conversation/page` | staff：登录即可（沿用现状）<br>admin：`message:service:supervisor` | 无 |
| B | 会话视图增加 `createTime` / `waitingSince` | 随 A 的列表响应及所有返回 `ConversationVO` 的读接口生效 | 随各接口既有权限 | 无（复用 `create_time` 列 + SQL 别名） |
| C | 在线坐席名单（转交/强制分配选人） | `GET /message/staff/agent/roster` | `message:service:agent` **OR** `message:service:supervisor` | 无（`message_agent` + upms 人员快照 + Redis presence） |
| D | 队列统计（等待数/最久等待/在线坐席/可用容量） | `GET /message/staff/conversation/queue-stats` | `message:service:agent` **OR** `message:service:supervisor` | 无 |

四项能力全部基于现有表（`message_conversation` / `message_participant` / `message_agent`）、现有 Redis presence 租约（`message:agent:presence:{tenantId}:{staffId}`，TTL 90s）与现有 upms Dubbo 接口（`RemoteMessageStaffService.queryRecipients`）实现，**不新增任何 DDL/DML**。

---

## 1. 通用约定

### 1.1 响应包装与错误码

所有接口返回 `Result<T>`：`{ "code": 0, "msg": null, "data": ... }`。

| 场景 | HTTP | body.code | 前端处理 |
|---|---|---|---|
| 成功 | 200 | 0 | 取 `data` |
| 业务参数错误 / 业务拒绝（`ArynBusinessException`） | 200 | 1 | 展示 `msg`；列表筛选场景建议回滚筛选条件并提示 |
| 未登录 / 会话失效 | 401 | 401 | 走既有会话失效链路（独立窗口弹窗原地重登） |
| 权限不足（缺少权限码） | 403 | 403 | 提示「无权限」；按钮/入口按权限隐藏 |
| 未预期异常 | 500 | 1 | 通用错误提示 + 重试 |

> 本契约里的「参数非法」统一指 `ArynBusinessException` → HTTP 200 + `code=1` + 中文 `msg`（现有全局异常处理约定，**不是** HTTP 400）。

### 1.2 时间与数字

- `LocalDateTime` 全局序列化为 `yyyy-MM-dd HH:mm:ss`（无时区、服务器本地时区），如 `"2026-11-06 10:40:00"`。
- 计数类字段为整数（`long`/`int` 序列化为 JSON number），未读/等待类计数不会为负。
- `enabled` / `autoAccept` 沿用消息域既有 char(1) 字符串约定：`"0"`=否、`"1"`=是（与 `AgentVO` / 前端 `AgentInfo` 一致，不用 boolean）。

### 1.3 双模式总述

- 前端 API 路径首段必须是微服务域 `message`，即 `/message/...`；统一经 `requestClient` 内部 `parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)` + `rewriteBootUrl(url, openBoot)` 改写，**禁止**在页面/组件里硬编码 `/boot` 或直接判断 `VITE_OPEN_BOOT`。
- cloud 模式：路径原样 `/message/staff/...`（网关路由到 aryn-message 服务）。
- boot 模式：`rewriteBootUrl` 去掉首段 `message` 并加 `/boot` 前缀（`aryn-boot` 的 `server.servlet.context-path: /boot`），即 `/boot/staff/...`。
- 后端 Controller 沿用现有前缀约定：staff 接口在 `@RequestMapping("/staff/...")`、主管接口在 `@RequestMapping("/admin/...")`，**不得**在 Controller 写 `/message` 或 `/boot`；本批次不新增网关路由、不新增 Nacos 配置。
- 4 个接口的双模式路径对照：

| 接口 | 前端调用 path（唯一写法） | cloud 实际 URL | boot 实际 URL |
|---|---|---|---|
| A-staff | `GET /message/staff/conversation` | `/message/staff/conversation` | `/boot/staff/conversation` |
| A-admin | `GET /message/admin/conversation/page` | `/message/admin/conversation/page` | `/boot/admin/conversation/page` |
| C | `GET /message/staff/agent/roster` | `/message/staff/agent/roster` | `/boot/staff/agent/roster` |
| D | `GET /message/staff/conversation/queue-stats` | `/message/staff/conversation/queue-stats` | `/boot/staff/conversation/queue-stats` |

### 1.4 租户与身份

- 租户一律从登录态（`ArynUser.tenantId`）取，服务端显式传参并在 SQL 中带 `tenant_id = #{tenantId}`；跨租户数据不可见，前端不传租户参数。
- 本批次 4 个接口均只服务管理端（TOB）工作人员，不涉及 C 端（TOC）接口。

---

## 2. 能力 A：会话列表增强

### 2.1 接口

| 项 | staff | admin |
|---|---|---|
| 方法/路径 | `GET /message/staff/conversation` | `GET /message/admin/conversation/page` |
| Controller | `StaffConversationController#inbox`（不改签名） | `ConversationAdminController#page`（不改签名） |
| 权限 | 无 `@SaCheckPermission`（沿用现状：登录的 TOB 员工即可） | `@SaCheckPermission("message:service:supervisor")` |
| 数据范围 | 调用者本人作为 ACTIVE 参与者的会话 | 本租户**全部**会话（不要求参与者） |
| 响应 | `Result<ConversationInboxPageVO>`（与现状同构，`ConversationVO` 增 2 字段见能力 B） | 同左（复用同一 PageVO） |

### 2.2 请求参数（`ConversationInboxQuery`，query string）

| 参数 | 类型 | 必填 | 默认 | 边界 / 语义 |
|---|---|---|---|---|
| `status` | string | 否 | 不过滤 | 页签遗留语义：`OPEN`=WAITING/ASSIGNED/ACTIVE，`CLOSED`=已关闭；大小写不敏感（服务端 trim+大写）；非法值 → `code=1`「status 仅支持 OPEN 或 CLOSED」 |
| `statuses` | string[]（逗号分隔或重复参数） | 否 | 不过滤 | **精确状态集合**：仅 `WAITING` / `ASSIGNED` / `ACTIVE` / `CLOSED`；服务端 trim、大写、去空、去重；任一非法值 → `code=1`「statuses 仅支持 WAITING/ASSIGNED/ACTIVE/CLOSED」；**非空时覆盖 `status`**（见 2.3） |
| `keyword` | string | 否 | 不过滤 | trim 后为空则忽略；长度 ≤64（服务端按字符计，超长 → `code=1`「keyword 长度不能超过 64」）；对 3 个字段做**包含匹配**（见 2.4） |
| `assignedStaffId` | string | 否 | 不过滤 | trim 后为空则忽略；精确匹配 `c.assigned_staff_id`；不提供「未分配」特殊值（待领取请用 `statuses=WAITING` 或 `/waiting`） |
| `queueCode` | string | 否 | 不过滤 | trim 后为空则忽略；精确匹配 `c.queue_code`；注意与 `GET /staff/conversation/waiting` 的默认 `DEFAULT` **不同**，本接口缺省**不按队列过滤**（多队列统计是能力 D 的职责） |
| `unreadOnly` | boolean | 否 | `false` | `true` 时只保留有未读的会话；staff 与 admin 的「未读」口径不同（见 2.5） |
| `cursor` | string | 否 | 首页 | 服务端生成的不透明游标，前端原样回传；**不允许前端构造**；翻页时必须同时回传**同一组筛选参数** |
| `limit` | integer | 否 | 20 | 有效范围 1–100；`<=0` 归一化为 20，`>100` 截断为 100；服务端查 `limit+1` 判断 `hasMore` |

参数示例：

```http
GET /message/staff/conversation?status=OPEN&keyword=%E7%8E%8B&limit=20
GET /message/admin/conversation/page?statuses=WAITING,ASSIGNED&queueCode=DEFAULT&assignedStaffId=1958...&unreadOnly=true&limit=50
```

### 2.3 与既有 `status` / `cursor` / `limit` 的优先级（必须按此实现）

1. **statuses 优先于 status**：`statuses` 归一化后非空 → 只按 `statuses` 过滤，`status` 被忽略（**不报错、不校验**，兼容前端旧页签参数残留）。
2. `statuses` 缺省 / 归一化后为空（如 `statuses=`、仅空白）→ 视为未传，回退按 `status` 单值过滤（既有语义不变）。
3. `status` 与 `statuses` 都缺省 → 不过滤状态（含 CLOSED）。
4. **unreadOnly 与状态条件是 AND 叠加**，且 `unreadOnly=true` 时服务端**强制排除 CLOSED**（即使 `statuses` 包含 `CLOSED` 也返回空集）：「未读会话」按未关闭会话口径，与 `attention` 接口一致。
5. `keyword` / `assignedStaffId` / `queueCode` / `unreadOnly` 之间以及与状态条件之间均为 AND。
6. 所有筛选条件与游标条件同处 WHERE，**筛选在排序/游标之前生效**；排序键与既有实现完全一致：`COALESCE(last_message_time, create_time) DESC, id DESC`。
7. `cursor` 只编码「上一页最后一条的最近活跃时间 + 会话 ID」，**不与筛选条件绑定**；前端翻页时若不回传相同筛选参数，服务端不报错但结果可能漏页/重复——这是前端责任，契约中明确。
8. `limit` 语义不变：一页条数上限，`hasMore` 为真时 `records.size() == limit` 且 `nextCursor` 非空。
9. 既有 `GET /staff/conversation/waiting`（待领取池，按 `create_time ASC` 排队最久优先）**不受本批次影响**，不叠加新筛选参数。

### 2.4 SQL LIKE 字段范围与转义

`keyword` 对以下 3 个字段做 `OR` 包含匹配（大小写不敏感依赖库排序规则 `utf8mb4_general_ci`）：

| 字段 | 来源 | 说明 |
|---|---|---|
| `c.customer_id` | `message_conversation.customer_id` | 会员 ID（前端可用「会员ID」搜索；无昵称快照的历史会话也能命中） |
| `cp.participant_name` | `message_participant`（MALL_USER，`cp` 别名） | 会员昵称快照（含历史会话回填的快照）；会员改名后需快照刷新才能搜到新昵称 |
| `c.last_message_summary` | `message_conversation.last_message_summary` | 最后一条消息摘要 |

SQL 形态（实现必须与之一致）：

```sql
AND (c.customer_id LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
     OR cp.participant_name LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
     OR c.last_message_summary LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!')
```

转义规则（沿用 `aryn-product` 既有 `escapeLikeKeyword` 约定）：

- 转义符固定为 `!`（不用反斜杠，规避 MySQL 字符串转义方言差异）。
- 服务层把用户输入中的 `!`、`%`、`_` 各自前置一个 `!` 后传给 SQL，SQL 侧固定 `ESCAPE '!'`，因此 `%`/`_` 按字面量匹配，用户无法用通配符绕过筛选语义或触发全表 LIKE 扫描。
- 示例：输入 `50%_off!` → 服务层传 `50!%!_off!!`。
- 性能说明：前导通配符 `%...%` 无法使用 B+Tree 索引，但查询始终带 `tenant_id` 等条件且 `limit ≤ 100`，按既有会话量级可接受；若后续会话量级超预期，需单独立项（全文索引/ES，属 DB 变更，不在本批次）。

### 2.5 staff / admin 权限与语义差异

| 维度 | `GET /message/staff/conversation` | `GET /message/admin/conversation/page` |
|---|---|---|
| 权限码 | 无（登录即可，与现状一致） | `message:service:supervisor`（403 时 `code=403`） |
| 数据范围 | 仅本人作为 ACTIVE 参与者的会话（`INNER JOIN message_participant p`） | 本租户全部会话（不要求参与者，含已关闭） |
| 待领取共享池 | **不包含**（无 `SYS_USER` 参与者行，需走 `/waiting`） | 包含（按状态筛 `WAITING` 可看作待领取） |
| `unreadOnly` 口径 | 调用者**本人**未读：`c.last_seq > p.last_read_seq`（`p` 为调用者参与者行，与 `attention.unreadConversations` 一致） | 以「**负责客服未读**」代理：存在 `SYS_USER` ACTIVE 参与者且 `c.last_seq > up.last_read_seq`（即会员发了消息但负责客服未读）；无 `SYS_USER` 参与者的待领取会话**不计入**（需要时用 `statuses=WAITING`） |
| `status`/`statuses` 支持 | 支持 | 支持 |
| `keyword`/`assignedStaffId`/`queueCode` | 支持（结果仍受本人范围约束） | 支持（可筛任意坐席/队列） |
| 未读相关返回字段 | `lastReadSeq`/`unreadCount`/`peerLastReadSeq` 有值 | 保持现状：`lastReadSeq`/`unreadCount`/`peerLastReadSeq` 可能为 null（主管视图不计算未读，本批次不改） |
| `createTime`/`waitingSince` | 返回 | 返回 |

### 2.6 响应 JSON 示例

```json
{
  "code": 0,
  "msg": null,
  "data": {
    "records": [
      {
        "id": "1958300000000000001",
        "conversationType": "CUSTOMER_SERVICE",
        "queueCode": "DEFAULT",
        "customerId": "1958000000000000123",
        "assignedStaffId": null,
        "status": "WAITING",
        "lastSeq": 0,
        "lastReadSeq": 0,
        "unreadCount": 0,
        "lastMessageSummary": null,
        "lastMessageTime": null,
        "createTime": "2026-11-06 10:40:00",
        "waitingSince": "2026-11-06 10:40:00",
        "closedTime": null,
        "reopenDeadline": null,
        "contextPayload": null,
        "customerName": "王小雅",
        "customerAvatar": "https://cdn.example.com/avatar/123.png",
        "assignedStaffName": null,
        "assignedStaffAvatar": null,
        "peerLastReadSeq": 0,
        "lastActiveTime": "2026-11-06 10:40:00"
      },
      {
        "id": "1958300000000000002",
        "conversationType": "CUSTOMER_SERVICE",
        "queueCode": "DEFAULT",
        "customerId": "1958000000000000124",
        "assignedStaffId": "1958100000000000011",
        "status": "ACTIVE",
        "lastSeq": 37,
        "lastReadSeq": 30,
        "unreadCount": 7,
        "lastMessageSummary": "麻烦看下物流",
        "lastMessageTime": "2026-11-06 11:48:12",
        "createTime": "2026-11-05 09:12:00",
        "waitingSince": null,
        "closedTime": null,
        "reopenDeadline": null,
        "contextPayload": "{\"orderId\":\"19582...\"}",
        "customerName": "李四",
        "customerAvatar": null,
        "assignedStaffName": "客服小美",
        "assignedStaffAvatar": "https://cdn.example.com/avatar/11.png",
        "peerLastReadSeq": 35,
        "lastActiveTime": "2026-11-06 11:48:12"
      }
    ],
    "nextCursor": "MTc2MjM5Mjk2MDAwMHwxOTU4MzAwMDAwMDAwMDAwMDAy",
    "hasMore": true
  }
}
```

### 2.7 字段语义（返回结构 `ConversationInboxPageVO` / `ConversationVO`）

- `records`：当前页会话；元素字段见能力 B 与现有 `ConversationVO`（`id/status/queueCode/customerId/.../lastActiveTime` 语义不变）。
- `nextCursor`：`hasMore=true` 时非空；下一页原样回传给 `cursor`。
- `hasMore`：是否还有下一页。
- 新增字段 `createTime` / `waitingSince` 见 [§3](#3-能力-bconversationvo-新增-createtime--waitingsince)。
- `lastActiveTime`：仍只用于服务端游标编码，前端无需消费（保留返回是为了排障）。

### 2.8 错误与降级

| 场景 | 表现 | 前端建议 |
|---|---|---|
| `status` / `statuses` / `keyword` 非法 | HTTP 200 + `code=1` + 中文 msg | 提示并回滚筛选；不应静默 |
| `cursor` 被篡改/格式错误 | `code=1`「游标参数不合法」 | 重置为首页重新拉取 |
| 无权限（仅 admin） | HTTP 403 + `code=403` | 入口按 `message:service:supervisor` 隐藏 |
| DB 异常 | HTTP 500 + `code=1` | 列表错误态 + 重试（P1 已有错误态） |
| 会员昵称快照缺失 | 不报错；`customerName` 为 null，`keyword` 仍可通过 `customerId` 命中 | 前端回退展示 `customerId`（既有逻辑） |
| 会话快照补齐服务（`CustomerSnapshotService`）异常 | 维持现状实现（批量补齐 best-effort），不影响筛选结果 | 无感 |

---

## 3. 能力 B：`ConversationVO` 新增 `createTime` / `waitingSince`

### 3.1 字段

| 字段 | 类型 | 可空 | 语义 |
|---|---|---|---|
| `createTime` | string（`yyyy-MM-dd HH:mm:ss`） | 理论上非空（列为 `datetime DEFAULT NULL`，历史数据可能 null） | 会话创建时间，直接取 `message_conversation.create_time` |
| `waitingSince` | string（`yyyy-MM-dd HH:mm:ss`） | **是** | `status = 'WAITING'` 时 = 该会话进入等待的时间（当前实现 = `create_time`）；**非 WAITING（ASSIGNED/ACTIVE/CLOSED）时为 `null`** |

前端等待时长 = `now - waitingSince`（仅在 `status=WAITING` 且 `waitingSince != null` 时展示）。前端不得自行用 `lastMessageTime` 推算等待时长（无消息的新会话没有 `lastMessageTime`）。

### 3.2 Mapper 实现方案（无 DB 变更，纯 SQL 别名）

- `conversationViewColumns` 片段新增两列：

  ```sql
  c.create_time,
  CASE WHEN c.status = 'WAITING' THEN c.create_time ELSE NULL END AS waiting_since,
  ```

- 该片段被 **3 个** select 复用，自动生效：`selectForParticipant`（会话详情）、`selectInbox`（本人列表）、`selectUnreadForParticipant`（铃铛提醒明细）。批次二 task-10 又新增 `selectForTenant`（按 ID 只读查询，主管非参与者降级）同样复用查询列，故批次二后该列定义共 **4 处**文本使用（含 `selectTenantInbox` 与 `selectWaitingPool` 两处显式列清单）。
- 另有 **2 个**返回 `ConversationVO` 的 select 使用显式列清单，必须**同步补列**（否则字段为 null，契约不一致）：`selectTenantInbox`（主管列表）、`selectWaitingPool`（待领取池/`attention` 明细）。
- `selectWaitingForTenant` 返回实体 `MessageConversation`，不受影响。
- 结论：批次一后 **5 个**、批次二（task-10 新增 `selectForTenant`）后 **6 个**返回 `ConversationVO` 的查询全部返回这两个字段；排序键 `last_active_time = COALESCE(last_message_time, create_time)` 与既有游标协议不变（`waitingSince` 不参与排序）。
- 映射依赖 MyBatis-Plus 默认 `mapUnderscoreToCamelCase`（现有 `last_message_summary → lastMessageSummary` 同机制），无需改配置。

### 3.3 兼容性与边界

- 纯新增可空字段，旧前端忽略即可；不改任何既有字段类型/含义；不改分页排序与游标。
- 无消息的新会话：`lastMessageTime=null`、`lastActiveTime=createTime`、`waitingSince=createTime`（WAITING）。
- **已知限制（沿用既有行为，本批次不修）**：关闭后在重开期内「重开」的会话（CLOSED→WAITING）`create_time` 仍是首次创建时间，`waitingSince` 因此偏大，与现有待领取池 `ORDER BY create_time ASC` 的排序口径一致。若产品要求「重开后的等待时间」，需新增 `waiting_start_time` 列并回填，属 P2-B（DB 变更）单独立项。
- 已关闭历史会话：`waitingSince=null`，`createTime` 有值（可展示创建时间）。

---

## 4. 能力 C：在线坐席名单 `GET /message/staff/agent/roster`

### 4.1 接口

| 项 | 值 |
|---|---|
| 方法/路径 | `GET /message/staff/agent/roster` |
| Controller | `AgentController#roster`（新增方法） |
| 权限 | `@SaCheckPermission(value = { "message:service:agent", "message:service:supervisor" }, mode = SaMode.OR)` |
| 请求参数 | 无 |
| 响应 | `Result<List<AgentRosterVO>>`（全量、不分页） |
| 用途 | 工作台转交 / 强制分配 / 发起会话选人时展示「在线 + 负载 + 是否可接」；供前端把可用坐席置顶 |

### 4.2 组装规则（服务端实现口径）

1. 读 `message_agent`（本租户 `del_flag='0'`，含 `enabled='0'` 的已禁用坐席；`selectAllAgents` 现有查询），按 `staffId` 去重（表上有 `uk_message_agent_staff` 唯一键，天然不重复）。
2. 一次性批量读 presence：Redis **MGET** `message:agent:presence:{tenantId}:{staffId}`（与现有 `filterOnline` 同 key/TTL）；
   - 值为 `ONLINE`/`BUSY`/`PAUSED` → 对应状态；键不存在 → `OFFLINE`；值无法解析 → `OFFLINE`；
   - Redis 异常 → **全部按 OFFLINE 降级**并记录 `warn` 日志，接口仍返回 200（宁可显示离线，不阻塞选人）。
3. 坐席昵称/头像来自 upms：调用 `@DubboReference RemoteMessageStaffService.queryRecipients`，请求 `{ tenantId, limit: 500, customerServiceOnly: true }`，用返回的 `id → {nickname, avatar}` 映射补齐 `staffName`/`staffAvatar`；
   - 只补展示字段，**不改变名单**（名单以 `message_agent` 为准）；
   - upms 调用异常 → 记录 `warn`，`staffName`/`staffAvatar` 返回 null，接口仍返回 200（前端回退展示 `staffId`）；
   - 无坐席配置时**不调用** upms/Redis，直接返回 `[]`。
4. 禁用坐席（`enabled='0'`）`presenceStatus` 一律返回 `OFFLINE`（即使 Redis 还有旧值），`available=false`。
5. `available = enabled=='1' && presenceStatus=='ONLINE' && currentActiveCount < maxActiveCount`（与 `isAcceptingNewWork`/自动分配口径一致；`BUSY`/`PAUSED` 不算可接）。
6. 排序（确定性）：`available DESC` → presence 优先级 `ONLINE > BUSY > PAUSED > OFFLINE` → `currentActiveCount ASC` → `staffId ASC`。
7. 上限：最多返回 **500** 条（与 `agent/candidates` 的 upms 上限一致）；超出截断（当前租户坐席数远小于 500；若将来需要分页，属新契约）。

### 4.3 字段语义

| 字段 | 类型 | 可空 | 语义 |
|---|---|---|---|
| `staffId` | string | 否 | 工作人员 ID（`message_agent.staff_id`） |
| `staffName` | string | 是 | 坐席昵称（upms 人员快照；缺失/upms 降级时为 null） |
| `staffAvatar` | string | 是 | 坐席头像（同上） |
| `presenceStatus` | string 枚举 | 否 | `ONLINE` / `BUSY` / `PAUSED` / `OFFLINE`；禁用坐席固定 `OFFLINE`；Redis 降级时全部 `OFFLINE` |
| `enabled` | string | 否 | `"1"` 启用 / `"0"` 禁用 |
| `autoAccept` | string | 否 | `"1"` 自动接待 / `"0"` 手动 |
| `currentActiveCount` | integer | 否 | 当前在办会话数快照（`message_agent.current_active_count`，由分配流程维护、维护任务对账） |
| `maxActiveCount` | integer | 否 | 接待上限 |
| `available` | boolean | 否 | 是否可接新会话（见 4.2-5） |

### 4.4 响应 JSON 示例

```json
{
  "code": 0,
  "msg": null,
  "data": [
    {
      "staffId": "1958100000000000011",
      "staffName": "客服小美",
      "staffAvatar": "https://cdn.example.com/avatar/11.png",
      "presenceStatus": "ONLINE",
      "enabled": "1",
      "autoAccept": "1",
      "currentActiveCount": 3,
      "maxActiveCount": 10,
      "available": true
    },
    {
      "staffId": "1958100000000000012",
      "staffName": "客服小刚",
      "staffAvatar": null,
      "presenceStatus": "BUSY",
      "enabled": "1",
      "autoAccept": "0",
      "currentActiveCount": 10,
      "maxActiveCount": 10,
      "available": false
    },
    {
      "staffId": "1958100000000000013",
      "staffName": null,
      "staffAvatar": null,
      "presenceStatus": "OFFLINE",
      "enabled": "0",
      "autoAccept": "1",
      "currentActiveCount": 0,
      "maxActiveCount": 10,
      "available": false
    }
  ]
}
```

### 4.5 与现有 `GET /staff/agent/candidates` 的关系

| 维度 | `agent/candidates`（既有，不改） | `agent/roster`（本批次新增） |
|---|---|---|
| 数据来源 | upms `queryRecipients(customerServiceOnly=true, limit=500)`：**具备客服资格的工作人员全集** | `message_agent` **已配置坐席** + Redis presence + 负载 |
| 是否含未配置坐席的人 | 含 | 不含 |
| 是否含在线/负载/容量 | 不含 | 含 |
| 权限 | `message:service:agent` | `message:service:agent` **OR** `message:service:supervisor` |
| 典型用途 | 客服坐席配置弹窗的「人」下拉；转交/强制分配弹窗的**全量搜索**兜底 | 转交/强制分配弹窗的「推荐/在线」列表；顶栏容量展示 |

前端选人建议（写入前端 task-4 参考）：

1. 转交/强制分配弹窗：优先请求 `roster`，把 `available=true` 置顶、在线但不可接的次之、离线/禁用置底并注明原因；
2. 仍保留对 `candidates` 的搜索能力（roster 里没有的合格人员也可选，**强制分配后端不校验目标在线**，普通转交目标需在线/可接，由后端既有校验决定，前端只做提示）；
3. `roster` 不替代 `candidates`：坐席配置保存前仍需 `candidates` 校验「是否具备客服资格」（后端 `saveConfig` 内部已用 `isCustomerServiceStaff` 强校验）；
4. 主管无 `message:service:agent` 权限时，只能通过 `roster` 获取带负载的选人列表（这正是把 `supervisor` 加入 roster 权限的原因）。

### 4.6 错误与降级

| 场景 | 表现 | 说明 |
|---|---|---|
| 未登录/会话失效 | HTTP 401 + `code=401` | 走既有会话失效链路 |
| 缺两个权限 | HTTP 403 + `code=403` | 前端按权限隐藏入口 |
| Redis 不可用 | **200** + `presenceStatus=OFFLINE`（全部）、`available=false` | 记 `warn`；前端提示「在线状态暂不可用」 |
| upms 不可用 | **200** + `staffName`/`staffAvatar=null` | 记 `warn`；仍展示 staffId/负载/容量 |
| 无坐席配置 | **200** + `data=[]` | 前端展示「暂无已配置坐席」引导 |
| DB 异常 | HTTP 500 + `code=1` | 通用错误 |

### 4.7 前端 TS 类型建议

```ts
// api/message/types.ts
export interface AgentRosterItem {
  staffId: string;
  staffName?: string;
  staffAvatar?: string;
  presenceStatus: 'BUSY' | 'OFFLINE' | 'ONLINE' | 'PAUSED';
  enabled: '0' | '1';
  autoAccept: '0' | '1';
  currentActiveCount: number;
  maxActiveCount: number;
  available: boolean;
}

// api/message/agent.ts
export function getAgentRoster() {
  return requestClient.get<AgentRosterItem[]>('/message/staff/agent/roster');
}
```

---

## 5. 能力 D：队列统计 `GET /message/staff/conversation/queue-stats`

### 5.1 接口

| 项 | 值 |
|---|---|
| 方法/路径 | `GET /message/staff/conversation/queue-stats` |
| Controller | `StaffConversationController#queueStats`（新增方法） |
| 权限 | `@SaCheckPermission(value = { "message:service:agent", "message:service:supervisor" }, mode = SaMode.OR)` |
| 请求参数 | 无 |
| 响应 | `Result<QueueStatsVO>` |
| 用途 | 工作台/独立窗口顶栏与队列面板：等待数、最久等待、在线坐席、可用容量 |

### 5.2 响应结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | object | 全租户汇总（必返回，即使全为 0） |
| `total.waitingCount` | integer | 等待中会话总数（= `groups` 各队列之和） |
| `total.oldestWaitingSince` | string/null | 全部等待会话中最早的等待开始时间（`MIN(create_time)`，口径见 5.3）；无等待时为 `null` |
| `total.onlineAgentCount` | integer | 在线坐席数（全租户，口径见 5.3） |
| `total.availableCapacity` | integer | 可用接待容量（全租户，口径见 5.3） |
| `groups` | array | **有等待会话**的队列分组，按 `oldestWaitingSince ASC, queueCode ASC` 排序；无等待时为 `[]` |
| `groups[].queueCode` | string | 队列编码（`message_conversation.queue_code`） |
| `groups[].waitingCount` | integer | 该队列等待中会话数（≥1） |
| `groups[].oldestWaitingSince` | string | 该队列最久等待开始时间（`MIN(create_time)`） |

> 设计说明：当前**没有坐席-队列绑定**（`message_agent` 无 queue 字段），因此 `onlineAgentCount` / `availableCapacity` **只在 `total` 上按全租户口径返回一次**，不在每个分组重复下发，避免前端误以为「该队列专属坐席」。将来 P2-B 引入队列/坐席绑定后再扩展分组字段（届时升级契约）。

### 5.3 统计口径（必须按此实现）

| 指标 | 口径 |
|---|---|
| 等待中会话 | `conversation_type='CUSTOMER_SERVICE'` AND `status='WAITING'` AND `assigned_staff_id IS NULL` AND `del_flag='0'`（与 `countWaiting`、`selectWaitingPool`、待领取池完全一致；**不含** `STAFF_DIRECT` 私信） |
| 最久等待开始时间 | `MIN(create_time)`，与待领取池 `ORDER BY create_time ASC` 同口径（重开会话沿用首次创建时间的已知限制同 §3.3） |
| 在线坐席数 | `enabled='1'` 且 presence 有效（`ONLINE`/`BUSY`/`PAUSED`，即有未过期心跳；`OFFLINE`=显式离线或 TTL 过期） |
| 可用容量 | `Σ max(0, max_active_count - current_active_count)`，仅统计 `enabled='1'` 且 `presenceStatus='ONLINE'` 的坐席（`BUSY`/`PAUSED` 不承接新工作，不计容量；与 `available`/`isAcceptingNewWork` 口径一致） |
| 分组 | 按 `queue_code` GROUP BY 等待中会话；`groups` 只含有等待的队列（队列全集属配置项，`queue_code` 目前无独立配置表） |
| 实时性 | 无缓存，实时查询（1 条分组聚合 SQL + 1 次坐席全量读 + 1 次 Redis MGET）；前端建议 ≥30s 轮询或随会话列表刷新触发，勿在渲染循环内高频调用 |

### 5.4 与既有 `attention.waitingTotal` 的差异

- `GET /staff/conversation/attention` 的 `waitingTotal` = **单个队列**（默认 `DEFAULT`）的待领取数，且仅对「启用且 ONLINE」的坐席返回；
- 本接口 `total.waitingCount` = **全部队列**的等待数合计，不要求调用者在岗；
- 两者数值不可直接对比；需要单队列口径时用 `groups` 中对应项。前端铃铛角标继续用 `attention`，队列面板用 `queue-stats`。

### 5.5 响应 JSON 示例

```json
{
  "code": 0,
  "msg": null,
  "data": {
    "total": {
      "waitingCount": 5,
      "oldestWaitingSince": "2026-11-06 10:40:00",
      "onlineAgentCount": 3,
      "availableCapacity": 12
    },
    "groups": [
      { "queueCode": "DEFAULT", "waitingCount": 4, "oldestWaitingSince": "2026-11-06 10:40:00" },
      { "queueCode": "VIP", "waitingCount": 1, "oldestWaitingSince": "2026-11-06 11:02:31" }
    ]
  }
}
```

无等待会话时：

```json
{
  "code": 0,
  "msg": null,
  "data": {
    "total": { "waitingCount": 0, "oldestWaitingSince": null, "onlineAgentCount": 2, "availableCapacity": 15 },
    "groups": []
  }
}
```

### 5.6 错误与降级

| 场景 | 表现 | 说明 |
|---|---|---|
| 未登录/会话失效 | HTTP 401 + `code=401` | 既有链路 |
| 缺两个权限 | HTTP 403 + `code=403` | 前端隐藏入口 |
| Redis 不可用 | **200** + `onlineAgentCount=0`、`availableCapacity=0`，等待数/最久等待照常返回 | 记 `warn`；前端提示「在线状态暂不可用」 |
| 无等待会话 | **200** + `total.waitingCount=0`、`oldestWaitingSince=null`、`groups=[]` | 不是错误 |
| DB 异常 | HTTP 500 + `code=1` | 通用错误 |

### 5.7 前端 TS 类型建议

```ts
// api/message/types.ts
export interface QueueStatsGroup {
  queueCode: string;
  waitingCount: number;
  oldestWaitingSince: string;
}

export interface QueueStatsTotal {
  waitingCount: number;
  oldestWaitingSince?: string;
  onlineAgentCount: number;
  availableCapacity: number;
}

export interface QueueStats {
  total: QueueStatsTotal;
  groups: QueueStatsGroup[];
}

// api/message/conversation.ts
export function getQueueStats() {
  return requestClient.get<QueueStats>('/message/staff/conversation/queue-stats');
}
```

---

## 6. 权限与菜单（无新增）

| 接口 | 权限 | 说明 |
|---|---|---|
| A-staff `GET /staff/conversation` | 无注解（登录即可） | 与现状一致，本批次不放宽/收紧 |
| A-admin `GET /admin/conversation/page` | `message:service:supervisor` | 与现状一致 |
| C `GET /staff/agent/roster` | `message:service:agent` OR `message:service:supervisor` | 复用现有两个权限码，无新增 |
| D `GET /staff/conversation/queue-stats` | `message:service:agent` OR `message:service:supervisor` | 同上 |

- 四个权限码已存在于 `db/boot/20message_menu.sql` / `db/cloud/20message_menu.sql`（客服工作台菜单下），**无需新增菜单/权限/SQL**。
- 前端按钮/入口按 `hasAccessByCodes(['message:service:agent','message:service:supervisor'])` 控制（与现有 presence/转交选人权限判断保持一致）。

---

## 7. 无 DB 变更声明（强制）

本批次**不涉及任何数据库变更**：

- 不新增/修改表、列、索引、外键、生成列、种子数据、菜单与角色授权；
- 不新增 `aryn-mall-java/db/boot/**` 与 `aryn-mall-java/db/cloud/**` 增量 SQL，不登记 `build-full-sql.mjs`，不重新生成 `aryn_boot_full.sql`；
- 仅复用现有列：`message_conversation.create_time/status/queue_code/assigned_staff_id/last_seq/last_message_time/last_message_summary`、`message_participant.participant_name/participant_type/participant_status/last_read_seq`、`message_agent.enabled/auto_accept/max_active_count/current_active_count`；
- 仅复用现有索引：`idx_message_conversation_waiting`（tenant_id, queue_code, status, create_time）可支撑 D 的等待聚合；`idx_message_conversation_assignee` 可支撑 A 的 assignedStaffId 过滤；keyword 为前导模糊匹配，不依赖新索引（见 §2.4）。
- 若后续实现中发现**必须改 DB**（如等待开始时间精确化、坐席-队列绑定、keyword 全文索引），立即停止并按 AGENTS.md 双模式 SQL 规则单独立项，且需先改本契约。

---

## 8. 错误码与降级汇总

| 场景 | HTTP | code | 涉及接口 | 前端行为 |
|---|---|---|---|---|
| 成功 | 200 | 0 | 全部 | 正常渲染 |
| 参数非法（status/statuses/keyword/cursor） | 200 | 1 | A | 提示 msg、回滚筛选/重置分页 |
| 权限不足 | 403 | 403 | A-admin / C / D | 隐藏入口；直接调用时提示 |
| 未登录/会话失效 | 401 | 401 | 全部 | 会话失效链路 |
| Redis 不可用 | 200 | 0 | C / D | 在线状态降级为 0/OFFLINE + 提示「在线状态暂不可用」 |
| upms 不可用 | 200 | 0 | C | 昵称/头像为空，回退 staffId |
| 无数据 | 200 | 0 | A/C/D | 空态（列表空/坐席空/队列全 0） |
| 其它异常 | 500 | 1 | 全部 | 通用错误 + 重试 |

---

## 9. 测试清单

### 9.1 后端自动化测试（task-3 必须覆盖）

| 测试类（新增） | 覆盖点 |
|---|---|
| `ConversationInboxQueryFilterTest` | keyword trim/转义/超长报错；statuses 归一化（大写/去空/去重/非法）；statuses 优先于 status（同时传时 status 被忽略）；空白 assignedStaffId/queueCode 归 null；unreadOnly 默认 false/true 透传；limit 归一化（0→20、>100→100）；cursor 与筛选参数同时透传；admin 与 staff 分别透传；非法 status 仍报旧文案 |
| `AgentRosterServiceTest` | roster 组装（upms 昵称/头像映射、enabled/autoAccept/计数）；available 计算（ONLINE+未满员=true；BUSY/满员/禁用/离线=false）；禁用坐席 presence 强制 OFFLINE；Redis 异常→全员 OFFLINE 降级；upms 异常→昵称/头像 null 且名单不丢；空坐席→空列表且不调 upms/Redis；排序（available→presence→负载→staffId）；payload 上限 500 |
| `QueueStatsServiceTest` | 分组聚合 + total 求和/最久等待取 MIN；无等待→total 0/null + groups 空；多队列排序；onlineAgentCount/availableCapacity 来自坐席能力统计（含 Redis 降级为 0）；等待统计与 `assigned_staff_id IS NULL`/`CUSTOMER_SERVICE` 口径 |
| `ConversationInboxSqlContractTest` | XML 文本守卫：LIKE 三字段 + `ESCAPE '!'`、statuses foreach、assignedStaffId/queueCode 片段、staff unread 片段（`c.last_seq > p.last_read_seq`）、admin unread EXISTS 片段、`waiting_since` CASE 与 `c.create_time`、5 个 ConversationVO 查询均含新列、`selectWaitingGroups` 聚合 SQL 含 `status = 'WAITING'`+`assigned_staff_id IS NULL` |

回归（必须保持全绿，不修改语义）：

- `ConversationServiceImplTest`（仅按新 mapper 签名做最小参数适配）；
- `AgentServiceImplTest`（`filterOnline` 重构后行为不变）；
- `ConversationViewMapperContractTest`、`ConversationMapperContractTest`、`MapperXmlParseTest`；
- `mvn -pl aryn-message/aryn-message-biz test` 全量消息域测试。

### 9.2 手工验证（后端/联调）

1. staff 列表：`status=OPEN` / `statuses=WAITING,ACTIVE` / `keyword` 中文昵称·会员ID·摘要片段 / `%_` 字面量 / `assignedStaffId` / `queueCode` / `unreadOnly=true` 组合；确认分页 `nextCursor` 翻页结果不重不漏（带相同筛选）。
2. admin 列表：supervisor 账号验证全租户 + 已关闭 + `statuses` 组合 + `unreadOnly=true`（负责客服未读口径）；agent 无 supervisor 权限应 403。
3. `waitingSince`：WAITING 会话非空、ACTIVE/ASSIGNED/CLOSED 为空；`/waiting` 池、`/attention` 明细则同样返回；`createTime` 有值。
4. roster：多坐席时 ONLINE/BUSY/OFFLINE/禁用状态与排序；把 Redis presence key 删除后刷新应掉到 OFFLINE；主管账号可访问、普通员工 403；断开 upms（或 mock）时昵称空但接口 200。
5. queue-stats：造 2 个队列的 WAITING 会话，确认分组/总计/最久等待/MIN 正确；全部领取后 `waitingCount=0`、`groups=[]`；停 Redis 后在线数/容量为 0 且接口 200。
6. 双模式：`VITE_OPEN_BOOT=false` 下 Network 请求为 `/message/...`；`true` 下为 `/boot/...`；两种模式下 4 个接口均 200。
7. 回归：私信页、通知收件箱、铃铛 `attention`、坐席配置保存/上线/心跳。

### 9.3 前端联调（task-4/task-5 参考）

- `Conversation` 类型补 `createTime`/`waitingSince`；`getStaffConversations`/`getAdminConversations` 参数补 `keyword/statuses/assignedStaffId/queueCode/unreadOnly`；
- 新增 `getAgentRoster` / `getQueueStats`；
- 筛选变化时必须重置 `cursor`；页签 `status` 与精细 `statuses` 不要同时下发（或明确按「statuses 覆盖 status」预期）；
- 双模式改写走 `requestClient`，禁止自拼 `/boot`。

---

## 10. 风险与待确认

| # | 风险/待确认 | 影响 | 处理 |
|---|---|---|---|
| 1 | 重开会话 `waitingSince` 沿用首次 `create_time`，等待时长偏大 | 前端展示/排序与产品预期可能不一致 | 本批次与既有待领取池排序一致；精确化需新增等待开始时间列（P2-B，DB 变更） |
| 2 | `keyword` 前导模糊匹配无法走索引 | 会话量级大时列表变慢 | 租户+limit 兜底；量级超预期时 P2-B 引入全文索引/ES |
| 3 | admin `unreadOnly` 采用「负责客服未读」代理口径 | 主管可能期望「会员未读」或「本人未读」 | 契约已明确；产品若调整口径先改契约 |
| 4 | presence 心跳 TTL 90s | 坐席异常掉线后最多 90s 才显示 OFFLINE，统计短时滞后 | 前端轮询 30s，接受最终一致；不做服务端强探测 |
| 5 | roster / candidates 上限 500 | 坐席数超大时截断（昵称缺失/名单不全） | 当前租户远小于 500；超限分页另立项 |
| 6 | Redis / upms 降级后前端无法区分「真离线」与「服务不可用」 | 可能误判 | 建议前端在响应反复全 OFFLINE/0 时展示「在线状态暂不可用」提示（可选：后续加降级标记字段，需改契约） |
| 7 | `statuses` 与 `status` 同时传时忽略 `status` | 旧前端若依赖 status 会无感（正是兼容策略） | 契约明确；新前端不要同时传 |
| 8 | 并行会话未提交改动（`CustomerSnapshotService` 等）与本次改同一批文件 | 冲突/覆盖风险 | 本次全部用最小 edit 增量修改，不整文件覆盖、不格式化；冲突先报 Lead |
| 9 | `queue-stats` 与 `attention.waitingTotal` 口径不同 | 前端铃铛/队列面板数字不一致的误解 | 见 §5.4；两个场景各用各的接口 |

---

## 11. 契约到实现的文件映射（后端 task-3）

**`aryn-message-api`（新增/修改）**

| 文件 | 变更 |
|---|---|
| `dto/conversation/ConversationInboxQuery.java` | 修改：新增 `statuses/keyword/assignedStaffId/queueCode/unreadOnly` |
| `vo/conversation/ConversationVO.java` | 修改：新增 `createTime/waitingSince` |
| `vo/agent/AgentRosterVO.java` | 新增：roster 元素 |
| `vo/agent/AgentCapacityVO.java` | 新增：坐席在线数/可用容量（D 内部聚合用） |
| `vo/conversation/QueueStatsVO.java` / `QueueStatsTotalVO.java` / `QueueStatsGroupVO.java` | 新增：D 的响应结构 |

**`aryn-message-biz`（修改/新增）**

| 文件 | 变更 |
|---|---|
| `controller/staff/StaffConversationController.java` | 修改：新增 `GET /queue-stats` |
| `controller/staff/AgentController.java` | 修改：新增 `GET /roster` |
| `service/ConversationService.java` + `impl/ConversationServiceImpl.java` | 修改：inbox/adminInbox 参数归一化与透传；新增 `queueStats`；LIKE 转义工具 |
| `service/AgentService.java` + `impl/AgentServiceImpl.java` | 修改：新增 `listRoster`、`agentCapacity`；presence 批量读取复用；`filterOnline` 行为不变 |
| `mapper/MessageConversationMapper.java` + `resources/mapper/MessageConversationMapper.xml` | 修改：selectInbox/selectTenantInbox 增加筛选参数与片段；`conversationViewColumns`/`selectTenantInbox`/`selectWaitingPool` 补 2 列；新增 `selectWaitingGroups` |
| 测试（新增） | `ConversationInboxQueryFilterTest`、`AgentRosterServiceTest`、`QueueStatsServiceTest`、`ConversationInboxSqlContractTest` |

---

## 12. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| v1 | 2026-11-06 | 初稿：锁定 A/B/C/D 四项契约，确认无 DB 变更 | be-p2a |
| v1（实现记录） | 2026-11-06 | 后端按 v1 完成实现并通过 message 域全量测试（见 §13） | be-p2a |

---

## 13. 后端实现完成记录（2026-11-06，task-3）

- 状态：**已按本契约 v1 实现，无契约偏差**；后端文件清单见 §11，另新增 4 个测试类。
- 验证命令与结果：

| 命令 | 结果 |
|---|---|
| `cd aryn-mall-java && mvn -q -pl aryn-message/aryn-message-api,aryn-message/aryn-message-biz -am -DskipTests compile` | 成功（exit 0） |
| `cd aryn-mall-java && mvn -pl aryn-message/aryn-message-biz -am test -Dtest='ConversationInboxQueryFilterTest,AgentRosterServiceTest,QueueStatsServiceTest,ConversationInboxSqlContractTest' -Dsurefire.failIfNoSpecifiedTests=false` | 28/28 通过，BUILD SUCCESS |
| `cd aryn-mall-java && mvn -pl aryn-message/aryn-message-biz test` | **159/159 通过**（含既有会话/分配/推送/契约测试） |
| `cd aryn-mall-java && mvn -q -pl aryn-boot -am -DskipTests compile` | 成功（boot 聚合模式编译，未新增 biz 间直接 import） |

- 实现细节补充（不改变契约）：
  1. `statuses` 归一化后为空时服务层向 Mapper 传 `null`（等价「未传」，SQL 分支一致）；
  2. roster 只对 `enabled='1'` 的坐席做 presence MGET（禁用坐席固定 OFFLINE，省一次无意义 Redis 往返）；
  3. queue-stats 的 `total.waitingCount/oldestWaitingSince` 由分组结果在服务层聚合（SUM/MIN），`onlineAgentCount/availableCapacity` 来自 `AgentService.agentCapacity`（只读 `message_agent` + Redis，不查 upms）；
  4. LIKE 转义复用 `aryn-product` 既有约定：服务层把 `! % _` 转义、SQL 固定 `ESCAPE '!'`（与 §2.4 一致）。
- 并行会话说明：`ConversationServiceImpl`、`MessageParticipantMapper(.xml)`、`ConversationAssignmentServiceImpl`、`CustomerSnapshotService` 的未提交改动为并行会话既有内容；本次仅在 `ConversationServiceImpl` 做最小增量 edit，未覆盖/回退/格式化。
- 联调提醒：本次未接真实 MySQL 验证（环境无库），SQL 语义由文本契约测试 + `MapperXmlParseTest` 守卫；上线前建议在 dev 库对「keyword 中文/转义 + statuses 组合 + queue-stats 分组」各跑一次真实查询。

---

# 批次二（v2 新增，2026-11-06）

## 14. 批次二总览

| # | 能力 | 接口 | 权限 | 数据库变更 |
|---|---|---|---|---|
| E | 消息引用（发送校验 + VO 引用摘要，批量防 N+1） | 复用既有 `POST /message/staff/conversation/{id}/messages`（C 端同构 `POST /message/app/conversation/{id}/messages`）；VO 增引用摘要字段 | 随既有发送接口（参与者校验） | 无（`message_chat.quoted_message_id` 列已存在） |
| F | 消息撤回 | `POST /message/staff/conversation/{conversationId}/messages/{messageId}/recall` | 登录 TOB + 服务内校验「发送者本人 + 会话参与者」 | 无（`message_chat.recall_status/recall_time` 列已存在） |
| G | 消息专用图片上传 | `POST /message/staff/upload/image`（multipart） | 登录 TOB（不设权限码，与发送接口一致） | 无 |

**共同约束**：

- 18 个新增字段/返回值全部基于现有列与现有 Redis/Dubbo 能力，**无 DDL/DML**（见 §18）；
- 前端路径一律以 `/message` 微服务域开头，经 `requestClient` 的 `parseOpenBoot` + `rewriteBootUrl` 改写（cloud `...`/boot `/boot/...`），后端 Controller 不写 `/boot`；
- 管理端优先：引用/撤回/图片入口本期只做管理端客服工作台与私信页；C 端后端能力天然兼容（同一 send/page 实现），C 端 UI 本批次不改；
- 实时同步**不新增 WS 事件类型**（复用既有 `CONVERSATION_MESSAGE`），保证 C 端/管理端对未知事件零风险（详见 §16.4）。

---

## 15. 能力 E：消息引用

### 15.1 发送侧（复用既有发送接口，不改路径/权限）

`POST /message/staff/conversation/{conversationId}/messages`（管理端）与 `POST /message/app/conversation/{id}/messages`（C 端）共用 `ChatMessageSendRequest`；`quotedMessageId` 字段**已存在**，本批次补齐服务端校验与回显。

请求示例（管理端回复时引用一条文本消息）：

```json
{
  "clientMessageId": "c-8f2a1b",
  "messageType": "TEXT",
  "content": "收到，我马上为您处理",
  "quotedMessageId": "1958600000000000221"
}
```

校验规则（服务层 `ChatMessageServiceImpl`，任何一条不满足 → HTTP 200 + `code=1` 中文错误）：

| # | 规则 | 错误 msg |
|---|---|---|
| 1 | 空白串视为未引用（`trim` 后为空 → 按普通消息发送） | — |
| 2 | 长度 ≤32（列 `varchar(32)`），超长拒绝 | `引用消息ID不合法` |
| 3 | 必须存在于**当前会话**且未删除（`tenant_id + conversation_id + id`） | `引用的消息不存在或不属于当前会话` |
| 4 | 被引用消息 `recall_status != '1'` | `引用的消息已撤回，不能引用` |
| 5 | 被引用消息类型不能是 `SYSTEM`（系统提示无引用语义） | `系统消息不支持引用` |
| 6 | 幂等重放（同 `clientMessageId` 已存在）直接返回原消息，**不重复校验引用**（避免被引用消息随后撤回导致重放失败） | — |

- `quotedMessageId` 不做「必须对方发送」的限制：可引用对方消息，也可引用自己之前的消息。
- 校验与发送在同一事务内；写入 `message_chat.quoted_message_id`（列已存在）。

### 15.2 响应字段：`ChatMessageVO` 引用摘要（page / send / recall 均返回）

| 字段 | 类型 | 可空 | 语义 |
|---|---|---|---|
| `quotedMessageId` | string | 是 | 已有字段；引用目标 ID；无引用为 null |
| `quotedMessageType` | string | 是 | 引用目标的消息类型（`TEXT/IMAGE/PRODUCT_CARD/ORDER_CARD/REFUND_CARD/NOTICE_CARD`）；目标不可用/已撤回时为 null |
| `quotedMessageSummary` | string | 是 | 引用摘要，**最大 100 字符**；目标不可用/已撤回时为 null（前端显示占位文案） |
| `quotedSenderName` | string | 是 | 引用目标的发送者昵称快照；不可用时为 null |
| `quotedRecalled` | boolean | 是 | **仅在有引用时出现**：`false`=引用可用；`true`=被引用消息已撤回/已删除/查不到（前端显示「引用的消息已撤回」或「原消息不可用」）。无引用（`quotedMessageId=null`）时本字段为 null |
| `recallStatus` | string | 否 | 已有字段：`"0"` 正常、`"1"` 已撤回 |
| `recallTime` | string | 是 | **新增**：撤回时间 `yyyy-MM-dd HH:mm:ss`，未撤回为 null |

摘要生成规则（读时动态生成，不落库，被引用消息后续撤回会实时反映到所有引用方）：

| 目标消息类型 | `quotedMessageSummary` |
|---|---|
| `TEXT` | 原文 trim 后截断至 100 字符 |
| `IMAGE` | `[图片]` |
| `PRODUCT_CARD` / `ORDER_CARD` / `REFUND_CARD` / `NOTICE_CARD` | `[商品]` / `[订单]` / `[退款]` / `[通知]`（目标 payload 含 `title` 时追加空格+title，整体仍 ≤100） |
| 目标已撤回 / 不存在 / 已删除 | null（`quotedRecalled=true`） |

### 15.3 批量补摘要（避免 N+1）

- **page**：`records` 取完后，收集所有非空 `quotedMessageId` 去重，**一次** `SELECT * FROM message_chat WHERE tenant_id=? AND conversation_id=? AND id IN (...)`（上限 100 个 ID，与 `limit ≤100` 对齐），构建 `Map<id, MessageChat>` 后回填；查不到的引用 → `quotedRecalled=true`。每页固定只多 1 条 SQL。
- **send**：引用目标在 §15.1 校验时已查出，直接复用该行回填新消息 VO，不额外查询。
- **幂等重放**：返回历史消息时若 `quotedMessageId` 非空，最多补 1 次批量查询（单条），可接受。
- 引用目标限定同会话：即使历史脏数据出现跨会话引用，也按「查不到 → `quotedRecalled=true`」降级，不报错。

### 15.4 被引用消息撤回后的回显语义

- 引用方消息本身**不重写、不删除**：`quotedMessageId` 保留，`quotedRecalled=true`，摘要/类型/发送人置 null；
- 被引用消息自身 `recallStatus="1"`、`content/payload` 不再下发（见 §16.3）；
- 前端渲染约定：`quotedMessageId` 存在时始终渲染引用块；`quotedRecalled=true` 时引用块文案用「引用的消息已撤回」/「原消息不可用」，不展示原内容。

### 15.5 C 端范围

- 后端校验与摘要生成对 C 端发送同样生效（同一 `send` 实现），C 端若传 `quotedMessageId` 也可用；
- **C 端引用 UI 本批次不做**（小程序无引用交互）；C 端消息列表展示引用摘要字段可作为后续小需求（字段已下发，前端可忽略）。

### 15.6 前端 TS 类型建议

```ts
// api/message/types.ts
export interface ChatMessage {
  // ...既有字段不变
  quotedMessageId?: string;
  quotedMessageType?: ChatMessageType;
  quotedMessageSummary?: string;
  quotedSenderName?: string;
  quotedRecalled?: boolean;
  recallStatus: '0' | '1';
  recallTime?: string;
}
```

---

## 16. 能力 F：消息撤回

### 16.1 接口

| 项 | 值 |
|---|---|
| 方法/路径 | `POST /message/staff/conversation/{conversationId}/messages/{messageId}/recall` |
| Controller | `StaffConversationController#recall`（新增方法） |
| 权限 | 不设 `@SaCheckPermission`（与发送接口一致：登录 TOB 即可，服务内校验参与者与发送者本人） |
| 请求体 | 无（`@PathVariable` 传参；如需撤回原因属后续需求） |
| 响应 | `Result<ChatMessageVO>`：更新后的消息（已脱敏，含 `recallStatus="1"` / `recallTime`） |

### 16.2 业务规则（服务内校验，任一不满足 → `code=1` 中文错误）

| # | 规则 | 错误 msg |
|---|---|---|
| 1 | 会话存在且属于当前租户（先锁会话行，避免与发送路径死锁顺序不一致） | `会话不存在` |
| 2 | 会话未关闭（`status != CLOSED`） | `已关闭会话不能撤回消息` |
| 3 | 调用者是该会话 ACTIVE 参与者（与发送同一门禁） | `无权撤回该会话消息` |
| 4 | 只能撤回**自己发送**的消息（`sender_type=SYS_USER` 且 `sender_id=当前用户`） | `只能撤回自己发送的消息` |
| 5 | 消息存在且属于该会话 | `消息不存在或已删除` |
| 6 | 已撤回的消息再次调用 → **幂等成功**（返回 `recallStatus="1"` 的 VO，不再校验时间窗） | — |
| 7 | 消息类型仅 `TEXT` / `IMAGE` 可撤回；`PRODUCT_CARD/ORDER_CARD/REFUND_CARD/NOTICE_CARD/SYSTEM` 不可撤回（理由：业务卡片是跨端结构化业务凭证/跳转入口，撤回会破坏订单/退款等业务语义；系统消息无撤回语义） | `消息类型不支持撤回（仅文本和图片）` |
| 8 | 时间窗：发送时间（`create_time`）起 **2 分钟**内（服务端常量 `RECALL_WINDOW_MINUTES=2`，以服务器时钟为准，边界 `<= 2min` 允许） | `已超过撤回时间（2 分钟内可撤回）` |

- 撤回时间窗是**产品建议值**，集中在 `ChatMessageServiceImpl` 常量；改口径只需改常量（本批次不改配置/DB）。
- 本期只开放管理端撤回；主管撤回他人消息、C 端撤回均不在本批次（若要开放，复用同一 service，另议权限）。

### 16.3 数据更新与脱敏（无 DB 变更）

- 条件更新 `message_chat`（防并发双击/重复提交）：

  ```sql
  UPDATE message_chat
  SET recall_status = '1', recall_time = #{recallTime}, update_by = #{operatorId}, update_time = NOW()
  WHERE tenant_id = #{tenantId} AND id = #{messageId} AND conversation_id = #{conversationId}
    AND sender_type = 'SYS_USER' AND sender_id = #{operatorId}
    AND recall_status = '0' AND del_flag = '0'
  ```

  - 受影响行数 0 → 重新查一次，若已撤回按幂等成功返回，否则 `撤回失败，请重试`；
  - 原始 `content/payload` **保留在 DB**（审计），仅接口不下发。
- **列表预览防泄漏**：若被撤回的消息正是会话最后一条（`message_conversation.last_message_id = messageId`），同步把 `last_message_summary` 改为 `[消息已撤回]`（`last_seq/last_message_time` 不变，游标协议不受影响）。不更新会让会话列表继续展示撤回正文。
- 接口脱敏规则（`ChatMessageVO`）：`recallStatus="1"` 时 `content=null`、`payload=null`；`recallTime` 有值；`messageType/sender*/seqNo` 保留（前端按「消息已撤回」占位渲染）。

### 16.4 实时同步方案（已读代码后定）

**结论：复用既有 `CONVERSATION_MESSAGE` 推送事件，不新增 WS 事件类型。**

依据（先读了 `store/message.ts`、`use-conversation-push.ts`、`MessagePushServiceImpl`、`MessagePushEvent`）：

1. 管理端 `store/message.ts#isConversationPush` 只认 `CONVERSATION_MESSAGE` / `CONVERSATION_QUEUED`，页面 `use-conversation-push.ts` 收到后**按游标补拉**会话消息；撤回后补拉即得到脱敏后的 VO，天然收敛双窗/多端。
2. C 端（uniapp）当前**不消费**会话推送事件（只处理 `SESSION_INVALID`/`NOTICE_RECEIVED`），未知事件被忽略不会报错；C 端在下次进入会话/刷新时看到撤回态。
3. 新增 `CONVERSATION_RECALL` 事件需要管理端 + C 端同时改事件白名单，收益仅为省一次补拉，风险大于收益，故不采用。

实现口径：

- `recall` 事务内完成 DB 更新；**事务提交后**调用既有 `pushService.pushConversationMessage(tenantId, conversationId, messageId, seqNo)`（复用 `runAfterCommit`），由既有 Redis Pub/Sub 广播到其它实例（跨实例去重逻辑不变）。
- 本窗口/其它窗口收到事件后按游标拉取当前会话消息，前端用 `mergeServerMessage` 覆盖本地该条消息 → 显示撤回占位。
- 若撤回的是非当前打开会话的消息，管理端其它页面最多刷新列表摘要，不影响使用。

### 16.5 响应 JSON 示例

```json
{
  "code": 0,
  "msg": null,
  "data": {
    "id": "1958600000000000221",
    "conversationId": "1958300000000000002",
    "seqNo": 38,
    "senderType": "SYS_USER",
    "senderId": "1958100000000000011",
    "senderName": "客服小美",
    "senderAvatar": "https://cdn.example.com/avatar/11.png",
    "messageType": "TEXT",
    "content": null,
    "payload": null,
    "clientMessageId": "c-8f2a1b",
    "quotedMessageId": null,
    "quotedMessageType": null,
    "quotedMessageSummary": null,
    "quotedSenderName": null,
    "quotedRecalled": null,
    "recallStatus": "1",
    "recallTime": "2026-11-06 12:03:11",
    "createTime": "2026-11-06 12:02:40"
  }
}
```

### 16.6 错误与降级汇总

| 场景 | 表现 |
|---|---|
| 会话不存在/无权/已关闭/非本人/超时/类型不支持 | HTTP 200 + `code=1` + 上表中文 msg，前端 toast 提示 |
| 重复撤回（已撤回） | HTTP 200 + `code=0`，返回 `recallStatus="1"`（幂等） |
| 未登录/会话失效 | HTTP 401 + `code=401` |
| 非法 device（C 端 token 调用 staff 接口） | `SecurityUtils.requireUser(TOB)` 抛 `ArynBusinessException` → **HTTP 200 + code=1「当前登录端无权访问该资源」**（平台统一语义，与其它 staff 接口一致；契约早期文字写 401 已按实现修正） |
| 推送失败（Redis/WS 异常） | 不影响撤回结果（`MessagePushServiceImpl` 内部兜底 warn，保留本实例直推） |
| DB 条件更新影响行数 0（并发） | 重查后幂等返回或 `撤回失败，请重试` |

---

## 17. 能力 G：消息专用图片上传

### 17.1 总体流程

```text
① POST /message/staff/upload/image (multipart) ──→ { fileId, url, name, size, contentType }
② POST /message/staff/conversation/{id}/messages { messageType: "IMAGE", payload: {...} }
③ 接收方（管理端/C 端）按既有 chat-message-card 渲染 <img :src="payload.url" />
```

上传与发送解耦：上传只负责落盘+返回可访问 URL，不产生会话消息；发送仍走既有 `send`（含频控、幂等、未读/推送逻辑）。

### 17.2 接口

| 项 | 值 |
|---|---|
| 方法/路径 | `POST /message/staff/upload/image` |
| Content-Type | `multipart/form-data`，文件字段名 **`file`** |
| 权限 | 不设权限码：登录 TOB 员工即可（与 `/staff/conversation/{id}/messages` 一致；私信页也复用本接口） |
| 请求参数 | `file`（必填，图片文件；见 §17.3 校验） |
| 响应 | `Result<MessageUploadVO>` |
| 双模式 | 前端 `POST /message/staff/upload/image`：cloud 原样；boot → `/boot/staff/upload/image`（requestClient 统一改写） |

### 17.3 请求校验（message 侧，全部中文 `ArynBusinessException`）

| # | 校验 | 边界/规则 | 错误 msg |
|---|---|---|---|
| 1 | 登录态 + 设备类型 | `SecurityUtils.requireUser(DeviceTypeEnum.TOB)` | 未登录 → 401；C 端 token → HTTP 200 + code=1「当前登录端无权访问该资源」（平台统一语义） |
| 2 | 文件非空 | `file != null && !file.isEmpty()` | `上传文件不能为空` |
| 3 | 大小 | `<= 5MB`（`5 * 1024 * 1024` 字节，先于落盘拦截） | `图片大小不能超过 5MB` |
| 4 | 扩展名白名单 | `jpg/jpeg/png/webp`（取原始文件名扩展名，大小写不敏感；**GIF 本期不支持**） | `仅支持 JPG/JPEG/PNG/WEBP 图片` |
| 5 | Content-Type 白名单 | `image/jpeg`、`image/png`、`image/webp`（缺省/`application/octet-stream` 时以魔数为准） | 同上 |
| 6 | 魔数（文件头）校验 | jpg/jpeg=`FF D8 FF`；png=`89 50 4E 47 0D 0A 1A 0A`；webp=`RIFF....WEBP` | `图片格式不合法` |
| 7 | 文件名清洗 | 去掉路径（`\` 与 `/` 之后部分）、限长 100；空则用 `image.<ext>` | — |
| 8 | 存储配置 | upms 侧读取当前租户启用中的存储配置；未配置 → 业务错误透传 | `当前租户未配置启用的文件存储，请联系管理员在系统设置-文件存储配置中启用` |
| 9 | 远程存储异常/超时 | message 侧 catch 后包装（记录 error 日志） | `图片上传服务暂不可用，请稍后重试` |

> 说明：第 5 条的 Content-Type 由客户端声明、不可信，最终以**扩展名 + 魔数**双校验为准；SVG/BMP 等不在白名单（避免 SVG 脚本注入面）。

### 17.4 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileId` | string | **= url**（沿用 C 端既有约定：C 端把上传 URL 直接当 fileId 传，`ChatMessageServiceImpl` 只做非空/≤512 长度校验，不做存在性校验） |
| `url` | string | 可访问地址；由存储实现生成，cloud 下形如 `/upms/file/local/{tenantId}/xxx.png` 或对象存储公网 URL，boot 下形如 `/boot/file/local/{tenantId}/xxx.png`；**路径必含 `/{tenantId}/`**（发送侧 `validateImagePayload` 会再次校验租户隔离） |
| `name` | string | 清洗后的原始文件名（≤100） |
| `size` | long | 字节数 |
| `contentType` | string | 规范化后的 MIME（`image/jpeg` / `image/png` / `image/webp`） |

响应示例：

```json
{
  "code": 0,
  "msg": null,
  "data": {
    "fileId": "/upms/file/local/1958000000000000001/9f0f1c6e2a4d4f1e8b1d0c3a5e7f9a2b.png",
    "url": "/upms/file/local/1958000000000000001/9f0f1c6e2a4d4f1e8b1d0c3a5e7f9a2b.png",
    "name": "订单截图.png",
    "size": 183245,
    "contentType": "image/png"
  }
}
```

### 17.5 发送侧约定（既有 send，仅补充 IMAGE payload 与白名单）

`POST /message/staff/conversation/{id}/messages`：

```json
{
  "clientMessageId": "c-img-001",
  "messageType": "IMAGE",
  "payload": "{\"fileId\":\"<upload.url>\",\"url\":\"<upload.url>\",\"mimeType\":\"image/png\",\"name\":\"订单截图.png\",\"size\":183245}"
}
```

- 既有 `messageType=IMAGE` 的 payload 校验保持：`fileId`/`url`/`mimeType` 必填、url 同租户、长度限制；`name/size` 为可选附加字段（校验器忽略未知字段），供 C 端/后续 UI 展示。
- **上传/发送白名单统一（Lead 2026-11-06 决策）**：上传白名单与既有 `ChatMessageServiceImpl.IMAGE_MIME_TYPES` 完全一致，均为 `image/jpeg,image/png,image/webp`；**GIF 本期不支持**（上传侧同样拒绝），因此**不改动既有发送校验、不改既有「gif 被拒绝」单测**，发送行为零变化。若后续要支持 GIF，需同时扩上传/发送两处白名单并补 GIF 魔数与安全评审，单独立项。

### 17.6 跨模块存储实现方案（Lead 2026-11-06 已批准）

`message-biz` 不能 import `upms-biz`（分层规则），也不能直读 upms 库的 `sys_storage_config`（cloud 双库）。故新增 **upms 侧 2 个 additive 新文件**（不改任何现有 upms 文件/yml）：

| 模块 | 新文件 | 内容 |
|---|---|---|
| `aryn-upms-api` | `api/remote/RemoteStorageService.java` | Dubbo 契约：`String uploadPublicFile(byte[] content, String originalFilename, String contentType, long size, String bizTag)`；返回可访问 URL；`bizTag` 仅用于服务端日志/审计（如 `service-chat`），**不落 `sys_material`** |
| `aryn-upms-biz` | `dubbo/RemoteStorageServiceImpl.java` | `@DubboService`：注入 `ISysStorageConfigService` + `StorageFactory`；`getConfig()` 取当前租户启用存储，未配置抛 `ArynBusinessException`；`storageFactory.getStrategy(config.getType()).uploadFile(config, new ByteArrayInputStream(content), originalFilename, contentType, size)`（与 `SysUploadController#appUpload` 同用法）；租户由 Dubbo 过滤器 `ArynDubboRequestFilter` 透传的 attachment / boot 同线程上下文提供 |

双模式说明：

- **cloud**：message 服务 `@DubboReference RemoteStorageService` 调 upms 服务（租户经 `ArynDubboRequestFilter` attachment 透传）；`byte[]` 走 Dubbo 序列化，**5MB 上限在 message 侧先拦截**（Dubbo 默认 payload 8MB，5MB 文件+元数据留有安全边际）；
- **boot**：两模块在同一 JVM，`@DubboReference` injvm 调用，租户上下文即 HTTP 线程上下文；无需额外配置；
- 无新增 Dubbo 配置项/Nacos 配置；upms 服务未部署或未配置存储时，按 §17.3 第 8/9 条返回业务错误（不 500）。

存储能力复用（`aryn-common-storage`）：

- 支持体系内已配置的存储策略（local/aliyun/tencent/qiniu/minio 等，由 `StorageFactory` 按 `sys_storage_config.type` 路由）；
- 租户隔离路径由 handler 生成：local = `{bucket 根目录}/{tenantId}/{uuid}.{ext}`（URL `/file/local/{tenantId}/...`），S3 = `{tenantId}/{dir}/{uuid}.{ext}`，均保证 URL 含 `/{tenantId}/`；
- 本批次只走**公开读** `uploadFile`（不落私有目录、不加签名），与素材图片/C 端图片现有行为一致。

**cloud 模式 multipart 配置**：`aryn-boot` 已配置 `maxFileSize/maxRequestSize=100MB`，但 `aryn-message-biz/src/main/resources/application.yml` 目前**未配置 multipart**（cloud 独立部署时走 Spring 默认 1MB，会导致 >1MB 图片 413）。本批次在 message 侧 `application.yml` 增加：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
```

> 说明：这是 message 模块自己的配置补齐，不改 upms/网关；boot 模式使用 `aryn-boot` 的 100MB 配置（≥5MB，行为一致）。Nacos 若对 `aryn-message` 覆盖 multipart，请保持 ≥5MB。
>
> 租户隔离核对（2026-11-06 Lead 复核）：`db/cloud/3aryn_nacos.sql` 中 `aryn-upms-biz-dev.yml` 的 `hx.tenant.tables` 已包含 `sys_storage_config`（boot 侧 `application.yml` 同样登记），cloud 下存储配置读取仍按租户上下文隔离；本批次无需新增 SQL/Nacos 配置。

### 17.7 错误与降级汇总

| 场景 | 表现 | 前端建议 |
|---|---|---|
| 空文件/超大/类型不支持/魔数不符 | 200 + `code=1` + 中文 msg | 上传前先做前端预校验（大小/类型），失败 toast |
| 未配置文件存储 | 200 + `code=1`「当前租户未配置启用的文件存储…」 | 提示管理员配置 |
| upms/Dubbo 不可用 | 200 + `code=1`「图片上传服务暂不可用，请稍后重试」 | toast + 可重试；不阻塞文本消息发送 |
| 未登录/会话失效 | 401 + `code=401` | 既有会话失效链路 |
| 非 TOB 登录态（C 端 token） | HTTP 200 + code=1「当前登录端无权访问该资源」（`SecurityUtils` 平台统一语义） | 不适用（C 端用自己的上传入口） |

### 17.8 前端 TS 类型建议

```ts
// api/message/types.ts
export interface MessageUploadResult {
  fileId: string;
  url: string;
  name: string;
  size: number;
  contentType: string;
}

// api/message/conversation.ts
export function recallConversationMessage(
  conversationId: string,
  messageId: string,
) {
  return requestClient.post<ChatMessage>(
    `/message/staff/conversation/${conversationId}/messages/${messageId}/recall`,
  );
}

// api/message/upload.ts
export function uploadConversationImage(file: File) {
  const form = new FormData();
  form.append('file', file);
  return requestClient.post<MessageUploadResult>(
    '/message/staff/upload/image',
    form,
  );
}
```

> 前端上传注意：`requestClient` 传 `FormData` 时不要手写 `Content-Type`（浏览器需自动带 boundary）；boot 模式路径由 requestClient 统一改写，页面禁止自拼 `/boot`；上传成功后按 §17.5 组装 payload 调既有 send。

---

## 18. 批次二无 DB 变更声明（强制）

- 不新增/修改表、列、索引、外键、生成列、种子数据、菜单与角色授权；
- 不新增 `db/boot/**`、`db/cloud/**` 增量 SQL，不登记 `build-full-sql.mjs`，不重新生成 `aryn_boot_full.sql`；
- 复用现有列（已核查 `db/boot/20message_center.sql` 与 `db/cloud/20message_center.sql` 第 157–159 行均存在）：
  `message_chat.quoted_message_id`、`message_chat.recall_status`（`char(1) DEFAULT '0'`）、`message_chat.recall_time`；
  以及 `message_conversation.last_message_id/last_message_summary`；
- 唯一配置变更：`aryn-message-biz/src/main/resources/application.yml` 补 cloud 模式 multipart 大小（§17.6），非 DB、非 Nacos 新增项；
- 新增权限点：无（上传接口沿用登录态，撤回/引用沿用发送接口门禁）。

---

## 19. 批次二测试清单（后端 task-8）

### 19.1 自动化测试（新增独立测试文件）

| 测试类（新增） | 覆盖点 |
|---|---|
| `ChatMessageQuoteRecallTest` | 引用：空白忽略 / 超长拒绝 / 跨会话拒绝 / 不存在拒绝 / 已撤回拒绝 / SYSTEM 拒绝 / 合法引用落库；摘要：TEXT 截断 100、IMAGE 与卡片标签、目标撤回→`quotedRecalled=true`、page 批量只查 1 次（避免 N+1）、send 返回摘要。撤回：TEXT/IMAGE 成功（`recall_status='1'`/`recall_time`/脱敏 content+payload）、非本人拒绝、超 2 分钟拒绝、会话已关闭拒绝、非参与者拒绝、类型不支持拒绝、已撤回幂等成功、最后一条消息撤回同步改 `last_message_summary`、`pushConversationMessage` afterCommit 调用、gif 发送白名单 |
| `MessageUploadServiceTest` | 合法 jpg/png/webp（魔数正确）→ 调 mock `RemoteStorageService` 并返回 fileId=url（url 含 `/{tenantId}/`）；空文件/超大/扩展名不在白名单（含 gif 拒绝）/Content-Type 不符/魔数不符 → 中文业务错误且不调远程；Dubbo 异常 → `图片上传服务暂不可用`；文件名清洗（路径穿越 `..\\xx.png`）；返回 url 可被 send 侧 `validateTenantFileUrl` 接受（与 `ChatMessageQuoteRecallTest` 的 IMAGE 发送用例联合覆盖） |
| `RemoteStorageServiceImplTest`（upms-biz，低成本） | mock `ISysStorageConfigService`+`StorageFactory`：未配置→业务错误；正常→调用 `uploadFile` 返回 URL；存储策略异常→业务错误 |
| 既有 `ChatMessageServiceImplTest` | **不改**（GIF 维持既有拒绝行为，白名单与测试均不动） |

### 19.2 手工验证

1. 管理端发消息引用对方文本/图片/订单卡片：引用块摘要正确、长度截断；引用自己消息也可。
2. 引用目标在发送后被撤回：刷新后引用块显示「引用的消息已撤回」，引用方消息本身不变。
3. 撤回：2 分钟内文本/图片可撤（双窗同时收敛为「消息已撤回」）；超过 2 分钟提示；撤回最后一条后会话列表摘要为 `[消息已撤回]`。
4. 撤回卡片/系统消息被拒；主管尝试撤回他人消息被拒；C 端 token 调撤回接口 401。
5. 图片：JPG/PNG/WEBP 各上传一次并能发送成功、两端可渲染；GIF 上传被拒（提示仅支持 JPG/JPEG/PNG/WEBP）；>5MB 提示；把 .exe 改名 .png 提示格式不合法；未配置存储租户提示配置文案。
6. 双模式：cloud 下上传走 Dubbo、URL 形如 `/upms/file/local/{tenantId}/...`；boot 下 URL 形如 `/boot/file/local/{tenantId}/...`，两种模式发送 IMAGE 均通过 `validateImagePayload` 的租户校验。
7. 回归：私信页发送图片、C 端发图、通知收件箱、消息铃铛、P2-A 批次一四项接口。

---

## 20. 批次二契约到实现文件映射（后端 task-8）

**`aryn-message-api`**

| 文件 | 变更 |
|---|---|
| `vo/conversation/ChatMessageVO.java` | 修改：+`quotedMessageType/quotedMessageSummary/quotedSenderName/quotedRecalled/recallTime`（`quotedMessageId/recallStatus` 已有） |
| `vo/conversation/MessageUploadVO.java` | 新增：`fileId/url/name/size/contentType` |
| `dto/conversation/ChatMessageSendRequest.java` | 修改：仅补 `quotedMessageId` 字段注释（结构不变） |

**`aryn-message-biz`**

| 文件 | 变更 |
|---|---|
| `controller/staff/StaffConversationController.java` | 修改：新增 `POST /{id}/messages/{messageId}/recall` |
| `controller/staff/MessageUploadController.java` | 新增：`POST /staff/upload/image`（multipart，TOB 登录态） |
| `service/ChatMessageService.java` + `impl/ChatMessageServiceImpl.java` | 修改：引用校验/摘要批量、`recall(...)`、撤回脱敏（**不改 IMAGE 类型白名单**） |
| `service/MessageUploadService.java` + `impl/MessageUploadServiceImpl.java` | 新增：上传校验（扩展名/魔数/大小/文件名）+ `@DubboReference RemoteStorageService` 调用与异常包装 |
| `mapper/MessageChatMapper.java` + `resources/mapper/MessageChatMapper.xml` | 修改：+`selectByConversationAndIds`、`markRecalled` |
| `resources/application.yml` | 修改：补 cloud 模式 multipart `max-file-size: 5MB / max-request-size: 6MB` |
| 测试（新增） | `ChatMessageQuoteRecallTest`、`MessageUploadServiceTest` |

**`aryn-upms-api` / `aryn-upms-biz`（additive 新文件，Lead 已批准）**

| 文件 | 变更 |
|---|---|
| `aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/remote/RemoteStorageService.java` | 新增接口 |
| `aryn-upms-biz/src/main/java/com/aryn/cloud/upms/dubbo/RemoteStorageServiceImpl.java` | 新增 `@DubboService` 实现（复用 `ISysStorageConfigService` + `StorageFactory`，不落 `sys_material`） |
| `aryn-upms-biz/src/test/java/com/aryn/cloud/upms/dubbo/RemoteStorageServiceImplTest.java` | 新增低成本单测（mock 配置/工厂） |

---

## 21. 批次二风险与待确认

| # | 风险/待确认 | 影响 | 处理 |
|---|---|---|---|
| 1 | 撤回时间窗 2 分钟、仅 TEXT/IMAGE 可撤回、卡片不可撤回 | 产品口径可能调整 | 常量集中（`RECALL_WINDOW_MINUTES`/`RECALL_ALLOWED_TYPES`）；改口径只需改常量+少量测试 |
| 2 | GIF 本期不支持（Lead 2026-11-06 决策） | 用户上传 GIF 会被拒 | 上传/发送白名单统一为 jpeg/png/webp，既有发送校验与测试零改动；后续若需 GIF 单独立项（两处白名单 + 魔数 + 安全评审） |
| 3 | 图片上传跨模块依赖 upms 新增 2 文件 | 若 upms 未部署/无存储配置，上传不可用 | 已按 Lead 决策 additive 实现；错误降级为可读中文，不阻塞文本消息 |
| 4 | 5MB 文件经 Dubbo `byte[]` 传输 | Dubbo 默认 payload 8MB，5MB 文件+序列化开销安全边际一般 | message 侧 5MB 先拦；如运维把 payload 调小需同步调整上限（部署配置另议） |
| 5 | 撤回实时同步复用 `CONVERSATION_MESSAGE` | C 端不监听该事件，需刷新才见撤回态；管理端立即收敛 | 本批次接受（C 端 UI 不做）；后续若要求 C 端实时，单独加事件与前端白名单 |
| 6 | 引用摘要读时动态生成 | 高并发大量引用消息时每页多 1 条批量 SQL | 每页固定 1 条 IN 查询（≤100 id），可接受；不落库保留一致性 |
| 7 | `last_message_summary` 撤回改为 `[消息已撤回]` | 仅改摘要，不删内容；历史数据无需回填 | 只影响撤回瞬间的最后一条；列表预览不再泄露撤回正文 |
| 8 | 撤回推送与发送/领取并发 | 事务内条件更新 + 事务后推送，幂等设计 | 重复撤回幂等返回；推送失败不影响撤回结果 |

---

## 22. 变更记录（v2）

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| v1 | 2026-11-06 | 初稿：锁定 A/B/C/D 四项契约，确认无 DB 变更 | be-p2a |
| v1（实现记录） | 2026-11-06 | 批次一后端实现完成并通过 message 域全量测试（§13） | be-p2a |
| v2 | 2026-11-06 | 追加批次二：消息引用（发送校验 + VO 摘要批量补全）、消息撤回（2 分钟窗/TEXT+IMAGE/幂等/脱敏/复用 CONVERSATION_MESSAGE 推送）、消息专用图片上传（multipart 校验 + upms 新增 RemoteStorageService，云/boot 双模式）；确认无 DB 变更；后端实现见 task-8 | be-p2a |

---

## 23. 批次二后端实现完成记录（2026-11-06，task-8）

- 状态：**已按 v2 契约实现，无结构性契约偏差**；按 Lead 2026-11-06 决策 GIF 本期不支持（上传/发送白名单统一 `image/jpeg,image/png,image/webp`，不改既有发送校验与测试）。
- 后端改动文件清单：见 §20（含 upms 2 个新增文件 + 1 个新增测试）；前端改动由前端批次二任务负责，本记录不含前端。
- 验证命令与结果：

| 命令 | 结果 |
|---|---|
| `cd aryn-mall-java && mvn -q -pl aryn-message/aryn-message-api,aryn-message/aryn-message-biz -am -DskipTests compile` | 成功（exit 0） |
| `cd aryn-mall-java && mvn -pl aryn-message/aryn-message-biz -am test -Dtest='ChatMessageQuoteRecallTest,MessageUploadServiceTest' -Dsurefire.failIfNoSpecifiedTests=false` | 24/24 通过（引用/撤回 14 + 上传 10） |
| `cd aryn-mall-java && mvn -pl aryn-message/aryn-message-biz test` | **183/183 通过**（含批次一与全部既有测试） |
| `cd aryn-mall-java && mvn -pl aryn-upms/aryn-upms-biz -am test -Dtest='RemoteStorageServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false` | 4/4 通过 |
| `cd aryn-mall-java && mvn -q -pl aryn-boot -am -DskipTests compile` | 成功（boot 聚合模式编译） |

- 实现细节补充（不改变契约）：
  1. **错误文案细化**：撤回时消息不存在/已删除单独返回「消息不存在或已删除」，非本人发送返回「只能撤回自己发送的消息」；
  2. **上传 URL 二次租户校验**：message 侧对存储返回 URL 再校验路径含 `/{tenantId}/`，不满足直接拒绝（避免「上传成功但发送必失败」）；`fileId=url` 可被既有 `validateTenantFileUrl` 接受（单测 `sendImageAcceptsUploadedTenantScopedUrl` 覆盖）；
  3. **Dubbo 异常解包**：upms 未配置存储等业务错误若被 Dubbo 包装为 RpcException，按 cause 链找回 `ArynBusinessException` 并透传中文提示；其它异常降级「图片上传服务暂不可用，请稍后重试」；
  4. **页大小与批量**：page 每页最多 1 条 IN 查询补引用摘要；撤回只在会话最后一条消息被撤回时更新 `last_message_summary`；
  5. **multipart 配置**：仅改 `aryn-message-biz/src/main/resources/application.yml`（`max-file-size: 5MB`/`max-request-size: 6MB`），未改 `aryn-boot` 与 upms 的 yml（boot 沿用全局 100MB）。
- 并行会话说明：`ConversationServiceImpl`、`MessageParticipantMapper(.xml)`、`ConversationAssignmentServiceImpl`、`CustomerSnapshotService` 等未提交改动为并行会话既有内容；本次 batch-2 只对 `ChatMessageServiceImpl`、`StaffConversationController`、Mapper/XML、message yml 做最小 edit，未覆盖/回退/格式化；upms 侧仅新增 3 个文件（2 生产 + 1 测试），未改任何现有 upms 文件。
- 联调提醒：C 端引用/撤回 UI 不在本批次（后端能力兼容）；撤回实时收敛依赖管理端既有 `CONVERSATION_MESSAGE` 补拉；图片上传需真实租户已启用文件存储（local/S3 任一），否则返回可读业务错误。


| v2（实现记录） | 2026-11-06 | 批次二后端实现完成（引用/撤回/图片上传 + upms 远程存储），message 183/183、upms 4/4 单测通过，boot 聚合编译通过（§23） | be-p2a |
