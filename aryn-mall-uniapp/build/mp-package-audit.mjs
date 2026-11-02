import fs from 'node:fs'
import path from 'node:path'

/**
 * 微信小程序产物分包审计（只读 dist 产物，不依赖编译器内部）。
 *
 * 口径与开发者工具「代码质量」对齐：主包 JS 必须被主包入口（主包页面 / app.js /
 * 自定义 tabBar / 主包内组件）沿 require 链用到；跨包引用在运行时才报错，这里提前拦截。
 */

const KB = 1024
export const MP_MAIN_PACKAGE_HARD_LIMIT = 2048 * KB

export const DEFAULT_MP_PACKAGE_POLICY = {
  mainLimitBytes: MP_MAIN_PACKAGE_HARD_LIMIT,
  // 余量不足 ~7% 时告警，提醒新需求先做主包瘦身
  mainMarginWarnBytes: 1900 * KB,
  // 开发者工具代码质量建议值
  mainWarnBytes: 1536 * KB,
  subPackageLimitBytes: 2048 * KB,
  requiredSubPackageRoots: [],
}

const NOT_COUNTED = new Set(['project.config.json', 'project.private.config.json'])
const REQUIRE_RE = /require\(\s*(['"])([^'"]+)\1\s*\)/g

function toPosix(p) {
  return p.split(path.sep).join('/')
}

function listFiles(root, base = root, acc = []) {
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const abs = path.join(root, entry.name)
    if (entry.isDirectory()) {
      listFiles(abs, base, acc)
    }
    else if (entry.isFile()) {
      acc.push(toPosix(path.relative(base, abs)))
    }
  }
  return acc
}

function readJsonSafe(file) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
  }
  catch {
    return null
  }
}

/** 解析产物内引用为产物根相对路径；非本地路径（插件、npm 名）返回 null */
function resolveRef(fromFile, spec) {
  if (spec.startsWith('/')) {
    return path.posix.normalize(spec.slice(1))
  }
  if (spec.startsWith('.')) {
    return path.posix.normalize(path.posix.join(path.posix.dirname(fromFile), spec))
  }
  return null
}

function formatKB(bytes) {
  return `${(bytes / KB).toFixed(1)} KB`
}

export function auditMpPackage(outputDir, policy = DEFAULT_MP_PACKAGE_POLICY) {
  const p = { ...DEFAULT_MP_PACKAGE_POLICY, ...policy }
  const appJson = JSON.parse(fs.readFileSync(path.join(outputDir, 'app.json'), 'utf8'))
  const subRoots = (appJson.subPackages ?? appJson.subpackages ?? []).map(s => s.root.replace(/\/+$/, ''))
  const packageOf = rel => subRoots.find(r => rel === r || rel.startsWith(`${r}/`)) ?? null

  const files = listFiles(outputDir).filter(f => !NOT_COUNTED.has(f))
  const fileSet = new Set(files)
  const errors = []
  const warnings = []

  const sizes = { main: 0 }
  for (const r of subRoots) {
    sizes[r] = 0
  }
  for (const f of files) {
    sizes[packageOf(f) ?? 'main'] += fs.statSync(path.join(outputDir, f)).size
  }

  if (sizes.main > p.mainLimitBytes) {
    errors.push(`主包 ${formatKB(sizes.main)} 超过门禁上限 ${formatKB(p.mainLimitBytes)}（微信上传硬上限 ${formatKB(MP_MAIN_PACKAGE_HARD_LIMIT)}）`)
  }
  else if (sizes.main > p.mainMarginWarnBytes) {
    warnings.push(`主包 ${formatKB(sizes.main)} 距上传硬上限仅剩 ${formatKB(MP_MAIN_PACKAGE_HARD_LIMIT - sizes.main)}，新增主包内容前需先瘦身`)
  }
  else if (sizes.main > p.mainWarnBytes) {
    warnings.push(`主包 ${formatKB(sizes.main)} 超过代码质量建议值 ${formatKB(p.mainWarnBytes)}`)
  }
  for (const r of subRoots) {
    if (sizes[r] > p.subPackageLimitBytes) {
      errors.push(`分包 ${r} ${formatKB(sizes[r])} 超过上限 ${formatKB(p.subPackageLimitBytes)}`)
    }
  }

  // 每个文件的出边：JS 的 require 与 JSON 的 usingComponents（组件按 .js 计）
  const edgesOf = (file) => {
    const out = []
    if (file.endsWith('.js')) {
      const code = fs.readFileSync(path.join(outputDir, file), 'utf8')
      for (const m of code.matchAll(REQUIRE_RE)) {
        const target = resolveRef(file, m[2])
        if (target) {
          out.push(target.endsWith('.js') ? target : `${target}.js`)
        }
      }
      const json = file.replace(/\.js$/, '.json')
      if (fileSet.has(json)) {
        out.push(...edgesOf(json))
      }
    }
    else if (file.endsWith('.json')) {
      const usingComponents = readJsonSafe(path.join(outputDir, file))?.usingComponents ?? {}
      for (const spec of Object.values(usingComponents)) {
        const target = typeof spec === 'string' ? resolveRef(file, spec) : null
        if (target) {
          out.push(`${target}.js`)
        }
      }
    }
    return out
  }

  // 跨包引用：主包不得引用分包；分包之间不得互相引用
  for (const f of files) {
    if (!f.endsWith('.js') && !f.endsWith('.json')) {
      continue
    }
    const from = packageOf(f)
    for (const target of edgesOf(f)) {
      const to = packageOf(target)
      if (to && to !== from) {
        errors.push(`跨包引用：${f} → ${target}（${from ? `分包 ${from}` : '主包'}不能引用分包 ${to}）`)
      }
    }
  }

  const isComponentJs = (f) => {
    const json = f.replace(/\.js$/, '.json')
    return fileSet.has(json) && readJsonSafe(path.join(outputDir, json))?.component === true
  }
  const mainJs = files.filter(f => f.endsWith('.js') && !packageOf(f))
  const seeds = ['app.js', 'app.json']
  for (const page of appJson.pages ?? []) {
    seeds.push(`${page}.js`)
  }
  if (appJson.tabBar?.custom) {
    seeds.push('custom-tab-bar/index.js')
  }
  seeds.push(...mainJs.filter(isComponentJs))

  const reached = new Set()
  const queue = seeds.filter(s => fileSet.has(s))
  while (queue.length > 0) {
    const f = queue.pop()
    if (reached.has(f) || packageOf(f)) {
      continue
    }
    reached.add(f)
    for (const target of edgesOf(f)) {
      if (fileSet.has(target) && !reached.has(target)) {
        queue.push(target)
      }
    }
  }
  const unusedMainJs = mainJs.filter(f => !reached.has(f))
  for (const f of unusedMainJs) {
    errors.push(`主包 JS 未被主包使用（应进分包）：${f}`)
  }

  const mainPages = appJson.pages ?? []
  for (const root of p.requiredSubPackageRoots) {
    if (!subRoots.includes(root)) {
      errors.push(`app.json 缺少分包 ${root}`)
    }
    const leaked = mainPages.filter(page => page.startsWith(`${root}/`))
    if (leaked.length > 0) {
      errors.push(`分包 ${root} 的页面仍登记在主包 pages：${leaked.join(', ')}`)
    }
  }
  for (const item of appJson.tabBar?.list ?? []) {
    if (!mainPages.includes(item.pagePath)) {
      errors.push(`tabBar 页面必须在主包：${item.pagePath}`)
    }
  }

  return {
    ok: errors.length === 0,
    sizes,
    unusedMainJs,
    errors,
    warnings,
  }
}

export function formatAuditReport(result) {
  const lines = []
  for (const [name, bytes] of Object.entries(result.sizes)) {
    lines.push(`${name === 'main' ? '主包' : `分包 ${name}`}: ${formatKB(bytes)}`)
  }
  for (const w of result.warnings) {
    lines.push(`[警告] ${w}`)
  }
  for (const e of result.errors) {
    lines.push(`[失败] ${e}`)
  }
  lines.push(result.ok ? '分包审计通过' : `分包审计失败：${result.errors.length} 项`)
  return lines.join('\n')
}
