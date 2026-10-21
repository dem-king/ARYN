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

export interface GoodsAttrTagFormValue {
  icon?: null | string;
  label?: null | string;
  linkType?: null | string;
  linkUrl?: null | string;
  popupContent?: null | string;
  popupTitle?: null | string;
  tone?: null | string;
  value?: null | string;
}

const MAX_ATTR_TAG_COUNT = 6;
const MAX_ATTR_TAG_LABEL_LENGTH = 12;
const MAX_ATTR_TAG_VALUE_LENGTH = 24;
const MAX_ATTR_TAG_POPUP_TITLE_LENGTH = 30;
const MAX_ATTR_TAG_POPUP_CONTENT_LENGTH = 500;
const MAX_ATTR_TAG_LINK_URL_LENGTH = 500;

function trimText(value: null | string | undefined) {
  return (value ?? '').trim();
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

export function validateGoodsAttrTags(
  attrTags: GoodsAttrTagFormValue[] | null | undefined,
) {
  if (!attrTags || attrTags.length === 0) return null;
  if (attrTags.length > MAX_ATTR_TAG_COUNT) {
    return `商品属性标签最多配置 ${MAX_ATTR_TAG_COUNT} 个`;
  }

  for (const [index, tag] of attrTags.entries()) {
    const row = index + 1;
    const label = trimText(tag.label);
    const value = trimText(tag.value);

    // 空行提交时由后端直接丢弃，前端不拦截
    if (!label && !value) continue;
    if (!label || !value) {
      return `第 ${row} 个属性标签的属性名和值必须同时填写`;
    }
    if (label.length > MAX_ATTR_TAG_LABEL_LENGTH) {
      return `第 ${row} 个属性标签的属性名不能超过 ${MAX_ATTR_TAG_LABEL_LENGTH} 个字符`;
    }
    if (value.length > MAX_ATTR_TAG_VALUE_LENGTH) {
      return `第 ${row} 个属性标签的属性值不能超过 ${MAX_ATTR_TAG_VALUE_LENGTH} 个字符`;
    }

    const linkType = trimText(tag.linkType) || 'none';
    if (linkType === 'popup') {
      const popupContent = trimText(tag.popupContent);
      if (!popupContent) {
        return `第 ${row} 个属性标签的弹窗内容不能为空`;
      }
      if (popupContent.length > MAX_ATTR_TAG_POPUP_CONTENT_LENGTH) {
        return `第 ${row} 个属性标签的弹窗内容不能超过 ${MAX_ATTR_TAG_POPUP_CONTENT_LENGTH} 个字符`;
      }
      if (trimText(tag.popupTitle).length > MAX_ATTR_TAG_POPUP_TITLE_LENGTH) {
        return `第 ${row} 个属性标签的弹窗标题不能超过 ${MAX_ATTR_TAG_POPUP_TITLE_LENGTH} 个字符`;
      }
    } else if (linkType === 'page') {
      const linkUrl = trimText(tag.linkUrl);
      if (
        !linkUrl.startsWith('/pages/') &&
        !linkUrl.startsWith('/sub-pages/')
      ) {
        return `第 ${row} 个属性标签的跳转路径必须以 /pages/ 或 /sub-pages/ 开头`;
      }
      if (linkUrl.length > MAX_ATTR_TAG_LINK_URL_LENGTH) {
        return `第 ${row} 个属性标签的跳转路径不能超过 ${MAX_ATTR_TAG_LINK_URL_LENGTH} 个字符`;
      }
    }
  }

  return null;
}
