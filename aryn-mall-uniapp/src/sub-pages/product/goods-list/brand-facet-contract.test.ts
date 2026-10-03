import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * 品牌筛选条的分面语义守卫。
 *
 * 背景（真实缺陷）：金刚区点分类进入商品列表页，顶部品牌条拉的是**全租户**品牌
 * （42 个），与当前分类无关。而品牌在本租户是稀疏属性（272 件在售商品仅 62 件有品牌），
 * 水果、海鲜水产、手机数码、家用电器、鲜花绿植、精选冻品、船舶物料这 7 个一级分类下
 * **全部商品都没有品牌** —— 用户点任何一个品牌都得到空列表，且没有任何解释。
 *
 * 修法是把品牌条改成分面导航：选项由服务端按当前条件聚合，只返回有货品牌。
 * 这类回归编译、类型检查、既有单测都拦不住（接口名变了但调用方没变、或悄悄换回全量接口），
 * 因此这里把「页面用分面接口 + 无选项时隐藏整条」绑成静态契约。
 */
describe('商品列表页品牌筛选条', () => {
  const PAGE = 'src/sub-pages/product/goods-list/index.vue'
  const API = 'src/sub-pages/api/product/brand.ts'
  const PANEL = 'src/components/goods-list-panel/index.vue'

  it('页面用按条件聚合的 filter-list，而不是全量品牌列表', () => {
    const page = source(PAGE)

    expect(page).toContain('getFilterList')
    expect(page).toContain('/sub-pages/api/product/brand')
    // 全量列表接口不感知分类/关键词，用它渲染筛选条必然产生死选项
    expect(page).not.toContain('getList as getBrandList')
  })

  it('品牌条把当前分类与关键词作为分面条件传给服务端', () => {
    const page = source(PAGE)

    // 三个条件缺一不可：漏掉任一个都会让别的维度下重新出现死选项
    expect(page).toMatch(/params\.categoryFirstId\s*=/)
    expect(page).toMatch(/params\.categorySecondId\s*=/)
    expect(page).toMatch(/params\.name\s*=/)
  })

  it('无品牌可选时隐藏整条筛选，但布局切换仍常驻', () => {
    const page = source(PAGE)

    expect(page).toMatch(/showBrandFilter\s*=\s*computed/)
    // 整条随分面结果为空而消失
    expect(page).toContain('v-if="showBrandFilter"')
    // 布局切换按钮不能被同一个 v-if 带走，否则该分类下无法切栅格/列表
    const filterBar = page.slice(page.indexOf('class="filter-bar"'))
    const toggleIndex = filterBar.indexOf('layout-toggle')
    expect(toggleIndex).toBeGreaterThan(0)
    expect(filterBar.slice(0, toggleIndex)).toContain('v-if="showBrandFilter"')
    expect(filterBar.slice(0, toggleIndex)).toContain('v-else')
  })

  it('品牌 chip 展示商品数，点击前可判断有无货', () => {
    const page = source(PAGE)

    expect(page).toContain('goodsCount')
  })

  it('空结果给出清除筛选的出口，且不把加载失败说成没数据', () => {
    const panel = source(PANEL)

    // 品牌选中项状态在页面侧，面板只能通过回调请父组件清空
    expect(panel).toContain('clearFilter')
    expect(panel).toContain('#empty')
    expect(panel).toContain('清除筛选')
    // 请求失败与无数据必须区分（船供列表踩过同一个坑）
    expect(panel).toContain('loadFailed')
    expect(panel).toContain('加载失败')
  })

  it('keeps both brand endpoints under the C-end product domain', () => {
    const api = source(API)

    expect(api).toContain('/product/app/goodsbrand/list')
    expect(api).toContain('/product/app/goodsbrand/filter-list')
    // C 端请求必须免登（金刚区落地页无登录态）
    expect(api.match(/skipToken: true/g)?.length).toBe(2)
  })
})
