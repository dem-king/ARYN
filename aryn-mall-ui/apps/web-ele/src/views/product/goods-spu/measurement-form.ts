import type { SkuMeasurementForm } from '#/api/product/goods-spu';

export type { SkuMeasurementForm } from '#/api/product/goods-spu';

export function normalizeMeasurement(
  value: SkuMeasurementForm,
): SkuMeasurementForm {
  return {
    ...value,
    salesUnit: value.salesUnit ?? '件',
    quantityPrecision: value.quantityPrecision ?? 'UNKNOWN',
    minOrderQty: value.minOrderQty ?? 1,
    stepQty: value.stepQty ?? 1,
    measurementRevision: value.measurementRevision ?? 0,
  };
}
export function validateMeasurement(value: SkuMeasurementForm): null | string {
  const form = normalizeMeasurement(value);
  if (!form.salesUnit?.trim() || form.salesUnit.length > 24)
    return '销售单位不能为空且最多24字';
  if (!['APPROX', 'EXACT', 'UNKNOWN'].includes(form.quantityPrecision ?? ''))
    return '请选择度量精度';
  for (const [name, quantity] of [
    ['起订量', form.minOrderQty],
    ['数量步长', form.stepQty],
  ] as const) {
    if (
      typeof quantity !== 'number' ||
      !Number.isInteger(quantity) ||
      quantity < 1 ||
      quantity > 2_147_483_647
    )
      return `${name}须为正整数`;
  }
  if (
    form.baseDimension ||
    form.baseUnit ||
    (form.baseQtyPerSalesUnit !== null &&
      form.baseQtyPerSalesUnit !== undefined) ||
    form.quantityPrecision !== 'UNKNOWN'
  ) {
    const units: Record<string, string> = {
      MASS: 'g',
      VOLUME: 'ml',
      LENGTH: 'mm',
      COUNT: 'pcs',
    };
    if (!form.baseDimension || units[form.baseDimension] !== form.baseUnit)
      return '基准量纲与单位不一致';
    const decimal = String(form.baseQtyPerSalesUnit ?? '');
    if (!/^\d{1,15}(?:\.\d{1,9})?$/.test(decimal) || !/[1-9]/.test(decimal))
      return '基准量须大于0，最多15位整数和9位小数';
  }
  if ((form.unitSpecText?.length ?? 0) > 500) return '展示规格最多500字';
  return null;
}
