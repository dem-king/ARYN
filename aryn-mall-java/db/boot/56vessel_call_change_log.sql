-- 悦航购靠港计划变更日志增量迁移（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 内容：靠港计划变更日志（零成本 ETA 自动提醒：人工改 ETA/ETD/泊位/时间窗后留痕并触发影响面通知）。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `vessel_call_change_log` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `call_id` varchar(32) NOT NULL COMMENT '靠港计划ID',
  `vessel_id` varchar(32) NOT NULL COMMENT '船舶ID',
  `old_eta` datetime DEFAULT NULL COMMENT '原 ETA',
  `new_eta` datetime DEFAULT NULL COMMENT '新 ETA',
  `old_etd` datetime DEFAULT NULL COMMENT '原 ETD',
  `new_etd` datetime DEFAULT NULL COMMENT '新 ETD',
  `old_berth` varchar(128) DEFAULT NULL COMMENT '原泊位',
  `new_berth` varchar(128) DEFAULT NULL COMMENT '新泊位',
  `old_window_start` datetime DEFAULT NULL COMMENT '原时间窗开始',
  `new_window_start` datetime DEFAULT NULL COMMENT '新时间窗开始',
  `old_window_end` datetime DEFAULT NULL COMMENT '原时间窗结束',
  `new_window_end` datetime DEFAULT NULL COMMENT '新时间窗结束',
  `operator_id` varchar(32) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名快照',
  `remark` varchar(500) DEFAULT NULL COMMENT '变更说明',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_vessel_call_change_call` (`tenant_id`,`call_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='靠港计划变更日志';

SET FOREIGN_KEY_CHECKS = 1;
