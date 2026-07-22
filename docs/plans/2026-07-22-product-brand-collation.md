# Product Brand Collation Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 统一商品品牌表与既有商品表的 MySQL 排序规则，修复品牌联表查询的 `Illegal mix of collations`。

**Architecture:** 将排序规则约束放在 Boot/Cloud schema 迁移中，并提供存量表转换语句；通过 Java SQL 契约测试检查增量脚本与生成后的 Boot 全量 SQL。运行态仅对本地 `aryn_boot.goods_brand` 执行同一迁移，不修改 Mapper 查询。

**Tech Stack:** MySQL 8、SQL、JUnit 5、AssertJ、Node.js SQL 聚合脚本、Maven。

---

### Task 1: 建立排序规则回归测试

**Files:**
- Create: `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/product/ProductBrandSqlContractTest.java`
- Test: `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/product/ProductBrandSqlContractTest.java`

**Step 1: Write the failing test**

新增测试读取以下文件：

- `db/boot/19product_brand.sql`
- `db/cloud/19product_brand.sql`
- `db/boot/aryn_boot_full.sql`

测试断言三个文件都包含：

```sql
DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
ALTER TABLE goods_brand CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

同时断言 Boot 与 Cloud 增量脚本均包含 `ADD COLUMN brand_id`，避免修复排序规则时丢失原迁移职责。

**Step 2: Run test to verify it fails**

Run: `mvn -pl aryn-boot -Dtest=ProductBrandSqlContractTest test`

Expected: FAIL，因为现有 `goods_brand` 建表未声明 collation，且没有存量表转换语句。

### Task 2: 修复 Boot 与 Cloud 增量 SQL

**Files:**
- Modify: `aryn-mall-java/db/boot/19product_brand.sql`
- Modify: `aryn-mall-java/db/cloud/19product_brand.sql`

**Step 1: Write minimal implementation**

将两个脚本的品牌表建表尾部改为：

```sql
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品品牌';
```

紧接建表语句增加：

```sql
ALTER TABLE goods_brand
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

**Step 2: Run focused test**

Run: `mvn -pl aryn-boot -Dtest=ProductBrandSqlContractTest test`

Expected: 仍 FAIL，仅缺少重新生成的 Boot 全量 SQL。

### Task 3: 重新生成 Boot 全量 SQL

**Files:**
- Modify: `aryn-mall-java/db/boot/aryn_boot_full.sql`
- Verify: `aryn-mall-java/db/boot/verify-full-sql.mjs`

**Step 1: Regenerate aggregate SQL**

Run: `node db/boot/build-full-sql.mjs`

**Step 2: Verify generated SQL**

Run: `node db/boot/verify-full-sql.mjs`

Expected: PASS。

**Step 3: Verify contract test is green**

Run: `mvn -pl aryn-boot -Dtest=ProductBrandSqlContractTest test`

Expected: PASS。

### Task 4: 修复本地运行数据库并验证原始症状

**Files:**
- No repository file changes.

**Step 1: Apply the existing-table conversion**

对本地 `aryn_boot` 执行：

```sql
ALTER TABLE goods_brand
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

**Step 2: Verify schema metadata**

查询 `information_schema.columns`，确认 `goods_brand.id`、`goods_brand.tenant_id`、`goods_spu.brand_id` 均为 `utf8mb4_general_ci`。

**Step 3: Re-run representative queries**

执行消息未读联表查询与商品列表品牌联表查询，Expected: 均成功，不再出现 `tenant_id is ambiguous`、`Unknown column goods_spu.brand_id` 或 `Illegal mix of collations`。

### Task 5: 完整验证与提交

**Files:**
- Modify: `docs/90-记录归档/需求记录.md`

**Step 1: Run regression tests**

Run: `mvn -pl aryn-boot -Dtest=ProductBrandSqlContractTest,TenantJoinAliasAuditTest test`

Expected: PASS，Tests run 为 11，Failures/Errors 均为 0。

**Step 2: Validate generated SQL and diff**

Run: `node db/boot/verify-full-sql.mjs`

Run: `git diff --check`

Expected: 均成功。

**Step 3: Record completion**

在需求记录中登记根因、修改文件和验证命令。

**Step 4: Commit**

仅暂存本任务文件并使用中文提交信息：

```bash
git commit -m "修复：统一商品品牌表排序规则"
```
