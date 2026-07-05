# AI 辅助工具

> Harness 自动化验证管道、静态检查脚本、自进化循环。

## 1. Harness 验证管道

### 1.1 完整验证流程

```bash
# 后端全量验证
python3 scripts/validate.py --backend

# 前端全量验证
python3 scripts/validate.py --frontend
```

### 1.2 后端验证步骤

`python3 scripts/validate.py --backend` 等价于依次执行：

| 步骤 | 命令 | 说明 |
|:----:|------|------|
| 1 | `mvn clean compile -pl aryn-boot -am` | 编译 |
| 2 | `python3 scripts/lint-deps.py` | 依赖层级检查 |
| 3 | `python3 scripts/lint-quality.py` | 代码质量检查 |
| 4 | `mvn clean test -pl aryn-boot -am` | 运行测试 |
| 5 | `python3 scripts/verify/run.py` | 端到端功能验证 |

### 1.3 前端验证步骤

`python3 scripts/validate.py --frontend` 包含：

| 步骤 | 命令 | 说明 |
|:----:|------|------|
| 1 | `pnpm build` | 构建 |
| 2 | `node scripts/lint-deps.mjs` | monorepo 依赖检查 |
| 3 | `pnpm lint` | ESLint 检查 |
| 4 | `pnpm typecheck` | TypeScript 类型检查 |

## 2. 静态检查脚本

### 2.1 依赖层级检查

| 脚本 | 适用项目 | 说明 |
|------|----------|------|
| `scripts/lint-deps.py` | Java 后端 | 检查 L0-L5 层级依赖是否违规 |
| `scripts/lint-deps.mjs` | UI monorepo | 检查 @vben 包层级依赖是否违规 |

**违规示例**：

- `aryn-order-biz` 直接 import `com.aryn.cloud.user`（biz 间禁止直接 import，应通过 Dubbo RPC）
- `aryn-common-core` import `com.aryn.cloud.upms`（L0 禁止依赖高层）

### 2.2 代码质量检查

```bash
python3 scripts/lint-quality.py
```

检查项：

| 检查项 | 说明 |
|--------|------|
| 文件行数 | 单文件不超过 500 行 |
| 禁止模式 | `System.out.println()`, `printStackTrace()` |
| 命名规范 | PascalCase(类) / camelCase(方法/变量) |
| 逻辑删除 | 确保使用 `del_flag` 而非物理删除 |

### 2.3 操作前预验证

```bash
# 创建文件前验证
python3 scripts/verify_action.py --action "create file aryn-order-biz/.../XxxService.java"

# 添加 import 前验证
python3 scripts/verify_action.py --action "import com.aryn.cloud.upms from com.aryn.cloud.order"
```

| 操作 | 验证内容 |
|------|----------|
| 创建文件 | 检查目录是否存在、文件是否已存在、命名是否合规 |
| 添加 import | 检查层级依赖是否违规 |

## 3. 自进化循环 (Critic → Refiner)

### 3.1 工作流

```
Agent 执行 → validate → 失败自动 record_failure → 定期跑 critic 分析 → refiner 修复规则 → 下一代 Agent 受益
```

### 3.2 脚本说明

| 脚本 | 用途 |
|------|------|
| `scripts/record_failure.py` | 记录验证失败事件到 `harness/trace/failures/` |
| `scripts/critic.py` | 分析失败模式，生成结构化报告 |
| `scripts/refiner.py` | 读取 Critic 报告，自动更新 LAYER_MAP 等规则 |
| `scripts/record_memory.py` | 记录情景记忆（教训）、程序记忆（成功步骤） |

### 3.3 记忆类型

| 类型 | 说明 | 示例 |
|------|------|------|
| 情景记忆 | 踩坑教训 | "aryn-order-biz 不能直接 import aryn-user-biz，需通过 Dubbo RPC" |
| 程序记忆 | 成功步骤 | "添加新 Controller 的步骤：1. 创建 Controller 类 2. 添加 @RestController 3. ..." |

## 4. 执行计划与任务状态

| 目录 | 说明 |
|------|------|
| `docs/exec-plans/` | 执行计划文件 |
| `harness/tasks/` | 任务状态文件 |
| `harness/trace/failures/` | 验证失败记录 |