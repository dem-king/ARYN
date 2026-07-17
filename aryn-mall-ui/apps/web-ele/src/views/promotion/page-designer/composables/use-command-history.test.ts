import { describe, expect, it } from 'vitest';

import { useCommandHistory } from './use-command-history';

describe('useCommandHistory', () => {
  it('supports undo, redo, and invalidates redo after a new commit', () => {
    const history = useCommandHistory({ title: 'A' });

    history.commit({ title: 'B' });
    history.commit({ title: 'C' });
    history.undo();

    expect(history.state.value.title).toBe('B');
    expect(history.canRedo.value).toBe(true);

    history.commit({ title: 'D' });

    expect(history.state.value.title).toBe('D');
    expect(history.canRedo.value).toBe(false);
  });

  it('groups consecutive input updates into one undo step', () => {
    const history = useCommandHistory({ title: '' });

    history.commit({ title: 'S' }, { groupKey: 'page-title' });
    history.commit({ title: 'Su' }, { groupKey: 'page-title' });
    history.commit({ title: 'Summer' }, { groupKey: 'page-title' });
    history.undo();

    expect(history.state.value.title).toBe('');
  });

  it('keeps only the latest 50 undo steps', () => {
    const history = useCommandHistory({ count: 0 }, { limit: 50 });

    for (let count = 1; count <= 60; count += 1) {
      history.commit({ count });
    }
    for (let count = 0; count < 50; count += 1) history.undo();

    expect(history.state.value.count).toBe(10);
    expect(history.canUndo.value).toBe(false);
  });
});
