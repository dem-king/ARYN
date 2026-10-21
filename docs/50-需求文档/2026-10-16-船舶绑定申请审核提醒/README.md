# 船舶绑定申请待审核提醒（管理端铃铛 + 声音 + 语音播报）

> 提出：2026-10-16 ｜ 完成：2026-10-18 ｜ 状态：**已实施并验证**
> 承接需求：[订单事件工作人员提醒与配送端消息中心](../2026-10-16-订单提醒与配送端消息中心/README.md)
> （复用同一套「站内信 + MQ + `/message/ws/staff` + 浏览器未读上升沿提醒」链路）

## 一、问题

在配送端/商城小程序里提交「船舶绑定申请」后，管理端完全无感：

- **申请人**侧：审核通过 / 驳回已有站内信（上一轮改造补的）。
- **审核人（运营）**侧：新申请到达时没有任何通知。运营不主动刷新
  「船供运营 → 绑定申请」列表就不知道有人提交，铃铛不亮、没有提示音、没有语音播报。
- 用户截图：管理端「绑定申请」列表里已有一条待审核申请（普通船员 / 悦航7号），
  但页面在后台时没有任何提醒。

这是「订单事件提醒」的同类缺口：业务事件已经落库，但通知只发给了事件结果相关方，
没有发给**需要处理该事件的人**。

## 二、方案要点

### 收件人口径：按权限码，而不是按角色码

审核入口与接口由菜单权限 `vessel:bindapply:audit` 控制（`db/{boot,cloud}/64vessel_bind_admin_menu.sql`），
授权是按「船供运营」父菜单的既有角色授权推导补授的，可能挂在运营角色而非 `ROLE_ADMIN` 上。
**只发 ROLE_ADMIN 会在角色拆分后静默漏人**，因此按「谁真正持有审核权限」筛收件人。

**回落**：权限查询为空时回落租户管理员（`queryRecipientsByRoleCode` + `CommonConstants.ROLE_ADMIN_CODE`）。
理由是新租户/未执行菜单授权脚本时审核权限尚未授出，若此时直接跳过，又会回到
「船员提交了申请但运营完全不知道」的原问题；管理员收到后可补授权或直接处理。
权限已授出时不走回落，避免打扰无关管理员。

为此把 upms 的工作人受众查询泛化了一层：

- `StaffMessageAudienceRequest` 新增 `permissions`（菜单权限码列表，命中任一即算受众）。
- `RemoteMessageStaffService` 新增 `queryRecipientsByPermission(tenantId, permissions)`
  （`*-api` 接口，`@DubboService` 实现，boot injvm / cloud Dubbo 同口径）。
- `SysUserMapper.xml` 的受众 SQL 增加 `EXISTS` 子查询：
  `sys_user_role → sys_role_menu → sys_menu.permission`，按传入权限码过滤，
  与既有 `customerServiceOnly` 的 EXISTS 写法同构；分页游标复用 `queryRecipients`
  （1000/页），租户上下文强校验（不匹配直接抛业务异常）。

### 通知时机与幂等

- 挂在 `VesselBindServiceImpl.submitApply` 落库之后：
  `TransactionalMqUtils.sendAfterCommit(() -> notifyReviewers(tenantId, apply))`。
  事务回滚时不留「申请没落库却通知了运营」的误报。
- 收件人解析收敛到 `queryReviewers`：先按审核权限筛，空则回落租户管理员，
  两者皆空才跳过；查询异常只记 warn，申请照常提交。
- 每个审核人一条 `MessageSendCommand`，`eventId = vessel-bind-apply:todo:{申请单ID}`；
  站内信按 `(source_type, source_key)` 幂等，**同一申请对多个审核人只落一条 notice、
  每人一条 recipient**，不会重复提醒。
- 收件人查询失败 / 单人发送失败 **只记 warn 并继续**：通知是旁路，不能反过来拖垮申请提交。

### 文案与跳转

| 项 | 值 |
|----|----|
| category | `VESSEL_BIND_TODO` |
| bizType（= 站内信 `source_type`） | `VESSEL_BIND_APPLY_TODO` |
| 标题 | 普通船员：「有新的船员绑定申请」；业务员认领：「有新的业务员认领船舶申请」 |
| 正文 | 申请人姓名、船名、联系电话、IMO/呼号、常靠港口、船上职务、补充说明、申请单号 |
| 跳转 | `{"route":"/vessel/bind-apply"}`（JSON 对象，裸串会被消息消费端校验拒绝） |

### 管理端提醒

`apps/web-ele/src/composables/use-message-alert.ts` 的 `buildSpeech` 新增
`VESSEL_BIND_APPLY_TODO` 分支：

- 单条：沿用标题（标题已区分船员申请 / 业务员认领，换成通用话术会丢信息）。
- 多条：合并为「有 N 条船舶绑定申请待审核」，不逐条朗读（4 秒节流既有逻辑）。

提示音、桌面通知、音频手势解锁**无需改动**——它们由「未读上升沿」统一触发，
与事件类型无关；`source_type` 由消息域按 `bizType` 原样下发（`notice.setSourceType`），
收件箱 SQL 也已返回该列。

## 三、改动清单

### 后端

| 模块 | 文件 | 改动 |
|------|------|------|
| upms-api | `StaffMessageAudienceRequest` | 新增 `permissions` 字段 |
| upms-api | `RemoteMessageStaffService` | 新增 `queryRecipientsByPermission` |
| upms-biz | `RemoteMessageStaffServiceImpl` | 按权限码分页查收件人 |
| upms-biz | `mapper/SysUserMapper.xml` | 受众 SQL 增加权限 EXISTS 子查询 |
| vessel-biz | `VesselBindServiceImpl` | `submitApply` 提交后通知审核人；新增 `notifyReviewers` / `queryReviewers`（权限优先，无人持权限回落管理员） |
| vessel-biz | `pom.xml` | 显式声明 `aryn-upms-api`（此前只靠 message-api 传递依赖） |

### 管理端

| 文件 | 改动 |
|------|------|
| `use-message-alert.ts` | `buildSpeech` 新增 `VESSEL_BIND_APPLY_TODO` 话术 |
| `use-message-alert.test.ts` | 新增语音文案用例（来源区分 + 多条合并） |

### 测试

| 模块 | 新增用例 |
|------|----------|
| vessel-biz `VesselBindServiceTest` | 5 例：按权限通知（2 收件人）、业务员标题区分、无权限人回落管理员、权限与管理员皆空跳过、收件人查询失败不影响提交 |
| upms-biz `RemoteMessageStaffServiceImplTest` | 3 例：按权限传参并汇总、权限码空白返回空、跨租户拒绝 |
| web-ele `use-message-alert.test.ts` | 1 例：绑定申请语音文案 |

## 四、验证结果

- **后端**：`mvn -o clean compile -pl aryn-boot -am` 通过。
  `mvn -o test -pl aryn-vessel/aryn-vessel-biz,aryn-upms/aryn-upms-biz -am` 通过
  （upms 71 条、vessel 101 条，含依赖模块全绿）。
- **管理端**：`vue-tsc --noEmit` 通过；`pnpm test:unit` 87 文件 / 657 条全绿；
  改动文件 eslint + prettier 干净。
- **数据库**：**无结构变更、无新增增量 SQL**。复用的 `vessel:bindapply:audit`
  菜单权限在 boot / cloud 两份 64 号脚本中均已存在并已重新生成进 `aryn_boot_full.sql`。
- **双模式**：没有新增 Controller 路径；`RemoteMessageStaffService` 定义在 `*-api`、
  实现为 `@DubboService`，boot 单体 injvm 与 cloud Dubbo 均可注入（与 COD 收款提醒同口径）。
- **测试环境清理**：工作树中另有一处并行改造遗留的陈旧用例（引用已被删除的
  `adminListMembers` 死代码）导致 vessel 测试编译失败，已清掉；与本需求无关。

## 五、剩余风险

1. **收件人兜底**：审核权限无人持有时回落租户管理员（日志 `绑定申请无审核权限收件人，回落租户管理员`）；
   权限与管理员都为空才跳过（日志 `绑定申请无可用收件人`），申请本身不受影响。
   上线前仍建议在「角色管理」核对 `vessel:bindapply:audit` 的实际授权。
2. **未做真实浏览器听觉验收**：语音音色取决于操作系统；页面需处于打开状态且
   用户手势已解锁音频（既有逻辑），否则只有铃铛数字变化。
3. 通知跳转落管理端「绑定申请」页面；C 端申请人收到的审核结果通知维持原样（跳绑定页）。
4. **cloud 模式发布顺序**：`RemoteMessageStaffService` 新增了 Dubbo 接口方法，
   vessel（消费方）与 upms（提供方）需同版本发布；先发 upms 再发 vessel 可避免
   Consumer 找不到新方法（boot 单体模式无此约束）。
