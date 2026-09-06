-- 字典与菜单乱码数据修复（Cloud 微服务模式）
-- 目标库：字典/菜单：aryn_upms；页面装修：aryn_promotion
-- 背景：历史增量脚本曾以 latin1/cp1252 客户端字符集导入，UTF-8 文本被双重编码，
--       界面出现「é˜¿é‡ŒOSS」「ä¸ƒç‰›äº‘」等乱码，涉及：存储配置类型字典、
--       商品品牌/仓库配置菜单按钮、页面装修发布备注。
-- 原理：MySQL latin1 与 CP1252 同语义，先把存储值还原回原始字节，再按 utf8mb4 解读。
-- 特性：可重复执行；仅命中含乱码特征（ÃÂäåæçèé 开头字节）的行，还原失败时保留原值，
--       正常中文与英文数据不受影响。
-- 执行：mysql -u root -p --default-character-set=utf8mb4 < 38dict_charset_mojibake_repair.sql
-- 验证：SELECT dict_label FROM aryn_upms.sys_dict_value WHERE dict_type='sys_storage_type';

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 字典值标签与备注（存储配置类型：阿里OSS/七牛云/腾讯云/MinIO/本机存储）
UPDATE `sys_dict_value`
JOIN (
    SELECT `id`,
           CONVERT(BINARY CONVERT(`dict_label` USING latin1) USING utf8mb4) AS fixed_label,
           CONVERT(BINARY CONVERT(`remarks` USING latin1) USING utf8mb4) AS fixed_remarks
    FROM `sys_dict_value`
    WHERE `dict_label` REGEXP _utf8mb4 0x5BC383C282C3A4C3A5C3A6C3A7C3A8C3A95D
       OR `remarks` REGEXP _utf8mb4 0x5BC383C282C3A4C3A5C3A6C3A7C3A8C3A95D
) t ON t.`id` = `sys_dict_value`.`id`
SET `sys_dict_value`.`dict_label` = COALESCE(t.fixed_label, `sys_dict_value`.`dict_label`),
    `sys_dict_value`.`remarks` = COALESCE(t.fixed_remarks, `sys_dict_value`.`remarks`);

-- 2) 菜单名称（商品品牌列表/查询/新增/修改/删除、查看/保存仓库配置等）
UPDATE `sys_menu`
JOIN (
    SELECT `id`,
           CONVERT(BINARY CONVERT(`name` USING latin1) USING utf8mb4) AS fixed_name
    FROM `sys_menu`
    WHERE `name` REGEXP _utf8mb4 0x5BC383C282C3A4C3A5C3A6C3A7C3A8C3A95D
) t ON t.`id` = `sys_menu`.`id`
SET `sys_menu`.`name` = COALESCE(t.fixed_name, `sys_menu`.`name`);

USE `aryn_promotion`;

-- 3) 页面装修发布备注
UPDATE `page_design_version`
JOIN (
    SELECT `id`,
           CONVERT(BINARY CONVERT(`publish_remark` USING latin1) USING utf8mb4) AS fixed_remark
    FROM `page_design_version`
    WHERE `publish_remark` REGEXP _utf8mb4 0x5BC383C282C3A4C3A5C3A6C3A7C3A8C3A95D
) t ON t.`id` = `page_design_version`.`id`
SET `page_design_version`.`publish_remark` = COALESCE(t.fixed_remark, `page_design_version`.`publish_remark`);

SET FOREIGN_KEY_CHECKS = 1;
