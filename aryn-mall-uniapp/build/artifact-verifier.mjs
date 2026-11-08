import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { auditMpPackage } from './mp-package-audit.mjs'

/**
 * 产物最终校验（子进程 close 后在父进程执行，是发布前的权威门）。
 *
 * 检查项：manifest / project.config.json / app.json 路由与资源 / bundle 身份证据 /
 * env 固化一致 / 禁止文件缺席 / 产物摘要。任何一项失败 => 不发布。
 */

export class ArtifactVerifyError extends Error {
  constructor(message) {
    super(message)
    this.name = 'ArtifactVerifyError'
  }
}

function sha256File(file) {
  const hash = crypto.createHash('sha256')
  hash.update(fs.readFileSync(file))
  return hash.digest('hex')
}

function readJson(file, label) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
  }
  catch (error) {
    throw new ArtifactVerifyError(`${label} 解析失败: ${error.message}`)
  }
}

function listFilesRecursive(root) {
  const acc = []
  if (!fs.existsSync(root)) {
    return acc
  }
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const abs = path.join(root, entry.name)
    if (entry.isDirectory()) {
      acc.push(...listFilesRecursive(abs))
    }
    else if (entry.isFile()) {
      acc.push(abs)
    }
  }
  return acc
}

const FORBIDDEN_FILE_PATTERNS = [
  /\.env(\.|$)/i,
  /project\.private\.config\.json$/i,
  /\.pem$/i,
  /\.key$/i,
  /\.p12$/i,
  /artifact-evidence\.json$/i,
]

function verifyNoForbiddenFiles(outputDir) {
  const offenders = []
  for (const abs of listFilesRecursive(outputDir)) {
    const rel = path.relative(outputDir, abs)
    if (FORBIDDEN_FILE_PATTERNS.some(re => re.test(rel))) {
      offenders.push(rel)
    }
  }
  if (offenders.length > 0) {
    throw new ArtifactVerifyError(`产物中存在禁止文件: ${offenders.join(', ')}`)
  }
}

function verifyProjectConfig(outputDir, normalized) {
  const project = readJson(path.join(outputDir, 'project.config.json'), 'project.config.json')
  if (project.appid !== normalized.wxAppId) {
    throw new ArtifactVerifyError(`project.config.json appid 期望 ${normalized.wxAppId}，实际 ${project.appid}`)
  }
  if (project.projectname !== normalized.name) {
    throw new ArtifactVerifyError(`project.config.json projectname 期望 "${normalized.name}"，实际 "${project.projectname}"`)
  }
  if (project.compileType !== 'miniprogram') {
    throw new ArtifactVerifyError(`project.config.json compileType 期望 miniprogram，实际 ${project.compileType}`)
  }
  const root = project.miniprogramRoot ?? './'
  if (root !== './' && root !== '') {
    throw new ArtifactVerifyError(`project.config.json miniprogramRoot 期望 "./"，实际 "${root}"`)
  }
}

function verifyManifest(snapshotAppRoot, normalized) {
  const manifest = readJson(path.join(snapshotAppRoot, 'src', 'manifest.json'), '副本 manifest.json')
  if (manifest.name !== normalized.name) {
    throw new ArtifactVerifyError(`manifest name 期望 "${normalized.name}"，实际 "${manifest.name}"`)
  }
  if (manifest['mp-weixin']?.appid !== normalized.wxAppId) {
    throw new ArtifactVerifyError(`manifest mp-weixin.appid 期望 ${normalized.wxAppId}，实际 ${manifest['mp-weixin']?.appid}`)
  }
  if (normalized.aliAppId && manifest['mp-alipay']?.appid !== normalized.aliAppId) {
    throw new ArtifactVerifyError(`manifest mp-alipay.appid 期望 ${normalized.aliAppId}，实际 ${manifest['mp-alipay']?.appid}`)
  }
}

function verifyAlipayManifest(snapshotAppRoot, outputDir, normalized) {
  const manifest = readJson(path.join(snapshotAppRoot, 'src', 'manifest.json'), '副本 manifest.json')
  if (manifest.name !== normalized.name) {
    throw new ArtifactVerifyError(`manifest name 期望 "${normalized.name}"，实际 "${manifest.name}"`)
  }
  if (normalized.aliAppId && manifest['mp-alipay']?.appid !== normalized.aliAppId) {
    throw new ArtifactVerifyError(`manifest mp-alipay.appid 期望 ${normalized.aliAppId}，实际 ${manifest['mp-alipay']?.appid}`)
  }
  const miniProject = readJson(path.join(outputDir, 'mini.project.json'), 'mini.project.json')
  const declaredAppId = miniProject.appId || miniProject.appid
  if (normalized.aliAppId && declaredAppId && declaredAppId !== normalized.aliAppId) {
    throw new ArtifactVerifyError(`mini.project.json appId 期望 ${normalized.aliAppId}，实际 ${declaredAppId}`)
  }
}

function verifyAppJson(outputDir) {
  const appJson = readJson(path.join(outputDir, 'app.json'), 'app.json')
  const dangling = []
  const pages = [...(appJson.pages ?? [])]
  for (const sub of appJson.subPackages ?? []) {
    for (const page of sub.pages ?? []) {
      pages.push(`${sub.root}/${page}`)
    }
  }
  for (const page of pages) {
    // 微信小程序页面编译产物：同目录 .js 必须存在
    if (!fs.existsSync(path.join(outputDir, `${page}.js`))) {
      dangling.push(page)
    }
  }
  if (dangling.length > 0) {
    throw new ArtifactVerifyError(`app.json 存在悬空路由（无编译产物）: ${dangling.join(', ')}`)
  }
  // tabBar 图标资源必须存在
  const missing = []
  for (const item of appJson.tabBar?.list ?? []) {
    for (const icon of [item.iconPath, item.selectedIconPath]) {
      if (icon && !fs.existsSync(path.join(outputDir, icon))) {
        missing.push(icon)
      }
    }
  }
  if (missing.length > 0) {
    throw new ArtifactVerifyError(`tabBar 图标缺失: ${missing.join(', ')}`)
  }
  return { pageCount: pages.length, tabBarCount: appJson.tabBar?.list?.length ?? 0 }
}

function verifyThemeResources(outputDir) {
  if (!fs.existsSync(path.join(outputDir, 'theme.json'))) {
    throw new ArtifactVerifyError('产物缺少 theme.json（manifest 开启 darkmode/themeLocation）')
  }
}

function verifyIdentityEvidence(evidencePath, normalized) {
  const evidence = readJson(evidencePath, 'artifact-evidence.json')
  const n = evidence.normalized ?? {}
  for (const key of ['key', 'tenantId', 'wxAppId', 'aliAppId', 'name', 'profile', 'deployment', 'apiBaseUrl', 'buildId']) {
    if (String(n[key] ?? '') !== String(normalized[key] ?? '')) {
      throw new ArtifactVerifyError(`身份证据 ${key} 期望 "${normalized[key] ?? ''}"，实际 "${n[key] ?? ''}"`)
    }
  }
  if (evidence.identityReachable !== true) {
    throw new ArtifactVerifyError('身份模块未通过入口可达性校验')
  }
  const env = evidence.effectiveEnv ?? {}
  const expectedEnv = {
    VITE_TENANT_BUILD: 'true',
    VITE_TENANT_ID: normalized.tenantId,
    VITE_TENANT_WX_APPID: normalized.wxAppId,
    VITE_TENANT_ALI_APPID: normalized.aliAppId || '',
    VITE_OPEN_BOOT: String(normalized.openBoot),
    VITE_API_BASE_URL: normalized.apiBaseUrl,
    VITE_ENV_NAME: normalized.profile,
    VITE_BUILD_ID: normalized.buildId,
  }
  for (const [key, value] of Object.entries(expectedEnv)) {
    if (String(env[key] ?? '') !== String(value)) {
      throw new ArtifactVerifyError(`固化 env ${key} 期望 "${value}"，实际 "${env[key] ?? ''}"`)
    }
  }
  return evidence
}

function checksumsFor(outputDir) {
  const checksums = {}
  for (const abs of listFilesRecursive(outputDir)) {
    const rel = path.relative(outputDir, abs).split(path.sep).join('/')
    checksums[rel] = { size: fs.statSync(abs).size, sha256: sha256File(abs) }
  }
  return checksums
}

/**
 * 执行完整校验，返回 verification 数据；失败抛 ArtifactVerifyError。
 */
export function verifyArtifact({ outputDir, snapshotAppRoot, normalized, evidencePath }) {
  verifyNoForbiddenFiles(outputDir)
  verifyProjectConfig(outputDir, normalized)
  verifyManifest(snapshotAppRoot, normalized)
  const appStats = verifyAppJson(outputDir)
  verifyThemeResources(outputDir)
  const packageAudit = auditMpPackage(outputDir)
  if (!packageAudit.ok) {
    throw new ArtifactVerifyError(`分包审计失败:\n${packageAudit.errors.join('\n')}`)
  }
  const evidence = verifyIdentityEvidence(evidencePath, normalized)

  const checksums = checksumsFor(outputDir)
  const totalBytes = Object.values(checksums).reduce((sum, item) => sum + item.size, 0)
  const verification = {
    ok: true,
    verifiedAt: new Date().toISOString(),
    buildId: normalized.buildId,
    key: normalized.key,
    profile: normalized.profile,
    deployment: normalized.deployment,
    appStats,
    packageSizes: packageAudit.sizes,
    identityChunks: evidence.identityChunks ?? [],
    fileCount: Object.keys(checksums).length,
    totalBytes,
  }
  return { verification, checksums }
}

/**
 * 支付宝小程序产物校验：不检查微信 project.config/the miniprogramRoot，
 * 但同样校验禁止文件、路由产物、身份证据与支付宝 AppID 固化。
 */
export function verifyAlipayArtifact({ outputDir, snapshotAppRoot, normalized, evidencePath }) {
  verifyNoForbiddenFiles(outputDir)
  verifyAlipayManifest(snapshotAppRoot, outputDir, normalized)
  const appStats = verifyAppJson(outputDir)
  // mp-package-audit 按微信 require 图与微信主包上限设计，不能套用到支付宝产物；
  // 支付宝侧仍由 app.json 路由产物、禁止文件与身份证据三项把关。
  const evidence = verifyIdentityEvidence(evidencePath, normalized)
  const checksums = checksumsFor(outputDir)
  const totalBytes = Object.values(checksums).reduce((sum, item) => sum + item.size, 0)
  const verification = {
    ok: true,
    platform: 'mp-alipay',
    verifiedAt: new Date().toISOString(),
    buildId: normalized.buildId,
    key: normalized.key,
    profile: normalized.profile,
    deployment: normalized.deployment,
    appStats,
    identityChunks: evidence.identityChunks ?? [],
    fileCount: Object.keys(checksums).length,
    totalBytes,
  }
  return { verification, checksums }
}

export { sha256File }
