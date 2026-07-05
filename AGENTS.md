# 悦航购 Aetheryn Mall — Agent Guide

> 本文档是 AI Agent 的导航地图，控制在 ~150 行。详细信息请按链接加载对应文档。

## 项目概览

悦航购是一套电商 SaaS 系统，支持单体/微服务双模式运行。三个独立子项目通过 API 通信：

| 目录 | 技术栈 | 定位 |
|------|--------|------|
| [aryn-mall-java/](aryn-mall-java/) | Spring Boot 3 + Spring Cloud Alibaba + Dubbo | 后端服务 |
| [aryn-mall-ui/](aryn-mall-ui/) | Vue 3 + Vite + TypeScript (monorepo) | 管理后台 |
| [aryn-mall-uniapp/](aryn-mall-uniapp/) | UniApp + Vue 3 + TypeScript | C 端移动商城 |

## 快速链接

- [架构总览](docs/ARCHITECTURE.md) — 分层规则、模块依赖、数据流
- [开发指南](docs/DEVELOPMENT.md) — 构建/测试/lint 命令与环境配置
- [业务上下文](docs/PRODUCT_SENSE.md) — 电商领域术语、业务流程
- [文档总索引](docs/README.md) — 分层文档体系入口
- [功能→代码索引](docs/20-业务与数据/功能到代码索引.md) — 功能模块文件映射
- [接口映射](docs/40-接口与风险/接口映射.md) — 前后端 API 完整映射

## 文档体系

分层文档按编号递进阅读：00(总览) → 10(开发) → 20(业务) → 30(端分析) → 40(接口) → 50(需求) → 90(归档)

| 编号 | 目录 | 定位 |
|:----:|------|------|
| 00 | [项目总览](docs/00-项目总览/) | 项目定位、环境启动、术语表 |
| 10 | [开发指南](docs/10-开发指南/) | 工作流、知识沉淀规则、AI 工具 |
| 20 | [业务与数据](docs/20-业务与数据/) | 功能索引、业务流程、数据模型 |
| 30 | [端分析](docs/30-端分析/) | Java 后端 / 管理后台 / 移动端详情 |
| 40 | [接口与风险](docs/40-接口与风险/) | API 映射、缺失模块清单 |
| 50 | [需求文档](docs/50-需求文档/) | 需求规格 |
| 90 | [记录归档](docs/90-记录归档/) | 历史需求记录 |

## 构建命令速查

```bash
# === 后端 ===
cd aryn-mall-java && mvn clean compile -pl aryn-boot -am     # 编译单体模式
cd aryn-mall-java && mvn clean test -pl aryn-boot -am        # 运行测试
cd aryn-mall-java && mvn clean install -DskipTests           # 全量打包

# === 管理后台 ===
cd aryn-mall-ui && pnpm install && pnpm dev                   # 启动开发服务器
cd aryn-mall-ui && pnpm build                                  # 构建
cd aryn-mall-ui && pnpm lint                                   # ESLint + Prettier

# === 移动端 ===
cd aryn-mall-uniapp && pnpm install && pnpm dev:mp-weixin     # 微信小程序
cd aryn-mall-uniapp && pnpm lint                               # ESLint

# === Harness 验证 ===
python3 scripts/validate.py --backend                           # 后端全量验证
python3 scripts/validate.py --frontend                          # 前端全量验证
python3 scripts/verify_action.py --action "<操作描述>"           # 操作前预验证
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

- 单文件不超过 **500 行**
- Java: `@Slf4j` 结构化日志，**禁止** `System.out.println()` / `printStacktrace()`
- TypeScript/Vue: 通过 ESLint + Prettier 格式化
- Java 命名: PascalCase(类) / camelCase(方法/变量)，lombok 简化 getter/setter
- TS 命名: PascalCase(组件/类型) / camelCase(函数/变量)
- 所有逻辑删除用 `del_flag` 字段，禁止物理删除
- 涉及多表操作需保证事务一致性

## 文档规则

- 稳定知识写入 `docs/`，不要散落在代码注释中
- 先读文档，文档不足时再扫描代码
- 新增功能时同步更新对应文档

## 工具规则

- **Markdown**：记录稳定知识（已知页面/API/实体/流程）
- **Harness 脚本**：自动化验证（层级依赖、代码质量、端到端）
- **小改动**直接改代码；**大改动**先查文档确认影响范围

## Harness 自动化

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

## 架构决策记录

- **单体/微服务双模式**：`aryn-boot` 聚合所有 biz 模块用于快速部署；拆分后各 biz 通过 Dubbo RPC 通信
- **多租户隔离**：所有表含 `tenant_id`，通过 MyBatis 拦截器自动注入
- **API 与 Biz 分离**：每个业务模块分为 `*-api`（接口+DTO）和 `*-biz`（实现），支持 Dubbo 远程调用
- **SaaS 权限模型**：Sa-Token + RBAC，管理端与 C 端分离认证
