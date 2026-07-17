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
