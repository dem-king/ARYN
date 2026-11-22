-- 商详商品档案与结构化详情（Cloud 微服务模式）
-- 目标库：aryn_product
-- 特性：可重复执行；只新增 JSON 列，不改存量字段；示例数据只填 goods_profile 为空的目标商品。
-- 执行：mysql -u root -p aryn_product < 1017goods_profile_detail_sections_incremental.sql

USE `aryn_product`;
SET NAMES utf8mb4;

-- ===========================================================================
-- 1. 商品档案：配料 / 营养 / 过敏原 / 执行标准 / 认证
-- ===========================================================================
SET @goods_profile_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'goods_spu' AND column_name = 'goods_profile'),
  'SELECT 1',
  'ALTER TABLE goods_spu ADD COLUMN goods_profile JSON NULL COMMENT ''商品档案（配料/营养/标准）'''
);
PREPARE goods_profile_stmt FROM @goods_profile_ddl;
EXECUTE goods_profile_stmt;
DEALLOCATE PREPARE goods_profile_stmt;

-- ===========================================================================
-- 2. 结构化详情：标题 + 纯文本 + 可选配图，按数组顺序渲染
-- ===========================================================================
SET @detail_sections_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'goods_spu' AND column_name = 'detail_sections'),
  'SELECT 1',
  'ALTER TABLE goods_spu ADD COLUMN detail_sections JSON NULL COMMENT ''结构化详情段落（标题/正文/配图）'''
);
PREPARE detail_sections_stmt FROM @detail_sections_ddl;
EXECUTE detail_sections_stmt;
DEALLOCATE PREPARE detail_sections_stmt;

-- ===========================================================================
-- 3. 樱桃预览数据：只填空档案，不覆盖运营已维护的内容
-- ===========================================================================
UPDATE goods_spu
SET goods_profile = JSON_OBJECT(
      'ingredients', '樱桃',
      'nutrition', JSON_ARRAY(
        JSON_OBJECT('name', '能量', 'amount', '264', 'unit', 'kJ', 'nrv', '3%'),
        JSON_OBJECT('name', '蛋白质', 'amount', '1.1', 'unit', 'g', 'nrv', '2%'),
        JSON_OBJECT('name', '脂肪', 'amount', '0.2', 'unit', 'g', 'nrv', '0%'),
        JSON_OBJECT('name', '碳水化合物', 'amount', '16.0', 'unit', 'g', 'nrv', '5%'),
        JSON_OBJECT('name', '膳食纤维', 'amount', '2.1', 'unit', 'g', 'nrv', '8%'),
        JSON_OBJECT('name', '钠', 'amount', '0', 'unit', 'mg', 'nrv', '0%')
      ),
      'allergens', '无已知过敏原；对樱桃过敏者请慎食',
      'standard', '按产地准入与企业验收标准执行',
      'certifications', JSON_ARRAY('产地直采', '冷链直供')
    ),
    detail_sections = JSON_ARRAY(
      JSON_OBJECT(
        'title', '产地与采摘',
        'content', '烟台产区当季现摘，采摘后预冷分选，按果径分级装箱。',
        'imageUrl', 'https://economies-gps-ceremony-cities.trycloudflare.com/boot/file/local/1590229800633634816/d71571ab-bbdd-4588-895b-437090ccf079.jpg'
      ),
      JSON_OBJECT(
        'title', '储存与食用',
        'content', '收到后建议 0–4℃ 冷藏，食用前清洗；冷藏后风味更佳，建议尽快食用。',
        'imageUrl', ''
      ),
      JSON_OBJECT(
        'title', '船供采购场景',
        'content', '适合船员食堂、靠港补给和整箱备货；按箱规配货，可与其它生鲜合并装运。',
        'imageUrl', ''
      )
    )
WHERE id = '9740000000000000201'
  AND tenant_id = '1590229800633634816'
  AND del_flag = '0'
  AND (goods_profile IS NULL OR JSON_LENGTH(goods_profile) = 0);

-- 自检：应返回目标商品的两列 JSON
SELECT id, name, goods_profile, detail_sections
FROM goods_spu
WHERE id = '9740000000000000201' AND tenant_id = '1590229800633634816';
