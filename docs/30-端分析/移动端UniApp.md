# 移动端 UniApp

> 详细架构信息参见 [ARCHITECTURE.md](../ARCHITECTURE.md)。

## 1. 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| UniApp | — | 跨平台框架 |
| Vue | 3 | Composition API |
| TypeScript | — | 类型安全 |
| Alova | 3.x | HTTP 请求库 |
| wot-design-uni | — | UI 组件库 |
| uni-mini-router | — | 文件路由 |
| Pinia | — | 状态管理 |

## 2. 项目结构

```
aryn-mall-uniapp/
├── src/
│   ├── api/                  # API 层 (7 模块, 60+ 接口)
│   │   ├── core/             #   Alova 实例 + 中间件
│   │   ├── auth.ts           #   认证 API
│   │   ├── user.ts           #   用户 API
│   │   ├── goods.ts          #   商品 API
│   │   ├── order.ts          #   订单 API
│   │   ├── shoppingCart.ts   #   购物车 API
│   │   ├── pay.ts            #   支付 API
│   │   └── coupon.ts         #   优惠券 API
│   ├── store/                # 状态层 (6 个 Pinia Store)
│   │   ├── auth.ts           #   认证状态
│   │   ├── user.ts           #   用户信息
│   │   ├── goods.ts          #   商品状态
│   │   ├── shoppingCart.ts   #   购物车状态
│   │   ├── dict.ts           #   字典缓存
│   │   └── searchHistory.ts  #   搜索历史
│   ├── composables/          # 逻辑复用 (7 个)
│   ├── components/           # 通用组件 (22 个)
│   ├── pages/                # 页面
│   │   ├── index/            #   首页 (主包)
│   │   ├── category/         #   分类 (主包)
│   │   ├── cart/             #   购物车 (主包)
│   │   ├── mine/             #   我的 (主包)
│   │   └── ...               #   分包页面 (30 个)
│   └── static/               # 静态资源
└── ...
```

## 3. 页面体系

### 主包页面 (5 个)

| 页面 | 路径 | 说明 |
|------|------|------|
| 首页 | `pages/index` | DIY 装修驱动，后端 JSON 配置 |
| 分类 | `pages/category` | 商品分类浏览 |
| 购物车 | `pages/cart` | 购物车管理 |
| 我的 | `pages/mine` | 个人中心 |
| 搜索 | `pages/search` | 商品搜索 |

### 分包页面 (30 个)

| 分类 | 页面 | 说明 |
|------|------|------|
| 商品 | 商品详情、商品列表、品牌详情 | 商品浏览 |
| 订单 | 订单列表、订单详情、确认订单、退款申请、退款详情 | 订单全流程 |
| 支付 | 支付页面、支付结果 | 支付流程 |
| 用户 | 登录、注册、个人信息、地址管理、地址编辑 | 用户管理 |
| 会员 | 积分明细、签到、余额、充值 | 会员体系 |
| 营销 | 优惠券列表、拼团详情 | 营销活动 |
| 其他 | 设置、关于、帮助、WebView | 辅助页面 |

## 4. API 层

### 7 个 API 模块，60+ 接口

| 模块 | 文件 | 主要接口 |
|------|------|----------|
| 认证 | `api/auth.ts` | 登录、注册、短信验证码、社交登录 |
| 用户 | `api/user.ts` | 用户信息、地址 CRUD、积分、签到、余额 |
| 商品 | `api/goods.ts` | SPU 列表/详情、SKU、分类、评价、收藏、足迹 |
| 订单 | `api/order.ts` | 订单 CRUD、状态流转、退款、物流 |
| 购物车 | `api/shoppingCart.ts` | 购物车 CRUD、数量修改、选中状态 |
| 支付 | `api/pay.ts` | 支付下单、支付结果查询 |
| 优惠券 | `api/coupon.ts` | 优惠券列表、领取、使用 |

### API 层架构

```
api/core/ (Alova 实例)
  ├── 创建 Alova 实例 (baseURL, timeout)
  ├── 请求拦截器 (添加 token, tenant-id header)
  ├── 响应拦截器 (统一错误处理, 401 跳转登录)
  └── 中间件 (loading, 重试)

api/{domain}/ (按业务域组织)
  └── 每个 API 函数返回 Alova Method 实例
```

> ⚠️ API 层禁止引用页面组件，禁止直接使用 `uni.request`。

## 5. Store 层

### 6 个 Pinia Store

| Store | 文件 | 说明 | 持久化 |
|-------|------|------|:------:|
| authStore | `store/auth.ts` | Token、登录状态 | ✅ |
| userStore | `store/user.ts` | 用户信息、会员等级 | ✅ |
| goodsStore | `store/goods.ts` | 商品浏览状态 | ❌ |
| shoppingCartStore | `store/shoppingCart.ts` | 购物车数量、选中状态 | ✅ |
| dictStore | `store/dict.ts` | 字典数据全局缓存 | ✅ |
| searchHistoryStore | `store/searchHistory.ts` | 搜索历史记录 | ✅ |

> Store 可引用 api 层，禁止引用页面组件。

## 6. 组件体系

### 22 个 Vue 组件

| 分类 | 组件 | 说明 |
|------|------|------|
| 全局组件 | NavBar、TabBar、Empty、Loading | 全局通用 |
| 业务组件 | GoodsCard、OrderCard、CouponCard、AddressCard | 业务展示 |
| 导航组件 | CategoryNav、SearchBar、TabFilter | 导航筛选 |
| DIY 装修 | DiyPage、DiyBanner、DiyGoods、DiyImage、DiyText | 首页装修 |
| 页面私有 | OrderStatusTab、PayChannelSelect、SignInCalendar | 页面专用 |

> 组件禁止引用 pages 目录。

## 7. Composables

### 7 个组合式函数

| Composable | 说明 |
|-----------|------|
| useAuth | 登录/登出/Token 管理 |
| useCart | 购物车操作 |
| usePay | 支付流程 |
| useAddress | 地址选择 |
| useUpload | 文件上传 |
| useDict | 字典数据 |
| useShare | 分享功能 |

> Composables 可引用 api/store，禁止引用 pages。

## 8. 跨平台支持

### 条件编译

项目包含 **124 处条件编译**，主要分布：

| 平台标记 | 说明 | 使用场景 |
|----------|------|----------|
| `MP-WEIXIN` | 微信小程序 | 支付、分享、登录 |
| `H5` | H5 网页 | 支付、分享、路由 |
| `APP` | App | 推送、支付 |

### 跨平台支付

| 平台 | 支付方式 | 实现 |
|------|----------|------|
| 微信小程序 | JSAPI 支付 | `uni.requestPayment` |
| H5 | 微信 H5 支付 / 支付宝 H5 | 跳转支付链接 |
| App | 微信 App 支付 / 支付宝 App | `uni.requestPayment` |

## 9. DIY 装修系统

首页由后端 JSON 配置驱动，前端根据配置动态渲染组件：

```
后端 page_design 表 → JSON 配置
    → 前端 DiyPage 组件解析
    → 按序渲染 DiyBanner / DiyGoods / DiyImage / DiyText 等组件
    → 每个组件读取自身 config 渲染
```

| 组件 | 配置项 |
|------|--------|
| DiyBanner | 轮播图列表、自动播放、间隔时间 |
| DiyGoods | 商品列表、展示样式(列表/网格)、数量 |
| DiyImage | 图片 URL、跳转链接 |
| DiyText | 文本内容、字体大小、颜色 |