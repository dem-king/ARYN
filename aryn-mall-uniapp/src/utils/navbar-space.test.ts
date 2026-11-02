import { describe, expect, it } from 'vitest'

import { NAVBAR_CONTENT_HEIGHT, resolveNavbarSpaceHeight } from './navbar-space'

/**
 * 回归测试：列表页顶栏占位直接用了 `--status-bar-height`。
 *
 * 背景（真实缺陷）：11 个 z-paging 列表页（收货地址 / 收藏 / 足迹 / 积分明细等）
 * 首行放 `.hr-navbar-scroll-space` 等高占位，高度写成
 * `calc(var(--status-bar-height, 0px) + 44px)`。小程序端 uni-app 注入的
 * `page{--status-bar-height:25px}` 是**写死的 25px**，而刘海屏真机的真实状态栏
 * 是 44~59px —— 占位短 20~35px，首屏卡片直接顶到导航栏标题上。
 * 解决办法是用 `uni.getWindowInfo().statusBarHeight` 实测后回填。
 */
describe('顶栏等高占位高度', () => {
  it('无状态栏（H5）时等于导航条内容区高度', () => {
    expect(resolveNavbarSpaceHeight(0)).toBe(NAVBAR_CONTENT_HEIGHT)
    expect(resolveNavbarSpaceHeight(0)).toBe(44)
  })

  it('刘海屏按实测状态栏高度累加，不再写死 25px', () => {
    // iPhone X / 12 / 13 (47)、iPhone 14 Pro (54)、Android 常见 24~30
    expect(resolveNavbarSpaceHeight(47)).toBe(91)
    expect(resolveNavbarSpaceHeight(54)).toBe(98)
    expect(resolveNavbarSpaceHeight(24)).toBe(68)
    // 关键回归口径：写死 25px 的旧占位是 69px，在 47px 状态栏的设备上短 22px
    expect(resolveNavbarSpaceHeight(47)).toBeGreaterThan(25 + NAVBAR_CONTENT_HEIGHT)
  })

  it('脏数据 / 取不到时按 0 处理，永不返回负数或 NaN', () => {
    expect(resolveNavbarSpaceHeight(undefined)).toBe(44)
    expect(resolveNavbarSpaceHeight(null)).toBe(44)
    expect(resolveNavbarSpaceHeight(Number.POSITIVE_INFINITY)).toBe(44)
    expect(resolveNavbarSpaceHeight(Number.NaN)).toBe(44)
    expect(resolveNavbarSpaceHeight(-12)).toBe(44)
  })
})
