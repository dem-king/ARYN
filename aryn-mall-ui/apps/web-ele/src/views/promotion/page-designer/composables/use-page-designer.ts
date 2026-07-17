import type { ComputedRef, Ref } from 'vue';

import type {
  DecorationComponent,
  DecorationDocument,
  PageSettings,
} from '../schema/types';

import { computed, ref } from 'vue';

import { nanoid } from 'nanoid';

import { cloneDesignerValue } from '../schema/clone';
import { useCommandHistory } from './use-command-history';

type NewComponent = Omit<DecorationComponent, 'id'>;

export interface UsePageDesignerOptions {
  idFactory?: () => string;
  initialDocument: DecorationDocument;
}

export interface PageDesignerState {
  addComponent: (component: NewComponent, atIndex?: number) => string;
  canRedo: ComputedRef<boolean>;
  canUndo: ComputedRef<boolean>;
  document: Ref<DecorationDocument>;
  duplicateComponent: (id: string) => string | undefined;
  moveComponent: (id: string, toIndex: number) => void;
  patchComponent: (
    id: string,
    patch: Record<string, unknown>,
    groupKey?: string,
  ) => void;
  patchPage: (patch: Partial<PageSettings>, groupKey?: string) => void;
  redo: () => void;
  removeComponent: (id: string) => void;
  reset: (document: DecorationDocument) => void;
  selectedId: Ref<string | undefined>;
  selectComponent: (id?: string) => void;
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

  function mutate(
    update: (document: DecorationDocument) => void,
    groupKey?: string,
  ) {
    const next = cloneValue(history.state.value);
    update(next);
    history.commit(next, { groupKey });
  }

  function addComponent(component: NewComponent, atIndex?: number) {
    const id = idFactory();
    mutate((document) => {
      const next = { ...cloneValue(component), id };
      const index = Math.min(
        Math.max(atIndex ?? document.components.length, 0),
        document.components.length,
      );
      document.components.splice(index, 0, next);
    });
    selectedId.value = id;
    return id;
  }

  function duplicateComponent(id: string) {
    const sourceIndex = history.state.value.components.findIndex(
      (component) => component.id === id,
    );
    if (sourceIndex === -1) return undefined;
    const duplicateId = idFactory();
    mutate((document) => {
      const source = document.components[sourceIndex];
      if (!source) return;
      document.components.splice(sourceIndex + 1, 0, {
        ...cloneValue(source),
        id: duplicateId,
      });
    });
    selectedId.value = duplicateId;
    return duplicateId;
  }

  function moveComponent(id: string, toIndex: number) {
    mutate((document) => {
      const fromIndex = document.components.findIndex(
        (component) => component.id === id,
      );
      if (fromIndex === -1) return;
      const [component] = document.components.splice(fromIndex, 1);
      if (!component) return;
      const target = Math.min(Math.max(toIndex, 0), document.components.length);
      document.components.splice(target, 0, component);
    });
  }

  function removeComponent(id: string) {
    mutate((document) => {
      document.components = document.components.filter(
        (component) => component.id !== id,
      );
    });
    if (selectedId.value === id) selectedId.value = undefined;
  }

  function patchComponent(
    id: string,
    patch: Record<string, unknown>,
    groupKey?: string,
  ) {
    mutate((document) => {
      const component = document.components.find((item) => item.id === id);
      if (component) component.props = { ...component.props, ...patch };
    }, groupKey);
  }

  function patchPage(patch: Partial<PageSettings>, groupKey?: string) {
    mutate((document) => {
      document.page = { ...document.page, ...patch };
    }, groupKey);
  }

  function selectComponent(id?: string) {
    selectedId.value = id;
  }

  function reset(document: DecorationDocument) {
    history.reset(document);
    selectedId.value = undefined;
  }

  return {
    addComponent,
    canRedo: computed(() => history.canRedo.value),
    canUndo: computed(() => history.canUndo.value),
    document: history.state,
    duplicateComponent,
    moveComponent,
    patchComponent,
    patchPage,
    redo: history.redo,
    removeComponent,
    reset,
    selectedId,
    selectComponent,
    undo: history.undo,
  };
}
