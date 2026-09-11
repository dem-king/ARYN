export interface ShipSkuProfileFormValue {
  moq?: null | number;
  packageSpec?: string;
  purchaseUnit?: string;
  skuId?: string;
  stepQty?: null | number;
}

export interface ShipProfileFormValue {
  barcode?: string;
  impaCode?: string;
  internalItemCode?: string;
  issaCode?: string;
  nameEn?: string;
  saleScope?: string;
  shelfLifeDays?: null | number;
  storageType?: string;
}

const SALE_SCOPES = new Set(['1', '2', '3']);
const STORAGE_TYPES = new Set(['1', '2', '3', '4', '5']);

function hasText(value: null | string | undefined): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

/** 销售范围必须为 1/2/3 */
export function validateSaleScope(saleScope: null | string | undefined) {
  if (!saleScope || !SALE_SCOPES.has(saleScope)) return '销售范围不合法';
  return null;
}

/** 船供可见商品（范围 2/3）必须至少提供 IMPA/ISSA/内部编码之一 */
export function validateShipCodes(profile: ShipProfileFormValue) {
  if (profile.saleScope === '1') return null;
  if (
    hasText(profile.impaCode) ||
    hasText(profile.issaCode) ||
    hasText(profile.internalItemCode)
  ) {
    return null;
  }
  return '船供商品必须至少提供 IMPA/ISSA/内部物料编码之一';
}

/**
 * 数量规则：船供可见商品必须提供采购单位、MOQ（≥1）和步长（>0），
 * 且 MOQ 必须是步长的整数倍；个人商品仅在填写时校验。
 */
export function validateQtyRule(
  saleScope: null | string | undefined,
  sku: ShipSkuProfileFormValue,
) {
  const shipSupplyVisible = saleScope !== '1';
  const moqFilled = sku.moq !== null && sku.moq !== undefined;
  const stepFilled = sku.stepQty !== null && sku.stepQty !== undefined;
  const filled = hasText(sku.purchaseUnit) || moqFilled || stepFilled;
  if (!shipSupplyVisible && !filled) return null;
  if (shipSupplyVisible && !hasText(sku.purchaseUnit))
    return '船供商品必须提供采购单位';
  if (sku.moq === null || sku.moq === undefined || sku.moq < 1)
    return '最小起订量必须大于0';
  if (sku.stepQty === null || sku.stepQty === undefined || sku.stepQty <= 0)
    return '数量步长必须大于0';
  if (sku.moq % sku.stepQty !== 0) return '最小起订量必须是数量步长的整数倍';
  return null;
}

/** 重量/体积必须为非负数字 */
export function validateWeightVolume(
  grossWeight: null | number | undefined,
  volume: null | number | undefined,
) {
  const valid = (value: null | number | undefined) =>
    (value !== null &&
      value !== undefined &&
      typeof value === 'number' &&
      Number.isFinite(value) &&
      value >= 0) ||
    value === null ||
    value === undefined;
  if (!valid(grossWeight) || !valid(volume)) return '重量和体积不能小于0';
  return null;
}

/** 储存条件取值校验 */
export function validateStorageType(storageType: null | string | undefined) {
  if (!storageType) return null;
  if (!STORAGE_TYPES.has(storageType)) return '储存条件不合法';
  return null;
}

/** 资料完整度：按船供关键资料字段填充比例计算（0-100） */
export function computePublishCompleteness(
  profile: ShipProfileFormValue,
  sku?: ShipSkuProfileFormValue,
) {
  const fields = [
    profile.saleScope,
    profile.impaCode,
    profile.issaCode,
    profile.internalItemCode,
    profile.barcode,
    profile.nameEn,
    profile.storageType,
    profile.shelfLifeDays,
    sku?.purchaseUnit,
    sku?.packageSpec,
    sku?.moq,
    sku?.stepQty,
  ];
  const filled = fields.filter((field) => {
    if (field === null || field === undefined) return false;
    return String(field).trim().length > 0;
  }).length;
  return Math.round((filled * 100) / fields.length);
}

/** 是否满足进入船供目录的发布要求 */
export function isPublishReadyForShipSupply(
  profile: ShipProfileFormValue,
  sku: ShipSkuProfileFormValue,
) {
  if (profile.saleScope === '1') return true;
  return (
    validateShipCodes(profile) === null &&
    validateQtyRule(profile.saleScope, sku) === null &&
    validateStorageType(profile.storageType) === null
  );
}
