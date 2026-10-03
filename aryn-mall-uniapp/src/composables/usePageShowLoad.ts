import { onShow } from '@dcloudio/uni-app'
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 装修组件（首页区块）的「装载即加载 + 页面每次显示刷新」接线。
 *
 * 背景（2026-09-29 首页补给单卡「登录后不立即出现」）：
 * 首页装修内容是 `onLoad` 里异步拉回来才渲染的，装修组件挂载时**页面的 onShow 已经跑完**。
 * 组件里只注册 `onShow` 拉数据，在微信小程序端就永远等不到第一次调用——
 * `@dcloudio/uni-mp-vue` 的 `injectHook` 没有「迟到注册的页面钩子立即补调」这段
 * （对比 `@dcloudio/uni-h5-vue` 的 `isRootImmediateHook`，H5 会在注册时补调一次）。
 * 所以小程序端表现为：进首页看不到，切到别的 Tab 再切回来才出现，
 * 而 H5 正常——两端行为不一致，且不报任何错。
 *
 * 因此装修组件的数据装载必须**同时**挂在两处：
 *   · `onMounted`：覆盖首屏（此时页面 onShow 已过，小程序端不会再触发）；
 *   · 页面 `onShow`：覆盖切 Tab / 返回本页，数据可能已被其它页面改动（如新建了补给单）。
 *
 * 去重：H5 的 onShow 补调发生在 setup 阶段、早于 onMounted，因此「挂载这次触发」
 * 只在**尚未发起过任何加载**时才发起——H5 已被补调覆盖，小程序端才是首屏来源。
 * 两种来源都受在途去重保护，避免同一时刻发出两个请求。
 */

export type PageShowLoadPhase = 'mount' | 'show'

export interface PageShowLoadState {
  /** 是否已有一次加载在途 */
  inflight: boolean
  /** 是否已发起过至少一次加载（H5 的 onShow 补调会先于挂载把它置真） */
  started: boolean
}

/**
 * 纯判定：这次触发是否应当真正发起加载。
 *
 * 抽成纯函数是为了让去重口径可单测——组件装载时机与页面事件顺序在各端不同，
 * 靠端上复现既慢又容易漏。
 */
export function shouldStartPageShowLoad(
  state: PageShowLoadState,
  phase: PageShowLoadPhase,
): boolean {
  if (state.inflight)
    return false
  // 挂载只是首屏兜底：H5 的 onShow 已在注册时补调过一次，不能再发第二次
  return phase === 'show' || !state.started
}

/**
 * 让 `load` 在组件挂载时执行一次，并在页面每次显示时重新执行。
 *
 * `load` 内部自行处理失败降级（装修组件失败一律静默，不打断整页渲染）。
 */
export function usePageShowLoad(load: () => Promise<unknown> | unknown) {
  const state: PageShowLoadState = { inflight: false, started: false }
  let disposed = false

  // 页面钩子注册在页面实例上，不随组件卸载自动摘除：
  // 装修内容被替换、区块被移除后，钩子仍会被页面 onShow 调用，必须自己拦住。
  onBeforeUnmount(() => {
    disposed = true
  })

  async function trigger(phase: PageShowLoadPhase) {
    if (disposed)
      return
    if (!shouldStartPageShowLoad(state, phase))
      return

    state.inflight = true
    state.started = true
    try {
      await load()
    }
    catch {
      // 兜底：`load` 通常自带失败降级，这里只保证抛出的异常不会变成
      // 全局未处理的 Promise 拒绝（小程序端会当成 JS 错误上报）
    }
    finally {
      state.inflight = false
    }
  }

  onMounted(() => {
    void trigger('mount')
  })
  onShow(() => {
    void trigger('show')
  })
}
