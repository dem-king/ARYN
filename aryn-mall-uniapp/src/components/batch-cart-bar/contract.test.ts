import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 批量加购 UI 契约守门。
 *
 * 这里守的三条都源自本项目的真实坑：
 *   1. 底部条定位：原型写的是 `bottom:56px` 硬编码，H5 的 tabBar 高度
 *      由 `--window-bottom` 提供、小程序为 0，抄原型会让 H5 端与 tabBar 重叠。
 *   2. 卡片二级操作必须阻止冒泡：卡片本身点击进详情，勾选/加购不 stop
 *      就会把用户弹到商品详情页。
 *   3. 勾选态必须走共享 store：若各楼层各自维护，底部条只会反映最后
 *      一个组件的选择。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * 只取 <style> 块。
 *
 * 断言「不得硬编码 bottom」时必须排除注释与脚本文案 ——
 * 组件注释里正解释「不要抄原型的 bottom:56px」，直接全文匹配会自我误伤。
 */
function styleBlock(sourceCode: string) {
  return sourceCode.match(/<style[^>]*>([\s\S]*?)<\/style>/)?.[1] ?? ''
}

describe('批量加购条布局契约', () => {
  it('底部条用 --window-bottom 定位，不硬编码 tabBar 高度', () => {
    const style = styleBlock(source('src/components/batch-cart-bar/index.vue'))
    expect(style).toContain('bottom: var(--window-bottom, 0px)')
    // 硬编码会让 H5 与小程序两端必有一端错位
    expect(style).not.toMatch(/bottom:\s*56px/)
    expect(style).not.toMatch(/bottom:\s*\d+px\s*;/)
  })

  it('底部条 z-index 低于 SKU 弹层（990），避免盖住规格选择', () => {
    const bar = styleBlock(source('src/components/batch-cart-bar/index.vue'))
    const match = bar.match(/z-index:\s*(\d+)/)
    expect(match).toBeTruthy()
    expect(Number(match![1])).toBeLessThan(990)
  })

  it('首页挂载了批量加购条', () => {
    const home = source('src/pages/home/index.vue')
    expect(home).toContain('BatchCartBar')
  })
})

describe('商品卡勾选契约', () => {
  it('勾选方块阻止冒泡，避免点勾选跳进商品详情', () => {
    const checkbox = source('src/components/goods-pick-checkbox/index.vue')
    expect(checkbox).toContain('@tap.stop="handleToggle"')
  })

  it('勾选态走共享 store，不在组件内各自维护', () => {
    const checkbox = source('src/components/goods-pick-checkbox/index.vue')
    expect(checkbox).toContain('useGoodsPickStore')
    // 组件内自己维护一份选中态就会与底部条总数不一致
    expect(checkbox).not.toMatch(/const\s+picked\s*=\s*ref\(/)
  })

  it('瀑布流与商品面板都接入了勾选与信息 chip', () => {
    for (const file of [
      'src/components/diy/diy-goods-waterfall/index.vue',
      'src/components/goods-list-panel/index.vue',
    ]) {
      const src = source(file)
      expect(src, `${file} 缺少勾选方块`).toContain('goods-pick-checkbox')
      expect(src, `${file} 缺少信息 chip`).toContain('cardTags')
    }
  })
})

describe('勾选态 store 契约', () => {
  it('只保存 SPU ID，不算数量（数量规则归服务端）', () => {
    const store = source('src/store/goodsPickStore.ts')
    expect(store).toContain('pickedIds')
    expect(store).not.toContain('quantity')
  })

  it('不持久化：临时意图不该跨会话残留', () => {
    const persist = source('src/store/persist.ts')
    expect(persist).toContain('\'goodsPick\'')
  })
})
