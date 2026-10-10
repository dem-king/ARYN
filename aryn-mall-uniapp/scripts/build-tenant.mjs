#!/usr/bin/env node
/**
 * 租户构建编排器。
 *
 * 固定顺序：解析参数 → 全量配置预检 → 工具/冲突检查 → UUID 独占目录与互斥锁
 *   → 白名单复制 + 摘要核对 → node_modules 链接与受控 env → 生成 manifest 与身份模块
 *   → spawn uni build → 等待 close（exitCode=0 且无信号）→ 产物校验
 *   → 写 build-meta/verification/checksums → 原子发布到不可变目录。
 *
 * 任何一步失败：ownership 记 FAILED，绝不发布，绝不污染原工作区
 * （原 src/manifest.json、src/pages.json、.env、node_modules 缓存都不写）。
 */
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { createRequire } from 'node:module'
import { fileURLToPath, pathToFileURL } from 'node:url'

// 测试接缝：故障流程测试注入独立 fixture 项目根；生产环境不设置该变量
const projectRoot = process.env.TENANT_BUILD_PROJECT_ROOT
  ? path.resolve(process.env.TENANT_BUILD_PROJECT_ROOT)
  : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(path.join(projectRoot, 'package.json'))

const { TenantConfigError, listTenantKeys, loadTenantConfig, precheckAll, resolveTarget, normalize, newBuildId, toDisplay } = await import(pathToFileURL(path.join(projectRoot, 'build', 'tenant-config.mjs')).href)
const { resolveBuildPaths, buildChildEnv } = await import(pathToFileURL(path.join(projectRoot, 'build', 'build-context.mjs')).href)
const { createSnapshot, verifySnapshot, summarizeInventory, SnapshotError } = await import(pathToFileURL(path.join(projectRoot, 'build', 'snapshot.mjs')).href)
const { verifyArtifact, ArtifactVerifyError, sha256File } = await import(pathToFileURL(path.join(projectRoot, 'build', 'artifact-verifier.mjs')).href)

class BuildFailedError extends Error {}

// ---------- 参数解析 ----------

function parseArgs(argv) {
  const args = { target: undefined, profile: undefined, deployment: undefined, dryRun: false }
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    if (arg === '--profile') {
      args.profile = argv[++i]
    }
    else if (arg === '--deployment') {
      args.deployment = argv[++i]
    }
    else if (arg === '--dry-run') {
      args.dryRun = true
    }
    else if (arg === '--help' || arg === '-h') {
      args.help = true
    }
    else if (!arg.startsWith('--')) {
      if (args.target !== undefined) {
        throw new BuildFailedError(`多余的位置参数: ${arg}`)
      }
      args.target = arg
    }
    else {
      throw new BuildFailedError(`未知参数: ${arg}`)
    }
  }
  if (!args.target && !args.help) {
    throw new BuildFailedError('缺少租户 key 或 all')
  }
  return args
}

function printHelp() {
  console.log(`用法:
  pnpm build:tenant <key>|all [--profile production|staging] [--deployment boot|cloud] [--dry-run]

不传 --profile/--deployment 时使用租户 defaultProfile/defaultDeployment；
生产 CI 必须显式传两维参数。--dry-run 只打印计划，不生成副本、不编译。`)
}

// ---------- 互斥锁 ----------

function acquireLock() {
  const lockDir = path.join(projectRoot, '.tenant-build')
  fs.mkdirSync(lockDir, { recursive: true })
  const lockFile = path.join(lockDir, 'build.lock')
  let fd
  try {
    fd = fs.openSync(lockFile, 'wx')
  }
  catch {
    let holder = ''
    try {
      holder = fs.readFileSync(lockFile, 'utf8')
    }
    catch {}
    throw new BuildFailedError(`已有租户构建在运行（.tenant-build/build.lock 由 ${holder.trim() || '未知进程'} 持有）；如确认无构建进程，请人工删除锁文件后重试`)
  }
  fs.writeFileSync(fd, `${process.pid} ${new Date().toISOString()}\n`, 'utf8')
  fs.closeSync(fd)
  return lockFile
}

function releaseLock(lockFile) {
  try {
    fs.unlinkSync(lockFile)
  }
  catch {}
}

// ---------- ownership ----------

const STATE_ORDER = ['CREATED', 'COPIED', 'COMPILED', 'VERIFIED', 'PUBLISHED']

function writeOwnership(paths, state, buildId, extra = {}) {
  const data = {
    project: 'aryn-mall-uniapp',
    buildId,
    state,
    createdAt: paths.createdAt,
    updatedAt: new Date().toISOString(),
    sourceRoot: projectRoot,
    ...extra,
  }
  fs.writeFileSync(paths.ownershipFile, JSON.stringify(data, null, 2), 'utf8')
}

// ---------- 生成物 ----------

function generateTenantIdentityModule(normalized) {
  return `/**
 * 由 scripts/build-tenant.mjs 生成 —— 构建身份的唯一事实来源。
 * 该模块必须被启动/请求守卫（src/api/core/tenant-identity.ts）实际消费；
 * 未被 import 时会被 tree-shaking，产物校验将失败。
 */
import type { TenantBuildIdentity } from '../api/core/tenant-build-types'

export const tenantBuildIdentity: Readonly<TenantBuildIdentity> | null = Object.freeze({
  schemaVersion: ${normalized.schemaVersion},
  key: '${normalized.key}',
  tenantId: '${normalized.tenantId}',
  wxAppId: '${normalized.wxAppId}',
  name: '${normalized.name.replace(/'/g, '\\\'')}',
  profile: '${normalized.profile}',
  deployment: '${normalized.deployment}',
  openBoot: ${normalized.openBoot},
  apiBaseUrl: '${normalized.apiBaseUrl}',
  buildId: '${normalized.buildId}',
}) as Readonly<TenantBuildIdentity>
`
}

async function generateManifestInCopy(appRoot, normalized) {
  // 从副本内工厂读取（不是原目录），保证副本自洽
  const factoryPath = path.join(appRoot, 'build', 'manifest-factory.mjs')
  const factory = await import(pathToFileURL(factoryPath).href)
  const manifest = factory.createManifest({
    name: normalized.name,
    wxAppId: normalized.wxAppId,
  })
  const target = path.join(appRoot, 'src', 'manifest.json')
  fs.writeFileSync(target, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
  const reparsed = JSON.parse(fs.readFileSync(target, 'utf8'))
  if (reparsed.name !== manifest.name || reparsed['mp-weixin'].appid !== manifest['mp-weixin'].appid) {
    throw new BuildFailedError('副本 manifest 回读校验失败')
  }
  return manifest
}

function resolveUniBin() {
  // 测试接缝：fake uni CLI 注入，用于子进程失败/产物无效等故障流程测试
  if (process.env.TENANT_TEST_UNI_BIN) {
    return process.env.TENANT_TEST_UNI_BIN
  }
  const pkgPath = require.resolve('@dcloudio/vite-plugin-uni/package.json')
  const pkg = require(pkgPath)
  const bin = typeof pkg.bin === 'string' ? pkg.bin : pkg.bin?.uni
  if (!bin) {
    throw new BuildFailedError('无法解析 @dcloudio/vite-plugin-uni 的 uni CLI bin')
  }
  const uniBin = path.join(path.dirname(pkgPath), bin)
  if (!fs.existsSync(uniBin)) {
    throw new BuildFailedError(`uni CLI 不存在: ${uniBin}`)
  }
  return uniBin
}

function nodeMajorVersion() {
  return Number(process.versions.node.split('.')[0])
}

// ---------- 单次构建 ----------

async function buildOne(normalized, { dryRun }) {
  const paths = resolveBuildPaths(projectRoot, normalized)
  paths.createdAt = new Date().toISOString()

  if (dryRun) {
    console.log('[dry-run] 规范化身份:', JSON.stringify(toDisplay(normalized), null, 2))
    console.log('[dry-run] 工作目录:', paths.workRoot)
    console.log('[dry-run] 发布目录:', paths.releaseRoot)
    console.log('[dry-run] 子进程 env（封闭清单）:', JSON.stringify(buildChildEnv(normalized, paths), null, 2))
    console.log('[dry-run] uni bin:', resolveUniBin())
    console.log('[dry-run] node:', process.version)
    return null
  }

  if (fs.existsSync(paths.releaseRoot)) {
    throw new BuildFailedError(`发布目标已存在，拒绝覆盖: ${paths.releaseRoot}`)
  }
  if (fs.existsSync(paths.workRoot)) {
    throw new BuildFailedError(`工作目录已存在（buildId 冲突？）: ${paths.workRoot}`)
  }
  fs.mkdirSync(paths.logsDir, { recursive: true })
  writeOwnership(paths, 'CREATED', normalized.buildId)
  console.log(`[build-tenant] workRoot: ${paths.workRoot}`)

  // 复制快照并立即核对副本摘要（生成物写入之前；此后副本仅允许出现
  // 明确许可的差异：manifest 与身份模块，由各自生成逻辑自行校验）
  const { inventory } = createSnapshot({ projectRoot, appRoot: paths.appRoot })
  const stability = verifySnapshot(paths.appRoot, inventory)
  if (!stability.ok) {
    throw new BuildFailedError(`副本快照校验失败: ${stability.reason}`)
  }
  writeOwnership(paths, 'COPIED', normalized.buildId)
  fs.writeFileSync(paths.sourceInventoryFile, JSON.stringify({
    ...summarizeInventory(inventory),
    inventory,
    recordedAt: new Date().toISOString(),
  }, null, 2), 'utf8')
  console.log(`[build-tenant] 副本文件数: ${inventory.length}`)

  // node_modules 链接（只读依赖）
  const nodeModulesLink = path.join(paths.appRoot, 'node_modules')
  fs.symlinkSync(path.join(projectRoot, 'node_modules'), nodeModulesLink, 'dir')

  // 受控 env（新建对象，继承残留无效）
  const childEnv = buildChildEnv(normalized, paths)

  // 生成 manifest（副本内工厂）与身份模块（pages.json 由 vite 配置阶段的预生成屏障刷新）
  const manifest = await generateManifestInCopy(paths.appRoot, normalized)
  const generatedDir = path.join(paths.appRoot, 'src', 'generated')
  fs.mkdirSync(generatedDir, { recursive: true })
  fs.writeFileSync(path.join(generatedDir, 'tenant-build.ts'), generateTenantIdentityModule(normalized), 'utf8')
  fs.writeFileSync(paths.normalizedConfigFile, JSON.stringify(toDisplay(normalized), null, 2), 'utf8')

  // spawn uni build
  const uniBin = resolveUniBin()
  const logStream = fs.createWriteStream(paths.buildLog, { flags: 'a' })
  console.log(`[build-tenant] 启动 uni build（mode=${normalized.profile}, deployment=${normalized.deployment}）`)
  const child = spawn(process.execPath, [
    uniBin,
    'build',
    '-p', 'mp-weixin',
    '--mode', normalized.profile,
  ], {
    cwd: paths.appRoot,
    env: childEnv,
    shell: false,
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  child.stdout.on('data', (chunk) => {
    logStream.write(chunk)
    process.stdout.write(chunk)
  })
  child.stderr.on('data', (chunk) => {
    logStream.write(chunk)
    process.stderr.write(chunk)
  })
  const signalHandler = (signal) => {
    child.kill(signal)
  }
  process.on('SIGINT', signalHandler)
  process.on('SIGTERM', signalHandler)

  const closeCode = await new Promise((resolve) => {
    child.on('close', (code, signal) => resolve({ code, signal }))
  })
  process.off('SIGINT', signalHandler)
  process.off('SIGTERM', signalHandler)
  logStream.end()

  if (closeCode.signal || closeCode.code !== 0) {
    throw new BuildFailedError(`uni build 失败: exitCode=${closeCode.code}, signal=${closeCode.signal ?? 'none'}（日志: ${paths.buildLog}）`)
  }
  writeOwnership(paths, 'COMPILED', normalized.buildId)
  console.log('[build-tenant] 编译完成，开始产物校验')

  // 产物校验
  const evidencePath = path.join(paths.attemptRoot, 'artifact-evidence.json')
  let verification
  let checksums
  try {
    const result = verifyArtifact({
      outputDir: paths.outputDir,
      snapshotAppRoot: paths.appRoot,
      normalized,
      evidencePath,
    })
    verification = result.verification
    checksums = result.checksums
  }
  catch (error) {
    if (error instanceof ArtifactVerifyError) {
      throw new BuildFailedError(`产物校验失败: ${error.message}`)
    }
    throw error
  }
  fs.writeFileSync(paths.verificationFile, JSON.stringify(verification, null, 2), 'utf8')
  writeOwnership(paths, 'VERIFIED', normalized.buildId)

  // 发布（同文件系统 rename；目标不存在已在前面检查）。
  // 微信开发者工具/上传工具导入的是 releaseRoot/mp-weixin 目录，包外元数据平级放置。
  fs.mkdirSync(path.join(paths.releaseRoot, 'mp-weixin'), { recursive: true })
  try {
    fs.renameSync(paths.outputDir, path.join(paths.releaseRoot, 'mp-weixin'))
  }
  catch (error) {
    throw new BuildFailedError(`发布 rename 失败（目标可能已被占用）: ${error.message}`)
  }
  const buildMeta = {
    buildId: normalized.buildId,
    key: normalized.key,
    name: normalized.name,
    tenantId: normalized.tenantId,
    wxAppId: normalized.wxAppId,
    profile: normalized.profile,
    deployment: normalized.deployment,
    openBoot: normalized.openBoot,
    apiBaseUrl: normalized.apiBaseUrl,
    createdAt: paths.createdAt,
    verifiedAt: verification.verifiedAt,
    publishedAt: new Date().toISOString(),
    node: process.version,
    sourceInventory: summarizeInventory(inventory),
    lockfileSha256: sha256File(path.join(projectRoot, 'pnpm-lock.yaml')),
  }
  // 包外元数据：build-meta / verification / checksums 放发布目录，不进小程序包体积
  fs.writeFileSync(path.join(paths.releaseRoot, 'build-meta.json'), JSON.stringify(buildMeta, null, 2), 'utf8')
  fs.writeFileSync(path.join(paths.releaseRoot, 'verification.json'), JSON.stringify(verification, null, 2), 'utf8')
  fs.writeFileSync(path.join(paths.releaseRoot, 'checksums.json'), JSON.stringify(checksums, null, 2), 'utf8')
  writeOwnership(paths, 'PUBLISHED', normalized.buildId)
  console.log(`[build-tenant] 已发布: ${paths.releaseRoot}`)
  return paths.releaseRoot
}

// ---------- 主流程 ----------

async function main() {
  const args = parseArgs(process.argv.slice(2))
  if (args.help) {
    printHelp()
    return
  }
  if (nodeMajorVersion() < 22) {
    throw new BuildFailedError(`需要 Node >= 22，当前 ${process.version}`)
  }

  const tenantsDir = path.join(projectRoot, 'tenants')
  // 全量预检（all 与单租户都做：错误提前失败）
  precheckAll(tenantsDir)

  const keys = args.target === 'all' ? listTenantKeys(tenantsDir) : [args.target]
  const plan = keys.map((key) => {
    const config = loadTenantConfig(tenantsDir, key)
    const target = resolveTarget(config, { profile: args.profile, deployment: args.deployment })
    return normalize(config, target, newBuildId())
  })
  console.log(`[build-tenant] 计划构建: ${plan.map(item => `${item.key}/${item.profile}/${item.deployment}`).join(', ')}`)

  if (args.dryRun) {
    for (const normalized of plan) {
      await buildOne(normalized, { dryRun: true })
    }
    return
  }

  const lockFile = acquireLock()
  const failures = []
  const releases = []
  try {
    for (const normalized of plan) {
      try {
        const release = await buildOne(normalized, { dryRun: false })
        if (release) {
          releases.push(release)
        }
      }
      catch (error) {
        const message = error instanceof TenantConfigError || error instanceof SnapshotError
          ? error.message
          : error.stack || error.message
        failures.push({ key: normalized.key, message })
        console.error(`[build-tenant] ${normalized.key} 构建失败:\n${message}`)
        // ownership 记 FAILED（目录可能尚未创建，容错处理）
        try {
          const failedPaths = resolveBuildPaths(projectRoot, normalized)
          if (fs.existsSync(failedPaths.ownershipFile)) {
            writeOwnership(failedPaths, 'FAILED', normalized.buildId, { failure: message.slice(0, 2000) })
          }
        }
        catch {}
        break // 首失败停止；此前成功的版本保留
      }
    }
  }
  finally {
    releaseLock(lockFile)
  }

  if (failures.length > 0) {
    console.error(`[build-tenant] 整体失败（已发布 ${releases.length} 个版本保留）`)
    process.exit(1)
  }
  console.log(`[build-tenant] 全部完成: ${releases.join(', ') || '(无)'}`)
}

main().catch((error) => {
  console.error(`[build-tenant] ${error?.stack || error}`)
  process.exit(1)
})
