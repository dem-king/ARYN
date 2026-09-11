import { requestClient } from '#/api/request';

/** SPU 船供资料 */
export interface ShipGoodsProfile {
  barcode?: string;
  impaCode?: string;
  internalItemCode?: string;
  issaCode?: string;
  nameEn?: string;
  publishCompleteness?: number;
  saleScope?: string;
  searchAliases?: string;
  shelfLifeDays?: number;
  shipSupplyRemark?: string;
  spuId: string;
  storageType?: string;
  temperatureRequirement?: string;
}

/** SKU 包装资料 */
export interface ShipSkuProfile {
  baseUnit?: string;
  conversionRate?: number;
  grossWeight?: number;
  moq?: number;
  packageSpec?: string;
  packageSpecEn?: string;
  skuId: string;
  stepQty?: number;
  stockWarningLine?: number;
  volume?: number;
}

/** 编码映射 */
export interface ProductCodeMapping {
  codeType: string;
  codeValue: string;
  confidence?: number;
  matchSource?: string;
  skuId: string;
  status?: string;
}

/** 船供商品摘要行 */
export interface ShipProductSummary {
  barcode?: string;
  baseUnit?: string;
  impaCode?: string;
  internalItemCode?: string;
  issaCode?: string;
  moq?: number;
  name?: string;
  nameEn?: string;
  packageSpec?: string;
  publishCompleteness?: number;
  purchaseUnit?: string;
  saleScope?: string;
  salesPrice?: number;
  spuId: string;
  status?: string;
  stock?: number;
  stepQty?: number;
  storageType?: string;
}

export async function getShipPage(query: any) {
  return requestClient.get('/product/goodsspu/ship/page', { params: query });
}

export async function getShipProfile(spuId: string) {
  return requestClient.get<{
    codeMappings: ProductCodeMapping[];
    profile: null | ShipGoodsProfile;
    skuProfiles: ShipSkuProfile[];
  }>(`/product/goodsspu/profile/${spuId}`);
}

export async function saveShipProfile(data: {
  codeMappings?: ProductCodeMapping[];
  profile: Omit<ShipGoodsProfile, 'publishCompleteness' | 'spuId'> & {
    spuId?: string;
  };
  skuProfiles?: ShipSkuProfile[];
  spuId: string;
}) {
  return requestClient.post('/product/goodsspu/profile', data);
}
