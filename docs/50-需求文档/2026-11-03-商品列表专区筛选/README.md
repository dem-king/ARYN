# 商品列表增加「专区」查询条件 · 需求与实施记录

- 需求来源：用户直接提需（2026-11-03，附商品列表截图：「增加一个查询条件，可以按专区进行分类查询」）
- 提出时间：2026-11-03
- 影响端：管理后台（`aryn-mall-ui`）、后端（`aryn-product`）
- 关联需求：[会员专区与低价捡漏规则化改造](../2026-11-02-会员专区与低价捡漏规则化改造/README.md)（本需求是其运营侧增量）
- 当前状态：**已实施**（2026-11-03，boot 单体真实库验证通过）

---

## 一、背景与目标

会员专区 / 低价捡漏在 2026-11-03 改为「商品级标签驱动」后，运营的日常动作变成：批量打标 → 复核 → 调整。但商品列表（商品管理 → 全部商品）只能按名称 / 类目 / 品牌 / 规格 / 状态查询，「哪个商品在哪个专区」「还有多少商品没进专区」只能翻页肉眼找，打标结果难以复核。

本次目标（非目标：不改专区选品/算价规则，不改 C 端展示，不做专区报表）：

1. 商品列表新增「专区」查询条件，可按 **会员专区 / 低价捡漏 / 未加入专区** 分类查询；
2. 口径与列表既有「专区」列完全一致（只看 `is_member_item` / `is_clearance_item` 标签）；
3. 存量查询行为零影响：不传该条件时 SQL 与改造前逐字一致。

---

## 二、接口契约

`GET /product/goodsspu/page` 新增可选查询参数 `zoneType`（管理端分页，cloud 路径；boot 模式由 `rewriteBootUrl` 改写为 `/boot/goodsspu/page`）：

| zoneType | 含义 | SQL 条件 |
|---|---|---|
| 空 / 不传 | 全部商品（存量行为） | 不追加条件 |
| `member` | 会员专区 | `goods_spu.is_member_item = 1` |
| `clearance` | 低价捡漏 | `goods_spu.is_clearance_item = 1` |
| `none` | 未加入任何专区 | `is_member_item` 与 `is_clearance_item` 均为 0（兼容历史 NULL） |

- 同时打了两个标签的商品，在 `member` 与 `clearance` 两个筛选下都会出现（与列表两枚标签同时展示一致），不做互斥分类；
- 取值合法性在服务层校验（`专区筛选只支持 member / clearance / none`），未知值返回业务错误而不是静默返回全量，避免前端拼错时运营误以为筛选生效；
- 只新增查询参数，**未新增**接口路径 / 白名单 / 权限点 / 表 / 索引 / 配置，boot 与 cloud 共用同一套代码，无需 SQL 增量脚本（`idx_member_item` / `idx_clearance_item` 已在专区改造中建好）。

**口径决策**：`clearance` 只看标签、不看到期时间。列表「专区」列也是只判标签，二者保持一致；已到期的出清商品仍需能被筛出来续期或取消标签（C 端专区选品/算价另有到期判断，互不影响）。

---

## 三、代码改动

| 层 | 文件 | 改动 |
|---|---|---|
| 管理端页面 | `aryn-mall-ui/apps/web-ele/src/views/product/goods-spu/index.vue` | 查询条件新增「专区」下拉（`zoneFilterOptions`：会员专区 / 低价捡漏 / 未加入专区），`queryParams.zoneType` 随现有分页请求下发；条件排在「商品名称 / 商品类目」之后，默认展开即可见 |
| 接口入口 | `aryn-product-biz/.../controller/admin/GoodsSpuController.java` | `page(...)` 增加 `@RequestParam(required=false) String zoneType` |
| 服务层 | `IGoodsSpuService#adminPage(Page, GoodsSpu, String zoneType)`、`GoodsSpuServiceImpl` | `normalizeAdminZoneType`：空白→null、trim、未知值抛 `ArynBusinessException` |
| SQL | `aryn-product-biz/src/main/resources/mapper/GoodsSpuMapper.xml#selectPageByAdmin` | `<if test="zoneType != null and zoneType != ''">` + `<choose>` 映射三个条件；空值整段不输出 |
| Mapper | `GoodsSpuMapper#selectPageByAdmin` | 增加 `@Param("zoneType")` |

---

## 四、验证记录（2026-11-03）

**后端单测**

- `GoodsSpuMapperXmlTest` 新增 2 例：不传/空串不追加专区条件且既有名称/状态/类目/品牌条件保留；member / clearance / none 三种条件正确、取值不落入 SQL 文本。
- `GoodsSpuServiceImplTest` 新增 3 例：trim 归一化后下传、空白下传 null、非法值抛业务异常且不查库。
- 目标用例 **43/43 全绿**（`mvn test -pl aryn-product/aryn-product-biz -am -Dtest='GoodsSpuMapperXmlTest,GoodsSpuServiceImplTest'`）。
- 全量 `mvn test -pl aryn-boot -am` **BUILD SUCCESS**（全模块无失败）；`mvn -Pcloud compile -DskipTests` 通过。

**真实库 + 运行态（本机 boot 容器 + 5777 管理端）**

- boot 镜像重建并重启（`docker exec aryn-boot md5sum /app/app.jar` 与本地 `aryn-boot/target/aryn-boot.jar` 一致，启动无错误）。
- 库内商品 635 件；`GET /boot/goodsspu/page` 实测：不传 635 / `member` 18 / `clearance` 5 / `none` 612，返回记录的专区标签与筛选值一致；`zoneType=`（空串）回落 635，`zoneType=%20member%20`（前后空格）按 18 命中，`zoneType=none&status=1` 组合生效。
- 非法值 `zoneType=members` → `{"code":1,"msg":"专区筛选只支持 member / clearance / none"}`。
- 经 Vite 代理完整链路 `http://127.0.0.1:5777/api/boot/goodsspu/page?...&zoneType=member` 返回 total=18；Vite 已成功编译改动模块（HTTP 200，含「未加入专区」选项文案），HMR 可热更新。
- 双模式：本改动只新增查询参数，`rewriteBootUrl` 路径与改造前一致（`boot-url.test.ts` 3 例通过），`VITE_OPEN_BOOT=true/false` 均无路径差异。

**管理端**

- `pnpm -F @vben/web-ele run typecheck` / `pnpm check:type` 通过；`pnpm test:unit` 132 文件 / 1265 用例全绿；改动文件 eslint/prettier 通过。
- 全量 `pnpm lint` 仍有**存量**失败（`apps/web-ele/dist-cn` 构建产物 + 两个既有未提交改动文件 `src/layouts/auth.vue`、`views/dashboard/analytics/components/AnalyticsZoneReport.vue` 未格式化），与本次改动无关，按「不回退/不顺手格式化无关文件」原则未处理。

---

## 五、边界与后续

- 到期状态不影响管理端筛选（见口径决策）；若后续希望「已到期」单独可筛，可再加 `expired` 取值，前端并列一个选项。
- 「专区」列为展示字段而非能力开关：关闭专区设置（`mall_zone_setting.enabled=0`）不改变商品标签，因此筛选仍按标签返回，属于预期（标签仍在，只是 C 端不出价/不出货盘）。
- 单商品编辑页与批量打标弹窗未改动；打标后列表可通过该筛选直接复核结果。
- 未新增自动化 UI 用例（页面为纯查询条件接入，已由 typecheck / 全量 vitest / 真实接口验证覆盖）；若后续该页交互继续增多，再补组件级用例。
