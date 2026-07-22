# 站内信与客服会话实施计划

> 执行时按本计划逐项完成测试、实现和验证；禁止创建或切换 Git worktree。实施前先阅读 `docs/50-需求文档/2026-07-21-站内信与客服会话/`，涉及认证、租户、共享实体和菜单时必须先完成 Graphify 影响检查。

**状态：** 本地实施完成，任务 1-12 已完成（2026-07-22）；待集成环境迁移、双模式 WebSocket、中间件故障恢复和容量验收。

**目标：** 新增独立消息域，交付 C 端和后台工作人员通知收件箱、群发通知、共享客服池双向会话、工作人员一对一私信、实时推送和业务通知接入。

**架构：** `aryn-message-api/biz` 作为独立业务域，MySQL 保存通知、会话和消息事实；Redis 保存客服实时状态并广播跨实例推送事件；HTTP 负责可靠发送，WebSocket 负责实时通知；RocketMQ 接收订单等业务域消息命令；XXL-JOB 恢复群发、超时会话和异常分配任务。Cloud 模式独立服务和数据库，Boot 模式聚合到 `aryn-boot`。

**技术栈：** Java 17、Spring Boot 3.5、Spring WebSocket、MyBatis-Plus、MySQL 8、Redis、RocketMQ、XXL-JOB、Dubbo、Sa-Token、Vue 3、Element Plus、UniApp、Alova、JUnit 5、Mockito、Vitest。

---

### 任务 1：建立影响基线和认证端类型契约

**文件：**
- 修改 `aryn-mall-java/aryn-common/aryn-common-security/src/main/java/com/aryn/cloud/common/security/entity/ArynUser.java`
- 修改 `aryn-mall-java/aryn-common/aryn-common-security/src/main/java/com/aryn/cloud/common/security/util/SecurityUtils.java`
- 修改 `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/LoginService.java`
- 修改 `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/TocLoginService.java`
- 新增 `aryn-mall-java/aryn-common/aryn-common-security/src/test/java/com/aryn/cloud/common/security/util/SecurityUtilsDeviceTypeTest.java`
- 新增或修改认证服务 TOB/TOC 登录测试

**步骤：**

1. 运行 `graphify explain ArynUser`、`graphify explain SecurityUtils`、`graphify path LoginService ArynUser` 并记录消费者，确认新增字段不会改变密码、权限和 token 序列化语义。
2. 写失败测试：TOB 登录后的安全上下文返回 `DeviceTypeEnum.TOB`，TOC 登录返回 `TOC`，缺失或不匹配时消息接口必须拒绝。
3. 运行目标测试，确认当前实现不能稳定获取登录端类型。
4. 为 `ArynUser` 增加独立 `deviceType` 字段；在 `loginByDevice` 前写入，或在 token session 中保存同等可靠值。禁止复用现有账号 `type`。
5. 在 `SecurityUtils` 增加空安全的当前用户、当前租户和登录端读取方法；无请求、未登录、session 缺失时返回明确失败而非空指针。
6. 重新运行安全与认证目标测试。
7. 检查 Graphify 消费者差异，确认未影响 TOB/TOC 既有登录和 Dubbo 用户对象。

### 任务 2：创建消息模块、部署入口和双模式骨架

**文件：**
- 修改 `aryn-mall-java/pom.xml`
- 新增 `aryn-mall-java/aryn-message/pom.xml`
- 新增 `aryn-mall-java/aryn-message/aryn-message-api/pom.xml`
- 新增 `aryn-mall-java/aryn-message/aryn-message-biz/pom.xml`
- 新增 `aryn-mall-java/aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/ArynMessageApplication.java`
- 新增 `aryn-mall-java/aryn-message/aryn-message-biz/src/main/resources/application.yml`
- 修改 `aryn-mall-java/aryn-boot/pom.xml`
- 修改 `aryn-mall-java/docker-compose.yml`
- 修改 `aryn-mall-java/db/cloud/1schema.sql`
- 修改 `aryn-mall-java/db/cloud/3aryn_nacos.sql`
- 修改 `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantConfigurationConsistencyTest.java`

**步骤：**

1. 写/扩展模块和租户配置测试，期望根 Reactor、Boot 聚合和 Cloud 配置中包含 `aryn-message`，当前应失败。
2. 新增 API/Biz 父子 POM；Biz 依赖 common-security、mybatis、redis、dubbo、job、swagger、RocketMQ 和 WebSocket 所需依赖，API 只保留 DTO/VO/Entity/Remote 契约所需低层依赖。
3. 创建服务启动类和 Nacos 导入型 `application.yml`，Cloud 服务名为 `aryn-message-biz`。
4. 根 POM 注册 `aryn-message`，Boot POM依赖 `aryn-message-biz`。
5. Cloud schema 新增 `aryn_message`，Nacos 新增消息服务数据源和 `hx.tenant.tables`，Gateway 增加 `/message/**` HTTP 与 WebSocket 路由。
6. Docker Compose 增加消息服务及 MySQL/Nacos/Redis/RocketMQ 依赖。
7. 将 `aryn-message-biz-dev.yml -> aryn_message` 加入租户配置一致性测试。
8. 运行 `mvn test -pl aryn-message/aryn-message-biz -am` 和 Boot 租户配置测试，确认骨架可解析。

### 任务 3：建立消息数据库、实体和索引门禁

**文件：**
- 新增 `aryn-mall-java/db/cloud/20message_center.sql`
- 新增 `aryn-mall-java/db/boot/20message_center.sql`
- 修改 `aryn-mall-java/db/boot/build-full-sql.mjs`
- 修改 `aryn-mall-java/aryn-boot/src/main/resources/application.yml`
- 新增 `aryn-mall-java/aryn-message/aryn-message-api/src/main/java/com/aryn/cloud/message/api/entity/*.java`
- 新增 `aryn-mall-java/aryn-message/aryn-message-api/src/main/java/com/aryn/cloud/message/api/enums/*.java`
- 新增 `aryn-mall-java/aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/mapper/*.java`
- 新增 Mapper XML 与 schema/索引测试

**步骤：**

1. 写 SQL 结构测试，要求八张表都有 `tenant_id`、`del_flag`、主键和审计字段，关键唯一索引存在。
2. 分别在 `aryn_message` 和 `aryn_boot` 创建 `message_notice`、`message_recipient`、`message_dispatch_task`、`message_conversation`、`message_participant`、`message_chat`、`message_agent`、`message_assignment_log`。
3. 为通知收件人建立 `(tenant_id, message_id, recipient_type, recipient_id)` 唯一索引和本人收件箱索引。
4. 为聊天消息建立 `(tenant_id, conversation_id, seq_no)` 与 `(tenant_id, sender_type, sender_id, client_message_id)` 唯一索引。
5. 为客服会话建立活跃唯一键；使用可空生成列或等效设计保证同一会员/客服池最多一个活跃会话。
6. 为工作人员私信建立参与者组合唯一键，组合值按两个工作人员 ID 排序生成。
7. 创建 MyBatis-Plus 实体、枚举和 Mapper；删除使用逻辑删除，用户隐藏使用独立收件箱状态字段。
8. 将消息表加入 Boot/Nacos 租户白名单；更新 Boot 全量 SQL构建清单并重新生成。
9. 运行 Boot 租户一致性测试、SQL 静态测试和 `node db/boot/verify-full-sql.mjs`（若脚本在执行时仍存在）。

### 任务 4：提供用户域和 UPMS 受众查询契约

**文件：**
- 新增 `aryn-mall-java/aryn-user/aryn-user-api/src/main/java/com/aryn/cloud/user/api/remote/RemoteMessageAudienceService.java`
- 新增对应 DTO/VO
- 新增 `aryn-mall-java/aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/dubbo/RemoteMessageAudienceServiceImpl.java`
- 新增用户域 Mapper 游标查询和测试
- 新增 `aryn-mall-java/aryn-upms/aryn-upms-api/src/main/java/com/aryn/cloud/upms/api/remote/RemoteMessageStaffService.java`
- 新增对应 DTO/VO
- 新增 `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/dubbo/RemoteMessageStaffServiceImpl.java`
- 新增 UPMS Mapper 游标查询和测试

**步骤：**

1. 写失败测试，覆盖当前租户内按全部、指定 ID、会员等级、会员标签、角色和部门游标分页查询；跨租户对象必须不可见。
2. 定义只暴露消息所需最小字段的游标请求和收件人快照 VO，不返回密码、手机号原文或无关权限数据。
3. 用户域实现基于主键游标的稳定分页，过滤逻辑删除和禁用对象；标签/等级条件必须在同一租户内生效。
4. UPMS 实现工作人员、角色、部门和客服资格查询；过滤禁用用户并返回稳定工作人员 ID、昵称和头像快照。
5. Dubbo 调用必须传播租户 ID；异步群发任务执行前由消息任务显式恢复租户上下文。
6. 运行 user/upms 目标测试，验证重复页、漏页、空结果和大页边界。

### 任务 5：实现通知管理、群发任务和双端收件箱

**文件：**
- 新增 `aryn-mall-java/aryn-message/aryn-message-api/src/main/java/com/aryn/cloud/message/api/dto/notice/*.java`
- 新增 `.../vo/notice/*.java`
- 新增 `aryn-message-biz/.../controller/admin/NoticeAdminController.java`
- 新增 `aryn-message-biz/.../controller/staff/StaffNoticeController.java`
- 新增 `aryn-message-biz/.../controller/app/AppNoticeController.java`
- 新增 `aryn-message-biz/.../service/impl/NoticeServiceImpl.java`
- 新增 `aryn-message-biz/.../service/impl/NoticeDispatchServiceImpl.java`
- 新增 `aryn-message-biz/.../job/NoticeDispatchRecoveryJob.java`
- 新增通知 Service/Controller/任务测试

**步骤：**

1. 写失败测试：草稿可编辑、发布后正文不可编辑、撤回保留历史、发布按目标端创建独立任务、重复执行不重复插入收件人。
2. 使用显式 DTO 校验标题、纯文本正文、目标端、受众条件、过期时间和安全跳转；拒绝任意 URL 和跨租户对象。
3. 发布事务冻结通知和受众快照，创建 `PENDING` 分发任务；事务提交后触发异步执行。
4. 分发任务按游标调用用户域/UPMS，批量写入收件人并依靠唯一索引幂等，逐批更新游标和计数。
5. XXL-JOB 扫描超时 `PENDING/RUNNING` 任务并安全恢复；超过最大重试进入 `PARTIAL_FAILED/FAILED` 并保留错误摘要。
6. 实现 C 端和工作人员通知分页、详情、未读数、单条已读、全部已读和隐藏；所有本人接口从安全上下文获取身份。
7. 已发布通知撤回后从普通收件箱隐藏，但管理审计和已读统计仍可查询。
8. 运行通知目标测试和租户隔离测试。

### 任务 6：实现会话、消息幂等、顺序和已读游标

**文件：**
- 新增 `aryn-message-api/.../dto/conversation/*.java`
- 新增 `aryn-message-api/.../vo/conversation/*.java`
- 新增 `aryn-message-biz/.../controller/app/AppConversationController.java`
- 新增 `aryn-message-biz/.../controller/staff/StaffConversationController.java`
- 新增 `aryn-message-biz/.../service/impl/ConversationServiceImpl.java`
- 新增 `aryn-message-biz/.../service/impl/ChatMessageServiceImpl.java`
- 新增会话与消息 Mapper XML
- 新增会话唯一性、顺序、幂等和已读测试

**步骤：**

1. 写并发失败测试：同一会员并发创建只生成一个活跃客服会话，相同 `clientMessageId` 只生成一条消息，会话 `seq_no` 不重复。
2. 创建或获取客服会话时先尝试读取活跃会话，再依靠数据库唯一索引处理竞态；唯一冲突后重新读取已有会话。
3. 发送接口在事务内校验参与者权限、会话状态和消息类型，锁定或条件更新会话 `last_seq`，写入消息并更新最后消息摘要。
4. `TEXT` 只保存纯文本；`IMAGE` 校验上传结果和 MIME；业务卡片只接受服务端允许的类型和字段。
5. 参与者标记已读时仅允许单调递增 `last_read_seq`，不得回退或超过会话 `last_seq`。
6. 实现基于 `beforeSeq/afterSeq` 的游标分页和断线补拉，禁止深分页 offset。
7. 为 `CUSTOMER_SERVICE` 和 `STAFF_DIRECT` 分别校验参与者组合，禁止会员私聊和工作人员群聊。
8. 运行会话、消息和权限测试。

### 任务 7：实现客服坐席、自动分配、领取、转交和 24 小时重开

**文件：**
- 新增 `aryn-message-biz/.../controller/staff/AgentController.java`
- 新增 `aryn-message-biz/.../controller/admin/ConversationAdminController.java`
- 新增 `aryn-message-biz/.../service/impl/AgentServiceImpl.java`
- 新增 `aryn-message-biz/.../service/impl/ConversationAssignmentServiceImpl.java`
- 新增 `aryn-message-biz/.../job/ConversationMaintenanceJob.java`
- 新增自动分配、并发领取、负载释放、转交和重开测试

**步骤：**

1. 写失败测试：最小负载优先、同负载最久未分配优先、无客服进入待领取、两人并发领取仅一人成功、关闭释放负载。
2. 坐席配置从 `message_agent` 读取，实时在线/忙碌/暂停状态从 Redis 读取；Redis 不可用时不进行自动在线分配。
3. 新会话创建后先尝试自动分配；候选客服必须拥有客服资格、在线、未暂停、开启自动接待且低于接待上限。
4. 分配事务条件更新会话负责人并乐观更新坐席负载；失败时重选有限次数，最终进入 `WAITING`。
5. 手动领取使用 `status = WAITING AND assigned_staff_id IS NULL` 条件更新。
6. 转交在事务内减少原客服负载、增加目标客服负载、更新负责人并写审计日志；目标客服必须属于当前租户且有容量。
7. 关闭会话时释放负载、清除活跃键、设置 `reopen_deadline = closed_time + 24h` 并写系统消息。
8. 24 小时内重开优先原客服，否则重新自动分配；超过截止时间新建会话。
9. 客服主动发起先复用活跃会话；他人负责时拒绝接管；达到上限时拒绝普通客服发起。
10. XXL-JOB 自动关闭超时会话并修复异常负载快照，所有修复必须幂等和可审计。
11. 运行分配和状态机测试。

### 任务 8：实现 WebSocket、跨实例推送和在线降级

**文件：**
- 新增 `aryn-message-biz/.../websocket/MessageWebSocketConfig.java`
- 新增 `aryn-message-biz/.../websocket/MessageHandshakeInterceptor.java`
- 新增 `aryn-message-biz/.../websocket/MessageWebSocketHandler.java`
- 新增 `aryn-message-biz/.../service/MessagePushService.java`
- 新增 Redis Pub/Sub 配置和监听器
- 修改 Gateway Nacos 路由
- 新增握手鉴权、跨租户、断线补拉和 Redis 降级测试

**步骤：**

1. 写失败测试：无 token、错误 TOB/TOC、错误租户和伪造身份的握手被拒绝。
2. 握手从 Sa-Token session读取当前用户、租户和设备类型，建立服务端连接身份，不接受客户端指定参与者。
3. HTTP 消息事务提交后发布轻量推送事件；当前实例直接推送，并通过 Redis Pub/Sub 通知其他实例。
4. 推送事件只包含消息 ID、会话 ID、序号和事件类型，不把完整敏感正文写入 Redis 日志或广播元数据。
5. 客户端确认只用于界面状态，不作为消息持久化成功依据。
6. 客服连接心跳维护 Redis TTL；断开或 TTL 过期后标记离线，不立即丢弃已分配会话。
7. Redis 故障时 HTTP 读写正常、自动分配降级到待领取、客户端通过 HTTP 补拉。
8. 验证 Cloud Gateway WebSocket 路由和 Boot 直连路径都能鉴权；将真实双模式联调列为集成环境验收。

### 任务 9：实现工作人员私信和通知回复建会话

**文件：**
- 新增 `aryn-message-biz/.../controller/staff/StaffDirectController.java`
- 新增 `aryn-message-biz/.../service/impl/StaffDirectServiceImpl.java`
- 扩展通知回复 Service
- 新增工作人员私信、组合唯一性和通知隔离测试

**步骤：**

1. 写失败测试：同一工作人员组合并发创建只产生一个活跃私信，跨租户、群聊和禁用用户请求失败。
2. 按排序后的两个工作人员 ID 生成组合唯一键；双方参与者都为 `SYS_USER`。
3. 私信不创建客服分配日志、不读写客服负载，也不进入待领取池。
4. 工作人员回复内部通知时复用与发布人的私信并插入 `NOTICE_CARD`。
5. 会员回复通知时复用或创建客服会话并插入 `NOTICE_CARD`；其他通知收件人无权访问。
6. 运行工作人员私信和通知回复测试。

### 任务 10：接入管理后台通知、铃铛、客服工作台和私信

**文件：**
- 新增 `aryn-mall-ui/apps/web-ele/src/api/message/notice.ts`
- 新增 `aryn-mall-ui/apps/web-ele/src/api/message/conversation.ts`
- 新增 `aryn-mall-ui/apps/web-ele/src/api/message/agent.ts`
- 新增 `aryn-mall-ui/apps/web-ele/src/views/message/notice/**`
- 新增 `aryn-mall-ui/apps/web-ele/src/views/message/service/**`
- 新增 `aryn-mall-ui/apps/web-ele/src/views/message/inbox/**`
- 新增 `aryn-mall-ui/apps/web-ele/src/store/message.ts`
- 修改 `aryn-mall-ui/apps/web-ele/src/layouts/basic.vue`
- 按需扩展 `aryn-mall-ui/packages/effects/layouts/src/widgets/notification/notification.vue`
- 新增 Vitest 测试

**步骤：**

1. 写失败测试：铃铛展示聚合未读、通知已读更新、客服列表状态切换、发送幂等本地态合并、WebSocket 断线补拉。
2. 为通知管理实现纯文本编辑、目标端、受众选择、预计人数、发布确认和统计页面。
3. 启用头部通知槽位，读取最近通知和聚合未读；“查看全部”进入消息中心。
4. 客服工作台实现待领取、我的会话、历史会话、消息时间线、业务卡片、会员侧栏和领取/转交/关闭操作。
5. 实现客服在线、忙碌、暂停状态和心跳；状态变化失败时回滚本地展示。
6. 实现工作人员一对一私信；不提供群聊按钮。
7. WebSocket 事件只更新索引和未读，本地消息以 HTTP 返回结果为准；按 `clientMessageId` 合并发送态。
8. 运行目标 Vitest、`pnpm check:type` 和 Prettier/ESLint 窄验证。

### 任务 11：接入 UniApp 消息中心和站内客服入口

**文件：**
- 新增 `aryn-mall-uniapp/src/api/message/notice.ts`
- 新增 `aryn-mall-uniapp/src/api/message/conversation.ts`
- 新增 `aryn-mall-uniapp/src/store/messageStore.ts`
- 新增 `aryn-mall-uniapp/src/sub-pages/message/notice/index.vue`
- 新增 `aryn-mall-uniapp/src/sub-pages/message/notice/detail.vue`
- 新增 `aryn-mall-uniapp/src/sub-pages/message/chat/index.vue`
- 新增 `aryn-mall-uniapp/src/components/message/**`
- 修改 `aryn-mall-uniapp/src/pages/user/user-center/index.vue`
- 修改 `aryn-mall-uniapp/src/sub-pages/product/goods-detail/components/GoodsFooter.vue`
- 修改订单详情和退款详情页面
- 修改 `aryn-mall-uniapp/src/components/diy/link-resolver.ts`
- 更新生成的 `aryn-mall-uniapp/src/pages.json`

**步骤：**

1. 为纯函数写失败测试：`clientMessageId` 合并、`seqNo` 游标、业务卡片路由和 `customer-service` 链接解析。
2. 实现通知/客服两个页签、个人中心未读角标、通知详情和历史会话。
3. 聊天页面支持文本、最多 6 张图片、商品/订单/退款/通知卡片和系统消息。
4. HTTP 发送前生成稳定 `clientMessageId`；成功后用服务端消息替换本地发送态，失败显示可重试状态。
5. WebSocket 断线时显示状态并继续允许 HTTP 发送；重连后从最后 `seqNo` 补拉。
6. 商品客服按钮改为站内会话入口并发送商品卡片；保留可配置灰度回退到微信原生客服。
7. 订单和退款页面增加联系客服并发送业务卡片。
8. 装修 `customer-service` 链接解析到站内客服页面，保持安全内部路由校验。
9. 运行 `pnpm type-check` 和 `pnpm build:mp-weixin`；若既有工具链阻断，记录与本需求无关的原始错误。

### 任务 12：接入业务通知、菜单权限和完整验证

**文件：**
- 新增 `aryn-message-api/.../dto/MessageSendCommand.java`
- 新增 `aryn-message-api/.../remote/RemoteMessageCommandService.java`（仅保留必要同步场景）
- 新增消息服务 RocketMQ 消费者和幂等测试
- 修改订单、支付、退款、营销的事务后事件发送点
- 新增 `aryn-mall-java/db/cloud/20message_menu.sql`
- 新增 `aryn-mall-java/db/boot/20message_menu.sql`
- 更新 Nacos、Boot 全量 SQL和菜单/租户一致性测试
- 更新 `docs/20-业务与数据/功能到代码索引.md`
- 更新 `docs/20-业务与数据/数据模型.md`
- 更新 `docs/30-端分析/*.md`
- 更新 `docs/40-接口与风险/接口映射.md`
- 实现完成后更新需求包和 `docs/90-记录归档/需求记录.md`

**步骤：**

1. 写重复事件测试：相同 `eventId` 重复投递只产生一次通知或目标会话消息。
2. 定义业务通知命令最小契约，包含 `eventId`、`tenantId`、收件人、分类、业务类型/ID和安全卡片数据。
3. 订单、支付、退款和营销只在本地事务提交后发送；不得让消息服务失败回滚核心交易。
4. 消息消费者显式恢复并最终清理租户上下文，依靠来源唯一键幂等。
5. 新增消息中心、通知管理、客服工作台和工作人员私信菜单及权限；同时处理套餐菜单与存量租户角色授权。
6. 运行 Graphify 检查认证、菜单、用户/API依赖关系，并在共享关系明显变化后更新图谱。
7. 执行全量验证：

```bash
cd aryn-mall-java && mvn test -pl aryn-boot -am
cd aryn-mall-ui && pnpm check:type && pnpm test:unit && pnpm lint
cd aryn-mall-uniapp && pnpm type-check && pnpm build:mp-weixin
git diff --check
```

8. 在集成环境执行 MySQL 迁移、Boot/Cloud WebSocket、Redis 降级、RocketMQ 重试、100,000 人群发恢复和容量测试；本地未执行不得声明通过。
9. 将实际测试结果、剩余风险和部署步骤回写需求包与需求记录。

**完成记录（2026-07-22）：**

- 任务 1-12 的后端、管理端、UniApp、菜单、SQL 和文档代码均已落地。
- 后端 Reactor 全量测试通过；管理端 typecheck、422 个用例和 lint 通过；UniApp typecheck 与 7 个用例通过。
- 微信小程序构建受当前 `node_modules` 中 npm 可选依赖原生 binding 缺失阻断，未执行破坏性依赖重装。
- Graphify 在本轮环境不可用，使用源码搜索、依赖编译、租户配置/拦截绕过审计及全量测试替代影响检查。
- 真实 MySQL、Redis、RocketMQ、XXL-JOB、Cloud/Boot WebSocket 和容量验收仍需集成环境执行。

## 建议提交拆分

每个提交只包含一个可验证主题，提交标题和正文使用中文：

1. `新增消息域模块与数据库骨架`
2. `补齐消息身份与受众查询边界`
3. `实现通知发布与双端收件箱`
4. `实现客服会话与自动分配`
5. `实现实时推送与工作人员私信`
6. `接入管理后台消息中心与客服工作台`
7. `接入移动端消息中心与客服会话`
8. `接入业务通知并完善菜单与文档`

提交前只暂存本任务文件，禁止把当前工作树中用户已有修改带入提交。
