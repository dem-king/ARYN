# 商品场景频道（横切视图）实施计划

> 日期：2026-11-03　负责人：待定　关联讨论：生鲜类目 benchmark（小象超市 17 个一级 / 单仓 1 万 SKU；盒马 2–3 万商品池 / 单店精选）

## 一、要解决的问题

类目树是**纵切**（一 SKU 一父，互斥穷尽），场景是**横切**（按消费 occasion 再切一刀，多对多、有时效、可轮转）。
现状把场景塞进了类目：`本周上新` 是一个一级类目（`9710000000000000006`，下挂二级 `新品首发`），因为 `goods_spu` 只有 `category_first_id`/`category_second_id` 两个槽位，一个"新到的肥牛卷"不可能同时属于 `肉禽蛋品` 和 `本周上新`——结果是场景类目要么空、要么靠运营复制商品维持。

系统内已有正确先例：`GoodsSpuMapper.xml:138-140` 的注释——"船供/个人是**下单场景**而非商品分类，同一商品池对所有场景可见"。本计划把同一条原则推广到消费场景。

## 二、模型决策（先钉死，避免走偏）

| 决策 | 取舍 |
|---|---|
| **新增 `goods_scene` + `goods_scene_spu` 多对多** | 商品归属不动，只多一扇窗；场景可增减不动类目树 |
| **不给 `goods_spu` 加 `is_xxx_item` 布尔列** | 现有 `is_member_item`/`is_clearance_item` 是"一列一专区"，每加一个专区要改实体+XML+表单+双模式 SQL。场景是 8~15 个且会轮转，列模式不可持续 |
| **频道页 = 现有微页面（`pageType='0'`）装修页** | 复用发布/版本/灰度/缓存/埋点全链路；C 端复用 `sub-pages/promotion/diy-page/index.vue`，**不新增页面、不动小程序主包体积** |
| **选品单一数据源**：装修组件新增 `dataSource.mode='scene'` | 运营只维护场景商品池一次；页面货架由装修自由组合 |
| **P0 只做手动选品**，规则选品（`本周上新=上架≤7天`）放 P2 | 规则一旦做不好，频道里会出现下架/无库存商品，比没有频道更糟 |
| **P1 才动分类页**（一级图标条混排场景入口） | 涉及 `goods_category` 加列与三端渲染，风险与收益都独立于 P0，可单独回滚 |

验收口径（P0 完成定义）：管理端建 2 个场景（`本周上新`/`火锅到家`）各配 30–60 个商品 → 首页金刚区两个入口 → 点进装修频道页能看到该池子，且**同一批商品在分类页原品类路径下依然可见、价格库存一致、下架商品即时从频道消失**。

## 三、分期

- **P0（本期）**：两张表 + 租户登记 + 后端 CRUD 与 `sceneId` 过滤链路 + 管理端场景页与选品 + 装修 `mode='scene'` + 首页金刚区入口 + 两个频道种子。
- **P1**：`goods_category` 加 `node_type`/`page_design_id`，分类页一级图标条混排（`category-browser` 三处分支），把 `本周上新` 从一级类目里退出。
- **P2**：规则选品（时间窗/类目集合/折扣率）+ 定时物化（仿 `184mall_zone_sync_job.sql`）+ 场景效果埋点 + 场景存在性后端校验（Dubbo）。

## 四、P0 前置任务（必须先做，否则新环境跑不通）

现网 22 个一级类目（`971` 前缀，含 `时令鲜果`/`火锅到家`相关父级）**在 repo 内没有任何创建脚本**：`boot/178`、`cloud/180` 只引用它们作 `parent_id`，`aryn_boot_full.sql` 的 `goods_category` 只含 191/192/951/952/963/976 六种前缀。用全量 SQL 新装环境会得到 16 个旧一级 + 35 个孤儿二级。

1. 从现网导出 `goods_category` 全部 `971` 一级行，写成可重放 seed 脚本：`db/boot/188category_tree_export_seed.sql`、`db/cloud/191category_tree_export_seed.sql`（`DELETE ... WHERE id LIKE '971%%'` + 显式列 INSERT，顺序为一级先于二级）。
2. 登记进 `build-full-sql.mjs` 的 `sections`（二元数组 `['标题','文件名']`，追加在 `194member_price_activity_purge_incremental.sql` 之后），重跑 `node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs`。
3. 复核 35 个孤儿二级在该脚本重放后能挂上：`select count(*) from goods_category c left join goods_category p on c.parent_id=p.id where c.parent_id<>'0' and p.id is null` 必须为 0。

> 场景与类目的边界同时写进 P1：`本周上新` 作为一级类目应逻辑删除（保留授权行，参照既定菜单/类目口径），其商品由场景池承接。

## 五、数据模型

编号：结构脚本 `boot/189goods_scene_incremental.sql` / `cloud/192goods_scene_incremental.sql`；菜单脚本 `boot/190goods_scene_menu.sql` / `cloud/193goods_scene_menu.sql`；场景种子 `boot/191goods_scene_seed.sql` / `cloud/194goods_scene_seed.sql`。boot 下一号 = 188（前置任务占用），cloud 下一号 = 191（两侧各自递增，主题名保持一致，历史已有错位与重号）。

```sql
-- 结构脚本用 CREATE TABLE IF NOT EXISTS，不 DROP/TRUNCATE 存量
CREATE TABLE IF NOT EXISTS `goods_scene` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `scene_code` varchar(64) NOT NULL COMMENT '场景编码，如 weekly_new/hotpot，用于埋点与入口引用',
  `name` varchar(32) NOT NULL COMMENT '场景名称',
  `sub_title` varchar(128) NULL DEFAULT NULL COMMENT '一句话卖点',
  `pic_url` varchar(512) NULL DEFAULT NULL COMMENT '入口图标，空则前端用首字色块兜底',
  `page_design_id` varchar(32) NULL DEFAULT NULL COMMENT '频道页（微页面 page_design.id），空则该场景不可进',
  `select_mode` char(1) NOT NULL DEFAULT '0' COMMENT '选品方式：0手动 1规则（规则为 P2）',
  `rule_json` varchar(1000) NULL DEFAULT NULL COMMENT '规则选品条件，P2 使用',
  `start_time` datetime NULL DEFAULT NULL COMMENT '生效时间，空=立即',
  `end_time` datetime NULL DEFAULT NULL COMMENT '失效时间，空=长期',
  `status` char(1) NOT NULL DEFAULT '1' COMMENT '状态：1启用 0停用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '入口排序',
  `create_by` varchar(64) NULL, `update_by` varchar(64) NULL,
  `create_time` datetime NULL, `update_time` datetime NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_scene_code` (`tenant_id`,`scene_code`,`del_flag`),
  KEY `idx_scene_tenant` (`tenant_id`,`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品场景频道';

CREATE TABLE IF NOT EXISTS `goods_scene_spu` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `scene_id` varchar(32) NOT NULL COMMENT '场景id',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sort` int NOT NULL DEFAULT 0 COMMENT '池内排序（运营指定顺序，页面可再按销量重排）',
  `source` char(1) NOT NULL DEFAULT '0' COMMENT '来源：0手动 1规则',
  `create_by` varchar(64) NULL, `update_by` varchar(64) NULL,
  `create_time` datetime NULL, `update_time` datetime NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_scene_spu` (`scene_id`,`spu_id`,`del_flag`),
  KEY `idx_scene_spu_tenant` (`tenant_id`,`scene_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='场景-商品关联';
```

风格照抄 `boot/185p1_zone_governance_incremental.sql`（最新、单脚本建 3 张租户表）与关系表 `boot/174seckill_daily_schedule.sql:102-119`（唯一键含 `del_flag`，逻辑删除后可重建）。种子用固定 id 前缀 `DELETE ... LIKE '980%%'` + 普通 INSERT（不用 `ON DUPLICATE KEY UPDATE`）。

## 六、多租户登记（4 处 + 1 个守门测试，漏一处启动/测试即失败）

`hx.tenant.tables` 是白名单：不在清单里 = 不自动注入 `tenant_id`（`ArynTenantLineHandler.java:30-36`），且 `TenantSchemaValidator` 会在登记了表但表无 `tenant_id` 列时启动失败。

1. boot：`aryn-boot/src/main/resources/application.yml`，清单起于 **190 行**，在 goods 系段（`goods_price_history`，**233 行**附近）追加 `- goods_scene` / `- goods_scene_spu`。
2. cloud 基线：`db/cloud/3aryn_nacos.sql` 中 `aryn-product-biz-dev.yml` 的 `config_info` INSERT（**41 行**附近）——改 content 必须同步重算 `MD5()`，`TenantConfigurationConsistencyTest.java:112` 会校验。
3. cloud 存量库重放：仿 `db/cloud/189p1_zone_nacos_tenant_tables.sql:12-25`，用 `REPLACE(content, ...)` 幂等追加。
4. 建表脚本：boot / cloud 各一份（见第五节编号）。
5. 守门：`aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantConfigurationConsistencyTest.java:51-74` 做全量比对——**先跑测试再提交**。

另外两张新表必须补进 `build-full-sql.mjs` 的 `resetTables`（181-230 行），否则 `verify-full-sql.mjs:269` 报"全量脚本缺少 DROP TABLE"；用 `IF NOT EXISTS` 建表则不用动 `verify-full-sql.mjs:271` 的裸建表硬编码 103。

## 七、后端改动（aryn-product）

| 位置 | 改动 |
|---|---|
| `product-api/entity/GoodsScene.java`、`GoodsSceneSpu.java` | 新实体，lombok + `@TableName(autoResultMap=true)`，`del_flag` 用 `@TableLogic`，与 `GoodsSpu.java` 同风格 |
| `product-biz/mapper/GoodsSceneMapper.java` + `resources/mapper/GoodsSceneMapper.xml` | 场景分页（带状态/时间窗）、`listEffective(tenantId)`、按 sceneId 取池内 spuId 分页 |
| `product-biz/service/IGoodsSceneService` + `impl/GoodsSceneServiceImpl` | CRUD、批量选品（幂等 upsert：已存在不重复插入、被移除的逻辑删）、启停与时效校验、成本价脱敏复用 `GoodsCostPriceMasker` |
| `product-biz/controller/admin/GoodsSceneController.java` | `/goodsscene/page`、`/{id}`、`POST`/`PUT`/`DELETE`(逻辑删)、`/{id}/goods`（池内列表）、`/{id}/goods/batch`（批量增删，参照 `admin/GoodsSpuController.java:68` 的 `/tags/batch`） |
| `product-biz/controller/app/AppGoodsSceneController.java` | `/app/goodsscene/list`（当前租户生效场景，给金刚区/分类页动态模式用） |
| `AppGoodsSpuController.java:48-57` | `/app/goodsspu/page` 增 `@RequestParam sceneId`（可空，不传行为逐字不变） |
| `IGoodsSpuService.java:115` / `GoodsSpuServiceImpl.java:347-380` | 新增重载或在现有方法尾部加参：**先在服务层查场景是否生效**（租户+status+时间窗），无效直接返回空页，不把时效逻辑写进 SQL；沿用既有"清空 `Page.orders` + 固定 ORDER BY"套路 |
| `GoodsSpuMapper.java:74-78` + `GoodsSpuMapper.xml:125-196` | `<if test="sceneId != null">AND EXISTS (SELECT 1 FROM goods_scene_spu gs WHERE gs.spu_id = goods_spu.id AND gs.del_flag='0' AND gs.scene_id = #{sceneId})</if>`。**白名单表参与 FROM/JOIN 必须起别名**（`TenantJoinAliasAuditTest`，硬约束见 `docs/40-接口与风险/README.md:18`）；`sceneId` 只作绑定参数，绝不拼 SQL 文本 |
| 分层约束 | 场景表放 **product 域**（与 `goods_spu` 同库同层），`promotion-biz` 已通过 `pom.xml:106` 依赖 `product-api`，P2 若要做场景存在性校验走 `product-api/remote/RemoteGoodsSceneService` + `@DubboService`，禁止 biz 直接 import biz |

## 八、装修数据源 `mode='scene'`（三端契约，漏改会被 parity 测试拦）

1. 后端：`DefaultPageDesignDocumentValidator.java` 仿 `MANUAL_MODE`(65 行) 加 `SCENE_MODE`，在 goods-tabs 校验处（**654 行**调用点）新增 `validateSceneTabSource`：`mode==='scene'` 且 `sceneId` 为空 → 阻断发布（P0 只做非空校验，存在性留给 P2）；`scene` 模式计入请求预算（同 588 行分支语义）。组件类型不变，`PageDesignComponentTypes` 无需改。
2. 管理端：`apps/web-ele/src/views/promotion/page-design/components/extension/types.ts`（数据源模式联合与默认值、`validateGoodsTabs` 435-467）、`extension-settings.vue`（模式单选 313-354、`updateTabMode` 137-145）、预览取数 `components/common/retail-preview/retail-data.ts`。
3. C 端：`src/components/diy/retail-types.ts:31` 的 `mode` 联合加 `'scene'` 并加 `sceneId?: string`；`retail-query.ts:32-58` 拼 `sceneId` 参数；`retail-data.ts:148-210 loadGoodsTabPage` 加 `mode==='scene'` 分支（goods-group / goods-waterfall / goods-scroll 同源复用）。
4. 测试：`pnpm exec vitest run --dom apps/web-ele/src/views/promotion`（`preview-parity.test.ts`、`page-type-whitelist-parity.test.ts`、`goods-card-info-parity.test.ts`）+ uniapp `pnpm test:unit`（`mobile-renderer-contract.test.ts`）。

## 九、管理后台

- `api/product/goods-scene.ts`：list/detail/add/edit/remove/goods/batch。
- `views/product/goods-scene/index.vue`：场景列表（名称/编码/入口图/池内商品数/频道页/生效区间/状态/排序）。
- `views/product/goods-scene/form.vue`：基础信息 + 频道页选择（从 `page-design` 微页面下拉里挑 `pageType='0'`）+ 时效。
- `views/product/goods-scene/goods-picker.vue`：选品弹层，**照抄** `views/product/goods-spu/index.vue:610-723` 的"批量专区打标"弹层与 `api/product/goods-spu.ts:71-72 batchZoneTags` 的交互口径（可按一级/二级类目筛选 + 已选池内排序）。
- 菜单与权限：`boot/190goods_scene_menu.sql` / `cloud/193goods_scene_menu.sql`，挂商品管理下。菜单是登录时快照，**改完必须重新登录**才可见。后台业务页不走 i18n（与 `goods-category`、`page-design` 一致，中文硬编码）。

## 十、移动端

- **不新增页面**：频道页复用分包内的 `src/sub-pages/promotion/diy-page/index.vue`（已在 `src/router/index.ts:12-27` 访客白名单），入口链接由装修 `link-resolver.ts:93-134` 的 `{type:'page', targetId}` 分派生成——这就是金刚区现有的跳装修页范式，场景入口直接复用。
- 若要让金刚区自动列出生效场景：`diy-category-nav/index.vue:50-84` 的动态模式当前只拉类目树，可改为 `source==='scene'` 时调 `/app/goodsscene/list` 并构造 `{type:'page'}` 链接；P0 也可先由运营手工在金刚区挂两个图片/入口，不改代码。
- 文案（如后续新增页面标题、空态）补 `src/locales/modules/catalog.{zh-CN,en-US}.ts` 两份；装修区文案补 `diy.{zh-CN,en-US}.ts`。英文只翻 UI 文案，商品与类目业务数据保持中文。
- 小程序主包只放 `src/pages/*` 的 tabBar 页；任何新增页面一律进 `src/sub-pages/`（主包已 ~2010KB，距 2048KB 上限仅 ~37KB）。P0 走复用路线，本项零风险。

## 十一、双模式与验证

- Controller 路径首段是微服务域：cloud 走 `/product/app/goodsscene/list`、`/product/app/goodsspu/page?sceneId=`；boot 由 `rewriteBootUrl` 改写为 `/boot/app/...`。前端只走 `parseOpenBoot`/`rewriteBootUrl`，禁止硬编码 `/boot`。改完在 `VITE_OPEN_BOOT=true/false` 下各确认一次请求路径。
- 本地库在 Docker：`docker exec -i aryn-boot-mysql mysql --default-character-set=utf8mb4 -uroot -p*** aryn_boot < db/boot/189goods_scene_incremental.sql`（宿主机无 mysql 客户端）。
- 命令（Java 测试必须带 `-am`，否则会拿 `~/.m2` 旧 jar 报假"找不到符号"）：
  - `cd aryn-mall-java && mvn clean compile -pl aryn-boot -am`
  - `cd aryn-mall-java && mvn test -pl aryn-boot -am`（覆盖租户一致性、JOIN 别名、`AppGoodsSpuControllerTest`）
  - `cd aryn-mall-java && node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs`
  - `cd aryn-mall-ui && pnpm check:type && pnpm test:unit && pnpm lint`
  - `cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit`
- 端到端要真拦截（不停在单测）：
  1. 建场景 + 挂池 + 建频道页，真机/H5 打开金刚区入口 → 频道页商品与池一致。
  2. 同一 SPU 在分类页原品类路径仍可见、价格库存一致（横切不改归属的核心断言）。
  3. 把池内某商品**下架** → 频道页与首页楼层同时消失（走 `/page` 的 `status='1'`，不是前端过滤）。
  4. 场景 `end_time` 过后 → 入口不再下发、`/app/goodsspu/page?sceneId=` 返回空页而非报错。
  5. 双租户隔离：租户 A 的场景与池对租户 B 完全不可见（含 `sceneId` 猜测式越权访问）。

## 十二、风险与豁免

1. `178`/`180` 引用了 repo 内不存在的 `971` 一级类目——**第四节前置任务不完成，本计划在新环境的可复现性为 0**。
2. `TenantConfigurationConsistencyTest` 是全量比对：只加表不改清单，或只改清单不加表，都会在 `mvn test -pl aryn-boot -am` 阶段红。
3. 装修数据源新增 mode 属三端契约变更，`preview-parity` 与 `mobile-renderer-contract` 会指出漏改项，不要绕过。
4. 池内商品被下架/删除时场景不做级联清理（保留行，查询期按 `status` 过滤），避免运营误删商品导致场景池永久丢失排序。
5. P1 若要给 `goods_category` 加 `node_type`，同时会牵动 `category-browser`（`selectFirst:210`、`selectSecond:217`、`handleSheetSelect:223`）与 `category-all-sheet`；本期不做，单列需求评审。

## 十三、交付检查清单

- [ ] 前置：971 一级类目 seed 脚本双模式落库 + full SQL 重生成 + 孤儿二级 = 0
- [ ] `goods_scene` / `goods_scene_spu` 建表脚本 boot 189 / cloud 192，可重复执行、无 DROP/TRUNCATE 存量
- [ ] 租户登记 4 处 + `resetTables` 补两行 + 一致性测试绿
- [ ] product 侧实体/Mapper/Service/Admin+App Controller/`sceneId` 过滤链路
- [ ] 装修 `mode='scene'` 三处（后端校验 / 管理端 / C 端）+ 两个契约测试绿
- [ ] 管理端场景 CRUD + 选品弹层 + 菜单权限脚本 + 重新登录可见
- [ ] 金刚区两个入口指向两张微页面频道页（不新增页面、不动主包）
- [ ] `本周上新` / `火锅到家` 种子（池 30–60 商品）双模式一致
- [ ] 端到端 5 条断言实测通过（含真下架、真过期、真越权）
- [ ] 结论回写 `docs/20-业务与数据/`（品类与场景的组织口径），需求登记进 `docs/90-记录归档/需求记录.md`
