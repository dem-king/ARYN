# 货到付款收款预警（收货超时未收款站内信提醒）

> 需求日期：2026-10-03
> 范围：后端（order-biz / upms）+ 管理端订单配置表单；C 端与管理端消息收件箱复用现有设施，零新增页面。
> 目标：货到付款（COD）订单收货后超过设定时长仍未被管理端「确认收款」时，
> 自动向买家与租户管理员发送站内信提醒，避免线下收款长期悬空。

---

## 一、需求澄清

- 系统内**没有独立的「线下支付」支付方式**：`payment_type` 只有 0（0元）/1（微信）/2（支付宝）/
  3（货到付款），管理端「确认收款」接口即 `confirmOfflinePayment`。本次预警口径收敛为
  **`payment_type=3` 且未确认收款（pay_status=0）的订单**。
- 提醒对象：**买卖双端都发**——买家（MALL_USER，催付款）+ 租户管理员（SYS_USER，催确认收款）。
- 时间锚点：**收货时间 `receiver_time` 起算**（不是下单时间——COD 仅限商城配送/内部配送，
  配送可耗时数日，下单起算会提前轰炸）；默认 **收货后 72h / 168h 两轮**，租户可配。

## 二、口径定案

- 扫描条件：`payment_type='3' AND pay_status='0' AND status='4'（已完成）AND receiver_time IS NOT NULL`。
  COD 允许未收款先确认收货，「已完成未收款」正是催收重点；退款中/已取消订单不提醒。
- 轮次语义：配置 `cod_pay_remind_hours="72,168"` 表示收货 72 小时发第 1 轮、168 小时发第 2 轮；
  逗号分隔升序去重，非法片段忽略；**空串=关闭该租户提醒**；null（存量 Redis 缓存无此字段）按默认 72,168 处理。
- 幂等去重：不建新表、订单不加字段。轮次 `eventId = cod-pay-remind:{buyer|admin}:{orderId}:R{n}`，
  复用站内信 `uk_message_notice_source(tenant_id, source_type, source_key)` +
  `message_recipient` 收件人唯一键双重幂等——任务反复调度只会补发「应到而未到」的轮次。
- 单轮多收件人共享一条通知（买家与管理员 eventId 不同，文案各自定制；管理员之间共享）。
- 同轮文案：买家「货到付款订单待付款」（含订单号/金额/收货时间/已收货小时数，jumpPayload 对齐
  订单创建通知 `{"bizType":"ORDER","bizId":...}`）；管理员「货到付款订单收款提醒」（含第 N 轮）。
- 管理端收件人 = 该租户 `ROLE_ADMIN` 角色的正常状态员工（租户创建时引导生成的「系统管理员」）；
  `RemoteMessageStaffService.queryRecipientsByRoleCode` 游标分页解析，无匹配角色时仅发买家侧。
- 通道：仅站内信（IN_APP）；每租户每轮扫描上限 500 单（按收货时间升序，先催最久的），
  扫描窗口 = 最大轮次 + 7 天追补缓冲，调度停摆期间的漏发仍能补，更早的历史单不再骚扰。

## 三、落地清单

### 后端

| 改动 | 文件 |
|------|------|
| 配置字段 `codPayRemindHours` | `aryn-order-api/.../entity/OrderConfig.java` |
| 按角色码查工作人员（新 Dubbo 方法） | `aryn-upms-api/.../remote/RemoteMessageStaffService.java`、`aryn-upms-biz/.../dubbo/RemoteMessageStaffServiceImpl.java` |
| 预警服务（阈值解析/扫描/轮次计算/双端发送） | `aryn-order-biz/.../service/ICodPayRemindService.java`、`impl/CodPayRemindServiceImpl.java` |
| XXL-JOB 处理器 `codPayRemindJobHandler`（遍历租户 + 异常隔离） | `aryn-order-biz/.../job/CodPayRemindJobHandler.java` |

发送链路完全复用现有设施：`MessageSendCommand` → RocketMQ `message-send-command-topic` →
message-biz 落库 + WebSocket 推送；category=ORDER、bizType=COD_PAY_REMIND。

### DB（双模式增量，information_schema / executor_handler 守卫幂等）

| 模式 | 脚本 | 内容 |
|------|------|------|
| boot | `db/boot/110cod_pay_remind_config_incremental.sql` | `aryn_boot.order_config` 加列，`NOT NULL DEFAULT '72,168'` |
| boot | `db/boot/111cod_pay_remind_job.sql` | `aryn_boot_job.xxl_job_info` 注册 codPayRemindJobHandler（FIX_RATE 3600s，起调） |
| cloud | `db/cloud/111cod_pay_remind_config_incremental.sql` | `aryn_order.order_config` 加列（cloud order 服务数据源为 aryn_order，见 Nacos） |
| cloud | `db/cloud/112cod_pay_remind_job.sql` | `aryn_job.xxl_job_info` 注册同任务（沿用 job_group=1） |

`aryn_boot_full.sql` 已重建并通过 `verify-full-sql.mjs`（verify required 断言补了
`ADD COLUMN cod_pay_remind_hours` 与 `'codPayRemindJobHandler'`）。

### 管理端

- `views/order/config/form.vue`：订单配置弹窗新增「货到付款收款提醒（小时）」输入
  （默认 72,168，格式校验逗号分隔数字，留空=关闭），走既有 add/edit 接口与缓存失效链路。

## 四、边界与说明

- **只提醒、不自动取消**：超时 COD 订单不做任何状态变更（与订单取消 job 的 30 分钟口径互不干扰，
  取消 job 只扫 `status=1` 待付款单，COD 单不在其列）。
- 管理端收件箱/铃铛当前不消费 `jumpPayload`（只存不用，与配送派单通知同状况），
  提醒落地为消息卡片，点击进收件箱已读。
- 审计：消息落库后可在管理端「通知管理」以分类 ORDER / 来源 COD_PAY_REMIND 溯源；
  任务日志经 `XxlJobHelper.log` 记录每租户发送条数。
- 配置读取走 `OrderConfigServiceImpl.getConfig()` 的 Redis 缓存（按租户 key），
  管理端保存配置即失效缓存，下轮调度生效。

## 五、验证

- `mvn test -pl aryn-boot -am` 全量 172 个测试类 933 用例 BUILD SUCCESS；
  新增 `CodPayRemindServiceImplTest` 8 例，`RemoteMessageStaffServiceImplTest` 扩至 4 例
  （新 Dubbo 方法两条路径 + 兼容原构造签名变更）。
- 管理端 `check:type` 通过（顺带修复 order-info 确认收款弹窗存量 type 错误后转绿）；
  改动文件 eslint/prettier 干净。
- `aryn_boot_full.sql` 重建并 `verify-full-sql.mjs` 通过（20268 行 / 1653257 字节）。

## 六、已知关联问题（本次未改）

- `db/cloud/110cod_pay_voucher_incremental.sql`（COD 凭证增量）`USE aryn_upms`，
  但 cloud 模式 order 表在 `aryn_order` 库（`7aryn_order.sql` 基线与 Nacos 数据源均为 aryn_order）。
  若该脚本曾在 cloud 库按字面执行会报表不存在；本次 111/112 号脚本已按 aryn_order/aryn_job 正确落库，
  110 号是否需要修正由维护者确认。
