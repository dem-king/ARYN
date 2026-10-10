#!/usr/bin/env node
/**
 * 默认 uni dev/build 包装器：pnpm dev:mp-weixin / build:mp-weixin 等旧命令名不变，
 * 内部统一走本脚本，保证：
 * 1. 启动 uni CLI 之前用唯一工厂把当前根的默认 manifest 写好（消除 CLI 早读旧文件的错位）；
 * 2. 清理租户构建残留的控制变量与 UNI_INPUT/OUTPUT_DIR 覆盖，普通开发回到 .env 行为；
 * 3. 转发退出码与 SIGINT/SIGTERM。
 */
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { createRequire } from 'node:module'

const thisFile = fileURLToPath(import.meta.url)
const projectRoot = path.resolve(path.dirname(thisFile), '..')
const require = createRequire(path.join(projectRoot, 'package.json'))

async function writeDefaultManifest() {
  const factory = await import(pathToFileURL(path.join(projectRoot, 'build', 'manifest-factory.mjs')).href)
  const manifest = factory.createManifest({ name: 'aryn-mall-uniapp', wxAppId: 'wx0a8242ea59f3e6b4' })
  const target = path.join(projectRoot, 'src', 'manifest.json')
  const current = fs.existsSync(target) ? JSON.parse(fs.readFileSync(target, 'utf8')) : null
  const next = JSON.stringify(manifest, null, 2)
  // 内容一致时不回写，避免无意义的 mtime 抖动触发 watcher 重编译
  if (current && JSON.stringify(current, null, 2) === next) {
    return
  }
  fs.writeFileSync(target, `${next}\n`, 'utf8')
}

function resolveUniBin() {
  const pkgPath = require.resolve('@dcloudio/vite-plugin-uni/package.json')
  const pkg = require(pkgPath)
  const bin = typeof pkg.bin === 'string' ? pkg.bin : pkg.bin?.uni
  if (!bin) {
    throw new Error('无法从 @dcloudio/vite-plugin-uni 的 bin 元数据解析 uni CLI')
  }
  return path.join(path.dirname(pkgPath), bin)
}

async function main() {
  const argv = process.argv.slice(2)
  const command = argv[0]
  if (command !== 'dev' && command !== 'build') {
    console.error(`run-uni.mjs 只支持 dev/build，收到: ${command}`)
    process.exit(2)
  }
  const rest = argv.slice(1)
  if (rest.includes('--watch') && command === 'dev') {
    // uni dev 本身就是 watch，保持原样放行
  }

  // 清理租户残留：租户控制变量 + 输出目录覆盖，普通开发不允许被它们影响
  const { env, removed } = cleanEnv()
  if (removed.length > 0) {
    console.warn(`[run-uni] 已清理残留环境变量: ${removed.join(', ')}`)
  }

  // 唯一工厂写默认 manifest；失败不得启动 CLI
  try {
    await writeDefaultManifest()
  }
  catch (error) {
    console.error(`[run-uni] 默认 manifest 生成失败: ${error.message}`)
    process.exit(1)
  }

  const uniBin = resolveUniBin()
  const child = spawn(process.execPath, [uniBin, command, ...rest], {
    cwd: projectRoot,
    env,
    shell: false,
    stdio: 'inherit',
  })
  forwardSignals(child)
  child.on('close', (code, signal) => {
    if (signal) {
      process.kill(process.pid, signal)
      return
    }
    process.exit(code ?? 1)
  })
}

function cleanEnv() {
  const env = { ...process.env }
  const removed = []
  const tenantKeys = ['VITE_TENANT_BUILD', 'VITE_TENANT_KEY', 'VITE_TENANT_ID', 'VITE_TENANT_WX_APPID', 'VITE_TENANT_NAME', 'VITE_BUILD_ID']
  for (const key of tenantKeys) {
    if (key in env) {
      delete env[key]
      removed.push(key)
    }
  }
  if ('UNI_INPUT_DIR' in env) {
    delete env.UNI_INPUT_DIR
    removed.push('UNI_INPUT_DIR')
  }
  if ('UNI_OUTPUT_DIR' in env) {
    delete env.UNI_OUTPUT_DIR
    removed.push('UNI_OUTPUT_DIR')
  }
  return { env, removed }
}

function forwardSignals(child) {
  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => {
      child.kill(signal)
    })
  }
}

main().catch((error) => {
  console.error(`[run-uni] ${error.message}`)
  process.exit(1)
})
