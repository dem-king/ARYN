-- 悦航购商超类目图片回填（Cloud 微服务模式）
-- 目标库：aryn_product（类目在 aryn_product，素材在 aryn_upms）
-- 内容：为 41grocery_catalog_seed.sql 的 12 个一级类目 + 51 个二级类目回填 category_pic，
--       并在 sys_material 素材库登记同一批图片，使后台「素材中心」可见可复用。
-- 图片实体：db/assets/grocery-category-images/（清单见该目录 manifest.tsv）
-- 部署方式：将 stored_file（uuid.jpg）放入文件存储根目录 /data/aryn/uploads/{TENANT}/；本脚本只写库。
-- 特性：可重复执行；仅按类目 ID 95x 精确匹配更新，不新增/删除类目，不触碰其它数据。
--
-- 执行：mysql -u root -p aryn_product < 71grocery_category_images.sql


USE `aryn_product`;
SET NAMES utf8mb4;

-- ---------- 1. 类目图回填（12 一级 + 51 二级） ----------
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/811e3097-3297-517c-8cd3-a68abb886b5d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000001' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/4da110f9-7661-50f0-af81-e9197545f933.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000002' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/44541e06-523e-585f-b2a7-88388cdc8c75.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000003' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/06303430-7745-5104-b9a5-072ae1042f3d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000004' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/63ab6b55-8bfb-56ab-b633-f49b722f3082.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000005' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/713e6001-1493-540e-9f36-bc061fd89330.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000006' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/7c5c8470-aef7-51df-a861-8630f7b73e5f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000007' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/32b003a3-ea83-5a53-a4e3-5e475c2c9c9a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000008' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/d912a375-2d39-5aaa-84aa-e0f87e2684bd.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000009' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/a2faf3a8-af8a-5d90-872a-275f531ef415.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000010' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/bbb62486-ff6e-52a3-8383-04436bd085e2.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000011' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/2d228933-e1b5-5195-8510-ccaed5322574.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9510000000000000012' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/35a3b0ea-e273-5059-92dd-f2554863f01d.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000001' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/a7ab94ed-385b-5e8d-8116-390f1efc2806.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000002' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/dd37469f-0b1a-526e-8c01-53a4776b00de.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000003' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/fff4b328-8eed-5977-bcfa-69ba7a084272.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000004' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/bcd09cee-31aa-5cdc-916f-b3f0c410b212.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000005' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/006b72af-28ce-5be6-a160-3871fb55f2cd.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000006' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/295b790e-23d9-570c-9f18-dd1f59881eff.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000007' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/4362c046-bdd9-55e4-bc4c-0dcdedb528f2.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000008' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/58f5eda7-8d78-57bc-a453-9cf53692d14b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000009' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/3e3bcea3-2902-5808-a96a-01f30536e801.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000010' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/5ef4e019-f092-5559-ab01-2d88b881b7b4.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000011' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/d5bd0671-f5c3-5a11-88c0-2e3e044c9345.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000012' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/c9a83de7-7a72-561b-b7e7-5cf4ec661a3a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000013' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/bd1022ec-bcb5-59cf-8ea6-71c76d1fe861.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000014' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/e3db51c3-dbdb-5dcd-8bac-9e227f39f06f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000015' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/e4178722-f6cb-5d03-8de4-00359e694da9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000016' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/32e91f4a-6540-5c6e-9ce7-9c1f301f5be2.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000017' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/d9be60c2-ba54-582f-a7b7-b85b67da957f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000018' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/ee23c1a0-e7f2-5050-b5da-81db671b84b7.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000019' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/a09eb476-d537-5cc5-86f0-44ff78999417.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000020' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/ca10ce2f-109e-5c4e-b393-7aeef00baa76.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000021' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/22cf2eaf-c908-5cc8-92aa-8369e0645e3b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000022' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/f1b645a9-1eef-5ea2-93c0-4630ba1cb661.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000023' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/1eaba45e-7c87-5975-a64d-6f45619df071.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000024' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/d80b85c1-4299-5021-981f-36336b8f9ccf.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000025' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/11e65f4d-a7b7-50d5-85c6-db88366f9aa8.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000026' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/b599aa65-a790-5f24-bc64-61baba22e931.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000027' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/bc1c972d-f06e-50aa-b4b5-994719bef3ed.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000028' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/456c36ae-3e5a-515f-a1be-02c5900794f0.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000029' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/1bb5fb9b-51bd-51b4-9557-2165a98c55dd.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000030' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/4358fecb-11ac-528f-8983-ead8e683bb54.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000031' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/fad75fac-20d5-55ad-84a7-9f2069da1669.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000032' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/63e5c8fb-bb4e-59ac-8045-11b93536641c.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000033' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/ac04a01e-381e-5e05-a088-e00efa7b72ff.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000034' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/c46fa196-4b6f-5dfa-862d-f3648dbc695b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000035' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/e47306e0-6797-50dc-8c48-9d2533e564c9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000036' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/c1c69397-9bc7-5b9b-b774-64f0878097e3.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000037' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/bad226fa-3411-5326-beb4-0171ed8ccf4a.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000038' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/8eca7eb8-77bf-527c-b7a9-117e54d60c62.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000039' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/44640e27-ac17-57fe-9e24-f38a3baf4442.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000040' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/74cd9798-26cb-56de-aec8-e98b09d98739.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000041' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/1d4c0431-0932-5330-ad96-1ebc8563a584.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000042' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/c5a20b45-7f88-5a0f-9f3e-1e07dc4d294f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000043' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/eaf108ef-726a-5103-9cbd-cb0507352473.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000044' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/7b8ac67a-6a1c-5db1-b403-fa5eae15b48b.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000045' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/34b916cd-e464-5c47-9771-af81ea987486.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000046' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/0a3c0999-fe11-5b33-a99f-d950f88318df.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000047' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/c95dda32-e0fe-5b86-83d0-b965e1a5f0b9.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000048' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/0afa3fd1-b950-5d45-90a9-f6cded92673f.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000049' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/148b5bcc-88db-552c-8b0e-cdf88e40cae0.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000050' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';
UPDATE `goods_category`
   SET `category_pic` = 'http://localhost:9999/upms/file/local/1590229800633634816/a91e308e-4178-5ea0-b1f9-a792975cffe7.jpg', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000051' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0';

-- ---------- 2. 素材库登记（等价于管理端上传接口的副作用） ----------
USE `aryn_upms`;

UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/811e3097-3297-517c-8cd3-a68abb886b5d.jpg', `name` = '蔬菜.jpg', `file_size` = 108122, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000001' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000001', '1', '-1', '蔬菜.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/811e3097-3297-517c-8cd3-a68abb886b5d.jpg', 108122, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000001');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/4da110f9-7661-50f0-af81-e9197545f933.jpg', `name` = '水果.jpg', `file_size` = 85402, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000002' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000002', '1', '-1', '水果.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/4da110f9-7661-50f0-af81-e9197545f933.jpg', 85402, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000002');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/44541e06-523e-585f-b2a7-88388cdc8c75.jpg', `name` = '肉禽蛋.jpg', `file_size` = 61010, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000003' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000003', '1', '-1', '肉禽蛋.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/44541e06-523e-585f-b2a7-88388cdc8c75.jpg', 61010, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000003');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/06303430-7745-5104-b9a5-072ae1042f3d.jpg', `name` = '海鲜水产.jpg', `file_size` = 1318901, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000004' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000004', '1', '-1', '海鲜水产.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/06303430-7745-5104-b9a5-072ae1042f3d.jpg', 1318901, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000004');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/63ab6b55-8bfb-56ab-b633-f49b722f3082.jpg', `name` = '乳品烘焙.jpg', `file_size` = 197882, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000005' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000005', '1', '-1', '乳品烘焙.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/63ab6b55-8bfb-56ab-b633-f49b722f3082.jpg', 197882, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000005');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/713e6001-1493-540e-9f36-bc061fd89330.jpg', `name` = '熟食预制菜.jpg', `file_size` = 94340, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000006' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000006', '1', '-1', '熟食预制菜.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/713e6001-1493-540e-9f36-bc061fd89330.jpg', 94340, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000006');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/7c5c8470-aef7-51df-a861-8630f7b73e5f.jpg', `name` = '米面粮油.jpg', `file_size` = 151685, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000007' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000007', '1', '-1', '米面粮油.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/7c5c8470-aef7-51df-a861-8630f7b73e5f.jpg', 151685, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000007');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/32b003a3-ea83-5a53-a4e3-5e475c2c9c9a.jpg', `name` = '休闲零食.jpg', `file_size` = 415543, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000008' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000008', '1', '-1', '休闲零食.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/32b003a3-ea83-5a53-a4e3-5e475c2c9c9a.jpg', 415543, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000008');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/d912a375-2d39-5aaa-84aa-e0f87e2684bd.jpg', `name` = '酒水饮料.jpg', `file_size` = 78497, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000009' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000009', '1', '-1', '酒水饮料.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/d912a375-2d39-5aaa-84aa-e0f87e2684bd.jpg', 78497, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000009');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/a2faf3a8-af8a-5d90-872a-275f531ef415.jpg', `name` = '个护清洁.jpg', `file_size` = 15297, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000010' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000010', '1', '-1', '个护清洁.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/a2faf3a8-af8a-5d90-872a-275f531ef415.jpg', 15297, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000010');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/bbb62486-ff6e-52a3-8383-04436bd085e2.jpg', `name` = '日用百货.jpg', `file_size` = 13289, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000011' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000011', '1', '-1', '日用百货.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/bbb62486-ff6e-52a3-8383-04436bd085e2.jpg', 13289, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000011');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/2d228933-e1b5-5195-8510-ccaed5322574.jpg', `name` = '鲜花绿植.jpg', `file_size` = 58430, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000012' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000012', '1', '-1', '鲜花绿植.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/2d228933-e1b5-5195-8510-ccaed5322574.jpg', 58430, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000012');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/35a3b0ea-e273-5059-92dd-f2554863f01d.jpg', `name` = '叶菜类.jpg', `file_size` = 66116, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000013' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000013', '1', '-1', '叶菜类.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/35a3b0ea-e273-5059-92dd-f2554863f01d.jpg', 66116, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000013');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/a7ab94ed-385b-5e8d-8116-390f1efc2806.jpg', `name` = '根茎类.jpg', `file_size` = 71912, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000014' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000014', '1', '-1', '根茎类.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/a7ab94ed-385b-5e8d-8116-390f1efc2806.jpg', 71912, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000014');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/dd37469f-0b1a-526e-8c01-53a4776b00de.jpg', `name` = '茄果瓜类.jpg', `file_size` = 22762, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000015' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000015', '1', '-1', '茄果瓜类.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/dd37469f-0b1a-526e-8c01-53a4776b00de.jpg', 22762, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000015');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/fff4b328-8eed-5977-bcfa-69ba7a084272.jpg', `name` = '葱蒜椒.jpg', `file_size` = 270534, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000016' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000016', '1', '-1', '葱蒜椒.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/fff4b328-8eed-5977-bcfa-69ba7a084272.jpg', 270534, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000016');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/bcd09cee-31aa-5cdc-916f-b3f0c410b212.jpg', `name` = '食用菌菇.jpg', `file_size` = 158251, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000017' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000017', '1', '-1', '食用菌菇.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/bcd09cee-31aa-5cdc-916f-b3f0c410b212.jpg', 158251, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000017');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/006b72af-28ce-5be6-a160-3871fb55f2cd.jpg', `name` = '豆制品.jpg', `file_size` = 72617, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000018' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000018', '1', '-1', '豆制品.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/006b72af-28ce-5be6-a160-3871fb55f2cd.jpg', 72617, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000018');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/295b790e-23d9-570c-9f18-dd1f59881eff.jpg', `name` = '苹果梨.jpg', `file_size` = 66393, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000019' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000019', '1', '-1', '苹果梨.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/295b790e-23d9-570c-9f18-dd1f59881eff.jpg', 66393, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000019');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/4362c046-bdd9-55e4-bc4c-0dcdedb528f2.jpg', `name` = '柑橘橙柚.jpg', `file_size` = 122430, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000020' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000020', '1', '-1', '柑橘橙柚.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/4362c046-bdd9-55e4-bc4c-0dcdedb528f2.jpg', 122430, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000020');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/58f5eda7-8d78-57bc-a453-9cf53692d14b.jpg', `name` = '热带水果.jpg', `file_size` = 52485, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000021' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000021', '1', '-1', '热带水果.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/58f5eda7-8d78-57bc-a453-9cf53692d14b.jpg', 52485, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000021');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/3e3bcea3-2902-5808-a96a-01f30536e801.jpg', `name` = '瓜类.jpg', `file_size` = 23593, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000022' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000022', '1', '-1', '瓜类.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/3e3bcea3-2902-5808-a96a-01f30536e801.jpg', 23593, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000022');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/5ef4e019-f092-5559-ab01-2d88b881b7b4.jpg', `name` = '浆果葡萄.jpg', `file_size` = 43078, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000023' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000023', '1', '-1', '浆果葡萄.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/5ef4e019-f092-5559-ab01-2d88b881b7b4.jpg', 43078, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000023');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/d5bd0671-f5c3-5a11-88c0-2e3e044c9345.jpg', `name` = '猪肉.jpg', `file_size` = 93801, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000024' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000024', '1', '-1', '猪肉.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/d5bd0671-f5c3-5a11-88c0-2e3e044c9345.jpg', 93801, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000024');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/c9a83de7-7a72-561b-b7e7-5cf4ec661a3a.jpg', `name` = '牛肉.jpg', `file_size` = 62002, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000025' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000025', '1', '-1', '牛肉.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/c9a83de7-7a72-561b-b7e7-5cf4ec661a3a.jpg', 62002, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000025');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/bd1022ec-bcb5-59cf-8ea6-71c76d1fe861.jpg', `name` = '羊肉.jpg', `file_size` = 38064, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000026' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000026', '1', '-1', '羊肉.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/bd1022ec-bcb5-59cf-8ea6-71c76d1fe861.jpg', 38064, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000026');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/e3db51c3-dbdb-5dcd-8bac-9e227f39f06f.jpg', `name` = '禽肉.jpg', `file_size` = 240980, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000027' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000027', '1', '-1', '禽肉.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/e3db51c3-dbdb-5dcd-8bac-9e227f39f06f.jpg', 240980, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000027');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/e4178722-f6cb-5d03-8de4-00359e694da9.jpg', `name` = '蛋类.jpg', `file_size` = 37164, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000028' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000028', '1', '-1', '蛋类.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/e4178722-f6cb-5d03-8de4-00359e694da9.jpg', 37164, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000028');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/32e91f4a-6540-5c6e-9ce7-9c1f301f5be2.jpg', `name` = '活鲜.jpg', `file_size` = 61034, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000029' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000029', '1', '-1', '活鲜.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/32e91f4a-6540-5c6e-9ce7-9c1f301f5be2.jpg', 61034, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000029');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/d9be60c2-ba54-582f-a7b7-b85b67da957f.jpg', `name` = '冰鲜鱼.jpg', `file_size` = 59572, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000030' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000030', '1', '-1', '冰鲜鱼.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/d9be60c2-ba54-582f-a7b7-b85b67da957f.jpg', 59572, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000030');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/ee23c1a0-e7f2-5050-b5da-81db671b84b7.jpg', `name` = '虾蟹贝.jpg', `file_size` = 180693, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000031' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000031', '1', '-1', '虾蟹贝.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/ee23c1a0-e7f2-5050-b5da-81db671b84b7.jpg', 180693, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000031');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/a09eb476-d537-5cc5-86f0-44ff78999417.jpg', `name` = '冷冻水产.jpg', `file_size` = 91070, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000032' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000032', '1', '-1', '冷冻水产.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/a09eb476-d537-5cc5-86f0-44ff78999417.jpg', 91070, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000032');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/ca10ce2f-109e-5c4e-b393-7aeef00baa76.jpg', `name` = '鲜奶.jpg', `file_size` = 13174, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000033' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000033', '1', '-1', '鲜奶.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/ca10ce2f-109e-5c4e-b393-7aeef00baa76.jpg', 13174, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000033');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/22cf2eaf-c908-5cc8-92aa-8369e0645e3b.jpg', `name` = '酸奶.jpg', `file_size` = 75813, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000034' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000034', '1', '-1', '酸奶.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/22cf2eaf-c908-5cc8-92aa-8369e0645e3b.jpg', 75813, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000034');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/f1b645a9-1eef-5ea2-93c0-4630ba1cb661.jpg', `name` = '常温乳品.jpg', `file_size` = 62088, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000035' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000035', '1', '-1', '常温乳品.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/f1b645a9-1eef-5ea2-93c0-4630ba1cb661.jpg', 62088, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000035');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/1eaba45e-7c87-5975-a64d-6f45619df071.jpg', `name` = '面包烘焙.jpg', `file_size` = 43156, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000036' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000036', '1', '-1', '面包烘焙.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/1eaba45e-7c87-5975-a64d-6f45619df071.jpg', 43156, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000036');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/d80b85c1-4299-5021-981f-36336b8f9ccf.jpg', `name` = '快手菜.jpg', `file_size` = 72454, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000037' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000037', '1', '-1', '快手菜.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/d80b85c1-4299-5021-981f-36336b8f9ccf.jpg', 72454, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000037');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/11e65f4d-a7b7-50d5-85c6-db88366f9aa8.jpg', `name` = '预制菜肴.jpg', `file_size` = 128234, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000038' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000038', '1', '-1', '预制菜肴.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/11e65f4d-a7b7-50d5-85c6-db88366f9aa8.jpg', 128234, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000038');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/b599aa65-a790-5f24-bc64-61baba22e931.jpg', `name` = '卤味熟食.jpg', `file_size` = 60590, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000039' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000039', '1', '-1', '卤味熟食.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/b599aa65-a790-5f24-bc64-61baba22e931.jpg', 60590, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000039');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/bc1c972d-f06e-50aa-b4b5-994719bef3ed.jpg', `name` = '面点主食.jpg', `file_size` = 14560, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000040' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000040', '1', '-1', '面点主食.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/bc1c972d-f06e-50aa-b4b5-994719bef3ed.jpg', 14560, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000040');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/456c36ae-3e5a-515f-a1be-02c5900794f0.jpg', `name` = '大米面粉.jpg', `file_size` = 16860, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000041' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000041', '1', '-1', '大米面粉.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/456c36ae-3e5a-515f-a1be-02c5900794f0.jpg', 16860, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000041');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/1bb5fb9b-51bd-51b4-9557-2165a98c55dd.jpg', `name` = '杂粮.jpg', `file_size` = 250540, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000042' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000042', '1', '-1', '杂粮.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/1bb5fb9b-51bd-51b4-9557-2165a98c55dd.jpg', 250540, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000042');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/4358fecb-11ac-528f-8983-ead8e683bb54.jpg', `name` = '食用油.jpg', `file_size` = 41847, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000043' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000043', '1', '-1', '食用油.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/4358fecb-11ac-528f-8983-ead8e683bb54.jpg', 41847, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000043');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/fad75fac-20d5-55ad-84a7-9f2069da1669.jpg', `name` = '调味品.jpg', `file_size` = 67807, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000044' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000044', '1', '-1', '调味品.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/fad75fac-20d5-55ad-84a7-9f2069da1669.jpg', 67807, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000044');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/63e5c8fb-bb4e-59ac-8045-11b93536641c.jpg', `name` = '坚果炒货.jpg', `file_size` = 113719, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000045' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000045', '1', '-1', '坚果炒货.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/63e5c8fb-bb4e-59ac-8045-11b93536641c.jpg', 113719, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000045');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/ac04a01e-381e-5e05-a088-e00efa7b72ff.jpg', `name` = '膨化食品.jpg', `file_size` = 39131, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000046' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000046', '1', '-1', '膨化食品.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/ac04a01e-381e-5e05-a088-e00efa7b72ff.jpg', 39131, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000046');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/c46fa196-4b6f-5dfa-862d-f3648dbc695b.jpg', `name` = '饼干糕点.jpg', `file_size` = 32738, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000047' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000047', '1', '-1', '饼干糕点.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/c46fa196-4b6f-5dfa-862d-f3648dbc695b.jpg', 32738, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000047');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/e47306e0-6797-50dc-8c48-9d2533e564c9.jpg', `name` = '糖果巧克力.jpg', `file_size` = 42749, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000048' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000048', '1', '-1', '糖果巧克力.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/e47306e0-6797-50dc-8c48-9d2533e564c9.jpg', 42749, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000048');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/c1c69397-9bc7-5b9b-b774-64f0878097e3.jpg', `name` = '果干蜜饯.jpg', `file_size` = 69572, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000049' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000049', '1', '-1', '果干蜜饯.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/c1c69397-9bc7-5b9b-b774-64f0878097e3.jpg', 69572, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000049');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/bad226fa-3411-5326-beb4-0171ed8ccf4a.jpg', `name` = '饮用水.jpg', `file_size` = 36679, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000050' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000050', '1', '-1', '饮用水.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/bad226fa-3411-5326-beb4-0171ed8ccf4a.jpg', 36679, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000050');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/8eca7eb8-77bf-527c-b7a9-117e54d60c62.jpg', `name` = '碳酸饮料.jpg', `file_size` = 69177, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000051' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000051', '1', '-1', '碳酸饮料.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/8eca7eb8-77bf-527c-b7a9-117e54d60c62.jpg', 69177, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000051');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/44640e27-ac17-57fe-9e24-f38a3baf4442.jpg', `name` = '果汁茶饮.jpg', `file_size` = 44734, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000052' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000052', '1', '-1', '果汁茶饮.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/44640e27-ac17-57fe-9e24-f38a3baf4442.jpg', 44734, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000052');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/74cd9798-26cb-56de-aec8-e98b09d98739.jpg', `name` = '啤酒.jpg', `file_size` = 24795, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000053' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000053', '1', '-1', '啤酒.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/74cd9798-26cb-56de-aec8-e98b09d98739.jpg', 24795, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000053');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/1d4c0431-0932-5330-ad96-1ebc8563a584.jpg', `name` = '白酒红酒.jpg', `file_size` = 35202, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000054' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000054', '1', '-1', '白酒红酒.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/1d4c0431-0932-5330-ad96-1ebc8563a584.jpg', 35202, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000054');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/c5a20b45-7f88-5a0f-9f3e-1e07dc4d294f.jpg', `name` = '洗发沐浴.jpg', `file_size` = 130171, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000055' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000055', '1', '-1', '洗发沐浴.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/c5a20b45-7f88-5a0f-9f3e-1e07dc4d294f.jpg', 130171, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000055');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/eaf108ef-726a-5103-9cbd-cb0507352473.jpg', `name` = '口腔护理.jpg', `file_size` = 65431, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000056' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000056', '1', '-1', '口腔护理.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/eaf108ef-726a-5103-9cbd-cb0507352473.jpg', 65431, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000056');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/7b8ac67a-6a1c-5db1-b403-fa5eae15b48b.jpg', `name` = '纸品湿巾.jpg', `file_size` = 120442, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000057' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000057', '1', '-1', '纸品湿巾.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/7b8ac67a-6a1c-5db1-b403-fa5eae15b48b.jpg', 120442, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000057');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/34b916cd-e464-5c47-9771-af81ea987486.jpg', `name` = '家庭清洁.jpg', `file_size` = 14723, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000058' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000058', '1', '-1', '家庭清洁.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/34b916cd-e464-5c47-9771-af81ea987486.jpg', 14723, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000058');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/0a3c0999-fe11-5b33-a99f-d950f88318df.jpg', `name` = '厨房用品.jpg', `file_size` = 879227, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000059' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000059', '1', '-1', '厨房用品.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/0a3c0999-fe11-5b33-a99f-d950f88318df.jpg', 879227, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000059');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/c95dda32-e0fe-5b86-83d0-b965e1a5f0b9.jpg', `name` = '家居收纳.jpg', `file_size` = 56117, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000060' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000060', '1', '-1', '家居收纳.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/c95dda32-e0fe-5b86-83d0-b965e1a5f0b9.jpg', 56117, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000060');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/0afa3fd1-b950-5d45-90a9-f6cded92673f.jpg', `name` = '一次性用品.jpg', `file_size` = 205982, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000061' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000061', '1', '-1', '一次性用品.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/0afa3fd1-b950-5d45-90a9-f6cded92673f.jpg', 205982, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000061');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/148b5bcc-88db-552c-8b0e-cdf88e40cae0.jpg', `name` = '鲜切花.jpg', `file_size` = 51891, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000062' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000062', '1', '-1', '鲜切花.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/148b5bcc-88db-552c-8b0e-cdf88e40cae0.jpg', 51891, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000062');
UPDATE `sys_material`
   SET `url` = 'http://localhost:9999/upms/file/local/1590229800633634816/a91e308e-4178-5ea0-b1f9-a792975cffe7.jpg', `name` = '绿植多肉.jpg', `file_size` = 378418, `del_flag` = '0', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9570000000000000063' AND `tenant_id` = '1590229800633634816';
INSERT INTO `sys_material`
  (`id`, `type`, `group_id`, `name`, `url`, `file_size`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9570000000000000063', '1', '-1', '绿植多肉.jpg', 'http://localhost:9999/upms/file/local/1590229800633634816/a91e308e-4178-5ea0-b1f9-a792975cffe7.jpg', 378418, '1590229800633634816', 'system', NOW(), '0'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_material` WHERE `id` = '9570000000000000063');
