import { requestClient } from '#/api/request';

/**
 * 运费规则实体（订单级满额包邮）
 */
export interface FreightRule {
  id?: string;
  ruleName?: string;
  /** 购买场景：0-全部；1-海员个人；2-船供采购；3-企业采购 */
  bizScene?: string;
  /** 配送方式：0-全部；1-普通快递；3-公司配送（岸上） */
  deliveryWay?: string;
  /** 适用省份编码，英文逗号分隔；空=全国 */
  provinceCodes?: string;
  thresholdAmount?: number;
  baseFreight?: number;
  maxFreight?: null | number;
  priority?: number;
  enabled?: string;
  remark?: string;
  createTime?: string;
}

/**
 * 运费规则分页列表
 */
export async function getFreightRulePage(params: any) {
  return requestClient.get('/mall-order/delivery/freight-rule/page', {
    params,
  });
}

/**
 * 启用中的运费规则
 */
export async function getFreightRuleList() {
  return requestClient.get('/mall-order/delivery/freight-rule/list');
}

/**
 * 新增运费规则
 */
export async function createFreightRule(data: FreightRule) {
  return requestClient.post('/mall-order/delivery/freight-rule', data);
}

/**
 * 修改运费规则
 */
export async function updateFreightRule(data: FreightRule) {
  return requestClient.put('/mall-order/delivery/freight-rule', data);
}

/**
 * 删除运费规则
 */
export async function deleteFreightRule(id: string) {
  return requestClient.delete(`/mall-order/delivery/freight-rule/${id}`);
}
