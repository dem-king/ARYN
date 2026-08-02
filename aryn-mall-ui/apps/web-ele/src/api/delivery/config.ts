import { requestClient } from '#/api/request';

/**
 * 仓库配置实体
 */
export interface WarehouseConfig {
  id?: string;
  /** 仓库名称 */
  warehouseName: string;
  /** 联系人 */
  contactName: string;
  /** 联系电话 */
  contactPhone: string;
  /** 省 */
  province: string;
  /** 市 */
  city: string;
  /** 区 */
  area: string;
  /** 详细地址 */
  address: string;
}

/**
 * 获取仓库配置
 */
export async function getWarehouseConfig() {
  return requestClient.get('/mall-order/delivery/warehouse-config');
}

/**
 * 更新仓库配置
 */
export async function updateWarehouseConfig(data: WarehouseConfig) {
  return requestClient.put('/mall-order/delivery/warehouse-config', data);
}

/**
 * 获取省市区级联数据树
 */
export async function getRegionTree() {
  return requestClient.get('/mall-order/delivery/region/tree');
}
