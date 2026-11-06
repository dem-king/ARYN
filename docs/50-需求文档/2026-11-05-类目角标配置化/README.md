# 类目角标配置化

> 状态：已实施并完成本机 boot 环境部署验收（2026-11-05）；cloud 仅静态验证，未部署实测。
> 实施契约与字段口径见 [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)，部署/验收证据见 [交付说明.md](交付说明.md)。

## 一、原始请求

用户（运营视角）在管理端「商品管理 → 商品类目 → 修改」里看到「类目角标」下拉只有
「荐（推荐）」「热（热卖）」两项，问「这个角标怎么维护，怎么新增」，并明确要求：
**长期由运营自由新增/维护，不再每次改代码发版。**

## 二、现状与目标

| | 改造前 | 改造后 |
|---|---|---|
| 数据 | `goods_category.badge_type char(2)` 写死枚举 `0 无 / 1 荐 / 2 热` | `badge_type varchar(32)` 存 `goods_category_badge.badge_key` |
| 角标定义 | 管理端下拉、管理端列表、C 端样式三处写死「荐/热 + 绿/红」 | 租户级配置表：文字（1–2 字）、底色、字色、排序、启停 |
| 新增角标 | 改 4 个文件 + 2 份 SQL + 2 个测试，重新发版 | 管理端「类目角标」页面新增一行，立即生效（C 端下次拉类目树时下发） |
| C 端 | 本地按 key 映射样式 | 类目树 `extra.badge` 下发 `{text,bgColor,color}`，C 端直接渲染；旧 key `'1'/'2'` 保留本地兜底 |

### 非目标

- 不做角标图片/图标上传，只支持文字 + 双色；
- 不复用到商品/活动等其他业务域；
- 不做角标文字多语言：运营配置的文字原样下发，`labels.categoryBadge*` 仅旧数据兜底；
- 不物理删除 `goods_category.badge_type`（继续作为 `badge_key` 引用列）。

## 三、影响端

| 端 | 改动 |
|---|---|
| 后端（aryn-product） | 新增 `GoodsCategoryBadge` 实体/Mapper/Service/管理端 Controller；类目树按「管理端原样 / App 归一」双口径解析角标 |
| 管理端（web-ele） | 新增「商品管理 → 类目角标」管理页（CRUD + 实时预览）；类目表单改为动态角标下拉；类目列表角标列改为读配置 |
| C 端（uniapp） | `resolveCategoryBadge(badgeType, serverBadge)` 服务端配置优先 + 旧映射兜底；分类页左栏与共享选货分类页接线 |
| 数据库 | 新表 `goods_category_badge`；`goods_category.badge_type` 改宽；试点租户 seed「荐/热」；新菜单/按钮/授权 |
| 双模式 | boot `db/boot/205/206` + `application.yml hx.tenant.tables`；cloud `db/cloud/210/211/212` + `3aryn_nacos.sql` 基线白名单 |

## 四、验收结论（本机 boot）

| 项 | 结果 |
|---|---|
| 后端编译 + 商品域单测 | BUILD SUCCESS；`aryn-product-biz` Tests run 204, Failures 0 |
| 管理端 typecheck | exit 0；新增 `logic.test.ts` 11 用例通过 |
| C 端 typecheck + 单测 | exit 0；152 文件 / 1700 用例全通过 |
| full-sql 生成 + 静态校验 | verify 通过（最终生成 30590 行 / 3369219 字节；工作区并行销售域 207 脚本也一并编入，非本需求改动） |
| boot SQL 执行 | 205 自检 `1/1/2/0`；206 自检 `6/5/6/6/2/2`；重跑幂等且不覆盖运营修改 |
| 后端容器重建 | 镜像重建 + 重启，`Started in 19.161s`，`/boot/actuator/health = UP` |
| 运行态 API | 新菜单可见；角标 list/page 正常；新建→类目引用→C 端下发→停用隐藏→删除保护 全链路通过 |

## 五、运营使用说明

1. 登录管理端（**需重新登录**，菜单权限在登录时快照）；
2. 进入「商品管理 → 类目角标」→ 新增：填角标文字（建议 1 字，最多 2 字）、底色、字色、排序、状态；
3. 进入「商品管理 → 商品类目」→ 修改类目 → 「类目角标」下拉选择刚建的角标；
4. C 端分类页左栏下次刷新即可看到（服务端类目树下发样式，无需发 C 端版本）；
5. 不想显示时把角标「停用」即可（C 端立即不渲染，管理端仍保留引用与回显）；已被类目引用的角标不可删除。

## 六、风险与遗留

- **C 端需发一次版**：旧 C 端只认识 `'1'/'2'`，新角标（雪花 key）要等 uniapp 新版发布后才渲染；
  过渡期内旧 C 端对旧角标行为不变。
- **cloud 未部署实测**：仅完成脚本与基线静态校验；cloud 环境需按
  `210 → 211 → 212` 顺序执行，并重启 `aryn-product-biz`/`aryn-upms-biz`。
- **管理端全仓 lint 存在基线失败**（`dist-cn` 生成物、`auth.vue`、`profile/index.vue`、他人 WIP），
  与本需求改动无关；本次改动文件定向 eslint/prettier/stylelint 全部通过。
- **角标文字无多语言**：运营配置什么 C 端显示什么，英文站点同样显示中文（已在非目标中声明）。
