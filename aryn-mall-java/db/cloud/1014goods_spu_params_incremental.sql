USE aryn_product;

-- 商品详情的 SPU 参数数组，存量商品默认为空。
SET @goods_params_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'goods_spu' AND column_name = 'params_json'),
  'SELECT 1',
  'ALTER TABLE goods_spu ADD COLUMN params_json JSON NULL COMMENT ''商品参数（label/value 数组）'''
);
PREPARE goods_params_stmt FROM @goods_params_ddl;
EXECUTE goods_params_stmt;
DEALLOCATE PREPARE goods_params_stmt;
