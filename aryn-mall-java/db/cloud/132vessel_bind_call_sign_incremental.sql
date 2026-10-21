-- 船舶绑定申请单：IMO 与呼号分列采集（Cloud 微服务模式）
--
-- 目标库：aryn_vessel
-- 特性：可重复执行，不做 DROP/TRUNCATE，不修改存量数据。
--
-- 背景（2026-10-18）：
--   申请单此前只有一个 `apply_vessel_imo` 字段，注释写的是「IMO 或呼号」——
--   小程序里就是一个输入框，用户填的东西到底是 IMO 还是呼号，系统从来不知道。
--   管理端审核时只好用「7 位纯数字」这个启发式去猜，猜中才预填进 IMO 列。
--
--   问题在于 IMO/呼号是审核链路里**最高优先级的认船强身份键**（高于船名）：
--   审核预检会拿它去比 `vessel_info.imo_code` / `vessel_info.call_sign`，
--   命中即建议直接绑定。入口处靠猜，强身份键必然失真：
--     · 7 位纯数字的呼号 → 被当成 IMO 塞进 IMO 列；
--     · 填的其实是呼号，却按 IMO 列报「已存在」，审核员看到的是误导性结论；
--     · 两者混在一个字段，审核通过后没有任何一处能还原用户本意。
--
--   本脚本把呼号拆成独立列 `apply_vessel_call_sign`，与 IMO 各自采集、各自比对。
--
-- 特性补充：存量申请单的 `apply_vessel_imo` 保持原样，不做自动拆分回填——
--        它本来就是「用户填的一串标识」，按 7 位纯数字回填到新列反而可能写错。
--        存量单在管理端仍按「IMO 列有值」展示，新单起两列独立。
--
-- 双模式：boot 同名增量 131vessel_bind_call_sign_incremental.sql 内容一致。
--
-- 执行：mysql -u root -p < 132vessel_bind_call_sign_incremental.sql

USE `aryn_vessel`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- vessel_bind_apply 增加呼号列
-- ---------------------------------------------------------------------------
SET @add_call_sign = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `vessel_bind_apply` ADD COLUMN `apply_vessel_call_sign` varchar(32) DEFAULT NULL COMMENT ''申请人填写的呼号（与 apply_vessel_imo 分列，不再混装）'' AFTER `apply_vessel_imo`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'vessel_bind_apply'
    AND COLUMN_NAME = 'apply_vessel_call_sign'
);
PREPARE stmt FROM @add_call_sign; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 存量 IMO 列语义收紧（仅改注释，不动数据）
-- ---------------------------------------------------------------------------
SET @imo_comment = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `vessel_bind_apply` MODIFY COLUMN `apply_vessel_imo` varchar(32) DEFAULT NULL COMMENT ''申请人填写的 IMO 编号（呼号已拆至 apply_vessel_call_sign）'''
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'vessel_bind_apply'
    AND COLUMN_NAME = 'apply_vessel_imo'
);
PREPARE stmt2 FROM @imo_comment; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- ---------------------------------------------------------------------------
-- 呼号检索索引
--
-- 审核预检按「IMO 只比 imo_code、呼号只比 call_sign」，自动匹配也会按呼号查
-- vessel_info.call_sign。vessel_info 现有索引只有 (tenant_id, imo_code)、
-- (tenant_id, vessel_name)、(tenant_id, status)——呼号查询会走全表。
-- 大租户下预检是「打开审核弹窗就触发」的高频只读路径，补索引值回成本。
-- ---------------------------------------------------------------------------
SET @add_call_sign_idx = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `vessel_info` ADD KEY `idx_vessel_info_call_sign` (`tenant_id`, `call_sign`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'vessel_info'
    AND INDEX_NAME = 'idx_vessel_info_call_sign'
);
PREPARE stmt3 FROM @add_call_sign_idx; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 自检：两列/索引都应就位（各输出 1）
-- ---------------------------------------------------------------------------
SELECT 'apply_vessel_call_sign' AS item, COUNT(*) AS cnt
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'aryn_vessel' AND TABLE_NAME = 'vessel_bind_apply' AND COLUMN_NAME = 'apply_vessel_call_sign'
UNION ALL
SELECT 'idx_vessel_info_call_sign', COUNT(*)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'aryn_vessel' AND TABLE_NAME = 'vessel_info' AND INDEX_NAME = 'idx_vessel_info_call_sign';

SET FOREIGN_KEY_CHECKS = 1;
