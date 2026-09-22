-- 悦航购家用电器/手机数码类目图片回填（Cloud 微服务模式）
-- 目标库：aryn_product（类目在 aryn_product，素材在 aryn_upms）
-- 背景：191/192 段 9 个类目（家用电器/手机数码/电视/空调/冰箱/手机/智能设备/无人机/电脑）
--       原 category_pic 指向已失效的 MinIO 域名 minio.Aetheryn.cn，本次回填为本机存储 URL。
-- 内容：更新 category_pic，并在 sys_material 素材库登记同一批图片，使后台「素材中心」可见可复用。
-- 图片实体：db/assets/appliance-digital-category-images/（清单见该目录 manifest.tsv）
-- 部署方式：将 stored_file（uuid.jpg）放入文件存储根目录 /data/aryn/uploads/{TENANT}/；本脚本只写库。
-- 特性：可重复执行；仅按类目 ID 精确匹配更新，不新增/删除类目，不触碰其它数据。
--
-- 执行：mysql -u root -p aryn_product < 73appliance_digital_category_images.sql


USE `aryn_product`;
SET NAMES utf8mb4;

-- ---------- 1. 类目图回填（2 一级 + 7 二级） ----------
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/7f1b75dc-7ed4-50c5-b0c2-d1618c5b7c69.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1912861788486148097' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/cb9c985d-cedf-5418-adc0-14c4762c1219.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1912863400222957569' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/4fca39a6-bedc-521c-bc25-768d7b1db1c0.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1912862220591734785' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/8fc757bb-5947-56a9-97d2-99b67880b72a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1912862615531593730' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/a866ffc8-5fee-52f0-af0e-c05acae6d737.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1912863000879079426' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/b1814746-6f02-575a-92e8-28c704a88f07.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1912863683464306689' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/004d03fc-ed39-574e-9201-79004df20bb2.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1912863991967948801' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/a5d1fd41-b122-598f-952e-dca167c548df.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1912864294511484929' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/449166a8-2256-5bcd-ab28-009062169a96.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '1925541056051564546' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

-- ---------- 2. 素材库登记（等价于管理端上传接口的副作用） ----------
USE `aryn_upms`;

UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/7f1b75dc-7ed4-50c5-b0c2-d1618c5b7c69.jpg', `name` = '家用电器.jpg', `file_size` = 20149, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000001' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000001', '1', '-1', '家用电器.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/7f1b75dc-7ed4-50c5-b0c2-d1618c5b7c69.jpg', 20149, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000001');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/cb9c985d-cedf-5418-adc0-14c4762c1219.jpg', `name` = '手机数码.jpg', `file_size` = 91853, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000002' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000002', '1', '-1', '手机数码.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/cb9c985d-cedf-5418-adc0-14c4762c1219.jpg', 91853, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000002');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/4fca39a6-bedc-521c-bc25-768d7b1db1c0.jpg', `name` = '电视.jpg', `file_size` = 142503, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000003' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000003', '1', '-1', '电视.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/4fca39a6-bedc-521c-bc25-768d7b1db1c0.jpg', 142503, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000003');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/8fc757bb-5947-56a9-97d2-99b67880b72a.jpg', `name` = '空调.jpg', `file_size` = 128450, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000004' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000004', '1', '-1', '空调.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/8fc757bb-5947-56a9-97d2-99b67880b72a.jpg', 128450, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000004');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/a866ffc8-5fee-52f0-af0e-c05acae6d737.jpg', `name` = '冰箱.jpg', `file_size` = 6467, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000005' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000005', '1', '-1', '冰箱.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/a866ffc8-5fee-52f0-af0e-c05acae6d737.jpg', 6467, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000005');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/b1814746-6f02-575a-92e8-28c704a88f07.jpg', `name` = '手机.jpg', `file_size` = 233692, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000006' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000006', '1', '-1', '手机.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/b1814746-6f02-575a-92e8-28c704a88f07.jpg', 233692, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000006');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/004d03fc-ed39-574e-9201-79004df20bb2.jpg', `name` = '智能设备.jpg', `file_size` = 36680, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000007' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000007', '1', '-1', '智能设备.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/004d03fc-ed39-574e-9201-79004df20bb2.jpg', 36680, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000007');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/a5d1fd41-b122-598f-952e-dca167c548df.jpg', `name` = '无人机.jpg', `file_size` = 70486, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000008' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000008', '1', '-1', '无人机.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/a5d1fd41-b122-598f-952e-dca167c548df.jpg', 70486, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000008');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/449166a8-2256-5bcd-ab28-009062169a96.jpg', `name` = '电脑.jpg', `file_size` = 56448, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9590000000000000009' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9590000000000000009', '1', '-1', '电脑.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/449166a8-2256-5bcd-ab28-009062169a96.jpg', 56448, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9590000000000000009');
