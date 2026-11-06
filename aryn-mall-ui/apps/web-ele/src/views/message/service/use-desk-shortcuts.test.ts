import { describe, expect, it } from 'vitest';

import { matchDeskShortcut } from './use-desk-shortcuts';

const plain = { key: '' };

describe('desk shortcut matcher', () => {
  it('non-typing keys: search / help / escape', () => {
    expect(matchDeskShortcut({ key: '/' }, false)).toBe('focus-search');
    expect(matchDeskShortcut({ key: '?' }, false)).toBe('open-help');
    expect(matchDeskShortcut({ key: 'Escape' }, false)).toBe('close-layer');
    expect(matchDeskShortcut({ key: '/' }, true)).toBeNull();
    expect(matchDeskShortcut({ key: '?' }, true)).toBeNull();
  });

  it('escape still works while typing (close popovers/clear search)', () => {
    expect(matchDeskShortcut({ key: 'Escape' }, true)).toBe('close-layer');
  });

  it('alt navigation shortcuts', () => {
    expect(matchDeskShortcut({ altKey: true, key: 'ArrowUp' }, false)).toBe(
      'prev-conversation',
    );
    expect(matchDeskShortcut({ altKey: true, key: 'ArrowDown' }, false)).toBe(
      'next-conversation',
    );
    expect(matchDeskShortcut({ altKey: true, key: 'k' }, false)).toBe(
      'focus-search',
    );
    expect(matchDeskShortcut({ altKey: true, key: 'N' }, true)).toBe(
      'next-unread',
    );
    expect(
      matchDeskShortcut({ altKey: true, key: 'p', shiftKey: true }, false),
    ).toBe('open-window');
  });

  it('does not steal browser/global shortcuts with ctrl or meta', () => {
    expect(matchDeskShortcut({ ctrlKey: true, key: 'k' }, false)).toBeNull();
    expect(matchDeskShortcut({ key: 'k', metaKey: true }, false)).toBeNull();
    expect(
      matchDeskShortcut({ altKey: true, ctrlKey: true, key: 'ArrowUp' }, false),
    ).toBeNull();
  });

  it('ignores IME composition and unknown keys', () => {
    expect(
      matchDeskShortcut({ isComposing: true, key: 'Enter' }, false),
    ).toBeNull();
    expect(matchDeskShortcut({ key: 'Enter' }, false)).toBeNull();
    expect(matchDeskShortcut({ ...plain, key: 'a' }, false)).toBeNull();
    expect(matchDeskShortcut({ altKey: true, key: 'x' }, false)).toBeNull();
  });
});
