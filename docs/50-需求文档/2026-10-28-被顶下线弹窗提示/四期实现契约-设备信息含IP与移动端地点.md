# 四期实现契约：被顶下线设备信息含 IP + 移动端展示地点

> 2026-11-01 增量。背景：运营反馈「账号已在其他设备登录」弹窗只显示时间，无法判断是哪台设备；
> 产品决策放开 IP 展示并要求移动端也带上地点。一期契约的「不展示 IP」自本期起废止，
> 其余约束（脱敏文案、按钮规则、Redis key/TTL、WS 推送）不变。

## 1. wire 变更（唯一数据面改动）

`data.device` 白名单新增 `ip` 字段（字符串，可空，长度截断 40，来自新会话在线记录的
`ipAddr`，键名改为 `ip` 下发）：

```json
"device": { "os": "OSX", "browser": "Chrome", "location": "内网IP", "ip": "192.168.107.1", "loginTime": "..." }
```

- 旧客户端（未升级的管理端/移动端）忽略未知字段，天然兼容；
- 旧 Redis 失效记录（无 `ip`）读取后自然缺该字段，前端隐藏对应片段；
- 非白名单键（`ipAddr`/`tokenId`/`tenantId`/`userName`…）依旧全部丢弃；
- IP 仅允许出现在 `device.ip`，401 响应体其它位置仍禁止出现完整 IP。

## 2. Unknown 兜底值过滤（质量修复）

无 User-Agent 的客户端（脚本/健康探测）登录时，hutool UA 解析在在线记录里写入
`os="Unknown"/browser="Unknown"`，此前会被原样渲染成「登录设备：Unknown · Unknown」。
本期在两侧统一过滤：

- 后端：`SessionInvalidSupport.readLoginDevice` 写入失效记录前把 `Unknown`（大小写不敏感）按缺失处理；
- 前端：管理端 `normalizeSessionInvalidDevice`、移动端 `cleanDeviceText` 读取侧再过滤一次（存量 Redis 记录兜底）。

## 3. 设备行格式（覆盖一期契约 §4）

- 管理端：`登录设备：{browser} · {os}｜地点：{location}｜IP：{ip}｜时间：{HH:mm}`（i18n key 新增 `sessionInvalid.device.ip`，中英双语）
- 移动端：`设备：{os} · {browser}｜{location}｜{HH:mm}`（地点段为裸值，无标签前缀；**不渲染 IP**）
- 各片段独立省略；移动端所有片段为空时整行隐藏（与一期一致）。

## 4. 改动清单

| 端 | 文件 |
|---|---|
| 后端 | `aryn-common-core/.../SessionInvalidPayload.java`（白名单 + `DEVICE_IP`）、`aryn-common-security/.../SessionInvalidSupport.java`（`readLoginDevice`/`deviceOf` + Unknown 过滤）、`aryn-gateway/.../SaTokenConfigure.java`（网关侧白名单同步） |
| 管理端 | `store/session.ts`（类型 + normalize + Unknown 过滤）、`store/message.ts`（WS 推送设备信息改走同一 normalize）、`layouts/session-invalid-content.ts`（IP 段）、`packages/locales` 双语 `device.ip` |
| 移动端 | `utils/session-invalid.ts`（`buildSessionInvalidDeviceLine` 地点段 + Unknown 过滤） |
| 测试 | `SessionInvalidSupportTest`、`SessionInvalidContractTest`、`session.test.ts`、`session-invalid-modal.test.ts`、`session-invalid.test.ts`（uniapp） |

## 5. 验证

```bash
cd aryn-mall-java && mvn -B test -pl aryn-boot -am -Dtest='SessionInvalidSupportTest,SessionInvalidContractTest' -Dsurefire.failIfNoTests=false
cd aryn-mall-ui && pnpm vitest run --dom apps/web-ele/src/store/session.test.ts apps/web-ele/src/store/message-session-invalid.test.ts apps/web-ele/src/layouts/session-invalid-modal.test.ts
cd aryn-mall-uniapp && pnpm vitest run src/utils/session-invalid.test.ts
```

部署注意：`device.ip` 由后端下发，**boot/cloud 必须重新打包部署后**新字段才出现；
前端未升级只影响「看不到 IP」，不影响弹窗本身。
