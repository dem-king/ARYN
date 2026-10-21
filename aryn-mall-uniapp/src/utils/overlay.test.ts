import { describe, expect, it } from 'vitest'

import { OVERLAY_Z_INDEX } from './overlay'

/**
 * 全局浮层层级契约。
 *
 * 背景（真实缺陷，2026-10-18）：船舶绑定页「提交申请」失败时，错误提示走
 * `useGlobalToast`（wd-toast 默认 z-index: 100），而页面底部弹层是 900/901，
 * 提示被弹层整个盖住 —— 用户看到的就是「点了提交没反应」。
 *
 * 因此「全局反馈层」必须高于页面内所有自定义浮层。页面浮层允许继续用
 * 900/901 这类裸数字（存量很多），但反馈层要有唯一出处，不允许各组件自己拍。
 */

/**
 * 页面浮层的实际最高值（不是 1000 就够）：
 * shared-cart-destination-sheet / trip-detail 的 wd-popup 都是 1020，
 * goods-detail-sheet 与日历 root-portal 是 1000。反馈层若只按 1000 定级，
 * 只是从「被 901 盖」换成「被 1020 盖」。
 */
const MAX_PAGE_OVERLAY_Z_INDEX = 1020

describe('全局浮层层级', () => {
  it('页面底部弹层在其遮罩之上', () => {
    expect(OVERLAY_Z_INDEX.sheet).toBeGreaterThan(OVERLAY_Z_INDEX.sheetMask)
  })

  it('弹层内二级浮层（日历等）压在弹层之上', () => {
    expect(OVERLAY_Z_INDEX.sheetPopover).toBeGreaterThan(OVERLAY_Z_INDEX.sheet)
  })

  it('toast 与 loading 高于所有页面浮层', () => {
    expect(OVERLAY_Z_INDEX.toast).toBeGreaterThan(OVERLAY_Z_INDEX.sheetPopover)
    expect(OVERLAY_Z_INDEX.loading).toBeGreaterThan(OVERLAY_Z_INDEX.sheetPopover)
  })

  it('全部反馈层高于页面里实际出现的最高浮层（1020）', () => {
    for (const key of ['toast', 'loading', 'messageBox', 'notify'] as const) {
      expect(OVERLAY_Z_INDEX[key], `${key} 必须压得住页面 1020 的弹层`)
        .toBeGreaterThan(MAX_PAGE_OVERLAY_Z_INDEX)
    }
  })

  it('消息框高于 toast，notify 最高', () => {
    expect(OVERLAY_Z_INDEX.messageBox).toBeGreaterThan(OVERLAY_Z_INDEX.toast)
    expect(OVERLAY_Z_INDEX.notify).toBeGreaterThan(OVERLAY_Z_INDEX.messageBox)
  })

  it('全部为整数且落在合理区间', () => {
    for (const value of Object.values(OVERLAY_Z_INDEX)) {
      expect(Number.isInteger(value)).toBe(true)
      expect(value).toBeGreaterThan(0)
      expect(value).toBeLessThan(9000)
    }
  })
})
