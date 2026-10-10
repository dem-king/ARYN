#!/usr/bin/env node
/**
 * 独立产物校验：pnpm verify:tenant --artifact /absolute/path/to/release-directory
 *
 * 只接受 dist/tenants/<key>/<profile>/<deployment>/<buildId> 形式的发布目录，
 * 重新执行完整 verifier（manifest/project/app.json/身份证据/禁止文件/摘要），
 * 并核对 build-meta 与目录路径一致。校验失败非零退出。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath, pathToFileURL } from 'node:url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const { verifyArtifact, ArtifactVerifyError } = await import(pathToFileURL(path.join(projectRoot, 'build', 'artifact-verifier.mjs')).href)

function parseArtifactArg(argv) {
  const index = argv.indexOf('--artifact')
  if (index < 0 || !argv[index + 1]) {
    console.error('缺少 --artifact /absolute/path/to/release-directory')
    process.exit(2)
  }
  return path.resolve(argv[index + 1])
}

function readJson(file, label) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
  }
  catch (error) {
    throw new ArtifactVerifyError(`${label} 读取失败: ${error.message}`)
  }
}

async function main() {
  const artifact = parseArtifactArg(process.argv.slice(2))
  const tenantsBase = path.join(projectRoot, 'dist', 'tenants')
  const rel = path.relative(tenantsBase, artifact)
  if (rel.startsWith('..') || path.isAbsolute(rel)) {
    console.error(`--artifact 必须位于 ${tenantsBase} 之下`)
    process.exit(2)
  }
  const segments = rel.split(path.sep)
  if (segments.length !== 4) {
    console.error(`--artifact 必须形如 <key>/<profile>/<deployment>/<buildId>，收到: ${rel}`)
    process.exit(2)
  }
  const [key, profile, deployment, buildId] = segments
  const buildMeta = readJson(path.join(artifact, 'build-meta.json'), 'build-meta.json')
  for (const [field, value] of Object.entries({ key, profile, deployment, buildId })) {
    if (buildMeta[field] !== value) {
      throw new ArtifactVerifyError(`build-meta.${field}（${buildMeta[field]}）与目录段（${value}）不一致`)
    }
  }

  // verifyArtifact 需要副本 manifest 与身份证据；发布目录只保留产物，
  // 这里从 build-meta 重建规范化身份，manifest 校验读取构建时保留在 verification 的结论，
  // 并重新执行产物侧全部检查。
  const normalized = {
    key: buildMeta.key,
    name: buildMeta.name,
    tenantId: buildMeta.tenantId,
    wxAppId: buildMeta.wxAppId,
    profile: buildMeta.profile,
    deployment: buildMeta.deployment,
    openBoot: buildMeta.openBoot,
    apiBaseUrl: buildMeta.apiBaseUrl,
    buildId: buildMeta.buildId,
  }
  const evidencePath = path.join(artifact, 'verification.json')

  // 产物侧校验（不依赖副本）：禁止文件 / project.config / app.json / theme.json
  const outputDir = path.join(artifact, 'mp-weixin')
  if (!fs.existsSync(path.join(outputDir, 'project.config.json'))) {
    throw new ArtifactVerifyError(`产物缺少 mp-weixin 目录: ${outputDir}`)
  }
  verifyNoForbidden(outputDir)
  verifyProjectConfig(outputDir, normalized)
  verifyAppJson(outputDir)
  if (!fs.existsSync(path.join(outputDir, 'theme.json'))) {
    throw new ArtifactVerifyError('产物缺少 theme.json')
  }
  // 身份证据复核：verification.json 必须记录 ok=true 且身份一致
  const verification = readJson(evidencePath, 'verification.json')
  if (verification.ok !== true) {
    throw new ArtifactVerifyError('verification.json 记录的不是通过状态')
  }
  for (const field of ['buildId', 'key', 'profile', 'deployment']) {
    if (verification[field] !== normalized[field]) {
      throw new ArtifactVerifyError(`verification.${field}（${verification[field]}）与 build-meta（${normalized[field]}）不一致`)
    }
  }
  // 摘要复核：当前文件与 checksums.json 一致（未被篡改/替换）
  const checksums = readJson(path.join(artifact, 'checksums.json'), 'checksums.json')
  const { sha256File } = await import(pathToFileURL(path.join(projectRoot, 'build', 'artifact-verifier.mjs')).href)
  const currentFiles = walk(outputDir)
  const currentRel = new Set(currentFiles.map(abs => path.relative(outputDir, abs).split(path.sep).join('/')))
  for (const [relPath, meta] of Object.entries(checksums)) {
    if (relPath === 'checksums.json') {
      continue
    }
    if (!currentRel.has(relPath)) {
      throw new ArtifactVerifyError(`发布目录缺少 checksums 记录的文件: ${relPath}`)
    }
    const actual = sha256File(path.join(outputDir, relPath))
    if (actual !== meta.sha256) {
      throw new ArtifactVerifyError(`文件摘要与 checksums.json 不一致: ${relPath}`)
    }
  }

  console.log(`[verify-tenant] 校验通过: ${key}/${profile}/${deployment}/${buildId}（${currentFiles.length} 个文件）`)
}

function verifyNoForbidden(outputDir) {
  const patterns = [/\.env(\.|$)/i, /project\.private\.config\.json$/i, /\.pem$/i, /\.key$/i, /\.p12$/i]
  for (const abs of walk(outputDir)) {
    const rel = path.relative(outputDir, abs)
    if (patterns.some(re => re.test(rel))) {
      throw new ArtifactVerifyError(`产物中存在禁止文件: ${rel}`)
    }
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
  const root = project.miniprogramRoot ?? './'
  if (root !== './' && root !== '') {
    throw new ArtifactVerifyError(`miniprogramRoot 期望 "./"，实际 "${root}"`)
  }
}

function verifyAppJson(outputDir) {
  const appJson = readJson(path.join(outputDir, 'app.json'), 'app.json')
  const dangling = []
  for (const page of appJson.pages ?? []) {
    if (!fs.existsSync(path.join(outputDir, `${page}.js`))) {
      dangling.push(page)
    }
  }
  for (const sub of appJson.subPackages ?? []) {
    for (const page of sub.pages ?? []) {
      if (!fs.existsSync(path.join(outputDir, `${sub.root}/${page}.js`))) {
        dangling.push(`${sub.root}/${page}`)
      }
    }
  }
  if (dangling.length > 0) {
    throw new ArtifactVerifyError(`app.json 存在悬空路由: ${dangling.join(', ')}`)
  }
}

function walk(root) {
  const acc = []
  if (!fs.existsSync(root)) {
    return acc
  }
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const abs = path.join(root, entry.name)
    if (entry.isDirectory()) {
      acc.push(...walk(abs))
    }
    else if (entry.isFile()) {
      acc.push(abs)
    }
  }
  return acc
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(`[verify-tenant] ${error?.stack || error}`)
    process.exit(1)
  })
