# 装修组件库扩容：按页面类型补齐装修组件（优化方案）

> 需求日期：2026-10-04（同日按用户澄清重排：核心诉求是**增加组件**，
> 为不同页面类型提供适合的装修组件；商详页「无关组件」问题是伴随痛点）
> 范围：三端（promotion 后端 / 管理端 page-designer / C 端 diy）
> 状态：**方案待评审，未实施**。每批次可独立实施，完成后回填落地与验证。

---

## 一、背景与诉求

对照 CRMEB 商城装修截图（用户提供）：

1. **核心诉求**：CRMEB 组件库分组丰富、组件数量多，本项目希望**扩充组件库**，
   并且不同页面（首页/商详/分类/个人中心/微页面）各有适合的组件可用；
2. **伴随痛点**：当前「商品详情页」组件面板里出现大量与商详无关的组件
   （轮播图、秒杀、公告等首页/列表页语义组件）。

## 二、CRMEB 装修体系调研

> 证据来源：GitHub `crmeb/CRMEB`（v5.x PHP 开源版）源码 + 编译产物
> `crmeb/public/statics/mp_view/subpackage/diyComponents/`，2026-10-04 经
> GitHub API 抓取。官方文档 help.crmeb.net 本机网络未能访问；下表组件中文名
> 依据 CRMEB 官网公开资料与命名惯例，**标注为推断的部分可能不准**，
> 但组件能力以源码为准。

### 2.1 C 端组件清单（40 个）

| 分类 | 组件（key） | 中文名（推断） |
|------|------------|----------------|
| 商详类 | productInfo / productDesc / productBottom | 商品信息 / 商品图文详情 / 商详底部操作栏 |
| 商详类 | homeReviews / homeProductService | 商品评价 / 商品服务保障 |
| 商品经营 | goodList / promotionList | 商品列表（手选/分类/标签 + 销量价格排序）/ 促销列表 |
| 营销活动 | seckill / bargain / combination / presale / newVip / coupon | 秒杀 / 砍价 / 拼团 / 预售 / 新人专享 / 优惠券 |
| 导航广告 | swiperBg、swipers / pictureCube / menus / tabNav / guide / headerSerch / hotspot | 轮播图 / 图片魔方 / 金刚区 / 图文导航 / 底部导航 / 搜索框 / 热点图 |
| 媒体内容 | videos / liveBroadcast / articleList、news / richText / titles / blankPage / pageDesign | 视频 / 直播 / 文章资讯 / 富文本 / 标题 / 辅助空白 / 设计页 |
| 个人中心 | homeUserInfor、userInfor / homePaidVip / pointsMall / signIn / follow | 用户信息头 / 付费会员 / 积分商城 / 签到 / 关注引导 |
| 工具 | customComponent / customerService / commonWrapper / placeholder | 自定义组件 / 客服 / 通用容器 / 占位 |

### 2.2 商详装修 = DiyPro 独立模板体系（关键差异）

CRMEB 把「商品详情页装修」做成与首页装修平行的 **DiyPro** 子系统
（`app/adminapi/controller/v1/diy/DiyPro.php` + `DiyProServices.php`）：

- **多模板管理**：商详模板可建多套（列表/改名/删除/导出导入），同一时刻
  仅一套启用（`setInfoStatus`，启用态带 `version` 快照供 C 端校验增量）。
- **商品列表瘦身存储**：模板 JSON 保存时把商品列表剥成 `ids`（手选上限
  limitMax=50，超限直接报错），C 端渲染时按 `typeConfig`（手选 ids /
  分类 cate_id / 标签 store_label_id）+ `goodsSort`（销量/价格）**实时取数**，
  模板不冗余存商品快照。
- **变量绑定系统**（`textField`）：预置商品/用户/优惠券/文章字段表
  （商品名称、售价、销量、浏览量……）作为占位变量，供富文本等组件
  在商详上下文动态替换——同一模板复用于不同商品时内容随商品变化。
- **商详页 = 组件拼装**：productInfo（商品信息卡）→ productDesc（图文详情）
  → productBottom（底部操作栏）→ homeReviews（评价）→ homeProductService
  （服务保障）→ goodList（推荐位）→ coupon（领券）自由排序。

### 2.3 管理端配置面板 = 通用配置控件

CRMEB 管理端（`template/admin/src/components/diyComponents/c_*.vue`）不为
每个组件手写面板，而是 13 个通用控件（c_upload_img / c_txt_list / c_tab /
c_is_show / c_bg_color / c_goods / c_page_ueditor / c_upload_video …）由
schema 驱动拼装。本项目 property-panel + schema/defaults 已是同类思路，
**无需照搬**。

## 三、本项目（ARYN）现状

- **存储与链路**：`page_design`（schemaVersion=3，longtext 存 JSON，草稿
  乐观锁）+ page_design_version（发布快照）+ template/release/theme/metric
  全套；C 端 `GET /app/pagedesign/type/{n}`（boot 下经 context-path 改写）。
- **页面类型**：home(1) / detail(2) / category(3) / usercenter(4) / micro；
  C 端整页消费（首页/分类/个人中心，`useDecorationPage`）+ 页内嵌入消费
  （商详页 `usePageDecoration('2')`，装修区渲染在原生骨架下方）。
- **已有组件 30 个**（管理端注册表与 C 端 `components/diy/` 已对齐）：
  - 基础/文本：title-text、rich-text、custom-html、gap、notice、search-bar、
    countdown；
  - 导航广告：swiper-banner、image-ad、tab-nav、category-nav、bottom-nav、
    marketing-entry（营销入口）、video-live（视频/直播**入口**卡片）；
  - 商品经营：goods（手选商品）、goods-group、goods-scroll、goods-ranking、
    goods-waterfall、shop-info、ship-workbench、replenish-card；
  - 营销/权益：seckill、discount、limited-activity、coupon-receive、
    coupon-combo、member-benefits、service-promise。
- **白名单机制已存在**：管理端 `detailPageAllowedComponents` 等三份白名单
  与后端 `PageDesignComponentTypes` 由 `page-type-whitelist-parity.test.ts`
  钉死一致性；C 端渲染器有 `UnknownComponent` 兜底，未知组件类型不崩。
- **痛点根因**：详情页白名单放行过宽——swiper-banner、image-ad、notice、
  seckill、discount、limited-activity 等首页/列表语义组件均可拖入商详。

## 四、新增组件矩阵（按页面类型）——本方案核心

> 原则：**每个新组件先回答「放在哪个页面、解决什么导购/内容问题」**，
> 数据源优先复用既有域接口；无对应业务的 CRMEB 组件（砍价/拼团/预售/
> 积分/签到/直播/付费会员）不补，需求立项时随域落地。

### 4.1 总表

| 组件（新 key） | 中文名 | 适用页面 | 能力要点 | 数据源 | 后端改动 | 成本 | 优先级 |
|---------------|--------|----------|----------|--------|----------|------|--------|
| `image-cube` | 图片魔方 | 首页/分类/微页 | 布局模板（1 图 / 横 2 / 竖 2 / 田字 / 1+2 / 1+3），每格图片+链接 | 纯静态 | 无 | 中 | **高** |
| `goods-recommend` | 商品推荐（看了又看） | **商详** | 同分类销量 Top N / 同分类新品 / 手选补充；横滑卡复用 goods-scroll 样式 | product 域在售商品接口 | 复用 goods-ranking 取数链；若不支持分类过滤则小改 | 中 | **高** |
| `video-player` | 视频 | 首页/商详/微页 | 封面+视频地址（素材库选）、静音自动播开关；区别于 video-live 入口卡 | 纯静态（素材库） | 无 | 中 | 中 |
| `hotspot-map` | 热点图 | 首页/分类/微页 | 大图 + N 个拖拽热区，每区独立跳转链接（大促会场图） | 纯静态 | 无 | 中高 | 中 |
| `floating-service` | 悬浮客服 | 首页/微页 | 悬浮按钮（联系客服/回到顶部），脱离文档流悬浮 | C 端客服链路（已有） | 无 | 低 | 中 |
| `review-highlights` | 精选好评 | **商详** | 该商品好评 2～4 条（星级+内容+昵称脱敏） | 评价域分页接口（复用） | 无 | 低 | 可选 |
| `article-list` | 资讯列表 | 首页/微页 | 带图文章列表（港口资讯/航期公告） | **缺文章域，需先做数据源决策** | 视决策 | 高 | 决策项 |

### 4.2 各页面最终形态

**首页**：现有组件全量可用 + 新增 image-cube、video-player、hotspot-map、
floating-service（article-list 视决策）。首页不缺「骨架级」组件，
缺的是**营销排版表达力**（魔方/热区/视频）。

**商品详情页**（先修语义，再加专属组件）：
- 白名单收窄（产出侧）：移除 swiper-banner、image-ad、notice、seckill、
  discount、limited-activity；保留 title-text、rich-text、custom-html、gap、
  goods、goods-waterfall、coupon-receive、coupon-combo；
- 新组件落地后加入白名单：`goods-recommend`（商详核心导购位）、
  `video-player`（船供品讲解视频）、`review-highlights`（可选）；
  已有的 service-promise（服务保障）加入商详白名单；
- C 端渲染保持宽容（UnknownComponent 兜底），已发布存量详情页不受冲击，
  运营重新编辑时自然收敛到新白名单；
- 商详本体保持原生骨架（GoodsInfo / GoodsComment / 图文详情位置不动），
  不做 CRMEB productInfo 式整页拼装——性能与稳定性优先。

**分类页**：无专属新组件。现有 banner/倒计时/优惠券/商品列表类组件
+ 新增 image-cube、hotspot-map 复用即覆盖；分类页的主体是原生左导航+
右列表，装修区是导购点缀。

**个人中心**：复用为主，不强造组件。现有 tab-nav（自定义宫格入口）、
member-benefits、coupon-receive 已覆盖 CRMEB homeUserInfor / pointsMall /
signIn 的对应场景（本项目无积分/签到业务）。

**微页面**：全量组件可用（含以上所有新增），微页面本来就是「自由页」。

### 4.3 决策项说明

- **article-list**：CRMEB 有文章域（article 表 + CMS），本项目没有。两条路：
  a) 复用 upms 公告/站内信做「资讯」口径（成本低但语义勉强）；
  b) 新建 article 域（实体+接口+双模式 SQL，成本高）。**建议暂缓**，
  航期公告先用 notice（已支持多条 contentList）承载，待运营侧确认
  有持续的内容生产需求再立项。
- **review-highlights**：原生评价区已存在（GoodsComment 分页），精选好评
  价值在「首屏可见的好口碑」，属锦上添花，标注可选。

## 五、实施批次

### 批次一（高优先级，建议先行）

> **落地状态：已实施（2026-10-07）**，落地明细见文末「批次一落地结果」。

1. **商详白名单收窄**（0.5 天）：管理端 `component-registry.ts` 与后端
   `PageDesignComponentTypes` 同步改，`page-type-whitelist-parity.test.ts`
   全绿即验收；无 SQL。
2. **image-cube 图片魔方**（1～1.5 天）：布局模板 + 每格配置；三端同步
   （管理端画布/设置面板/预览 + C 端 diy 组件 + parity 对齐 1rpx=0.5px）。
3. **goods-recommend 看了又看**（1～1.5 天）：C 端 `<DiyPage>` 已透传
   `:goods-id`，组件按「同分类 Top N」默认取数 + 手选补充；先核对
   goods-ranking 取数链是否支持分类过滤，不支持则给 product 接口加参数
   （双模式编译验证）。

### 批次二

4. video-player（1 天）、floating-service（0.5 天，注意浮层单实例——
   避免历史「全局弹层多实例」教训）、hotspot-map（1.5～2 天，热区编辑器
   是管理端主要工作量）；
5. service-promise 加入商详白名单（随手改常量）。

### 批次三（决策后）

6. article-list（待数据源决策）、review-highlights（可选）。

### 每个新组件的落地清单（通用）

- 管理端：注册表登记（key/中文名/分组）+ 画布预览 + 设置面板 + 预置
  defaults/validate；预览与 C 端逐值对齐（preview-parity 守卫）；
- C 端：`components/diy/diy-<key>/` 组件 + registry 注册；取数走
  retail-data 或组件内 API；主题色接入 themeStore；
- 后端：仅当需要新接口/新参数时改动，boot/cloud 双模式编译与单测；
  白名单常量同步（Java `PageDesignComponentTypes` ↔ TS registry，parity
  测试钉死）；
- 全部批次无数据库改动（当前矩阵内所有组件数据源均为静态 props 或既有
  域接口）。

## 六、边界与非目标

- 不做 CRMEB 式商详整页组件化拼装（productInfo/productDesc/productBottom
  不引入），商详原生骨架不动；
- 不补无业务承载的营销组件：砍价/拼团/预售/积分商城/签到/直播/付费会员；
- CRMEB 调研基于开源版源码，商业版（SaaS）能力（如按单品绑定模板）未能
  验证；变量绑定（textField）与商详多模板属能力增强，不在本方案组件
  扩容范围，如需另行立项。

## 七、批次一落地结果（2026-10-07）

### 落地

- **商详白名单收窄**：管理端 `registry/component-registry.ts`
  `detailPageAllowedComponents` 移除 swiper-banner / image-ad / notice /
  seckill / discount / limited-activity，加入 goods-recommend；后端
  `PageDesignComponentTypes.DETAIL_PAGE_ALLOWED_TYPES` 同步（parity 测试
  钉死两端一致）。分类页白名单两端同步加入 image-cube。
- **image-cube 图片魔方**：
  - 管理端：`extension/types.ts`（布局常量 `IMAGE_CUBE_LAYOUT_CELLS` +
    defaults/validate，空魔方默认校验不通过=刻意设计）、
    `extension-preview.vue` 新增六种布局的画布预览（格距 4px、格高
    188/92/60px）、`extension/image-cube/`（转发预览 + 专用设置面板：
    布局单选切换自动补齐/裁剪格数、每格图片地址 + LinkUrl 跳转、
    CommonStyle）、注册表归入「导航广告」分组。
  - C 端：`diy-image-cube/index.vue` 用**静态 class 承载布局**
    （`grid-auto-rows: 376/184/120rpx` + 大格 span，避免动态 inline rpx
    的平台兼容问题），空格渲染浅灰占位、有图才可点跳转
    （followDecorationLink）；registry + diy/index.vue 静态分支登记。
- **goods-recommend 商品推荐（看了又看）**：
  - 取数链核对结论：`getGoodsPage` 早已支持 `categorySecondId` 过滤 +
    `desc: sales_volume` 排序，**零后端取数改动**；分类解析自当前商品
    （`getById(goodsId).categorySecondId`），解析失败或非商详上下文回落
    全站销量榜；自动结果排除当前商品、不足 count 时按手选 targetIds
    顺序补位；TTL 缓存 key 含 goodsId。
  - 管理端：defaults/validate（manual 模式手选必填）+ 通用设置面板分支
    （标题/数据来源/数量/缓存/价格开关）+ 画布横滑卡占位预览 + 注册表
    归入「商品经营」分组；商详白名单加入。
  - C 端：`diy-goods-recommend/index.vue` 横滑卡（scroll-view scroll-x，
    卡 200rpx/圆角 16rpx/主题色价格/划线原价按 original>sales 口径），
    列表为空整组件隐藏；`diy/index.vue` 把此前未下发的 `goodsId` prop
    传给组件；registry 登记。
  - 后端：`PageDesignComponentTypes` 新增 GOODS_RECOMMEND/IMAGE_CUBE 常量
    入 KNOWN_TYPES；GOODS_RECOMMEND 入 DATA_DRIVEN_TYPES（手选为空发布
    阻断）且 targetIds 按商品 ID 校验存在性
    （`DefaultPageDesignDocumentValidator`）。
- **存量缺口顺手修复**：管理端组件库面板（`component-library.vue`）此前
  只列 legacy+retail，6 个已注册的 extension 组件（瀑布流/优惠券组合/
  服务承诺/底部导航等）在面板里根本拖不到；现把
  `extensionComponentTypes` 并入展示列表，全部扩展组件首次可用。
- 契约守卫扩展：`mobile-renderer-contract.test.ts` 的「移动端注册表 +
  静态渲染分支」检查从 legacy+retail 扩到全部 extension 类型；
  `preview-parity.test.ts` 新增图片魔方（格距/格高/裁切口径）与商品推荐
  （卡宽/圆角/间距）两条 1rpx=0.5px 两端锁定；
  `component-registry.test.ts` extension 计数 6→8 并给 image-cube 空默认
  校验开专门断言。

### 无 SQL 与双模式说明

纯注册表/组件/校验常量改动，取数全部复用既有接口（product app 分页/详情），
无新表新列新接口路径；发布校验在 promotion 模块内，boot/cloud 同构生效。

### 验证

- 管理端：page-designer 域 vitest 185 例全绿（含白名单双端 parity、
  新增 parity 两条）；`pnpm -F @vben/web-ele run typecheck` 通过；改动
  文件 eslint --fix / prettier / stylelint 通过。
- C 端：`pnpm type-check` 通过；改动文件 `npx eslint --fix` 通过；
  全量 vitest 602 例全绿。
- 后端：`mvn clean compile -pl aryn-boot -am` BUILD SUCCESS；
  `DefaultPageDesignDocumentValidatorTest`（31 例）+
  `NewTemplateSeedValidationTest`（1 例）全绿——既有模板种子未被白名单
  收窄误伤（受影响模板均为 pageType=1/3/4，商详白名单收紧不影响）。
