import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 公告组件的渲染契约（前缀回落 + 单条跑马灯 + 空公告不渲染 + 小程序插槽陷阱）。
 *
 * 四类缺陷都只在实机/长驻页面里才看得见，类型检查和单测发现不了：
 *
 * 1. **空白前缀**：`titleType='2'` 但 `titleText` 为空时，原实现仍走文字分支，
 *    公告条左侧留一段空白——整条公告看起来像背景缺了一块。前缀必须按
 *    `notice-prefix.ts` 收敛，配置不足以渲染就回落铃铛。
 *
 * 2. **单条不动**：`wd-notice-bar` 的横向滚动只把文案整条滚出可视区、空等
 *    一段时间再跳回来，单条时中段只剩一个铃铛；但公告条是运营配了滚动速度的
 *    组件，也不能因为只有一条就完全静止。单条横向必须用 MarqueeText 的
 *    ticker 模式无缝循环，并受全站自动动效开关约束。
 *
 * 3. **多条公告整条空白（小程序端独有）**：uniapp 在小程序端的插槽是编译期
 *    静态登记的——父模板只要给 `wd-notice-bar` 声明了默认插槽，编译产物就会把
 *    `'d'` 写进 `u-s`，运行时 `observerSlots` 把 `$slots.d` 置真；子组件模板
 *    `<block wx:if="{{$slots.d}}"><slot/></block>` 于是永远走插槽、不再渲染
 *    内建文案 fallback `{{ currentText }}`。单条跑马灯分支用 `v-if` 隐藏时插槽
 *    为空，整条公告就只剩铃铛和箭头。H5 的插槽按运行时条件注册，浏览器上看不出
 *    问题，所以这条必须由源码契约钉死：前缀与箭头一律在行内渲染，
 *    `wd-notice-bar` 不接收任何插槽。
 *
 * 4. **文案清空留空色块**：空公告整块不渲染（运营清空文案后不应留下背景条）。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const NOTICE = 'src/components/diy/diy-notice/index.vue'

describe('公告组件渲染契约', () => {
  const code = source(NOTICE)

  it('前缀类型走出纯函数，模板不再直接判断 titleType', () => {
    expect(code).toContain('notice-prefix')
    expect(code).toMatch(/resolveNoticePrefix\(noticeData\.value\)/)
    expect(code).toMatch(/prefixKind === 'image'/)
    expect(code).toMatch(/prefixKind === 'text'/)
    // 直接判断 titleType 会漏掉「配了文字类型但文案为空」的存量数据
    expect(code).not.toMatch(/noticeData\.titleType === '2'/)
  })

  it('单条横向公告走无缝跑马灯，并受自动动效开关约束', () => {
    expect(code).toContain('const singleNotice = computed')
    expect(code).toContain('const singleTicker = computed')
    expect(code).toContain('MarqueeText')
    expect(code).toMatch(/ticker(?:\s|>)/)
    expect(code).toContain('mallThemeStore.autoMotionEnabled')
    // 单条交给 ticker，多条才交给 wd-notice-bar 轮播
    expect(code).toContain(':scrollable="!singleNotice"')
    expect(code).toContain(':wrapable="singleNotice"')
  })

  it('不给 wd-notice-bar 传任何插槽：小程序端静态插槽会顶掉内建文案', () => {
    // 详见文件头注释第 3 条：`u-s` 静态登记 → `$slots.d` 恒真 → fallback 被吞。
    // 因此 `wd-notice-bar` 必须是自闭合标签（只吃 text / direction / speed 等 props），
    // 前缀与箭头改由外层 `.notice-icon` / `.notice-arrow` 行内渲染。
    expect(code).not.toMatch(/<template[^>]*#/)
    expect(code).not.toMatch(/slot="(?:default|prefix|suffix)"/)
    expect(code).toMatch(/<wd-notice-bar[\s\S]*?\/>/)
    // 与页面设计器画布同结构：前缀 / 正文 / 箭头三段式，正文 flex:1 拿可用宽度
    expect(code).toMatch(/class="notice-icon/)
    expect(code).toMatch(/class="notice-body"/)
    expect(code).toMatch(/class="notice-arrow"/)
  })

  it('文案为空时整块不渲染，不留一条空色块', () => {
    expect(code).toContain('const hasContent = computed')
    expect(code).toContain('v-if="hasContent"')
  })
})
