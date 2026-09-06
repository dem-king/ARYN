# 悦航购项目 Onboarding 初始化实施计划

> **For Codex:** 按 `project-onboarding-vibe` 流程逐项执行并验证，不覆盖现有用户改动。

> **2026-08-31 刷新状态：** 已按新版流程重新分类为 brownfield/full，刷新分层文档、配送/营销现状和风险台账，并通过 `validate_onboarding.sh`（0 warning）及 `git diff --check`。当前主机未安装 Graphify/Understand Anything，因此任务 2 的历史产物不在本轮重建，使用源码检查回退；Maven/pnpm 全量构建和运行态联调未执行。

**目标：** 为悦航购建立可持续维护的中文项目知识层，使后续自然语言需求可从文档快速定位到代码、接口与验证命令。

**架构：** 以 Markdown 作为长期记忆，以 Graphify 和 Understand Anything 作为按需代码侦察工具。文档按总览、开发、业务数据、端分析、接口风险、需求和归档七层组织，并由 `docs/README.md` 统一索引。

**技术栈：** Spring Boot 3、Spring Cloud Alibaba、Dubbo、Vue 3、Vite、TypeScript、UniApp、Maven、pnpm、Graphify、Understand Anything。

---

### 任务 1：建立可信项目清单

**读取：** 根 POM、前端清单、页面路由、API、Controller、实体、SQL、现有 Git 历史文档。

**验收：** 明确子项目、入口、依赖层级、运行时要求、当前工作区风险。

### 任务 2：初始化代码情报工具

**按需生成：** 工具已安装且本轮确有关系/架构侦察需要时，生成 `graphify-out/graph.json`、`graphify-out/GRAPH_TREE.html` 或 UA knowledge graph；工具不可用时记录源码检查回退。

**验收：** 记录节点/边/层/导览统计，生成物保持 Git 忽略。

### 任务 3：生成分层项目文档

**创建：** `docs/README.md` 及 `00`、`10`、`20`、`30`、`40`、`50`、`90` 分层目录。

**验收：** 每层有索引；自然语言功能可映射到前端页面、API、后端入口和数据表。

### 任务 4：更新 Agent 导航

**修改：** `AGENTS.md`。

**验收：** 包含真实有效的文档入口、Git 约定、验证命令、工具使用规则和知识回写规则。

### 任务 5：验证交付

**运行：** Markdown 链接检查、Graphify/UA 产物检查、Git diff 检查。

**验收：** 不声明未执行的构建成功；静态推断与运行验证明确区分。
