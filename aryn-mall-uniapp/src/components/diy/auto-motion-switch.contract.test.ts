import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import process from 'node:process'
import { describe, expect, it } from 'vitest'

/**
 * 全站自动动效总开关的接线契约：
 * 开关（mallThemeStore.autoMotionEnabled）必须被 5 处自动动效消费，
 * 且「关闭时不启动」的判定要真的落在自动播放/定时器上，而不是只存在字段。
 */
const diyRoot = resolve(process.cwd(), 'src/components/diy')
const storePath = resolve(process.cwd(), 'src/store/mallThemeStore.ts')

function read(relative: string) {
  return readFileSync(resolve(diyRoot, relative), 'utf8')
}

describe('全站自动动效总开关接线', () => {
  it('store 暴露 autoMotionEnabled 并纳入缓存与脏值校验', () => {
    const store = readFileSync(storePath, 'utf8')
    expect(store).toContain('autoMotionEnabled: true')
    expect(store).toContain('autoMotionEnabled?: boolean')
    // sanitize 需同时接受接口的 '0'/'1' 字符串
    expect(store).toMatch(/autoMotionEnabled === '0'/)
    expect(store).toMatch(/autoMotionEnabled === '1'/)
    // 落缓存（冷启动首帧即生效）
    expect(store).toContain('autoMotionEnabled: this.autoMotionEnabled')
  })

  it.each([
    ['diy-newbie-floor', '自动前进'],
    ['diy-goods-scroll', '商品横滑条轮播'],
    ['diy-swiper-banner', '轮播图'],
    ['diy-image', '图片组件轮播'],
    ['diy-search-bar', '搜索栏热词轮换'],
  ])('%s（%s）消费总开关', (dir, label) => {
    const source = read(`${dir}/index.vue`)
    expect(source, `${label} 应引入 store`).toContain('useMallThemeStore')
    expect(source, `${label} 应消费开关`).toContain('autoMotionEnabled')
  })

  it('swiper 类组件的 autoplay 绑定到开关，而非写死 true', () => {
    for (const dir of ['diy-goods-scroll', 'diy-swiper-banner', 'diy-image']) {
      const source = read(`${dir}/index.vue`)
      expect(source, `${dir} 不应再写死 autoplay`).not.toMatch(/\sautoplay(\s|>)/)
      expect(source, `${dir} 应绑定 :autoplay`).toMatch(/:autoplay="mallThemeStore\.autoMotionEnabled"/)
    }
  })

  it('搜索栏热词轮换受开关约束，且开关异步到达后能自愈启停', () => {
    const source = read('diy-search-bar/index.vue')
    // 关闭时不启动定时器
    expect(source).toMatch(/if \(!mallThemeStore\.autoMotionEnabled\)\s*\n\s*return/)
    // 总开关变化时重新评估（主题拉取晚于挂载）
    expect(source).toContain('watch(')
    expect(source).toContain('startRotation()')
  })

  it('新人专享：关闭开关后不启动自动前进', () => {
    const source = read('diy-newbie-floor/index.vue')
    expect(source).toMatch(/shouldAutoAdvance[\s\S]{0,200}mallThemeStore\.autoMotionEnabled/)
  })
})
