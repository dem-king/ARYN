-- 清理历史 MinIO 数据残留
-- 项目已切换本地存储，旧 MinIO 地址不可访问：
--   1) 头像/分类图/订单与购物车快照图置空；
--   2) 商品图字段置空；
--   3) 失效素材与页面设计历史快照逻辑删除并清空内容；
--   4) 活跃装修内容中的旧图片地址替换为透明占位图。

USE `aryn_boot`;

UPDATE `sys_user`
SET `avatar` = NULL
WHERE `avatar` LIKE '%minio%';

UPDATE `sys_material`
SET `url` = '', `object_key` = NULL, `del_flag` = '1'
WHERE `url` LIKE '%minio%' OR `object_key` LIKE '%minio%';

UPDATE `goods_category`
SET `category_pic` = ''
WHERE `category_pic` LIKE '%minio%';

UPDATE `goods_spu`
SET `spu_urls` = ''
WHERE `spu_urls` LIKE '%minio%';

UPDATE `goods_spu`
SET `spu_urls` = ''
WHERE `spu_urls` <> '' AND REPLACE(`spu_urls`, ',', '') = '';

UPDATE `order_item`
SET `pic_url` = ''
WHERE `pic_url` LIKE '%minio%';

UPDATE `shopping_cart`
SET `pic_url` = ''
WHERE `pic_url` LIKE '%minio%';

UPDATE `page_design`
SET `legacy_content_backup` = NULL
WHERE `legacy_content_backup` LIKE '%minio%';

UPDATE `page_design`
SET `page_content` = REGEXP_REPLACE(`page_content`, 'https?://minio\\.[^"]+', 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==')
WHERE `page_content` LIKE '%minio%';

UPDATE `page_design`
SET `page_content` = '{}'
WHERE `del_flag` = '1' AND JSON_VALID(`page_content`) = 0;

UPDATE `page_design_release`
SET `page_content` = '{}', `del_flag` = '1'
WHERE `page_content` LIKE '%minio%';

UPDATE `page_design_release`
SET `page_content` = '{}'
WHERE `del_flag` = '1' AND JSON_VALID(`page_content`) = 0;

UPDATE `page_design_version`
SET `page_content` = '{}', `del_flag` = '1'
WHERE `page_content` LIKE '%minio%';

UPDATE `page_design_version`
SET `page_content` = '{}'
WHERE `del_flag` = '1' AND JSON_VALID(`page_content`) = 0;

SET FOREIGN_KEY_CHECKS = 1;
