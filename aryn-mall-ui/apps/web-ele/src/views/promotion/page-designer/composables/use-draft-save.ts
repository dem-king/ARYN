import type { Ref } from 'vue';

import { computed, ref } from 'vue';

export type DraftSaveStatus =
  | 'conflict'
  | 'dirty'
  | 'error'
  | 'idle'
  | 'saved'
  | 'saving';

export interface BeforeUnloadEventLike {
  preventDefault: () => void;
  returnValue: unknown;
}

export interface UseDraftSaveOptions<TPayload> {
  buildPayload: () => TPayload;
  delay?: number;
  revision: Ref<number>;
  save: (payload: TPayload) => Promise<number>;
}

function isConflict(error: unknown) {
  if (!error || typeof error !== 'object') return false;
  const candidate = error as { code?: unknown; status?: unknown };
  return candidate.code === 'DRAFT_CONFLICT' || candidate.status === 409;
}

export function useDraftSave<TPayload>(options: UseDraftSaveOptions<TPayload>) {
  const delay = options.delay ?? 2000;
  const status = ref<DraftSaveStatus>('idle');
  const dirty = ref(false);
  const lastError = ref<unknown>();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let inFlight: Promise<void> | undefined;

  const isDirty = computed(() => dirty.value);

  function clearTimer() {
    if (timer) clearTimeout(timer);
    timer = undefined;
  }

  async function executeSaveLoop() {
    while (dirty.value) {
      dirty.value = false;
      status.value = 'saving';
      lastError.value = undefined;
      try {
        options.revision.value = await options.save(options.buildPayload());
      } catch (error) {
        dirty.value = true;
        lastError.value = error;
        status.value = isConflict(error) ? 'conflict' : 'error';
        throw error;
      }
    }
    status.value = 'saved';
  }

  function saveNow() {
    clearTimer();
    if (!dirty.value && !inFlight) return Promise.resolve();
    if (!inFlight) {
      inFlight = executeSaveLoop().finally(() => {
        inFlight = undefined;
      });
    }
    return inFlight;
  }

  function markDirty() {
    dirty.value = true;
    if (status.value !== 'saving') status.value = 'dirty';
    clearTimer();
    if (!inFlight) {
      timer = setTimeout(() => {
        timer = undefined;
        void saveNow();
      }, delay);
    }
  }

  function handleBeforeUnload(event: BeforeUnloadEventLike) {
    if (!dirty.value && status.value !== 'saving') return undefined;
    event.preventDefault();
    event.returnValue = '';
    return '';
  }

  function dispose() {
    clearTimer();
  }

  return {
    dispose,
    handleBeforeUnload,
    isDirty,
    lastError,
    markDirty,
    saveNow,
    status,
  };
}
