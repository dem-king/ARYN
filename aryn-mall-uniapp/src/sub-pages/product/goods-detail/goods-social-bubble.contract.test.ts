import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import process from 'node:process'
import { describe, expect, it } from 'vitest'

/**
 * 商详口碑气泡（轮播图左上角浮层）的接线契约：
 * - 数据只从详情接口 goodsSpu.socialBubble 来（服务端成文，前端不自算口径）；
 * - 压图浮层用固定深色，不用 --app-* 语义变量（底图不可控，语义变量会串色）；
 * - 弹出与轮换必须接 autoMotionEnabled（内容不因关开关而丢）；
 * - @keyframes 必须写在 <style> 块内（块外会被 SFC 静默丢弃）。
 */
const detailRoot = resolve(process.cwd(), 'src/sub-pages/product/goods-detail')

function read(relative: string) {
  return readFileSync(resolve(detailRoot, relative), 'utf8')
}

function styleBlockOf(source: string) {
  return source.slice(source.indexOf('<style'))
}

describe('商详口碑气泡接线', () => {
  const goodsInfo = read('components/GoodsInfo.vue')
  const indexPage = read('index.vue')

  it('气泡数据走服务端成文（socialText/praiseText），前端不自拼数字口径', () => {
    expect(goodsInfo).toContain('socialBubble?.socialText')
    expect(goodsInfo).toContain('socialBubble?.praiseText')
    // 防御：气泡文案不允许前端格式化数字（口径单点在后端 SocialProofFormatter）
    expect(goodsInfo).not.toMatch(/bubbleMessages[\s\S]{0,80}formatCount/)
  })

  it('压图浮层用固定深色玻璃，不引入语义变量', () => {
    const style = styleBlockOf(goodsInfo)
    // 精确取 &__bubble { ... } 规则体（到下一个 __bubble-avatar 锚点为止）
    const start = style.indexOf('&__bubble {')
    const end = style.indexOf('&__bubble-avatar', start)
    expect(start).toBeGreaterThan(-1)
    expect(end).toBeGreaterThan(start)
    const bubbleRule = style.slice(start, end)
    expect(bubbleRule).toContain('rgb(17 17 17 / 55%)')
    // 气泡规则不得消费 --app-* 变量（底图/底内容不可控，语义变量会串色）
    expect(bubbleRule).not.toContain('--app-')
  })

  it('手机端气泡悬浮（fixed）不随滚动消失，宽屏双栏回落图集卡内', () => {
    const style = styleBlockOf(goodsInfo)
    // 折叠态：fixed 悬浮——一滚动就消失是回退态（曾因 absolute 跟图集滚走被打回）
    const start = style.indexOf('&__bubble {')
    const end = style.indexOf('&__bubble-avatar', start)
    expect(style.slice(start, end)).toContain('position: fixed')
    // 宽屏（MP-WEIXIN ≥600px 双栏）：平板上悬整个视口没有意义，改回图集卡内 absolute
    const wideStart = style.indexOf('@media (min-width: 600px)')
    const widePart = style.slice(wideStart)
    const bubbleStart = widePart.indexOf('.goods-swiper__bubble {')
    expect(widePart.slice(bubbleStart, widePart.indexOf('}', bubbleStart))).toContain('position: absolute')
  })

  it('弹出与轮换接 autoMotionEnabled，关闭时首帧静态显示不丢内容', () => {
    expect(goodsInfo).toContain('useMallThemeStore')
    // 弹出：关闭开关 → 直接可见
    expect(goodsInfo).toMatch(/if \(!mallThemeStore\.autoMotionEnabled\)\s*\{\s*bubbleVisible\.value = true/)
    // 轮换：关闭开关不启动定时器；开关异步到达后 watch 自愈
    expect(goodsInfo).toMatch(/function startRotation\(\)\s*\{\s*stopRotation\(\)/)
    expect(goodsInfo).toMatch(/watch\(\[\(\) => mallThemeStore\.autoMotionEnabled/)
  })

  it('文案轮换有淡出/滑入过渡（三态类切换，不依赖小程序不支持的 <transition>）', () => {
    // 状态机：idle → leaving（上移淡出）→ entering（无过渡就位）→ idle（滑入淡上）
    expect(goodsInfo).toMatch(/const bubbleTextState = ref<'idle' \| 'leaving' \| 'entering'>/)
    expect(goodsInfo).toContain('function rotateMessage()')
    const style = styleBlockOf(goodsInfo)
    const leavingStart = style.indexOf('&__bubble-text--leaving')
    const enteringStart = style.indexOf('&__bubble-text--entering')
    expect(leavingStart).toBeGreaterThan(-1)
    expect(enteringStart).toBeGreaterThan(leavingStart)
    // entering 必须关掉过渡（无过渡跳变到下方），释放回 base 才触发滑入
    expect(style.slice(enteringStart, style.indexOf('}', enteringStart))).toContain('transition: none')
    // 基态带过渡
    const textStart = style.indexOf('&__bubble-text {')
    expect(style.slice(textStart, style.indexOf('}', textStart))).toContain('transition:')
  })

  it('@keyframes 写在 <style> 块内且有 reduced-motion 兜底', () => {
    const style = styleBlockOf(goodsInfo)
    expect(style).toContain('@keyframes goods-bubble-pop')
    expect(style).toContain('prefers-reduced-motion')
  })

  it('详情页传入 socialBubble 并接住 gotoComment 跳评价区', () => {
    expect(indexPage).toContain(':social-bubble=')
    expect(indexPage).toContain('@goto-comment=')
    expect(indexPage).toContain('gotoCommentFromBubble')
  })
})
