-- 悦航购商品批量导入解析行增量迁移（Cloud 微服务模式）
-- 目标库：aryn_product
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_product < 50product_import_row_incremental.sql

USE `aryn_product`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 商品导入解析行（上传 Excel 后服务端解析结果，确认导入时重新校验）
CREATE TABLE IF NOT EXISTS `product_import_row` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `job_id` varchar(32) NOT NULL COMMENT '导入任务ID',
  `row_no` int NOT NULL DEFAULT 0 COMMENT '行号（从1开始，不含表头）',
  `row_content` text DEFAULT NULL COMMENT '行内容（受控JSON，字段与导入模板一致）',
  `valid_flag` char(2) NOT NULL DEFAULT '1' COMMENT '预览校验结果：1有效 0错误',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_product_import_row_job` (`tenant_id`,`job_id`,`row_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品导入解析行';

SET FOREIGN_KEY_CHECKS = 1;
