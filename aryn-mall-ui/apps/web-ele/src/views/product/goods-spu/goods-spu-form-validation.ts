export interface GoodsSkuFormValue {
  costPrice?: null | number;
  id?: string;
  originalPrice?: null | number;
  salesPrice?: null | number;
  specsArr?: Array<{
    specsId?: string;
    specsValueId?: string;
  }>;
  stock?: null | number;
  version?: null | number;
}

function isNonNegativeNumber(value: null | number | undefined) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

function buildSpecsKey(sku: GoodsSkuFormValue) {
  if (!sku.specsArr || sku.specsArr.length === 0) return '';

  const keys: string[] = [];
  for (const specs of sku.specsArr) {
    if (!specs.specsId || !specs.specsValueId) return null;
    keys.push(`${specs.specsId}:${specs.specsValueId}`);
  }
  return keys.sort().join('|');
}

export function validateGoodsSkus(
  enableSpecs: string,
  goodsSkus: GoodsSkuFormValue[] | null | undefined,
) {
  if (!goodsSkus || goodsSkus.length === 0) return '请至少配置一个SKU';
  if (enableSpecs === '0' && goodsSkus.length !== 1) {
    return '单规格商品只能配置一个SKU';
  }

  const specsKeys = new Set<string>();
  for (const sku of goodsSkus) {
    if (
      !isNonNegativeNumber(sku.salesPrice) ||
      !isNonNegativeNumber(sku.originalPrice) ||
      !isNonNegativeNumber(sku.costPrice) ||
      !isNonNegativeNumber(sku.stock)
    ) {
      return 'SKU价格和库存不能小于0';
    }
    if (sku.id && (sku.version === null || sku.version === undefined)) {
      return '商品库存版本缺失，请刷新后重试';
    }

    const specsKey = buildSpecsKey(sku);
    if (specsKey === null) return 'SKU规格信息不完整';
    if (enableSpecs === '1' && !specsKey) return '多规格商品的SKU规格不能为空';
    if (specsKeys.has(specsKey)) return 'SKU规格组合不能重复';
    specsKeys.add(specsKey);
  }

  return null;
}
