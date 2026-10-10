import path from 'node:path'
import fs from 'node:fs'

/**
 * 构建上下文：本次构建的绝对路径契约与子进程受控环境。
 *
 * 子进程 env 是新建对象：清除继承的 VITE_ 与 UNI_ 前缀残留、HBuilderX 参数和未知 NODE_OPTIONS，
 * 只注入封闭清单里的变量。租户构建绝不读取原项目 .env —— 全部值来自规范化配置。
 */

const SYSTEM_ENV_KEYS = [
  'PATH',
  'HOME',
  'TMPDIR',
  'TEMP',
  'TMP',
  'LANG',
  'LC_ALL',
  'LC_CTYPE',
  'TZ',
  'USER',
  'LOGNAME',
  'SHELL',
  'TERM',
  'XDG_CACHE_HOME',
  'XDG_CONFIG_HOME',
  'XDG_DATA_HOME',
  'SSL_CERT_FILE',
  'SSL_CERT_DIR',
  'SYSTEMROOT',
  'COMSPEC',
  'PROGRAMFILES',
  'NUMBER_OF_PROCESSORS',
]

/** 租户构建注入的 VITE_ 变量封闭清单（不含派生值，见 buildChildEnv）。 */
export const TENANT_VITE_KEYS = [
  'VITE_TENANT_BUILD',
  'VITE_TENANT_KEY',
  'VITE_TENANT_ID',
  'VITE_TENANT_WX_APPID',
  'VITE_TENANT_NAME',
  'VITE_OPEN_BOOT',
  'VITE_API_BASE_URL',
  'VITE_ENV_NAME',
  'VITE_BUILD_ID',
  'VITE_PORT',
]

export function resolveProjectRoot(startDir) {
  let dir = path.resolve(startDir)
  while (true) {
    if (fs.existsSync(path.join(dir, 'package.json')) && fs.existsSync(path.join(dir, 'manifest.config.ts'))) {
      return dir
    }
    const parent = path.dirname(dir)
    if (parent === dir) {
      throw new Error(`无法从 ${startDir} 向上定位 aryn-mall-uniapp 项目根`)
    }
    dir = parent
  }
}

/**
 * 本次构建的完整目录契约。
 */
export function resolveBuildPaths(projectRoot, normalized) {
  const workRoot = path.join(projectRoot, '.tenant-build', normalized.key, normalized.profile, normalized.deployment, normalized.buildId)
  const attemptRoot = path.join(workRoot, 'attempt')
  return {
    workRoot,
    appRoot: path.join(workRoot, 'app'),
    attemptRoot,
    outputDir: path.join(attemptRoot, 'mp-weixin'),
    cacheViteDir: path.join(workRoot, 'cache', 'vite'),
    cacheUniDir: path.join(workRoot, 'cache', 'uni'),
    logsDir: path.join(workRoot, 'logs'),
    buildLog: path.join(workRoot, 'logs', 'build.log'),
    ownershipFile: path.join(workRoot, 'ownership.json'),
    sourceInventoryFile: path.join(workRoot, 'source-inventory.json'),
    normalizedConfigFile: path.join(workRoot, 'normalized-config.json'),
    verificationFile: path.join(workRoot, 'verification.json'),
    releaseRoot: path.join(projectRoot, 'dist', 'tenants', normalized.key, normalized.profile, normalized.deployment, normalized.buildId),
  }
}

/**
 * 由规范化身份生成子进程 env。父进程同名变量一律无效（新建对象 + 封闭清单）。
 */
export function buildChildEnv(normalized, paths, { nodeEnv = 'production' } = {}) {
  const env = {}
  for (const key of SYSTEM_ENV_KEYS) {
    if (process.env[key] !== undefined) {
      env[key] = process.env[key]
    }
  }
  if (process.env.CI !== undefined) {
    env.CI = process.env.CI
  }
  env.CI = env.CI || '1'
  env.NODE_ENV = nodeEnv

  env.VITE_TENANT_BUILD = 'true'
  env.VITE_TENANT_KEY = normalized.key
  env.VITE_TENANT_ID = normalized.tenantId
  env.VITE_TENANT_WX_APPID = normalized.wxAppId
  env.VITE_TENANT_NAME = normalized.name
  env.VITE_OPEN_BOOT = String(normalized.openBoot)
  env.VITE_API_BASE_URL = normalized.apiBaseUrl
  env.VITE_ENV_NAME = normalized.profile
  env.VITE_BUILD_ID = normalized.buildId
  env.VITE_PORT = '8888'

  env.VITE_ROOT_DIR = paths.appRoot
  env.UNI_INPUT_DIR = path.join(paths.appRoot, 'src')
  env.UNI_OUTPUT_DIR = paths.outputDir
  env.UNI_APP_X_CACHE_DIR = paths.cacheUniDir
  // 非 VITE_ 前缀：只给构建工具链用，不进客户端代码
  env.TENANT_BUILD_CACHE_DIR = paths.cacheViteDir
  return env
}

/** 默认开发包装器使用的 env 清理：剥掉租户控制变量与残留输出目录覆盖。 */
export function cleanDefaultEnv(baseEnv = process.env) {
  const env = { ...baseEnv }
  const removed = []
  for (const key of Object.keys(env)) {
    if (key === 'VITE_TENANT_BUILD' || key === 'VITE_TENANT_KEY' || key === 'VITE_TENANT_ID'
      || key === 'VITE_TENANT_WX_APPID' || key === 'VITE_TENANT_NAME' || key === 'VITE_BUILD_ID'
      || key.startsWith('UNI_INPUT_DIR') || key.startsWith('UNI_OUTPUT_DIR')) {
      delete env[key]
      removed.push(key)
    }
  }
  return { env, removed }
}
