import { createCrudApi } from '#/api/crud-factory';

/** 订单分页查询参数 */
export interface OrderPageQuery {
  id?: string;
  keyword?: string;
  orderNo?: string;
  status?: string;
  paymentType?: string;
  deliveryWay?: string;
  payStatus?: string;
  recipientName?: string;
  recipientPhone?: string;
  paymentQueryTimes?: string[];
}

/** 订单 DTO（新增/编辑） */
export interface OrderDTO {
  id?: string;
  status?: string;
  remark?: string;
}

/** 订单 VO（查询返回） */
export interface OrderVO {
  id: string;
  orderNo: string;
  userId: string;
  status: string;
  payStatus: string;
  totalPrice: number;
  freightPrice: number;
  couponPrice: number;
  paymentPrice: number;
  paymentType: string;
  deliveryWay: string;
  createTime: string;
  paymentTime?: string;
  deliverTime?: string;
  receiverTime?: string;
  recipientName?: string;
  recipientPhone?: string;
  recipientAddress?: string;
}

/** 标准 CRUD 方法（通过工厂函数生成） */
export const { getPage, getById, add, edit, del } = createCrudApi<
  OrderPageQuery,
  OrderDTO,
  OrderVO
>('/mall-order/orderinfo');

/**
 * 获取订单统计信息
 */
export async function getStatistics(query: OrderPageQuery) {
  return requestClient.get('/mall-order/orderinfo/statistics', {
    params: query,
  });
}

/**
 * 获取订单数量
 */
export async function getCount() {
  return requestClient.get('/mall-order/orderinfo/count');
}

/**
 * 订单发货
 */
export interface DeliverOrderDTO {
  id: string;
  logisticsCompanyCode?: string;
  logisticsCompanyName?: string;
  logisticsNo?: string;
  orderItemIdList?: string[];
}

export async function deliverOrder(data: DeliverOrderDTO) {
  return requestClient.post('/mall-order/orderinfo/deliver', data);
}

/**
 * 取消订单
 */
export async function cancelObj(id: string) {
  return requestClient.put(`/mall-order/orderinfo/cancel/${id}`);
}

/**
 * 自提订单
 */
export async function selffetchObj(data: OrderDTO) {
  return requestClient.post('/mall-order/orderinfo/selffetch', data);
}

// 保持向后兼容的别名
export const addObj = add;
export const editObj = edit;
export const delObj = del;

import { requestClient } from '#/api/request';
