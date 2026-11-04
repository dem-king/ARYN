-- ============================================================================
-- 平台租户默认文件存储配置增量（Boot 单体模式）
-- 目标库：aryn_boot
--
-- 背景：平台管理员 system 属于「系统租户」（tenant_id=1881232176465358849）。
--       个人信息页更换头像、平台级素材上传等操作都按登录用户自己的租户查找
--       sys_storage_config；平台租户此前没有启用的存储配置，上传直接报
--       「当前租户未配置启用的文件存储，请联系管理员完成配置」。
--
-- 内容：为系统租户补一条启用的本机存储配置，根目录沿用部署约定
--       /data/aryn/uploads（boot/cloud 的 Docker 都把这个目录挂成持久卷，
--       文件按 tenant_id 分子目录落盘，不会和其他租户串文件）。
--
-- 幂等：固定 ID + INSERT IGNORE + 「已存在启用配置则跳过」，可重复执行；
--       已有对象存储或自定义配置的部署不会被覆盖。
--
-- 注意：domain 留空时返回站内相对 URL。如果管理端与上传服务不同源（例如
--       本机 Vite dev server 打开管理端），请在「系统设置-文件存储配置」里
--       补公开访问域名；本机开发可执行 dev-tools/dev-image-tunnel.sh，
--       它会同步 sys_storage_config.domain。
--
-- 执行：mysql -u root -p aryn_boot < 181platform_storage_config.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;

INSERT IGNORE INTO `sys_storage_config`
	(`id`, `access_key`, `access_secret`, `endpoint`, `bucket`, `type`, `create_time`, `update_time`,
	 `dir`, `is_https`, `del_flag`, `tenant_id`, `create_by`, `update_by`, `status`,
	 `style_access_enabled`, `domain`)
SELECT '1881232176465358849001', NULL, NULL, NULL, '/data/aryn/uploads', 'local', NOW(), NOW(),
	   NULL, NULL, '0', '1881232176465358849', 'system', NULL, '0', 0, NULL
FROM DUAL
WHERE NOT EXISTS (
	SELECT 1
	FROM `sys_storage_config`
	WHERE `tenant_id` = '1881232176465358849'
	  AND `del_flag` = '0'
	  AND `status` = '0'
);

-- 自检：平台租户应有且仅有一条启用的存储配置
SELECT `id`, `type`, `bucket`, `status`, `domain`
FROM `sys_storage_config`
WHERE `tenant_id` = '1881232176465358849' AND `del_flag` = '0' AND `status` = '0';

SET FOREIGN_KEY_CHECKS = 1;
