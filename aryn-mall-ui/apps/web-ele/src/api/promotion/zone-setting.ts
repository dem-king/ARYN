import { requestClient } from '#/api/request';

/** 读取专区设置（未配置返回默认开启） */
export async function getZoneSetting(zoneType: 'clearance' | 'member') {
  return requestClient.get(`/promotion/zone/setting/${zoneType}`);
}

/** 保存专区设置 */
export async function saveZoneSetting(
  zoneType: 'clearance' | 'member',
  data: any,
) {
  return requestClient.put(`/promotion/zone/setting/${zoneType}`, data);
}
