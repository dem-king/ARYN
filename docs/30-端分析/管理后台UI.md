# 管理后台 UI

> 详细架构信息参见 [ARCHITECTURE.md](../ARCHITECTURE.md)。

## 1. 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | Composition API |
| Vite | 6 | 构建工具 |
| TypeScript | 5.8 | 类型安全 |
| Element Plus | 2.9 | UI 组件库 |
| Vben Admin | 3.2.1 | 管理后台基座 |
| Pinia | — | 状态管理 |
| pnpm | — | 包管理 (monorepo) |

## 2. 项目结构

```
aryn-mall-ui/
├── apps/
│   └── web-ele/                  # Element Plus 管理后台应用 (L5)
│       ├── src/
│       │   ├── views/            # 页面组件 (95+ 页面)
│       │   ├── api/              # API 请求 (64 个文件)
│       │   ├── router/           # 路由配置
│       │   └── locales/          # 国际化
│       └── ...
├── packages/
│   ├── @core/                    # 核心基础包 (@vben-core/*)
│   │   ├── typings/              # L0: 类型定义
│   │   ├── shared/               # L1: 共享工具
│   │   ├── preferences/          # L2: 偏好设置
│   │   ├── composables/          # L3: 组合式函数
│   │   ├── form-ui/              # L4: 表单 UI
│   │   ├── layout-ui/            # L4: 布局 UI
│   │   └── menu-ui/              # L4: 菜单 UI
│   ├── types/                    # L0: 业务类型 (@vben/types)
│   ├── utils/                    # L1: 工具函数 (@vben/utils)
│   ├── constants/                # L1: 常量 (@vben/constants)
│   ├── icons/                    # L1: 图标 (@vben/icons)
│   ├── locales/                  # L1: 国际化 (@vben/locales)
│   ├── stores/                   # L2: 状态管理 (@vben/stores)
│   ├── preferences/              # L2: 偏好 (@vben/preferences)
│   ├── access/                   # L3: 权限控制 (@vben/access)
│   ├── hooks/                    # L3: 钩子 (@vben/hooks)
│   ├── request/                  # L3: 请求封装 (@vben/request)
│   ├── plugins/                  # L3: 插件 (@vben/plugins)
│   ├── layouts/                  # L4: 布局 (@vben/layouts)
│   └── styles/                   # L4: 样式 (@vben/styles)
└── ...
```

## 3. 层级依赖

```
L0 (类型):    @vben-core/typings → @vben/types
                  (无内部依赖)

L1 (工具):    @vben-core/shared, @vben/utils, @vben/constants,
              @vben/icons, @vben/locales
                  (仅依赖 L0)

L2 (状态):    @vben-core/preferences, @vben/stores, @vben/preferences
                  (依赖 L0-L1 + pinia)

L3 (效果):    @vben/access, @vben/hooks, @vben/request,
              @vben/plugins, @vben-core/composables
                  (依赖 L0-L2)

L4 (UI):      @vben/layouts, @vben/styles,
              @vben-core/*-ui (form-ui, layout-ui, menu-ui…)
                  (依赖 L0-L3 + Element Plus)

L5 (应用):    @vben/web-ele
                  (依赖所有低层)
```

## 4. 业务域与页面

| 业务域 | 页面数 | 主要页面 |
|--------|:------:|----------|
| 系统管理 | 15+ | 用户管理、角色管理、菜单管理、部门管理、字典管理、日志管理、租户管理 |
| 会员管理 | 10+ | C端用户、会员等级、积分管理、签到管理、余额管理、充值管理 |
| 商品管理 | 15+ | SPU管理、SKU管理、分类管理、规格管理、品牌管理、评价管理 |
| 订单管理 | 10+ | 订单列表、退款管理、物流管理、订单统计 |
| 支付管理 | 5+ | 支付订单、退款流水、支付配置 |
| 营销管理 | 15+ | 优惠券管理、拼团管理、分销管理、页面装修 |
| 素材管理 | 5+ | 文件上传、素材库 |
| 数据统计 | 10+ | 用户统计、订单统计、商品统计 |
| 系统配置 | 5+ | 系统参数、物流公司、通知管理 |

> 合计 **95+** 页面。

## 5. API 层

- **64 个 API 文件**，**225+** 接口
- 位于 `apps/web-ele/src/api/` 目录
- 按业务域组织：`api/upms/`, `api/user/`, `api/order/`, `api/pay/`, `api/product/`, `api/promotion/`

### API 请求约定

```typescript
// 统一使用 @vben/request 封装
import { requestClient } from '#/api/request';

// GET 请求
export function getUserList(params: UserListParams) {
  return requestClient.get<Result<PageResult<UserVO>>>('/admin/upms/user/page', { params });
}

// POST 请求
export function createUser(data: UserForm) {
  return requestClient.post('/admin/upms/user', data);
}
```

## 6. 权限体系

### 认证方式

| 维度 | 实现 |
|------|------|
| 认证框架 | Sa-Token (无状态 JWT) |
| Token 传递 | Authorization Header |
| 多租户 | tenant-id Header |
| 路由模式 | 后端驱动动态路由 (accessMode: backend) |

### 权限控制

| 层级 | 方式 | 说明 |
|------|------|------|
| 路由级 | 动态路由 | 后端返回菜单数据，前端动态生成路由 |
| 按钮级 | `v-action` 指令 | `v-action="'user:xxx:yyy'"` |
| API 级 | 后端 `@SaCheckPermission` | 后端接口权限校验 |

### 双模式切换

| 环境变量 | 说明 |
|----------|------|
| `VITE_OPEN_BOOT` | 单体模式：`true`，微服务模式：`false` |

## 7. 公共组件

**11 个业务公共组件**：

| 组件 | 说明 |
|------|------|
| DictTag | 字典标签渲染 |
| FormModal | 弹窗表单 |
| ImageUpload | 图片上传 |
| FileUpload | 文件上传 |
| RichTextEditor | 富文本编辑器 |
| TreeSelect | 树形选择 |
| CategoryCascader | 分类级联选择 |
| SpecEditor | 规格编辑器 |
| SkuTable | SKU 表格编辑 |
| PageDesignEditor | 页面装修编辑器 |
| AddressSelector | 地址选择器 |

## 8. 包清单

### @vben 包 (8 个)

| 包名 | 层级 | 说明 |
|------|:----:|------|
| @vben/types | L0 | 业务类型定义 |
| @vben/utils | L1 | 工具函数 |
| @vben/constants | L1 | 常量 |
| @vben/icons | L1 | 图标 |
| @vben/locales | L1 | 国际化 |
| @vben/stores | L2 | 状态管理 |
| @vben/preferences | L2 | 偏好设置 |
| @vben/access | L3 | 权限控制 |
| @vben/hooks | L3 | 钩子 |
| @vben/request | L3 | 请求封装 |
| @vben/plugins | L3 | 插件 |
| @vben/layouts | L4 | 布局 |
| @vben/styles | L4 | 样式 |

### @vben-core 包 (10 个)

| 包名 | 层级 | 说明 |
|------|:----:|------|
| @vben-core/typings | L0 | 核心类型 |
| @vben-core/shared | L1 | 共享工具 |
| @vben-core/preferences | L2 | 核心偏好 |
| @vben-core/composables | L3 | 组合式函数 |
| @vben-core/form-ui | L4 | 表单 UI |
| @vben-core/layout-ui | L4 | 布局 UI |
| @vben-core/menu-ui | L4 | 菜单 UI |

### effects 包 (6 个)

| 包名 | 说明 |
|------|------|
| @vben/effects | 副作用入口 |
| @vben/effects-access | 权限副作用 |
| @vben/effects-common | 公共副作用 |
| @vben/effects-layout | 布局副作用 |
| @vben/effects-plugins | 插件副作用 |
| @vben/effects-request | 请求副作用 |