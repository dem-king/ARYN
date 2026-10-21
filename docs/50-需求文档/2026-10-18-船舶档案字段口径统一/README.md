# 船舶档案字段口径统一（P0）

> 日期：2026-10-18 ｜ 状态：**已实施（自动化验证通过，未做集成环境联调）**
> 起因：管理端「船舶档案」新增弹窗有 9 个字段，小程序「绑定申请」只有 8 项且与档案只有船名重叠，
> 中间还有第三个入口「审核弹窗新建船舶」只能填 4 项。三处口径不一致，问「是否需要在小程序端增加字段」。

## 一、结论：不加档案字段，先修数据正确性

判断标准不是「表单长得像不像」，而是四条：

| 判断标准 | 事实 | 结论 |
|---|---|---|
| 该字段 C 端有消费场景吗？ | `imoCode/callSign/vesselNameEn/vesselType/flagState/dwt/crewCapacity` 在 `aryn-mall-uniapp/src` **全仓 0 命中** | 无消费 → 不该采集 |
| 权威来源是用户本人吗？ | 载重吨/船旗国/定员属船舶登记信息，地推现场的船员给不出可靠值 | 非用户权威 → 不该由 C 端录入 |
| 填错有下游后果吗？ | IMO 错 → 认船失败/重复建档；载重吨错 → 仅影响展示 | 只有身份键必须准 |
| 提交后能改吗？ | 小程序**无档案页、无 vessel 写接口**（`api/vessel.ts` 全是读+绑定） | 一次填定、错了无解 → 更不该加 |

因此本次**不向小程序增加档案字段**，只把「同一个概念在三端口径打架」的四处缺陷修正。

## 二、字段归属（本次确立的口径）

| 层 | 表 | 职责 | 字段 |
|---|---|---|---|
| 认船层 | `vessel_bind_apply` | 证明「我是哪条船的人」+ 让运营能联系到人 | 船名、**IMO**、**呼号**、常靠港、姓名、电话、职务、说明 |
| 关系层 | `vessel_member` | 这个人在船上是谁 | 成员角色、在船状态、权限开关 |
| 主数据层 | `vessel_info` | 运营维护的船舶档案 | 名称、英文名、IMO、呼号、船型、船旗国、载重吨、定员、状态、备注 |

**规则**：每个字段只允许一个权威写入方；C 端只写认船层，规格字段一律归管理端。

## 三、本次修复的四个缺陷（P0）

### 1. IMO 与呼号分列采集

**问题**：申请单只有一个 `apply_vessel_imo`（注释「IMO 或呼号」），小程序是一个输入框。
审核时靠 `/^\d{7}$/` 猜是哪个（`bind-apply/index.vue` 旧实现），猜中才预填进 IMO 列。
而 IMO/呼号是审核预检里**最高优先级的认船强身份键**（高于船名），命中即建议直接绑定——
入口靠猜，强身份键必然失真：

- 7 位纯数字的呼号 → 被当成 IMO 塞进 IMO 列；
- 填的其实是呼号，却按 IMO 列报「已存在」，审核员看到误导性结论；
- 预检把「同一个值」同时比 `imo_code` 和 `call_sign`，会出现「你填的是呼号，却命中了某条船的 IMO」。

**修复**：新增 `vessel_bind_apply.apply_vessel_call_sign`；小程序拆两个输入框；管理端列表拆两列、
预填各取各的；服务端预检与自动匹配改为 **IMO 只比 `imo_code`、呼号只比 `call_sign`**，
自动匹配优先级 IMO → 呼号 → 船名。删除「7 位数字猜 IMO」启发式。

**存量数据**：不回填自动拆分——原值本就是「用户填的一串标识」，按位数回填反而可能写错。
存量单在管理端仍按「IMO 列有值」展示。

### 2. 空串/trim 归一化（修一个未爆的 1062）

**问题**：`vessel_info` 上有唯一键 `uk_vessel_info_imo(tenant_id, imo_code)`，
MySQL 唯一索引**允许多个 NULL、不允许多个 `''`**。而全局 MyBatis 配置是
`insert-strategy: not_empty`（新增时空串被省略成 NULL）+ `update-strategy: not_null`（修改时空串真的写进库）。
管理端修改弹窗提交的是全量表单，未填的 IMO 是 `''` →
**连续修改两条没有 IMO 的船，第二条必然撞键 1062**（试点数据 5 条船里有 3 条 IMO 为空）。

**修复**：`saveVessel` / `updateVessel` 增加 `normalizeVessel()`：文本字段 trim，空白转 null；
船型空白回落缺省值。另外补齐「修改时不校验船名」的缺口——此前 update 能把 NOT NULL 的
`vessel_name` 改成空串。

**已知限制**（既有行为，未在本次改动）：`update-strategy: not_null` 意味着传 null 不会清空已有值，
即「把已填的 IMO 改成空」目前做不到。要支持清空需改字段级 `updateStrategy`，属独立改动。

### 3. 船型字典口径冲突

**问题**：同一套码值有两份互斥定义，且无枚举、无 `sys_dict`：

| 出处 | 取值 |
|---|---|
| `VesselInfo` 实体注释 / DDL 注释 / 管理端下拉 | `1集装箱 2散货 3油轮 4杂货 5其他` |
| `VesselBindAuditDTO.newVesselType` 注释 | **`1货轮 2油轮 3集装箱 4散货 5其他`** |

按 DTO 注释传 `3` 想表达「集装箱」，落库后管理端显示「油轮」。

**修复**：新增 `com.aryn.cloud.vessel.api.constant.VesselType`（唯一口径 + `isValid` + `label`），
实体/DTO/管理端全部引用；管理端新增共享 `views/vessel/constants.ts`。
服务端对非法码值直接拒绝（不再静默落库）。

### 4. 审核建船没有船型控件

**问题**：`newVesselType` 在审核弹窗模板里**没有任何控件**，只有代码里的 `'5'` 默认值 →
**所有经审核创建的船，船型 100% 是「其他」**，管理端下拉那套字典在审核路径上完全未生效。

**修复**：审核弹窗补「船舶类型」下拉（默认「其他」）；管理端档案列表补「船型」列
（此前船型只在弹窗里可写、列表看不到，改了也验证不了）。

## 四、改动清单

### 后端

| 文件 | 改动 |
|---|---|
| `vessel-api/.../constant/VesselType.java` | **新增**：船型唯一口径常量 |
| `vessel-api/.../entity/VesselBindApply.java` | 新增 `applyVesselCallSign`，IMO 注释收紧 |
| `vessel-api/.../dto/VesselBindApplyDTO.java` | 新增 `applyVesselCallSign` |
| `vessel-api/.../dto/VesselBindAuditDTO.java` | 船型注释改为统一口径 |
| `vessel-api/.../vo/VesselBindApplyVO.java` | 新增 `applyVesselCallSign` |
| `vessel-api/.../vo/VesselBindPrecheckVO.java` | 新增 `applyVesselCallSign` |
| `vessel-api/.../entity/VesselInfo.java` | 船型注释指向常量类 |
| `vessel-biz/.../service/impl/VesselServiceImpl.java` | `normalizeVessel()`；update 补船名校验与归一化 |
| `vessel-biz/.../service/impl/VesselBindServiceImpl.java` | 提交/预检/自动匹配/建船/通知全链路拆字段；建船 trim 与船型校验 |
| `db/boot/131vessel_bind_call_sign_incremental.sql` | **新增**：呼号列 + 呼号索引（幂等） |
| `db/cloud/132vessel_bind_call_sign_incremental.sql` | **新增**：同上，cloud 一份 |
| `db/boot/build-full-sql.mjs` + `aryn_boot_full.sql` | 注册新脚本并重新生成（21380 行） |

### 管理端

| 文件 | 改动 |
|---|---|
| `views/vessel/constants.ts` | **新增**：船型字典共享常量 |
| `views/vessel/vessel/index.vue` | 引用共享常量；新增「船型」列；载重吨/定员改 `ElInputNumber` |
| `views/vessel/bind-apply/index.vue` | 列表拆 IMO/呼号两列；预填拆字段删正则；新建分支补船型下拉 |
| `views/vessel/bind-apply/bind-apply-audit-contract.test.ts` | 补 2 组契约断言（拆字段、船型统一） |
| `api/vessel/vessel.ts` | 类型补 `applyVesselCallSign` |

### 小程序

| 文件 | 改动 |
|---|---|
| `src/api/vessel.ts` | 两个接口类型补 `applyVesselCallSign` |
| `src/sub-pages/vessel/bind/index.vue` | 「IMO / 呼号」单行拆为「IMO 编号」「呼号」两行；payload 同步 |
| `src/sub-pages/vessel/bind/contract.test.ts` | 补 4 条契约断言 |

## 五、验收命令与结果

| 命令 | 结果 |
|---|---|
| `mvn -o test -pl aryn-vessel/aryn-vessel-biz -am` | **通过**：111 个用例全绿（新增 10 个） |
| `mvn -o clean compile -pl aryn-boot -am` | **通过**（BUILD SUCCESS，24.3s） |
| `mvn -o test -pl aryn-boot -am` | **2 个既有失败，均与本次无关**（详见下方说明） |
| `aryn-mall-ui: pnpm check:type` | **通过**（vue-tsc，37 包） |
| `aryn-mall-ui: pnpm test:unit` | **通过**：93 files / 729 tests |
| `aryn-mall-ui: eslint（改动文件）` | **通过**（exit 0） |
| `aryn-mall-uniapp: pnpm type-check` | **通过** |
| `aryn-mall-uniapp: pnpm test:unit` | **通过**：70 files / 753 tests |
| `node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs` | **通过**：21380 行，1715751 字节 |
| 增量 SQL 在真实库执行 | **未执行**（本机无运行库） |

### boot 全量测试的 2 个既有失败（与本次改动无关）

| 失败测试 | 原因 | 是否本次引入 |
|---|---|---|
| `TenantConfigurationConsistencyTest.cloudTenantTablesMatchEachServiceSchema` | `db/cloud/3aryn_nacos.sql` 的 `hx.tenant.tables` 缺 `vessel_port_vehicle`（124 号补丁脚本只在运行时改写 Nacos，不回写基线）。`docs/README.md` 第 46 条早已登记该测试为红 | **否**，既有基线落后 |
| `TenantInterceptorBypassAuditTest.onlyReviewedMapperMethodsMayBypassTenantInterceptor` | `aryn-product` 的 `GoodsSpuMapper` 新增了 `selectCatalogPage`/`selectSuggestNames` 两个 `@InterceptorIgnore`，未登记进测试的 `ALLOWED_BYPASSES` 白名单 | **否**，属其他并行改造 |

两者均不涉及 `aryn-vessel` 模块：本模块 `mvn test -pl aryn-vessel/aryn-vessel-biz -am` **111 个用例全绿**，
`mvn compile -pl aryn-boot -am` 也通过（编译期就能覆盖 biz 间 import 约束）。

## 六、剩余风险

1. **增量 SQL 未在真实库跑过**：`131/132` 含 `PREPARE/EXECUTE` 幂等 ALTER，语法与既有
   `67/127` 脚本同构，但未实际连库验证。
2. **cloud 基线未更新**：`db/cloud/3aryn_nacos.sql` 的 `hx.tenant.tables` 仍缺
   `vessel_port_vehicle`（既有问题，与本次无关），仓库自带 `TenantConfigurationConsistencyTest`
   对当前工作树应报红。
3. **存量申请单不拆分**：历史单的呼号可能躺在 `apply_vessel_imo` 里，管理端会显示在「IMO」列。
   如需清洗需人工核对，不做自动回填。
4. **空的 IMO 无法通过管理端清空**（`update-strategy: not_null`，既有行为）。
5. **小程序真机视觉未验证**：拆成 4 行后行高/分隔线仅做了结构对齐（复用同 class + style），
   未用微信开发者工具截图确认。
6. **常靠港口仍是自由文本**：用户已确认后续再改选择器（本次范围外）。
7. **未做端到端联调**：后端接口与前端表单各自单测/类型检查通过，但未起服务实跑提交流程。

## 七、后续（用户已确认的下一步）

- **常靠港口改为选择器**：复用既有港口数据源
  （管理端 `/vessel/admin/port-vehicles/port-options`、C 端 `/vessel/app/port-vehicles/ports`），
  或明确语义后降级为备注。
- P1 可选项：`vessel_member` 补 `real_name/phone/position` 列，审核通过时从申请单搬运
  （当前姓名/电话/职务审核后仅存活于申请单行，成员表无承载列）。
