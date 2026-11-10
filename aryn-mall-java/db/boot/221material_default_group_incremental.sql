-- ============================================================================
-- 素材默认分组落库（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-10 需求「素材中心-分组管理：未分组可改名」）：
--   「未分组」此前不是真实数据——前端拿到 /upms/materialgroup/page 的结果后
--   unshift 一条 {id:'-1'} 的虚拟行，并用 v-if="scope.row.id !== '-1'" 把它的操作
--   按钮整行隐藏，所以这一行永远不能改名；而 sys_material.group_id 的默认值就是
--   '-1'（DB 注释「分组ID -1.未分组」），这些素材在分组管理里没有任何归属。
--   本次把「未分组」做成真实分组记录：每租户一条默认分组（is_default='1'），
--   存量 group_id 为 NULL/''/'-1' 或指向不存在/已逻辑删除分组的素材全部回填到
--   该租户的默认分组，前端随之去掉虚拟行与按钮隐藏逻辑。
--
-- 默认分组 ID 用公式而不是雪花 ID：
--   MD5(CONCAT('material-default-group:', tenant_id)) → 32 位小写十六进制，
--   正好等于 id varchar(32)。SQL 与后端 MaterialDefaultGroupSupport#defaultGroupId
--   必须完全一致，否则「脚本建的」和「懒创建建的」会变成两条默认分组，
--   命中下面的函数唯一索引后还会让接口 500。
--
-- 唯一性：每租户最多一条默认分组，靠函数唯一索引（MySQL 8.0.13+ 支持函数键）：
--   UNIQUE KEY uk_material_group_default_tenant
--     ((IF(is_default = '1' AND del_flag = '0', tenant_id, NULL)))
--   非默认行与已逻辑删除行的键值为 NULL，MySQL 允许多个 NULL，因此不占唯一性；
--   默认行按 tenant_id 互斥，并发懒创建由该索引兜底（后端捕获 DuplicateKeyException）。
--
-- 特性：可重复执行；加列/加索引前先查 information_schema，重复执行是空操作；
--   种子用 INSERT IGNORE（不覆盖运营已改过的分组名），回填只改 group_id，
--   不 DROP/TRUNCATE 任何存量数据。
-- 与 cloud 版一致性：本脚本与 db/cloud/232material_default_group_incremental.sql
--   内容一致，仅目标库不同。
-- 配套代码：SysMaterialGroup（isDefault 字段）、SysMaterialGroupServiceImpl
--   （ensureDefaultGroupId / resolveGroupIdForSave / removeGroup）。
-- 执行：mysql -u root -p aryn_boot < 221material_default_group_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、sys_material_group 增加 is_default 列
--   放在 name 之后，与实体字段顺序一致，便于对表排查。
-- ============================================================================
SET @material_group_is_default_column_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_material_group'
    AND column_name = 'is_default'
);
SET @material_group_is_default_column_sql := IF(
  @material_group_is_default_column_exists = 0,
  'ALTER TABLE `sys_material_group` ADD COLUMN `is_default` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''0'' COMMENT ''是否默认分组：1.是；0.否'' AFTER `name`',
  'SELECT 1'
);
PREPARE material_group_is_default_column_stmt FROM @material_group_is_default_column_sql;
EXECUTE material_group_is_default_column_stmt;
DEALLOCATE PREPARE material_group_is_default_column_stmt;

-- ============================================================================
-- 二、每租户唯一默认分组：函数唯一索引
--   索引表达式与后端口径一致：只有「未删除的默认分组」参与唯一性，
--   普通分组与已删除分组取 NULL，互不影响。
-- ============================================================================
SET @material_group_default_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_material_group'
    AND index_name = 'uk_material_group_default_tenant'
);
SET @material_group_default_index_sql := IF(
  @material_group_default_index_exists = 0,
  'ALTER TABLE `sys_material_group` ADD UNIQUE KEY `uk_material_group_default_tenant` ((IF(`is_default` = ''1'' AND `del_flag` = ''0'', `tenant_id`, NULL)))',
  'SELECT 1'
);
PREPARE material_group_default_index_stmt FROM @material_group_default_index_sql;
EXECUTE material_group_default_index_stmt;
DEALLOCATE PREPARE material_group_default_index_stmt;

-- 结构自检：列与索引各 1 行（期望 is_default_column_rows=1、default_index_rows=1）
SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_material_group'
      AND column_name = 'is_default') AS is_default_column_rows,
  (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_material_group'
      AND index_name = 'uk_material_group_default_tenant') AS default_index_rows;

-- ============================================================================
-- 三、为所有租户补默认分组
--   id 由公式决定，因此 INSERT IGNORE 天然幂等：主键已存在或该租户已有
--   默认分组（函数唯一索引冲突）时静默跳过，不会覆盖运营改过的分组名。
--   sys_tenant.del_flag 为 char(1)，取值 '0'.正常 / '1'.隐藏，已按实际列核对。
-- ============================================================================
INSERT IGNORE INTO `sys_material_group`
(`id`, `name`, `create_time`, `sort`, `type`, `tenant_id`, `create_by`, `del_flag`, `is_default`)
SELECT
  MD5(CONCAT('material-default-group:', t.`id`)),
  '未分组',
  NOW(),
  0,
  '1',
  t.`id`,
  'system',
  '0',
  '1'
FROM `sys_tenant` AS t
WHERE t.`del_flag` = '0';

-- ============================================================================
-- 四、素材回填：改挂到所属租户的默认分组
--   命中条件（任一）：
--     1) group_id 为 NULL 或空串；
--     2) group_id = '-1'（历史虚拟「未分组」）；
--     3) group_id 指向不存在、已逻辑删除、或属于其它租户的分组（g.id IS NULL）。
--   只处理租户仍有效的素材（JOIN sys_tenant 且 del_flag='0'）：租户已删除时没有
--   默认分组可挂，硬写公式 ID 只会造出一条指向不存在分组的脏数据，保持原值更安全。
--   已逻辑删除的素材同样回填：它们只是「已删除」，恢复后仍应落在默认分组而不是
--   指向一个已消失的分组；本步骤只改 group_id，不动 del_flag / update_time。
-- ============================================================================
UPDATE `sys_material` AS m
  JOIN `sys_tenant` AS t
    ON t.`id` = m.`tenant_id` AND t.`del_flag` = '0'
  JOIN `sys_material_group` AS d
    ON d.`tenant_id` = m.`tenant_id` AND d.`is_default` = '1' AND d.`del_flag` = '0'
  LEFT JOIN `sys_material_group` AS g
    ON g.`id` = m.`group_id` AND g.`tenant_id` = m.`tenant_id` AND g.`del_flag` = '0'
   SET m.`group_id` = d.`id`
 WHERE m.`group_id` IS NULL
    OR m.`group_id` = ''
    OR m.`group_id` = '-1'
    OR g.`id` IS NULL;

-- ============================================================================
-- 五、数据自检（全部应为期望值，出现异常需人工核对后再继续）
--   default_group_rows        默认分组条数（= 有效租户数）
--   invalid_default_flag_rows is_default 非法值行数（期望 0）
--   ungrouped_material_rows   有效租户下 group_id 仍为 NULL/''/'-1' 的素材数（期望 0）
--   dangling_material_rows    有效租户下 group_id 指向不存在/已删除/跨租户分组的素材数（期望 0）
--   max_default_per_tenant    每租户默认分组数的最大值（期望 1）
--   tenant_without_default_rows 没有默认分组的有效租户数（期望 0）
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM `sys_material_group`
    WHERE `is_default` = '1' AND `del_flag` = '0') AS default_group_rows,
  (SELECT COUNT(*) FROM `sys_material_group`
    WHERE `is_default` NOT IN ('0', '1')) AS invalid_default_flag_rows,
  (SELECT COUNT(*) FROM `sys_material` AS m
     JOIN `sys_tenant` AS t ON t.`id` = m.`tenant_id` AND t.`del_flag` = '0'
    WHERE m.`group_id` IS NULL OR m.`group_id` = '' OR m.`group_id` = '-1') AS ungrouped_material_rows,
  (SELECT COUNT(*) FROM `sys_material` AS m
     JOIN `sys_tenant` AS t ON t.`id` = m.`tenant_id` AND t.`del_flag` = '0'
     LEFT JOIN `sys_material_group` AS g
       ON g.`id` = m.`group_id` AND g.`tenant_id` = m.`tenant_id` AND g.`del_flag` = '0'
    WHERE g.`id` IS NULL) AS dangling_material_rows,
  (SELECT IFNULL(MAX(c), 0) FROM (
     SELECT COUNT(*) AS c FROM `sys_material_group`
      WHERE `is_default` = '1' AND `del_flag` = '0'
      GROUP BY `tenant_id`) AS per_tenant) AS max_default_per_tenant,
  (SELECT COUNT(*) FROM `sys_tenant` AS t
    WHERE t.`del_flag` = '0'
      AND NOT EXISTS (SELECT 1 FROM `sys_material_group` AS g
                       WHERE g.`tenant_id` = t.`id` AND g.`is_default` = '1' AND g.`del_flag` = '0')
  ) AS tenant_without_default_rows;

SET FOREIGN_KEY_CHECKS = 1;
