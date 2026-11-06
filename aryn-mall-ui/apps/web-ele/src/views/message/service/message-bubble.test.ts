import type { ViewChatMessage } from '../message-state';

import { mount } from '@vue/test-utils';

import { describe, expect, it } from 'vitest';

import MessageBubble from './message-bubble.vue';

/**
 * 气泡引用/撤回交互契约（P2-A v2 §15/§16）。
 *
 * 这里只锁渲染与事件：接口调用/二次确认在页面消息域（use-desk-messages），
 * 纯展示组件必须做到「已撤回不泄露原内容」「按钮只在规则内出现」。
 */
function message(overrides: Partial<ViewChatMessage> = {}): ViewChatMessage {
  return {
    clientMessageId: 'client-1',
    content: '你好',
    conversationId: 'conversation-1',
    createTime: '2026-11-06 12:00:00',
    id: 'm1',
    messageType: 'TEXT',
    senderId: 'staff-1',
    senderName: '客服小美',
    senderType: 'SYS_USER',
    sendState: 'sent',
    seqNo: 5,
    ...overrides,
  };
}

const baseProps = {
  groupStart: true,
  isLatestOutgoing: false,
  receipt: 'none' as const,
  receiptLabel: '',
};

function mountBubble(overrides: Partial<ViewChatMessage> = {}, extra = {}) {
  return mount(MessageBubble, {
    props: { ...baseProps, message: message(overrides), ...extra },
  });
}

function findButton(wrapper: ReturnType<typeof mountBubble>, label: string) {
  return wrapper
    .findAll('button')
    .find((button) => button.text().trim() === label);
}

describe('message bubble recall/quote', () => {
  it('已撤回消息只渲染占位，不展示原内容', () => {
    const wrapper = mountBubble({
      content: '不能泄露的原文',
      recallStatus: '1',
    });
    expect(wrapper.find('.recalled-placeholder').text()).toBe('消息已撤回');
    expect(wrapper.text()).not.toContain('不能泄露的原文');
    expect(findButton(wrapper, '撤回')).toBeUndefined();
    wrapper.unmount();
  });

  it('本人在 2 分钟内显示撤回按钮并回传事件', async () => {
    const wrapper = mountBubble(
      {},
      {
        currentUserId: 'staff-1',
        now: new Date('2026-11-06T12:01:00').getTime(),
      },
    );
    const recall = findButton(wrapper, '撤回');
    expect(recall).toBeDefined();
    await recall?.trigger('click');
    expect(wrapper.emitted('recall')).toHaveLength(1);
    wrapper.unmount();
  });

  it('他人消息或超过 2 分钟不显示撤回', () => {
    const notMine = mountBubble(
      {},
      {
        currentUserId: 'staff-2',
        now: new Date('2026-11-06T12:01:00').getTime(),
      },
    );
    expect(findButton(notMine, '撤回')).toBeUndefined();
    notMine.unmount();

    const expired = mountBubble(
      {},
      {
        currentUserId: 'staff-1',
        now: new Date('2026-11-06T12:03:00').getTime(),
      },
    );
    expect(findButton(expired, '撤回')).toBeUndefined();
    expired.unmount();
  });

  it('已撤回消息不显示引用/复制/撤回入口', () => {
    const wrapper = mountBubble({ recallStatus: '1' });
    expect(findButton(wrapper, '引用')).toBeUndefined();
    expect(findButton(wrapper, '复制')).toBeUndefined();
    expect(findButton(wrapper, '撤回')).toBeUndefined();
    wrapper.unmount();
  });

  it('引用块渲染发送者+摘要，点击回传被引用消息 ID', async () => {
    const wrapper = mountBubble({
      quotedMessageId: 'quoted-9',
      quotedMessageSummary: '麻烦看下物流',
      quotedMessageType: 'TEXT',
      quotedSenderName: '会员小雅',
    });
    const block = wrapper.find('.quote-block');
    expect(block.text()).toContain('会员小雅');
    expect(block.text()).toContain('麻烦看下物流');
    await block.trigger('click');
    expect(wrapper.emitted('locateQuoted')).toEqual([['quoted-9']]);
    wrapper.unmount();
  });

  it('被引用消息已撤回时只显示占位，不展示摘要', () => {
    const wrapper = mountBubble({
      quotedMessageId: 'quoted-9',
      quotedRecalled: true,
      quotedMessageSummary: '不能泄露的摘要',
    });
    expect(wrapper.find('.quote-block.recalled').text()).toBe(
      '引用的消息已撤回',
    );
    expect(wrapper.text()).not.toContain('不能泄露的摘要');
    wrapper.unmount();
  });

  it('引用按钮只在可引用消息上出现并回传事件', async () => {
    const wrapper = mountBubble();
    const quote = findButton(wrapper, '引用');
    expect(quote).toBeDefined();
    await quote?.trigger('click');
    expect(wrapper.emitted('quote')).toHaveLength(1);
    wrapper.unmount();

    const recalled = mountBubble({ recallStatus: '1' });
    expect(findButton(recalled, '引用')).toBeUndefined();
    recalled.unmount();
  });
});
