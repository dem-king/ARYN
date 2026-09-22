-- 悦航购船舶物料类目图片回填（Boot 单体模式）
-- 目标库：aryn_boot（单体模式所有表同库）
-- 内容：为 963 段 7 个船舶物料类目（1 一级 + 6 二级）回填 category_pic，
--       并在 sys_material 素材库登记同一批图片，使后台「素材中心」可见可复用。
-- 图片实体：db/assets/vessel-category-images/（清单见该目录 manifest.tsv）
-- 部署方式：将 stored_file（uuid.jpg）放入文件存储根目录 /data/aryn/uploads/{TENANT}/；本脚本只写库。
-- 特性：可重复执行；仅按类目 ID 963x 精确匹配更新，不新增/删除类目，不触碰其它数据。
-- 注意：Boot 模式 context-path 为 /boot，故回源路径首段为 /boot；
--
-- 执行：mysql -u root -p aryn_boot < 72vessel_category_images.sql


USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------- 1. 类目图回填（1 一级 + 6 二级） ----------
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/boot/file/local/1590229800633634816/d9230b66-c1ec-5ccd-a0f0-7af6757c1206.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9630000000000000001' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/boot/file/local/1590229800633634816/6f2d9c67-d579-5756-a146-79858a6034ec.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9630000000000000002' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/boot/file/local/1590229800633634816/de1be548-ad4b-5988-be0e-678b17f9d86b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9630000000000000003' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/boot/file/local/1590229800633634816/d69f171d-a4e5-5975-97f9-d4659f145d2d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9630000000000000004' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/boot/file/local/1590229800633634816/7e0d05a0-ebd6-556b-b1cf-0c16f6465526.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9630000000000000005' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/boot/file/local/1590229800633634816/ab83f69c-c67f-50d2-9049-d17ddc8c5d0f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9630000000000000006' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/boot/file/local/1590229800633634816/930cdcc6-681f-5755-ab18-5e4b83cbb229.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9630000000000000007' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

-- ---------- 2. 素材库登记（等价于管理端上传接口的副作用） ----------
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/d9230b66-c1ec-5ccd-a0f0-7af6757c1206.jpg', `name` = '船舶物料.jpg', `file_size` = 259455, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9580000000000000001' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9580000000000000001', '1', '-1', '船舶物料.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/d9230b66-c1ec-5ccd-a0f0-7af6757c1206.jpg', 259455, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9580000000000000001');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/6f2d9c67-d579-5756-a146-79858a6034ec.jpg', `name` = '清洁用品.jpg', `file_size` = 15407, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9580000000000000002' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9580000000000000002', '1', '-1', '清洁用品.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/6f2d9c67-d579-5756-a146-79858a6034ec.jpg', 15407, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9580000000000000002');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/de1be548-ad4b-5988-be0e-678b17f9d86b.jpg', `name` = '安全防护.jpg', `file_size` = 61810, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9580000000000000003' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9580000000000000003', '1', '-1', '安全防护.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/de1be548-ad4b-5988-be0e-678b17f9d86b.jpg', 61810, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9580000000000000003');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/d69f171d-a4e5-5975-97f9-d4659f145d2d.jpg', `name` = '甲板索具.jpg', `file_size` = 10567, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9580000000000000004' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9580000000000000004', '1', '-1', '甲板索具.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/d69f171d-a4e5-5975-97f9-d4659f145d2d.jpg', 10567, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9580000000000000004');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/7e0d05a0-ebd6-556b-b1cf-0c16f6465526.jpg', `name` = '轮机备件.jpg', `file_size` = 92083, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9580000000000000005' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9580000000000000005', '1', '-1', '轮机备件.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/7e0d05a0-ebd6-556b-b1cf-0c16f6465526.jpg', 92083, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9580000000000000005');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/ab83f69c-c67f-50d2-9049-d17ddc8c5d0f.jpg', `name` = '电工照明.jpg', `file_size` = 47835, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9580000000000000006' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9580000000000000006', '1', '-1', '电工照明.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/ab83f69c-c67f-50d2-9049-d17ddc8c5d0f.jpg', 47835, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9580000000000000006');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/930cdcc6-681f-5755-ab18-5e4b83cbb229.jpg', `name` = '船用食品.jpg', `file_size` = 82284, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9580000000000000007' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9580000000000000007', '1', '-1', '船用食品.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/930cdcc6-681f-5755-ab18-5e4b83cbb229.jpg', 82284, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9580000000000000007');
