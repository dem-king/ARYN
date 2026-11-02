# 秒杀每日排期与开抢提醒

## 1. 需求来源

用户对比小象超市、盒马等主流商城后提出：主流秒杀是「每天固定几场、天天都有」，当前商城是一次性活动，运营设置几点到几点后，时间一过会场就没有了，需要每天手工建场次。

目标：

- 运营配置一次「每日几点到几点 + 周几 + 每场商品/库存」，系统按天自动生成场次，天天循环。
- 当天最后一场结束后继续展示明日场次，不再空窗。
- 场次结束未售库存顺延到当日下一场；次日按模板重新生成配额。
- 未开始场次支持微信「开抢提醒」。

非目标：

- 不做商家报名/审核、SKU 疲劳期、近 7 天排期日历（平台化能力，本期不做）。
- 不改动既有一次性场次链路，存量活动继续可用。

## 2. 数据模型

新增表（boot `174seckill_daily_schedule.sql` / cloud `175seckill_daily_schedule.sql`）：

| 表 | 作用 |
|---|---|
| `seckill_session_template` | 每日排期模板：场次名、每日起止时间、生效星期（1-7） |
| `seckill_goods_template` | 排期商品模板：SKU、秒杀价、每场计划库存、每人限购 |
| `seckill_subscribe` | 开抢提醒订阅：场次、商城用户、微信模板、状态（待发送/已投递/已取消/失败） |

存量表增量列：

- `seckill_activity.schedule_type`：0 一次性场次 / 1 每日重复。
- `seckill_session.template_id`、`biz_date`：生成来源与业务日期，唯一键 `(template_id, biz_date, del_flag)` 保证幂等。
- `seckill_session.carryover_done`：未售库存是否已顺延，防止重复加库存。

## 3. 后端实现

### 3.1 场次生成

`SeckillScheduleJobHandler`（xxl-job，FIX_RATE 60s）逐租户执行：

1. `generateSessions(3)`：今天 + 未来 2 天，按「活动日期范围 × 模板生效星期 × 场次」生成 `seckill_session` + `seckill_goods`，Redis 库存 TTL = 本场结束 + 1h。
2. `carryoverEndedSessions()`：场次结束后 55 分钟内，把 Redis 剩余库存顺延到当日同活动的下一场；先 CAS 置 `carryover_done=1` 再处理，失败最多漏顺延，不重复加库存。当日最后一场的未售库存不跨天顺延，次日按模板重新生成。
3. `sendDueReminders()`：开抢前 10 分钟（可配）投递 `SECKILL_SESSION_START` 微信订阅消息；订阅行状态 0→1 抢占，多实例只发一次。

活动保存后也会在事务提交后立即生成/重建场次；重建只删除「未开始、无订单、无有效订阅」的未来场次，已有用户占位或订单的场次保留。

### 3.2 C 端接口

| 接口 | 说明 |
|---|---|
| `GET /app/seckill/sessions` | 返回今日剩余 + 明日场次；新增 `bizDate`、`dayOffset`（0 今天/1 明天）、`subscribed`、`reminderEnabled`、`reminderTemplateId`、`reminderOffsetMinutes` |
| `POST /app/seckill/subscribe/{sessionId}` | 订阅开抢提醒（需登录，场次未开始且提醒已开放） |
| `DELETE /app/seckill/subscribe/{sessionId}` | 取消订阅（已投递不可取消） |
| `GET /app/seckill/subscribe/{sessionId}` | 查询订阅状态（需登录） |

### 3.3 微信订阅消息

- 消息模板字段：`thing1`=标题、`thing2`=场次与开抢时间、`time3`=开抢时间。
- Boot：`aryn-boot/src/main/resources/application.yml` 的 `wechat.subscribe.template-map.SECKILL_SESSION_START`。
- Cloud：`aryn-message-biz` 配置（Nacos 增量 `177seckill_tenant_config_nacos.sql`）。
- 提醒开关：`aryn.seckill.reminder.template-id` 为空时 C 端隐藏提醒按钮，Job 只落库不发送。
- 商城用户 openid：`RemoteSocialUserService#getOpenidByMallUserId`，由 `social_user.mall_user_id` 反查。

## 4. 管理端

秒杀活动编辑页新增「排期方式」：

- 一次性场次：沿用原「场次 + 绝对时间 + 商品」表单。
- 每日重复：每个场次配置「场次名称 / 每日时段 / 生效星期 / 每场商品与计划库存」；每日配额 = 各场计划库存之和。活动时间范围决定排期从哪天生效、到哪天结束。

## 5. 小程序

- 场次轴展示今日剩余 + 明日场次，明日场次标注「明日 10:00 开抢」。
- 当天最后一场结束后自动选中明日场次，倒计时改为「距明日开抢」，页面不再空态。
- 未开始场次在 hero 区展示「开抢提醒」；点击后请求微信一次性订阅授权并落库，已订阅显示「已设提醒」。
- 首页活动条从「仅进行中」改为「进行中优先，否则展示下一场」，空窗期入口不消失。

## 6. 双模式与配置

- Boot：`db/boot/174seckill_daily_schedule.sql` + `175seckill_schedule_job.sql`；租户表登记在 `aryn-boot/src/main/resources/application.yml`。
- Cloud：`db/cloud/175seckill_daily_schedule.sql` + `176seckill_schedule_job.sql` + `177seckill_tenant_config_nacos.sql`；租户表与提醒/模板配置写入 Nacos。
- `aryn_boot_full.sql` 已重建，`verify-full-sql.mjs` 通过。

## 7. 验证

- 后端 `mvn test -pl aryn-boot -am`：全绿。
- 新增单测：`SeckillScheduleServiceImplTest`（生成/幂等/顺延/提醒/订阅时间校验）、`WechatSubscribeChannelServiceImplTest`（商城用户 openid + time3）。
- 小程序：`pnpm type-check` 通过；`pnpm test:unit` 134 文件 / 1461 用例全绿。
- 管理端：`pnpm check:type` 通过；改动文件 ESLint 通过；`pnpm test:unit` 1263/1264（唯一失败为导航栏磨砂预览一致性用例，`navigation-scroll.ts` 未在本需求改动）。
- 本地 Boot 库已执行 174/175 两遍（幂等），确认新表、新列与 `seckillScheduleJobHandler` 注册成功。
- 本地 Boot 容器真实链路 E2E（2026-10-30）：
  - 独立测试用户密码登录；每日活动两个连续场次由 Job 自动生成（今日 + 明日）。
  - 真实预扣下单 1 件：A 场 Redis 库存 10→9，`seckill_order` 落库。
  - A 场结束：Job 顺延 9 件到 B 场，B 场 DB/Redis 均 10→19，`carryover_done=1`。
  - 订阅 B 场提醒：`seckill_subscribe.status=1`、`send_time` 已写入；因未配置 app-id，消息模块按设计跳过真实微信发送。
  - C 端 sessions 返回今日 B（`subscribed=true`、剩余 19）+ 明日 A/B（`dayOffset=1`、剩余 10）。
  - 测试数据已清理，容器已恢复为无测试模板配置。

## 8. 风险与后续

- 真实微信订阅消息发送未实测（本地无 app-id/模板），上线前需在开发者工具与真机补测。
- 提醒链路沿用消息模块单 `app-id` 配置；多小程序租户需按租户扩展 app-id/模板映射。
- 每人限购目前按「场」计算，次日场次自然重置；若运营需要「每天跨场共享限购」，需新增按日维度的 bought key。
- 模板修改只影响未开始且无订单/订阅的未来场次；当天已生成场次保持原配置。
- 当日最后一场未售库存不跨天顺延，次日按模板重新生成配额。
