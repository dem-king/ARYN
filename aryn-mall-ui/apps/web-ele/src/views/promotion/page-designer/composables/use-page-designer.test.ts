import { describe, expect, it } from 'vitest';

import { createDefaultDecorationDocument } from '../schema/defaults';
import { usePageDesigner } from './use-page-designer';

describe('usePageDesigner commands', () => {
  it('adds, duplicates, moves, removes, and undoes components deterministically', () => {
    const ids = ['component-1', 'component-2'];
    const designer = usePageDesigner({
      idFactory: () => ids.shift() ?? 'fallback',
      initialDocument: createDefaultDecorationDocument(),
    });

    designer.addComponent({
      props: { text: 'Title' },
      type: 'title-text',
      version: 1,
    });
    designer.duplicateComponent('component-1');

    expect(designer.document.value.components.map(({ id }) => id)).toEqual([
      'component-1',
      'component-2',
    ]);
    expect(designer.document.value.components[1]?.props).toEqual({
      text: 'Title',
    });

    designer.moveComponent('component-2', 0);
    designer.removeComponent('component-1');
    expect(designer.document.value.components.map(({ id }) => id)).toEqual([
      'component-2',
    ]);

    designer.undo();
    expect(designer.document.value.components).toHaveLength(2);
  });

  it('groups component property input and preserves a single source document', () => {
    const designer = usePageDesigner({
      idFactory: () => 'component-1',
      initialDocument: createDefaultDecorationDocument(),
    });
    designer.addComponent({
      props: { text: '' },
      type: 'title-text',
      version: 1,
    });

    designer.patchComponent('component-1', { text: 'S' }, 'title-input');
    designer.patchComponent('component-1', { text: 'Summer' }, 'title-input');
    designer.undo();

    expect(designer.document.value.components[0]?.props).toEqual({ text: '' });
  });

  it('stores page settings inside the document and makes them undoable', () => {
    const designer = usePageDesigner({
      initialDocument: createDefaultDecorationDocument(),
    });

    designer.patchPage({ backgroundColor: '#101010' }, 'page-background');

    expect(designer.document.value.page.backgroundColor).toBe('#101010');
    designer.undo();
    expect(designer.document.value.page.backgroundColor).toBe('#f5f5f5');
  });
});
