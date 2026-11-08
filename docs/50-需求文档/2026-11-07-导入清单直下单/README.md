# 导入清单直下单（不创建共享购物车）

> 2026-11-07 · 需求来源：用户「我的意思是不创建共享购物车也能导入」。

## 需求口径

「导入清单下单」向导的完整链路改为：

```
选定配送目标（船供=船舶+靠港 / 岸上=收货地址）
  → 上传 Excel（/app/replenish-import/preview，服务端解析落行）
  → 报告页逐行处置（复用补给单导入的报告/补选渲染）
  → 确认（confirm 返回已复核 SKU 清单，同 SKU 已合并）
  → 结算页（order-confirm，createWay=2，锁定 way/scene）
  → 提交订单
```

**全程不产生补给单/共享购物车单据**。补给单多人收集流程不变（列表页发起 → 详情页「导入 Excel」，走 80 号脚本的挂车导入）。

## 关键决策

| 决策点 | 结论 | 理由 |
|---|---|---|
| 清单去向 | 直接下单（经结算页） | 页面标题「导入清单下单」的字面含义；2000 行导入必须在下单前看到金额/运费 |
| 是否后台直接建单 | 否，confirm 只返回复核清单 | 复用结算页的金额/支付/备注评审；expectedPaymentPrice 防串价照常生效 |
| 解析任务落库 | 新表 `replenish_direct_import(.row)` | 80 号两表 cart_id NOT NULL 且 requireImport 以 cartId 为查询键；防篡改契约要求解析行服务端留存 |
| scene=3 走 HTTP | 放宽 CreateOrderDTO/SettlementOrderDTO 正则 `[12]`→`[123]` | 共享车 service 直调不受 @Valid 限制，结算页走 HTTP 会 400 |
| 配送上下文传递 | 新 pinia store `directImportOrderStore` | order-confirm 只认全局 shipContextStore，有船用户选岸上地址会被强制 way=4 |

## 改动清单

### 后端（aryn-mall-java）

- 实体：`order-api/.../entity/ReplenishDirectImport(.row).java`；VO：`ReplenishDirectImportVO/RowVO/ItemVO`；DTO：`ReplenishDirectImportConfirmDTO`
- 服务：`order-biz/.../service/IReplenishDirectImportService` + `impl/ReplenishDirectImportServiceImpl`（解析/分类/匹配/复检与 `SharedCartServiceImpl.previewImport/confirmImport` 同源，终点不同）
- 控制器：`controller/app/AppReplenishImportController`（`/app/replenish-import`：preview / imports/{id} / pending / imports/{id}/confirm）；模板下载复用 `GET /app/shared-cart/import/template`（本就不带 cartId）
- DTO：`CreateOrderDTO` / `SettlementOrderDTO` purchaseScene 正则 `[12]`→`[123]`
- 测试：`ReplenishDirectImportServiceTest`（8 例：解析/未填数量/合并/下架跳过/幂等/归属）

### SQL（双模式）

| 模式 | 脚本 | 内容 |
|---|---|---|
| boot | `db/boot/213replenish_direct_import_incremental.sql` | 两张表（已执行） |
| boot | `application.yml` hx.tenant.tables + `build-full-sql.mjs` | 白名单 + 全量重生成（verify 通过） |
| cloud | `db/cloud/220replenish_direct_import_incremental.sql` | 两张表（与 213 同内容，USE aryn_order） |
| cloud | `db/cloud/221replenish_direct_import_nacos_tenant_tables.sql` | Nacos `aryn-order-biz-dev.yml` 白名单 REPLACE 锚点 |

### 前端（aryn-mall-uniapp）

- `store/directImportOrderStore.ts`：`{active, scene, userAddressId, remark}`，下单成功后 clear
- `sub-pages/order/shared-cart/import-entry.vue`：向导改为只收集配送目标（船供/岸上切换 + 地址弹层保留），**拆除 createSharedCart / active-summary 复用**；CTA「上传清单导入」→ `import?mode=direct`
- `sub-pages/order/shared-cart/import.vue`：`mode=direct` 分支——preview/恢复/confirm 分流到新端点；确认成功把复核清单写入 `goodsStore` → redirectTo `order-confirm?createWay=2`；1201 姓名闸门 direct 模式跳过
- `sub-pages/order/order-confirm/index.vue`：handoff 接入——岸上 `getById` 预选地址 + `purchaseScene='3'`；船供强制 `scene='2'`（覆盖 store 默认个人）；way 切换锁定；下单成功 clear；空清单守卫
- `components/ShopOrderItem.vue`：`deliveryWayLocked` prop（handoff 模式配送方式行转静态说明）
- `api/order/replenishImport.ts`（alova）+ `sub-pages/api/order/sharedCartImport.ts`（`previewDirectImport` 文件流）

## 验证记录

- 后端：`mvn clean compile -pl aryn-boot -am` 通过；`ReplenishDirectImportServiceTest` 8/8；`TenantConfigurationConsistencyTest` 4/4（Nacos 基线补表+md5 后）；boot 全量 `mvn test -pl aryn-boot -am` **BUILD SUCCESS**（boot 模块 108 例全绿）
- 前端：`pnpm type-check` 0 error；`pnpm test:unit` 154 文件 / 1745 用例全绿（契约测试改写：entry 禁 `createSharedCart`/`getActiveSharedCartSummary`，锁 `mode=direct` 与 handoff；新控制器断言不沾 `SharedCartItem`）
- H5 实测（boot 真后端，clean 重打包 md5 双验）：curl preview 3 行 CODE 全匹配、confirm 返回 3 项且幂等；浏览器向导→报告页→结算页→提交订单全链路通，库核对 `purchase_scene='3'`/`delivery_way=3`/COD/收货人=所选地址/明细 5·8·3 与 Excel 一致；下单成功 handoff 自动 clear；测试数据已全部还原

## 2026-11-07 审查修复（13 项）

> 修复会话：跨租户入口、并发确认、RPC 故障、Cloud 基线、交接收养、场景同源、空回执、备注、船供刷新、岸上运费、规格误匹配、合并库存、统一合并清单。旧「验证记录」保留为修复前基线。

| # | 结论 | 关键落点 |
|---|---|---|
| 1 | 会话租户始终优先于 `tenant-id` 头；平台管理员仍可切租户，匿名仍按头选租户 | `ArynTenantContextFilter` 不再以 satoken 头是否存在决定是否读会话；真实 Sa-Token 参数/头令牌测试 |
| 2 | 并发确认串行化，重复/并发返回同一结果 | `ReplenishDirectImportMapper.selectForConfirm` 行锁 + 已确认分支读侧重建 |
| 3 | 商品 RPC 故障中止确认并回滚，恢复后可重试 | `loadCurrentSkus` 抛 `ArynBusinessException`，不再吞异常全跳过 |
| 4 | Cloud 基线补齐两张表 | `db/cloud/7aryn_order.sql`；boot 213/cloud 220/221 与全量 SQL 校验通过 |
| 5 | 交接绑定 handoffId，普通 `createWay=2` 不再被全局 active 接管 | 向导生成 handoff 随 URL 传递；普通结算入口清理残留；下单成功 clear |
| 6 | 预览/重新结算/提交共用场景解析 | `utils/checkout-delivery.ts`；岸上 way3/scene3、船供 way4/scene2 全程一致 |
| 7 | 空回执保持待确认，回执同步跳过原因 | confirm 返回 `status/checkoutReady/rows/message`；零商品不写 CONFIRMED |
| 8 | 备注展示/编辑/清空/重新结算/提交同源 | `SettlementOrderDTO.remark` 回显；`orderParams.remark` 唯一来源，空串清除有效 |
| 9 | 船供刷新不丢配送目标 | handoff 持久化 vesselId/vesselCallId；结算页刷新后重新校验并在失效时就地重选靠港 |
| 10 | 岸上 way3 展示运费 | `ShopOrderItem` 运费行覆盖 way1/way3，待计算/零运费/正常运费与后端同源 |
| 11 | 规格数字/单位/包装数量真实差异不再因子串放行 | `ReplenishMatchRules.specEquivalent` 抽取数量片段比较；500ml/1500ml、1kg/11kg 回归 |
| 12 | 按合并 SKU 总量复检库存/MOQ/步长并反馈缺口 | 聚合用 long 防溢出；整组跳过并写可处理原因 |
| 13 | 首次、重试、并发、恢复统一合并清单 | `buildItems` 读侧按 SKU 合并；DB 保留逐行审计不重复计数 |

### 本次验证

- 后端：JDK21 `mvn clean compile -pl aryn-boot -am` 通过；受影响定向测试（租户过滤、规格规则、商品匹配、导入服务、分类器、结算备注、积分场景、SQL 契约、租户一致性）全部通过；`verify-full-sql.mjs` 通过（31,196 行）。
- 前端：`pnpm type-check` 0 error；`pnpm test:unit` 156 文件 / 1,756 用例全绿。
- H5 真后端 E2E：岸上导入合并 SKU 10+2、备注回显、way3/scene3、运费行、COD 下单成功；船供 way4/scene2 并在刷新结算页后恢复船舶/靠港并成功下单；并发两次 confirm 返回同一 `[10,2]`；同 SKU 500+600 库存 999 时保持待确认并回写「合并后共需 1100 件」；参数令牌 + 冲突 `tenant-id` 的预览/待取任务仍落会话租户。
- 测试数据：两条测试订单已按业务接口取消并恢复库存（999），导入任务/靠港/配送任务逻辑删除，优惠券回到未使用；临时 Excel 已移除。

## 2026-11-07 补充：已确认恢复入口与同车并发并入

### 已确认导入的结算恢复

- 后端新增 `GET /app/replenish-import/latest`（最近一份待确认/已确认任务）与
  `GET /app/replenish-import/imports/{id}/items`（只读重建已确认合并清单）。
  待确认任务调用恢复接口返回 `checkoutReady=false`，引导回报告页；已取消任务拒绝。
- 前端 `import.vue` 启动时按 store 里的 `importId` 调 `restoreDirectReport` 取回报告；
  已确认任务展示「上次已确认 N 项，可继续去结算」与「继续去结算」按钮，
  点击调 `resumeDirectImport` 后写 `goodsStore` 并带原 handoff 进结算页。
- 向导 `import-entry.vue` 在交接未过期且有 `importId` 时展示「继续上次导入」入口，
  避免杀进程后只能重新上传。
- 验证：curl `latest` 返回已确认任务、`items` 返回 `[206:10, 207:2]`；浏览器模拟
  重启（重建 Pinia 持久化状态）后导入页恢复报告，点「继续去结算」进入 way3/scene3
  结算页，商品、备注、金额均正确。

### 同车不同导入并发并入

- `SharedCartServiceImpl.confirmImport` 在任务行锁之外，先对 `shared_cart` 加
  `FOR UPDATE` 行锁，再进入 `mergeIntoCartItems`；同车所有导入确认串行化，
  避免不同任务同时读改写 `shared_cart_item` 的丢失更新或唯一键冲突。
- 真实压测（本地 boot，两个导入任务对同一共享车同时 confirm，重复三轮）：
  - 4 + 6 → 单行 `requested_quantity=10`
  - 2 + 3 → 15
  - 5 + 7 → 27
  每轮两个 confirm 均成功，最终只有一行 SKU 明细，无丢失/重复/唯一键失败。
- 压测共享车/明细/成员/导入任务已逻辑删除，未产生订单或库存变动。

### 回归

- 后端全量 `mvn test -pl aryn-boot -am` exit 0；`ReplenishDirectImportServiceTest` 19 项、
  `SharedCartImportServiceTest` 29 项通过。
- 前端 `pnpm type-check` 0 error；全量 156 文件 / 1,757 用例通过。
