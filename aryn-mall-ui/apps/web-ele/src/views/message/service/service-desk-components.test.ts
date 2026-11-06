import type { Ref } from 'vue';

import type { ViewChatMessage } from '../message-state';

import { mount } from '@vue/test-utils';
import { defineComponent, h, nextTick, ref, watch } from 'vue';

import { ElLoading } from 'element-plus';
import { describe, expect, it } from 'vitest';

import Composer from './composer.vue';
import MessageTimeline from './message-timeline.vue';

/**
 * 拆分后的展示组件接线契约。
 *
 * <p>两个最容易在重构中静默坏掉的点：
 * - 时间线滚动元素由 MessageTimeline 内部持有，页面必须能通过
 *   `defineExpose({ scroller })` 在 `nextTick` 之后拿到它（scrollToBottom/上翻锚点依赖）；
 * - 发送按钮门禁从页面搬进 Composer 后，禁用条件必须仍是
 *   `composer.disabled || !draft.trim() || messageLoading`。</p>
 */
function textMessage(): ViewChatMessage {
  return {
    clientMessageId: 'client-1',
    content: '你好',
    conversationId: 'conversation-1',
    createTime: '2026-11-06T10:00:00',
    id: 'message-1',
    messageType: 'TEXT',
    seqNo: 1,
    senderId: 'customer-1',
    senderName: '会员',
    senderType: 'MALL_USER',
  };
}

const timelineProps = {
  hasOlder: false,
  messageLoadFailed: false,
  messageLoading: false,
  messages: [textMessage()],
  olderLoading: false,
  receipt: 'none' as const,
  receiptLabel: '',
  stickToBottom: true,
  timelineItems: [
    {
      groupStart: true,
      key: 'message-1',
      message: textMessage(),
      type: 'message' as const,
    },
  ],
  unreadWhileAway: 0,
};

function mountTimeline() {
  return mount(MessageTimeline, {
    global: { directives: { loading: ElLoading.directive } },
    props: timelineProps,
  });
}

function sendButton(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAll('button')
    .find((button) => button.text().includes('发送回复'));
}

describe('message timeline scroller wiring', () => {
  it('exposes the .timeline element to the page', () => {
    const wrapper = mountTimeline();
    const scroller = (wrapper.vm as unknown as { scroller?: HTMLElement })
      .scroller;
    expect(scroller).toBeInstanceOf(HTMLElement);
    expect(scroller?.classList.contains('timeline')).toBe(true);
    wrapper.unmount();
  });

  it('binds scroller through the page post-flush watch before nextTick resolves', async () => {
    let timeline!: Ref<HTMLElement | undefined>;
    let open!: () => Promise<HTMLElement | undefined>;

    const Host = defineComponent({
      setup() {
        const show = ref(false);
        const timelineRef = ref<InstanceType<typeof MessageTimeline>>();
        timeline = ref<HTMLElement>();
        watch(
          () => timelineRef.value?.scroller,
          (scroller) => {
            timeline.value = scroller;
          },
          { flush: 'post' },
        );
        // 复刻 openConversation：先切 active 挂载时间线，await nextTick 后滚动到底
        open = async () => {
          show.value = true;
          await nextTick();
          return timeline.value;
        };
        return () =>
          h('div', [
            show.value
              ? h(MessageTimeline, { ...timelineProps, ref: timelineRef })
              : null,
          ]);
      },
    });

    const wrapper = mount(Host, {
      global: { directives: { loading: ElLoading.directive } },
    });
    const scroller = await open();
    expect(scroller).toBeInstanceOf(HTMLElement);
    expect(scroller?.classList.contains('timeline')).toBe(true);
    wrapper.unmount();
  });
});

const composerProps = {
  composer: { disabled: false, hint: '', placeholder: '输入回复' },
  composerNote: 'HTTP 可靠发送',
  draft: '你好',
  messageLoading: false,
};

describe('composer send gate', () => {
  it('keeps the send button enabled for a replyable conversation with draft', () => {
    const wrapper = mount(Composer, { props: composerProps });
    expect(sendButton(wrapper)?.attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });

  it.each([
    [
      'read-only conversation',
      { composer: { ...composerProps.composer, disabled: true } },
    ],
    ['blank draft', { draft: '   ' }],
    ['message loading', { messageLoading: true }],
  ])('disables send for %s', (_label, overrides) => {
    const wrapper = mount(Composer, {
      props: { ...composerProps, ...overrides },
    });
    expect(sendButton(wrapper)?.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('emits send when clicked', async () => {
    const wrapper = mount(Composer, { props: composerProps });
    await sendButton(wrapper)?.trigger('click');
    expect(wrapper.emitted('send')).toHaveLength(1);
    wrapper.unmount();
  });
});
