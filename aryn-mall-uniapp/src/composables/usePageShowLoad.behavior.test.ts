import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 装修组件首屏加载接线的行为测试。
 *
 * 直接模拟两种平台的钩子时序（这是缺陷的根源，必须按真实时序守）：
 *   · 小程序（`@dcloudio/uni-mp-vue`）：注册 onShow 只是登记到页面实例上，
 *     **首次 onShow 不会触发** —— 组件挂载晚于页面 onShow，首屏只能靠 onMounted；
 *   · H5（`@dcloudio/uni-h5-vue`）：`isRootImmediateHook` 会在注册时立即补调一次
 *     onShow，紧接着才是 onMounted —— 首屏由补调覆盖，挂载那次必须去重。
 *
 * 只有纯判定函数被测的话，把 onMounted 漏掉（原缺陷）依然能全绿，所以这里连
 * 「是否真的注册了两个钩子、各触发几次」一起守。
 */
const mountedHandlers: Array<() => void> = []
const unmountHandlers: Array<() => void> = []
const showHandlers: Array<() => void> = []

vi.mock('vue', () => ({
  onMounted: (handler: () => void) => mountedHandlers.push(handler),
  onBeforeUnmount: (handler: () => void) => unmountHandlers.push(handler),
}))

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (handler: () => void) => showHandlers.push(handler),
}))

const { usePageShowLoad } = await import('./usePageShowLoad')

/** 把已注册的钩子按顺序跑一遍（microtask 洪泛后会等所有挂起的加载落地） */
async function runHandlers(handlers: Array<() => void>) {
  for (const handler of handlers)
    handler()
  await Promise.resolve()
  await Promise.resolve()
}

describe('usePageShowLoad 接线行为', () => {
  beforeEach(() => {
    mountedHandlers.length = 0
    unmountHandlers.length = 0
    showHandlers.length = 0
  })

  it('注册了挂载与页面显示两个钩子，且卸载钩子已接', () => {
    usePageShowLoad(() => Promise.resolve())
    expect(mountedHandlers).toHaveLength(1)
    expect(showHandlers).toHaveLength(1)
    expect(unmountHandlers).toHaveLength(1)
  })

  it('小程序时序：页面 onShow 已过、组件才挂载，首屏靠挂载触发', async () => {
    const load = vi.fn(() => Promise.resolve())
    usePageShowLoad(load)

    // 小程序端注册 onShow 不补调，组件挂载才拿到首屏
    expect(load).not.toHaveBeenCalled()
    await runHandlers(mountedHandlers)
    expect(load).toHaveBeenCalledTimes(1)

    // 之后每次页面显示都要刷新（切 Tab 回来、从子页返回）
    await runHandlers(showHandlers)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('h5 时序：onShow 注册时被补调，随后的挂载不再重复请求', async () => {
    const load = vi.fn(() => Promise.resolve())
    usePageShowLoad(load)

    // H5 的 isRootImmediateHook 在注册时就把 onShow 调了一次
    await runHandlers(showHandlers)
    expect(load).toHaveBeenCalledTimes(1)

    await runHandlers(mountedHandlers)
    expect(load).toHaveBeenCalledTimes(1)

    // 页面再次显示仍然刷新
    await runHandlers(showHandlers)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('请求在途时重复触发只发一次', async () => {
    let resolveLoad: () => void = () => {}
    const load = vi.fn(() => new Promise<void>((resolve) => {
      resolveLoad = resolve
    }))
    usePageShowLoad(load)

    mountedHandlers[0]?.()
    showHandlers[0]?.()
    await Promise.resolve()
    expect(load).toHaveBeenCalledTimes(1)

    resolveLoad()
    await Promise.resolve()
    await Promise.resolve()
    // 在途请求落地后，下一次页面显示才允许再拉
    await runHandlers(showHandlers)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('加载抛错不冒泡成未处理拒绝，且不卡死在途标记', async () => {
    const load = vi.fn(() => Promise.reject(new Error('boom')))
    usePageShowLoad(load)

    mountedHandlers[0]?.()
    await Promise.resolve()
    await Promise.resolve()
    expect(load).toHaveBeenCalledTimes(1)

    // 在途标记必须已复位，否则后续刷新全被吞掉
    await runHandlers(showHandlers)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('组件卸载后，遗留在页面上的钩子不再发起请求', async () => {
    const load = vi.fn(() => Promise.resolve())
    usePageShowLoad(load)

    await runHandlers(unmountHandlers)
    await runHandlers(mountedHandlers)
    await runHandlers(showHandlers)
    expect(load).not.toHaveBeenCalled()
  })
})
