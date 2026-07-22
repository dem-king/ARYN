import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import { useDictStore } from './dict';

describe('dict store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('rejects empty keys', () => {
    const store = useDictStore();

    expect(store.getDict('')).toBeNull();
    expect(store.setDict('', ['ignored'])).toBe(false);
    expect(store.dict).toEqual([]);
  });

  it('reports whether a cached dictionary was removed', () => {
    const store = useDictStore();
    store.setDict('status', ['enabled']);

    expect(store.removeDict('status')).toBe(true);
    expect(store.removeDict('status')).toBe(false);
  });

  it('updates an existing dictionary instead of retaining stale duplicates', () => {
    const store = useDictStore();
    store.setDict('status', ['old']);
    store.setDict('status', ['new']);

    expect(store.getDict('status')).toEqual(['new']);
    expect(store.dict).toHaveLength(1);
  });
});
