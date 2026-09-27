import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

import { pages, subPackages } from '@/pages.json'

/**
 * 跳转目标的静态守卫。
 *
 * 背景（真实缺陷）：装修组件 `diy-video-live` 的「直播入口」跳向
 * `/sub-pages/promotion/live/index`，但该页面在全仓**从未创建**。
 * 点一次就失败一次，微信开发者工具报
 * `routeDone with a webviewId xxx is not found`。
 *
 * 页面是否存在的真相只有 `pages.json`（由 uni-pages 从 `definePage` 生成），
 * 因此这里直接拿源码里的路由字面量与它对账，而不是靠人工 review。
 */

const projectRoot = fileURLToPath(new URL('..', import.meta.url))

/** 从 pages.json 收集已声明的完整路由（`/pages/x`、`/sub-pages/x/y`） */
function declaredRoutes() {
  const routes = new Set<string>()
  for (const page of pages)
    routes.add(`/${page.path}`)
  for (const subPackage of (subPackages ?? []) as Array<{ root: string, pages: Array<{ path: string }> }>) {
    for (const page of subPackage.pages)
      routes.add(`/${subPackage.root}/${page.path}`)
  }
  return routes
}

function walkSourceFiles(dir: string, files: string[] = []) {
  for (const entry of readdirSync(dir)) {
    const fullPath = join(dir, entry)
    if (statSync(fullPath).isDirectory()) {
      walkSourceFiles(fullPath, files)
      continue
    }
    if (!/\.(?:vue|ts)$/.test(entry) || /\.test\.ts$/.test(entry))
      continue
    files.push(fullPath)
  }
  return files
}

/**
 * 剥离注释。
 *
 * 注释里出现的路径（例如「原本跳向 /sub-pages/xxx，因未实现已改为提示」）
 * 不是跳转目标，扫进来会产生假失败。用状态机而非正则：字符串里的 `//`
 * （如 `https://`、`/sub-pages//x`）不能被当成注释起点。
 */
function stripComments(source: string) {
  let result = ''
  let inString: string | null = null
  let inLineComment = false
  let inBlockComment = false

  for (let i = 0; i < source.length; i++) {
    const char = source[i]
    const next = source[i + 1]

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

/** 源码里出现的站内路由字面量（去 query、去尾斜杠） */
function referencedRoutes(source: string) {
  const found = new Set<string>()
  const pattern = /['"`](\/(?:pages|sub-pages)\/[\w\-/]*)[?'"`\s]/g
  const code = stripComments(source)
  let match = pattern.exec(code)
  while (match) {
    const route = match[1].replace(/\/+$/, '')
    if (route)
      found.add(route)
    match = pattern.exec(code)
  }
  return found
}

/**
 * 允许存在的「非当前页面」引用：
 *   · 历史路径映射表 —— 它们字面上就是**旧路径**，用于迁移到新路径
 *   · 配送路由前缀 —— 是前缀常量而非具体页面
 */
const ALLOWED = new Set([
  'pages/product/goods-detail/index',
  'pages/shop/diy-page/index',
  'pages/delivery',
].map(route => `/${route}`))

describe('跳转目标必须在 pages.json 中声明', () => {
  it('源码引用的站内路由都已注册', () => {
    const routes = declaredRoutes()
    const offenders: string[] = []

    for (const file of walkSourceFiles(resolve(projectRoot, 'src'))) {
      const source = readFileSync(file, 'utf8')
      for (const route of referencedRoutes(source)) {
        if (routes.has(route) || ALLOWED.has(route))
          continue
        offenders.push(`${relative(projectRoot, file)} → ${route}`)
      }
    }

    // 跳向未注册页面必然失败，且在开发者工具表现为 routeDone/webviewId 报错；
    // 新增页面时先确认 definePage 存在（据此生成 pages.json），再写跳转。
    expect(offenders).toEqual([])
  })

  it('守卫本身有效：能识别出未声明的路由', () => {
    const routes = declaredRoutes()
    // 反向验证解析逻辑没有空转
    expect(routes.has('/sub-pages/product/goods-detail/index')).toBe(true)
    expect(routes.has('/sub-pages/promotion/live/index')).toBe(false)
    expect(referencedRoutes('uni.navigateTo({ url: \'/sub-pages/promotion/live/index\' })'))
      .toContain('/sub-pages/promotion/live/index')
  })
})
