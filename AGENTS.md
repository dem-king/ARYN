# 悦航购 Aetheryn Mall — Agent Guide

> 本文档是 AI Agent 的导航地图，控制在 ~100 行。详细信息请按链接加载对应文档。

## 项目概览

悦航购是一套电商 SaaS 系统，支持单体/微服务双模式运行。四个业务子项目通过 API 通信，另含一份独立调度源码：

| 目录 | 技术栈 | 定位 |
|------|--------|------|
| [aryn-mall-java/](aryn-mall-java/) | Spring Boot 3 + Spring Cloud Alibaba + Dubbo | 后端服务 |
| [aryn-mall-ui/](aryn-mall-ui/) | Vue 3 + Vite + TypeScript (monorepo) | 管理后台 |
| [aryn-mall-uniapp/](aryn-mall-uniapp/) | UniApp + Vue 3 + TypeScript | C 端移动商城 |
| [aryn-mall-delivery-uniapp/](aryn-mall-delivery-uniapp/) | UniApp + Vue 3 + TypeScript | TOB 配送员独立小程序 |
| [xxl-job-3.2.0/](xxl-job-3.2.0/) | XXL-JOB 3.2.0 + Spring Boot | 独立上游调度源码，不随 `aryn-boot` 构建 |

## 快速链接

- [项目知识库](docs/README.md) — 固定阅读顺序和常用入口
- [项目总览](docs/00-项目总览/项目总览.md) — 系统边界、部署形态和主要风险
- [开发工作流](docs/10-开发指南/开发工作流.md) — 定位、修改、验证和知识回写
- [功能到代码索引](docs/20-业务与数据/功能到代码索引.md) — 从业务语言定位三端代码
- [接口与风险](docs/40-接口与风险/README.md) — 路径映射、缺失模块和风险台账

## 构建命令速查

```bash
# === 后端 ===
cd aryn-mall-java && mvn clean compile -pl aryn-boot -am     # 编译单体模式
cd aryn-mall-java && mvn clean test -pl aryn-boot -am        # 运行测试
cd aryn-mall-java && mvn clean install -DskipTests           # 全量打包

# === 管理后台 ===
cd aryn-mall-ui && pnpm install && pnpm dev:ele               # 启动管理后台
cd aryn-mall-ui && pnpm build:ele                              # 构建管理后台
cd aryn-mall-ui && pnpm lint                                   # ESLint + Prettier

# === 移动端 ===
cd aryn-mall-uniapp && pnpm install && pnpm dev:mp-weixin     # 微信小程序（需 Node >= 22）
cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit

# === 配送员小程序 ===
cd aryn-mall-delivery-uniapp && pnpm install && pnpm dev:mp-weixin
cd aryn-mall-delivery-uniapp && pnpm type-check && pnpm test:unit

# === 当前可靠验证 ===
cd aryn-mall-java && mvn test -pl aryn-boot -am
cd aryn-mall-ui && pnpm check:type && pnpm test:unit && pnpm lint
cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit && pnpm build:mp-weixin
cd aryn-mall-delivery-uniapp && pnpm type-check && pnpm test:unit && pnpm build:mp-weixin
```

## 分层规则

### 后端 (Java) — 6 层架构

| 层 | 模块 | 规则 |
|:--:|------|------|
| L0 | `aryn-common-core` | 纯基础类型，**禁止 import 任何 aryn 内部包** |
| L1 | `common-log/redis/mybatis/storage/sms/swagger/job/dubbo` | 基础设施，仅依赖 L0 |
| L2 | `common-security/sentinel/seata/logistics` | 中间件封装，依赖 L0-1 |
| L3 | `aryn-*-api` (order-api, user-api …) | 接口 + DTO 定义，依赖 L0-2 |
| L4 | `aryn-*-biz` (order-biz, user-biz …) | 业务实现，依赖 L0-3 |
| L5 | `gateway / auth / boot / visual` | 入口层，依赖 L0-4 |

> **核心约束**：高层可依赖低层，反向禁止。不同 biz 模块之间**不可直接 import**（通过 Dubbo RPC 通信）。

### 管理后台 (UI monorepo) — 5 层架构

| 层 | 包名 | 规则 |
|:--:|------|------|
| L0 | `@vben-core/typings`, `@vben/types` | 类型定义，无内部业务依赖 |
| L1 | `@vben-core/shared`, `@vben/utils`, `@vben/constants`, `@vben/icons`, `@vben/locales` | 工具/常量，依赖 L0 |
| L2 | `@vben-core/preferences`, `@vben/stores`, `@vben/preferences` | 状态管理，依赖 L0-1 |
| L3 | `@vben/access`, `@vben/hooks`, `@vben/request`, `@vben/plugins`, `@vben-core/composables` | 副作用层，依赖 L0-2 |
| L4 | `@vben/layouts`, `@vben/styles`, `@vben-core/*-ui` | UI 组件/布局，依赖 L0-3 |
| L5 | `@vben/web-ele` (apps) | 应用层，可依赖所有低层 |

### 移动端 (UniApp) — 单项目分层

| 目录 | 定位 |
|------|------|
| `src/api/` | API 层 (Alova + 中间件)，禁止引用页面组件 |
| `src/store/` | 状态层 (Pinia)，可引用 api |
| `src/composables/` | 逻辑复用，可引用 api/store |
| `src/components/` | 通用组件，禁止引用 pages |
| `src/pages/` | 页面层，可引用所有下层 |

## 质量标准 (三项目通用)

- Java: `@Slf4j` 结构化日志，**禁止** `System.out.println()` / `printStacktrace()`
- TypeScript/Vue: 通过 ESLint + Prettier 格式化
- Java 命名: PascalCase(类) / camelCase(方法/变量)，lombok 简化 getter/setter
- TS 命名: PascalCase(组件/类型) / camelCase(函数/变量)
- 所有逻辑删除用 `del_flag` 字段，禁止物理删除
- 涉及多表操作需保证事务一致性

## Harness 自动化（历史基线）

> 当前工作树中 `scripts/` 与 `harness/` 处于删除状态。以下表格用于说明原设计，恢复前不得执行或声称通过；当前验证使用上面的 Maven/pnpm 原生命令。

### 静态验证

| 脚本 | 用途 |
|------|------|
| `scripts/validate.py` | 统一验证入口 (build → lint-arch → lint-quality → test → verify) |
| `scripts/lint-deps.py` | Java 后端层级依赖检查 |
| `scripts/lint-deps.mjs` | UI monorepo 层级依赖检查 |
| `scripts/lint-quality.py` | Java 代码质量检查 |
| `scripts/verify_action.py` | 创建文件/添加 import 前的预验证 |
| `scripts/verify/run.py` | 端到端功能验证脚本 |

### 自进化循环 (Critic → Refiner)

| 脚本 | 用途 |
|------|------|
| `scripts/record_failure.py` | 记录验证失败事件到 harness/trace/failures/ |
| `scripts/critic.py` | 分析失败模式，生成结构化报告 |
| `scripts/refiner.py` | 读取 Critic 报告，自动更新 LAYER_MAP 等规则 |
| `scripts/record_memory.py` | 记录情景记忆（教训）、程序记忆（成功步骤） |

**工作流**: Agent 执行 → validate → 失败自动 record_failure → 定期跑 critic 分析 → refiner 修复规则 → 下一代 Agent 受益。

> 执行计划文件存放在 `docs/exec-plans/`，任务状态存放在 `harness/tasks/`。

## Git 与知识维护

- 当前主开发分支为 `dev`，远端为 `origin`；新分支默认使用 `codex/<topic>`，除非用户指定其他约定。
- Git 提交信息的标题和正文必须使用中文；禁止使用英文提交标题或英文正文。
- 禁止创建、使用或切换 Git worktree；所有操作必须在当前工作目录完成。
- 工作区可能包含用户未提交修改；不得回退、覆盖或顺带格式化无关文件。
- 先读 [docs/README.md](docs/README.md)，再按目标业务读取对应层文档；不要每次从头扫描全仓。
- 稳定结论回写 `docs/`；重大需求放入 `docs/50-需求文档/YYYY-MM-DD-需求名/`，完成结果登记到 `docs/90-记录归档/需求记录.md`。
- 修改实体、租户、认证、菜单、公共请求层或共享工具时使用 Graphify 检查影响范围；UA 用于陌生模块和跨域全景分析。
- `graphify-out/`、`.ua/`、`.understand-anything/`、`.codex/hooks.json` 是本地生成物，保持 Git 忽略。

## 架构决策记录

- **单体/微服务双模式**：`aryn-boot` 聚合所有 biz 模块用于快速部署；拆分后各 biz 通过 Dubbo RPC 通信
- **多租户隔离**：所有表含 `tenant_id`，通过 MyBatis 拦截器自动注入
- **API 与 Biz 分离**：每个业务模块分为 `*-api`（接口+DTO）和 `*-biz`（实现），支持 Dubbo 远程调用
- **SaaS 权限模型**：Sa-Token + RBAC，管理端与 C 端分离认证
