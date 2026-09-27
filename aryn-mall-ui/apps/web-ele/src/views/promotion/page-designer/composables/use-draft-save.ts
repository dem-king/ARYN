import type { Ref } from 'vue';

import { computed, ref } from 'vue';

export type DraftSaveStatus =
  'conflict' | 'dirty' | 'error' | 'idle' | 'saved' | 'saving';

/**
 * 手动保存的结果。
 *
 * - `saved`：本次调用确实写入了服务端（revision 已推进）
 * - `skipped`：没有未持久化的改动，因此没有发出请求
 *
 * 区分二者是为了让「保存草稿」按钮能给出明确反馈——
 * 自动保存（默认 1.8s 防抖）常常已经写完，此时再点按钮若静默返回，
 * 用户看到的就是"点了没有任何反应"。
 */
export type DraftSaveResult = 'saved' | 'skipped';

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

/**
 * 判定是否为草稿修订号冲突。
 *
 * 后端 `ArynBusinessException` 走的是 **HTTP 200 + body.code=1**，
 * 由 `defaultResponseInterceptor` 抛出，错误对象形状为
 * `{ response: { status: 200, data: { code: 1, msg: '草稿已被其他人修改，请重新加载' } } }`
 * ——既不是 409，也没有 `DRAFT_CONFLICT` 码。因此这里必须按响应体判定，
 * 否则冲突会被误判成普通失败，状态停在"保存失败"而非"版本冲突"。
 */
const DRAFT_CONFLICT_HINTS = [
  '草稿已被其他人修改',
  'Draft revision has changed',
  '草稿已被其他人',
] as const;

function isConflict(error: unknown) {
  if (!error || typeof error !== 'object') return false;
  const candidate = error as {
    code?: unknown;
    response?: { data?: { msg?: unknown }; status?: unknown };
    status?: unknown;
  };
  if (candidate.code === 'DRAFT_CONFLICT' || candidate.status === 409) {
    return true;
  }
  if (candidate.response?.status === 409) return true;

  const message = candidate.response?.data?.msg;
  return (
    typeof message === 'string' &&
    DRAFT_CONFLICT_HINTS.some((hint) => message.includes(hint))
  );
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

  function saveNow(): Promise<DraftSaveResult> {
    clearTimer();
    if (!dirty.value && !inFlight) return Promise.resolve('skipped');
    if (!inFlight) {
      inFlight = executeSaveLoop().finally(() => {
        inFlight = undefined;
      });
    }
    return inFlight.then(() => 'saved' as const);
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
