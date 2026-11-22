USE aryn_boot;

-- 商详首屏服务承诺标签（code 数组），存量商品默认为空、端上回退全局三项。
SET @goods_service_tags_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'goods_spu' AND column_name = 'service_tags'),
  'SELECT 1',
  'ALTER TABLE goods_spu ADD COLUMN service_tags JSON NULL COMMENT ''服务承诺标签（code 数组）'''
);
PREPARE goods_service_tags_stmt FROM @goods_service_tags_ddl;
EXECUTE goods_service_tags_stmt;
DEALLOCATE PREPARE goods_service_tags_stmt;
