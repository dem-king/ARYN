import type { ViewChatMessage } from '../message-state';

import type { Conversation } from '#/api/message/types';

import { describe, expect, it } from 'vitest';

import {
  claimState,
  composerState,
  conversationActions,
  conversationDisplayName,
  conversationOwnerName,
  conversationQuery,
  conversationStatusLabel,
  conversationStatusTone,
  formatContextPayload,
  formatDateTime,
  isLatestOutgoingMessage,
  isSendEnter,
  latestOutgoingMessage,
  readReceipt,
  shouldMarkRead,
  visibleServiceTabs,
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
