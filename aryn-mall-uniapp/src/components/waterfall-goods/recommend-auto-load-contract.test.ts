import { readdirSync, readFileSync, statSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 推荐商品流「滚动加载」契约。
 *
 * 背景（2026-10-26）：购物车「为你推荐」、商详「猜你喜欢」、个人中心「猜你喜欢」
 * 等推荐位滚到底部一直显示「点击加载更多」，必须再点一下才继续翻页，与「自动加载、
 * 无感浏览」的预期不符。根因不是接口分页，而是 z-paging 的 use-page-scroll 模式
 * 不会自己监听页面滚动：宿主页没把页面滚动/触底广播给组件，z-paging 便停在默认态
 * 渲染可点击文案。
 *
 * 修复后锁住三条契约，防止回归：
 *   1. 推荐组件本身必须是「预加载」形态：页面滚动广播 + 组件测距续页 + 旧上下文
 *      响应丢弃，且默认态不渲染可点文案（加载中 / 没有更多 / 加载失败文案必须保留）；
 *   2. **不能**用 `completeByTotal`：推荐接口后端 `page.setSearchCount(false)`，不下发
 *      真实 total（GoodsSpuServiceImpl#recommendPage 注释：前端只按返回条数判断是否
 *      还有下一页）。total=0 会被 z-paging 判成「没有更多」，第一页后永远不再加载；
 *   3. 任何渲染 WaterfallGoods 的宿主页都必须调用 useDecorationInfiniteScroll()，
 *      否则组件收不到滚动信号——列表会在第一页悄悄停住，比显示可点文案更糟。
 *
 * 断言源码而非渲染结果：小程序组件依赖 uni 运行时，单测跑不起来；而漏接线、
 * 漏测距、误用 completeByTotal 恰恰都能在源码层面发现（同 original-price-contract.test.ts
 * 的思路）。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))
const srcDir = resolve(projectRoot, 'src')
const COMPONENT = 'src/components/waterfall-goods/index.vue'
/** 组件模板根节点 class：useDiyPagedLoadMore 用同名选择器量自身底边位置 */
const ROOT_CLASS = 'waterfall-goods'

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 去掉注释，避免「注释里提到某某」被当成真实调用（契约只认代码） */
function stripComments(code: string) {
  return code
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/[^\n]*/g, '')
}

function walkVueFiles(dir: string, found: string[] = []) {
  for (const entry of readdirSync(dir)) {
    const fullPath = resolve(dir, entry)
    if (statSync(fullPath).isDirectory()) {
      walkVueFiles(fullPath, found)
      continue
    }
    if (entry.endsWith('.vue'))
      found.push(fullPath)
  }
  return found
}

/**
 * 自动发现所有渲染推荐商品流的宿主页。
 *
 * 不写死文件清单：新增页面（例如收藏页也挂推荐位）忘记接页面侧广播时，
 * 本契约会直接失败，而不是等用户在一页里发现「怎么滑不动了」。
 */
function recommendationHosts() {
  return walkVueFiles(srcDir)
    // 同时覆盖 pascal-case（自动注册的组件标签）与 kebab-case 两种写法
    .filter(file => /<(?:WaterfallGoods|waterfall-goods)[\s>/]/i.test(stripComments(readFileSync(file, 'utf8'))))
    .map(file => relative(projectRoot, file))
    .sort()
}

describe('推荐商品流滚动加载', () => {
  it('推荐组件以页面滚动 + 测距预加载续页，不再依赖点击', () => {
    const code = source(COMPONENT)
    // 继续复用页面滚动：推荐位就在宿主页长页面的尾部，不能改成组件内独立滚动
    expect(code).toMatch(/use-page-scroll/)
    expect(code).toContain('useDiyPagedLoadMore')
    // 选择器必须与模板根节点 class 一致，否则测距永远量不到节点、不会自动翻页
    expect(code).toMatch(new RegExp(`useDiyPagedLoadMore\\(pagingRef,\\s*'\\.${ROOT_CLASS}'`))
    expect(code).toMatch(new RegExp(`<view class="[^"]*\\b${ROOT_CLASS}\\b`))
    // @query 处理器必须包进 runQuery：加载完成后才会自愈重测（撞上加载中被丢弃也能补上）
    expect(code).toMatch(/await runQuery\(/)
    // 推荐位可能在首屏（空购物车）：挂载即补页会与 z-paging 首次 reload 抢跑，必须关掉；
    // 关掉后 page 2 只在 page 1 成功返回后由自愈链路发出（composable 内的首查门禁）
    expect(code).toMatch(/preloadOnMount:\s*false/)
    // 上下文异步到齐会 reload：旧上下文的在途响应必须丢弃，否则两次 page1 都会 append
    expect(code).toContain('contextEpoch')
    expect(code).toMatch(/epoch !== contextEpoch/)
    expect(code).toMatch(/isCurrent|\(\) => epoch === contextEpoch/)
  })

  it('按本页条数推断「没有更多」，不得用 completeByTotal（后端关闭了 count）', () => {
    const code = stripComments(source(COMPONENT))
    // 后端 GoodsSpuServiceImpl#recommendPage: page.setSearchCount(false)，total 恒为 0/缺省，
    // 用 completeByTotal 会把第一页永久判成「没有更多」
    expect(code).not.toContain('completeByTotal')
    expect(code).toMatch(/await pagingRef\.value\?\.complete\(/)
    expect(code).toContain('records')
  })

  it('默认态不再渲染可点击的「点击加载更多」', () => {
    const code = source(COMPONENT)
    // 默认态文案置空（与装修商品楼层同一口径）；否则滑动过程中仍会出现可点文案
    expect(code).toMatch(/loading-more-default-text=""/)
    // 加载中 / 没有更多 / 加载失败三种状态文案必须保留，失败仍可手点重新加载
    expect(code).toContain('loading-more-loading-text')
    expect(code).toContain('loading-more-no-more-text')
    expect(code).toContain('loading-more-fail-text')
  })

  it('每个推荐位宿主页都调用了页面滚动广播', () => {
    const hosts = recommendationHosts()
    // 扫描失效（例如文件结构变动）时不要空转通过：当前至少有 5 个宿主页
    expect(hosts.length).toBeGreaterThanOrEqual(5)
    for (const file of hosts) {
      // 只 import 不算接线：必须真的在 setup 里调用
      expect(stripComments(source(file)), `${file} 渲染了 WaterfallGoods 但没调用 useDecorationInfiniteScroll()`)
        .toMatch(/useDecorationInfiniteScroll\s*\(\s*\)/)
    }
  })
})
