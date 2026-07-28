import { requestClient } from '#/api/request';

export interface DeliveryArea {
  areaCode?: string;
  cityCode?: string;
  cityName?: string;
  districtCode?: string;
  districtName?: string;
  enabled: string;
  id?: string;
  provinceCode?: string;
  provinceName?: string;
  scopeLevel: 'CITY' | 'DISTRICT';
}

const baseUrl = '/mall-order/delivery/admin/areas';

export async function getDeliveryAreas() {
  return requestClient.get<DeliveryArea[]>(baseUrl);
}

export async function createDeliveryArea(data: DeliveryArea) {
  return requestClient.post(baseUrl, data);
}

export async function updateDeliveryArea(id: string, data: DeliveryArea) {
  return requestClient.put(`${baseUrl}/${id}`, data);
}

export async function removeDeliveryArea(id: string) {
  return requestClient.delete(`${baseUrl}/${id}`);
}
