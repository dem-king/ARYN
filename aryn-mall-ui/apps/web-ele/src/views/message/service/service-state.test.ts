import type { ViewChatMessage } from '../message-state';

import type { Conversation } from '#/api/message/types';

import { describe, expect, it } from 'vitest';

import {
  buildConversationInboxParams,
  buildImageMessagePayload,
  buildQuoteSummary,
  buildQuoteTarget,
  buildTimelineItems,
  canQuoteMessage,
  canRecallMessage,
  chatMessageSenderLabel,
  claimState,
  composerState,
  conversationActions,
  conversationDisplayName,
  conversationFilterTags,
  conversationOwnerName,
  conversationQuery,
  conversationStatusLabel,
  conversationStatusTone,
  conversationTimeLabel,
  createConversationFilterState,
  formatContextPayload,
  formatDateDivider,
  formatDateTime,
  formatWaitingDuration,
  isLatestOutgoingMessage,
  isSendEnter,
  latestOutgoingMessage,
  quotedBlockView,
  readReceipt,
  shouldMarkRead,
  validateImageUpload,
  visibleServiceTabs,
  waitingDurationLabel,
} from './service-state';

const OFFLINE_GATE = {
  configured: true,
  enabled: true,
  loaded: true,
  presence: 'OFFLINE' as const,
};
const ONLINE_GATE = { ...OFFLINE_GATE, presence: 'ONLINE' as const };

function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    conversationType: 'CUSTOMER_SERVICE',
    id: 'conversation-1',
    lastReadSeq: 0,
    lastSeq: 0,
    queueCode: 'DEFAULT',
    status: 'ASSIGNED',
    unreadCount: 0,
    ...overrides,
  };
}

function message(overrides: Partial<ViewChatMessage> = {}): ViewChatMessage {
  return {
    clientMessageId: 'client-1',
    content: 'hello',
    conversationId: 'conversation-1',
    createTime: '2026-11-04 10:00:00',
    id: 'local:client-1',
    messageType: 'TEXT',
    seqNo: 0,
    senderId: 'staff-1',
    senderType: 'SYS_USER',
    ...overrides,
  };
}

describe('service workbench state helpers', () => {
  it('基础坐席只有三个页签，主管多一个全部会话', () => {
    expect(visibleServiceTabs(false).map((tab) => tab.key)).toEqual([
      'mine',
      'waiting',
      'history',
    ]);
    expect(visibleServiceTabs(true).map((tab) => tab.key)).toEqual([
      'mine',
      'waiting',
      'history',
      'supervisor',
    ]);
    expect(
      visibleServiceTabs(true).find((tab) => tab.key === 'supervisor')?.label,
    ).toBe('全部会话');
  });

  it('页签映射到状态过滤：我的=OPEN、历史=CLOSED、待领取/全部不过滤', () => {
    expect(conversationQuery('mine')).toEqual({ status: 'OPEN' });
    expect(conversationQuery('history')).toEqual({ status: 'CLOSED' });
    expect(conversationQuery('waiting')).toEqual({});
    expect(conversationQuery('supervisor')).toEqual({});
  });

  it('展示会员昵称，缺失时回退会员 ID，私信会话单独展示', () => {
    expect(
      conversationDisplayName(conversation({ customerName: '张三' })),
    ).toBe('张三');
    expect(conversationDisplayName(conversation({ customerId: '10086' }))).toBe(
      '会员 10086',
    );
    expect(
      conversationDisplayName(
        conversation({
          conversationType: 'STAFF_DIRECT',
          customerId: '10086',
        }),
      ),
    ).toBe('工作人员私信');
    expect(conversationDisplayName(undefined)).toBe('');
    expect(
      conversationOwnerName(conversation({ assignedStaffName: '客服A' })),
    ).toBe('客服A');
    expect(conversationOwnerName(conversation({ assignedStaffId: '9' }))).toBe(
      '9',
    );
    expect(conversationOwnerName(conversation())).toBe('未分配');
  });

  it('会员消息发送者优先显示会话昵称，避免暴露 MALL_USER 枚举', () => {
    const memberConversation = conversation({ customerName: '八度的骄傲' });
    expect(
      chatMessageSenderLabel(
        message({ senderType: 'MALL_USER', senderName: undefined }),
        memberConversation,
      ),
    ).toBe('八度的骄傲');
    expect(
      chatMessageSenderLabel(
        message({ senderType: 'MALL_USER', senderName: undefined }),
        conversation({ customerName: undefined, customerId: '10086' }),
      ),
    ).toBe('会员');
    expect(
      chatMessageSenderLabel(
        message({ senderType: 'SYS_USER', senderName: '宇宙第一帅' }),
        memberConversation,
      ),
    ).toBe('宇宙第一帅');
    expect(
      chatMessageSenderLabel(
        message({ senderType: 'SYS_USER', senderName: undefined }),
        memberConversation,
      ),
    ).toBe('SYS_USER');
    expect(chatMessageSenderLabel(undefined, memberConversation)).toBe('');
  });

  it('状态标签文案与配色覆盖四种状态', () => {
    expect(conversationStatusLabel('WAITING')).toBe('待领取');
    expect(conversationStatusLabel('ASSIGNED')).toBe('已分配');
    expect(conversationStatusLabel('ACTIVE')).toBe('进行中');
    expect(conversationStatusLabel('CLOSED')).toBe('已关闭');
    expect(conversationStatusLabel()).toBe('');
    expect(conversationStatusTone('WAITING')).toBe('warning');
    expect(conversationStatusTone('ASSIGNED')).toBe('primary');
    expect(conversationStatusTone('ACTIVE')).toBe('success');
    expect(conversationStatusTone('CLOSED')).toBe('info');
  });

  it('待领取会话输入禁用并提示领取后可回复', () => {
    const state = composerState(conversation({ status: 'WAITING' }));
    expect(state.disabled).toBe(true);
    expect(state.hint).toContain('领取后可回复');
    expect(state.placeholder).toBe('领取后可回复');
  });

  it('已关闭会话输入禁用并展示 24h 重开截止时间', () => {
    const state = composerState(
      conversation({
        reopenDeadline: '2026-11-05T12:30:00',
        status: 'CLOSED',
      }),
    );
    expect(state.disabled).toBe(true);
    expect(state.hint).toContain('2026-11-05 12:30');
    expect(state.hint).toContain('重新咨询');

    const noDeadline = composerState(conversation({ status: 'CLOSED' }));
    expect(noDeadline.hint).toBe('会话已关闭，仅可查看历史消息');
  });

  it('已分配/进行中会话可回复，且离线不阻塞回复', () => {
    for (const status of ['ASSIGNED', 'ACTIVE'] as const) {
      const state = composerState(conversation({ status }));
      expect(state.disabled).toBe(false);
      // 离线只影响领取/自动分配，不影响已分配会话回复
      expect(OFFLINE_GATE.presence).toBe('OFFLINE');
    }
  });

  it('未选择会话时输入禁用并提示先选会话', () => {
    expect(composerState(undefined).disabled).toBe(true);
    expect(composerState(undefined).placeholder).toBe('请选择左侧会话');
  });

  it('领取按钮只在 WAITING 出现，离线/未配置/禁用时禁用并给出原因', () => {
    const waiting = conversation({ status: 'WAITING' });
    expect(claimState(waiting, ONLINE_GATE)).toEqual({
      disabled: false,
      hint: '',
      visible: true,
    });

    const offline = claimState(waiting, OFFLINE_GATE);
    expect(offline.visible).toBe(true);
    expect(offline.disabled).toBe(true);
    expect(offline.hint).toContain('在线');

    const unconfigured = claimState(waiting, {
      ...ONLINE_GATE,
      configured: false,
    });
    expect(unconfigured.disabled).toBe(true);
    expect(unconfigured.hint).toContain('配置客服坐席');

    const disabled = claimState(waiting, { ...ONLINE_GATE, enabled: false });
    expect(disabled.disabled).toBe(true);
    expect(disabled.hint).toContain('禁用');

    const loading = claimState(waiting, { ...ONLINE_GATE, loaded: false });
    expect(loading.disabled).toBe(true);

    // 非待领取会话不显示领取按钮
    expect(claimState(conversation(), ONLINE_GATE).visible).toBe(false);
  });

  it('状态机按钮：WAITING 只领取；ASSIGNED/ACTIVE 可回复转交关闭；CLOSED 全隐藏', () => {
    const waiting = conversationActions({
      conversation: conversation({ status: 'WAITING' }),
      gate: ONLINE_GATE,
      supervisor: false,
      supervisorView: false,
    });
    expect(waiting.canClaim).toBe(true);
    expect(waiting.canReply).toBe(false);
    expect(waiting.canTransfer).toBe(false);
    expect(waiting.canClose).toBe(false);

    const active = conversationActions({
      conversation: conversation({ status: 'ACTIVE' }),
      gate: OFFLINE_GATE,
      supervisor: false,
      supervisorView: false,
    });
    expect(active.canReply).toBe(true);
    expect(active.canTransfer).toBe(true);
    expect(active.canClose).toBe(true);
    expect(active.canClaim).toBe(false);

    const closed = conversationActions({
      conversation: conversation({ status: 'CLOSED' }),
      gate: ONLINE_GATE,
      supervisor: false,
      supervisorView: false,
    });
    expect(closed.canReply).toBe(false);
    expect(closed.canTransfer).toBe(false);
    expect(closed.canClose).toBe(false);
    expect(closed.reopenHint).toContain('重开窗口已结束');
  });

  it('主管全部会话视图出现强制分配与分配日志入口，并用强制分配替代普通转交', () => {
    const asSupervisor = conversationActions({
      conversation: conversation({ status: 'ASSIGNED' }),
      gate: OFFLINE_GATE,
      supervisor: true,
      supervisorView: true,
    });
    expect(asSupervisor.canForceAssign).toBe(true);
    expect(asSupervisor.canOpenAssignmentLogs).toBe(true);
    expect(asSupervisor.canTransfer).toBe(false);
    // 强制分配交给后端鉴权，主管本人不必是在线坐席
    expect(asSupervisor.canClaim).toBe(false);

    const asAgent = conversationActions({
      conversation: conversation({ status: 'ASSIGNED' }),
      gate: ONLINE_GATE,
      supervisor: false,
      supervisorView: false,
    });
    expect(asAgent.canForceAssign).toBe(false);
    expect(asAgent.canOpenAssignmentLogs).toBe(false);
    expect(asAgent.canTransfer).toBe(true);

    // 关闭态不再提供强制分配
    expect(
      conversationActions({
        conversation: conversation({ status: 'CLOSED' }),
        gate: OFFLINE_GATE,
        supervisor: true,
        supervisorView: true,
      }).canForceAssign,
    ).toBe(false);
  });

  it('只有已分配的进行中会话才推进已读游标', () => {
    expect(
      shouldMarkRead(
        conversation({ lastReadSeq: 1, lastSeq: 3, status: 'ACTIVE' }),
      ),
    ).toBe(true);
    expect(
      shouldMarkRead(
        conversation({ lastReadSeq: 3, lastSeq: 3, status: 'ASSIGNED' }),
      ),
    ).toBe(false);
    // 待领取只读预览不能产生已读游标
    expect(
      shouldMarkRead(
        conversation({ lastReadSeq: 0, lastSeq: 3, status: 'WAITING' }),
      ),
    ).toBe(false);
    expect(
      shouldMarkRead(
        conversation({ lastReadSeq: 0, lastSeq: 3, status: 'CLOSED' }),
      ),
    ).toBe(false);
  });

  it('已读回执按 peerLastReadSeq 与 seqNo 比较', () => {
    const sent = message({ id: 'message-3', seqNo: 3 });
    expect(readReceipt(3, sent)).toBe('read');
    expect(readReceipt(4, sent)).toBe('read');
    expect(readReceipt(2, sent)).toBe('unread');
    expect(readReceipt(undefined, sent)).toBe('unread');
    // 发送中/失败/本地乐观消息不展示回执
    expect(readReceipt(99, message({ sendState: 'failed', seqNo: 3 }))).toBe(
      'none',
    );
    expect(readReceipt(99, message({ sendState: 'sending' }))).toBe('none');
    expect(readReceipt(99, message({ seqNo: Number.MAX_SAFE_INTEGER }))).toBe(
      'none',
    );
    // 对方消息没有“我方已读回执”
    expect(
      readReceipt(99, message({ senderType: 'MALL_USER', seqNo: 3 })),
    ).toBe('none');
    expect(readReceipt(99, undefined)).toBe('none');
  });

  it('回执只挂在最后一条已落库的我方消息上', () => {
    const messages = [
      message({ clientMessageId: 'c-1', id: 'm-1', seqNo: 1 }),
      message({ clientMessageId: 'c-2', id: 'm-2', seqNo: 2 }),
      message({
        clientMessageId: 'c-3',
        id: 'local:c-3',
        sendState: 'failed',
        seqNo: Number.MAX_SAFE_INTEGER,
      }),
      message({
        clientMessageId: 'c-4',
        id: 'm-4',
        senderType: 'MALL_USER',
        seqNo: 4,
      }),
    ];
    const newest = messages[1] as ViewChatMessage;
    const older = messages[0] as ViewChatMessage;
    expect(latestOutgoingMessage(messages)?.clientMessageId).toBe('c-2');
    expect(isLatestOutgoingMessage(messages, newest)).toBe(true);
    expect(isLatestOutgoingMessage(messages, older)).toBe(false);
    expect(latestOutgoingMessage([])).toBeUndefined();
  });

  it('中文输入法组合中的 Enter 不触发发送，Shift+Enter 换行', () => {
    expect(isSendEnter({ isComposing: true, key: 'Enter' })).toBe(false);
    expect(isSendEnter({ isComposing: false, key: 'Enter' })).toBe(true);
    expect(isSendEnter({ key: 'Enter', shiftKey: true })).toBe(false);
    expect(isSendEnter({ key: 'a' })).toBe(false);
  });

  it('时间格式化兼容 T 分隔与空格分隔，非法值原样返回', () => {
    expect(formatDateTime('2026-11-05T12:30:00')).toBe('2026-11-05 12:30');
    expect(formatDateTime('2026-11-05 08:05:09')).toBe('2026-11-05 08:05');
    expect(formatDateTime('not-a-date')).toBe('not-a-date');
    expect(formatDateTime(undefined)).toBe('');
  });

  it('contextPayload 可解析时格式化，否则原样展示', () => {
    expect(formatContextPayload('{"orderId":"1"}')).toBe(
      JSON.stringify({ orderId: '1' }, null, 2),
    );
    expect(formatContextPayload('raw text')).toBe('raw text');
    expect(formatContextPayload(undefined)).toBe('');
  });
});

describe('会话列表时间与时间线分隔', () => {
  const now = new Date('2026-11-05T15:00:00');

  it('今天显示时分、昨天显示「昨天」、同年显示月日、跨年显示完整日期', () => {
    expect(conversationTimeLabel('2026-11-05 09:08:00', now)).toBe('09:08');
    expect(conversationTimeLabel('2026-11-04 23:59:00', now)).toBe('昨天');
    expect(conversationTimeLabel('2026-10-28 10:00:00', now)).toBe('10-28');
    expect(conversationTimeLabel('2025-12-31 10:00:00', now)).toBe(
      '2025-12-31',
    );
    expect(conversationTimeLabel(undefined, now)).toBe('');
    expect(conversationTimeLabel('not-a-date', now)).toBe('');
  });

  it('日期分隔今天/昨天/完整日期', () => {
    expect(formatDateDivider(new Date('2026-11-05T08:00:00'), now)).toBe(
      '今天',
    );
    expect(formatDateDivider(new Date('2026-11-04T08:00:00'), now)).toBe(
      '昨天',
    );
    expect(formatDateDivider(new Date('2026-10-01T08:00:00'), now)).toBe(
      '2026-10-01',
    );
  });

  it('跨天消息插入日期分隔，单日只有消息项', () => {
    const messages = [
      message({
        clientMessageId: 'a',
        createTime: '2026-11-04 23:00:00',
        id: 'm1',
      }),
      message({
        clientMessageId: 'b',
        createTime: '2026-11-05 09:00:00',
        id: 'm2',
      }),
      message({
        clientMessageId: 'c',
        createTime: '2026-11-05 09:01:00',
        id: 'm3',
      }),
    ];
    const items = buildTimelineItems(messages, { now });
    expect(items.map((item) => item.type)).toEqual([
      'date',
      'message',
      'date',
      'message',
      'message',
    ]);
    expect(items[0]).toMatchObject({ label: '昨天', type: 'date' });
    expect(items[2]).toMatchObject({ label: '今天', type: 'date' });

    const single = buildTimelineItems([messages[1] as ViewChatMessage], {
      now,
    });
    expect(single.filter((item) => item.type === 'date')).toHaveLength(1);
  });

  it('连续同人消息只标组首，间隔超过 5 分钟重新开组', () => {
    const messages = [
      message({
        clientMessageId: 'a',
        createTime: '2026-11-05 09:00:00',
        id: 'm1',
        senderId: 'staff-1',
        senderType: 'SYS_USER',
        seqNo: 1,
      }),
      message({
        clientMessageId: 'b',
        createTime: '2026-11-05 09:03:00',
        id: 'm2',
        senderId: 'staff-1',
        senderType: 'SYS_USER',
        seqNo: 2,
      }),
      message({
        clientMessageId: 'c',
        createTime: '2026-11-05 09:10:00',
        id: 'm3',
        senderId: 'staff-1',
        senderType: 'SYS_USER',
        seqNo: 3,
      }),
    ];
    const items = buildTimelineItems(messages, { now }) as Array<{
      groupStart?: boolean;
      type: string;
    }>;
    const messageItems = items.filter((item) => item.type === 'message');
    expect(messageItems.map((item) => item.groupStart)).toEqual([
      true,
      false,
      true,
    ]);
  });

  it('未读边界只插入一次，且位于第一条大于游标的消息前', () => {
    const messages = [
      message({ clientMessageId: 'a', id: 'm1', seqNo: 1 }),
      message({ clientMessageId: 'b', id: 'm2', seqNo: 2 }),
      message({ clientMessageId: 'c', id: 'm3', seqNo: 3 }),
    ];
    const items = buildTimelineItems(messages, { now, unreadAfterSeq: 1 });
    const types = items.map((item) => item.type);
    expect(types).toEqual(['date', 'message', 'unread', 'message', 'message']);
    expect(
      buildTimelineItems(messages, { now, unreadAfterSeq: 0 }).some(
        (item) => item.type === 'unread',
      ),
    ).toBe(false);
  });
});

describe('会话列表精细筛选参数（P2-A 契约 §2.2/§2.3）', () => {
  it('statuses 非空时覆盖页签 status，不下发两者', () => {
    const params = buildConversationInboxParams({
      filters: {
        ...createConversationFilterState(),
        statuses: ['WAITING', 'ASSIGNED'],
      },
      limit: 20,
      status: 'OPEN',
    });
    expect(params).toEqual({ limit: 20, statuses: 'WAITING,ASSIGNED' });
  });

  it('无精细筛选时保留页签 status', () => {
    expect(
      buildConversationInboxParams({
        filters: createConversationFilterState(),
        limit: 20,
        status: 'CLOSED',
      }),
    ).toEqual({ limit: 20, status: 'CLOSED' });
  });

  it('trim 后为空的 keyword/queueCode/assignedStaffId 不下发', () => {
    expect(
      buildConversationInboxParams({
        filters: {
          ...createConversationFilterState(),
          assignedStaffId: '  ',
          queueCode: ' ',
        },
        keyword: '   ',
        limit: 20,
      }),
    ).toEqual({ limit: 20 });
  });

  it('翻页原样携带 cursor 与同一组筛选参数', () => {
    expect(
      buildConversationInboxParams({
        cursor: ' cursor-2 ',
        filters: {
          assignedStaffId: 'staff-9',
          queueCode: 'DEFAULT',
          statuses: [],
          unreadOnly: true,
        },
        keyword: ' 王 ',
        limit: 20,
        status: 'OPEN',
      }),
    ).toEqual({
      assignedStaffId: 'staff-9',
      cursor: 'cursor-2',
      keyword: '王',
      limit: 20,
      queueCode: 'DEFAULT',
      status: 'OPEN',
      unreadOnly: true,
    });
  });
});

describe('会话筛选标签', () => {
  it('按包含条件生成标签，负责人优先展示昵称', () => {
    expect(
      conversationFilterTags({
        assignedStaffId: 'staff-9',
        assignedStaffName: '客服小美',
        queueCode: 'VIP',
        statuses: ['WAITING', 'CLOSED'],
        unreadOnly: true,
      }),
    ).toEqual([
      { key: 'unreadOnly', label: '仅未读' },
      { key: 'queueCode', label: '队列 VIP' },
      { key: 'assignedStaffId', label: '负责人 客服小美' },
      { key: 'statuses', label: '状态 待领取/已关闭' },
    ]);
  });

  it('负责人昵称缺失时回退 ID，空条件无标签', () => {
    expect(
      conversationFilterTags({
        ...createConversationFilterState(),
        assignedStaffId: 'staff-9',
      }),
    ).toEqual([{ key: 'assignedStaffId', label: '负责人 staff-9' }]);
    expect(conversationFilterTags(createConversationFilterState())).toEqual([]);
  });
});

describe('等待时长展示（P2-A 契约 §3.1）', () => {
  const base = '2026-11-06 10:00:00';

  it('按 mm:ss / HH:mm / Nd HH:mm 格式化', () => {
    expect(formatWaitingDuration(base, new Date('2026-11-06T10:00:35'))).toBe(
      '00:35',
    );
    expect(formatWaitingDuration(base, new Date('2026-11-06T10:03:12'))).toBe(
      '03:12',
    );
    expect(formatWaitingDuration(base, new Date('2026-11-06T11:05:00'))).toBe(
      '01:05',
    );
    expect(formatWaitingDuration(base, new Date('2026-11-08T13:04:00'))).toBe(
      '2天 03:04',
    );
  });

  it('缺失/非法时间返回空串，未来时间钳制为 00:00', () => {
    expect(formatWaitingDuration()).toBe('');
    expect(formatWaitingDuration('bad-value')).toBe('');
    expect(formatWaitingDuration(base, new Date('2026-11-06T09:59:00'))).toBe(
      '00:00',
    );
  });

  it('等待徽标文案带「等待」前缀，非等待态为空', () => {
    expect(waitingDurationLabel()).toBe('');
    expect(waitingDurationLabel(base, new Date('2026-11-06T10:02:05'))).toBe(
      '等待 02:05',
    );
  });
});

describe('消息引用（P2-A v2 契约 §15）', () => {
  it('仅已落库的 TEXT/IMAGE 可引用；撤回/系统/本地消息不可引用', () => {
    expect(
      canQuoteMessage(message({ id: 'm1', sendState: 'sent', seqNo: 3 })),
    ).toBe(true);
    expect(
      canQuoteMessage(
        message({
          id: 'm2',
          messageType: 'IMAGE',
          payload: '{}',
          sendState: 'sent',
          seqNo: 3,
        }),
      ),
    ).toBe(true);
    expect(
      canQuoteMessage(
        message({ id: 'm3', recallStatus: '1', sendState: 'sent', seqNo: 3 }),
      ),
    ).toBe(false);
    expect(
      canQuoteMessage(
        message({
          id: 'm4',
          messageType: 'SYSTEM',
          sendState: 'sent',
          seqNo: 4,
        }),
      ),
    ).toBe(false);
    expect(
      canQuoteMessage(
        message({
          id: 'local:x',
          sendState: 'sending',
          seqNo: Number.MAX_SAFE_INTEGER,
        }),
      ),
    ).toBe(false);
  });

  it('引用摘要与后端同口径：文本 trim + 截断 100，图片/卡片给 [标签]', () => {
    expect(
      buildQuoteSummary(
        message({ content: '  你好  ', id: 'm1', sendState: 'sent', seqNo: 1 }),
      ),
    ).toBe('你好');
    expect(
      buildQuoteSummary(
        message({
          id: 'm2',
          messageType: 'IMAGE',
          sendState: 'sent',
          seqNo: 2,
        }),
      ),
    ).toBe('[图片]');
    expect(
      buildQuoteSummary(
        message({
          id: 'm3',
          messageType: 'ORDER_CARD',
          sendState: 'sent',
          seqNo: 3,
        }),
      ),
    ).toBe('[订单]');
    const long = 'a'.repeat(120);
    expect(
      buildQuoteSummary(
        message({ content: long, id: 'm4', sendState: 'sent', seqNo: 4 }),
      ),
    ).toBe(`${'a'.repeat(100)}…`);
  });

  it('引用目标带发送者与摘要；不可引用消息返回 undefined', () => {
    const session = conversation({ customerName: '王小雅' });
    expect(
      buildQuoteTarget(
        message({
          content: '在吗',
          id: 'm1',
          sendState: 'sent',
          senderId: 'customer-1',
          senderName: undefined,
          senderType: 'MALL_USER',
          seqNo: 2,
        }),
        session,
      ),
    ).toEqual({
      id: 'm1',
      messageType: 'TEXT',
      senderName: '王小雅',
      summary: '在吗',
    });
    expect(buildQuoteTarget(message({ seqNo: 0 }))).toBeUndefined();
  });

  it('引用块：有 quotedMessageId 即渲染；quotedRecalled 显示占位文案', () => {
    expect(
      quotedBlockView(message({ sendState: 'sent', seqNo: 2 })),
    ).toBeUndefined();
    expect(
      quotedBlockView(
        message({
          id: 'm1',
          quotedMessageId: 'q1',
          quotedMessageSummary: '你好',
          quotedMessageType: 'TEXT',
          quotedSenderName: '王小雅',
          seqNo: 3,
          sendState: 'sent',
        }),
      ),
    ).toEqual({
      recalled: false,
      senderName: '王小雅',
      text: '你好',
      typeLabel: '文本',
    });
    expect(
      quotedBlockView(
        message({
          id: 'm2',
          quotedMessageId: 'q1',
          quotedRecalled: true,
          seqNo: 4,
          sendState: 'sent',
        }),
      ),
    ).toEqual({ recalled: true, text: '引用的消息已撤回' });
  });
});

describe('消息撤回（P2-A v2 契约 §16）', () => {
  const now = new Date('2026-11-06T12:02:00');

  function sentMessage(overrides: Partial<ViewChatMessage> = {}) {
    return message({
      createTime: '2026-11-06 12:00:00',
      id: 'm1',
      sendState: 'sent',
      seqNo: 5,
      ...overrides,
    });
  }

  it('本人在 2 分钟内可撤回 TEXT/IMAGE', () => {
    expect(canRecallMessage(sentMessage(), { now, userId: 'staff-1' })).toBe(
      true,
    );
    expect(
      canRecallMessage(sentMessage({ messageType: 'IMAGE', payload: '{}' }), {
        now,
        userId: 'staff-1',
      }),
    ).toBe(true);
  });

  it('超时/他人/非 TEXT-IMAGE/已撤回/会话关闭都不可撤回', () => {
    expect(
      canRecallMessage(sentMessage(), {
        now: new Date('2026-11-06T12:02:01'),
        userId: 'staff-1',
      }),
    ).toBe(false);
    expect(canRecallMessage(sentMessage(), { now, userId: 'staff-2' })).toBe(
      false,
    );
    expect(
      canRecallMessage(sentMessage({ messageType: 'ORDER_CARD' }), {
        now,
        userId: 'staff-1',
      }),
    ).toBe(false);
    expect(
      canRecallMessage(sentMessage({ recallStatus: '1' }), {
        now,
        userId: 'staff-1',
      }),
    ).toBe(false);
    expect(
      canRecallMessage(sentMessage(), {
        conversationStatus: 'CLOSED',
        now,
        userId: 'staff-1',
      }),
    ).toBe(false);
  });

  it('时间窗边界含 2 分钟；本地乐观消息不可撤回', () => {
    expect(canRecallMessage(sentMessage(), { now, userId: 'staff-1' })).toBe(
      true,
    );
    expect(
      canRecallMessage(
        message({ sendState: 'sending', seqNo: Number.MAX_SAFE_INTEGER }),
        { now, userId: 'staff-1' },
      ),
    ).toBe(false);
  });
});

describe('图片发送（P2-A v2 契约 §17）', () => {
  it('预校验：非空、≤5MB、仅 jpeg/png/webp（GIF 拒绝）', () => {
    expect(validateImageUpload({ size: 1024, type: 'image/png' })).toEqual({
      ok: true,
    });
    expect(validateImageUpload({ size: 0, type: 'image/png' }).error).toBe(
      '上传文件不能为空',
    );
    expect(
      validateImageUpload({ size: 6 * 1024 * 1024, type: 'image/png' }).error,
    ).toBe('图片大小不能超过 5MB');
    expect(validateImageUpload({ size: 1024, type: 'image/gif' }).error).toBe(
      '仅支持 JPG/JPEG/PNG/WEBP 图片',
    );
  });

  it('payload 组装：fileId=url，字段名与契约 §17.5 一致', () => {
    const payload = buildImageMessagePayload({
      contentType: 'image/png',
      fileId: '/upms/file/local/1/a.png',
      name: 'a.png',
      size: 183_245,
      url: '/upms/file/local/1/a.png',
    });
    expect(JSON.parse(payload)).toEqual({
      fileId: '/upms/file/local/1/a.png',
      mimeType: 'image/png',
      name: 'a.png',
      size: 183_245,
      url: '/upms/file/local/1/a.png',
    });
  });
});
