# 微页面发布后切换为线上首页

> 日期：2026-09-23
> 状态：已实现
> 关联：[商城装修完整性与竞品调研](../2026-09-05-商城装修完整性与竞品调研/README.md)、[首页装修审查修复](../2026-07-16-首页装修审查修复/README.md)

---

## 1. 原始请求

> 这个商城装修，我新建了一个页面发布，但是移动端没有变化，还显示的第一个

用户截图：管理端「微页面」列表有 3 条 —— `首页`（已同步，线上版本 `2102411461664677889`）、
`新建页面`（待发布）、`小象超市风格首页`（已同步，线上版本 `2102577496772542466`）。
用户期望：把新页面发布后，移动端首页展示新页面。

## 2. 根因

**不是发布失败，也不是缓存问题，而是缺少「把某页切换成线上首页」的能力。**

移动端首页固定读取 `/promotion/app/pagedesign` → `PageDesignPreviewService#getPublishedHome()`，
其查询条件是：

```java
.eq(PageDesign::getPageType, "1")
.eq(PageDesign::getHomeStatus, CommonConstants.YES)
.eq(PageDesign::getPublishedStatus, "1")
.last("limit 1")
```

即：**只有同时满足 `page_type='1' AND home_status='1'` 的那一条记录才是移动端首页**。
而「新建页面」创建出来的页面 `page_type='0'`（微页面），发布只更新它自己的
`published_status=1` / `published_version_id`，**不会**触碰首页指针。

唯一能翻转这两个标记的接口是 `PUT /promotion/pagedesign`（`updatePageDesignById`，
当 `homeStatus='1'` 时互斥地重置旧首页、把目标页 `pageType` 改成 `'1'`）。
但管理端「微页面」列表页的入口在 2026-07-16 被删掉了。

删除的原始判断是：

```vue
v-if="scope.row.homeStatus === '0' && scope.row.pageType === '1'"
```

`pageType === '1'` 本身就代表「这是首页」，因此该条件**永远不成立**。
当时的任务把它当作「互斥的死按钮」清理（见
[首页装修审查修复](../2026-07-16-首页装修审查修复/README.md) 验收标准第 3 条
「微页面列表不再提供互斥的『设为首页』操作」），
清理死按钮本身没错，但**误删掉了唯一可用的首页切换语义**，此后运营再无任何途径
把已发布页面变成线上首页。这是产品能力缺口，不是回归缺陷。

### 现场证据（cloud 模式真实库）

```
id                     page_name            page_type home_status published_status
1912871334512336898    首页                  1         1           1     <- 移动端读它
2102574246438600705    小象超市风格首页        0         0           1     <- 用户新页面
2102572931049308162    新建页面               0         0           0
```

```
$ curl -H "tenant-id: 1590229800633634816" localhost:9999/promotion/app/pagedesign
{"data":{"id":"1912871334512336898","pageName":"首页","pageType":"1", ...}}
```

## 3. 方案

新增**明确的首页切换接口**，而不是恢复那个条件错误的旧按钮：

| 项 | 决策 |
|---|---|
| 接口 | `POST /promotion/pagedesign/{id}/set-home` |
| 权限 | 复用 `promotion:pagedesign:publish`（与发布/下线同级，已存在于 boot/cloud 菜单种子） |
| 前置校验 | 目标页必须已发布（`published_status='1'` 且有 `published_version_id`），否则拒绝 |
| 事务/并发 | 复用租户级 Redisson 锁 + `@Transactional`，对外一次性翻转旧首页与目标页 |
| 缓存 | 事务提交后清 `home_page_design_cache:{tenantId}`（沿用既有 afterCommit 语义） |
| 审计 | 新增 `PageDesignAuditLog.ACTION_SET_HOME = 'SET_HOME'`，记录切换人/时间/线上版本 |
| 幂等 | 目标已是首页时直接返回 `true`，不重复写库 |

**为什么必须校验「已发布」**：移动端读的是线上快照。
若允许把草稿页设为首页，`getPublishedHome()` 会因缺少发布版本抛
`Published page does not exist`，**整个 C 端首页直接报错**，
比"显示旧首页"严重得多。这条校验是本方案的核心安全边界。

**为什么不用旧接口**：`PUT /promotion/pagedesign` 是"通用字段更新"，
语义模糊且需要前端拼装 `homeStatus/pageType` 两个内部标记。
专用接口把 8 个字段的隐式契约收敛到服务端一个方法，
前端只需页面 ID。

## 4. 改动清单

### 后端

- `PageDesignAuditLog`：新增 `ACTION_SET_HOME = "SET_HOME"` 常量，并同步 `action` 列的 Schema/注释枚举
- `IPageDesignService#setAsHome(String pageId)`：新增接口方法
- `PageDesignServiceImpl#setAsHome`：锁内校验 + 互斥翻转 + 审计 + 提交后清缓存
- `PageDesignController`：新增 `POST /{id}/set-home`，`@SaCheckPermission("promotion:pagedesign:publish")` + `@SysLog`

### 管理端

- `api/promotion/page-design.ts`：新增 `setAsHome(id)`
- `page-design/index.vue`：操作列新增「设为首页」按钮（`HomeFilled` 图标），
  守卫 `publishedStatus === '1' && homeStatus !== '1'`，带二次确认
- `release-dialog.vue`：审计动作标签补 `SET_HOME: '设为首页'`

### 数据库（双模式增量）

- `db/boot/81page_design_audit_action_comment.sql`
- `db/cloud/82page_design_audit_action_comment.sql`

仅同步 `page_design_audit_log.action` 的列注释枚举（`varchar(32)` 本身容纳 `SET_HOME`，
不改类型、不改数据），并用 `information_schema` 判存在做幂等 + 缺表跳过护栏；
boot 脚本已登记进 `build-full-sql.mjs` 并重新生成 `aryn_boot_full.sql`。

> 命名差异说明：同一逻辑脚本在 boot 为 81 号、cloud 为 82 号，
> 因为两目录各自已有编号序列（cloud 81 已被 `81shared_cart_import_nacos_config.sql` 占用），
> 遵循仓库"各目录内部编号递增"的既有惯例。

## 5. 验收与实测

### 自动化

| 项 | 结果 |
|---|---|
| `PageDesignServiceImplTest`（新增 6 例 setAsHome） | 19 用例 0 失败 |
| `PageDesignControllerTest`（补 setHome 权限断言） | 3 用例 0 失败 |
| `mvn -pl aryn-promotion/aryn-promotion-biz -am test` | 181 用例 0 失败 |
| `mvn -pl aryn-boot -am test` | BUILD SUCCESS（含 `BootSqlSchemaTargetTest`、`TenantConfigurationConsistencyTest`） |
| `pnpm test:unit` | 484 用例 0 失败（新增 3 例） |
| `pnpm check:type` / `pnpm lint` / `pnpm build:ele` | 全部通过 |
| `mvn clean compile -pl aryn-boot -am` | 通过 |
| `node build-full-sql.mjs` + `verify-full-sql.mjs` | 静态校验通过（11438 行） |

**反向验证**（逐个注入缺陷确认测试能捕获，之后均已还原）：

| 注入的缺陷 | 被捕获 |
|---|---|
| 删掉"必须已发布"守卫 | `setAsHomeRejectsPageWithoutPublishedVersion` 失败 |
| 不翻转目标页 `pageType` | `setAsHomePromotesPublishedMicroPageInsideTenantLock` 失败（expected 1, got null） |
| 前端守卫弱化为只看 `publishedStatus` | `page-design-home-switch.test.ts` 用例 2 失败 |

### 真实运行态（cloud 模式，`localhost:9999`，按 `restart-service.sh promotion` 增量部署）

| 步骤 | 结果 |
|---|---|
| 切换前 `GET /promotion/app/pagedesign` | 返回 `首页`（`1912871334512336898`）——复现用户现象 |
| `POST /promotion/pagedesign/{小象}/set-home` | 200 |
| 切换后 `GET /promotion/app/pagedesign` | 返回 `小象超市风格首页`（`2102574246438600705`）——**问题解决** |
| `GET /promotion/pagedesign/home-edit` | 返回新首页且 `pageType=1 homeStatus=1` |
| 列表页 pageType/homeStatus | `首页` 变 `pageType=0/homeStatus=0`，无"双首页" |
| 审计日志 | 新增 `SET_HOME` 记录，`afterVersionId` 指向线上版本 |
| 未发布页 `set-home` | 拒绝：`页面尚未发布，发布后才能设为首页` |
| 不存在的页 `set-home` | 拒绝：`页面不存在或无权访问` |
| 匿名 `set-home` | 401 `未能读取到有效 token` |
| 切回原首页 | 200，移动端恢复返回 `首页`（可逆） |

增量脚本另在两个真实库上各执行两次：boot 脚本在临时库中（本机未部署 boot）验证幂等与缺表跳过，
cloud 脚本在 `aryn_promotion` 真实库验证幂等；执行后列注释均正确含 `SET_HOME`。
验证完成后已删除临时 `aryn_boot` 库，业务数据与 `page_design` 页面身份未变化。

## 6. 遗留与风险

- **首页切换是"指针语义"**：切换后旧首页记录保留（`pageType` 变 `0`），
  其线上版本与历史版本都可回滚，但**不再对外生效**；如需切回，再次点击原页面的「设为首页」即可。
- **`micro` 页面删除**：`handleDelete` 仍只对 `pageType === '0'` 开放，当前首页不可删；
  若想删掉曾经的首页，需先把它切下去（已由本次能力覆盖）。
- **未做**：定时/灰度方式切换首页、首页切换的独立权限码、批量切换。
  当前以"发布权限 + 已发布校验 + 审计"为最小闭环。
- **未做真实浏览器点击验证**：本机 `cua` 不可用（`unsupported Codex auth method`），
  故 UI 层以源码契约测试 + 生产构建产物断言（`dist/js/page-design-*.js` 含 `pagedesign/${e}/set-home`
  与确认文案）替代；接口链路走的是真实网关与真实库，已端到端验证。
