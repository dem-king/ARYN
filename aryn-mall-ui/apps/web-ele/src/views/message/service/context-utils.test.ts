import type { ViewChatMessage } from '../message-state';

import type { OrderContextItem } from '#/api/message/context';

import { describe, expect, it } from 'vitest';

import {
  extractConversationCards,
  formatDeliveryWay,
  formatOrderAmount,
  memberDisplayName,
  memberInitial,
  normalizeRecentOrders,
  orderDetailLink,
  orderStatusLabel,
  orderStatusTone,
} from './context-utils';

function message(overrides: Partial<ViewChatMessage> = {}): ViewChatMessage {
  return {
    clientMessageId: 'client-1',
    content: '',
    conversationId: 'conversation-1',
    createTime: '2026-11-05 10:00:00',
    id: 'message-1',
    messageType: 'PRODUCT_CARD',
    payload: '{}',
    seqNo: 1,
    senderId: 'staff-1',
    senderType: 'SYS_USER',
    ...overrides,
  };
}

describe('extractConversationCards', () => {
  it('提取商品卡片并生成可跳转链接', () => {
    const cards = extractConversationCards([
      message({
        payload: JSON.stringify({
          productId: 'p-1',
          summary: '¥19.9 · 现货',
          title: '矿泉水 550ml',
        }),
      }),
    ]);
    expect(cards).toHaveLength(1);
    expect(cards[0]).toMatchObject({
      summary: '¥19.9 · 现货',
      title: '矿泉水 550ml',
      type: 'PRODUCT_CARD',
    });
    expect(cards[0]?.link).toContain('/spu/form?id=p-1');
  });

  it('忽略纯文本与系统消息，坏 payload 用类型名兜底', () => {
    const cards = extractConversationCards([
      message({ id: 't1', messageType: 'TEXT', payload: undefined }),
      message({ id: 's1', messageType: 'SYSTEM', payload: undefined }),
      message({ id: 'o1', messageType: 'ORDER_CARD', payload: 'not-json' }),
    ]);
    expect(cards).toHaveLength(1);
    expect(cards[0]).toMatchObject({ title: '订单', type: 'ORDER_CARD' });
  });

  it('同一业务卡片重复发送只保留最新一条', () => {
    const cards = extractConversationCards([
      message({
        payload: JSON.stringify({ productId: 'p-1', title: '旧标题' }),
        seqNo: 1,
      }),
      message({
        id: 'message-2',
        payload: JSON.stringify({ productId: 'p-1', title: '新标题' }),
        seqNo: 5,
      }),
    ]);
    expect(cards).toHaveLength(1);
    expect(cards[0]?.title).toBe('新标题');
  });

  it('按时间倒序返回且最多 20 条', () => {
    const messages = Array.from({ length: 25 }, (_, index) =>
      message({
        id: `message-${index}`,
        payload: JSON.stringify({
          orderId: `o-${index}`,
          title: `订单${index}`,
        }),
        seqNo: index,
      }),
    );
    const cards = extractConversationCards(messages);
    expect(cards).toHaveLength(20);
    expect(cards[0]?.title).toBe('订单24');
    expect(cards.at(-1)?.title).toBe('订单5');
  });
});

function order(overrides: Partial<OrderContextItem> = {}): OrderContextItem {
  return {
    createTime: '2026-11-06 10:30:00',
    deliveryWay: '1',
    orderId: 'order-1',
    orderNo: 'NO20261106001',
    payAmount: 88.8,
    payStatus: '1',
    productSummary: null,
    status: '2',
    statusName: '待发货',
    ...overrides,
  };
}

describe('formatOrderAmount', () => {
  it('金额固定两位小数并带人民币符号', () => {
    expect(formatOrderAmount(88.8)).toBe('¥88.80');
    expect(formatOrderAmount(0)).toBe('¥0.00');
    expect(formatOrderAmount(1234.567)).toBe('¥1234.57');
  });

  it('兼容字符串金额（后端 decimal 两种序列化形态）', () => {
    expect(formatOrderAmount('88.8')).toBe('¥88.80');
    expect(formatOrderAmount(' 12 ')).toBe('¥12.00');
  });

  it('null/空白/非法值返回空串，模板据此隐藏金额区', () => {
    expect(formatOrderAmount(null)).toBe('');
    expect(formatOrderAmount(undefined)).toBe('');
    expect(formatOrderAmount('')).toBe('');
    expect(formatOrderAmount('   ')).toBe('');
    expect(formatOrderAmount('abc')).toBe('');
    expect(formatOrderAmount(Number.NaN)).toBe('');
  });
});

describe('orderStatusLabel', () => {
  it('优先 statusName，缺失回退 status 码（未知状态码场景）', () => {
    expect(orderStatusLabel('2', '待发货')).toBe('待发货');
    expect(orderStatusLabel('99', null)).toBe('99');
  });

  it('两侧空白 trim 后回退，全部缺失返回空串', () => {
    expect(orderStatusLabel(' 2 ', ' 待发货 ')).toBe('待发货');
    expect(orderStatusLabel(undefined, undefined)).toBe('');
    expect(orderStatusLabel('', '   ')).toBe('');
  });
});

describe('orderStatusTone', () => {
  it('已知状态码映射标签色调', () => {
    expect(orderStatusTone('1')).toBe('warning');
    expect(orderStatusTone('2')).toBe('primary');
    expect(orderStatusTone('4')).toBe('success');
    expect(orderStatusTone('5')).toBe('danger');
    expect(orderStatusTone('11')).toBe('info');
  });

  it('未知/缺失状态回退中性 info', () => {
    expect(orderStatusTone('99')).toBe('info');
    expect(orderStatusTone(undefined)).toBe('info');
  });
});

describe('formatDeliveryWay', () => {
  it('契约 §2.2：1 普通快递 / 2 上门自提', () => {
    expect(formatDeliveryWay('1')).toBe('普通快递');
    expect(formatDeliveryWay('2')).toBe('上门自提');
  });

  it('其它码/缺失返回空串（模板不展示配送方式）', () => {
    expect(formatDeliveryWay('3')).toBe('');
    expect(formatDeliveryWay('')).toBe('');
    expect(formatDeliveryWay(null)).toBe('');
    expect(formatDeliveryWay(undefined)).toBe('');
  });
});

describe('orderDetailLink', () => {
  it('按管理端既有约定生成订单详情路由并编码订单 ID', () => {
    expect(orderDetailLink('1958000000000000001')).toBe(
      '/order/detail?id=1958000000000000001',
    );
    expect(orderDetailLink('a b&c')).toBe('/order/detail?id=a%20b%26c');
    expect(orderDetailLink(' o-1 ')).toBe('/order/detail?id=o-1');
  });

  it('缺失/空白订单 ID 不生成链接（不渲染点击行为）', () => {
    expect(orderDetailLink()).toBe('');
    expect(orderDetailLink(null)).toBe('');
    expect(orderDetailLink('   ')).toBe('');
  });
});

describe('normalizeRecentOrders', () => {
  it('按下单时间倒序并截断到 3 条', () => {
    const rows = normalizeRecentOrders([
      order({ createTime: '2026-11-01 09:00:00', orderId: 'o-1' }),
      order({ createTime: '2026-11-05 09:00:00', orderId: 'o-5' }),
      order({ createTime: '2026-11-03 09:00:00', orderId: 'o-3' }),
      order({ createTime: '2026-11-04 09:00:00', orderId: 'o-4' }),
      order({ createTime: '2026-11-02 09:00:00', orderId: 'o-2' }),
    ]);
    expect(rows).toHaveLength(3);
    expect(rows.map((item) => item.orderId)).toEqual(['o-5', 'o-4', 'o-3']);
  });

  it('过滤缺少订单 ID 的坏数据，坏/缺失时间稳定排最后', () => {
    const rows = normalizeRecentOrders([
      order({ createTime: null, orderId: 'no-time' }),
      order({ createTime: '2026-11-01 09:00:00', orderId: 'o-1' }),
      order({ orderId: '   ' }),
      null as unknown as OrderContextItem,
    ]);
    expect(rows.map((item) => item.orderId)).toEqual(['o-1', 'no-time']);
  });

  it('空输入返回空数组（接口 200 + orders=[] 的空态语义）', () => {
    expect(normalizeRecentOrders([])).toEqual([]);
    expect(normalizeRecentOrders(null)).toEqual([]);
    expect(normalizeRecentOrders(undefined)).toEqual([]);
  });

  it('支持自定义上限（默认 3）', () => {
    const rows = normalizeRecentOrders(
      [
        order({ createTime: '2026-11-01 09:00:00', orderId: 'o-1' }),
        order({ createTime: '2026-11-05 09:00:00', orderId: 'o-2' }),
      ],
      1,
    );
    expect(rows.map((item) => item.orderId)).toEqual(['o-2']);
  });
});

describe('memberDisplayName / memberInitial', () => {
  it('昵称缺失回退「会员 {id}」，会话快照全缺失回退「会员」', () => {
    expect(memberDisplayName({ id: '1001', name: '王小雅' })).toBe('王小雅');
    expect(memberDisplayName({ id: '1001', name: null })).toBe('会员 1001');
    expect(memberDisplayName({ id: '1001' })).toBe('会员 1001');
    expect(memberDisplayName(null)).toBe('会员');
  });

  it('头像首字优先昵称首字，其次会员 ID 首字', () => {
    expect(memberInitial({ id: '1001', name: ' 王小雅 ' })).toBe('王');
    expect(memberInitial({ id: '1001', name: null })).toBe('1');
    expect(memberInitial(null)).toBe('会');
  });
});
