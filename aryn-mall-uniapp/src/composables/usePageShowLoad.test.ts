import { describe, expect, it } from 'vitest'

import { shouldStartPageShowLoad } from './usePageShowLoad'

/**
 * 装修组件首屏加载去重口径。
 *
 * 组件挂载与页面 onShow 在各端顺序不同，两端都要只发一次首屏请求：
 *   · H5：`onShow()` 在 setup 里注册时就被立即补调（uni-h5-vue 的 isRootImmediateHook），
 *     紧接着才是 onMounted；
 *   · 小程序：注册只是个登记，首次 onShow 根本不会触发，首屏只能靠 onMounted。
 * 因此判定逻辑单独测——靠端上复现这两种时序既慢又容易漏。
 */
describe('usePageShowLoad 去重判定', () => {
  it('请求在途时不再重复发起', () => {
    expect(shouldStartPageShowLoad({ inflight: true, started: true }, 'show')).toBe(false)
    expect(shouldStartPageShowLoad({ inflight: true, started: false }, 'mount')).toBe(false)
  })

  it('从未加载过时，挂载必须发起（小程序首屏的唯一来源）', () => {
    expect(shouldStartPageShowLoad({ inflight: false, started: false }, 'mount')).toBe(true)
  })

  it('页面已经显示过一次后，挂载不再重复发起（H5 会被 onShow 补调覆盖）', () => {
    expect(shouldStartPageShowLoad({ inflight: false, started: true }, 'mount')).toBe(false)
  })

  it('页面每次显示都刷新（切 Tab 返回、从子页返回都要拿新数据）', () => {
    expect(shouldStartPageShowLoad({ inflight: false, started: true }, 'show')).toBe(true)
  })
})
