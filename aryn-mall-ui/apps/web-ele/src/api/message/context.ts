import type { Conversation } from './types';

import { requestClient } from '#/api/request';

/**
 * 会话上下文聚合接口（P2-A 会员/订单上下文契约 §8）。
 *
 * 一个只读接口同时返回会员资料、最近订单、会话摘要与业务上下文，
 * 供客服工作台右侧上下文面板渲染，避免前端分别请求 message/order 两域。
 */

/** 会话上下文中的会员资料（来自会话参与者快照，契约 §8.2）。 */
export interface MemberContext {
  /** 头像 URL；缺失为 null */
  avatar?: null | string;
  /** 会员 ID（会话 customer_id） */
  id: string;
  /** 会员昵称：参与者快照缺失时后端回退为会员 ID，仍可能为 null */
  name?: null | string;
}

/** 会话上下文中单条最近订单（订单域 `OrderContextVO` 的镜像，契约 §8.2）。 */
export interface OrderContextItem {
  /** 下单时间（`yyyy-MM-dd HH:mm:ss`） */
  createTime?: null | string;
  /** 配送方式码：1 普通快递 / 2 上门自提（文案由前端映射） */
  deliveryWay?: null | string;
  /** 订单 ID（`order_info.id`），订单详情跳转依据 */
  orderId: string;
  /** 订单单号 */
  orderNo?: null | string;
  /** 支付金额：优先实收金额，缺失回退应付金额；均空为 null（前端不展示金额） */
  payAmount?: null | number;
  /** 支付状态码：0 未支付 / 1 已支付 */
  payStatus?: null | string;
  /** 商品摘要；本批次订单域固定返回 null，前端不渲染空行 */
  productSummary?: null | string;
  /** 订单状态码：1 待付款 / 2 待发货 / 3 待收货 / 4 已完成 / 5 退款中 / 11 已取消 */
  status?: null | string;
  /** 状态中文名；未知状态码为 null，前端回退展示 status */
  statusName?: null | string;
}

/** `GET /message/staff/conversation/{id}/context` 的聚合响应（契约 §8.2）。 */
export interface ConversationContext {
  /** 会话摘要（复用既有 ConversationVO）；主管降级视角不计算未读字段 */
  conversation?: Conversation;
  /** 会话业务上下文 JSON（与 conversation.contextPayload 同值）；无则 null */
  contextPayload?: null | string;
  /** 会员资料；`STAFF_DIRECT` 私信会话为 null */
  member: MemberContext | null;
  /**
   * 最近订单（最多 3 条，按下单时间倒序）。
   *
   * 无订单 / 私信会话 / 订单域降级时均为空数组（不会是 null）；
   * 接口 200 + 空数组表示「确实没有订单」，与请求失败降级要区分展示。
   */
  orders: OrderContextItem[];
}

/**
 * 查询会话上下文（会员 + 最近订单）。
 *
 * 路径以微服务域 `message` 开头：cloud 模式原样请求 `/message/...`，
 * boot 模式由 `requestClient` 内部的 `parseOpenBoot` + `rewriteBootUrl`
 * 统一改写为 `/boot/...`，禁止在页面/组件里硬编码 `/boot`。
 *
 * 权限与会话详情一致：会话参与者；主管持 `message:service:supervisor`
 * 时后端走全租户只读降级，接口仍可能返回 200。
 */
export function getConversationContext(conversationId: string) {
  return requestClient.get<ConversationContext>(
    `/message/staff/conversation/${encodeURIComponent(conversationId)}/context`,
  );
}
