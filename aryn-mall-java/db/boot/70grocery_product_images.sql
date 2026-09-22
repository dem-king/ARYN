-- 悦航购商超商品主图回填（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 内容：为 41grocery_catalog_seed.sql 的 106 个商超种子商品（SPU 954x）回填 spu_urls，
--       并在 sys_material 素材库登记同一批图片，使后台「素材中心」可见可复用。
-- 图片实体：db/assets/grocery-product-images/（清单见该目录 manifest.tsv）
-- 部署方式：dev-tools/install-product-images.sh 负责把图片放进文件存储根目录；本脚本只写库。
-- 特性：可重复执行；仅按 SPU 954x 精确匹配更新，不新增/删除商品，不触碰其它商品与素材。
-- 注意：Boot 模式 context-path 为 /boot，故回源路径首段为 /boot；
--       Cloud 模式经网关 /upms 路由到 upms 服务，故首段为 /upms。
--
-- 执行：mysql -u root -p aryn_boot < 70grocery_product_images.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------- 1. 商品主图回填（仅商超种子 SPU 954x） ----------
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/d6650771-5f1b-5910-9e70-8aac605a0ad2.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000001' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/bf250889-7506-5663-b68c-f9c267227d0f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000002' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/d3dcc610-8d23-5908-bf5f-e8553e67d279.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000003' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/f5fa1f7f-a5d8-5d06-91ae-6105281e3a18.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000004' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/0daad437-94a2-541a-811b-a760b7045604.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000005' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/9ad07eba-62cd-5411-972c-0b7c66a2da72.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000006' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/062cdae5-544c-5f0b-96c5-288987793e4a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000007' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/0d13ba9e-652f-5c19-8b8b-6c020296e246.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000008' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/8193de5e-8775-5351-8c44-92d3295afda6.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000009' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/78f47746-bc68-56bf-8070-ee7ffe72eeba.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000010' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/6d9dbdc2-3aff-5c19-8617-b989388da68f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000011' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/392f5c86-3d4d-51bc-83a5-2eb02c49ab51.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000012' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/5ed8a171-36b3-5dd4-acb1-cac617e71ea0.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000013' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/013568ca-01cb-519c-8c0e-1b9bf6a0fc50.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000014' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/41816ad8-59e0-56eb-a90c-a66c24d2f6fd.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000015' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/e8890bed-4962-5297-994f-8341236a738e.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000016' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/911bd777-8d4a-5d03-9a5b-e7f7b1e1e7ff.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000017' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/751151ee-2861-52ed-837b-c41fc7af06c9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000018' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/30c310d8-3773-57ba-97e9-eac41e98e23b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000019' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/bd8dc217-50a4-5047-a319-482da462045a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000020' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/fb63dbf5-f4c8-5882-a4ae-cbf76421a7fd.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000021' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/66ec184a-1eee-5b49-85bb-2198f26d8252.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000022' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/f1120f6f-ee96-5f3d-97d3-800c5b6b8d62.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000023' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/bc58e7ba-12d6-5cb6-b312-ffb6ac1b928d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000024' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/871161fb-ff69-5661-8cbe-1e84345cc58e.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000025' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/32ce4e22-347c-5fb4-9cf6-df94de028362.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000026' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/1060e150-4d0d-5e07-a14f-76f3dce01ef5.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000027' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/b2879c2e-3b6d-5c01-8d6f-6d0ad4b2333e.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000028' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/3de6b614-e23e-5171-8022-687092f3f29d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000029' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/46805a9b-a84f-5aa8-a0c4-351fa4357bac.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000030' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/26e8ab5d-67fd-5438-82c7-0074ea3b4cbb.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000031' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/c2ae8584-af58-59bf-8123-0e72a7cc899e.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000032' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/aab20876-68c4-594e-aa88-0e1dc67b9c59.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000033' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/aa338aaf-580a-5a96-b192-bbcc790befb3.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000034' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/2d751244-d448-51e1-a348-60a48e06e3d5.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000035' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/d9d7844b-7a20-5b6c-86be-0a49065cb296.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000036' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/38199a9b-2470-5865-ada7-db259f7e408a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000037' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/b83ab7f9-4f61-5ec9-bffd-ad0e3bd4b61d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000038' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/eb22e3f1-6e4e-56fc-b7cf-f8c3728d3dff.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000039' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/5e949170-6408-5936-a2d0-abcfba1f403b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000040' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/66602ae6-c0fd-56e3-ad20-8337595e7f36.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000041' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/f581e529-0bcc-5acd-890d-13babe4b13af.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000042' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/0452355d-193b-5fe1-9b98-333f2e1455e4.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000043' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/06eccc45-5f57-5d6d-89c1-f2fc3e465c3b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000044' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/308f143f-b208-54ea-887e-fbf4760863bc.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000045' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/18394667-2bb3-5601-a4fa-3c70ec34112e.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000046' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/d361d47d-c4d7-5cc3-acf2-567242d2d154.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000047' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/4121b190-9fe2-54b0-99f7-d44dd7cf3ba2.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000048' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/b07be96d-a998-51ea-b7af-9ab2f699d55c.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000049' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/ce669240-4df4-54b4-9162-4e482cdc8048.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000050' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/8d1bd7a8-df8b-5daf-ba99-05b71b043e00.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000051' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/ebef5a7c-1d06-5264-a8c5-93cdc4637faa.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000052' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/c175db19-2690-5168-86b0-edb0951321b6.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000053' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/1486989a-f4c9-5cdb-8c6f-c70b778a70fd.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000054' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/116f6ff3-5d79-5948-9356-c244f0a87dc9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000055' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/e8d7a4f0-27ee-5787-928f-4748bff80a9f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000056' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/621f931e-362f-523d-9e38-a990a7d2e21a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000057' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/8eba70da-8546-54ed-ba0f-223e88258cbe.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000058' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/7865b31b-6842-553f-ac29-a6eac07ac278.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000059' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/75a1b2ae-da71-544c-89c2-37728c585fa9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000060' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/ae471a67-61d0-5e69-98c4-9277580a34cb.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000061' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/b4147e7c-cbeb-5603-94e3-cf4a93471720.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000062' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/4de1ff54-a5cb-596c-825b-de40d97c133b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000063' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/ddef0e70-5c32-5179-a4f2-e8eec209124d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000064' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/b1782e99-881f-5a82-b94f-a48d33a7991f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000065' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/2f09f7ec-77ca-50b7-bf5b-9285f8b85135.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000066' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/15bad279-be8b-5696-9852-73c734bb2041.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000067' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/2fa2df12-c8b7-5cff-a979-89fd8ae2c4ae.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000068' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/fbaed193-bc98-58a8-b247-29962e7a152c.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000069' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/ba4af140-30a4-5877-be2d-51dbf25c1fc1.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000070' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/39556ada-5532-56e0-9a5e-0735e9038534.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000071' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/520a7b9a-75be-54fc-8b90-b9fce56a2efc.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000072' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/be924432-060e-5c71-8dfb-ff34299449ae.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000073' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/2bd66d4c-bf73-5efd-bf6e-6ef49a7e609f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000074' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/3bf9b1d6-0437-5072-9513-55eb8e307c55.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000075' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/bd361f5a-89ce-548d-a16f-0b267dabfefb.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000076' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/d8975494-ae89-5b29-a742-611bdbfa82a9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000077' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/af9089c2-c849-518e-a095-9a871bff04b9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000078' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/88cf76b8-46fd-5c10-aea9-0ca6e9138b8d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000079' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/8fc7cdcf-35ed-5be0-8df3-b43331fdd619.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000080' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/b21fb9b8-cf3b-56ed-902d-fdc80cf46ccc.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000081' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/eb16f801-4c57-5c21-b3eb-0f78f8851a6d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000082' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/8d7c1946-cd15-5c56-bb8e-394eb05a2e42.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000083' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/454da1ec-e6be-563b-bd5f-b67329dc0566.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000084' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/fcae3db3-5302-50f5-ab43-6cd50b3ef07a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000085' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/514878f9-23b8-5a65-9a0a-0638f85e2496.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000086' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/44423e72-9fcc-53d4-8c6b-4e01bec8519b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000087' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/fa3ee693-7d85-5326-acde-a06c56121183.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000088' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/bd7cbaa4-9ab9-5288-8f83-99df5267e334.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000089' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/c845c250-5ef6-5332-bccc-d2b0917c8e07.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000090' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/4b7a5b3d-c338-5a4c-a159-cce84b78da66.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000091' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/a525a124-ba29-5043-baf5-837287f9d1d8.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000092' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/38f65e00-4ec1-52f0-9963-f11bebcda5a6.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000093' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/53164646-f933-53bf-b0f1-5d2fb7281e7d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000094' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/7b466830-62a8-5f70-a423-06a5f6ce4973.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000095' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/13d20d35-c04d-54c5-a472-fbe33df34105.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000096' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/a51e9872-19cd-58d6-b086-c38c9cd0ecbf.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000097' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/1f0420fd-47f8-5c55-81b8-c3a27c648e69.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000098' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/442d5833-77c8-5e82-9ad2-0c9dc1cc4cef.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000099' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/4e892efd-ccc2-5570-8f58-9cd1a6db013c.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000100' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/a53e5dd5-3077-5699-91f6-948f11b3caee.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000101' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/2c9a9652-c7a7-51e0-85ad-5051ed5d0cf9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000102' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/2366f862-8b39-5b7c-ae0d-c81d6e31ecd2.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000103' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/f40ba0a6-3f02-5a91-8f2b-75d8b5b98567.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000104' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/1ded5713-3dc3-5082-89fe-5cf67c0a9d7e.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000105' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_spu`
   SET `spu_urls` = 'http://localhost:9999/boot/file/local/1590229800633634816/b29b4db5-08db-5eb7-82b3-a7e752107626.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9540000000000000106' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

-- ---------- 2. 素材库登记（等价于管理端上传接口的副作用） ----------
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/d6650771-5f1b-5910-9e70-8aac605a0ad2.jpg', `name` = '油麦菜 约300g-份.jpg', `file_size` = 403973, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000001' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000001', '1', '-1', '油麦菜 约300g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/d6650771-5f1b-5910-9e70-8aac605a0ad2.jpg', 403973, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000001');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/bf250889-7506-5663-b68c-f9c267227d0f.jpg', `name` = '新鲜菠菜 约300g-份.jpg', `file_size` = 24629, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000002' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000002', '1', '-1', '新鲜菠菜 约300g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/bf250889-7506-5663-b68c-f9c267227d0f.jpg', 24629, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000002');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/d3dcc610-8d23-5908-bf5f-e8553e67d279.jpg', `name` = '娃娃菜 3颗装 约500g.jpg', `file_size` = 30714, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000003' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000003', '1', '-1', '娃娃菜 3颗装 约500g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/d3dcc610-8d23-5908-bf5f-e8553e67d279.jpg', 30714, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000003');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/f5fa1f7f-a5d8-5d06-91ae-6105281e3a18.jpg', `name` = '黄心土豆 约500g-份.jpg', `file_size` = 249417, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000004' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000004', '1', '-1', '黄心土豆 约500g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/f5fa1f7f-a5d8-5d06-91ae-6105281e3a18.jpg', 249417, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000004');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/0daad437-94a2-541a-811b-a760b7045604.jpg', `name` = '紫皮洋葱 约500g-份.jpg', `file_size` = 175810, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000005' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000005', '1', '-1', '紫皮洋葱 约500g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/0daad437-94a2-541a-811b-a760b7045604.jpg', 175810, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000005');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/9ad07eba-62cd-5411-972c-0b7c66a2da72.jpg', `name` = '新鲜胡萝卜 约400g-份.jpg', `file_size` = 40471, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000006' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000006', '1', '-1', '新鲜胡萝卜 约400g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/9ad07eba-62cd-5411-972c-0b7c66a2da72.jpg', 40471, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000006');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/062cdae5-544c-5f0b-96c5-288987793e4a.jpg', `name` = '普罗旺斯西红柿 约500g-份.jpg', `file_size` = 238126, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000007' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000007', '1', '-1', '普罗旺斯西红柿 约500g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/062cdae5-544c-5f0b-96c5-288987793e4a.jpg', 238126, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000007');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/0d13ba9e-652f-5c19-8b8b-6c020296e246.jpg', `name` = '荷兰黄瓜 2根装 约300g.jpg', `file_size` = 113553, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000008' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000008', '1', '-1', '荷兰黄瓜 2根装 约300g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/0d13ba9e-652f-5c19-8b8b-6c020296e246.jpg', 113553, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000008');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/8193de5e-8775-5351-8c44-92d3295afda6.jpg', `name` = '紫长茄子 约400g-份.jpg', `file_size` = 145220, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000009' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000009', '1', '-1', '紫长茄子 约400g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/8193de5e-8775-5351-8c44-92d3295afda6.jpg', 145220, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000009');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/78f47746-bc68-56bf-8070-ee7ffe72eeba.jpg', `name` = '小香葱 约100g-份.jpg', `file_size` = 327239, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000010' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000010', '1', '-1', '小香葱 约100g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/78f47746-bc68-56bf-8070-ee7ffe72eeba.jpg', 327239, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000010');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/6d9dbdc2-3aff-5c19-8617-b989388da68f.jpg', `name` = '独头蒜 约200g-份.jpg', `file_size` = 43744, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000011' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000011', '1', '-1', '独头蒜 约200g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/6d9dbdc2-3aff-5c19-8617-b989388da68f.jpg', 43744, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000011');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/392f5c86-3d4d-51bc-83a5-2eb02c49ab51.jpg', `name` = '白玉菇 2连包 约400g.jpg', `file_size` = 72026, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000012' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000012', '1', '-1', '白玉菇 2连包 约400g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/392f5c86-3d4d-51bc-83a5-2eb02c49ab51.jpg', 72026, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000012');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/5ed8a171-36b3-5dd4-acb1-cac617e71ea0.jpg', `name` = '新鲜香菇 约300g-份.jpg', `file_size` = 269967, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000013' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000013', '1', '-1', '新鲜香菇 约300g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/5ed8a171-36b3-5dd4-acb1-cac617e71ea0.jpg', 269967, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000013');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/013568ca-01cb-519c-8c0e-1b9bf6a0fc50.jpg', `name` = '嫩豆腐 2盒装 约800g.jpg', `file_size` = 35691, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000014' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000014', '1', '-1', '嫩豆腐 2盒装 约800g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/013568ca-01cb-519c-8c0e-1b9bf6a0fc50.jpg', 35691, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000014');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/41816ad8-59e0-56eb-a90c-a66c24d2f6fd.jpg', `name` = '千张豆腐皮 约300g-份.jpg', `file_size` = 134363, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000015' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000015', '1', '-1', '千张豆腐皮 约300g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/41816ad8-59e0-56eb-a90c-a66c24d2f6fd.jpg', 134363, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000015');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/e8890bed-4962-5297-994f-8341236a738e.jpg', `name` = '烟台红富士苹果 4个装 约1kg.jpg', `file_size` = 139884, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000016' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000016', '1', '-1', '烟台红富士苹果 4个装 约1kg.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/e8890bed-4962-5297-994f-8341236a738e.jpg', 139884, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000016');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/911bd777-8d4a-5d03-9a5b-e7f7b1e1e7ff.jpg', `name` = '新疆库尔勒香梨 6个装 约1.2kg.jpg', `file_size` = 84513, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000017' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000017', '1', '-1', '新疆库尔勒香梨 6个装 约1.2kg.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/911bd777-8d4a-5d03-9a5b-e7f7b1e1e7ff.jpg', 84513, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000017');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/751151ee-2861-52ed-837b-c41fc7af06c9.jpg', `name` = '赣南脐橙 5个装 约1kg.jpg', `file_size` = 79255, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000018' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000018', '1', '-1', '赣南脐橙 5个装 约1kg.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/751151ee-2861-52ed-837b-c41fc7af06c9.jpg', 79255, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000018');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/30c310d8-3773-57ba-97e9-eac41e98e23b.jpg', `name` = '福建琯溪蜜柚 1个 约1kg.jpg', `file_size` = 43455, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000019' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000019', '1', '-1', '福建琯溪蜜柚 1个 约1kg.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/30c310d8-3773-57ba-97e9-eac41e98e23b.jpg', 43455, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000019');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/bd8dc217-50a4-5047-a319-482da462045a.jpg', `name` = '海南高山香蕉 约1kg-把.jpg', `file_size` = 16158, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000020' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000020', '1', '-1', '海南高山香蕉 约1kg-把.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/bd8dc217-50a4-5047-a319-482da462045a.jpg', 16158, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000020');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/fb63dbf5-f4c8-5882-a4ae-cbf76421a7fd.jpg', `name` = '海南贵妃芒 3个装 约500g.jpg', `file_size` = 110384, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000021' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000021', '1', '-1', '海南贵妃芒 3个装 约500g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/fb63dbf5-f4c8-5882-a4ae-cbf76421a7fd.jpg', 110384, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000021');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/66ec184a-1eee-5b49-85bb-2198f26d8252.jpg', `name` = '泰国椰青 1个装 约1kg.jpg', `file_size` = 179196, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000022' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000022', '1', '-1', '泰国椰青 1个装 约1kg.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/66ec184a-1eee-5b49-85bb-2198f26d8252.jpg', 179196, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000022');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/f1120f6f-ee96-5f3d-97d3-800c5b6b8d62.jpg', `name` = '麒麟西瓜 1个 约2kg.jpg', `file_size` = 153517, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000023' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000023', '1', '-1', '麒麟西瓜 1个 约2kg.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/f1120f6f-ee96-5f3d-97d3-800c5b6b8d62.jpg', 153517, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000023');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/bc58e7ba-12d6-5cb6-b312-ffb6ac1b928d.jpg', `name` = '西州蜜哈密瓜 1个 约1.5kg.jpg', `file_size` = 10510, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000024' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000024', '1', '-1', '西州蜜哈密瓜 1个 约1.5kg.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/bc58e7ba-12d6-5cb6-b312-ffb6ac1b928d.jpg', 10510, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000024');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/871161fb-ff69-5661-8cbe-1e84345cc58e.jpg', `name` = '云南阳光玫瑰葡萄 500g-串.jpg', `file_size` = 73428, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000025' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000025', '1', '-1', '云南阳光玫瑰葡萄 500g-串.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/871161fb-ff69-5661-8cbe-1e84345cc58e.jpg', 73428, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000025');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/32ce4e22-347c-5fb4-9cf6-df94de028362.jpg', `name` = '丹东99红颜草莓 250g-盒.jpg', `file_size` = 77495, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000026' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000026', '1', '-1', '丹东99红颜草莓 250g-盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/32ce4e22-347c-5fb4-9cf6-df94de028362.jpg', 77495, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000026');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/1060e150-4d0d-5e07-a14f-76f3dce01ef5.jpg', `name` = '猪五花肉片 约300g-份.jpg', `file_size` = 89476, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000027' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000027', '1', '-1', '猪五花肉片 约300g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/1060e150-4d0d-5e07-a14f-76f3dce01ef5.jpg', 89476, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000027');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/b2879c2e-3b6d-5c01-8d6f-6d0ad4b2333e.jpg', `name` = '猪里脊肉 约300g-份.jpg', `file_size` = 87397, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000028' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000028', '1', '-1', '猪里脊肉 约300g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/b2879c2e-3b6d-5c01-8d6f-6d0ad4b2333e.jpg', 87397, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000028');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/3de6b614-e23e-5171-8022-687092f3f29d.jpg', `name` = '原切谷饲牛腩块 约500g-盒.jpg', `file_size` = 275211, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000029' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000029', '1', '-1', '原切谷饲牛腩块 约500g-盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/3de6b614-e23e-5171-8022-687092f3f29d.jpg', 275211, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000029');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/46805a9b-a84f-5aa8-a0c4-351fa4357bac.jpg', `name` = '澳洲谷饲原切牛排 2片装 约300g.jpg', `file_size` = 443783, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000030' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000030', '1', '-1', '澳洲谷饲原切牛排 2片装 约300g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/46805a9b-a84f-5aa8-a0c4-351fa4357bac.jpg', 443783, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000030');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/26e8ab5d-67fd-5438-82c7-0074ea3b4cbb.jpg', `name` = '内蒙古羔羊肉卷 约300g-份.jpg', `file_size` = 97609, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000031' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000031', '1', '-1', '内蒙古羔羊肉卷 约300g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/26e8ab5d-67fd-5438-82c7-0074ea3b4cbb.jpg', 97609, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000031');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/c2ae8584-af58-59bf-8123-0e72a7cc899e.jpg', `name` = '内蒙古羔羊排 约600g-份.jpg', `file_size` = 62633, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000032' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000032', '1', '-1', '内蒙古羔羊排 约600g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/c2ae8584-af58-59bf-8123-0e72a7cc899e.jpg', 62633, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000032');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/aab20876-68c4-594e-aa88-0e1dc67b9c59.jpg', `name` = '温氏三黄鸡 1只 约1kg.jpg', `file_size` = 42343, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000033' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000033', '1', '-1', '温氏三黄鸡 1只 约1kg.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/aab20876-68c4-594e-aa88-0e1dc67b9c59.jpg', 42343, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000033');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/aa338aaf-580a-5a96-b192-bbcc790befb3.jpg', `name` = '单冻鸡胸肉 约500g-袋.jpg', `file_size` = 56333, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000034' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000034', '1', '-1', '单冻鸡胸肉 约500g-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/aa338aaf-580a-5a96-b192-bbcc790befb3.jpg', 56333, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000034');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/2d751244-d448-51e1-a348-60a48e06e3d5.jpg', `name` = '正大鲜鸡蛋 10枚装 约500g.jpg', `file_size` = 58831, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000035' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000035', '1', '-1', '正大鲜鸡蛋 10枚装 约500g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/2d751244-d448-51e1-a348-60a48e06e3d5.jpg', 58831, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000035');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/d9d7844b-7a20-5b6c-86be-0a49065cb296.jpg', `name` = '红泥咸鸭蛋 6枚装 约360g.jpg', `file_size` = 251933, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000036' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000036', '1', '-1', '红泥咸鸭蛋 6枚装 约360g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/d9d7844b-7a20-5b6c-86be-0a49065cb296.jpg', 251933, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000036');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/38199a9b-2470-5865-ada7-db259f7e408a.jpg', `name` = '鲜活河虾 约250g-份.jpg', `file_size` = 92605, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000037' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000037', '1', '-1', '鲜活河虾 约250g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/38199a9b-2470-5865-ada7-db259f7e408a.jpg', 92605, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000037');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/b83ab7f9-4f61-5ec9-bffd-ad0e3bd4b61d.jpg', `name` = '鲜活肉蟹 1只 约400g.jpg', `file_size` = 257215, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000038' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000038', '1', '-1', '鲜活肉蟹 1只 约400g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/b83ab7f9-4f61-5ec9-bffd-ad0e3bd4b61d.jpg', 257215, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000038');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/eb22e3f1-6e4e-56fc-b7cf-f8c3728d3dff.jpg', `name` = '冰鲜三文鱼刺身段 约200g-盒.jpg', `file_size` = 111717, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000039' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000039', '1', '-1', '冰鲜三文鱼刺身段 约200g-盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/eb22e3f1-6e4e-56fc-b7cf-f8c3728d3dff.jpg', 111717, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000039');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/5e949170-6408-5936-a2d0-abcfba1f403b.jpg', `name` = '冰鲜大黄鱼 1条 约500g.jpg', `file_size` = 205823, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000040' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000040', '1', '-1', '冰鲜大黄鱼 1条 约500g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/5e949170-6408-5936-a2d0-abcfba1f403b.jpg', 205823, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000040');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/66602ae6-c0fd-56e3-ad20-8337595e7f36.jpg', `name` = '冷冻白虾仁 约250g-袋.jpg', `file_size` = 60853, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000041' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000041', '1', '-1', '冷冻白虾仁 约250g-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/66602ae6-c0fd-56e3-ad20-8337595e7f36.jpg', 60853, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000041');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/f581e529-0bcc-5acd-890d-13babe4b13af.jpg', `name` = '鲜活花蛤 约500g-份.jpg', `file_size` = 110441, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000042' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000042', '1', '-1', '鲜活花蛤 约500g-份.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/f581e529-0bcc-5acd-890d-13babe4b13af.jpg', 110441, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000042');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/0452355d-193b-5fe1-9b98-333f2e1455e4.jpg', `name` = '阿根廷红虾 2kg-盒.jpg', `file_size` = 162594, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000043' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000043', '1', '-1', '阿根廷红虾 2kg-盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/0452355d-193b-5fe1-9b98-333f2e1455e4.jpg', 162594, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000043');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/06eccc45-5f57-5d6d-89c1-f2fc3e465c3b.jpg', `name` = '越南巴沙鱼片 约300g-袋.jpg', `file_size` = 112594, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000044' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000044', '1', '-1', '越南巴沙鱼片 约300g-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/06eccc45-5f57-5d6d-89c1-f2fc3e465c3b.jpg', 112594, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000044');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/308f143f-b208-54ea-887e-fbf4760863bc.jpg', `name` = '悦航优鲜 全脂鲜牛奶 950ml.jpg', `file_size` = 143274, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000045' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000045', '1', '-1', '悦航优鲜 全脂鲜牛奶 950ml.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/308f143f-b208-54ea-887e-fbf4760863bc.jpg', 143274, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000045');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/18394667-2bb3-5601-a4fa-3c70ec34112e.jpg', `name` = '悦航优鲜 脱脂鲜牛奶 950ml.jpg', `file_size` = 26325, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000046' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000046', '1', '-1', '悦航优鲜 脱脂鲜牛奶 950ml.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/18394667-2bb3-5601-a4fa-3c70ec34112e.jpg', 26325, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000046');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/d361d47d-c4d7-5cc3-acf2-567242d2d154.jpg', `name` = '简爱 0添加原味酸奶 135g×3杯.jpg', `file_size` = 35721, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000047' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000047', '1', '-1', '简爱 0添加原味酸奶 135g×3杯.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/d361d47d-c4d7-5cc3-acf2-567242d2d154.jpg', 35721, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000047');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/4121b190-9fe2-54b0-99f7-d44dd7cf3ba2.jpg', `name` = '蒙牛冠益乳 草莓味酸奶 250g×3杯.jpg', `file_size` = 225150, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000048' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000048', '1', '-1', '蒙牛冠益乳 草莓味酸奶 250g×3杯.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/4121b190-9fe2-54b0-99f7-d44dd7cf3ba2.jpg', 225150, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000048');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/b07be96d-a998-51ea-b7af-9ab2f699d55c.jpg', `name` = '伊利金典 纯牛奶 250ml×10盒.jpg', `file_size` = 59733, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000049' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000049', '1', '-1', '伊利金典 纯牛奶 250ml×10盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/b07be96d-a998-51ea-b7af-9ab2f699d55c.jpg', 59733, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000049');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/ce669240-4df4-54b4-9162-4e482cdc8048.jpg', `name` = '特仑苏 纯牛奶 250ml×10盒.jpg', `file_size` = 103001, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000050' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000050', '1', '-1', '特仑苏 纯牛奶 250ml×10盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/ce669240-4df4-54b4-9162-4e482cdc8048.jpg', 103001, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000050');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/8d1bd7a8-df8b-5daf-ba99-05b71b043e00.jpg', `name` = '悦航工坊 爆浆巧克力麻薯 4个装.jpg', `file_size` = 98552, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000051' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000051', '1', '-1', '悦航工坊 爆浆巧克力麻薯 4个装.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/8d1bd7a8-df8b-5daf-ba99-05b71b043e00.jpg', 98552, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000051');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/ebef5a7c-1d06-5264-a8c5-93cdc4637faa.jpg', `name` = '悦航工坊 全麦贝果 3个装.jpg', `file_size` = 590456, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000052' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000052', '1', '-1', '悦航工坊 全麦贝果 3个装.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/ebef5a7c-1d06-5264-a8c5-93cdc4637faa.jpg', 590456, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000052');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/c175db19-2690-5168-86b0-edb0951321b6.jpg', `name` = '悦航大厨 鱼香肉丝快手菜 约350g.jpg', `file_size` = 190012, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000053' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000053', '1', '-1', '悦航大厨 鱼香肉丝快手菜 约350g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/c175db19-2690-5168-86b0-edb0951321b6.jpg', 190012, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000053');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/1486989a-f4c9-5cdb-8c6f-c70b778a70fd.jpg', `name` = '悦航大厨 宫保鸡丁快手菜 约350g.jpg', `file_size` = 92086, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000054' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000054', '1', '-1', '悦航大厨 宫保鸡丁快手菜 约350g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/1486989a-f4c9-5cdb-8c6f-c70b778a70fd.jpg', 92086, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000054');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/116f6ff3-5d79-5948-9356-c244f0a87dc9.jpg', `name` = '悦航大厨 黑椒牛柳意面 约320g.jpg', `file_size` = 37404, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000055' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000055', '1', '-1', '悦航大厨 黑椒牛柳意面 约320g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/116f6ff3-5d79-5948-9356-c244f0a87dc9.jpg', 37404, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000055');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/e8d7a4f0-27ee-5787-928f-4748bff80a9f.jpg', `name` = '悦航大厨 广式豉汁排骨 约400g.jpg', `file_size` = 57397, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000056' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000056', '1', '-1', '悦航大厨 广式豉汁排骨 约400g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/e8d7a4f0-27ee-5787-928f-4748bff80a9f.jpg', 57397, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000056');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/621f931e-362f-523d-9e38-a990a7d2e21a.jpg', `name` = '悦航工坊 五香酱牛肉 约200g-盒.jpg', `file_size` = 125496, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000057' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000057', '1', '-1', '悦航工坊 五香酱牛肉 约200g-盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/621f931e-362f-523d-9e38-a990a7d2e21a.jpg', 125496, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000057');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/8eba70da-8546-54ed-ba0f-223e88258cbe.jpg', `name` = '悦航工坊 盐水鸭 半只 约600g.jpg', `file_size` = 19515, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000058' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000058', '1', '-1', '悦航工坊 盐水鸭 半只 约600g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/8eba70da-8546-54ed-ba0f-223e88258cbe.jpg', 19515, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000058');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/7865b31b-6842-553f-ac29-a6eac07ac278.jpg', `name` = '悦航工坊 鲜虾云吞 20只装 约400g.jpg', `file_size` = 335031, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000059' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000059', '1', '-1', '悦航工坊 鲜虾云吞 20只装 约400g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/7865b31b-6842-553f-ac29-a6eac07ac278.jpg', 335031, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000059');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/75a1b2ae-da71-544c-89c2-37728c585fa9.jpg', `name` = '悦航工坊 老面馒头 6个装 约480g.jpg', `file_size` = 458156, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000060' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000060', '1', '-1', '悦航工坊 老面馒头 6个装 约480g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/75a1b2ae-da71-544c-89c2-37728c585fa9.jpg', 458156, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000060');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/ae471a67-61d0-5e69-98c4-9277580a34cb.jpg', `name` = '金龙鱼 东北大米 5kg-袋.jpg', `file_size` = 197985, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000061' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000061', '1', '-1', '金龙鱼 东北大米 5kg-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/ae471a67-61d0-5e69-98c4-9277580a34cb.jpg', 197985, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000061');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/b4147e7c-cbeb-5603-94e3-cf4a93471720.jpg', `name` = '香满园 麦芯小麦粉 2.5kg-袋.jpg', `file_size` = 12965, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000062' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000062', '1', '-1', '香满园 麦芯小麦粉 2.5kg-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/b4147e7c-cbeb-5603-94e3-cf4a93471720.jpg', 12965, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000062');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/4de1ff54-a5cb-596c-825b-de40d97c133b.jpg', `name` = '悦航优选 有机黄小米 1kg-袋.jpg', `file_size` = 181944, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000063' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000063', '1', '-1', '悦航优选 有机黄小米 1kg-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/4de1ff54-a5cb-596c-825b-de40d97c133b.jpg', 181944, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000063');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/ddef0e70-5c32-5179-a4f2-e8eec209124d.jpg', `name` = '悦航优选 红芸豆 500g-袋.jpg', `file_size` = 27787, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000064' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000064', '1', '-1', '悦航优选 红芸豆 500g-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/ddef0e70-5c32-5179-a4f2-e8eec209124d.jpg', 27787, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000064');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/b1782e99-881f-5a82-b94f-a48d33a7991f.jpg', `name` = '金龙鱼 压榨一级花生油 4L-桶.jpg', `file_size` = 57686, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000065' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000065', '1', '-1', '金龙鱼 压榨一级花生油 4L-桶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/b1782e99-881f-5a82-b94f-a48d33a7991f.jpg', 57686, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000065');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/2f09f7ec-77ca-50b7-bf5b-9285f8b85135.jpg', `name` = '鲁花 5S压榨一级花生油 5L-桶.jpg', `file_size` = 41987, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000066' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000066', '1', '-1', '鲁花 5S压榨一级花生油 5L-桶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/2f09f7ec-77ca-50b7-bf5b-9285f8b85135.jpg', 41987, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000066');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/15bad279-be8b-5696-9852-73c734bb2041.jpg', `name` = '海天 金标生抽 1.9L-瓶.jpg', `file_size` = 34762, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000067' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000067', '1', '-1', '海天 金标生抽 1.9L-瓶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/15bad279-be8b-5696-9852-73c734bb2041.jpg', 34762, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000067');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/2fa2df12-c8b7-5cff-a979-89fd8ae2c4ae.jpg', `name` = '太太乐 三鲜鸡精 400g-罐.jpg', `file_size` = 61412, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000068' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000068', '1', '-1', '太太乐 三鲜鸡精 400g-罐.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/2fa2df12-c8b7-5cff-a979-89fd8ae2c4ae.jpg', 61412, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000068');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/fbaed193-bc98-58a8-b247-29962e7a152c.jpg', `name` = '洽洽 每日坚果 30日装 750g.jpg', `file_size` = 455689, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000069' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000069', '1', '-1', '洽洽 每日坚果 30日装 750g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/fbaed193-bc98-58a8-b247-29962e7a152c.jpg', 455689, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000069');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/ba4af140-30a4-5877-be2d-51dbf25c1fc1.jpg', `name` = '三只松鼠 碧根果 500g-袋.jpg', `file_size` = 143664, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000070' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000070', '1', '-1', '三只松鼠 碧根果 500g-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/ba4af140-30a4-5877-be2d-51dbf25c1fc1.jpg', 143664, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000070');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/39556ada-5532-56e0-9a5e-0735e9038534.jpg', `name` = '乐事 原味薯片 104g×3连包.jpg', `file_size` = 55032, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000071' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000071', '1', '-1', '乐事 原味薯片 104g×3连包.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/39556ada-5532-56e0-9a5e-0735e9038534.jpg', 55032, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000071');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/520a7b9a-75be-54fc-8b90-b9fce56a2efc.jpg', `name` = '好丽友 薯愿原味薯片 104g.jpg', `file_size` = 92819, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000072' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000072', '1', '-1', '好丽友 薯愿原味薯片 104g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/520a7b9a-75be-54fc-8b90-b9fce56a2efc.jpg', 92819, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000072');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/be924432-060e-5c71-8dfb-ff34299449ae.jpg', `name` = '奥利奥 原味夹心饼干 388g-盒.jpg', `file_size` = 83691, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000073' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000073', '1', '-1', '奥利奥 原味夹心饼干 388g-盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/be924432-060e-5c71-8dfb-ff34299449ae.jpg', 83691, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000073');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/2bd66d4c-bf73-5efd-bf6e-6ef49a7e609f.jpg', `name` = '盼盼 梅尼耶干蛋糕 1kg-箱.jpg', `file_size` = 807426, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000074' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000074', '1', '-1', '盼盼 梅尼耶干蛋糕 1kg-箱.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/2bd66d4c-bf73-5efd-bf6e-6ef49a7e609f.jpg', 807426, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000074');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/3bf9b1d6-0437-5072-9513-55eb8e307c55.jpg', `name` = '德芙 丝滑牛奶巧克力 252g-盒.jpg', `file_size` = 121098, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000075' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000075', '1', '-1', '德芙 丝滑牛奶巧克力 252g-盒.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/3bf9b1d6-0437-5072-9513-55eb8e307c55.jpg', 121098, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000075');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/bd361f5a-89ce-548d-a16f-0b267dabfefb.jpg', `name` = '徐福记 酥心糖 500g-袋.jpg', `file_size` = 378759, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000076' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000076', '1', '-1', '徐福记 酥心糖 500g-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/bd361f5a-89ce-548d-a16f-0b267dabfefb.jpg', 378759, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000076');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/d8975494-ae89-5b29-a742-611bdbfa82a9.jpg', `name` = '百草味 芒果干 300g-袋.jpg', `file_size` = 62688, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000077' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000077', '1', '-1', '百草味 芒果干 300g-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/d8975494-ae89-5b29-a742-611bdbfa82a9.jpg', 62688, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000077');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/af9089c2-c849-518e-a095-9a871bff04b9.jpg', `name` = '悦航优选 新疆无核葡萄干 500g-袋.jpg', `file_size` = 2723528, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000078' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000078', '1', '-1', '悦航优选 新疆无核葡萄干 500g-袋.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/af9089c2-c849-518e-a095-9a871bff04b9.jpg', 2723528, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000078');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/88cf76b8-46fd-5c10-aea9-0ca6e9138b8d.jpg', `name` = '农夫山泉 饮用天然水 550ml×24瓶.jpg', `file_size` = 18570, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000079' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000079', '1', '-1', '农夫山泉 饮用天然水 550ml×24瓶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/88cf76b8-46fd-5c10-aea9-0ca6e9138b8d.jpg', 18570, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000079');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/8fc7cdcf-35ed-5be0-8df3-b43331fdd619.jpg', `name` = '农夫山泉 饮用天然水 4L×4桶.jpg', `file_size` = 72663, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000080' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000080', '1', '-1', '农夫山泉 饮用天然水 4L×4桶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/8fc7cdcf-35ed-5be0-8df3-b43331fdd619.jpg', 72663, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000080');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/b21fb9b8-cf3b-56ed-902d-fdc80cf46ccc.jpg', `name` = '可口可乐 500ml×12瓶.jpg', `file_size` = 226484, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000081' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000081', '1', '-1', '可口可乐 500ml×12瓶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/b21fb9b8-cf3b-56ed-902d-fdc80cf46ccc.jpg', 226484, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000081');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/eb16f801-4c57-5c21-b3eb-0f78f8851a6d.jpg', `name` = '元气森林 白桃味气泡水 480ml×6瓶.jpg', `file_size` = 12740, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000082' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000082', '1', '-1', '元气森林 白桃味气泡水 480ml×6瓶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/eb16f801-4c57-5c21-b3eb-0f78f8851a6d.jpg', 12740, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000082');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/8d7c1946-cd15-5c56-bb8e-394eb05a2e42.jpg', `name` = '农夫山泉 NFC橙汁 950ml-瓶.jpg', `file_size` = 96018, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000083' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000083', '1', '-1', '农夫山泉 NFC橙汁 950ml-瓶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/8d7c1946-cd15-5c56-bb8e-394eb05a2e42.jpg', 96018, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000083');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/454da1ec-e6be-563b-bd5f-b67329dc0566.jpg', `name` = '三得利 无糖乌龙茶 500ml×15瓶.jpg', `file_size` = 86368, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000084' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000084', '1', '-1', '三得利 无糖乌龙茶 500ml×15瓶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/454da1ec-e6be-563b-bd5f-b67329dc0566.jpg', 86368, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000084');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/fcae3db3-5302-50f5-ab43-6cd50b3ef07a.jpg', `name` = '青岛啤酒 经典10度 500ml×12听.jpg', `file_size` = 82956, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000085' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000085', '1', '-1', '青岛啤酒 经典10度 500ml×12听.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/fcae3db3-5302-50f5-ab43-6cd50b3ef07a.jpg', 82956, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000085');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/514878f9-23b8-5a65-9a0a-0638f85e2496.jpg', `name` = '百威小麦醇正拉罐 500ml×18听.jpg', `file_size` = 87719, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000086' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000086', '1', '-1', '百威小麦醇正拉罐 500ml×18听.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/514878f9-23b8-5a65-9a0a-0638f85e2496.jpg', 87719, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000086');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/44423e72-9fcc-53d4-8c6b-4e01bec8519b.jpg', `name` = '牛栏山 陈酿白酒 42度 500ml.jpg', `file_size` = 53761, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000087' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000087', '1', '-1', '牛栏山 陈酿白酒 42度 500ml.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/44423e72-9fcc-53d4-8c6b-4e01bec8519b.jpg', 53761, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000087');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/fa3ee693-7d85-5326-acde-a06c56121183.jpg', `name` = '张裕 解百纳干红葡萄酒 750ml.jpg', `file_size` = 134404, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000088' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000088', '1', '-1', '张裕 解百纳干红葡萄酒 750ml.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/fa3ee693-7d85-5326-acde-a06c56121183.jpg', 134404, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000088');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/bd7cbaa4-9ab9-5288-8f83-99df5267e334.jpg', `name` = '海飞丝 去屑洗发露 750ml.jpg', `file_size` = 47683, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000089' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000089', '1', '-1', '海飞丝 去屑洗发露 750ml.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/bd7cbaa4-9ab9-5288-8f83-99df5267e334.jpg', 47683, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000089');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/c845c250-5ef6-5332-bccc-d2b0917c8e07.jpg', `name` = '舒肤佳 沐浴露 720ml.jpg', `file_size` = 131369, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000090' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000090', '1', '-1', '舒肤佳 沐浴露 720ml.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/c845c250-5ef6-5332-bccc-d2b0917c8e07.jpg', 131369, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000090');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/4b7a5b3d-c338-5a4c-a159-cce84b78da66.jpg', `name` = '云南白药 牙膏 210g.jpg', `file_size` = 47011, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000091' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000091', '1', '-1', '云南白药 牙膏 210g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/4b7a5b3d-c338-5a4c-a159-cce84b78da66.jpg', 47011, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000091');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/a525a124-ba29-5043-baf5-837287f9d1d8.jpg', `name` = '高露洁 光感白牙膏 180g.jpg', `file_size` = 17201, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000092' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000092', '1', '-1', '高露洁 光感白牙膏 180g.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/a525a124-ba29-5043-baf5-837287f9d1d8.jpg', 17201, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000092');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/38f65e00-4ec1-52f0-9963-f11bebcda5a6.jpg', `name` = '维达 棉韧抽纸 3层130抽×24包.jpg', `file_size` = 94275, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000093' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000093', '1', '-1', '维达 棉韧抽纸 3层130抽×24包.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/38f65e00-4ec1-52f0-9963-f11bebcda5a6.jpg', 94275, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000093');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/53164646-f933-53bf-b0f1-5d2fb7281e7d.jpg', `name` = '心相印 厨房湿巾 40抽×3包.jpg', `file_size` = 44084, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000094' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000094', '1', '-1', '心相印 厨房湿巾 40抽×3包.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/53164646-f933-53bf-b0f1-5d2fb7281e7d.jpg', 44084, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000094');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/7b466830-62a8-5f70-a423-06a5f6ce4973.jpg', `name` = '蓝月亮 洗衣液 3kg-瓶.jpg', `file_size` = 139025, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000095' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000095', '1', '-1', '蓝月亮 洗衣液 3kg-瓶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/7b466830-62a8-5f70-a423-06a5f6ce4973.jpg', 139025, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000095');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/13d20d35-c04d-54c5-a472-fbe33df34105.jpg', `name` = '立白 洗洁精 1.5kg-瓶.jpg', `file_size` = 27278, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000096' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000096', '1', '-1', '立白 洗洁精 1.5kg-瓶.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/13d20d35-c04d-54c5-a472-fbe33df34105.jpg', 27278, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000096');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/a51e9872-19cd-58d6-b086-c38c9cd0ecbf.jpg', `name` = '美丽雅 点断式保鲜袋 200只-卷.jpg', `file_size` = 33103, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000097' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000097', '1', '-1', '美丽雅 点断式保鲜袋 200只-卷.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/a51e9872-19cd-58d6-b086-c38c9cd0ecbf.jpg', 33103, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000097');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/1f0420fd-47f8-5c55-81b8-c3a27c648e69.jpg', `name` = '悦航优选 硅胶铲勺三件套.jpg', `file_size` = 25268, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000098' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000098', '1', '-1', '悦航优选 硅胶铲勺三件套.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/1f0420fd-47f8-5c55-81b8-c3a27c648e69.jpg', 25268, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000098');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/442d5833-77c8-5e82-9ad2-0c9dc1cc4cef.jpg', `name` = '悦航优选 棉麻收纳袋 三件套.jpg', `file_size` = 177644, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000099' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000099', '1', '-1', '悦航优选 棉麻收纳袋 三件套.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/442d5833-77c8-5e82-9ad2-0c9dc1cc4cef.jpg', 177644, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000099');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/4e892efd-ccc2-5570-8f58-9cd1a6db013c.jpg', `name` = '悦航优选 折叠收纳箱 30L.jpg', `file_size` = 15979, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000100' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000100', '1', '-1', '悦航优选 折叠收纳箱 30L.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/4e892efd-ccc2-5570-8f58-9cd1a6db013c.jpg', 15979, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000100');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/a53e5dd5-3077-5699-91f6-948f11b3caee.jpg', `name` = '妙洁 一次性纸杯 250ml×50只.jpg', `file_size` = 109404, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000101' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000101', '1', '-1', '妙洁 一次性纸杯 250ml×50只.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/a53e5dd5-3077-5699-91f6-948f11b3caee.jpg', 109404, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000101');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/2c9a9652-c7a7-51e0-85ad-5051ed5d0cf9.jpg', `name` = '悦航优选 一次性加厚台布 10片.jpg', `file_size` = 141393, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000102' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000102', '1', '-1', '悦航优选 一次性加厚台布 10片.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/2c9a9652-c7a7-51e0-85ad-5051ed5d0cf9.jpg', 141393, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000102');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/2366f862-8b39-5b7c-ae0d-c81d6e31ecd2.jpg', `name` = '云南直发 玫瑰混搭花束 10枝.jpg', `file_size` = 63295, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000103' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000103', '1', '-1', '云南直发 玫瑰混搭花束 10枝.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/2366f862-8b39-5b7c-ae0d-c81d6e31ecd2.jpg', 63295, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000103');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/f40ba0a6-3f02-5a91-8f2b-75d8b5b98567.jpg', `name` = '云南直发 向日葵鲜切花 5枝.jpg', `file_size` = 157505, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000104' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000104', '1', '-1', '云南直发 向日葵鲜切花 5枝.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/f40ba0a6-3f02-5a91-8f2b-75d8b5b98567.jpg', 157505, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000104');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/1ded5713-3dc3-5082-89fe-5cf67c0a9d7e.jpg', `name` = '绿萝盆栽 带盆栽好 苗高约15cm.jpg', `file_size` = 378418, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000105' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000105', '1', '-1', '绿萝盆栽 带盆栽好 苗高约15cm.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/1ded5713-3dc3-5082-89fe-5cf67c0a9d7e.jpg', 378418, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000105');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/boot/file/local/1590229800633634816/b29b4db5-08db-5eb7-82b3-a7e752107626.jpg', `name` = '多肉植物组合盆栽 3棵装.jpg', `file_size` = 209872, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9560000000000000106' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9560000000000000106', '1', '-1', '多肉植物组合盆栽 3棵装.jpg', 'http://localhost:9999/boot/file/local/1590229800633634816/b29b4db5-08db-5eb7-82b3-a7e752107626.jpg', 209872, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9560000000000000106');
