import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商详首屏「价格区 → 活动条 → 标题卡」纵向节奏契约守门。
 *
 * 用户反馈的真实缺陷：无新人价/会员价时首屏正常，一旦这两栏出现，
 * 价格区与标题卡之间的间隔明显变大。
 *
 * 机制：价格渐变条写死 `h-72px`，比「主价 + 划线价」两行内容高约 25px；
 * 这段余量原本靠标题卡的 `margin-top: -32px` 上提盖掉。有活动条时标题卡
 * 按 `--striped` 取消上提，余量就整块露在条与标题之间。改动只动 CSS，
 * 类型检查与既有价格契约测试都发现不了，只能按源码结构钉住：
 * `hasPriceStrip` 必须同时驱动「价格条高度跟随内容」与「标题卡取消上提」。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const GOODS_INFO = 'src/sub-pages/product/goods-detail/components/GoodsInfo.vue'

describe('goods detail price strip spacing contract', () => {
  it('价格条在有条时取消固定高度（否则余量会露在条与标题之间）', () => {
    const info = source(GOODS_INFO)

    // 固定高度仍是手机端的默认形态（无活动条时由标题卡上提咬合）
    expect(info).toContain('h-72px')

    // 有条时必须挂 auto 修饰类，且该类真的把高度放开
    expect(info).toContain(`:class="{ 'goods-price-bar--auto': hasPriceStrip }"`)
    const autoRule = info.slice(
      info.indexOf('.goods-price-bar--auto {'),
      info.indexOf('.member-badge'),
    )
    expect(autoRule).toMatch(/height\s*:\s*auto/)
    // Uno 工具类同权注入在后，没有 !important 会被 h-72px 压掉（实测 72px 不变）
    expect(autoRule).toContain('!important')
  })

  it('标题卡有活动条时取消 -32px 上提，避免首行被条裁切', () => {
    const info = source(GOODS_INFO)

    expect(info).toContain(`:class="{ 'goods-title-card--striped': hasPriceStrip }"`)
    const stripedStart = info.indexOf('.goods-title-card--striped {')
    // 只取规则体本身：紧邻的说明注释里会提到 -32px 的来历，不能算违规
    const stripedRule = info.slice(stripedStart, info.indexOf('}', stripedStart) + 1)
    // 不能保留 -32px：那会把标题首行拖进条内
    expect(stripedRule).not.toContain('-32px')
    // 只抵消活动条自身的下外边距，让卡片与条贴边
    expect(stripedRule).toMatch(/margin-top\s*:\s*-12rpx/)
  })

  it('hasPriceStrip 覆盖四类活动条，避免某一类漏判后间隔又变大', () => {
    const info = source(GOODS_INFO)
    const block = info.slice(
      info.indexOf('const hasPriceStrip = computed('),
      info.indexOf('/** 秒杀剩余库存'),
    )
    for (const marker of ['props.seckillInfo', 'memberGuideText.value', 'newbieLimitText.value', 'props.discountDisplay.showDiscountPrice'])
      expect(block).toContain(marker)
  })
})
