# AI 辅助工具

## Graphify

### 状态

- 安装方式：`uv tool install "graphifyy[sql]"`
- 命令位置：用户级 `uv` tool 环境，可直接执行 `graphify`
- 图谱：`graphify-out/graph.json`
- 静态树：`graphify-out/GRAPH_TREE.html`
- 初始化统计：2,210 个代码文件首轮扫描；补齐 SQL 解析器后为 17,431 个节点、41,948 条边。
- 说明：本次未配置 Gemini/Google API key，图谱是 AST/关系结构分析，不是 LLM 语义总结。

### 刷新和查询

```powershell
graphify update . --no-cluster
graphify explain PageDesignController
graphify explain ArynTenantLineHandler
graphify tree --graph graphify-out/graph.json --output graphify-out/GRAPH_TREE.html --root "D:\Codes\aryn-mall" --label "悦航购 Aetheryn Mall"
```

Graphify 主要用于命令行查询；`GRAPH_TREE.html` 是本地静态树，不是常驻服务。

## Understand Anything

### 状态

- 官方仓库：`C:\Users\admin\.understand-anything\repo`
- 通用插件链接：`C:\Users\admin\.understand-anything-plugin`
- Codex skill 链接：`C:\Users\admin\.agents\skills\understand*`
- core 包已完成 `pnpm --filter @understand-anything/core build`。
- 项目数据目录：`.ua/`，已生成 `.understandignore` 和中文配置。
- 当前尚未生成 `.ua/knowledge-graph.json`，因此不能声明 dashboard 可用。

安装发生在当前会话，重启 Codex 后技能才能正常出现在新任务中。首次完整语义分析：

```text
$understand D:\Codes\aryn-mall --language zh
```

该仓库超过 2,000 个代码文件，完整语义分析会分批运行；可按 `aryn-mall-java`、`aryn-mall-ui`、`aryn-mall-uniapp` 分域生成后再合并。

### Dashboard

只有 `.ua/knowledge-graph.json` 生成并通过验证后再启动：

```powershell
cd C:\Users\admin\.understand-anything-plugin\packages\dashboard
$env:GRAPH_DIR='D:\Codes\aryn-mall'
pnpm exec vite --host 127.0.0.1
```

必须使用启动日志输出的完整 `http://127.0.0.1:<port>/?token=<token>` URL，token 不可省略。本轮没有启动 dashboard。

## Git 规则

以下均为本机生成物，不提交：

```gitignore
graphify-out/
.ua/
.understand-anything/
.codex/hooks.json
```
