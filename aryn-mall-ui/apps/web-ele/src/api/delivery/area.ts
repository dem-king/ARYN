import { requestClient } from '#/api/request';

/**
 * 配送范围实体
 */
export interface DeliveryArea {
  id?: string;
  provinceCode?: string;
  provinceName?: string;
  cityCode?: string;
  cityName?: string;
  areaCode?: string;
  areaName?: string;
  enabled?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 获取配送范围分页列表
 */
export async function getDeliveryAreaPage(params: any) {
  return requestClient.get('/mall-order/delivery/area/page', { params });
}

/**
 * 新增配送范围
 */
export async function createDeliveryArea(data: DeliveryArea) {
  return requestClient.post('/mall-order/delivery/area', data);
}

/**
 * 修改配送范围
 */
export async function updateDeliveryArea(data: DeliveryArea) {
  return requestClient.put('/mall-order/delivery/area', data);
}

/**
 * 删除配送范围
 */
export async function deleteDeliveryArea(id: string) {
  return requestClient.delete(`/mall-order/delivery/area/${id}`);
}