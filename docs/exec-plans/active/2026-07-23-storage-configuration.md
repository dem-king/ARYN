# 文件存储统一配置设计

状态：已完成（2026-07-23）

## 目标

管理后台按租户配置并切换本机、阿里 OSS、七牛、腾讯 COS、MinIO。每个租户允许保存多条配置，但同一时间仅启用一条；上传、素材记录和本地文件读取沿用现有租户隔离。

## 类型与兼容

新配置统一使用 `local`、`aliyun`、`qiniu`、`tencent`、`minio`。历史 `1/2/3/4/oss` 继续可读，并由迁移脚本转换为描述性类型。四种对象存储共用 S3 兼容实现，根据类型和 endpoint 推导签名区域；MinIO 默认使用 path-style，七牛要求配置公开访问域名。

## 本机存储

`bucket` 保存服务器本地根目录，文件写入 `{root}/{tenantId}/{uuid}.{ext}`。Cloud Docker 默认使用 `/data/aryn/uploads` 命名卷；Boot 可配置任意进程可写目录。未配置访问域名时返回 `/boot/file/local/**` 或 `/upms/file/local/**` 相对 URL，配置域名时返回绝对 URL。

## 安全与一致性

服务端校验类型、状态、凭据、endpoint、bucket、path-style 和域名，保存与禁用旧配置处于同一事务。AccessKey 与 AccessSecret 在查询响应中脱敏，编辑未改动时保留原值。本地读取只接受数字租户 ID 和服务端生成的 UUID 图片名；上传同时校验大小、扩展名、MIME 和文件头。

## 验证

增加类型兼容、配置校验、本地落盘与 URL、SQL 双模式契约测试；运行 Upms Maven 测试和管理端类型检查，并在当前 Cloud 环境配置本地存储进行真实上传验证。

- 后端存储针对性测试 18 项通过，Cloud profile 可执行 JAR 打包通过。
- 管理端 `@vben/web-ele` 类型检查和任务文件 ESLint/Prettier 通过，两套 Docker Compose 配置校验通过。
- 当前 Cloud 库完成 `21/22/23` 迁移，Nacos 完成本地文件公开路由迁移；Upms 与 Gateway 使用最新镜像且健康。
- 当前平台租户启用本地存储 `/data/aryn/uploads`，真实 PNG 上传、命名卷落盘和 Gateway 读取均成功，读取内容 SHA-256 与源文件一致。
