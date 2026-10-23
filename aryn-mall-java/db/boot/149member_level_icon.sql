-- 会员等级图标回填（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 内容：为 147member_level_seed.sql 的 5 档种子等级（2120x）回填 level_icon，
--       并在 sys_material 素材库登记同一批徽章，使后台「素材中心」可见可复用。
-- 图片实体：db/assets/member-level-badges/（透明底 PNG，512x512，UUID 文件名与本脚本 URL 一一对应）
-- 部署方式：先把 5 个 png 放入本地文件存储根目录 {bucket}/1590229800633634816/，
--           再执行本脚本；只跑 SQL 不铺文件会回源 404。
-- 特性：可重复执行；仅按 2120 固定前缀的 5 个等级精确更新 level_icon，
--       素材按 2120 系列素材 ID upsert，不新增/删除等级，不触碰其它数据。
-- 注意：Boot 模式 context-path 为 /boot，故回源路径首段为 /boot。
--
-- 执行：mysql -u root -p aryn_boot < 149member_level_icon.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------- 1. 五档等级图标回填（仅 2120 种子等级，精确匹配） ----------
UPDATE `member_level`
   SET `level_icon` = 'http://localhost:9999/boot/file/local/1590229800633634816/28a84c3c-7fec-488d-b1b9-2c0356b9b05a.png',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '2120000000000000001' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

UPDATE `member_level`
   SET `level_icon` = 'http://localhost:9999/boot/file/local/1590229800633634816/70d9b1d4-2e27-4e37-b893-82f6e2ff9cf0.png',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '2120000000000000002' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

UPDATE `member_level`
   SET `level_icon` = 'http://localhost:9999/boot/file/local/1590229800633634816/3c500bce-66b1-46c3-979e-815361c9755e.png',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '2120000000000000003' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

UPDATE `member_level`
   SET `level_icon` = 'http://localhost:9999/boot/file/local/1590229800633634816/0c072b08-a6b6-42cf-ab25-cb7daa1f87fd.png',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '2120000000000000004' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

UPDATE `member_level`
   SET `level_icon` = 'http://localhost:9999/boot/file/local/1590229800633634816/86ba299c-5764-4772-8e06-9d47d2d33293.png',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '2120000000000000005' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

-- ---------- 2. 素材库登记（2120 系列素材 ID，存在则刷新、不存在则补，幂等） ----------
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/28a84c3c-7fec-488d-b1b9-2c0356b9b05a.png',
       `name` = '新客会员徽章.png', `file_size` = 138768, `del_flag` = '0',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '212000000000000101' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '212000000000000101', '1', '-1', '新客会员徽章.png',
       'http://localhost:9999/boot/file/local/1590229800633634816/28a84c3c-7fec-488d-b1b9-2c0356b9b05a.png',
       138768, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '212000000000000101');

UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/70d9b1d4-2e27-4e37-b893-82f6e2ff9cf0.png',
       `name` = '银卡会员徽章.png', `file_size` = 230211, `del_flag` = '0',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '212000000000000102' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '212000000000000102', '1', '-1', '银卡会员徽章.png',
       'http://localhost:9999/boot/file/local/1590229800633634816/70d9b1d4-2e27-4e37-b893-82f6e2ff9cf0.png',
       230211, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '212000000000000102');

UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/3c500bce-66b1-46c3-979e-815361c9755e.png',
       `name` = '金卡会员徽章.png', `file_size` = 283080, `del_flag` = '0',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '212000000000000103' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '212000000000000103', '1', '-1', '金卡会员徽章.png',
       'http://localhost:9999/boot/file/local/1590229800633634816/3c500bce-66b1-46c3-979e-815361c9755e.png',
       283080, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '212000000000000103');

UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/0c072b08-a6b6-42cf-ab25-cb7daa1f87fd.png',
       `name` = '铂金会员徽章.png', `file_size` = 187858, `del_flag` = '0',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '212000000000000104' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '212000000000000104', '1', '-1', '铂金会员徽章.png',
       'http://localhost:9999/boot/file/local/1590229800633634816/0c072b08-a6b6-42cf-ab25-cb7daa1f87fd.png',
       187858, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '212000000000000104');

UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/86ba299c-5764-4772-8e06-9d47d2d33293.png',
       `name` = '钻石会员徽章.png', `file_size` = 288754, `del_flag` = '0',
       `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '212000000000000105' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '212000000000000105', '1', '-1', '钻石会员徽章.png',
       'http://localhost:9999/boot/file/local/1590229800633634816/86ba299c-5764-4772-8e06-9d47d2d33293.png',
       288754, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '212000000000000105');

-- 验证：
-- SELECT level_name, level_icon FROM member_level
--  WHERE tenant_id='1590229800633634816' AND del_flag='0' ORDER BY sort_order;
-- SELECT id,name,url,file_size FROM sys_material WHERE id LIKE '2120000000000001%' AND del_flag='0';
