# 专区配置收敛与装修器易用性优化 · 需求与实施记录

- 需求来源：用户直接提需（2026-11-04），原话：「把四、真正重复、需要清理的点做一下，最终目的是易用性好，方便工作人员操作」
- 提出时间：2026-11-04
- 影响端：管理后台（`aryn-mall-ui`）、后端（`aryn-promotion`、`aryn-product`）；UniApp C 端无行为改动（仅注释口径同步 + 回归）
- 关联需求：
  - [会员专区与低价捡漏规则化改造](../2026-11-02-会员专区与低价捡漏规则化改造/README.md) 及其 [配置操作手册](../2026-11-02-会员专区与低价捡漏规则化改造/操作手册.md)
  - [商品列表增加「专区」查询条件](../2026-11-03-商品列表专区筛选/README.md)
  - [管理端「批量专区打标」交互分析与改造建议](../2026-11-04-专区批量打标交互优化/README.md)（同日相邻需求；**本次不改批量打标交互**）
- 当前状态：**已实施并验证通过**（2026-11-04；Lead + backend + docs 并行交付，验证结果见 §七 与 [需求记录](../../90-记录归档/需求记录.md)）

---

## 一、原始请求与前序分析结论

### 1.1 用户原话

> 把四、真正重复、需要清理的点做一下，最终目的是易用性好，方便工作人员操作。

即在上一轮「专区配置面盘点」中，用户指定先落地第 4 项结论——**真正重复、需要清理的点**；交互层面的批量打标改造属于同批运营易用性优化，另立需求包，不在本次范围。

### 1.2 前序分析结论（为什么这些是「真正重复」）

把专区相关可配置项按「配置入口 → 谁消费 → 是否还有渲染」逐项比对后，确认 4 类真正重复/死配置：

| # | 重复/死配置 | 现状 | 判断 |
|---|---|---|---|
| 1 | 专区设置里的「专区标题 / 副标题 / 公告」 | 会员侧下发接口已随 2026-11-03 会员价活动功能删除；出清侧 `/product/app/clearance/activity-goods` 虽仍保留，但小程序从未调用 | **死配置**：C 端没有消费方；实际展示文案由装修 Tab 标题与会员权益条承担 |
| 2 | 装修 Tab「排序」下拉里的「会员商品专区（按标签）/ 出清商品专区（按标签）」 | 这两项本质是「选品维度」（按商品标签圈品），却混在「排序」里；与专区设置的数据源语义重复 | **概念错误**：应拆成独立的「专区选品」，排序只保留真正的排序项 |
| 3 | 装修 Tab「排序」下拉里的「折扣力度优先（低价捡漏）」 | 与专区设置「出清排序」+ 出清标签选品重复；它扫全店折扣率、不是标签选品，会把原价虚高/临时调价商品误判进专区 | **已弃用**：应从可选列表移除，历史配置只做兼容并给一键迁移 |
| 4 | 「会员权益条 = 会员价总开关」的误解 | 2026-11-03 起会员价/捡漏价已由共享补价层在**所有** Tab 的商品卡展示（跨页面价格一致性）；权益条只渲染 Tab 顶部横幅 | **语义重复/误导**：总开关是专区设置开关，权益条只管横幅，需在 UI 文案与手册中澄清 |

结论：把配置面收敛为两句话——**专区设置 = 规则与价格；装修多 Tab 商品流 = 展示位置 / 标题 / 选品**。运营不必再在两个页面之间来回找配置，也不会因为重复字段/重复排序入口而误配。

---

## 二、目标与非目标

### 2.1 目标

1. **专区设置页减负**：删除「专区标题 / 副标题 / 公告」两组死字段（会员 + 低价捡漏），页面只保留 开关 / 默认折扣率 / 成本兜底倍率 / 专区排序（出清），并给出「去哪里配展示」的指引。
2. **装修 Tab 编辑器改语义**：把原「排序」拆成「排序」+「专区选品」两个明确维度；移除旧「折扣力度优先（低价捡漏）」，历史配置给警告与一键迁移；补手选商品与会员权益条的边界提示；新建组件的默认 Tab 直接落到正确的专区选品。
3. **后端契约收敛**：专区设置接口与快照 VO 不再返回 `title / subtitle / notice`；出清专区 VO 同步删除；**数据库列保留、零 DDL**。
4. **行为口径澄清**：明确「谁才是总开关」「哪里的排序生效」，并写进 UI 文案与操作手册，减少运营误配与客诉。

### 2.2 非目标

- 不改 C 端价格链与专区算价/选品规则（会员价、出清价、取低、倒挂保护、时间分层均不动）；
- 不改数据库结构：`mall_zone_setting.title/subtitle/notice` 三列保留未使用，**不写增量 SQL**；
- 不删除 `/product/app/clearance/activity-goods`（保留接口，仅删响应中文案字段；小程序未调用）；
- 不做批量打标交互改造（见同日需求包 [2026-11-04-专区批量打标交互优化](../2026-11-04-专区批量打标交互优化/README.md)）；
- 不做历史装修文档的批量迁移：只在 Tab 编辑器内提示 + 单 Tab 一键迁移，发布不阻断；
- 不改计划中的「专区设置默认折扣率/成本兜底」的既有行为。

---

## 三、影响端、权限与租户

| 端 | 改动 | 说明 |
|---|---|---|
| 管理后台 `aryn-mall-ui` | A（专区设置页）+ B（多 Tab 商品流 Tab 编辑器） | 删除死字段、拆排序/专区选品、补提示与迁移入口 |
| 后端 `aryn-promotion` | C：`MallZoneSetting` 实体、`MallZoneSettingSnapshotVO`、`MallZoneSettingService`、`MallZoneSettingController` 注释 | 专区设置接口不再读写/校验 `title/subtitle/notice` |
| 后端 `aryn-product` | C：`GoodsZoneTagServiceImpl#applyZoneSetting`、`AppClearanceGoodsVO` | 出清专区 VO 删除三字段；快照只取 `sortMode` |
| UniApp `aryn-mall-uniapp` | 零行为/接口改动 | 本次仅同步 `retail-data.ts` 注释口径（权益条不控制会员价）；旧装修文档 `sort=bargain` 仍可读、可发布；C 端不消费三个文案字段 |
| 数据库 | 零 DDL | `mall_zone_setting` 三列保留未使用；无 boot/cloud 增量脚本 |

**权限与租户**：专区设置页读写沿用既有 `promotion:shipactivity:page` / `promotion:shipactivity:save` 权限码（`v-access:code`），**未新增权限点**；接口继续从登录上下文取租户、禁止入参传租户；路径 `/promotion/zone/setting/{member|clearance}` 不变，boot 模式仍由 `rewriteBootUrl` 改写为 `/boot/zone/setting/...`，双模式无需分别配置。

---

## 四、改动清单

### 4.1 A · 管理端「专区设置」页收敛

页面：`aryn-mall-ui/apps/web-ele/src/views/promotion/zone-setting/index.vue`

- 删除会员专区、低价捡漏两区的「专区标题 / 副标题 / 公告」输入项与表单字段（`ZoneForm` 不再含三字段，提交体与回显同步移除）；
- 页面只保留：**专区开关 / 默认折扣率 / 成本兜底倍率（会员） / 专区排序（出清，即 `sortMode`）**；
- 顶部 `ElAlert` 提示改为：「本页只配置专区的规则与价格（开关、默认折扣率、成本下限、出清排序）。专区的展示位置、Tab 标题与公告请在「页面设计 → 多 Tab 商品流」中配置。」；
- 新增「去页面设计配置专区展示 →」入口（`router.push('/promotion/page-design')`），避免运营找不到装修入口；
- 每个字段补帮助文案：开关——关闭后 C 端不再出货盘/不再出价，商品标签保留、重开自动恢复；默认折扣率——商品未单独配置时兜底、与等级折扣取优不叠加；成本兜底倍率——会员价下限 = 成本 × 倍率、商品显式下限优先；专区排序（出清）——作用于 C 端所有出清列表，**装修 Tab 里的排序对出清不生效**。

### 4.2 B · 装修器「多 Tab 商品流」Tab 编辑器

编辑器：`aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/components/extension/extension-settings.vue`（类型/默认值/校验在 `extension/types.ts`，画布预览在 `goods-tabs/index.vue`；数据源仍为「按分类/排序自动 / 手选商品」两项）

1. **把原「排序」下拉拆成两个维度**（同一 Tab 卡片内）：
   - **① 排序**：销量优先（`sales`）/ 新品优先（`create_time`）/ 价格从低到高（`sales_price`）；**仅「专区选品 = 不参与专区」时显示**（选中专区后隐藏，避免与专区排序语义打架）；
   - **② 专区选品**：**不参与专区（普通商品）** / **会员专区（会员标签商品）** / **低价捡漏（出清标签商品）**。选会员/出清后，该 Tab 由商品标签圈品，底层仍下发既有 C 端选品参数（`sort=member_price` / `sort=clearance`，持久化仍在 `dataSource.sort`，后端/C 端白名单不变）；切回「不参与专区」时恢复该 Tab 上次的普通排序，并清掉 `bargainRate` 脏字段。
2. **旧版「折扣力度优先（低价捡漏）」从可选列表移除**：`bargain` 仅保留在**发布校验白名单**与 C 端读取兼容中。历史装修文档（`sort=bargain`）打开时，专区选品显示禁用的「折扣力度优先（旧版，已停用）」+ 黄色警告「旧版「折扣力度优先」已停用」，并提供按钮 **「改为「低价捡漏（按标签）」」**，一键切为「按分类/排序自动 + 专区选品：低价捡漏（出清标签商品）」。
3. **手选商品模式提示**：「手选商品不套用专区标签与专区开关；需要专区货盘请改用「按分类/排序自动」并选择「专区选品」。」
4. **会员权益条提示**改为：「仅控制 C 端商品流顶部的会员权益横幅，**不控制会员价**；会员价由「专区设置」开关控制。建议只给会员专区 Tab 开启。」
5. **新建组件的默认 Tab 调整**：`createGoodsTabsDefaults()` 里「低价捡漏」= 专区选品 `clearance`、「会员专区」= 专区选品 `member_price`（Tab id/title 保持 `bargain`/`member` 兼容），「猜你喜欢」仍为普通排序 `sales`。
6. **画布预览口径同步**：会员专区判定改为「专区选品 = `member_price` 或旧配置开启权益条」，出清/旧 bargain 按价格主导样式渲染；单 Tab 时隐藏 Tab 头（避免只有一个 Tab 的空壳）。

### 4.3 C · 后端契约收敛（删除死字段）

| 文件 | 改动 |
|---|---|
| `aryn-promotion-api/.../entity/MallZoneSetting.java` | 删除 `title / subtitle / notice` 字段，类注释收敛为「会员/出清专区设置：开关、默认折扣率、成本兜底倍率与排序」 |
| `aryn-promotion-api/.../vo/MallZoneSettingSnapshotVO.java` | 删除 `title / subtitle / notice` 字段（保留 `zoneType/enabled/defaultDiscountRate/costFloorMultiplier/sortMode`） |
| `aryn-promotion-biz/.../service/impl/MallZoneSettingService.java` | `getSnapshot` 不再 set 三字段；`save` 的 `lambdaUpdate` 不再 set；`validate` 删除三个长度校验；注释同步（`MallZoneSettingServiceTest` 同步调整） |
| `aryn-promotion-biz/.../controller/admin/MallZoneSettingController.java` | 类注释改为「专区设置：会员/出清专区开关、默认折扣率、成本兜底与排序」 |
| `aryn-product-biz/.../service/impl/GoodsZoneTagServiceImpl.java` | `applyZoneSetting(AppClearanceGoodsVO, MallZoneSettingSnapshotVO)` 只保留 `vo.setSortMode(...)` |
| `aryn-product-api/.../vo/AppClearanceGoodsVO.java` | 删除 `title / subtitle / notice` 字段（保留 `total / sortMode / items`） |
| 数据库 | `mall_zone_setting.title/subtitle/notice` 三列**保留未使用**，零 DDL、不写增量 SQL，不重建全量 SQL |

### 4.4 D · 行为口径澄清（写进 UI 文案与操作手册）

| 问题 | 新口径 | 依据 |
|---|---|---|
| C 端专区货盘/价格由谁开关 | **专区设置 → 对应专区开关**才是总开关；关闭后不再出货盘、不再出专区价 | 2026-11-02 需求包 §12.3 |
| 会员权益条的作用 | **只控制 Tab 顶部横幅**；会员价/捡漏价由共享补价层在所有 Tab 的商品卡展示（2026-11-03 跨页面一致性），与权益条无关 | 2026-11-02 需求包 §12.5 |
| 出清排序以哪里为准 | **专区设置 `sortMode` 为准**，作用于 C 端所有出清列表；装修 Tab 的「排序」对出清专区不生效 | 本次 |
| 手选商品 Tab 与专区的关系 | 手选商品走通用 `list/{ids}`，**不套用专区标签/专区开关**；需要专区语义必须用「按分类/排序自动 + 专区选品」 | 本次 |

---

## 五、兼容策略

| 兼容项 | 策略 |
|---|---|
| 旧装修文档 `sort=bargain`（折扣力度优先） | **C 端与后端继续兼容读取**，旧文档可正常发布/展示；管理端只是不再提供新选，历史 Tab 编辑时显示警告 + 一键迁移。产品发布校验保留旧值白名单，避免存量文档被阻断 |
| 旧专区设置文案字段删除 | 属 **API 契约变化**：`GET/PUT /promotion/zone/setting/{type}` 与 `MallZoneSettingSnapshotVO` 不再返回/接收 `title/subtitle/notice`；**无 C 端消费者**（会员侧接口已删、出清侧接口 C 端未使用），管理端表单同步移除；DB 旧值保留但不再读取 |
| 新建组件默认 Tab 变化 | 只影响**新拖入**的多 Tab 商品流；存量文档的 Tab 配置保持原值，不做隐式改写 |
| `/product/app/clearance/activity-goods` | 接口保留，仅响应删除 `title/subtitle/notice` 三字段；小程序从未调用，无兼容风险 |
| 双模式（boot/cloud） | 接口路径与权限点不变，boot 由 `rewriteBootUrl` 改写；无需分别改配置 |

---

## 六、验收命令

```bash
# 后端：编译（Lead/backend 交付口径）
cd aryn-mall-java
mvn -pl aryn-promotion/aryn-promotion-biz,aryn-product/aryn-product-biz -am -DskipTests compile

# 后端：测试（可定向到专区相关用例）
mvn -pl aryn-promotion/aryn-promotion-biz,aryn-product/aryn-product-biz -am test
# 定向示例：
mvn -pl aryn-promotion/aryn-promotion-biz,aryn-product/aryn-product-biz -am \
  -Dtest='MallZoneActivitySyncTest,MallZoneSettingServiceTest,GoodsZoneTagServiceImplTest,GoodsSpuServiceImplTest' test

# 管理端：类型检查 + 单测（装修器默认 Tab / 排序白名单 / 专区设置页）
cd aryn-mall-ui
pnpm check:type && pnpm test:unit
# 相关用例文件：apps/web-ele/src/views/promotion/page-design/components/extension/types.test.ts 等

# C 端：无行为改动，跑回归确认旧 sort=bargain 文档/接口兼容
cd aryn-mall-uniapp
pnpm type-check && pnpm test:unit

# 静态自查：应无输出（仅允许历史兼容分支保留 bargain 读取）
grep -rn "getTitle\|setTitle\|getSubtitle\|setSubtitle\|getNotice\|setNotice" \
  aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/entity/MallZoneSetting.java \
  aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/vo/MallZoneSettingSnapshotVO.java \
  aryn-mall-java/aryn-product/aryn-product-api/src/main/java/com/aryn/cloud/product/api/vo/AppClearanceGoodsVO.java
grep -rn 'value="bargain"' aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/components/extension/
```

真实库/运行态可选核对：

- 保存专区设置后，`GET /promotion/zone/setting/member`（boot 为 `/boot/zone/setting/member`）响应**无** `title/subtitle/notice`；
- 打开历史 `sort=bargain` 装修文档可见禁用选项 + 迁移警告 + 「改为「低价捡漏（按标签）」」按钮，一键迁移后 Tab 的专区选品为低价捡漏；
- C 端会员专区/低价捡漏 Tab 的商品与价格与改造前一致。

**实际结果（2026-11-04 验证通过）**：

- **后端**：`mvn -pl aryn-promotion/aryn-promotion-biz,aryn-product/aryn-product-biz -am -DskipTests compile` BUILD SUCCESS；定向专区用例 80 例（`MallZoneActivitySyncTest` 7 / `GoodsZoneTagServiceImplTest` 8 / `GoodsSpuServiceImplTest` 35 / `MemberPriceActivityServiceTest` 30）全过；promotion-biz 全量 **421 例**（含新增 `MallZoneSettingServiceTest` 2 例）0 fail / 0 err / 1 既有 skip；全 reactor `mvn test -pl aryn-boot -am` BUILD SUCCESS；`mvn -Pcloud -pl aryn-boot -am -DskipTests compile` BUILD SUCCESS；全仓 grep 无 `title/subtitle/notice` 遗留引用。
- **管理端**：`pnpm check:type` 通过；`pnpm test:unit` **132 文件 / 1267 用例**全绿；promotion 子集 51 文件 / 432 用例全绿；改动文件 prettier/eslint 通过；`extension/types.test.ts` 新增 3 例（默认 Tab 专区选品 + 白名单接受 `member_price`/`clearance`）。
- **C 端**：仅 `retail-data.ts` 注释口径同步；`pnpm type-check` 通过、`pnpm test:unit` **148 文件 / 1646 用例**全绿；旧 `sort=bargain` 文档兼容分支未改。
- **附带修复**：`MallZoneSettingService#save` 更新分支补 `costFloorMultiplier` 持久化（原缺陷：运营改「成本兜底倍率」保存后回退旧值），新增红/绿回归用例 2 例（移除修复行必失败）。
- **未执行**：真实库/运行态接口响应核对（本机 boot 容器未重新打包部署；三字段删除由编译与单测保证，接口路径/白名单未变）；管理端浏览器目检（Vite dev server HMR 已编译改动模块）。

---

## 七、实际结果

已实施并验证通过（2026-11-04）。代码侧：专区设置页删除标题/副标题/公告并加提示与「去页面设计配置专区展示 →」入口；装修 Tab 拆「排序 + 专区选品」、旧 `bargain` 仅兼容并给警告/一键迁移、新建组件默认 Tab 改为 `clearance`/`member_price`；后端 VO/实体/服务删除三字段且零 DDL；C 端零行为改动（仅注释同步）。验证：后端全 reactor `mvn test -pl aryn-boot -am` BUILD SUCCESS + cloud 编译 BUILD SUCCESS；管理端 `pnpm check:type` + `pnpm test:unit`（132 文件 / 1267 用例）全绿；C 端 `pnpm type-check` + `pnpm test:unit`（148 文件 / 1646 用例）全绿。详见上节「实际结果」与 [需求记录](../../90-记录归档/需求记录.md) 同名条目。

---

## 八、边界与剩余风险

1. **数据库列保留未使用**：`mall_zone_setting.title/subtitle/notice` 三列与历史数据仍在库内，本次零 DDL。后续确认无用需另立清理需求，并按要求提供 boot/cloud 双份可重复执行增量脚本。注：同日并行的 [专区批量打标交互优化](../2026-11-04-专区批量打标交互优化/README.md) 需求会给同表新增 `last_sync_*` 列，其 DDL 归属该需求，与本需求「零 DDL」不冲突。
2. **手选商品 Tab 不 gate 专区开关（已知限制）**：手选走通用 `list/{ids}`，不感知专区标签与开关，即使专区关闭也照常展示手选商品、价格也不走专区价。Tab 编辑器已加提示；若要联动需改通用列表接口，不在本次范围。
3. **`/product/app/clearance/activity-goods` 保留但 C 端未使用**：该接口仍由后端维护，本次仅删响应文案字段。若确认长期无消费方可另立需求下线（需同步白名单/兼容脚本）。
4. **旧 `sort=bargain` 装修文档为过渡态**：C 端/后端继续兼容，但该排序仍是「扫全店折扣率」，不是标签选品；需运营在装修器内逐个迁移（有警告与一键按钮，不阻断发布）。后续版本可评估彻底下线。
5. **运营培训口径需同步**：本仓库操作手册已改为新口径；线下培训材料/群内旧截图若仍写「会员权益条是会员价总开关」「专区设置配标题/公告」，需人工同步更新。
6. **C 端跨页面价格一致性不由本次改动负责**：会员价/捡漏价在所有 Tab 的展示依赖 2026-11-03 共享补价层（`useZonePrices` / `zone-price`），本次未改；若某 Tab 缺价按 [2026-11-02 需求包](../2026-11-02-会员专区与低价捡漏规则化改造/README.md) §12.5 排查。
7. **无权限/租户/支付/状态机变更**，无 DB 变更；回滚只需回退代码与文档（数据库列从未删除，无补偿脚本）。

---

## 九、参考

- [会员专区与低价捡漏规则化改造](../2026-11-02-会员专区与低价捡漏规则化改造/README.md)（§12.3 专区设置、§12.5 跨页面价格一致性、§12.6 会员价活动删除）
- [会员专区 / 低价捡漏 · 配置操作手册](../2026-11-02-会员专区与低价捡漏规则化改造/操作手册.md)（已按新口径更新）
- [商品列表增加「专区」查询条件](../2026-11-03-商品列表专区筛选/README.md)
- [管理端「批量专区打标」交互分析与改造建议](../2026-11-04-专区批量打标交互优化/README.md)
