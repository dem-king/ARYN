import { describe, expect, it } from 'vitest';

import { createDefaultDecorationDocument } from '../schema/defaults';
import { usePageDesigner } from './use-page-designer';

describe('usePageDesigner commands', () => {
  it('adds, duplicates, moves, removes, and undoes components within sections', () => {
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

    const components = designer.document.value.sections[0]?.components ?? [];
    expect(components.map(({ id }) => id)).toEqual([
      'component-1',
      'component-2',
    ]);
    expect(components[1]?.props).toEqual({ text: 'Title' });

    designer.moveComponent('component-2', 0);
    designer.removeComponent('component-1');
    expect(
      (designer.document.value.sections[0]?.components ?? []).map(
        ({ id }) => id,
      ),
    ).toEqual(['component-2']);

    designer.undo();
    expect(designer.flatComponents.value).toHaveLength(2);
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

    expect(designer.flatComponents.value[0]?.props).toEqual({ text: '' });
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

describe('usePageDesigner sections', () => {
  it('adds, patches, moves and removes sections with undo support', () => {
    const ids = ['section-1', 'section-2'];
    const designer = usePageDesigner({
      idFactory: () => ids.shift() ?? 'fallback-section',
      initialDocument: createDefaultDecorationDocument(),
    });

    const secondId = designer.addSection();
    expect(designer.document.value.sections).toHaveLength(2);
    expect(designer.activeSectionId.value).toBe(secondId);

    designer.patchSection(
      secondId,
      { name: '秒杀区', style: { horizontalScroll: true, paddingY: 12 } },
      'section-style',
    );
    const second = designer.document.value.sections[1];
    expect(second?.name).toBe('秒杀区');
    expect(second?.style.horizontalScroll).toBe(true);
    expect(second?.style.paddingY).toBe(12);

    designer.moveSection(secondId, 0);
    expect(designer.document.value.sections[0]?.id).toBe(secondId);

    designer.undo();
    expect(designer.document.value.sections[0]?.id).toBe('section-root');

    designer.removeSection('section-root');
    expect(designer.document.value.sections.map(({ id }) => id)).toEqual([
      secondId,
    ]);

    designer.removeSection(secondId);
    // 至少保留一个区块
    expect(designer.document.value.sections).toHaveLength(1);
  });

  it('duplicates a section with fresh component and section ids', () => {
    let sequence = 0;
    const designer = usePageDesigner({
      idFactory: () => `id-${(sequence += 1)}`,
      initialDocument: createDefaultDecorationDocument(),
    });
    designer.addComponent({
      props: {},
      type: 'gap',
      version: 1,
    });

    const duplicateId = designer.duplicateSection('section-root');

    // 区块 ID 先分配，区块内组件克隆使用后续 ID
    expect(duplicateId).toBe('id-2');
    expect(designer.document.value.sections).toHaveLength(2);
    const cloned = designer.document.value.sections[1];
    expect(cloned?.components).toHaveLength(1);
    expect(cloned?.components[0]?.id).not.toBe(
      designer.document.value.sections[0]?.components[0]?.id,
    );
  });

  it('moves components across sections and reports their owning section', () => {
    let sequence = 0;
    const designer = usePageDesigner({
      idFactory: () => `id-${(sequence += 1)}`,
      initialDocument: createDefaultDecorationDocument(),
    });
    designer.addComponent({ props: {}, type: 'gap', version: 1 });
    const secondId = designer.addSection();

    designer.moveComponent('id-1', 0, secondId);

    expect(designer.document.value.sections[0]?.components).toHaveLength(0);
    expect(designer.document.value.sections[1]?.components).toHaveLength(1);
    expect(designer.sectionOf('id-1')?.id).toBe(secondId);
  });

  it('supports batch duplicate and removal', () => {
    let sequence = 0;
    const designer = usePageDesigner({
      idFactory: () => `id-${(sequence += 1)}`,
      initialDocument: createDefaultDecorationDocument(),
    });
    designer.addComponent({ props: {}, type: 'gap', version: 1 });
    designer.addComponent({ props: {}, type: 'notice', version: 1 });

    const duplicated = designer.duplicateComponents(['id-1', 'id-2']);
    expect(duplicated).toHaveLength(2);
    expect(designer.flatComponents.value).toHaveLength(4);

    designer.removeComponents(['id-1', 'id-2']);
    expect(designer.flatComponents.value.map(({ id }) => id)).toEqual(
      duplicated,
    );
  });
});
