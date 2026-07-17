import type { ComputedRef, ShallowRef } from 'vue';

import { computed, shallowReactive, shallowRef } from 'vue';

import { cloneDesignerValue } from '../schema/clone';

export interface CommandHistoryOptions<T> {
  clone?: (value: T) => T;
  limit?: number;
}

export interface CommitOptions {
  groupKey?: string;
}

export interface CommandHistory<T> {
  canRedo: ComputedRef<boolean>;
  canUndo: ComputedRef<boolean>;
  commit: (next: T, options?: CommitOptions) => void;
  redo: () => void;
  reset: (next: T) => void;
  state: ShallowRef<T>;
  undo: () => void;
}

function cloneJson<T>(value: T): T {
  return cloneDesignerValue(value);
}

export function useCommandHistory<T>(
  initialValue: T,
  options: CommandHistoryOptions<T> = {},
): CommandHistory<T> {
  const clone = options.clone ?? cloneJson;
  const limit = options.limit ?? 50;
  const state = shallowRef<T>(clone(initialValue));
  const past = shallowReactive<T[]>([]);
  const future = shallowReactive<T[]>([]);
  let activeGroupKey: string | undefined;

  const canUndo = computed(() => past.length > 0);
  const canRedo = computed(() => future.length > 0);

  function commit(next: T, commitOptions: CommitOptions = {}) {
    if (!commitOptions.groupKey || commitOptions.groupKey !== activeGroupKey) {
      past.push(clone(state.value));
      if (past.length > limit) past.shift();
    }
    state.value = clone(next);
    future.length = 0;
    activeGroupKey = commitOptions.groupKey;
  }

  function undo() {
    const previous = past.pop();
    if (!previous) return;
    future.push(clone(state.value));
    state.value = previous;
    activeGroupKey = undefined;
  }

  function redo() {
    const next = future.pop();
    if (!next) return;
    past.push(clone(state.value));
    state.value = next;
    activeGroupKey = undefined;
  }

  function reset(next: T) {
    state.value = clone(next);
    past.length = 0;
    future.length = 0;
    activeGroupKey = undefined;
  }

  return { canRedo, canUndo, commit, redo, reset, state, undo };
}
