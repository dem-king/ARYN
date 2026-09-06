# AI 辅助工具

> 证据：当前 macOS 主机的命令查找、仓库目录和 `.gitignore`；检查于 2026-08-31；置信度：已验证。

## 当前状态

| 工具 | 当前主机状态 | 本轮策略 |
|---|---|---|
| Graphify | 未发现 `graphify` 命令或 `graphify-out/` | 使用源码、配置、Git 和原生搜索回退；不声明图谱结论 |
| Understand Anything | 未发现可执行命令、`.ua/` 或 `.understand-anything/` | 不声明 dashboard/tour 可用 |

2026-07-16 的首次初始化曾在另一台 Windows 主机生成 Graphify 结构图，并记录 17,431 个节点、41,948 条边；这些是历史快照，不代表当前分支或当前主机状态。本轮没有获得安装授权，因此没有联网安装或刷新工具。

## Graphify

安装并验证后，可在仓库根目录使用：

```bash
graphify update . --no-cluster
graphify explain ArynTenantLineHandler
graphify path "node A" "node B"
graphify query "question" --budget 1200
graphify tree --graph graphify-out/graph.json \
  --output graphify-out/GRAPH_TREE.html \
  --root "$PWD" --label "悦航购 Aetheryn Mall"
```

Graphify 适合调用者、依赖路径和共享代码影响范围分析。没有实际生成并验证 `graphify-out/graph.json` 时，不得引用历史节点数或声称当前图谱可用。

## Understand Anything

安装并完成项目分析后，才可记录 `.understand-anything/knowledge-graph.json` 或启动 dashboard。启动时必须使用工具输出的完整 `http://127.0.0.1:<port>/?token=<token>` 地址；没有图文件或 token 时视为不可用。

UA 适合陌生模块、跨端业务流和架构导览。当前回退方式是先读 `docs/README.md` 与目标层文档，再按 Controller/API/Entity/SQL 进行源码检查。

## 安装与刷新规则

- 安装外部工具前必须获得用户明确授权，并优先使用固定版本或已审阅安装器。
- 工具输出只用于发现关系；可复用结论仍回写 `docs/`，并标明证据、日期和置信度。
- 只有工具实际可用且改动影响已记录关系时才刷新图谱，不为普通局部改动强制生成。

## Git 规则

以下均为本机生成物，不提交：

```gitignore
graphify-out/
.ua/
.understand-anything/
.codex/hooks.json
```
