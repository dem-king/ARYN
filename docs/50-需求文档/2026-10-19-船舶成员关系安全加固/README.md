# 船舶成员关系安全加固（P0）

> 日期：2026-10-19 ｜ 状态：**已实施，自动化验证全绿（未做集成环境联调）**
> 起因：管理端「绑定申请」审核弹窗里，小程序申请的「悦航8号」被绑定到了「悦航7号」，
> 全程无提示、无校验、无留痕。追查后确认这不是单点缺陷——`vessel_member`（船舶成员关系）
> 有 **5 条写入通道**，各自信任级别不同，安全水位参差不齐。

## 一、问题域

`vessel_member` 不只是「谁在这条船上」的展示数据，它是多个下游域的数据权限基线：

| 能力 | 依据 | 代码 |
|---|---|---|
| 查看该船成员列表（昵称 + 脱敏手机号） | `requireMembership` | `VesselBindServiceImpl.listMembers` |
| 查看/申报该船靠港计划 | `requireMembership` | `VesselServiceImpl.availableCalls` / `declareCall` |
| 以该船身份下内部配送单 | `isVesselMember` | `DeliveryContextValidator#validate` |
| 参与/管理该船共享购物车 | 船舶成员身份 | `SharedCartServiceImpl` |

一条误绑 = 把另一条船的采购、配送与人员信息交给不相关的人。

### 1.1 五条写入通道（现状）

| # | 入口 | 信任级别 | 缺陷 |
|---|---|---|---|
| 1 | `approveApply` 审核通过 | 运营 | 目标船与申请船**零比对**；`memberRole` 取自 DTO **无白名单**（可传 `"1"` 发起人）；已有单测把「随便写的船名→绑别的船」钉成合法 |
| 2 | `redeemInviteCode` 邀请码 | 持码人 | 角色固定船员，尚可；无来源标记 |
| 3 | `addMember`（C 端船管拉人） | 船管角色 | `memberRole` 取自 DTO **无白名单** |
| 4 | `VesselServiceImpl.addMember`（管理端） | `vessel:member:save` | 角色取自原始实体**无白名单**；`requireVessel` **不校验船舶在营** |
| 5 | `bindMemberByShare` 分享链接 | **任何拿到链接的人** | 自动补建成员，**无审批、无提示、不可追溯** |

### 1.2 一致性缺陷

- `ensureOnboard` 对已存在的成员直接返回 `ALREADY_ONBOARD`，**不更新角色**：申请单记的是
  「采购确认人」，库里仍是「普通船员」，而站内信按申请角色告知用户 → 三方口径不一致。
- 审核通过 / 解绑**没有任何审计留痕**，只有一行 `log.info`。
- `assistant` 相关：`approveApply` 的 `memberRole` 可从请求体注入任意值（越权面）。

## 二、决策（用户已确认）

### 2.1 跨船绑定：强键或同名放行，其余强制确认 + 必填理由 + 留痕

判据是**客观身份键**，不是船名：

| 条件 | 处理 |
|---|---|
| 目标船 IMO = 申请单 IMO（均非空） | 放行，来源 `APPLY_AUDIT` |
| 目标船呼号 = 申请单呼号（均非空） | 放行，来源 `APPLY_AUDIT` |
| 船名归一化后相等 | 放行，来源 `APPLY_AUDIT` |
| 以上都不满足 | 必须 `confirmVesselMismatch=true` **且** `auditRemark` 非空，否则拒绝；来源 `APPLY_AUDIT_MISMATCH` |

理由：船改名、换旗、租船、申请人写法差异都是合法的「船名不同但确实是同一条船」；
而 IMO/呼号一致时任何船名差异都不该拦。反过来，双强键不命中却改绑，必须人工背书并留痕。

### 2.2 分享链接自动绑船：保留，但显式标记 + 可回收 + 明确提示

不取消（保住「分享即用」体验），改为：

- 成员行写入 `bind_source = SHARE_CART`；
- 管理端船舶成员列表显示来源标签，可按来源识别并「回收」；
- C 端加入成功提示中明确告知「已同时加入本船成员」。

## 三、契约（所有并行工作流以此为准）

### 3.1 数据库：`vessel_member` 增列

```sql
`bind_source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '绑定来源：APPLY_AUDIT/APPLY_AUDIT_MISMATCH/INVITE_CODE/MEMBER_ADD/ADMIN_ADD/SHARE_CART/MANUAL',
`bind_reason` varchar(500) DEFAULT NULL COMMENT '绑定补充原因（审核偏离理由等）'
KEY `idx_vessel_member_bind_source` (`tenant_id`, `vessel_id`, `bind_source`)
```

枚举常量类：`com.aryn.cloud.vessel.api.constant.VesselMemberBindSource`

| 常量 | 值 | 含义 |
|---|---|---|
| `MANUAL` | `MANUAL` | 历史/未知（DB 默认值，仅兜底） |
| `APPLY_AUDIT` | `APPLY_AUDIT` | 绑定申请审核通过（身份一致） |
| `APPLY_AUDIT_MISMATCH` | `APPLY_AUDIT_MISMATCH` | 绑定申请审核通过，但申请船与目标船不一致（已确认 + 有理由） |
| `INVITE_CODE` | `INVITE_CODE` | 邀请码加入 |
| `MEMBER_ADD` | `MEMBER_ADD` | 船内管理角色（C 端）添加成员 |
| `ADMIN_ADD` | `ADMIN_ADD` | 管理端直接绑定成员 |
| `SHARE_CART` | `SHARE_CART` | 分享购物车链接自动补建 |

提供 `ALL`（List<String>）、`isValid(String)`、`label(String)`（未知回退 `手工/历史`）。

### 3.2 数据库：新增审计表 `vessel_member_audit_log`

```sql
id varchar(32) PK,
tenant_id varchar(32) NOT NULL,
vessel_id varchar(32) NOT NULL,
user_id varchar(32) NOT NULL,
action varchar(32) NOT NULL,          -- BIND / REBIND / ROLE_CHANGE / UNBIND
bind_source varchar(32) DEFAULT NULL,
old_member_role char(2) DEFAULT NULL,
new_member_role char(2) DEFAULT NULL,
reason varchar(500) DEFAULT NULL,
operator_id varchar(32) DEFAULT NULL,
operator_name varchar(64) DEFAULT NULL,
create_by/update_by/create_time/update_time/del_flag（与 AbstractVesselEntity 一致）
KEY idx_vessel_member_audit_target (tenant_id, vessel_id, user_id, create_time),
KEY idx_vessel_member_audit_action (tenant_id, action, create_time)
```

追加写，不提供业务更新。实体：`com.aryn.cloud.vessel.api.entity.VesselMemberAuditLog`
（继承 `AbstractVesselEntity`），Mapper：`com.aryn.cloud.vessel.mapper.VesselMemberAuditLogMapper`。

### 3.3 常量：`com.aryn.cloud.vessel.api.constant.VesselMemberRole`

```java
OWNER="1"; CREW="2"; CONFIRMER="3"; SALES="4";
List<String> ASSIGNABLE = List.of(CREW, CONFIRMER, SALES);  // 任何写入通道只允许这三个
static boolean isAssignable(String role);   // 非 null 且属于 ASSIGNABLE
static String label(String role);           // 未知回退 "成员"
```

> `VesselMember` 实体里既有的 `ROLE_*` 常量**保留不动**（避免大面积改动），
> 新代码统一引用 `VesselMemberRole`，两者取值相同。

**角色白名单必须在 `VesselMemberRegistrar` 内兜底**（所有 5 条通道最终都过它），
各业务入口再加一层带清晰报错的校验。

### 3.4 `VesselMemberRegistrar` 行为变更

签名（新增最后一个参数）：

```java
public OnboardResult ensureOnboard(String tenantId, String vesselId, String userId, String memberRole,
        String canEdit, String canConfirm, String remark, String bindSource)
```

| 现状 | 新行为 |
|---|---|
| 已存在成员 → `ALREADY_ONBOARD`，不写库 | 角色相同 → `ALREADY_ONBOARD`（不写库）；**角色不同 → 更新角色/canEdit/canConfirm，写 `ROLE_CHANGE` 审计，返回新枚举 `ROLE_UPDATED`** |
| 复活历史行 → `REVIVED` | 同前，额外写 `REBIND` 审计 |
| 新建 → `CREATED` | 同前，额外写 `BIND` 审计 |

- 落库时同步写 `bind_source` / `bind_reason`（`bind_reason` = 传参 `remark` 中的业务理由，
  审核偏离理由单独由调用方传 `remark`）。
- 角色非法（不在 `ASSIGNABLE`）→ 抛 `ArynBusinessException("成员角色不合法：" + role)`。

### 3.5 `VesselBindAuditDTO` 新增字段

```java
@Schema(description = "通过时：已确认「申请船名与所选船舶不一致」（不一致时必须为 true）")
private Boolean confirmVesselMismatch;
```

规则（`approveApply` → `resolveVesselForApprove`，仅当 `matchedVesselId` 非空时触发）：

```
target = requireVesselActive(tenantId, matchedVesselId)
matched = (apply.imo 非空 且 equals(apply.imo, target.imoCode))
       || (apply.callSign 非空 且 equals(apply.callSign, target.callSign))
       || normalize(apply.applyVesselName).equals(normalize(target.vesselName))
if (!matched):
    若 confirmVesselMismatch != TRUE:
        throw "申请船名「{申请名}」与所选船舶「{目标名}」不一致。请确认确实是同一条船后勾选确认，并填写审核意见说明原因"
    若 auditRemark 空白:
        throw "申请船名与所选船舶不一致时，必须填写审核意见说明原因"
```

`normalize` 复用既有 `VesselBindServiceImpl#normalizeVesselName`（全角转半角、去空白/分隔符、
转小写、去尾部「号」）。命中与否决定 `bindSource`：`APPLY_AUDIT` / `APPLY_AUDIT_MISMATCH`。
`bind_reason` 取 `auditRemark`。

新建船舶路径、自动匹配路径 → `APPLY_AUDIT`。

### 3.6 `VesselBindApplyVO` 新增字段

```java
@Schema(description = "申请船名与最终绑定船名不一致（归一化后仍不同）")
private Boolean vesselMismatch;
```

在 `toApplyVOs` 里计算：仅 `matchedVesselName` 非空时判定，调用同一 `normalizeVesselName`。

### 3.7 `VesselMemberVO` 新增字段

```java
@Schema(description = "绑定来源：APPLY_AUDIT/APPLY_AUDIT_MISMATCH/INVITE_CODE/MEMBER_ADD/ADMIN_ADD/SHARE_CART/MANUAL")
private String bindSource;
@Schema(description = "绑定来源中文名")
private String bindSourceLabel;
@Schema(description = "绑定补充原因")
private String bindReason;
```

### 3.8 新增接口：管理端回收成员

```
POST /vessel/admin/{id}/members/{userId}/unbind
权限：vessel:member:save（与所在页面一致）
Body: { "reason": "..." }
```

委托既有 `IVesselBindService.adminUnbindMember(tenantId, adminUserId, vesselId, userId, reason)`，
不重复实现解绑逻辑。返回 `Result<Void>`。

### 3.9 分享加入返回体（order 域）

`SharedCartJoinVO`（`aryn-order-api/.../api/vo/SharedCartJoinVO.java`）：

```java
String cartId;          // 购物车 ID
String vesselName;      // 船舶名（提示用，可为 null）
Boolean vesselMemberBound;  // 本次是否自动补建了船舶成员关系
```

- `ISharedCartService.joinByShareToken` 返回值 `SharedCart` → `SharedCartJoinVO`
- `AppSharedCartController#joinByToken` 返回 `Result<SharedCartJoinVO>`
- 补建失败（fail-open）时 `vesselMemberBound=false`，加入本身仍成功。

### 3.10 通知文案（申请审核通过）

`notifyApplyResult` 在「申请船名 ≠ 实际绑定船名」时必须说清两者，不能把实际绑定船说成「您申请的」：

```
已通过且一致：您申请的「悦航8号」已绑定成功 / 已加入「悦航8号」
已通过但不一致：您申请的「悦航8号」已通过审核，实际绑定「悦航7号」。原因：{auditRemark}。
```

## 四、改动清单与写入范围

| 工作流 | 范围 |
|---|---|
| `vessel-api-sql` | `aryn-vessel-api/**`（新增常量/实体/VO/DTO 字段）、`db/boot/132*`、`db/cloud/133*`、`db/boot/build-full-sql.mjs`、`db/boot/aryn_boot_full.sql` |
| `vessel-biz` | `aryn-vessel-biz/**`（registrar/service/controller/mapper + 测试）、`aryn-order-api/.../vo/SharedCartJoinVO.java`、`aryn-order-biz/**`（shared cart 三文件 + 测试） |
| `admin-ui` | `aryn-mall-ui/apps/web-ele/src/views/vessel/**`、`src/api/vessel/vessel.ts` |
| `miniapp-ui` | `aryn-mall-uniapp/src/sub-pages/vessel/**`、`src/sub-pages/order/shared-cart/detail.vue`、`src/api/vessel.ts`、`src/api/order/sharedCart.ts`、`src/utils/vessel-bind-apply.ts` |

## 五、验收标准

1. 后端：`mvn clean compile -pl aryn-boot -am` 通过；vessel/order 域单测全绿（含新增一致性门槛、
   角色白名单、来源标记、角色变更、审计留痕用例）。
2. 管理端：`pnpm check:type`、`pnpm test:unit`、目标文件 eslint 通过；审核弹窗在船名不一致时
   拦截并要求确认 + 必填理由。
3. 小程序：`pnpm type-check`、`pnpm test:unit` 通过；「我的申请」显示实际绑定船与不一致标记；
   分享加入成功提示包含「已同时加入本船成员」。
4. 数据库：boot/cloud 各一份增量 SQL，可重复执行；`aryn_boot_full.sql` 重新生成并通过
   `verify-full-sql.mjs`。
5. 双模式：无新增硬编码 `/boot`；新增接口路径首段为 `vessel`/`order` 域。
