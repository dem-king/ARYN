-- 悦航购船舶域数据源字符集参数修复（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 背景：47 号脚本写入的 aryn-vessel-biz-dev.yml 数据源 URL 使用了
--       characterEncoding=utf8mb4。MySQL Connector/J 9.x 已移除 utf8mb4 到 Java
--       字符集的隐式映射，连接时会抛
--       java.sql.SQLException: Unsupported character encoding 'utf8mb4'，
--       导致 aryn-vessel-biz 无法建立数据库连接、/actuator/health 长时间挂起。
--       其余微服务配置统一使用 characterEncoding=UTF-8。
-- 特性：幂等执行，仅替换该参数，不删除或重建 Nacos 配置数据。
-- 执行：mysql -u root -p < 61vessel_datasource_charset_patch.sql

USE `aryn_nacos`;

UPDATE `config_info`
SET `content` = REPLACE(`content`, 'characterEncoding=utf8mb4', 'characterEncoding=UTF-8'),
    `md5` = MD5(REPLACE(`content`, 'characterEncoding=utf8mb4', 'characterEncoding=UTF-8')),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-vessel-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `content` LIKE '%characterEncoding=utf8mb4%';

-- 验证：应返回 0 行
SELECT COUNT(*) AS remaining_utf8mb4
FROM `config_info`
WHERE `data_id` = 'aryn-vessel-biz-dev.yml'
  AND `content` LIKE '%characterEncoding=utf8mb4%';
