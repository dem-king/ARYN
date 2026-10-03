# 首页金刚区动态分类导航（跟随分类）

> 日期：2026-09-30
> 状态：**已实施**（双端类型检查 + 既有契约测试全绿，待实机走查）
> 类型：装修组件能力增强
> 关联：[装修预览与实机对齐](../../10-开发指南/装修预览对齐.md)（preview-parity 契约）

## 1. 背景与问题

运营反馈：修改商品分类后，小程序首页金刚区（装修组件「分类导航」`category-nav`）没有任何变化。

排查结论：**这是原设计如此，不是 bug**。金刚区是纯静态装修内容：

- C 端 `diy-category-nav` 的 `navList` 直接来自装修 JSON 快照，不发分类请求；
- 管理端配置项是手工条目（素材库图标 + 自由输入文字 + 链接选择器），与分类树无绑定；
- 唯一关联是「分类」类型跳转链接按 `categoryId` 引用——分类改名后点击仍正常，但文字/图标漂移。

代价：分类改名/新增/停用后，运营必须逐项手改金刚区并**重新发布装修页**。

## 2. 方案

金刚区组件增加 `source` 数据来源切换，**保留静态模式（默认，存量数据零迁移）**，新增「跟随分类」动态模式：

| 配置项 | 静态模式 | 跟随分类模式 |
|---|---|---|
| 数据 | 装修 JSON 里的 `navList` | 实时调 `/product/app/goodscategory/tree` |
| 范围 | 运营手工配置 | 启用中的一级分类（`status !== '1'`），按 `sort` 排序 |
| 数量 | 即配置条目数 | `categoryMax`（默认 8，1~50） |
| 图标 | 手工上传 | 分类 `categoryPic`；未配图/图挂掉走首字色块兜底（复用分类页 `resolveCategoryFallback` 色板，按 id 稳定哈希） |
| 点击 | 配置的链接 | 跳商品列表带 `categoryFirstId`（与后台分类树选择器产出的链接同构） |
| 空态 | 保持原渲染 | 整体隐藏（不渲染空壳） |
| 生效 | 需重新发布 | 实时生效，无需发布 |

后端**零改动**：树接口现成、双模式白名单已登记（boot `application.yml` + 契约测试钉死；cloud 该接口分类页在用）。无数据库改动、无增量 SQL。

## 3. 改动清单

### C 端（aryn-mall-uniapp）

- `src/components/diy/diy-category-nav/index.vue`：新增 `source`/`categoryMax` 读取；动态模式 watch 即时拉树（onCleanup 防竞态）、按 sort 排序、截取前 N、映射为 `{text, url, link}`；图片 `@error` 与空 url 走 `resolveCategoryFallback` 首字色块（字号 = 图标 0.45 倍）；动态空态整体 `v-if` 隐藏；静态模式行为逐字节保持原样。

### 停用分类过滤对齐（同日追加）

运营确认导航展示位需要过滤停用分类，与金刚区口径对齐。关键取舍：**导航展示过滤、层级解析不过滤**，刻意双轨——

- `src/utils/category-tree.ts`（新增）：`filterActiveCategoryTree` 纯函数，一级/二级各滤一层（`status !== '1'`，无状态视为启用），其余字段原样保留；单测 `category-tree.test.ts` 钉契约。
- `src/api/product/category.ts`：新增 `getActiveTree()`（导航展示位专用，内部走 `filterActiveCategoryTree`）；原 `getTree()` 保持全量并在注释中写明适用方。
- `src/pages/product/category/index.vue`：`getCategory()` 改用 `getActiveTree()`——分类页左栏二级 rail、顶部图标条、「全部分类」浮层都吃这份 props，一处过滤三处生效。
- `src/sub-pages/product/goods-list/index.vue`：**不改**，`resolveCategoryId` 保留全量 `getTree()` 并加注释说明——停用一级分类的老链接若被滤掉，会落入兜底分支被当成二级，`categorySecondId` 传一级 id 查出来恒空。
- `diy-category-nav` 动态模式同步切换到 `getActiveTree()`，移除组件内局部过滤。

### 管理端（aryn-mall-ui）

- `page-design/components/category-nav/setting.vue`：新增「数据来源」单选（手动配置/跟随分类，默认手动）；跟随分类下显示「最多显示」`ElInputNumber`（1~50）与说明文案；「每行数量」两种模式共用；拖拽编辑列表仅手动模式可见。
- `page-design/components/category-nav/index.vue`（预览渲染器）：同样支持 `source`/`categoryMax`，编辑器内实时调管理端分类树接口渲染真实数据（所见即所得）；图挂掉/未配图与小程序同口径走色块兜底；拉取失败退化为占位。
- `page-design/components/category-nav/category-fallback.ts`（新增）：C 端 `src/utils/category-icon.ts` 的**逐位镜像**（色板 + 31 进制哈希 + `| 0` 截断）。哈希算法必须与 C 端完全一致否则同分类两端颜色不同，故对 eslint 的 `prefer-math-trunc`/`prefer-code-point` 做行内豁免。

## 4. 语义与边界

- 动态模式只取**一级**分类；二级入口仍由分类页承载。
- 停用分类过滤口径（已对齐）：导航展示位（分类页、金刚区动态模式）统一走 `getActiveTree()`，一级/二级均过滤、无状态视为启用；goods-list 层级解析保留全量 `getTree()`（原因见改动清单）。后端 tree 接口维持不过滤 `status`，不改动共享接口行为。
- 分类删除后自然从金刚区消失（动态模式无死链问题，这正是动态化的收益之一）。
- 会话内不缓存：每次组件挂载/配置变化重拉。分类树很小（几十节点），且与分类页同接口。
- preview-parity 契约锁定的容器 padding（12px 0）与文字字号（12px）未动；新增色块样式两端逐值对齐（白字 600 加粗、line-height 1、overflow hidden）。

## 5. 验证

- 管理端：`pnpm check:type` 0 error；`pnpm test:unit` 72 files / 534 tests 全绿（含 `preview-parity` 16 项）；改动目录 eslint 0 问题。
- C 端：`pnpm type-check` 0 error；diy + utils 定向 vitest 全绿（含新增 `category-tree.test.ts` 4 项过滤契约）；改动文件 eslint 0 问题。
- `pnpm test:unit`（C 端全量）中 `src/api/order/replenish-contract.test.ts` 1 项失败为**存量失败**：该用例断言 Java 文件 `ShoppingCartServiceImpl.java` 内容，而该文件正处在本分支未提交的补给单改造中，与本次改动无关。
- 待办：小程序真机/H5 走查动态金刚区（渲染、点击跳转、色块兜底、停用过滤）；boot/cloud 无需后端部署。
