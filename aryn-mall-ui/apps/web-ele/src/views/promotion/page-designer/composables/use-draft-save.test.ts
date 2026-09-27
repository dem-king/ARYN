import { ref } from 'vue';

import { afterEach, describe, expect, it, vi } from 'vitest';

import { useDraftSave } from './use-draft-save';

describe('useDraftSave', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('debounces autosave and transitions from dirty to saved', async () => {
    vi.useFakeTimers();
    const revision = ref(2);
    const save = vi.fn().mockResolvedValue(3);
    const draft = useDraftSave({
      buildPayload: () => ({ title: 'Summer' }),
      delay: 500,
      revision,
      save,
    });

    draft.markDirty();
    draft.markDirty();
    expect(draft.status.value).toBe('dirty');

    await vi.advanceTimersByTimeAsync(500);

    expect(save).toHaveBeenCalledTimes(1);
    expect(revision.value).toBe(3);
    expect(draft.status.value).toBe('saved');
    expect(draft.isDirty.value).toBe(false);
  });

  it('manual save is immediate and never overlaps writes', async () => {
    vi.useFakeTimers();
    const revision = ref(1);
    let resolveFirst: (value: number) => void = () => {};
    const save = vi
      .fn()
      .mockImplementationOnce(
        () => new Promise<number>((resolve) => (resolveFirst = resolve)),
      )
      .mockResolvedValueOnce(3);
    const draft = useDraftSave({
      buildPayload: () => ({ revision: revision.value }),
      delay: 500,
      revision,
      save,
    });

    draft.markDirty();
    const first = draft.saveNow();
    draft.markDirty();
    const queued = draft.saveNow();

    expect(save).toHaveBeenCalledTimes(1);
    resolveFirst(2);
    await first;
    await queued;

    expect(save).toHaveBeenCalledTimes(2);
    expect(revision.value).toBe(3);
  });

  it('reports when a manual save had nothing to persist', async () => {
    vi.useFakeTimers();
    const revision = ref(1);
    const save = vi.fn().mockResolvedValue(2);
    const draft = useDraftSave({
      buildPayload: () => ({}),
      delay: 1800,
      revision,
      save,
    });

    draft.markDirty();
    await vi.advanceTimersByTimeAsync(1800);
    expect(save).toHaveBeenCalledTimes(1);

    // 自动保存已把改动写入后再点「保存草稿」：必须能分辨"未持久化"，
    // 否则按钮静默返回，用户看到的就是"点了没有任何反应"
    await expect(draft.saveNow()).resolves.toBe('skipped');
    expect(save).toHaveBeenCalledTimes(1);
  });

  it('reports a real manual save as persisted', async () => {
    const revision = ref(1);
    const save = vi.fn().mockResolvedValue(2);
    const draft = useDraftSave({
      buildPayload: () => ({}),
      revision,
      save,
    });

    draft.markDirty();

    await expect(draft.saveNow()).resolves.toBe('saved');
    expect(save).toHaveBeenCalledTimes(1);
    expect(revision.value).toBe(2);
  });

  it('detects a revision conflict from the real backend error shape', async () => {
    vi.useFakeTimers();
    const revision = ref(4);
    // 后端 ArynBusinessException 走 HTTP 200 + body.code=1，
    // 经由 defaultResponseInterceptor 抛出的错误对象形如 { response: { status: 200, data: {...} } }
    const conflict = Object.assign(new Error('业务失败'), {
      response: {
        data: { code: 1, msg: '草稿已被其他人修改，请重新加载' },
        status: 200,
      },
    });
    const save = vi.fn().mockRejectedValue(conflict);
    const draft = useDraftSave({
      buildPayload: () => ({}),
      revision,
      save,
    });

    draft.markDirty();
    await expect(draft.saveNow()).rejects.toThrow('业务失败');

    // 必须识别为冲突并保持 dirty，让用户能重新加载，而不是显示"保存失败"般含糊
    expect(draft.status.value).toBe('conflict');
    expect(draft.isDirty.value).toBe(true);
  });

  it('exposes stale revision conflicts and protects unsaved unloads', async () => {
    const revision = ref(4);
    const save = vi
      .fn()
      .mockRejectedValue(
        Object.assign(new Error('stale'), { code: 'DRAFT_CONFLICT' }),
      );
    const draft = useDraftSave({
      buildPayload: () => ({}),
      revision,
      save,
    });
    const event = {
      preventDefault: vi.fn(),
      returnValue: undefined as unknown,
    };

    draft.markDirty();
    expect(draft.handleBeforeUnload(event)).toBe('');
    expect(event.preventDefault).toHaveBeenCalled();

    await expect(draft.saveNow()).rejects.toThrow('stale');
    expect(draft.status.value).toBe('conflict');
    expect(draft.isDirty.value).toBe(true);
  });
});
