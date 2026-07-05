# ARYN 站内信 / 通知中心 — 实施说明

**实施日期**：2026-07-05
**范围**：站内信基础功能 + 实时推送（单实例），不含发货通知、不含多实例 WS 路由

---

## 一、交付清单

### 后端（aryn-mall-java）

新增模块 `aryn-notify`（含 `aryn-notify-api` 与 `aryn-notify-biz`）：

| 路径 | 说明 |
|------|------|
| `db/boot/5aryn_boot_notify.sql` | 单体模式建表 + 预置模板 |
| `db/cloud/10aryn_notify.sql` | 微服务模式建表 + 预置模板 |
| `aryn-notify/aryn-notify-api/.../enums/` | 5 个枚举（类型/已读/跳转/目标/群发状态） |
| `aryn-notify/aryn-notify-api/.../entity/` | 3 个实体（Template/Message/Broadcast） |
| `aryn-notify/aryn-notify-api/.../dto/` | 3 个 DTO |
| `aryn-notify/aryn-notify-api/.../vo/` | 3 个 VO |
| `aryn-notify/aryn-notify-biz/.../mapper/` | 3 个 Mapper + 1 个 XML |
| `aryn-notify/aryn-notify-biz/.../service/` | 3 个 Service 接口 + 3 个实现 |
| `aryn-notify/aryn-notify-biz/.../listener/` | 4 个 RocketMQ 监听器（支付/完成/退款/取消） |
| `aryn-notify/aryn-notify-biz/.../websocket/` | WebSocket 配置 + Handler + SessionManager（单实例） |
| `aryn-notify/aryn-notify-biz/.../controller/app/` | C 端消息接口 |
| `aryn-notify/aryn-notify-biz/.../controller/admin/` | 管理端模板/群发/消息记录接口 |

对其他模块的修改（必要、最小）：

| 文件 | 修改内容 |
|------|----------|
| `pom.xml`（根） | 注册 aryn-notify 模块 + dependencyManagement 新增 aryn-notify-api |
| `aryn-boot/pom.xml` | 引入 aryn-notify-biz 依赖 |
| `aryn-boot/.../application.yml` | 新增白名单 `/ws/**`、租户表 `notify_*`、mapper 日志 |
| `aryn-common-core/.../OrderRefundSuccessEvent.java` | 新增 `orderNo` 字段（退款消息需要订单号） |
| `aryn-order/.../ArynOrderRefundEventListener.java` | 发布退款事件时 set orderNo |
| `aryn-order-api/.../OrderConsumerDTO.java` | 新增 `userId` 字段（取消消息需要用户ID） |
| `aryn-order/.../ArynOrderCreateAfterEventListener.java` | 发布取消事件时 set orderNo、userId |

### 前端管理后台（aryn-mall-ui / web-ele）

| 路径 | 说明 |
|------|------|
| `apps/web-ele/src/api/notify/notify-template.ts` | 模板 CRUD API |
| `apps/web-ele/src/api/notify/notify-broadcast.ts` | 群发 API |
| `apps/web-ele/src/api/notify/notify-message.ts` | 消息记录查询 API |
| `apps/web-ele/src/views/notify/notify-template/index.vue` | 模板列表页 |
| `apps/web-ele/src/views/notify/notify-template/form.vue` | 模板新增/编辑表单 |
| `apps/web-ele/src/views/notify/notify-broadcast/index.vue` | 群发列表 + 发起群发对话框 |
| `apps/web-ele/src/views/notify/notify-message/index.vue` | 消息记录查看页 |

### 移动端（aryn-mall-uniapp）

| 路径 | 说明 |
|------|------|
| `src/api/notify/notifyMessage.ts` | C 端消息 API（alova） |
| `src/composables/useNotifyWebSocket.ts` | WS 客户端（连接/心跳/重连） |
| `src/sub-pages/notify/notify-list/index.vue` | 消息列表页（z-paging + wd-tabs） |
| `src/pages.json` | 注册 `notify/notify-list/index` 子页 |
| `src/pages/user/user-center/index.vue` | 个人中心新增"消息通知"入口 |
| `src/App.vue` | 启动时连接 WS + 全局未读 Badge 更新 |

---

## 二、API 端点

### C 端（需登录，Sa-Token 鉴权）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/app/notifymessage/page?notifyType=&current=1&size=20` | 分页查询消息 |
| GET | `/app/notifymessage/unread-count` | 未读消息数（按类型） |
| PUT | `/app/notifymessage/read/{messageId}` | 标记已读 |
| PUT | `/app/notifymessage/read-all?notifyType=` | 全部已读 |
| DELETE | `/app/notifymessage/{messageId}` | 删除消息 |
| WS | `/ws/notify?token=xxx` | 实时推送通道 |

### 管理端（需登录 + 权限注解）

| Method | Path | 权限 | 说明 |
|--------|------|------|------|
| GET | `/notifytemplate/page` | `notify:template:list` | 模板列表 |
| GET | `/notifytemplate/{id}` | `notify:template:info` | 模板详情 |
| POST | `/notifytemplate` | `notify:template:add` | 新增模板 |
| PUT | `/notifytemplate` | `notify:template:edit` | 修改模板 |
| DELETE | `/notifytemplate/{id}` | `notify:template:del` | 删除模板 |
| POST | `/notifybroadcast/send` | `notify:broadcast:send` | 发起群发 |
| GET | `/notifybroadcast/page` | `notify:broadcast:list` | 群发记录 |
| GET | `/notifybroadcast/{id}` | `notify:broadcast:info` | 群发详情 |
| GET | `/notifymessage/page` | `notify:message:list` | 消息记录 |

> 单体模式下，前端调用 `/admin/notify/xxx/page` 会被 `request.ts` 重写为 `/boot/xxx/page`。

---

## 三、预置消息模板（10 个）

| Code | 类型 | 触发方式 |
|------|------|----------|
| `ORDER_PAY_SUCCESS` | 订单 | RocketMQ `order-pay-success-notify-topic` |
| `ORDER_COMPLETE` | 订单 | RocketMQ `order-complete-notify-topic` |
| `ORDER_CANCEL` | 订单 | RocketMQ `order-cancel-topic`（超时未支付自动取消） |
| `REFUND_SUCCESS` | 支付 | RocketMQ `order-refund-success-notify-topic` |
| `REFUND_REJECT` | 支付 | （预留，需人工触发或扩展事件） |
| `COUPON_EXPIRE` | 营销 | （预留，需定时任务触发） |
| `PROMOTION_START` | 营销 | （预留） |
| `MEMBER_LEVEL_UP` | 系统 | （预留） |
| `BALANCE_CHANGE` | 系统 | （预留） |
| `SIGN_IN_REMIND` | 系统 | （预留） |

---

## 四、部署步骤

### 1. 初始化数据库

```bash
# 单体模式
docker exec -i dev-mysql mysql -uroot -p<密码> aryn_boot < aryn-mall-java/db/boot/5aryn_boot_notify.sql

# 微服务模式
docker exec -i dev-mysql mysql -uroot -p<密码> aryn_notify < aryn-mall-java/db/cloud/10aryn_notify.sql
```

### 2. 编译

```bash
cd aryn-mall-java
mvn clean compile -pl aryn-boot -am -P boot -DskipTests
```

### 3. 启动后端

按现有方式启动 `aryn-boot`。新增模块会随 aryn-boot 一起加载。

### 4. 启动管理后台

```bash
cd aryn-mall-ui
pnpm dev
```

新增的「消息通知」菜单需要在 `sys_menu` 表中手动注册（或通过后端权限管理界面添加）。菜单权限码：
- `notify:template:list/info/add/edit/del`
- `notify:broadcast:send/list/info`
- `notify:message:list`

### 5. 启动移动端

```bash
cd aryn-mall-uniapp
pnpm dev:mp-weixin
```

---

## 五、约束与限制

1. **群发目标**：一期仅支持「指定用户」模式（targetType=2）。「全部用户」「会员等级」「标签」需要 user 模块扩展 Dubbo 远程接口后支持。
2. **WebSocket 多实例**：当前为单实例实现。多实例部署需引入 Redis Pub/Sub 跨节点路由。
3. **未实现项**：发货通知（用户决策不做）、REFUND_REJECT/COUPON_EXPIRE 等营销/系统消息的自动触发（需扩展对应事件或定时任务）。
4. **租户隔离**：notify_message / notify_template / notify_broadcast 已加入 `hx.tenant.tables` 清单，MyBatis-Plus 拦截器自动注入 tenant_id 条件。

---

## 六、验证

后端编译已通过：
```
mvn clean compile -pl aryn-boot -am -P boot -DskipTests
[INFO] BUILD SUCCESS
[INFO] aryn-notify-api ............................. SUCCESS
[INFO] aryn-notify-biz ............................. SUCCESS
[INFO] aryn-boot ................................... SUCCESS
```

前端管理后台 type-check 仅 1 个预存在的无关错误（`@vben/types/global` 缺失，与本次改动无关）。
