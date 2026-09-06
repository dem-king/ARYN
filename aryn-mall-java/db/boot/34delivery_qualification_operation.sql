-- 配送资格操作补偿记录表增量迁移（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 34delivery_qualification_operation.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- 配送资格操作（可靠授权/回收记录）
-- 说明：UPMS 角色授予/回收是跨服务远程操作，无法随订单域本地事务回滚。
-- 本表作为 outbox：本地事务提交前写入待处理操作，事务提交后同步执行一次，
-- 失败由补偿任务（DeliveryQualificationRetryJob）按指数退避重试直到成功，
-- 保证不残留孤儿角色或未回收权限。
-- 幂等键 (tenant_id, idempotent_key) 保证同一租户内同一员工同一角色同一操作仅一条记录，
-- 历史记录复用重置，不重复新增。
CREATE TABLE IF NOT EXISTS `delivery_qualification_operation` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `staff_id` varchar(32) NOT NULL COMMENT '配送员资料ID（delivery_staff.id）',
  `sys_user_id` varchar(32) NOT NULL COMMENT '员工账号ID（sys_user.id）',
  `role_code` varchar(64) NOT NULL COMMENT '角色编码',
  `operation` varchar(16) NOT NULL COMMENT '操作类型：GRANT授予 REVOKE回收',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待处理 DONE已完成',
  `idempotent_key` varchar(128) NOT NULL COMMENT '幂等键（operation:sys_user_id:role_code）',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已执行次数（含失败重试）',
  `last_error` varchar(512) DEFAULT NULL COMMENT '最近一次失败原因',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_qual_op_key` (`tenant_id`, `idempotent_key`),
  KEY `idx_delivery_qual_op_retry` (`tenant_id`, `status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配送资格操作补偿记录';

SET FOREIGN_KEY_CHECKS = 1;
