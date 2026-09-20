-- 悦航购共享采购按人拆行与配送到人标签增量迁移（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 租户白名单：Boot 侧在 aryn-boot/src/main/resources/application.yml 的 hx.tenant.tables。
--             本脚本仅对已有表 order_item / shared_cart_member 增加列，不新增表，无需改动白名单。
-- 特性：幂等执行，不删除或重建数据；不修改既有列类型，不覆盖业务数据。
--
-- 背景：共享购物车原先在提交订单时**按 SKU 聚合**，把多个成员的同款商品并成一条订单明细，
--       contributor_user_id 用逗号拼接多个雪花 ID（20 位），而字段为 varchar(32)，
--       两人同购即 41 字符 → 非严格模式静默截断、严格模式报错；且合并后无法区分商品归属，
--       仓库配送时无法为「谁要的商品」逐人贴标签。
--
-- 本次改造：
--   1. 订单明细改为**按成员拆行**：谁加购就按谁生成一条明细，同一 SKU 多位成员各自成行。
--      拆行后每条明细只对应一个贡献者（20 字符），上述溢出问题自然消除。
--   2. 新增姓名快照字段，供仓库/司机打印配送标签使用（存姓名而非 ID，避免标签反查用户表）。
--
-- 执行：mysql -u root -p aryn_boot < 65shared_cart_member_label_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 1. shared_cart_member.display_name
--    成员加入共享购物车时填写一次姓名，作为该成员在本轮采购中的展示名。
--    取值优先级：成员填写 → 商城默认收货地址收货人姓名 → 商城昵称 → 用户{ID后6位}
-- ===========================================================================
SET @add_member_display_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart_member` ADD COLUMN `display_name` varchar(64) DEFAULT NULL COMMENT ''成员展示姓名（加入时填写，配送贴标签用）'' AFTER `can_confirm`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_member' AND COLUMN_NAME = 'display_name'
);
PREPARE stmt FROM @add_member_display_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 2. order_item.contributor_name
--    下单时结转的贡献者姓名快照。必须是快照而非外键：
--    用户后续改名或离船，历史订单的标签仍应保持下单当时的信息。
-- ===========================================================================
SET @add_order_item_contributor_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `contributor_name` varchar(64) DEFAULT NULL COMMENT ''共享购物车贡献者姓名快照（配送贴标签用）'' AFTER `contributor_user_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'contributor_name'
);
PREPARE stmt FROM @add_order_item_contributor_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 3. 存量数据回填（仅补空值，不覆盖已有业务数据）
--    历史订单的明细是合并行，contributor_user_id 可能是 "id1,id2" 形式，无法精确拆分归属，
--    因此不回填为姓名——保持 NULL 让管理端显示为“未记录”。
-- ===========================================================================

SET FOREIGN_KEY_CHECKS = 1;

-- 自检：两个新列应存在
SELECT `TABLE_NAME`, `COLUMN_NAME`, `COLUMN_TYPE`, `COLUMN_COMMENT`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (   (`TABLE_NAME` = 'shared_cart_member' AND `COLUMN_NAME` = 'display_name')
       OR (`TABLE_NAME` = 'order_item'         AND `COLUMN_NAME` = 'contributor_name'))
ORDER BY `TABLE_NAME`;
