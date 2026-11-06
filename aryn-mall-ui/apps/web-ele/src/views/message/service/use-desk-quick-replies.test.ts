import { describe, expect, it, vi } from 'vitest';

import {
  createQuickReply,
  MAX_QUICK_REPLIES,
  MAX_QUICK_REPLY_CONTENT_LENGTH,
  MAX_QUICK_REPLY_TITLE_LENGTH,
  normalizeQuickReplyInput,
  parseQuickReplies,
  QUICK_REPLY_STORAGE_PREFIX,
  quickReplyStorageKey,
  useDeskQuickReplies,
} from './use-desk-quick-replies';

const DEFAULT_TITLES = ['欢迎语', '催发货', '物流查询', '退款指引', '结束语'];

function structuredEntry(index: number): Record<string, unknown> {
  return {
    content: `内容 ${index}`,
    id: `reply-${index}`,
    title: `标题 ${index}`,
  };
}

describe('parseQuickReplies', () => {
  it('坏 JSON、空值与非数组结构一律返回空数组', () => {
    expect(parseQuickReplies(null)).toEqual([]);
    expect(parseQuickReplies('')).toEqual([]);
    expect(parseQuickReplies('   ')).toEqual([]);
    expect(parseQuickReplies('not-json')).toEqual([]);
    expect(parseQuickReplies('42')).toEqual([]);
    expect(parseQuickReplies('"text"')).toEqual([]);
    expect(parseQuickReplies('{"id":"a","title":"t","content":"c"}')).toEqual(
      [],
    );
    expect(parseQuickReplies('[1,2,3]')).toEqual([]);
  });

  it('过滤非法项，trim 字段并保留合法数据', () => {
    const raw = JSON.stringify([
      { id: ' reply-1 ', title: ' 欢迎语 ', content: ' 您好，欢迎光临 ' },
      { id: 'reply-1', title: '重复 id', content: '重复' },
      null,
      42,
      'text',
      {},
      { id: '', title: 'x', content: 'y' },
      { id: 'reply-2', title: '   ', content: 'y' },
      { id: 'reply-3', title: 't', content: '   ' },
      { content: 'c', id: 3, title: 't' },
      { id: 'reply-4' },
    ]);
    expect(parseQuickReplies(raw)).toEqual([
      { content: '您好，欢迎光临', id: 'reply-1', title: '欢迎语' },
    ]);
  });

  it('最多保留 50 条', () => {
    const raw = JSON.stringify(
      Array.from({ length: 60 }, (_, index) => structuredEntry(index)),
    );
    const replies = parseQuickReplies(raw);
    expect(replies).toHaveLength(MAX_QUICK_REPLIES);
    expect(replies[0]?.id).toBe('reply-0');
    expect(replies[replies.length - 1]?.id).toBe('reply-49');
  });

  it('内容超过 2000 字时截断到上限', () => {
    const raw = JSON.stringify([
      { content: 'a'.repeat(2500), id: 'long', title: '长内容' },
    ]);
    const [reply] = parseQuickReplies(raw);
    expect(reply?.content).toHaveLength(MAX_QUICK_REPLY_CONTENT_LENGTH);
  });
});

describe('normalizeQuickReplyInput', () => {
  it('两端 trim 后返回标题和内容', () => {
    expect(normalizeQuickReplyInput('  催发货  ', '  请尽快发货  ')).toEqual({
      content: '请尽快发货',
      title: '催发货',
    });
  });

  it('标题或内容为空（含纯空白）时返回 null', () => {
    expect(normalizeQuickReplyInput('', '内容')).toBeNull();
    expect(normalizeQuickReplyInput('   ', '内容')).toBeNull();
    expect(normalizeQuickReplyInput('标题', '')).toBeNull();
    expect(normalizeQuickReplyInput('标题', '   ')).toBeNull();
  });

  it('内容超过上限返回 null，正好等于上限时通过', () => {
    expect(
      normalizeQuickReplyInput(
        '标题',
        'a'.repeat(MAX_QUICK_REPLY_CONTENT_LENGTH),
      ),
    ).not.toBeNull();
    expect(
      normalizeQuickReplyInput(
        '标题',
        'a'.repeat(MAX_QUICK_REPLY_CONTENT_LENGTH + 1),
      ),
    ).toBeNull();
  });

  it('标题超过上限返回 null，正好等于上限时通过', () => {
    expect(
      normalizeQuickReplyInput(
        '字'.repeat(MAX_QUICK_REPLY_TITLE_LENGTH),
        '内容',
      ),
    ).not.toBeNull();
    expect(
      normalizeQuickReplyInput(
        '字'.repeat(MAX_QUICK_REPLY_TITLE_LENGTH + 1),
        '内容',
      ),
    ).toBeNull();
  });
});

describe('createQuickReply', () => {
  it('创建带唯一 id 的合法常用语并 trim 字段', () => {
    const first = createQuickReply(' 催发货 ', ' 请尽快发货 ');
    const second = createQuickReply('催发货', '请尽快发货');
    expect(first).toMatchObject({ content: '请尽快发货', title: '催发货' });
    expect(first?.id).toBeTruthy();
    expect(second?.id).toBeTruthy();
    expect(second?.id).not.toBe(first?.id);
  });

  it('空值或超长输入返回 null', () => {
    expect(createQuickReply('', '内容')).toBeNull();
    expect(createQuickReply('标题', '   ')).toBeNull();
    expect(
      createQuickReply('标题', 'a'.repeat(MAX_QUICK_REPLY_CONTENT_LENGTH + 1)),
    ).toBeNull();
    expect(
      createQuickReply('字'.repeat(MAX_QUICK_REPLY_TITLE_LENGTH + 1), '内容'),
    ).toBeNull();
  });
});

describe('useDeskQuickReplies', () => {
  it('首次使用返回内置默认常用语，且不主动写入 localStorage', () => {
    localStorage.clear();
    const quick = useDeskQuickReplies('staff-defaults');
    expect(quick.replies.value.map((reply) => reply.title)).toEqual(
      DEFAULT_TITLES,
    );
    expect(quick.storageKey.value).toBe(
      `${QUICK_REPLY_STORAGE_PREFIX}:staff-defaults`,
    );
    expect(quick.storageKey.value).toBe(quickReplyStorageKey('staff-defaults'));
    expect(localStorage.getItem(quick.storageKey.value)).toBeNull();
  });

  it('新增/删除/重置会同步写入 localStorage', () => {
    localStorage.clear();
    const quick = useDeskQuickReplies('staff-sync');
    const created = quick.add(' 催发货 ', ' 请尽快发货 ');
    expect(created).not.toBeNull();
    expect(quick.replies.value).toHaveLength(DEFAULT_TITLES.length + 1);
    const stored = localStorage.getItem(quickReplyStorageKey('staff-sync'));
    expect(parseQuickReplies(stored ?? null)).toContainEqual(
      expect.objectContaining({ content: '请尽快发货', title: '催发货' }),
    );

    expect(quick.remove('default-welcome')).toBe(true);
    expect(quick.remove('not-exists')).toBe(false);
    expect(quick.replies.value).toHaveLength(DEFAULT_TITLES.length);

    quick.reset();
    expect(quick.replies.value.map((reply) => reply.title)).toEqual(
      DEFAULT_TITLES,
    );
  });

  it('同一坐席的多个实例共享同一份状态', () => {
    localStorage.clear();
    const first = useDeskQuickReplies('staff-shared');
    const second = useDeskQuickReplies('staff-shared');
    first.add('共享', '两个实例都能看到');
    expect(second.replies.value.some((reply) => reply.title === '共享')).toBe(
      true,
    );
  });

  it('存储不可用时静默降级为内存态', () => {
    vi.stubGlobal('localStorage', {
      clear() {},
      getItem() {
        throw new Error('storage disabled');
      },
      removeItem() {},
      setItem() {
        throw new Error('storage disabled');
      },
    });
    try {
      const quick = useDeskQuickReplies('staff-no-storage');
      expect(quick.replies.value).toHaveLength(DEFAULT_TITLES.length);
      expect(quick.add('离线', '存储不可用也能记在内存里')).not.toBeNull();
      expect(quick.replies.value).toHaveLength(DEFAULT_TITLES.length + 1);
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
