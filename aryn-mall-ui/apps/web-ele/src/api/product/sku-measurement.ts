import type { SkuMeasurementForm } from '#/api/product/goods-spu';

import { requestClient } from '#/api/request';

export interface MeasurementDecision {
  normalizedBaseQuantity: null | number;
  suggestedSalesQuantity: null | number;
  excessBaseQuantity: null | number;
  unitStatus: string;
  quantityStatus: string;
  requiresReview: boolean;
  reason: string;
}
export const previewMeasurement = (
  measurement: SkuMeasurementForm,
  rawQuantity: string,
  rawUnit: string,
) =>
  requestClient.post<MeasurementDecision>('/product/sku-measurement/preview', {
    measurement,
    rawQuantity,
    rawUnit,
    approximate: false,
  });
export interface SkuIdentifier {
  id: string;
  skuId: string;
  identifierType: string;
  displayValue: string;
  status: string;
  source: string;
  reviewBy: null | string;
  reviewTime: null | string;
  revision: number;
}
export const getSkuIdentifiers = (skuId: string) =>
  requestClient.get<SkuIdentifier[]>('/product/sku-identifiers', {
    params: { skuId },
  });
export const registerSkuIdentifiers = (
  inputs: { skuId: string; source: string; type: string; value: string }[],
) => requestClient.post<SkuIdentifier[]>('/product/sku-identifiers', inputs);
export const reviewSkuIdentifier = (
  id: string,
  expectedRevision: number,
  approved: boolean,
) =>
  requestClient.post(
    `/product/sku-identifiers/${encodeURIComponent(id)}/review`,
    { expectedRevision, approved },
  );
