# 分类页品牌筛选交互优化（调研 + 方案）

> 日期：2026-09-29
> 状态：**方案 B 已实施并端到端验证**（后端已重新打包部署 + H5 实机走查通过）
> 类型：竞品调研 + 现状盘点 + 数据核对 + 方案设计 + 实施
> 关联：[移动端分类页改版](../../2026-09-21-移动端分类页改版/README.md)、[移动端UI交互优化方案（对标小象超市）](../../2026-09-22-移动端UI交互优化方案（对标小象超市）/方案.md)
>
> 实施结果见文末 [§9 实施结果](#9-实施结果2026-09-29-已完成)



## 1. 原始请求

> 从小程序首页金刚区点击分类，会进入这个页面，显示出当前分类下的商品，但是上面的品牌选择有点不正确吧，有一些商品完全没有这个产品，点击之后就显示空的，这样交互体验不够好，你调研一下同类产品，看怎么设计一下这个交互能更合理一些。

截图：`sub-pages/product/goods-list` 页，顶部搜索框下方一条横向品牌 chip 条（「全部品牌 / 悦航优选 / 悦航优鲜 / 悦航工坊 / …」），当前进入的是**水果**分类，列表里是烟台红富士苹果、新疆库尔勒香梨、赣南脐橙、福建琯溪蜜柚等商品。

---

## 2. 结论摘要

1. **问题属实，且比截图看到的更严重。** 品牌条的数据源是全租户品牌列表，与当前分类、关键词完全无关。实测本租户 **42 个品牌**，而每个一级分类下的品牌数最多 10 个：**水果、海鲜水产、手机数码、家用电器、鲜花绿植、精选冻品、船舶物料 这 7 个分类下 42 个 chip 全部点击必空**（蔬菜 41/42）；表现最好的休闲零食也有 **32/42 是死选项**。
2. **根因是「筛选维度没有跟随结果集收敛」**，不是某个分类的数据问题。`AppGoodsBrandController#list` 无任何入参，`goods-list` 页 `onLoad` 里一次性拉全量品牌后不再更新；商品查询 `selectApiPage` 支持 `categoryFirstId / categorySecondId / name / brandId`，但品牌列表接口完全不感知这些条件。
3. **行业标准做法就是「分面导航（faceted navigation）」**：筛选项从**当前结果集**动态计算，只展示有结果的值，并通常带结果计数。Azure AI Search 的官方定义即「分面是动态的，因为它们基于每个特定的查询结果集……计数指示每个分面的匹配项数」；Algolia 提供「contextual facet values and counts」；Elasticsearch 的 `min_doc_count` **默认值为 1**，即引擎层面默认就不返回零计数的分面值。
4. **推荐方案（分三档，建议直接做 B）**：
   - **A（最小修复）**：新增按条件聚合的品牌接口，品牌条从「全量 chips」改为「按当前分类/关键词实际有商品的品牌」渲染。
   - **B（推荐）**：A + 品牌条加**商品计数**、单分类下品牌数 < 2 时**整条隐藏**（该维度无区分度）、空结果给解释与出口。
   - **C（理想态，可延后）**：升级为多维「筛选」浮层，品牌 + 价格区间等，计数随选中条件联动。
5. **同时必须修「空结果」体验**：即使做了收敛，仍可能出现「先选品牌再切分类」导致的空结果。当前空态是 z-paging 默认的「没有数据哦~」，没有解释也没有出口。

---

## 3. 现状盘点（已核对到源码与运行库）

### 3.1 代码链路

| 环节 | 位置 | 事实 |
|---|---|---|
| 页面 | `aryn-mall-uniapp/src/sub-pages/product/goods-list/index.vue:142-162` | 品牌 chip 条；`onLoad` 中 `getBrandList()` 拉一次即定稿（`:67-69`），**不随分类/关键词变化** |
| 品牌接口 | `aryn-mall-uniapp/src/sub-pages/api/product/brand.ts` → `GET /product/app/goodsbrand/list` | **无任何入参** |
| 后端实现 | `AppGoodsBrandController.java:22-29` | `Wrappers.lambdaQuery().eq(status,"0").orderByAsc(sort)` —— 仅按「启用」过滤，**与商品/分类无 JOIN** |
| 商品查询 | `GoodsSpuMapper.xml:105` `selectApiPage` | 支持 `categoryFirstId / categorySecondId / name / brandId`，是**唯一**会按条件收敛的查询 |
| 面板 | `components/goods-list-panel/index.vue:63-98` | `brandId` 通过 props 传入；变化时 `refresh()` 重查 |
| 空态 | 同文件 `:164-169` | 未自定义 `empty` 插槽，走 z-paging 默认「没有数据哦~」（`i18n/zh-Hans.json`） |

### 3.2 运行库实测（boot 库，租户 `1590229800633634816`）

```
启用品牌总数            42
在售商品总数            272
其中有品牌字段的商品     62   (22.8%)
```

**各一级分类的品牌覆盖与死 chip 数**（死 chip = 42 个全站品牌中在该分类下无商品的）：

| 一级分类 | 在售商品 | 分类下出现过的品牌 | 点击必空的死 chip |
|---|---:|---:|---:|
| 休闲零食 | 12 | 10 | 32 |
| 酒水饮料 | 16 | 8 | 34 |
| 个护清洁 | 8 | 8 | 34 |
| 乳品烘焙 | 8 | 6 | 36 |
| 米面粮油 | 46 | 6 | 36 |
| 日用百货 | 10 | 3 | 39 |
| 肉禽蛋 | 25 | 2 | 40 |
| 熟食预制菜 | 16 | 2 | 40 |
| 蔬菜 | 50 | 1 | 41 |
| **水果（截图所在）** | **11** | **0** | **42（全部）** |
| 海鲜水产 / 手机数码 / 家用电器 / 鲜花绿植 / 精选冻品 / 船舶物料 | — | **0** | **42（全部）** |

> 全 42 个 chip 点击必空的一级分类共 **7 个**：水果、海鲜水产、手机数码、家用电器、鲜花绿植、精选冻品、船舶物料。

**接口实测（复现原始反馈）**：

```bash
# 水果分类 11 件商品，全部无品牌
GET /boot/app/goodsspu/page?categoryFirstId=9510000000000000002  → total=11，brandId 全为 null
# 点任意品牌 chip
GET /boot/app/goodsspu/page?categoryFirstId=...002&brandId=9530000000000000001  → total=0
GET /boot/app/goodsspu/page?categoryFirstId=...002&brandId=9530000000000000004  → total=0
# 同一个品牌换到有覆盖的分类（米面粮油）
GET /boot/app/goodsspu/page?categoryFirstId=...007&brandId=9530000000000000001  → total=2（有机黄小米 / 红芸豆）
```

> 补充：**全站维度**下 42 个品牌其实都有商品，所以接口本身没有「返回脏数据」——它只是**没有按当前上下文收敛**，这正是问题所在。

### 3.3 无品牌商品占比

「无品牌」不是个例，是当前数据的主体形态：

| 分类 | 在售 | 无品牌 | 无品牌占比 |
|---|---:|---:|---:|
| 水果 | 11 | 11 | 100% |
| 船舶物料 | 27 | 27 | 100% |
| 手机数码 | 13 | 13 | 100% |
| 海鲜水产 | 14 | 14 | 100% |
| 蔬菜 | 50 | 48 | 96% |
| 米面粮油 | 46 | 38 | 83% |
| 肉禽蛋 | 25 | 23 | 92% |

含义：**品牌在本租户商品池中是一个稀疏属性**。生鲜、船舶物料、数码等分类的商品名是「品类+产地/规格」（烟台红富士苹果、赣南脐橙），本来就不该有品牌概念。所以在这些分类里，品牌筛选**不具备区分能力**，展示它只会制造死选项（见 §4.5）。

### 3.4 入口矩阵（同一页被多处复用）

| 入口 | 传入条件 | 品牌条表现 |
|---|---|---|
| 首页金刚区分类 | `categoryId`（树解析为一级/二级） | 全量品牌，绝大多数必空（截图场景） |
| 装修链接（link-resolver） | `categoryFirstId/categorySecondId` | 同上 |
| 搜索页 | `keyword` | 全量品牌。实测「苹果」2 条结果**均无品牌** → 42 个 chip 全空 |
| 优惠券列表/购物车 | 无条件 | 全量品牌（此场景下 42 个品牌都有商品，问题相对轻） |

搜索场景实测：关键词「悦航」19 条命中 4 个品牌、「牛奶」5 条命中 4 个品牌——**同一页面下，有区分度的品牌集合随关键词剧烈变化**，固定全量列表必然失配。

---

## 4. 竞品调研：同类产品怎么做

### 4.1 行业标准就是「分面导航（faceted navigation）」

这不是「谁家 UI 更好看」的问题，而是有明确定义的成熟交互范式：筛选项**从当前结果集动态计算**，而不是展示全库的维度取值。

| 来源 | 关键结论（原文引用） |
|---|---|
| **Azure AI Search**（官方文档《向查询添加分面》） | 「**分面是动态的，因为它们基于每个特定的查询结果集。** 搜索响应包含用于在结果中导航文档的所有分面存储桶。首先执行查询，然后从当前结果拉取分面，并组合成分面导航结构。」「**计数指示每个分面的匹配项数。**」 |
| **Algolia**（官方文档 Faceting） | 「List all possible values for the selected attributes and return **contextual values and counts** with each search result.」——即每次搜索返回的是**当前上下文下的**可选值与计数；并提供 `maxValuesPerFacet` 限制展示数量。 |
| **Elasticsearch**（Terms aggregation 文档） | 多桶聚合「buckets are **dynamically built** - one per unique value」，且 `min_doc_count` **「Default value is 1」** —— 引擎层面默认就**不返回零计数的分面值**。 |
| **Wikipedia · Faceted search** | 「allowing users to narrow results by applying filters based on a faceted classification of the items」；「Faceted search improves navigation and conversion by helping users find relevant products more quickly.」电商产品天然适合（"most products have a type, brand, price, etc."）。 |
| **NN/g《Filters vs. Facets》** | 分面导航提供多个维度的过滤器，「provides a structure to help users understand the content space」；同时指出分面「more difficult to create and maintain」——需要后端聚合支持。 |

**直接推论**：本项目的品牌条现状（拉全量品牌 → 与结果集无关联 → 大量零结果选项）属于**反分面**实现。上面 §3.2/§3.3 的数据（42 个 chip 中 32~42 个必空）正是 Elm/ES 默认 `min_doc_count=1` 所要避免的情形。

### 4.2 关于「同类产品」的说明（证据强度声明）

本次调研中，**WebSearch 与多数中文站点（知乎/百度/CSDN/少数派等）在本机网络环境下被反爬拦截**，无法取得美团小象超市、叮咚买菜、盒马、京东到家等 App 分类页品牌筛选的**第一手界面取证**。因此这里必须明确区分：

- **强证据**：上述分面导航的工程定义与实现规范（Azure/Algolia/Elasticsearch 官方文档）、以及本项目自身运行库的实测数据（§3）。
- **中等证据**：本项目既有竞品研究文档已建立的判断——[移动端UI交互优化方案（对标小象超市）](../../2026-09-22-移动端UI交互优化方案（对标小象超市）/方案.md) 记录小象超市「商品浏览即加购、模块化卡片、低学习成本」，并在 §3.2 明确列出我方尚缺的能力是「筛选/排序轻浮层（价格、销量、新品）」；[移动端分类页改版](../../2026-09-21-移动端分类页改版/README.md) 已把分类页改为「一级图标条 + 二级 rail + 商品流」并保留排序条，**两处均未把「品牌」当作分类页的常驻维度**。
- **弱证据（一般经验，未经本次取证）**：即时零售类产品（小象超市/叮咚/朴朴）通常**不在二级分类列表页做品牌 chip 条**，因为生鲜商超的品牌密度低、用户决策以品类/规格/价格为主；品牌筛选更多出现在标品占比高的场景（京东/天猫/拼多多），且一律收在「筛选」浮层内、只列有货品牌。**这一条需要产品侧用真机复核后再采信**（见 §7 待确认事项）。

> 结论上不依赖弱证据：即便是「保留品牌条」这一选择，强证据也已经唯一地指向「必须按当前结果集收敛 + 抑制零结果选项」。

### 4.3 可迁移的设计模式清单

| # | 模式 | 说明 | 证据强度 |
|---|---|---|---|
| 1 | **分面值随结果集动态生成** | 品牌列表按「当前分类 / 关键词下实际有商品的品牌」查询，而非全库 | **强**（Azure/Algolia/ES 三家一致） |
| 2 | **零结果分面值不展示** | 不用「置灰」：置灰仍占位、仍诱使点击；ES 默认 `min_doc_count=1` 即直接不返回 | **强** |
| 3 | **展示分面计数** | 品牌后带商品数（如「悦航优选 2」）或随结果返回计数，用户可预判点击后有没有货 | **强**（Azure「计数指示每个分面的匹配项数」、Algolia contextual counts） |
| 4 | **维度无区分度时整体不展示** | 某分类下只有 0~1 个品牌（或全部商品都无品牌）时，隐藏整条品牌筛选——展示一个永远只有「全部」和唯一选项的筛选器没有意义 | **中**（由 1/2 推导；NN/g 强调分面要「help users understand the content space」） |
| 5 | **筛选维度收进「筛选」浮层** | 维度增多后，chip 条会挤占首屏；行业中多维筛选统一收在浮层，chip 条只留排序 | **中**（本项目方案文档 §3.2 已把「筛选/排序轻浮层」列为待补能力） |
| 6 | **空结果必须有解释与出口** | 空态文案说明「当前条件无商品」并给出「清除筛选」按钮，而不是通用「没有数据哦~」 | **中**（一般可用性经验；本项目 §3.1 已记录过同类问题：船供列表把「加载失败」误报为「暂无商品」） |

---

## 5. 方案设计

### 5.1 方案 A —— 品牌条按当前结果集收敛（最小修复）

**后端**：新增按条件聚合的品牌接口（不改现有 `list` 的行为，避免破坏其它调用方）。

```
GET /product/app/goodsbrand/filterList
    ?categoryFirstId=&categorySecondId=&name=
返回：[{ id, name, logoUrl, goodsCount }]     // 仅返回在该条件下有在售商品的品牌
```

实现要点（SQL 语义必须与 `selectApiPage` **对齐**，否则计数与实际列表不符）：

```sql
SELECT b.id, b.name, b.logo_url, COUNT(s.id) AS goods_count
FROM goods_brand b
JOIN goods_spu s ON s.brand_id = b.id
    AND s.del_flag = '0' AND s.status = '1'
WHERE b.del_flag = '0' AND b.status = '0'
  -- 与 selectApiPage 完全一致的可选条件
  [AND s.category_first_id  = ?]
  [AND s.category_second_id = ?]
  [AND LOWER(s.name) LIKE CONCAT('%', LOWER(?), '%')]
GROUP BY b.id, b.name, b.logo_url
ORDER BY b.sort ASC
```

- 多租户：`goods_brand`（`application.yml:176`）与 `goods_spu`（`:182`）均已在 `hx.tenant.tables` 白名单，MyBatis 拦截器会自动注入 `tenant_id`，**不需要**也无法用 `@InterceptorIgnore`（`TenantInterceptorBypassAuditTest` 会拦截新增绕过）。
- 索引：现有 `idx_goods_spu_brand (tenant_id, brand_id)` 可支撑该聚合（数据量小时无压力）。
- 双模式：接口定义在 `aryn-product-biz`，boot/cloud 共用；C 端免登白名单需在 `application.yml`（boot）与 Nacos（cloud）**两侧**新增 `/app/goodsbrand/filterList`，并同步 `PublicEndpointWhitelistContractTest`。

**SQL 已对拍真实库**（本次实测，与 §3.2 的接口结论一致）：

| 条件 | 聚合结果 | 与列表接口是否自洽 |
|---|---|---|
| `categoryFirstId=水果` | **0 行** → 品牌条整条隐藏 | ✅ 列表 `total=11` 全部无品牌 |
| `categoryFirstId=休闲零食` | **10 行**（各计数 1） | ✅ 10 个 chip 点进去都有货 |
| `name LIKE '%牛奶%'` | **4 行**（悦航优鲜 2 / 伊利 1 / 特仑苏 1 / 德芙 1） | ✅ 与关键词搜索命中品牌完全一致 |

**前端**：

- `goods-list/index.vue`：`getBrandList()` 改为随 `categoryFirstId / categorySecondId / keyword` 变化重新请求（`watch` 驱动，与 `goods-list-panel` 的刷新时机对齐）。
- 选中品牌后**不重新拉品牌条**（否则选中项会因条件互斥而消失），或拉取时保留「当前选中项 + 有货品牌」的并集。

### 5.2 方案 B —— A + 计数 + 无区分度隐藏（推荐）

在 A 的基础上加两条规则，直接消除截图里的体验问题：

1. **品牌 chip 带计数**：`悦航优选 2`（或右上角角标），用户点之前就知道有没有货。
2. **品牌数 < 2（或该分类下商品全部无品牌）时隐藏整条品牌栏**：水果、海鲜水产、手机数码、家用电器、鲜花绿植、精选冻品、船舶物料这 7 个分类将不再出现品牌条，首屏还给商品列表。
3. **保留「全部品牌」并始终置首位**；当前选中项高亮不变。
4. 品牌条**只在有内容时占据布局**，隐藏时不留下空白（`filter-bar` 整块 `v-if`）。

预期效果（按 §3.2 数据推演）：

| 分类 | 现状 chip 数 | 方案 A/B 后 | 说明 |
|---|---:|---:|---|
| 水果（截图） | 42（全死） | **0（整条隐藏）** | 11 件商品全无品牌 |
| 蔬菜 | 42（41 死） | 1 | 仅「悦航优选」（或按阈值隐藏） |
| 休闲零食 | 42（32 死） | **10（全活）** | 死选项归零 |
| 米面粮油 | 42（36 死） | **6（全活）** | 死选项归零 |

### 5.3 方案 C —— 分面筛选浮层（理想态，可延后）

上游方案文档已把「筛选/排序轻浮层」列为待补能力。分面接口做好后，自然延伸为：

- 顶部只留排序条（综合/销量/价格/新品），右侧固定「筛选」入口；
- 浮层内承载「品牌（带计数）+ 价格区间 + 其它维度」，计数随已选条件联动；
- 复用 `wd-drop-menu`（已在依赖中）或 `wd-popup` 承载，避免自研。

> 本期建议**不做 C**：当前分类页商品量级（单分类 4~50 件）不足以支撑多维筛选，先把 A/B 做扎实。

### 5.4 空结果体验（与 A/B 并行，必做）

即使收敛后仍会出现空结果（例如「先选品牌、再切到没有该品牌的分类」，或关键词搜索叠加品牌）。当前空态是 z-paging 默认「没有数据哦~」，无解释无出口。建议：

- 在 `goods-list-panel` 自定义 `#empty` 插槽：文案「当前筛选条件下暂无商品」，附「清除筛选」按钮（清空 `brandId` 并重查）；
- 若因 `brandId` 导致为空，**自动回退选中状态**到「全部品牌」并提示，避免用户停在死状态；
- 注意 z-paging 的 `showEmptyViewReload` 默认 `false`（`js/modules/empty.js:19`），加载失败态与无数据态需分别处理——**不要把「请求失败」显示成「暂无数据」**（本项目已有同类踩坑记录）。

---

## 6. 实施拆分与验收

| 阶段 | 任务 | 验收 |
|---|---|---|
| P0 | 后端 `filterList` 聚合接口 + 免登白名单（boot/cloud 双份）+ 契约测试 | 单测覆盖「仅返回有货品牌」「条件组合与 selectApiPage 一致」 |
| P0 | 前端品牌条改为按条件请求 | 水果分类下品牌条为空；休闲零食下 10 个 chip 点进去均有商品 |
| P1 | chip 计数 + 「品牌数 < 2 隐藏整条」 | 各分类实测无死选项 |
| P1 | 空态自定义 + 清除筛选出口 | 人为构造空结果，看到解释与出口而非「没有数据哦~」 |
| P2 | （可选）分面筛选浮层 | 另开需求 |

**验证命令**：
```bash
cd aryn-mall-java && mvn clean compile -pl aryn-boot -am && mvn test -pl aryn-boot -am
cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit
```

**回归用例（对拍 SQL）**：对每个一级分类，断言「品牌条返回的每个品牌 × 该分类」查询 `selectApiPage` 的 `total > 0`，且「品牌条之外的品牌」查询 `total === 0`。

---

## 7. 待确认事项（需产品/业务拍板）

1. **是否需要保留品牌筛选这一维度？** 本租户 22.8% 商品有品牌，且集中在零食/酒饮/个护/粮油等标品类目。可选口径：**只在有区分度的分类展示**（方案 B）／**全量隐藏品牌筛选**（更彻底，但会弱化标品类目的筛选能力）。
2. **品牌 chip 的排序口径**：当前按 `goods_brand.sort`（悦航系自有品牌靠前）。是否改为按商品数降序，让「有货多」的品牌更靠前？建议保留 `sort`（运营可控）为第一关键字、商品数为次。
3. **是否要同步治理商品数据**：为水果、蔬菜等分类的商品补品牌字段（例如「悦航优选」果蔬），可以从根本上让品牌维度在这些分类也可用。这属于**运营/数据治理**，不是前端能解决的。
4. **方案 C 的排期**：是否纳入上游《移动端UI交互优化方案》P1 的「筛选/排序轻浮层」一并实施。

---

## 8. 参考链接

- Azure AI Search《向查询添加分面》：<https://learn.microsoft.com/zh-cn/azure/search/search-faceted-navigation>（「分面是动态的，因为它们基于每个特定的查询结果集」「计数指示每个分面的匹配项数」）
- Algolia Faceting：<https://www.algolia.com/doc/guides/managing-results/refine-results/faceting/>（contextual facet values and counts）
- Elasticsearch Terms aggregation：<https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-terms-aggregation.html>（`min_doc_count` 默认 1）
- Wikipedia · Faceted search：<https://en.wikipedia.org/wiki/Faceted_search>
- NN/g《Filters vs. Facets: Definitions》：<https://www.nngroup.com/articles/filters-vs-facets/>
- 项目内：[移动端UI交互优化方案（对标小象超市）](../../2026-09-22-移动端UI交互优化方案（对标小象超市）/方案.md)、[移动端分类页改版](../../2026-09-21-移动端分类页改版/README.md)

> 取数环境：boot 单体模式（`docker` `aryn-boot-mysql`，库 `aryn_boot`），租户 `1590229800633634816`，接口经 `http://127.0.0.1:9999/boot/...` 实测。所有数字均为本次实测，非估算。

---

## 9. 实施结果（2026-09-29 已完成）

**采用方案 B**（含空结果体验修复）。无数据库结构变更 —— 这是纯读取侧的聚合查询，**不需要增量 SQL**。

### 9.1 改动清单

**后端**

| 文件 | 改动 |
|---|---|
| `GoodsBrandFilterVO.java`（新增，`aryn-product-api`） | 品牌筛选项 VO：`id / name / logoUrl / goodsCount`，implement `Serializable`（走 Dubbo 的 `*-api` 类必须） |
| `GoodsBrandMapper.xml`（新增） | `selectFilterList` 聚合查询；`INNER JOIN goods_spu` 排除零商品品牌，谓词与 `selectApiPage` 逐条对齐 |
| `GoodsBrandMapper.java` | 新增 `selectFilterList(@Param("query") GoodsSpu query)` |
| `IGoodsBrandService` / `GoodsBrandServiceImpl` | 新增 `listFilterOptions(GoodsSpu)`，空结果返回 `List.of()` 而非 null |
| `AppGoodsBrandController.java` | 新增 `GET /app/goodsbrand/filter-list`；原 `/list` 行为不变（避免影响其它调用方） |
| `aryn-boot/application.yml` | 免登白名单新增 `/app/goodsbrand/filter-list` |

**前端**

| 文件 | 改动 |
|---|---|
| `sub-pages/api/product/brand.ts` | 新增 `getFilterList()`；原 `getList()` 保留并注明「C 端筛选条不应使用」 |
| `sub-pages/product/goods-list/index.vue` | 品牌条改为按 `categoryFirstId / categorySecondId / name` 请求分面接口；chip 带商品计数；`brandOptions` 为空时整条隐藏（`showBrandFilter`），布局切换按钮保持常驻 |
| `components/goods-list-panel/index.vue` | 新增 `clearFilter` prop + 自定义 `#empty` 插槽：区分「加载失败」与「无数据」，无结果时给「清除筛选」出口 |

**测试**

| 测试 | 覆盖 |
|---|---|
| `BrandFilterPredicateContractTest`（新增，aryn-boot） | 品牌聚合查询与 `selectApiPage` 的可见性谓词/条件/大小写口径逐条一致；必须用 INNER JOIN 排除零商品品牌 |
| `AppGoodsBrandControllerTest`（扩充） | 三个查询条件必须原样透传（漏一个就会在别的维度重现死选项） |
| `PublicEndpointWhitelistContractTest`（扩充） | 新增免登路径登记 |
| `brand-facet-contract.test.ts`（新增，uniapp） | 页面用分面接口而非全量列表；三条件透传；空则隐藏但切换按钮常驻；计数；空态区分失败/无数据 |

### 9.2 验证结果

**接口对拍（部署后实测，boot 容器已重建）**

| 断言 | 结果 |
|---|---|
| 水果分类（原 42 个 chip 全死） | 返回 **空数组** → 前端整条隐藏 ✅ |
| 休闲零食（原 32/42 死） | 返回 **10 个品牌**，计数各 1 ✅ |
| **全 13 个一级分类 × 全部返回品牌** | 逐个回查商品列表：**0 个死选项**，且计数与 `total` 完全一致 ✅ |
| 关键词「牛奶」/「悦航」/「苹果」 | 品牌条收敛到命中集合（4 / 4 / 0），与列表命中品牌一致 ✅ |
| 免登（不带 token） | 正常返回，不 401 ✅ |
| 老接口 `/goodsbrand/list` | 仍返回全量 42 个，行为未变 ✅ |

**H5 实机走查（真后端、无 mock）**

- 水果分类：品牌条消失，布局切换按钮仍在右上，商品网格正常（对照原始截图）。
- 休闲零食：`全部品牌 + 10 个带计数 chip`；点「洽洽 1」→ 结果收敛为该品牌 1 件商品且 chip 高亮；点「全部品牌」→ 恢复 10 件。
- 空结果（未知关键词）：品牌条消失，空态文案为「当前筛选条件下暂无商品」（未选中品牌时不显示「清除筛选」，符合设计）。

**自动化**

- `mvn test -pl aryn-boot -am`：**BUILD SUCCESS**，全模块 0 failures 0 errors（含新增 3 例契约测试与既有租户/白名单审计）。
- `pnpm type-check` 通过；`pnpm test:unit` 546 例中 545 通过。
  - 唯一失败 `src/components/hr-navbar/contract.test.ts` 是**工作区并行未提交改动**（购物车页 navbar `#right` 插槽）导致，与本次无关：把购物车页与 navbar 目录 stash 掉后该测试反而 6 例失败，已确认为存量问题、非本次引入。
- 触及文件 ESLint 0 error（顺带修掉 `brand.ts` 原有的 4 个 style error）。
- `npx uni build -p mp-weixin` 通过，产物 `goods-list/index.wxml` 确认品牌条 `wx:if` / `wx:else` 占位 / `layout-toggle` 在条件之外 / 计数节点均正确生成。

### 9.3 遗留与后续

- **§7 的四项待拍板仍在**：是否保留品牌维度（当前实现＝只在有区分度分类展示）、chip 排序口径（当前＝`sort` 优先，计数次之）、是否治理商品品牌数据、方案 C 排期。
- 若后续要按 §7 第 2 条调整排序，只改 `GoodsBrandMapper.xml` 的 `ORDER BY` 即可，且需同步 `BrandFilterPredicateContractTest`。
- 分类页（`pages/product/category`）**本来就没有品牌条**，本次未改动；它复用 `goods-list-panel`，因此自动获得了新的空态体验。

