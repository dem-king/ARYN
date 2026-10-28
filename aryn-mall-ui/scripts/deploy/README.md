# 管理端 Web 部署说明（`aryn-mall-ui/scripts/deploy`）

本目录是悦航购管理端（`apps/web-ele`）构建产物的 Nginx 镜像部署配置。

## 1. 文件清单

| 文件 | 作用 | 是否进入镜像 |
| --- | --- | --- |
| `Dockerfile` | 基于 `nginx:stable-alpine` 打包 `dist` 静态资源 | 构建文件本身 |
| `aryn-ui.conf` | **默认站点配置**：监听 80、静态站点 + `/api` 反向代理、安全响应头基线 | ✅ 是（`COPY ./aryn-ui.conf`） |
| `aryn-ui-https.conf.example` | 443/HTTPS **示例模板**（80 ACME + 301、443 TLS server） | ❌ 否（仅 `dist` 与 `aryn-ui.conf` 被 COPY） |
| `docker-compose.yml` | 默认部署：只映射 `80:80`，行为保持不变 | ❌ 否 |
| `docker-compose.https.example.yml` | 443 示例：映射 80/443 + 证书目录只读挂载 | ❌ 否 |
| `README.md` | 本文件 | ❌ 否 |

> 文件后缀 `*.example` 有两层保险：Dockerfile 只 COPY 指定文件；nginx 的 `include /etc/nginx/conf.d/*.conf;` 也不会匹配 `*.example`。

## 2. 两种部署形态

### A. 80 明文 HTTP（仓库默认形态）

```
浏览器 ──HTTP:80──> nginx(静态 dist + /api 反向代理) ──> http://aryn-boot:9999/
```

- 配置：`aryn-ui.conf`（`listen 80;`），当前无任何 TLS server 块；
- compose：`docker-compose.yml`，仅映射 `80:80`；
- 适用：内网/测试环境，或 TLS 由外部 CDN / 负载均衡器终止（此时到 nginx 的回源段可能是明文，需保证回源链路处于可信内网，或补 mTLS / 网络隔离）。

```bash
# 1) 构建静态资源（在 aryn-mall-ui 根目录执行）
cd aryn-mall-ui && pnpm build:ele && cd ..

# 2) 把产物放到 deploy 目录：build context 是本目录，Dockerfile 的 COPY ./dist 相对它解析
#    （仓库根 .gitignore 已忽略 dist/，不会误提交构建产物）
cp -r aryn-mall-ui/apps/web-ele/dist aryn-mall-ui/scripts/deploy/dist

# 3) 构建镜像并启动
docker compose -f aryn-mall-ui/scripts/deploy/docker-compose.yml up -d --build
```

### B. 443 HTTPS（模板，需要人工启用）

```
浏览器 ──HTTPS:443──> nginx(TLS 终止 + 静态 dist + /api 反向代理) ──> http://aryn-boot:9999/
浏览器 ──HTTP:80───> 仅 ACME http-01 校验 + 301 跳 HTTPS
```

- 配置：参考 `aryn-ui-https.conf.example`，**启用时必须复制/改名为 `aryn-ui.conf`** （或修改 Dockerfile 的 COPY 源），否则镜像里仍是 80 配置；
- compose：`docker-compose.https.example.yml`，映射 80/443 并挂载证书目录；
- 与默认 `docker-compose.yml` 二选一（容器名、端口冲突，不能同时起）。

## 3. 证书挂载

约定容器内证书路径为 `/etc/nginx/certs/live/<域名>/`：

| 容器内路径                                   | 说明                          |
| -------------------------------------------- | ----------------------------- |
| `/etc/nginx/certs/live/<域名>/fullchain.pem` | 证书链（`ssl_certificate`）   |
| `/etc/nginx/certs/live/<域名>/privkey.pem`   | 私钥（`ssl_certificate_key`） |

推荐做法（见 `docker-compose.https.example.yml`）：

```yaml
volumes:
  - /etc/letsencrypt:/etc/nginx/certs:ro # 只读挂载整个证书目录
```

注意事项：

1. **挂载目录而不是单个文件**。Let's Encrypt 的 `live/*.pem` 是指向 `archive/` 的软链；续期会更新软链目标。若只把 `fullchain.pem` 单文件挂进容器，续期后容器内看到的仍是旧 inode，热 reload 也不会生效。
2. 证书目录只读（`:ro`），私钥不要写进镜像、不要提交仓库。
3. certbot 一般在宿主机运行；容器只负责读证书。续期后用 deploy hook 让 nginx 重新加载：
   ```bash
   certbot renew --deploy-hook "docker exec aryn-ui-pro nginx -s reload"
   ```

## 4. 启用 HTTPS 的步骤

1. 域名解析指向本机，且 **80 端口可从公网访问**（ACME http-01 校验必需）；
2. 申请证书，例如：
   ```bash
   certbot certonly --webroot -w ./certbot-www -d <域名>
   ```
   校验文件会写到 `./certbot-www/.well-known/acme-challenge/`，容器内 `aryn-ui-https.conf.example` 的 80 server 已放行该路径；
3. 复制模板并替换占位符（`example.com` → 真实域名、证书路径、`server_name`）：
   ```bash
   cd aryn-mall-ui/scripts/deploy
   cp aryn-ui-https.conf.example aryn-ui.conf
   # 编辑 aryn-ui.conf：server_name / ssl_certificate / ssl_certificate_key
   ```
4. 用 HTTPS compose 示例启动：
   ```bash
   docker compose -f docker-compose.https.example.yml up -d --build
   ```
5. 验证：
   ```bash
   curl -sSI https://<域名>/            # 期望 200，且含 Strict-Transport-Security
   curl -sSI http://<域名>/             # 期望 301 到 https
   openssl s_client -connect <域名>:443 -servername <域名> </dev/null \
     | openssl x509 -noout -subject -dates
   ```

## 5. 配置自检

本机没有 nginx 时可以用 docker 跑静态自检（`nginx -t` 只校验语法，不启动服务）。下面命令把测试用的 `conf.d` 整体挂进镜像，并用 `--add-host` 模拟 `aryn-boot` 容器名（`proxy_pass` 里的主机名会在配置解析阶段被解析）：

```bash
cd aryn-mall-ui/scripts/deploy
tmp=$(mktemp -d) && mkdir -p "$tmp/conf.d" && cp aryn-ui.conf "$tmp/conf.d/"
docker run --rm -v "$tmp/conf.d:/etc/nginx/conf.d:ro" \
  --add-host aryn-boot:127.0.0.1 nginx:stable-alpine nginx -t
# 期望：nginx: configuration file /etc/nginx/nginx.conf test is successful
```

`aryn-ui-https.conf.example` 引用了证书文件，直接 `nginx -t` 会因 `cannot load certificate ... No such file or directory` 失败，先用自签名证书占位再校验：

```bash
tmp=$(mktemp -d) && mkdir -p "$tmp/conf.d" "$tmp/certs/live/example.com"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=example.com" \
  -keyout "$tmp/certs/live/example.com/privkey.pem" \
  -out "$tmp/certs/live/example.com/fullchain.pem"
cp aryn-ui-https.conf.example "$tmp/conf.d/aryn-ui.conf"
docker run --rm \
  -v "$tmp/conf.d:/etc/nginx/conf.d:ro" \
  -v "$tmp/certs:/etc/nginx/certs:ro" \
  --add-host aryn-boot:127.0.0.1 nginx:stable-alpine nginx -t
# 现代 nginx 会提示 `listen ... http2` deprecated 警告，属预期（见模板文件头注释）
```

静态自检：

```bash
# 1) Dockerfile COPY 引用的文件必须真实存在
grep -n 'COPY' Dockerfile && ls -l dist aryn-ui.conf
# 2) 示例文件后缀不会被 Dockerfile COPY，也不会被 nginx 的 /etc/nginx/conf.d/*.conf 匹配
ls -1 *.example README.md
# 3) CORS 只应有一组（下面命令应只命中 add_header / 注释，不应出现 ACAO '*'）
grep -n "Access-Control-Allow-Origin" aryn-ui.conf
```

本次修复已用 `nginx:stable-alpine` 实测：两个配置 `nginx -t` 均通过（443 模板用自签名证书）；修复后的 Dockerfile `docker build` 成功；容器内通过 `curl` 验证了安全头、`server_tokens off`、OPTIONS 预检 204 且 ACAO 只出现一次、`/actuator` 403、静态 hash 资源 30 天缓存与 SPA 回退 200；并用「自身也输出 ACAO 的模拟上游」验证了代理链路最终只保留 1 个 ACAO。443 模板另验证了 ACME 校验路径 200、其余路径 301、HTTP/2 + HSTS、TLS1.2/1.3 握手（TLS1.1 已按配置禁用）。

## 6. 审查结论（重要）

安全审查发现并已修复的部署配置问题：

1. `Dockerfile` 曾 COPY 不存在的 `./aryn-ui-pro.conf`，镜像构建在 COPY 阶段直接失败；现已改为实际文件 `./aryn-ui.conf`；
2. `aryn-ui.conf` 的 `/api` 代理里同时写了 `Access-Control-Allow-Origin '*'` 和 `$http_origin` 两套头，响应会出现重复的 ACAO，浏览器按 CORS 规范拒绝；现已只保留「回显 Origin + 允许凭证 + Vary: Origin」一组，OPTIONS 预检返回 204；同时用 `proxy_hide_header` 剥离上游可能输出的 CORS 头（`aryn-boot` 的 `SaTokenConfigure` 在 Origin 命中 `cors.allowed-origins` 时也会自己输出 ACAO），保证全链路只有 Nginx 这一个 CORS 出口；
3. 缺少安全响应头与请求体上限；现已补 `server_tokens off`、`nosniff`、 `X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy`，并把 `client_max_body_size` 设为 `100m`（与后端 `multipart.max-request-size: 100MB` 对齐）。

CORS 遗留风险（需生产按需收敛）：

- 当前策略沿用「回显请求 Origin + `Access-Control-Allow-Credentials: true`」，等价于允许任意站点发起带凭证的跨域请求。管理端凭证走 `satoken` 请求头（`sa-token.is-read-cookie: false`，非 Cookie），第三方站点读不到 localStorage 中的 token，实际 CSRF 风险受控；但若后续切换为 Cookie 鉴权，必须改为可信 Origin 白名单（可用 `map $http_origin $cors_origin { ... }` 实现），否则风险会放大。
- 后端 `application.yml` 的 `cors.allowed-origins` 目前默认是开发端口 `http://localhost:5777`，生产应由环境变量 `HX_CORS_ALLOWED_ORIGINS` 显式配置；Nginx 侧已剥离上游 CORS 头，因此以 Nginx 的策略为准。

**「不能仅凭仓库模板认定生产已走 HTTPS」**：

- 本仓库原有的部署配置只有 `listen 80`，没有 TLS server 块、没有 443 端口映射、没有证书挂载；本次新增的 `aryn-ui-https.conf.example` 只是**示例模板**，它不代表生产环境已经启用 TLS；
- 生产是否启用 HTTPS 必须到真实入口核实，模板文件不能作为证据。建议至少确认：
  1. 域名解析与真实入口（是否经过 CDN / SLB / Ingress），TLS 在哪一层终止；
  2. `curl -sSI http://<域名>/` 是否返回 301/308 到 `https://`；
  3. `curl -sSI https://<域名>/` 是否返回 200，`Strict-Transport-Security` 是否存在；
  4. `openssl s_client -connect <域名>:443` 证书链、有效期、签发者是否符合预期；
  5. 服务器上 `docker port aryn-ui-pro` / `ss -lntp | grep 443` 是否确有 443；
  6. 若 TLS 由外部网关终止，确认「网关 → nginx 回源」段是可信内网还是明文，以及入口是否正确透传 `X-Forwarded-For` / `X-Forwarded-Proto`（客户端 IP 与协议感知）。

> 说明：`/api` 反代固定为 `http://aryn-boot:9999/`，boot 单体模式下 `/api/boot/xxx` → `/boot/xxx` 的路径改写由前端 `rewriteBootUrl` 完成，Nginx 不得再拼接 `/boot` 前缀，否则会变成 `/boot/boot/xxx`。
