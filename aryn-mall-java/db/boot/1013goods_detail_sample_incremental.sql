USE aryn_boot;
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- 银鹭样板商品补齐品牌和可核实的商品规格。仅填目标租户中尚未配置的字段。
START TRANSACTION;

SET @goods_detail_sample_tenant = '1590229800633634816';
SET @goods_detail_sample_spu = '9740000000000000332';
SET @goods_detail_sample_sku = '10740000000000000332';

INSERT INTO goods_brand (id, name, status, tenant_id, create_by, create_time, del_flag)
SELECT REPLACE(UUID(), '-', ''), '银鹭', '0', @goods_detail_sample_tenant, 'system', NOW(), '0'
FROM goods_spu spu
WHERE spu.id = @goods_detail_sample_spu AND spu.tenant_id = @goods_detail_sample_tenant
  AND spu.del_flag = '0' AND spu.name = '银鹭花生牛奶450ml'
  AND (spu.brand_id IS NULL OR spu.brand_id IN ('', '0'))
  AND NOT EXISTS (
    SELECT 1 FROM goods_brand brand
    WHERE brand.tenant_id = @goods_detail_sample_tenant AND brand.name = '银鹭' AND brand.del_flag = '0'
  );

SET @goods_detail_sample_brand = (
  SELECT id FROM goods_brand
  WHERE tenant_id = @goods_detail_sample_tenant AND name = '银鹭' AND status = '0' AND del_flag = '0'
  ORDER BY id LIMIT 1
);

UPDATE goods_spu
SET brand_id = @goods_detail_sample_brand
WHERE id = @goods_detail_sample_spu AND tenant_id = @goods_detail_sample_tenant
  AND del_flag = '0' AND name = '银鹭花生牛奶450ml'
  AND (brand_id IS NULL OR brand_id IN ('', '0')) AND @goods_detail_sample_brand IS NOT NULL;

UPDATE goods_sku sku
JOIN goods_spu spu ON spu.id = sku.spu_id AND spu.tenant_id = sku.tenant_id
SET sku.unit_spec_text = '450 mL'
WHERE spu.id = @goods_detail_sample_spu AND spu.tenant_id = @goods_detail_sample_tenant
  AND spu.del_flag = '0' AND spu.name = '银鹭花生牛奶450ml' AND spu.enable_specs = '0'
  AND sku.id = @goods_detail_sample_sku AND sku.del_flag = '0'
  AND (sku.unit_spec_text IS NULL OR TRIM(sku.unit_spec_text) = '');

COMMIT;
