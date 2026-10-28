import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 新人专享楼层「抢」按钮接线契约。
 *
 * 背景（真实缺陷）：「抢」原本是个裸 `<view>`，没有自己的点击处理器，点下去
 * 事件冒泡到整卡 `@click="openGoods(goods.spuId)"` —— 用户以为在加购，实际被
 * 带进商品详情，按钮语义完全没兑现。这类问题类型检查、单测都发现不了：
 * 模板能编译、没有报错、只是「点了不对」。
 *
 * 这里断言源码而不是渲染结果：楼层依赖 uni 运行时（scroll-view/SelectorQuery），
 * 单测跑不起来，而「事件绑定消失」「漏 .stop 冒泡」「绕过统一加购入口」都恰好
 * 能在源码层面抓到（同 auto-motion-switch.contract.test.ts 的思路）。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * 剥离注释后再断言「不含某段文案」。
 *
 * 本组件里大量注释解释「为什么不再自己 toast『已加入购物车』」，注释里出现该文案
 * 是正常的；只有真正写进代码的行为才是回归。用状态机而非正则：字符串里的 `//`
 * （如 `'/pages/login/index'` 之外的 URL）不能被当成注释起点。
 */
function codeOf(relativePath: string) {
  const text = source(relativePath)
  let result = ''
  let inString: string | null = null
  let inLineComment = false
  let inBlockComment = false

  for (let i = 0; i < text.length; i++) {
    const char = text[i]
    const next = text[i + 1]

    if (inLineComment) {
      if (char === '\n') {
        inLineComment = false
        result += char
      }
      continue
    }
    if (inBlockComment) {
      if (char === '*' && next === '/') {
        inBlockComment = false
        i++
      }
      continue
    }
    if (inString) {
      result += char
      if (char === '\\') {
        result += next ?? ''
        i++
      }
      else if (char === inString) {
        inString = null
      }
      continue
    }
    if (char === '"' || char === '\'' || char === '`') {
      inString = char
      result += char
      continue
    }
    if (char === '/' && next === '/') {
      inLineComment = true
      i++
      continue
    }
    if (char === '/' && next === '*') {
      inBlockComment = true
      i++
      continue
    }
    result += char
  }
  return result
}

const FLOOR = 'src/components/diy/diy-newbie-floor/index.vue'
const QUICK_CART = 'src/composables/useQuickCart.ts'

/** 「抢」按钮的 handler 名：模板绑定与函数定义必须是同一个名字 */
const GRAB_HANDLER = 'handleGrab'

describe('新人专享楼层「抢」＝直接加购', () => {
  it('「抢」按钮有自己的点击处理器，且带 .stop 阻止冒泡跳商详', () => {
    const floor = source(FLOOR)
    // 必须绑定在 .nf-btn 上：绑到卡片上就退回「点哪都跳商详」
    expect(floor).toMatch(
      new RegExp(`class="nf-btn"[\\s\\S]{0,200}@click\\.stop="${GRAB_HANDLER}\\(goods\\)"`),
    )
    // .stop 是这条需求的关键：不阻止冒泡会「加购完立刻被带进商详」
    expect(floor).toMatch(/@click\.stop="handleGrab\(goods\)"/)
  })

  it('引入统一加购 composable 并真的调用 quickAdd', () => {
    const floor = source(FLOOR)
    expect(floor).toContain('useQuickCart')
    // 只 import 不调用 = 按钮仍然是死的
    expect(floor).toMatch(/await quickAdd\(/)
    // 不允许自己写加购接口：绕开统一入口会丢掉数量归一化与去向确认
    expect(floor).not.toContain('addShoppingCart')
    expect(floor).not.toContain('addSharedCartItem')
  })

  it('未登录的登录引导由 useQuickCart 统一负责，楼层不自己写跳登录', () => {
    const composable = source(QUICK_CART)
    // 单一真相：登录跳转只在 useQuickCart 里出现，所有加购宿主共用
    expect(composable).toContain('/pages/login/index')
    expect(composable).toContain('authStore.isLoggedIn')
    // 楼层自己再写一份 navigateTo 登录，就会与统一入口二次跳转
    expect(source(FLOOR)).not.toMatch(/navigateTo\(\{\s*url:\s*'\/pages\/login\/index'/)
  })

  it('handler 名字同时出现在模板绑定与脚本定义里（防止删掉绑定只留函数）', () => {
    const floor = source(FLOOR)
    expect(floor).toMatch(/@click\.stop="handleGrab\(goods\)"/)
    expect(floor).toMatch(new RegExp(`as(?:ync)?\\s+function\\s+${GRAB_HANDLER}\\s*\\(`))
  })

  it('整卡点击仍然跳商详（按钮加购不能把卡片入口改没）', () => {
    const floor = source(FLOOR)
    expect(floor).toMatch(/@click="openGoods\(goods\.spuId\)"/)
    // 跳转仍走装修链路解析器，而不是自己拼 route
    expect(floor).toContain('followDecorationLink')
  })

  it('多规格回退商详：needChoose 时提示选规格并打开商详', () => {
    const floor = source(FLOOR)
    expect(floor).toContain('result.needChoose')
    // 文案走 i18n 词条（中文即原「请选择规格」），此处只校验接线到的 key
    expect(floor).toContain("t('diy.newbieFloor.selectSpecsToast')")
    // needChoose 分支内必须真的去商详，否则用户点「抢」后无从选择规格
    expect(floor).toMatch(/result\.needChoose[\s\S]{0,300}openGoods\(spuId\)/)
  })

  it('成功提示不与 useQuickCart 重复：楼层自己不再 toast「已加入购物车」', () => {
    const composable = source(QUICK_CART)
    // 加购成功的提示由统一入口（useCartDestination.addPersonal）发出
    expect(composable).toContain('submitCartAdd')
    // 只断言代码：注释里提这句文案是在解释「为什么这里不再提示」，属正常
    expect(codeOf(FLOOR)).not.toContain('已加入购物车')
  })

  it('新人限购件数取自楼层配置，并与统一数量算法比对后再决定是否加购', () => {
    const floor = source(FLOOR)
    // 限购数来自活动（floor.limitPerUser），不是商品自带字段
    expect(floor).toContain('limitPerUser')
    // 必须用与 useQuickCart 同一份纯函数算最小合规数量，不能各写一套
    expect(floor).toContain('resolveQuickAddQuantity')
    expect(floor).toContain('QUICK_CART_MODE_DIRECT')
    // 数量超过限购时给提示并跳商详（冲突场景不自行加购）
    expect(floor).toMatch(/resolveQuickAddQuantity\(info\) > limit[\s\S]{0,200}showToast/)
  })

  it('活动 SKU 与快捷加购选中的 SKU 不一致时回退商详', () => {
    const floor = source(FLOOR)
    // 楼层项自带活动 skuId；后端快捷加购按库存挑 SKU，两者可能不是同一行。
    // 对不上还硬加，用户看到的是活动价、入车的是另一规格，价差到结算才暴露。
    expect(floor).toMatch(/activitySkuId/)
    expect(floor).toMatch(/String\(info\?\.skuId \|\| ''\) !== activitySkuId/)
    expect(floor).toMatch(/!== activitySkuId[\s\S]{0,200}openGoods\(spuId\)/)
  })

  it('确认弹层按楼层标价展示新人价，并把数量封顶到限购件数', () => {
    const floor = source(FLOOR)
    // quick-cart 只回普通售价；不带活动价进弹层就会出现「卡片 ¥0.01、小计 ¥12.90」
    expect(floor).toMatch(/quickAdd\(spuId,\s*\{\s*promotion:\s*\{/)
    expect(floor).toMatch(/unitPrice:\s*goods\.newbiePrice/)
    // 超量不是恢复原价而是整单被后端拒绝，必须在弹层里提前封顶
    expect(floor).toMatch(/maxQuantity:\s*newbieLimitPerUser\(\)/)
  })

  it('防重复点击：复用 composable 的 pending，并把在途态反映到按钮上', () => {
    const floor = source(FLOOR)
    expect(floor).toMatch(/const \{ pending, quickAdd \} = useQuickCart\(\)/)
    expect(floor).toMatch(/if \(pending\.value\)[\s\S]{0,80}return/)
    expect(floor).toContain('nf-btn--pending')
  })
})
