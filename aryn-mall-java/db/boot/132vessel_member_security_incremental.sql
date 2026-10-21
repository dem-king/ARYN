-- 船舶成员关系安全加固：绑定来源列 + 成员审计日志（Boot 单体模式）
-- 目标库：aryn_boot
--
-- 背景（2026-10-19）：
--   管理端「绑定申请」审核弹窗里，小程序申请的「悦航8号」被绑到了「悦航7号」，
--   全程没有提示、没有校验、没有留痕。追查后确认这不是单点缺陷——
--   `vessel_member`（船舶成员关系）有 5 条写入通道，信任级别各不相同：
--     1. approveApply 审核通过（运营）
--     2. redeemInviteCode 邀请码（持码人）
--     3. addMember C 端船管拉人
--     4. VesselServiceImpl.addMember 管理端直接绑定
--     5. bindMemberByShare 分享购物车链接自动补建（任何拿到链接的人）
--
--   而 `vessel_member` 不只是一张展示表，它是多个下游域的数据权限基线：
--   成员列表、靠港计划查看/申报、内部配送下单、共享购物车管理都以「是不是该船成员」授权。
--   一条误绑 = 把另一条船的采购、配送与人员信息交给不相关的人。
--
--   本脚本补两块基础设施：
--     · vessel_member.bind_source / bind_reason —— 让「这个人是怎么进船的」可区分、可回收；
--     · vessel_member_audit_log —— 绑定/换角色/解绑全留痕，事后可追溯。
--
-- 特性：可重复执行，不做 DROP/TRUNCATE，不覆盖已有业务数据。
--       存量成员行按「能判定才回填、判不出就留 MANUAL」处理，不猜测。
--
-- 双模式：cloud 同名增量 133vessel_member_security_incremental.sql 内容一致，
--         目标库为 aryn_vessel。
--
-- 执行：mysql -u root -p aryn_boot < 132vessel_member_security_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. vessel_member 增加绑定来源列
--
-- DEFAULT 'MANUAL' 而不是 NULL：历史行与新行的语义都是「来源未知/手工绑定」，
-- 用 NOT NULL 让「来源」成为必填语义，避免后续代码写出空来源的成员行。
-- ---------------------------------------------------------------------------
SET @add_bind_source = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `vessel_member` ADD COLUMN `bind_source` varchar(32) NOT NULL DEFAULT ''MANUAL'' COMMENT ''绑定来源：APPLY_AUDIT/APPLY_AUDIT_MISMATCH/INVITE_CODE/MEMBER_ADD/ADMIN_ADD/SHARE_CART/MANUAL'' AFTER `remark`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'vessel_member'
    AND COLUMN_NAME = 'bind_source'
);
PREPARE stmt_bind_source FROM @add_bind_source; EXECUTE stmt_bind_source; DEALLOCATE PREPARE stmt_bind_source;

SET @add_bind_reason = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `vessel_member` ADD COLUMN `bind_reason` varchar(500) DEFAULT NULL COMMENT ''绑定补充原因（审核跨船偏离理由等）'' AFTER `bind_source`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'vessel_member'
    AND COLUMN_NAME = 'bind_reason'
);
PREPARE stmt_bind_reason FROM @add_bind_reason; EXECUTE stmt_bind_reason; DEALLOCATE PREPARE stmt_bind_reason;

-- ---------------------------------------------------------------------------
-- 2. 按来源筛查成员的索引
--
-- 管理端「成员来源」列与「按来源回收」是运营处理误绑的入口：
-- SHARE_CART 来源（分享链接自动补建，未经任何审核）是最需要定期巡检的一类。
-- 不加索引时按来源筛选会退化成「取出该船全部成员再过滤」。
-- ---------------------------------------------------------------------------
SET @add_bind_source_idx = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `vessel_member` ADD KEY `idx_vessel_member_bind_source` (`tenant_id`, `vessel_id`, `bind_source`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'vessel_member'
    AND INDEX_NAME = 'idx_vessel_member_bind_source'
);
PREPARE stmt_bind_source_idx FROM @add_bind_source_idx; EXECUTE stmt_bind_source_idx; DEALLOCATE PREPARE stmt_bind_source_idx;

-- ---------------------------------------------------------------------------
-- 3. 存量回填：只回填能判定的来源
--
-- 能判定的只有一类：成员关系可以关联到一条「已通过」的绑定申请单
-- （vessel_bind_apply.status='2' 且 matched_vessel_id 指向同一艘船、user_id 同一人）。
-- 这类成员几乎必然是该申请审核通过时写入的。
--
-- 其余通道（邀请码/分享链接/船管加人/管理端绑定）历史上没有留痕，
-- 任何回填都只是猜测 —— 保持 MANUAL，让运营在界面上看到「来源不明」比看到错误来源更有价值。
-- ---------------------------------------------------------------------------
UPDATE `vessel_member` m
JOIN `vessel_bind_apply` a
  ON a.`tenant_id` = m.`tenant_id`
 AND a.`matched_vessel_id` = m.`vessel_id`
 AND a.`user_id` = m.`user_id`
 AND a.`status` = '2'
 AND a.`del_flag` = '0'
SET m.`bind_source` = 'APPLY_AUDIT'
WHERE m.`bind_source` = 'MANUAL';

-- ---------------------------------------------------------------------------
-- 4. 成员关系审计日志（追加写，不提供业务更新）
--
-- 与靠港计划变更日志（vessel_call_change_log）同构：只记「谁在什么时候把谁怎么改了」。
-- 之所以单独建表而不是打日志：误绑的追查常常发生在几个月后，日志早已滚动掉，
-- 而「为什么把 8 号绑到了 7 号」必须能给出操作人、时间与理由。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `vessel_member_audit_log` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `vessel_id` varchar(32) NOT NULL COMMENT '船舶ID',
  `user_id` varchar(32) NOT NULL COMMENT '商城用户ID',
  `action` varchar(32) NOT NULL COMMENT '动作：BIND新建绑定/REBIND复活历史行/ROLE_CHANGE角色变更/UNBIND解绑',
  `bind_source` varchar(32) DEFAULT NULL COMMENT '本次动作对应的绑定来源',
  `old_member_role` char(2) DEFAULT NULL COMMENT '原成员角色',
  `new_member_role` char(2) DEFAULT NULL COMMENT '新成员角色',
  `reason` varchar(500) DEFAULT NULL COMMENT '原因（跨船偏离理由、解绑原因等）',
  `operator_id` varchar(32) DEFAULT NULL COMMENT '操作人ID（用户自助操作时为本人）',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名快照',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_vessel_member_audit_target` (`tenant_id`, `vessel_id`, `user_id`, `create_time`),
  KEY `idx_vessel_member_audit_action` (`tenant_id`, `action`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='船舶成员关系审计日志';

-- ---------------------------------------------------------------------------
-- 自检：两列 + 索引应就位，审计表应存在
-- ---------------------------------------------------------------------------
SELECT 'vessel_member.bind_source' AS item, COUNT(*) AS cnt
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'aryn_boot' AND TABLE_NAME = 'vessel_member' AND COLUMN_NAME = 'bind_source'
UNION ALL
SELECT 'vessel_member.bind_reason', COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'aryn_boot' AND TABLE_NAME = 'vessel_member' AND COLUMN_NAME = 'bind_reason'
UNION ALL
SELECT 'idx_vessel_member_bind_source', COUNT(*)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'aryn_boot' AND TABLE_NAME = 'vessel_member' AND INDEX_NAME = 'idx_vessel_member_bind_source'
UNION ALL
SELECT 'vessel_member_audit_log', COUNT(*)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'aryn_boot' AND TABLE_NAME = 'vessel_member_audit_log';

-- 来源分布：回填后应看到 APPLY_AUDIT（可判定的）+ MANUAL（历史不可判定）
SELECT `bind_source`, COUNT(*) AS cnt
FROM `vessel_member`
WHERE `del_flag` = '0'
GROUP BY `bind_source`
ORDER BY cnt DESC;

SET FOREIGN_KEY_CHECKS = 1;
