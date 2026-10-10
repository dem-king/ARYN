import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'

/**
 * 独立构建副本：白名单复制 + SHA-256 摘要核对 + 路径安全。
 *
 * - 只复制白名单；.env*、测试文件、tenants、私钥、project.private.config.json 一律不进副本。
 * - 源码 symlink 拒绝复制（node_modules 链接由调用方在复制完成后单独创建）。
 * - 检测到 src/project.wx.json / src/project.config.json 预检失败（它们会覆盖 manifest 派生工程配置）。
 * - 复制完成后核对副本摘要，并回查原目录文件集是否在复制期间变化（dev watcher 回写检测）。
 */

export class SnapshotError extends Error {
  constructor(message) {
    super(message)
    this.name = 'SnapshotError'
  }
}

const COPY_TOP_LEVEL = [
  'package.json',
  'pnpm-lock.yaml',
  'tsconfig.json',
  'index.html',
  'vite.config.ts',
  'manifest.config.ts',
  'pages.config.ts',
  'uno.config.ts',
  'async-import.d.ts',
  'async-component.d.ts',
]

const COPY_DIRS = ['src', 'build']

const SRC_EXCLUDE_PATTERNS = [
  /(^|\/)node_modules(\/|$)/,
  /(^|\/)dist(\/|$)/,
  /(^|\/)\.git(\/|$)/,
  /(^|\/)__tests__(\/|$)/,
  /\.test\.[cm]?[jt]sx?$/,
  /\.spec\.[cm]?[jt]sx?$/,
  /(^|\/)project\.private\.config\.json$/,
  /(^|\/)tenant-build\.ts$/, // 由构建脚本按身份重新生成，不带默认版本
]

function fail(message) {
  throw new SnapshotError(message)
}

function sha256File(file) {
  const hash = crypto.createHash('sha256')
  hash.update(fs.readFileSync(file))
  return hash.digest('hex')
}

function isExcluded(relPosix) {
  return SRC_EXCLUDE_PATTERNS.some(re => re.test(relPosix))
}

/** 递归收集待复制文件（相对路径 POSIX 形式）。lstat 拒绝 symlink。 */
function collectFiles(absDir, baseDir, acc) {
  const entries = fs.readdirSync(absDir, { withFileTypes: true })
  for (const entry of entries) {
    const absPath = path.join(absDir, entry.name)
    const relPosix = path.relative(baseDir, absPath).split(path.sep).join('/')
    if (isExcluded(relPosix)) {
      continue
    }
    let stat
    try {
      stat = fs.lstatSync(absPath)
    }
    catch (error) {
      fail(`读取 ${absPath} 失败: ${error.message}`)
    }
    if (stat.isSymbolicLink()) {
      fail(`源码不允许 symlink: ${relPosix}`)
    }
    if (stat.isDirectory()) {
      collectFiles(absPath, baseDir, acc)
    }
    else if (stat.isFile()) {
      acc.push({ relPosix, absPath, size: stat.size })
    }
  }
  return acc
}

export function assertNoConflictingProjectFiles(projectRoot) {
  const conflicts = ['src/project.wx.json', 'src/project.config.json', 'project.config.json', 'project.wx.json']
  const found = conflicts.filter(rel => fs.existsSync(path.join(projectRoot, rel)))
  if (found.length > 0) {
    fail(`检测到会覆盖 manifest 派生工程配置的文件：${found.join(', ')}。请先删除或完成显式受控迁移后再执行租户构建`)
  }
}

/**
 * 创建副本并返回摘要清单。
 * @returns {{ inventory: Array<{path:string,size:number,sha256:string}>, sourceRoot: string }}
 */
export function createSnapshot({ projectRoot, appRoot }) {
  assertNoConflictingProjectFiles(projectRoot)

  /** @type {Array<{relPosix:string,absPath:string,size:number}>} */
  const wanted = []
  for (const rel of COPY_TOP_LEVEL) {
    const abs = path.join(projectRoot, rel)
    if (fs.existsSync(abs)) {
      wanted.push({ relPosix: rel, absPath: abs, size: fs.lstatSync(abs).size })
    }
  }
  for (const dir of COPY_DIRS) {
    const abs = path.join(projectRoot, dir)
    if (!fs.existsSync(abs)) {
      fail(`项目缺少必需目录 ${dir}/`)
    }
    collectFiles(abs, projectRoot, wanted)
  }

  fs.mkdirSync(appRoot, { recursive: true })
  const copied = []
  for (const item of wanted) {
    const target = path.join(appRoot, item.relPosix)
    fs.mkdirSync(path.dirname(target), { recursive: true })
    fs.copyFileSync(item.absPath, target)
    copied.push({ relPosix: item.relPosix, absSource: item.absPath, absTarget: target, size: item.size })
  }

  // 副本摘要核对
  const inventory = copied.map((item) => {
    const stat = fs.lstatSync(item.absTarget)
    if (stat.isSymbolicLink()) {
      fail(`副本内出现 symlink: ${item.relPosix}`)
    }
    if (stat.size !== item.size) {
      fail(`副本大小不一致: ${item.relPosix}`)
    }
    return { path: item.relPosix, size: stat.size, sha256: sha256File(item.absTarget) }
  })

  // 回查原目录：复制期间源文件被改写（或 dev 回写生成物）则本次快照不成立
  for (const item of copied) {
    let stat
    try {
      stat = fs.lstatSync(item.absSource)
    }
    catch {
      fail(`复制期间源文件消失: ${item.relPosix}`)
    }
    if (stat.isSymbolicLink() || stat.size !== item.size || sha256File(item.absSource) !== sha256File(item.absTarget)) {
      fail(`复制期间源文件发生变化: ${item.relPosix}（若 dev watcher 正在运行请重试）`)
    }
  }

  return { inventory }
}

/**
 * 重新计算当前副本与 inventory 的差异，用于构建前/后校验。
 */
export function verifySnapshot(appRoot, inventory) {
  for (const item of inventory) {
    const abs = path.join(appRoot, item.path)
    if (!fs.existsSync(abs)) {
      return { ok: false, reason: `副本文件缺失: ${item.path}` }
    }
    if (fs.lstatSync(abs).isSymbolicLink()) {
      return { ok: false, reason: `副本文件变成 symlink: ${item.path}` }
    }
    if (sha256File(abs) !== item.sha256) {
      return { ok: false, reason: `副本文件摘要变化: ${item.path}` }
    }
  }
  return { ok: true }
}

/** 记录副本源文件集合（相对路径排序），供 verifySnapshot 复用与追溯。 */
export function summarizeInventory(inventory) {
  return {
    fileCount: inventory.length,
    totalBytes: inventory.reduce((sum, item) => sum + item.size, 0),
    sha256: crypto.createHash('sha256')
      .update(inventory.map(item => `${item.path}:${item.sha256}`).join('\n'))
      .digest('hex'),
  }
}
