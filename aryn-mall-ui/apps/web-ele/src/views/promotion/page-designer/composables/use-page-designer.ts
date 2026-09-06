import type { ComputedRef, Ref } from 'vue';

import type {
  DecorationComponent,
  DecorationDocument,
  DecorationSection,
  PageSettings,
  SectionStyle,
} from '../schema/types';

import { computed, ref } from 'vue';

import { nanoid } from 'nanoid';

import { cloneDesignerValue } from '../schema/clone';
import { createDefaultSectionStyle } from '../schema/defaults';
import { useCommandHistory } from './use-command-history';

type NewComponent = Omit<DecorationComponent, 'id'>;

export interface UsePageDesignerOptions {
  idFactory?: () => string;
  initialDocument: DecorationDocument;
}

export interface PageDesignerState {
  /** 新增组件；不传区块时加入当前选中区块，返回组件 ID */
  addComponent: (
    component: NewComponent,
    atIndex?: number,
    sectionId?: string,
  ) => string;
  addSection: (afterSectionId?: string) => string;
  activeSectionId: Ref<string | undefined>;
  canRedo: ComputedRef<boolean>;
  canUndo: ComputedRef<boolean>;
  document: Ref<DecorationDocument>;
  duplicateComponent: (id: string) => string | undefined;
  duplicateSection: (sectionId: string) => string | undefined;
  duplicateComponents: (ids: string[]) => string[];
  flatComponents: ComputedRef<DecorationComponent[]>;
  moveComponent: (id: string, toIndex: number, toSectionId?: string) => void;
  moveSection: (sectionId: string, toIndex: number) => void;
  patchComponent: (
    id: string,
    patch: Record<string, unknown>,
    groupKey?: string,
  ) => void;
  patchPage: (patch: Partial<PageSettings>, groupKey?: string) => void;
  patchSection: (
    sectionId: string,
    patch: Partial<Omit<DecorationSection, 'components' | 'id' | 'style'>> & {
      style?: Partial<SectionStyle>;
    },
    groupKey?: string,
  ) => void;
  redo: () => void;
  removeComponent: (id: string) => void;
  removeComponents: (ids: string[]) => void;
  removeSection: (sectionId: string) => void;
  reset: (document: DecorationDocument) => void;
  sectionOf: (componentId: string) => DecorationSection | undefined;
  selectedId: Ref<string | undefined>;
  selectComponent: (id?: string) => void;
  selectSection: (sectionId?: string) => void;
  undo: () => void;
}

function cloneValue<T>(value: T): T {
  return cloneDesignerValue(value);
}

export function usePageDesigner(
  options: UsePageDesignerOptions,
): PageDesignerState {
  const idFactory = options.idFactory ?? nanoid;
  const history = useCommandHistory(options.initialDocument, {
    clone: cloneValue,
    limit: 50,
  });
  const selectedId = ref<string>();
  const activeSectionId = ref<string>();

  function mutate(
    update: (document: DecorationDocument) => void,
    groupKey?: string,
  ) {
    const next = cloneValue(history.state.value);
    update(next);
    history.commit(next, { groupKey });
  }

  function findSection(
    document: DecorationDocument,
    sectionId?: string,
  ): DecorationSection | undefined {
    if (sectionId) {
      return document.sections.find((section) => section.id === sectionId);
    }
    return document.sections[0];
  }

  function sectionOf(componentId: string): DecorationSection | undefined {
    return history.state.value.sections.find((section) =>
      section.components.some((component) => component.id === componentId),
    );
  }

  function targetSection(sectionId?: string): DecorationSection | undefined {
    const document = history.state.value;
    return (
      findSection(document, sectionId) ??
      document.sections.find(
        (section) => section.id === activeSectionId.value,
      ) ??
      document.sections[0]
    );
  }

  function addComponent(
    component: NewComponent,
    atIndex?: number,
    sectionId?: string,
  ) {
    const id = idFactory();
    mutate((document) => {
      const section = findSection(document, sectionId) ?? document.sections[0];
      if (!section) return;
      const next = { ...cloneValue(component), id };
      const index = Math.min(
        Math.max(atIndex ?? section.components.length, 0),
        section.components.length,
      );
      section.components.splice(index, 0, next);
    });
    activeSectionId.value = sectionId ?? targetSection(sectionId)?.id;
    selectedId.value = id;
    return id;
  }

  function duplicateComponent(id: string) {
    const duplicateId = idFactory();
    mutate((document) => {
      const section = document.sections.find((item) =>
        item.components.some((component) => component.id === id),
      );
      if (!section) return;
      const sourceIndex = section.components.findIndex(
        (component) => component.id === id,
      );
      const source = section.components[sourceIndex];
      if (!source) return;
      section.components.splice(sourceIndex + 1, 0, {
        ...cloneValue(source),
        id: duplicateId,
      });
    });
    selectedId.value = duplicateId;
    return duplicateId;
  }

  function duplicateComponents(ids: string[]) {
    const duplicated: string[] = [];
    mutate((document) => {
      for (const id of ids) {
        const section = document.sections.find((item) =>
          item.components.some((component) => component.id === id),
        );
        if (!section) continue;
        const sourceIndex = section.components.findIndex(
          (component) => component.id === id,
        );
        const source =
          sourceIndex === -1 ? undefined : section.components[sourceIndex];
        if (!source) continue;
        const duplicateId = idFactory();
        section.components.splice(sourceIndex + 1, 0, {
          ...cloneValue(source),
          id: duplicateId,
        });
        duplicated.push(duplicateId);
      }
    });
    return duplicated;
  }

  function moveComponent(id: string, toIndex: number, toSectionId?: string) {
    mutate((document) => {
      const source = document.sections.find((item) =>
        item.components.some((component) => component.id === id),
      );
      if (!source) return;
      const fromIndex = source.components.findIndex(
        (component) => component.id === id,
      );
      const [component] = source.components.splice(fromIndex, 1);
      if (!component) return;
      const target =
        (toSectionId
          ? document.sections.find((item) => item.id === toSectionId)
          : source) ?? source;
      const insertIndex = Math.min(
        Math.max(toIndex, 0),
        target.components.length,
      );
      target.components.splice(insertIndex, 0, component);
    });
  }

  function removeComponent(id: string) {
    removeComponents([id]);
  }

  function removeComponents(ids: string[]) {
    if (ids.length === 0) return;
    mutate((document) => {
      const idSet = new Set(ids);
      for (const section of document.sections) {
        section.components = section.components.filter(
          (component) => !idSet.has(component.id),
        );
      }
    });
    if (selectedId.value && ids.includes(selectedId.value)) {
      selectedId.value = undefined;
    }
  }

  function patchComponent(
    id: string,
    patch: Record<string, unknown>,
    groupKey?: string,
  ) {
    mutate((document) => {
      for (const section of document.sections) {
        const component = section.components.find((item) => item.id === id);
        if (component) {
          component.props = { ...component.props, ...patch };
          return;
        }
      }
    }, groupKey);
  }

  function patchPage(patch: Partial<PageSettings>, groupKey?: string) {
    mutate((document) => {
      document.page = { ...document.page, ...patch };
    }, groupKey);
  }

  function addSection(afterSectionId?: string) {
    const id = idFactory();
    mutate((document) => {
      const section: DecorationSection = {
        components: [],
        id,
        style: createDefaultSectionStyle(),
        type: 'default',
      };
      const afterIndex = afterSectionId
        ? document.sections.findIndex((item) => item.id === afterSectionId)
        : document.sections.length - 1;
      const insertIndex =
        afterIndex >= 0 ? afterIndex + 1 : document.sections.length;
      document.sections.splice(insertIndex, 0, section);
    });
    activeSectionId.value = id;
    selectedId.value = undefined;
    return id;
  }

  function removeSection(sectionId: string) {
    mutate((document) => {
      if (document.sections.length <= 1) return;
      document.sections = document.sections.filter(
        (section) => section.id !== sectionId,
      );
    });
    if (activeSectionId.value === sectionId) {
      activeSectionId.value = history.state.value.sections[0]?.id;
    }
    selectedId.value = undefined;
  }

  function duplicateSection(sectionId: string) {
    const duplicateId = idFactory();
    mutate((document) => {
      const sourceIndex = document.sections.findIndex(
        (section) => section.id === sectionId,
      );
      const source = document.sections[sourceIndex];
      if (!source) return;
      document.sections.splice(sourceIndex + 1, 0, {
        ...cloneValue(source),
        components: source.components.map((component) => ({
          ...cloneValue(component),
          id: idFactory(),
        })),
        id: duplicateId,
      });
    });
    activeSectionId.value = duplicateId;
    return duplicateId;
  }

  function moveSection(sectionId: string, toIndex: number) {
    mutate((document) => {
      const fromIndex = document.sections.findIndex(
        (section) => section.id === sectionId,
      );
      if (fromIndex === -1) return;
      const [section] = document.sections.splice(fromIndex, 1);
      if (!section) return;
      const target = Math.min(Math.max(toIndex, 0), document.sections.length);
      document.sections.splice(target, 0, section);
    });
  }

  function patchSection(
    sectionId: string,
    patch: Partial<Omit<DecorationSection, 'components' | 'id' | 'style'>> & {
      style?: Partial<SectionStyle>;
    },
    groupKey?: string,
  ) {
    mutate((document) => {
      const section = document.sections.find((item) => item.id === sectionId);
      if (!section) return;
      const { style, ...rest } = patch;
      Object.assign(section, rest);
      if (style) {
        section.style = { ...section.style, ...style };
      }
    }, groupKey);
  }

  function selectComponent(id?: string) {
    selectedId.value = id;
    if (!id) return;
    const section = sectionOf(id);
    if (section) activeSectionId.value = section.id;
  }

  function selectSection(sectionId?: string) {
    activeSectionId.value = sectionId;
  }

  function reset(document: DecorationDocument) {
    history.reset(document);
    selectedId.value = undefined;
    activeSectionId.value = document.sections[0]?.id;
  }

  return {
    addComponent,
    addSection,
    activeSectionId,
    canRedo: computed(() => history.canRedo.value),
    canUndo: computed(() => history.canUndo.value),
    document: history.state,
    duplicateComponent,
    duplicateComponents,
    duplicateSection,
    flatComponents: computed(() =>
      history.state.value.sections.flatMap((section) => section.components),
    ),
    moveComponent,
    moveSection,
    patchComponent,
    patchPage,
    patchSection,
    redo: history.redo,
    removeComponent,
    removeComponents,
    removeSection,
    reset,
    sectionOf,
    selectedId,
    selectComponent,
    selectSection,
    undo: history.undo,
  };
}
