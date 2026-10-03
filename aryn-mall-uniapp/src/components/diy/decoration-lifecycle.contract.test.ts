import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 装修组件（`components/diy/*`）数据装载接线守门。
 *
 * 这类缺陷的漏网方式（2026-09-29 首页补给单卡「登录成功后没立即出现，
 * 切到购物车再切回首页才出现」）：
 *
 * 首页装修内容是页面 `onLoad` 里异步拉回来才渲染的，装修组件挂载时
 * **页面的 onShow 早就跑完了**。组件若只用 `onShow` 拉数据，在微信小程序端
 * 就永远等不到第一次调用——`@dcloudio/uni-mp-vue` 的 injectHook 里没有
 * 「迟到注册的页面钩子立即补调」这段（`isRootImmediateHook` 只存在于
 * uni-h5-vue）。于是小程序端首屏不显示、切 Tab 回来才出现，而 H5 正常，
 * 两端行为不一致且不报错；类型检查、单测都发现不了。
 *
 * 因此所有自带数据装载的装修组件都必须走 `usePageShowLoad`（onMounted + onShow）。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 装修组件目录下自带数据请求、必须自行接线的组件 */
const LOADING_COMPONENTS = [
  'src/components/diy/diy-replenish-card/index.vue',
  'src/components/diy/diy-ship-workbench/index.vue',
]

describe('装修组件装载接线契约', () => {
  it('自带数据请求的装修组件必须同时挂在挂载与页面显示两处', () => {
    for (const path of LOADING_COMPONENTS) {
      const code = source(path)
      expect(code, `${path} 应使用 usePageShowLoad 而非只挂 onShow`).toContain('usePageShowLoad')
      // 裸 onShow 会让小程序端漏掉首屏（详见 usePageShowLoad 注释）
      expect(code, `${path} 不应直接 import onShow 自行拉取`).not.toMatch(
        /import \{[^}]*\bonShow\b[^}]*\} from '@dcloudio\/uni-app'/,
      )
    }
  })

  it('装修组件目录里不再有漏掉首屏的 onShow 数据装载', () => {
    const diyDir = resolve(projectRoot, 'src/components/diy')
    const offenders: string[] = []
    for (const entry of readdirSync(diyDir, { withFileTypes: true })) {
      if (!entry.isDirectory())
        continue
      const file = resolve(diyDir, entry.name, 'index.vue')
      let code: string
      try {
        code = readFileSync(file, 'utf8')
      }
      catch {
        continue
      }
      if (/import \{[^}]*\bonShow\b[^}]*\} from '@dcloudio\/uni-app'/.test(code))
        offenders.push(entry.name)
    }
    expect(offenders, '这些组件只挂 onShow 拉数据，小程序端首屏会漏').toEqual([])
  })
})
